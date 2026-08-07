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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShPageRenderService.ShRenderedPage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

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
    /** SH476 — its own page, so SH464's counts on `/` are untouched by a fixture about escaping. */
    private static final String ENTITY_URL = "/entities";
    /** SH549 — a page that kept the source's markup, so it needs the source's stylesheet. */
    private static final String VERBATIM_URL = "/verbatim";
    /** SH559 — a page that is both, which is the shape the layout's title can least describe. */
    private static final String MIXED_URL = "/mixed";
    /** The sentinel the captured theme's CSS carries, so its presence on a page is unambiguous. */
    private static final String CAPTURED_RULE = ".sh549-captured-marker{color:#123456}";
    /**
     * SH555 — a rule with the one character a real stylesheet is full of and SH549's sentinel had
     * none of.
     *
     * <p>{@code .sh549-captured-marker{color:#123456}} carries no {@code "}, {@code &}, {@code <} or
     * {@code >}, so it survived HTML escaping unchanged and the assertion above passed on a page
     * whose every {@code url()} had been destroyed. That is the whole shape of the miss: the failure
     * was invisible in proportion to how ordinary the CSS was, and the fixture was the least
     * ordinary CSS there is. An {@code @font-face} is what a captured theme actually contains.</p>
     */
    private static final String CAPTURED_FONT =
            "@font-face{font-family:\"Inter\";src:url(\"/assets/inter.woff2\") format(\"woff2\")}";

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
         * <p>This exemption is where <b>SH471</b> was found, by writing it: a conversion stored the
         * source's own path in these fields rather than an id, so a converted {@code SectionHero}
         * rendered {@code <img src="">} and the two row fields here inherited it. That is fixed —
         * `convert` uploads the bytes on both paths now and stores the id — but the exemption stays,
         * because a sentinel string still is not an id and never can be. What replaces the coverage
         * it cannot give is {@link #theCliKnowsWhichFieldsAreFiles}: the fields skipped here are
         * exactly the ones that test requires the CLI to know about.</p>
         */
        boolean isRenderableAsText() {
            return !"File".equals(widget);
        }

        /**
         * A {@code Relator} is the <b>collection</b>, not a value — SH475's rule, one level up: the
         * indexed path addresses a row's leaf, and adding, removing or reordering rows is the
         * console form's job. Its own name is therefore not something a page can address.
         */
        boolean isList() {
            return "Relator".equals(widget);
        }

        /**
         * SH503 — the fields this coverage deliberately does not require, and the reason is the
         * <b>widget</b> rather than a list of names.
         *
         * <p>{@code align}, {@code variant} and {@code columns} are drawn into attributes like
         * {@code ctaHref} is, and they are {@code Combo Box} fields: a closed set of choices, while
         * the bridge's edit surface is a text box. A box in which three strings work and every other
         * one silently does nothing is worse than sending a curator to the console form, so they stay
         * the form's job in the same way adding a row does.</p>
         *
         * <p>A predicate rather than the hand-kept set this replaces, because that is what stops the
         * exemption from rotting (SH142): a presentation field that becomes free text stops being
         * exempt the moment its declaration changes, with nobody having to remember to come back.</p>
         */
        boolean isClosedChoice() {
            return "Combo Box".equals(widget);
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

            // SH549 — the head carries the per-page captured reference, read out of convert.mjs
            // rather than spelled here. This layout names no THEME, so anything that reaches a page
            // below came through that reference and through nothing else.
            post(site, theme, layoutType, LAYOUT_TITLE, "/theme/layout",
                    json("HTML", "<!doctype html><html><head><title>{{post.title}}</title>"
                            + capturedStyleHtml()
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

            // SH549 — the captured `Theme` and a page that kept the source's markup. The layout above
            // names no theme at all, which is what makes this fixture the honest one for the
            // question: what reaches the page here can only have come through the reference in the
            // head, and not from `{{theme.css}}`.
            post(site, theme, postType(ShSystemPostType.THEME, (byte) 1, when),
                    siteName + " (captured)", "/theme/captured",
                    json("TITLE", siteName + " (captured)", "CSS", CAPTURED_RULE + CAPTURED_FONT),
                    when);
            post(site, root, pageType, "Verbatim", VERBATIM_URL,
                    "{\"title\":\"Verbatim\",\"content\":\"<section class=\\\"features\\\">"
                            + "<p>Kept</p></section>\"}",
                    when);
            // SH559 — the third shape, on a page of its own so SH464's counts on `/` are untouched.
            // One layout serves all three since SH464, which is exactly why its title cannot say
            // which one a reader is looking at.
            post(site, root, pageType, "Mixed", MIXED_URL,
                    "{\"title\":\"Mixed\",\"content\":\"<section class=\\\"features\\\">"
                            + "<p>Kept</p></section>\",\"sections\":[\"/sections/prose\"]}",
                    when);

            // SH476 — the render half of the round trip: a field holding the characters the source
            // meant, on a page of its own so SH464's counts on `/` are untouched.
            post(site, sections, heroType, "Ampersand", ENTITY_URL + "/hero",
                    json("heading", "rerank & media models"), when);
            ObjectNode entities = MAPPER.createObjectNode();
            entities.put("title", "Entities");
            entities.set("sections", MAPPER.createArrayNode().add(ENTITY_URL + "/hero"));
            post(site, root, pageType, "Entities", ENTITY_URL, entities.toString(), when);
        });
    }

    /**
     * SH476 — the other half of the seam, and the reason it is asserted here rather than in the CLI.
     *
     * <p>`propose` decodes an entity when text becomes a field; this renderer escapes what it is
     * given. Each is right alone, and the defect was the pair: the field held the five characters
     * `&amp;` and the page showed `&amp;amp;`, so a reader saw the entity and the editor field held
     * it too — content a curator cannot repair through the surface built for repairing content.
     * Asserting both ends in one language would be a copy of one side checked against itself, which
     * is the SH245 mistake this test class exists to avoid.</p>
     */
    @Test
    @DisplayName("SH476: a stored `&` is escaped exactly once, which is what makes the round trip close")
    void anAmpersandInAFieldIsEscapedOnce() {
        String html = shPageRenderService.render(siteName, ENTITY_URL, ShPostState.DRAFT).html();

        assertThat(html)
                .as("what the source spelled, byte for byte — the field holds the character and the "
                        + "renderer writes the entity")
                .contains("rerank &amp; media models");
        assertThat(html)
                .as("""
                        and NOT the doubled form, which is what a page reads when the capture \
                        stored `&amp;` as five characters: every re-capture escapes what the last \
                        one stored, so it compounds rather than staying at one layer""")
                .doesNotContain("&amp;amp;");
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

    /**
     * SH575 — a site of pages with no way between them, asserted on the page a visitor gets.
     *
     * <h3>What was measured</h3>
     *
     * <p>An authorable conversion keeps what the vocabulary matched and drops the rest, which is the
     * design; the source's navigation lives in <em>the rest</em>, and nothing put one back. On
     * viglet.org converted with {@code --accept all}: {@code shio diff} reported
     * {@code internal links: 16 → 0} on all twelve pages, {@code audit} reported {@code no-landmark},
     * and {@code verify} exited <b>0</b> — twelve pages, none of which a reader can leave, and every
     * gate green. Each instrument measures the page it is given, and a page with no links is a valid
     * page.
     *
     * <h3>Why the assertion is here and not only in the CLI</h3>
     *
     * <p>{@code convert.test.mjs} asserts the template <em>says</em> {@code {{#query type="Page"}}};
     * that is a statement about a string. Whether those rows exist is a question only this side can
     * answer — {@code {{#query}}} with no {@code folder=} resolves the <b>rendering post's own</b>
     * folder, and a conversion writes its pages into the site root ({@code PAGES_FOLDER} is
     * {@code "/"}) with {@code sections} and {@code theme} beside them. A template that compiled and
     * listed nothing would pass every check in the other language, which is the SH245 shape this
     * class exists to avoid.
     *
     * <h3>And why {@code {{#query}}} rather than {@code {{#navigation}}}</h3>
     *
     * <p>XVII.80 left the choice open and named {@code {{#navigation}}} as the cheapest. It is the
     * wrong helper for this tree: it yields the site's top-level <b>folders</b>, so a replica's nav
     * would read <i>sections, theme</i> — and link to neither, since a folder row's {@code url} is
     * its index post (SH381) and neither folder has one. The pages are posts in the root, and
     * {@code {{#query}}} is the helper that lists posts.
     */
    @Test
    @DisplayName("SH575: an authorable replica is a site you can move around in")
    void anAuthorablePageCarriesTheSitesOwnNavigation() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the landmark `audit` reported missing, with the accessible name that tells it "
                        + "apart from a nav the source's own markup may also carry")
                .contains("<nav class=\"shio-nav\" aria-label=\"Site\">");
        assertThat(html)
                .as("""
                        and it lists the site's other pages, by their friendly URLs. This is the \
                        half a template assertion cannot make: `{{#query}}` with no `folder=` \
                        resolves the rendering post's own folder, and if that lookup came back \
                        empty the nav would render as an empty <ul> with every CLI check still \
                        green.""")
                .contains("href=\"" + VOCABULARY_URL + "\"")
                .contains("href=\"" + ENTITY_URL + "\"")
                .contains("href=\"" + MIXED_URL + "\"");
        assertThat(html.indexOf(">Entities<"))
                .as("alphabetical by title, not newest-first: a menu whose items move when somebody "
                        + "edits a page is a control people misclick, which is the same argument "
                        + "`{{#translations}}` makes for ordering a switcher by locale")
                .isLessThan(html.indexOf(">Vocabulary<"));
    }

    /**
     * SH575 — and the page that must <b>not</b> get one, which is the whole reason for the guard.
     *
     * <p>SH549's precedent: {@code content} holds the source's own markup, so
     * {@code {{#unless content}}} is the per-page condition separating a page that <em>lost</em> its
     * navigation from one that still has the source's. A fidelity replica is judged at 0.00% against
     * its source by {@code snapshot --against} — that bar has been reached, it is the mode's whole
     * claim — and a Shio nav above the source's own would put it out of reach for the one mode that
     * had nothing wrong with it.
     */
    @Test
    @DisplayName("SH575: a page that kept the source's markup keeps the source's navigation too")
    void averbatimPageIsNotGivenASecondNavigation() {
        assertThat(shPageRenderService.render(siteName, VERBATIM_URL, ShPostState.DRAFT).html())
                .as("a fidelity page already carries whatever the source's <body> had, nav included, "
                        + "and a second one would be both a duplicate landmark and a pixel the "
                        + "0.00% comparison can never get back")
                .doesNotContain("shio-nav");
        assertThat(shPageRenderService.render(siteName, MIXED_URL, ShPostState.DRAFT).html())
                .as("and a mixed page is on the same side of the line: SH520 keeps the source's "
                        + "bytes around the blocks that were accepted, so its chrome is still there")
                .doesNotContain("shio-nav");
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

    /**
     * SH503 — the check the other three could not make, and the hole they left between them.
     *
     * <h2>What was counted and what was not</h2>
     *
     * <p>SH465 counts the fields the page <b>draws</b> and SH475 the row fields it <b>addresses</b>,
     * and {@code ctaHref} fell between the two: it is drawn — into the {@code href} of the link
     * whose text is {@code ctaLabel}, which is why its sentinel is on the page and SH465 was green
     * — and it was addressed by nothing, while SH475's loop skips a path with no dot in it. So the
     * count that mattered was never taken: <b>9 of the vocabulary's 10 filled fields</b> carried an
     * annotation, and the missing one was the destination of the call to action. A curator could
     * rename the button and not repoint it.</p>
     *
     * <h2>Why the exemption is a predicate and not a list</h2>
     *
     * <p>What this replaces is a hand-kept {@code Set.of("features.icon", "logos.href")}, which is
     * the shape SH142 warns about: it named the two row fields somebody had noticed and could not
     * name the scalar nobody had. The rule is now asked of the <b>declaration</b> — a
     * {@code Combo Box} is a closed set of choices and stays the console form's job, a
     * {@code Relator} is the list rather than a value, and <b>everything else must be addressable
     * in one of the two forms</b>. A field the model gains fails here by name.</p>
     */
    @Test
    @DisplayName("SH503: every declared field the layout fills is addressable, as text or as an attribute")
    void everyDeclaredFieldIsAddressable() {
        String html = shPageRenderService.render(siteName, VOCABULARY_URL, ShPostState.DRAFT).html();
        Set<String> addressed = addressedFields(html);

        List<String> checked = new ArrayList<>();
        List<String> unaddressable = new ArrayList<>();
        for (ShSectionType type : VOCABULARY) {
            for (ShSectionField field : fieldsOf(type)) {
                if (field.isList() || field.isClosedChoice()) {
                    continue;
                }
                String prop = addressOf(field.path());
                checked.add(type.name() + " → " + prop);
                if (!addressed.contains(prop)) {
                    unaddressable.add(type.name() + " → " + prop);
                }
            }
        }

        assertThat(checked)
                .as("a predicate that excluded everything would make the next assertion pass over "
                        + "nothing, which is the state SH465's own exemption list was drifting into")
                .isNotEmpty()
                .contains("SectionHero → ctaHref", "SectionFeatureGrid → features.0.icon",
                        "SectionLogoWall → logos.0.href");
        assertThat(unaddressable)
                .as("""
                        `sh-field` declares what an element's CONTENT is, so one element declared \
                        one field — and a link is two of them, its text and its target. The value \
                        was captured, stored on the section and rendered into the page; only the \
                        declaration was absent, which is the one shape every other instrument reads \
                        as working: the field is in the post, on the page, and editable in the \
                        console form. `sh-field-attr="FIELD:ATTRIBUTE"` is where the second one \
                        goes.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH503: and the destination is addressed as an attribute, which is the mechanism")
    void anAttributeFieldIsDeclaredOnTheElementThatDrawsIt() {
        String html = shPageRenderService.render(siteName, VOCABULARY_URL, ShPostState.DRAFT).html();

        assertThat(attributeAddressedFields(html))
                .as("""
                        addressed in the form that says which attribute holds the value, because \
                        that is what the bridge reads it out of and writes it back into. The \
                        alternative SH503 rejected was wrapping the anchor so the text and the href \
                        each own an element: a link is one thing to a curator, and splitting it into \
                        two boxes to satisfy the markup is the tail wagging the dog.""")
                .contains("ctaHref", "features.0.icon", "logos.0.href");
        assertThat(html)
                .as("the pair keeps the attribute name, and the renderer copies it verbatim — it "
                        + "has never parsed a prop and this does not make it start")
                .contains("data-shio-prop-attr=\"ctaHref:href\"");
    }

    @Test
    @DisplayName("SH475: every collection row field is addressed by an indexed path, and the page carries it")
    void aCollectionRowIsAddressedByAnIndexedPath() {
        String html = shPageRenderService.render(siteName, VOCABULARY_URL, ShPostState.DRAFT).html();

        assertThat(html)
                .as("the rows are still marked with the collection they came from, which the path "
                        + "repeats but a stylesheet cannot read out of an sh-field")
                .contains("data-shio-row=\"features\"")
                .contains("data-shio-row=\"questions\"")
                .contains("data-shio-row=\"logos\"")
                .contains("data-shio-row=\"quotes\"");

        assertThat(misroutedRowFields())
                .as("""
                        asked of every {{#each}} block in the template rather than of the four \
                        names, so the next collection inherits the rule. A row is NOT a post — an \
                        SH95 Relator declares nested fields, so the value is an inline array of row \
                        maps in the section's own data with no id to put in an sh-post — and a flat \
                        `sh-field="body"` inside `<section sh-post="{{id}}">` would write data.body \
                        onto a SectionFeatureGrid that declares no such field, while `title` would \
                        rename the post. So the address is `<collection>.{{@index}}.<leaf>`, rooted \
                        at the block it sits in, and anything else here is an edit routed at the \
                        wrong value.""")
                .isEmpty();

        List<String> missing = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        Set<String> asAttribute = attributeDeclaredFields();
        for (ShSectionType type : VOCABULARY) {
            for (ShSectionField field : fieldsOf(type)) {
                int dot = field.path().indexOf('.');
                String prop = dot < 0 ? field.path() : addressOf(field.path());
                // SH503 — a row field the template draws into an attribute is declared there
                // instead, and asked of the template rather than of a list of names this file keeps:
                // the two entries that list held were the ones somebody had noticed.
                if (dot < 0 || !field.isRenderableAsText() || asAttribute.contains(prop)) {
                    continue;
                }
                checked.add(type.name() + " → " + prop);
                if (!html.contains("data-shio-prop=\"" + prop + "\"")) {
                    missing.add(type.name() + " → " + prop);
                }
            }
        }
        assertThat(checked)
                .as("a filter that excluded everything would make the next assertion pass against "
                        + "a page with no rows on it at all")
                .isNotEmpty();
        assertThat(missing)
                .as("""
                        SH465's coverage check, one level down: it counted the fields DRAWN against \
                        what the type declares and found four collections rendering as a heading. \
                        This counts the fields ADDRESSABLE, inside a collection too — the gap it \
                        closes is a page whose two feature cards a curator can read and cannot \
                        edit, on the half of the vocabulary that holds the actual content.""")
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
     * SH471 — the CLI's `File`-field list is the blueprints' own, and this is what says so.
     *
     * <h2>Why the CLI has a second copy at all</h2>
     *
     * <p>`propose` runs on a machine that has not installed the model — a capture happens before the
     * site exists — so `SECTION_TYPES` is a deliberate duplication of `@viglet/shio-sections`, and
     * `SECTION_FILE_FIELDS` is the same duplication for a different fact: which of those fields the
     * blueprint declares as a `File` widget. `convert` needs it to store the <b>id</b> of the post
     * holding the bytes rather than the source path, which is what stopped a converted section from
     * rendering {@code <img src="">}.</p>
     *
     * <h2>And why a duplication is allowed to exist here</h2>
     *
     * <p>Because this closes it. A `File` field the blueprints gain and that list does not would go
     * back to storing a path, silently — the render would be an empty `src` again, and the coverage
     * assertion above <em>exempts</em> `File` fields, so nothing else in this file would notice. The
     * exemption is what makes this test owed.</p>
     */
    @Test
    @DisplayName("SH471: every blueprint File field is named in the CLI's SECTION_FILE_FIELDS")
    void theCliKnowsWhichFieldsAreFiles() {
        String declared = sectionFileFieldsSource();

        List<String> fileFields = VOCABULARY.stream()
                .flatMap(type -> fieldsOf(type).stream()
                        .filter(field -> "File".equals(field.widget()))
                        .map(field -> type.name() + "." + field.path()))
                .distinct()
                .sorted()
                .toList();
        assertThat(fileFields)
                .as("the exemption in isRenderableAsText covers something, or this test proves "
                        + "nothing about a list that could be empty and still pass")
                .isNotEmpty();

        for (String each : fileFields) {
            String type = each.substring(0, each.indexOf('.'));
            String leaf = each.substring(each.lastIndexOf('.') + 1);
            assertThat(declared)
                    .as("%s is a File field in the blueprints; convert must store an id for it, and "
                            + "it can only know that from SECTION_FILE_FIELDS", each)
                    .contains(type + ":")
                    .contains("\"" + leaf + "\"");
        }
    }

    /**
     * SH582 — the CLI's required-field list is the blueprints' own, and this is what says so.
     *
     * <h2>What the duplication costs when it drifts</h2>
     *
     * <p>{@code propose --accept all} on a real source accepted fifteen of twenty-one candidates, and
     * three of them became sections whose {@code heading} — {@code required: true} here — is empty, so
     * {@code shio verify} exited 1 with three {@code required-field-empty} errors on a replica that
     * was otherwise the best that chain had produced. The proposal now declines a candidate whose
     * derivation cannot fill a required field, and it can only know which fields those are from
     * {@code SECTION_REQUIRED} — a third deliberate copy of the model, for the reason
     * {@code SECTION_TYPES} is the first: {@code propose} runs before the model exists.</p>
     *
     * <h2>Both directions, unlike the File check above</h2>
     *
     * <p>A blueprint that makes a field required and a list that does not know it writes content the
     * gate refuses — the defect itself. A list naming a field the blueprint stopped requiring declines
     * a candidate that was appliable, which is the same drift making the conversion quietly smaller.
     * So the sets are compared, not searched.</p>
     */
    @Test
    @DisplayName("SH582: the CLI's SECTION_REQUIRED is exactly the blueprints' required fields")
    void theCliKnowsWhichFieldsAreRequired() {
        Map<String, List<String>> declared = sectionRequiredSource();

        assertThat(declared)
                .as("SECTION_REQUIRED is what --accept all reads to decline an incomplete candidate; "
                        + "an empty parse would make every assertion below pass over nothing")
                .isNotEmpty();

        for (ShSectionType type : VOCABULARY) {
            List<String> required = new ArrayList<>();
            for (JsonNode field : type.declaration().path("fields")) {
                if (field.path("required").asBoolean(false)) {
                    required.add(field.path("name").asString(""));
                }
            }
            Collections.sort(required);
            assertThat(declared.getOrDefault(type.name(), List.of()))
                    .as("%s declares %s as required in the %s blueprint; cli/src/propose.mjs must "
                            + "agree, or a conversion either writes what the gate refuses or declines "
                            + "a candidate it could have applied",
                            type.name(), required, type.blueprint())
                    .containsExactlyElementsOf(required);
        }
    }

    /**
     * The body of `SECTION_REQUIRED` in `cli/src/propose.mjs`, parsed rather than remembered.
     *
     * <p>Parsed into a map instead of searched as text, which is what lets the assertion above run in
     * both directions: a substring check can only ever notice an entry that is missing.</p>
     */
    private static Map<String, List<String>> sectionRequiredSource() {
        String body = exportBody("export const SECTION_REQUIRED = {");
        Map<String, List<String>> out = new LinkedHashMap<>();
        Matcher rows = Pattern.compile("(\\w+):\\s*\\[([^]]*)]").matcher(body);
        while (rows.find()) {
            List<String> names = new ArrayList<>();
            Matcher each = Pattern.compile("\"([^\"]+)\"").matcher(rows.group(2));
            while (each.find()) {
                names.add(each.group(1));
            }
            Collections.sort(names);
            out.put(rows.group(1), List.copyOf(names));
        }
        return out;
    }

    /** The body of `SECTION_FILE_FIELDS` in `cli/src/propose.mjs`, read rather than remembered. */
    private static String sectionFileFieldsSource() {
        return exportBody("export const SECTION_FILE_FIELDS = {");
    }

    /** One `export const NAME = {` block of `cli/src/propose.mjs`, read rather than remembered. */
    private static String exportBody(String marker) {
        Path propose = Path.of("..", "cli", "src", "propose.mjs");
        String source;
        try {
            source = Files.readString(propose, StandardCharsets.UTF_8);
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(
                    "cli/src/propose.mjs declares what the CLI knows about the section model; this "
                            + "test reads it rather than keeping a third copy (SH245).", cannotRead);
        }
        int start = source.indexOf(marker);
        assertThat(start)
                .as("%s is the export this test reads; renamed or inlined, the duplication it "
                        + "closes has no reader left", marker)
                .isGreaterThanOrEqualTo(0);
        int end = source.indexOf("\n};", start);
        assertThat(end).isGreaterThan(start);
        return source.substring(start, end);
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
                String name = tree.path("name").asString("");
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
            String name = field.path("name").asString("");
            if (name.isBlank()) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "." + name;
            into.add(new ShSectionField(path, field.path("widget").asString("")));
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
            String name = field.path("name").asString("");
            if (name.isBlank()) {
                continue;
            }
            JsonNode nested = field.path("fields");
            if (nested.isArray() && !nested.isEmpty()) {
                ObjectNode row = MAPPER.createObjectNode();
                for (JsonNode child : nested) {
                    String childName = child.path("name").asString("");
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
     * The address the page would carry for a declared field's path: a row's index is always 0 here,
     * because {@link #sentinelData} composes one row per collection.
     */
    private static String addressOf(String path) {
        int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot) + ".0." + path.substring(dot + 1);
    }

    /**
     * Every field the <b>rendered page</b> addresses, in either of SH185's two forms.
     *
     * <p>Parsed out of the attribute values rather than asked with {@code contains}: a pair is one
     * of a space-separated list, and a substring check would answer yes for {@code ctaHref} on a
     * page whose only mention of it is inside {@code ctaHrefLegacy}.</p>
     */
    private static Set<String> addressedFields(String html) {
        Set<String> found = new java.util.TreeSet<>(attributeAddressedFields(html));
        Matcher text = Pattern.compile("data-shio-prop=\"([^\"]*)\"").matcher(html);
        while (text.find()) {
            found.add(text.group(1));
        }
        return found;
    }

    /** SH503 — only the half addressed as an attribute, so the mechanism can be asserted alone. */
    private static Set<String> attributeAddressedFields(String html) {
        return fieldsOfPairs(Pattern.compile("data-shio-prop-attr=\"([^\"]*)\"").matcher(html));
    }

    /**
     * SH503 — the field half of every {@code sh-field-attr} pair the <b>template</b> declares, with
     * {@code {{@index}}} resolved to the row the fixture composes.
     */
    private static Set<String> attributeDeclaredFields() {
        Set<String> resolved = new java.util.TreeSet<>();
        for (String declared : fieldsOfPairs(attrPairsIn(pageBodyHtml()))) {
            resolved.add(declared.replace("{{@index}}", "0"));
        }
        return resolved;
    }

    /** Every {@code sh-field-attr} value in a stretch of template or rendered markup. */
    private static Matcher attrPairsIn(String markup) {
        return Pattern.compile(ShRegionComposer.ATTR_FIELD_ATTR + "=\"([^\"]*)\"").matcher(markup);
    }

    /** The {@code FIELD} of every {@code FIELD:ATTRIBUTE} pair in every match of a group-1 pattern. */
    private static Set<String> fieldsOfPairs(Matcher matcher) {
        Set<String> fields = new java.util.TreeSet<>();
        while (matcher.find()) {
            for (String pair : matcher.group(1).trim().split("\\s+")) {
                int colon = pair.indexOf(':');
                if (colon > 0) {
                    fields.add(pair.substring(0, colon));
                }
            }
        }
        return fields;
    }

    /**
     * Every {@code sh-field} inside an {@code {{#each}}} block that is not that block's own indexed
     * path (SH475) — a block with none at all included, which is the SH465 state.
     *
     * <p>The collection's name is the reported unit, which is what makes a failure readable: the
     * answer to "which collection is addressed wrongly" is the collection.</p>
     */
    private static List<String> misroutedRowFields() {
        String body = pageBodyHtml();
        List<String> wrong = new ArrayList<>();
        Matcher block = Pattern.compile("\\{\\{#each (\\w+)}}(.*?)\\{\\{/each}}", Pattern.DOTALL)
                .matcher(body);
        while (block.find()) {
            String collection = block.group(1);
            Pattern indexed = Pattern.compile(
                    "^" + Pattern.quote(collection) + "\\.\\{\\{@index}}\\.\\w+$");
            Matcher field = Pattern.compile(ShRegionComposer.FIELD_ATTR + "=\"([^\"]*)\"")
                    .matcher(block.group(2));
            int found = 0;
            while (field.find()) {
                found++;
                if (!indexed.matcher(field.group(1)).matches()) {
                    wrong.add(collection + ": " + field.group(1));
                }
            }
            // SH503 — and the fields the row draws into its attributes, which are addressed by the
            // same path and can be misrouted in the same way. Asked here rather than in a test of
            // its own: the question is the block's, and a second place to ask it is a second place
            // for the next collection to be forgotten.
            for (String attrField : fieldsOfPairs(attrPairsIn(block.group(2)))) {
                found++;
                if (!indexed.matcher(attrField).matches()) {
                    wrong.add(collection + ": " + attrField);
                }
            }
            if (found == 0) {
                wrong.add(collection + ": no row field is addressable at all");
            }
        }
        return wrong;
    }

    /**
     * SH549 — the half of the seam only the JVM can answer.
     *
     * <h2>What was wrong, and why the fix needed a check on this side</h2>
     *
     * <p>{@code authorable} was {@code verbatim === 0 && mixed === 0}, a <b>site-wide</b> switch, so
     * one block the vocabulary could not name flipped it for every page: measured on viglet.org,
     * twelve pages each carrying 1,062,146 characters of the source's CSS, defining none of the
     * {@code shio-*} classes the section templates draw, with {@code page-unstyled} on ten of them. A
     * real site always has such a block, so that was the normal case and not the rare one.</p>
     *
     * <p>The fix names the vocabulary theme whenever any page composes sections, and reaches the
     * pages that kept verbatim markup with the captured CSS <b>per page</b> —
     * {@code {{#if content}}{{#getRelation "/theme/captured"}}}. That template is written in
     * JavaScript and rendered here, and it turns on something no JS test can check: whether a
     * <b>literal</b> first argument resolves at all, when {@code getRelation}'s declared shape
     * ({@code BLOCK_PATH_ARG}) is a field <em>path</em>. A silent {@code {{else}}} would be a page
     * with no stylesheet and a suite that says the fix landed.</p>
     */
    @Test
    @DisplayName("SH549: a verbatim page gets the captured CSS through the layout's own reference")
    void aVerbatimPageResolvesTheCapturedThemeByLiteralReference() {
        String html = shPageRenderService.render(siteName, VERBATIM_URL, ShPostState.DRAFT).html();

        assertThat(html)
                .as("a literal argument to {{#getRelation}} resolves — the whole reason this "
                        + "assertion is in the JVM and not beside the template that spells it")
                .contains(CAPTURED_RULE);
        assertThat(html)
                .as("and it is what a page carrying the source's class names needs, since the layout "
                        + "names the vocabulary theme for the pages that compose sections")
                .contains("class=\"features\"");
    }

    /**
     * SH555 — the captured stylesheet reaches the browser as CSS, not as escaped text.
     *
     * <h2>One brace between a stylesheet and a browser</h2>
     *
     * <p>SH549's per-page emission reads a plain field inside a relation block, so Handlebars
     * escaped it. The stored {@code Theme} was clean — 1,061,481 characters, zero entities — and the
     * page it produced carried 378, including
     * {@code src:url(&quot;/assets/inter-cyrillic-ext-wght-normal-BOeWTOD4.woff2&quot;)}. Not
     * cosmetic: {@code verify} reported ten {@code page-reference-broken} errors on the one verbatim
     * page of an otherwise clean site, each naming a path beginning with a literal {@code &quot;},
     * and the gate exited 1. Every {@code @font-face}, every quoted {@code content:} and every
     * quoted {@code url()} in a captured stylesheet was affected, so a mixed conversion's verbatim
     * pages lost their webfonts and their background images while rendering 200 with a megabyte of
     * CSS attached.</p>
     *
     * <p>The fidelity path was the contrast that located it: it emits the same theme through the
     * layout's own {@code theme.css}, which SH461 already made a {@code SafeString}, and served
     * sixty clean {@code url()} with no entity at all. So the defect was in neither the capture, the
     * store nor the escaping rule — one of the two emissions was written as a field read, and
     * {@code ShRenderHelpers.rawTextFields} is that emission getting SH461's answer.</p>
     */
    @Test
    @DisplayName("SH555: the captured stylesheet reaches the page unescaped, url() quotes intact")
    void aCapturedStylesheetIsNotEntityEscapedOnTheWayToThePage() {
        String html = shPageRenderService.render(siteName, VERBATIM_URL, ShPostState.DRAFT).html();

        assertThat(html)
                .as("`<style>` is a raw-text element: entity escaping is not the encoding for that "
                        + "context, so what it removed was correctness. A path beginning `&quot;` is "
                        + "the ten `page-reference-broken` errors that failed the gate")
                .contains(CAPTURED_FONT)
                .doesNotContain("&quot;/assets/inter.woff2&quot;");
        assertThat(html)
                .as("and the whole of the theme survived, not just the half without quotes — "
                        + "SH549's sentinel passed on a page whose every url() had been destroyed")
                .contains(CAPTURED_RULE);
    }

    /**
     * SH559 — the banner reports how the page is made, and stops inferring it from a name.
     *
     * <h2>The label that stopped describing what it labels</h2>
     *
     * <p>The fixture is the measured situation exactly: one layout, titled
     * {@code Sh464 (fidelity)}, serving an authorable page at {@code /}, a verbatim one and a mixed
     * one. On the real site — eleven pages of section lists, a home page carrying sixteen annotated
     * fields — {@code layout <site> (fidelity)} was the only statement anywhere about how the page
     * had been produced, and it named the other mode. The name was accurate when one layout served
     * one mode; SH464 made a single generated layout serve all three and the title kept the word it
     * was born with. Nothing downstream reads it, which is why it survived.</p>
     *
     * <p>It cost a wrong inference in the work that found it: reading {@code layout R6Aut
     * (fidelity)} beside a page that had lost its styling, the first hypothesis was that an
     * authorable conversion had been handed a fidelity layout, and the real cause — a site-wide
     * theme switch — took a detour to find. <b>A label that is wrong is worse than no label</b>,
     * because it is evidence and it points somewhere.</p>
     */
    @Test
    @DisplayName("SH559: the mode comes from the page, on the three shapes one layout serves")
    void theCompositionIsReadFromThePageAndNotFromTheLayoutName() {
        assertThat(LAYOUT_TITLE)
                .as("the premise: the one layout all three of these render through claims a mode "
                        + "in its title, and it is the wrong one for two of them")
                .contains("fidelity");

        assertThat(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).composition())
                .as("""
                        a page whose `sections` compose it is authorable, whatever its layout is \
                        called — this is the exact string that read `fidelity` on eleven pages.""")
                .isEqualTo("authorable");
        assertThat(shPageRenderService.render(siteName, VERBATIM_URL, ShPostState.DRAFT).composition())
                .isEqualTo("verbatim");
        assertThat(shPageRenderService.render(siteName, MIXED_URL, ShPostState.DRAFT).composition())
                .as("both fields, which is the shape no single word in a layout title can carry")
                .isEqualTo("mixed");
    }

    @Test
    @DisplayName("SH559: the banner states the mode and quotes the layout title as a name")
    void theBannerStatesTheModeItReadFromThePage() {
        ShRenderedPage page = shPageRenderService.render(siteName, "/", ShPostState.DRAFT);

        String banner = new ShPreviewBanner().wrap(page);

        assertThat(banner)
                .as("the fact, read from the page's own fields — the same two the generated layout "
                        + "branches on, so the banner and the page cannot disagree (SH281's rule)")
                .contains("authorable page");
        assertThat(banner)
                .as("""
                        and the title is still there, because "which layout rendered this" is a \
                        real question — but quoted, which marks it as a name rather than a claim. \
                        Renaming it is a `postTypeLayout` key change and therefore a migration.""")
                .contains("layout \"" + LAYOUT_TITLE + "\"");
    }

    @Test
    @DisplayName("SH549: an authorable page receives no captured CSS, which is the payload SH510 stopped")
    void anAuthorablePageIsUntouchedByTheCapturedReference() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the condition is the page's own `content`, so the megabyte reaches only the "
                        + "pages that cannot render without it. This is the assertion that keeps "
                        + "SH510's result: an authorable page is byte-identical to before")
                .doesNotContain(CAPTURED_RULE);
        assertThat(html)
                .as("and it is still the page it was")
                .contains("Ship faster");
    }

    /**
     * {@code CAPTURED_STYLE_HTML} from {@code cli/src/convert.mjs} — the per-page reference, read.
     *
     * <p>A single-quoted constant with no interpolation, for exactly this reason (SH464's seam): a
     * copy here would be a second spelling of the one string whose resolution is under test.</p>
     */
    private static String capturedStyleHtml() {
        String source = convertSource();
        String marker = "export const CAPTURED_STYLE_HTML =";
        int start = source.indexOf(marker);
        assertThat(start)
                .as("CAPTURED_STYLE_HTML is SH549's per-page reference; if it was renamed or "
                        + "interpolated, the contract it pins has no reader left")
                .isGreaterThanOrEqualTo(0);
        int open = source.indexOf('\'', start);
        int close = source.indexOf('\'', open + 1);
        assertThat(close).as("the string literal is unterminated").isGreaterThan(open);
        return source.substring(open + 1, close);
    }

    /**
     * {@code PAGE_BODY_HTML} from {@code cli/src/convert.mjs} — a slice between two backticks.
     *
     * <p>A missing file is a <b>failure</b> and never a skip: this test exists to assert a
     * cross-language contract, and one that quietly stops checking it is worse than none.</p>
     */
    private static String pageBodyHtml() {
        String source = convertSource();
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

    /** The file both readers above slice, read once. */
    private static String convertSource() {
        try {
            return Files.readString(CONVERT, StandardCharsets.UTF_8);
        } catch (IOException cannotRead) {
            throw new UncheckedIOException(
                    "cli/src/convert.mjs is where the layout is written; this test reads it rather "
                            + "than keeping a second copy (SH245). If it moved, move this with it.",
                    cannotRead);
        }
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
