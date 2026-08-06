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
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
