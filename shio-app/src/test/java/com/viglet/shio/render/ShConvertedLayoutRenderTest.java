/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH464 — the layout a conversion writes actually renders, and the editor has something to frame.
 *
 * <h2>What Block Q was named after and did not ship</h2>
 *
 * <p>The block is "an existing site becomes an <b>authorable</b> Shio site", and neither mode
 * produced one. Measured on two clean replicas of the same page: <b>fidelity renders and cannot be
 * edited</b> — {@code /preview/Final/} answers 200, {@code ?editor=true} injects the bridge, and the
 * page carries <b>zero</b> {@code data-shio-prop}, because verbatim markup has no {@code sh-field}
 * to annotate. And <b>authorable did not render</b> — accepting both candidates wrote two sections,
 * a Page and a Theme and <b>no PageLayout</b>, so {@code /preview/Autor2/} answered
 * <b>400 — binds no layout for post-type 'Page'</b>, and the console frames the site's front end or,
 * failing that, that same preview, so the editor had nothing to frame either. A replica was
 * curatable only by standing up the Next.js front end and pointing the site's {@code url} at it.</p>
 *
 * <h2>Why this test reads a JavaScript file</h2>
 *
 * <p>Because the seam is between two languages and every earlier failure of this kind was one side
 * asserted against a copy of itself (SH245). The layout is written by {@code cli/src/convert.mjs};
 * the annotation is done by {@code ShRegionComposer} in this JVM. A Java test with its own copy of
 * the template would go green while the shipped one was broken — which is exactly what SH449's CLI
 * tests did: they asserted the document's <em>shape</em> and could not see that the authorable
 * branch wrote no layout at all. So {@code PAGE_BODY_HTML} is a no-interpolation export and this
 * reads those bytes.</p>
 *
 * <h2>SH465 — and why two hand-seeded section types were not enough</h2>
 *
 * <p>The tests above seed a {@code SectionHero} and a {@code SectionRichText}, "chosen because between
 * them they cover every branch the union renders". They do cover every branch that <em>existed</em>,
 * and that is the flaw: both types are scalar-only, so the four <b>collection-shaped</b> types
 * ({@code features}, {@code questions}, {@code logos}, {@code quotes}) rendered a heading and stopped
 * while this class was green. {@code SectionLogoWall}, which declares only {@code heading} and
 * {@code logos}, rendered as a bare {@code <h2>}.</p>
 *
 * <p>So SH465's instrument does not seed a type it chose. It <b>reads the vocabulary</b> — every
 * {@code Section*} post-type the shipped blueprints declare — composes one section of each with a
 * sentinel in every declared field, renders the page and reports the fields that are not on it. A
 * section type or a field the model gains and the layout never draws fails here, by name, without
 * anyone having to remember to come back. That is SH338's rule applied to a render: the check that
 * finds the <em>next</em> one lands before the fix for this one.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-converted-layout;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShConvertedLayoutRenderTest {

    /** The file that writes the layout — read, never copied. */
    private static final Path CONVERT =
            Path.of("..", "cli", "src", "convert.mjs");

    private static final String LAYOUT_TITLE = "Sh464 (fidelity)";

    private static final String BACKSLASH = "\\";
    private static final String QUOTE = "\"";

    /** Where the SH465 coverage page lives, kept off {@code /} so SH464's counts are untouched. */
    private static final String VOCABULARY_URL = "/vocabulary";

    /**
     * Every section type the shipped blueprints declare — the vocabulary, read rather than listed.
     *
     * <p>SH465's instrument, and the reason it is a <em>read</em>: SH464's own tests seeded two of the
     * seven section types by hand and were green while four of the other five rendered as a heading
     * and nothing else. A list in this file would have had to be edited by the same person who forgot
     * the fields, so it would have said whatever the layout said. The declaration cannot: a section
     * type or a field the model gains and the layout never draws fails here, naming it.</p>
     */
    private static final List<ShSectionType> VOCABULARY = readVocabulary();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * One declared section type, and where it was declared.
     *
     * @param blueprint the package that declares it, so two blueprints that have drifted apart are
     *                  two entries rather than one that silently won
     */
    private record ShSectionType(String blueprint, String name, JsonNode declaration) {

        /** The slug every sentinel and the section's own friendly URL are built from. */
        String slug() {
            return (blueprint + "-" + name).toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One field of one section type, flattened — a relator's row fields included. */
    private record ShSectionField(String path, String widget) {

        /**
         * A {@code File} field holds a post id that {@code file_url} resolves, so a sentinel is not
         * a value it can render — the helper answers the empty string for anything that is not an
         * id. Excluded from the assertion and from nothing else: the field is still written, so the
         * {@code <img>} around it is still exercised.
         *
         * <p>This exemption is <b>SH471</b>, found by writing it: a conversion stores the source's
         * own path in these fields, not an id, so a converted {@code SectionHero} renders
         * {@code <img src="">} and the two row fields here inherit it. Named rather than silently
         * skipped, because an exemption nobody can read is how a coverage check stops covering.</p>
         */
        boolean isRenderableAsText() {
            return !"File".equals(widget);
        }
    }

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void seedAConvertedAuthorableReplica() {
        siteName = "sh464-" + UUID.randomUUID().toString().substring(0, 8);
        String body = pageBodyHtml();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            // The binding convert writes: one layout per post-type, which is why there cannot be
            // one layout per conversion mode and why this one template serves both.
            site.setPostTypeLayout("{\"Page\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, null, siteName, when);
            ShFolder sections = folder(site, root, "sections", when);
            ShFolder theme = folder(site, root, "theme", when);

            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType pageType = postType("Page", (byte) 0, when);
            ShPostType heroType = postType("SectionHero", (byte) 0, when);
            ShPostType richTextType = postType("SectionRichText", (byte) 0, when);

            post(site, theme, layoutType, LAYOUT_TITLE, "/theme/layout",
                    json("HTML", "<!doctype html><html><head><title>{{post.title}}</title>"
                            + "</head><body>\n" + body + "\n</body></html>"), when);

            // Two of the seven SH95 section types, chosen because between them they cover every
            // branch the union renders: heading/subheading/body/cta on the hero, and an HTML Editor
            // body that must arrive raw on the rich text.
            post(site, sections, heroType, "Hero", "/sections/hero",
                    json("heading", "Ship faster",
                            "subheading", "The line under it",
                            "ctaLabel", "Start now",
                            "ctaHref", "/signup",
                            "align", "center"), when);
            post(site, sections, richTextType, "Prose", "/sections/prose",
                    json("heading", "About", "body", "<p><em>Rich</em> text</p>"), when);

            // The Page as `composePages` writes it: a title and a `sections` list of friendly URLs,
            // in render order, and no `content` — which is the shape that used to answer 400.
            post(site, root, pageType, "Home", "/",
                    "{\"title\":\"Home\",\"sections\":[\"/sections/hero\",\"/sections/prose\"]}",
                    when);

            // SH465 — and one section per *declared* type, every field carrying a sentinel, on a page
            // of its own so the counts SH464 asserts on `/` are untouched.
            ArrayNode composed = MAPPER.createArrayNode();
            for (ShSectionType type : VOCABULARY) {
                String furl = VOCABULARY_URL + "/" + type.slug();
                post(site, sections, postType(type.name(), (byte) 0, when), type.name(), furl,
                        sentinelData(type), when);
                composed.add(furl);
            }
            ObjectNode page = MAPPER.createObjectNode();
            page.put("title", "Vocabulary");
            page.set("sections", composed);
            post(site, root, pageType, "Vocabulary", VOCABULARY_URL, page.toString(), when);
        });
    }

    @Test
    @DisplayName("SH464: an authorable replica renders — the 400 was the whole of the other half")
    void anAuthorablePageRendersThroughTheConvertedLayout() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("`{{#getRelation sections}}` walks Page.sections in order, so the section posts "
                        + "are on the page a visitor gets — before this there was no layout at all "
                        + "and /preview/<site>/ answered 400 binds-no-layout")
                .contains("Ship faster")
                .contains("The line under it")
                .contains("Start now")
                .contains("About");
        assertThat(html.indexOf("Ship faster"))
                .as("in the order the field names them, which is what makes `sections` a sequence "
                        + "rather than a set")
                .isLessThan(html.indexOf("About"));
        assertThat(html)
                .as("an HTML Editor field arrives raw — a rich-text body escaped into entities is "
                        + "the SH461 defect one field along")
                .contains("<em>Rich</em> text");
    }

    @Test
    @DisplayName("SH464: every section field carries data-shio-prop, so the editor has props to bind")
    void everyRenderedFieldIsAnnotatedForTheEditor() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        the measured failure of fidelity mode was ZERO data-shio-prop on a page \
                        that renders: verbatim markup declares no sh-field, so the bridge loaded \
                        over a page with nothing to offer. `sh-field` is what ShRegionComposer \
                        turns into this, and the layout declares one per field of the SH95 \
                        vocabulary.""")
                .contains("data-shio-prop=\"heading\"")
                .contains("data-shio-prop=\"subheading\"")
                .contains("data-shio-prop=\"ctaLabel\"")
                .contains("data-shio-prop=\"body\"")
                .contains("data-shio-prop=\"title\"");
        assertThat(html)
                .as("""
                        and each block is bound to its SECTION post rather than to the page \
                        (`sh-post="{{id}}"`) — without that, editing a heading would write the \
                        field onto the Page, which is the one shape that loses content silently.""")
                .contains("data-shio-post=");
    }

    @Test
    @DisplayName("SH464: a field the section does not carry renders nothing, so the union is safe")
    void anAbsentFieldRendersNothing() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        // SectionRichText declares only heading + body, so the hero's other fields must not appear
        // twice. The guard per field is what makes one template serve seven types.
        assertThat(html.split("data-shio-prop=\"subheading\"", -1))
                .as("the union renders per section, guarded — a template branching on {{type}} would "
                        + "need an equality helper this engine deliberately does not register")
                .hasSize(2);
        assertThat(html)
                .as("and an empty presentation choice is an empty attribute rather than the string "
                        + "`null` in a class name")
                .doesNotContain("data-shio-variant=\"null\"");
    }

    @Test
    @DisplayName("SH465: every field of every declared section type is drawn, collections included")
    void everyDeclaredFieldOfEverySectionTypeIsRendered() {
        String html = shPageRenderService.render(siteName, VOCABULARY_URL, ShPostState.DRAFT).html();

        List<String> missing = new ArrayList<>();
        for (ShSectionType type : VOCABULARY) {
            for (ShSectionField field : fieldsOf(type)) {
                if (field.isRenderableAsText() && !html.contains(sentinel(type, field.path()))) {
                    missing.add(type.name() + "." + field.path() + " (" + type.blueprint() + ")");
                }
            }
        }

        assertThat(missing)
                .as("""
                        SH465's whole defect, and the reason this asks the declaration instead of a \
                        list: SH464's union drew six scalar fields and no collection, so \
                        SectionFeatureGrid.features, SectionFaq.questions, SectionLogoWall.logos and \
                        SectionTestimonial.quotes were captured, converted, stored, addressed and \
                        never drawn — four of seven section types rendering as a heading, and \
                        SectionLogoWall as a bare <h2>. Every field named here is content a curator \
                        can see in the console and a visitor cannot see on the page, which is the \
                        one shape no instrument reported.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH465: a collection row renders no editable prop, because the protocol has no address for one")
    void aCollectionRowIsNotAnnotatedAsAField() {
        String html = shPageRenderService.render(siteName, VOCABULARY_URL, ShPostState.DRAFT).html();

        assertThat(html)
                .as("the rows are marked with the collection they came from, so what is on the page "
                        + "is traceable to the field that holds it")
                .contains("data-shio-row=\"features\"")
                .contains("data-shio-row=\"questions\"")
                .contains("data-shio-row=\"logos\"")
                .contains("data-shio-row=\"quotes\"");
        assertThat(annotatedCollectionRows())
                .as("""
                        and NOT with sh-field — asked of every {{#each}} block in the template \
                        rather than of the four names, so the next collection inherits the rule. \
                        SH185 addresses a field as a flat `prop` on the post the nearest sh-post \
                        ancestor names, and a row sits inside `<section sh-post="{{id}}">` — so \
                        `sh-field="body"` on a feature row would write data.body onto a \
                        SectionFeatureGrid, which declares no such field, and `sh-field="title"` \
                        would rename the post. There is no form for "the third row's second field", \
                        which is why the console's own relator widget is read-only too. An \
                        annotation a curator can type into and silently lose is the surprise P7 \
                        forbids.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH465: the collections are read as inline rows, not as references to other posts")
    void aCollectionIsWalkedWithEachRatherThanGetRelation() {
        String body = pageBodyHtml();

        assertThat(body)
                .as("""
                        an SH95 Relator declares NESTED fields, so its value is an array of row \
                        objects inside the section's own `data` — not a list of ids or friendly \
                        URLs. getRelation resolves references and would stringify a row map into \
                        nothing, while fieldsOf already hands Handlebars the List<Map> that \
                        {{#each}} walks. Getting this backwards is the one way to write four \
                        {{#getRelation}} blocks that compile, validate and render empty.""")
                .contains("{{#each features}}")
                .contains("{{#each questions}}")
                .contains("{{#each logos}}")
                .contains("{{#each quotes}}");
        assertThat(body.split("\\{\\{#getRelation", -1))
                .as("getRelation stays for `sections`, which genuinely is a list of friendly URLs")
                .hasSize(2);
    }

    @Test
    @DisplayName("the guard is not vacuous: the template really came out of convert.mjs")
    void theTemplateIsReadFromTheShippedFile() {
        String body = pageBodyHtml();

        assertThat(body)
                .as("a slice that silently came back empty would make every assertion above pass "
                        + "against a layout with no fields in it")
                .contains("{{#getRelation sections}}")
                .contains("sh-post=\"{{id}}\"")
                .contains("sh-field=\"heading\"")
                .hasSizeGreaterThan(400);
    }

    /**
     * The section types the shipped blueprints declare, one entry per declaration.
     *
     * <p>Keyed by package <b>and</b> name rather than merged: two blueprints that have drifted apart
     * are two things to draw, and a merge would let whichever file was read first decide silently —
     * the same shape of failure this whole test is about. A classpath with no section type at all is
     * a <b>failure</b>: an empty vocabulary makes every assertion below pass over nothing.</p>
     */
    private static List<ShSectionType> readVocabulary() {
        List<ShSectionType> found = new ArrayList<>();
        Pattern where = Pattern.compile("/blueprints/([^/]+)/post-types/");
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:/blueprints/*/post-types/Section*.json");
            for (Resource resource : resources) {
                JsonNode tree = new ObjectMapper().readTree(resource.getInputStream());
                Matcher matcher = where.matcher(resource.getURL().getPath());
                String name = tree.path("name").asText("");
                if (name.isBlank() || !matcher.find()) {
                    continue;
                }
                found.add(new ShSectionType(matcher.group(1), name, tree));
            }
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(
                    "the section vocabulary is declared by the shipped blueprints; this test reads "
                            + "them rather than listing the types it happens to remember", cannotRead);
        }
        found.sort(java.util.Comparator.comparing(ShSectionType::slug));
        assertThat(found)
                .as("an empty vocabulary would make the SH465 coverage assertion pass over nothing, "
                        + "which is the failure it was written to replace")
                .isNotEmpty();
        return List.copyOf(found);
    }

    /** One type's fields, flattened — {@code features} and {@code features.title} both appear. */
    private static List<ShSectionField> fieldsOf(ShSectionType type) {
        List<ShSectionField> flat = new ArrayList<>();
        collect(type.declaration().path("fields"), "", flat);
        return flat;
    }

    private static void collect(JsonNode fields, String prefix, List<ShSectionField> into) {
        for (JsonNode field : fields) {
            String name = field.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            into.add(new ShSectionField(path, field.path("widget").asText("")));
            collect(field.path("fields"), path, into);
        }
    }

    /**
     * The section's {@code data}, every declared field carrying a value only this test could write.
     *
     * <p>A relator becomes a one-row array, which is enough: the question is whether the layout draws
     * the collection at all, and it drew none of the four.</p>
     */
    private static String sentinelData(ShSectionType type) {
        ObjectNode data = MAPPER.createObjectNode();
        for (JsonNode field : type.declaration().path("fields")) {
            String name = field.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            JsonNode nested = field.path("fields");
            if (nested.isArray() && !nested.isEmpty()) {
                ObjectNode row = MAPPER.createObjectNode();
                for (JsonNode child : nested) {
                    String childName = child.path("name").asText("");
                    if (!childName.isBlank()) {
                        row.put(childName, sentinel(type, name + "." + childName));
                    }
                }
                data.set(name, MAPPER.createArrayNode().add(row));
            } else {
                data.put(name, sentinel(type, name));
            }
        }
        return data.toString();
    }

    /**
     * A value that can only have come from one field of one section type.
     *
     * <p>Letters, digits and hyphens only, so it survives HTML escaping unchanged and is as findable
     * inside a {@code data-} attribute as inside a {@code <p>} — {@code icon}, {@code align},
     * {@code variant} and {@code columns} are drawn as attributes, and a sentinel that needed
     * escaping would make those four unassertable for the wrong reason.</p>
     */
    private static String sentinel(ShSectionType type, String field) {
        return "sh465-" + type.slug() + "-" + field.replace('.', '-')
                .toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Any {@code {{#each}}} block of the template that annotates a row as an editable field.
     *
     * <p>The block's own opening tag is the reported unit, which is what makes a failure readable:
     * the answer to "which collection did somebody make editable" is the collection's name.</p>
     */
    private static List<String> annotatedCollectionRows() {
        String body = pageBodyHtml();
        List<String> annotated = new ArrayList<>();
        Matcher block = Pattern.compile("\\{\\{#each (\\w+)}}(.*?)\\{\\{/each}}", Pattern.DOTALL)
                .matcher(body);
        while (block.find()) {
            if (block.group(2).contains(ShRegionComposer.FIELD_ATTR)) {
                annotated.add(block.group(1));
            }
        }
        return annotated;
    }

    /**
     * {@code PAGE_BODY_HTML} from {@code cli/src/convert.mjs} — a slice between two backticks.
     *
     * <p>A missing file is a <b>failure</b> and never a skip: this test exists to assert a
     * cross-language contract, and one that quietly stops checking it is worse than none.</p>
     */
    private static String pageBodyHtml() {
        String source;
        try {
            source = Files.readString(CONVERT, StandardCharsets.UTF_8);
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(
                    "cli/src/convert.mjs is where the layout is written; this test reads it rather "
                            + "than keeping a second copy (SH245). If it moved, move this with it.",
                    cannotRead);
        }
        String marker = "export const PAGE_BODY_HTML = `";
        int start = source.indexOf(marker);
        assertThat(start)
                .as("PAGE_BODY_HTML is the export this test reads; if it was renamed or inlined, "
                        + "the contract it pins has no reader left")
                .isGreaterThanOrEqualTo(0);
        int from = start + marker.length();
        int end = source.indexOf('`', from);
        assertThat(end).as("the template literal is unterminated").isGreaterThan(from);
        return source.substring(from, end);
    }

    private ShFolder folder(ShSite site, ShFolder parent, String name, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setParentFolder(parent);
        folder.setRootFolder(parent == null ? (byte) 1 : (byte) 0);
        if (parent == null) {
            folder.setShSite(site);
        }
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name).getResultStream().findFirst().orElse(null);
        if (existing != null) {
            return existing;
        }
        ShPostType type = new ShPostType();
        type.setName(name);
        type.setTitle(name);
        type.setSystem(system);
        type.setDate(when);
        type.setOwner("admin");
        entityManager.persist(type);
        return type;
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.DRAFT);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setLocale("en");
        post.setOwner("admin");
        post.setDate(when);
        post.setData(data);
        entityManager.persist(post);
    }

    private static String json(String... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(pairs[i]).append("\":\"")
                    .append(escape(pairs[i + 1])).append('"');
        }
        return out.append('}').toString();
    }

    /**
     * A JSON string body — and the newline is the one that matters.
     *
     * <p>Without it the layout's {@code data} is unparseable JSON, {@code fieldsOf} returns no
     * fields, and the page renders as the empty string with a CONTENT incident nobody in a test
     * reads: the first run of this test failed on exactly that and looked like the template being
     * wrong. Worth the four lines, because the failure is indistinguishable from the one it hides.</p>
     */
    private static String escape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (char each : value.toCharArray()) {
            switch (each) {
                case '\\' -> out.append(BACKSLASH).append(BACKSLASH);
                case '"' -> out.append(BACKSLASH).append(QUOTE);
                case '\n' -> out.append(BACKSLASH).append('n');
                case '\r' -> { /* dropped: a CR in a JSON string is as invalid as a newline */ }
                default -> out.append(each);
            }
        }
        return out.toString();
    }
}
