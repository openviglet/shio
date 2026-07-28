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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.render.ShPageRenderService.ShRenderedPage;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH183 — <b>the whole point of Block P, asserted end to end.</b>
 *
 * <p>Before this, applying the {@code marketing-site} blueprint produced thirteen correct
 * pages and no way to look at one: preview minted a URL against {@code site.url} and 400'd
 * without it, and the Universal Editor is an {@code <iframe src={site.url}>}. Both
 * human-facing surfaces pointed outward at a front end this repository cannot start. This
 * test builds a site the way an agent does — a layout post, two region posts, a page post
 * — and asserts a person can see the result.</p>
 *
 * <p>The fixture is deliberately the 2018 shape: an {@code sh-region}-slotted layout, a
 * region whose {@code HTML} is a Handlebars template, and a {@code postTypeLayout} that is
 * the flat map {@code openviglet/bootstrap-site} actually shipped — <em>not</em> the
 * {@code ShSitePostTypeLayouts} array the entity can also express, which was built and, in
 * the one real example, never used.</p>
 *
 * <p>Its own database, for {@link com.viglet.shio.api.agent.ShAgentRoundTripTest}'s reason
 * (SH246): the system post-types it creates are instance-wide, and the SH164/SH239
 * benchmarks measure documents whose size depends on how many post-types exist.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-render;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShPageRenderTest {

    private static final String LAYOUT_TITLE = "BOOTSTRAP_LAYOUT";
    private static final String NAV_REGION = "BOOTSTRAP_NAV_REGION";
    private static final String LIST_REGION = "BOOTSTRAP_CONTENT_LIST_REGION";

    /** The layout's HTML field, in the shape the 2018 package holds it. */
    private static final String LAYOUT_HTML = """
            <html><head><title>{{post.title}}</title><style>{{theme.css}}</style></head>
            <body>
              <div sh-region="%s"></div>
              <main><h1>{{post.title}}</h1>{{{TEXT}}}</main>
              <div sh-region="%s"></div>
            </body></html>""".formatted(NAV_REGION, LIST_REGION);

    /**
     * What replaced 767 characters of hand-written JavaScript. The 2018 region called
     * {@code shObject.navigation(siteName, true)} and then looped over the result
     * remapping fields; the loop is the block body now.
     */
    private static final String NAV_HTML = """
            <nav>{{#navigation}}<a href="/{{name}}">{{name}}</a>{{else}}<span>no sections</span>{{/navigation}}</nav>""";

    private static final String LIST_HTML = """
            <ul>{{#query type="%s" limit=10}}<li><a href="{{link}}">{{title}}</a></li>{{/query}}</ul>""";

    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String articleType;

    @BeforeAll
    void setup() {
        siteName = "sh183-" + UUID.randomUUID().toString().substring(0, 8);
        articleType = "Sh183Article" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");

            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            // The flat map, which is what the one shipped example used.
            site.setPostTypeLayout("{\"" + articleType + "\":\"" + LAYOUT_TITLE + "\"}");
            entityManager.persist(site);

            ShFolder root = folder(site, null, "root", (byte) 1, when);
            folder(site, root, "guides", (byte) 0, when);
            folder(site, root, "reference", (byte) 0, when);

            ShPostType article = postType(articleType, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);

            post(site, root, themeType, "SITE_THEME", "/theme",
                    "{\"CSS\":\"body{font-family:sans-serif}\"}", when);
            post(site, root, layoutType, LAYOUT_TITLE, "/layout",
                    json("HTML", LAYOUT_HTML, "THEME", "SITE_THEME"), when);
            post(site, root, regionType, NAV_REGION, "/region-nav",
                    json("HTML", NAV_HTML, "JAVASCRIPT", "document.title += ' (nav)'"), when);
            post(site, root, regionType, LIST_REGION, "/region-list",
                    json("HTML", LIST_HTML.formatted(articleType)), when);

            post(site, root, article, "Welcome", "/", json("TEXT", "<p>Hello world</p>"), when);
            post(site, root, article, "Second", "/second", json("TEXT", "<p>Two</p>"), when);
        });
    }

    // ── the claim ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a site built over the agent surface renders as a page a person can see")
    void aPageRenders() {
        ShRenderedPage page = shPageRenderService.render(siteName, "/", ShPostState.DRAFT);

        assertThat(page.html())
                .as("the layout's own template resolved against the page's fields")
                .contains("<title>Welcome</title>")
                .contains("<h1>Welcome</h1>")
                .as("a rich-text field is markup, and the layout said so with a triple stash")
                .contains("<p>Hello world</p>")
                .as("the theme the layout names supplied the stylesheet")
                .contains("body{font-family:sans-serif}");
        assertThat(page.layout()).isEqualTo(LAYOUT_TITLE);
        assertThat(page.regions()).containsExactly(NAV_REGION, LIST_REGION);
    }

    @Test
    @DisplayName("regions compose into the page, and the helpers replaced the JavaScript")
    void regionsCompose() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("{{#navigation}} — what shObject.navigation plus a hand-written loop did")
                .contains("<a href=\"/guides\">guides</a>")
                .contains("<a href=\"/reference\">reference</a>");
        assertThat(html)
                .as("{{#query type=…}} — the content list, ordered and bounded by the helper")
                .contains("<a href=\"/second\">Second</a>");
    }

    @Test
    @DisplayName("the rendered DOM tells the Universal Editor which node is which region")
    void theRenderIsAnnotated() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        SH185's problem is that visual-editor.page.tsx posts into an iframe and \
                        nothing on the other side knows what a DOM node maps to. sh-region was \
                        already the contract in 2018; keeping it and adding the post id is what \
                        makes a region the edit unit rather than just the reuse unit.""")
                .contains("sh-region=\"" + NAV_REGION + "\"")
                .contains("data-shio-region=\"" + NAV_REGION + "\"")
                .contains("data-shio-region-post=");
    }

    @Test
    @DisplayName("SH248: a region's JavaScript reaches the page, and the JVM never runs it")
    void regionJavaScriptIsServedToTheBrowser() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        the Block D split is HTML on the server, JavaScript in the browser. SH183 \
                        shipped the first half and served the second nowhere, so the field \
                        accepted code and discarded its effect.""")
                .contains("<script data-shio-region=\"" + NAV_REGION + "\"")
                .contains("document.title += ' (nav)'");
    }

    @Test
    @DisplayName("rendering the same page twice produces the same bytes")
    void renderingIsDeterministic() {
        // P5, and the precondition for SH101's digest and SH102's snapshot meaning
        // anything: every listing the helpers yield is sorted by a total order, and the
        // model is built through ShStableMap (SH136) rather than Map.copyOf, whose
        // iteration order is derived from a per-JVM salt.
        assertThat(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html())
                .isEqualTo(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html());
    }

    // ── the refusals ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a page whose post-type has no layout binding is told which types have one")
    void anUnboundPostTypeIsATeachingError() {
        // The PageLayout post itself has no binding. Refusing beats the alternative — a
        // blank page — which is the failure mode that made the original engine expensive
        // to author against, and P6 says the refusal has to carry the way out.
        ShTeachingException thrown = catchTeaching(
                () -> shPageRenderService.render(siteName, "/layout", ShPostState.DRAFT));

        assertThat(thrown).isInstanceOf(ShPageRenderService.NoLayoutBindingException.class);

        ProblemDetail problem = thrown.toProblemDetail();
        assertThat(problem.getTitle()).contains("binds no layout for post-type");
        assertThat(problem.getProperties())
                .as("naming the post-types that *are* bound is what makes it actionable — "
                        + "the answer is usually a typo in postTypeLayout")
                .containsEntry("allowed", List.of(articleType));
        assertThat(problem.getProperties().get("fix").toString()).contains("postTypeLayout");
    }

    private ShTeachingException catchTeaching(Runnable action) {
        try {
            action.run();
        } catch (ShTeachingException e) {
            return e;
        }
        throw new AssertionError("expected a teaching refusal");
    }

    @Test
    @DisplayName("an unknown site and an unknown page each say what exists")
    void unknownTargetsTeach() {
        assertThatThrownBy(() -> shPageRenderService.render("no-such-site", "/", ShPostState.DRAFT))
                .isInstanceOf(ShPageRenderService.UnknownSiteException.class);
        assertThatThrownBy(() -> shPageRenderService.render(siteName, "/nope", ShPostState.DRAFT))
                .isInstanceOf(ShPageRenderService.UnknownPageException.class)
                .hasMessageContaining("/nope");
    }

    // ── SH192: a template fails when it is written ───────────────────────────────────

    @Test
    @DisplayName("SH192: an unclosed block is refused on write, with its position")
    void aBrokenTemplateIsRefusedOnWrite() {
        ShTeachingException thrown = catchTeaching(() ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(new ShAgentBatch.Op(
                        ShAgentBatchService.POST_UPSERT,
                        "post:" + siteName + "/broken-region", ShSystemPostType.REGION, "/",
                        objectMapper.valueToTree(Map.of("HTML", "<div>\n{{#each items}}\n</div>"))))),
                        true, "tester"));

        ProblemDetail problem = thrown.toProblemDetail();
        assertThat(problem.getTitle()).contains("will not compile");
        assertThat(problem.getDetail())
                .as("""
                        the alternative is a blank page discovered later, which costs a guess \
                        per save — and FSP printed the line, the column and a caret in 2019""")
                .contains("line 3, column ")
                .contains("^");
        assertThat(problem.getDetail()).contains("HTML:");
    }

    @Test
    @DisplayName("SH192: nothing is written when the template is refused")
    void aRefusedTemplateWritesNothing() {
        catchTeaching(() -> write(false, region("/never-written", "{{#each items}}")));

        assertThatThrownBy(() -> shPageRenderService.render(siteName, "/never-written",
                ShPostState.DRAFT))
                .as("the refusal is a refusal, not a warning attached to a saved post")
                .isInstanceOf(ShPageRenderService.UnknownPageException.class);
    }

    @Test
    @DisplayName("SH192: a plan reports the broken template instead of stopping on it")
    void aPlanReportsRatherThanStops() {
        // Same rule SH168's undeclared-field check follows: a plan that died on the first
        // bad field would hide the rest of the report, and the report is the reason to plan.
        ShAgentChangeSet planned = write(true, region("/planned-region", "{{#each items}}"));

        assertThat(planned.results().get(0).detail())
                .contains("invalid template")
                .contains("a real run will refuse this write");
    }

    @Test
    @DisplayName("SH192: an ordinary post's markup is content, not a template")
    void ordinaryPostsAreNotCompiled() {
        // An Article's TEXT can legitimately contain braces — a code sample, a price in a
        // currency nobody expected — and compiling it would reject perfectly good content.
        ShAgentChangeSet written = write(false, new ShAgentBatch.Op(
                ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/braces", articleType, "/",
                objectMapper.valueToTree(Map.of("TEXT", "<p>Use {{#each}} like this</p>"))));

        assertThat(written.results().get(0).action()).isEqualTo("create");
    }

    /** One batch, in its own transaction — the batch service expects to be inside one. */
    private ShAgentChangeSet write(boolean dryRun, ShAgentBatch.Op op) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(op)), true, "tester"));
    }

    private ShAgentBatch.Op region(String url, String html) {
        return new ShAgentBatch.Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + url,
                ShSystemPostType.REGION, "/", objectMapper.valueToTree(Map.of("HTML", html)));
    }

    // ── fixture helpers ──────────────────────────────────────────────────────────────

    private ShFolder folder(ShSite site, ShFolder parent, String name, byte root, Instant when) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder(root);
        // Only a root folder carries shSite — SH128's invariant, which the render model
        // resolves through ShFolderSite rather than by reading the field.
        folder.setShSite(root == 1 ? site : null);
        folder.setParentFolder(parent);
        folder.setDate(when);
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    private ShPostType postType(String name, byte system, Instant when) {
        ShPostType existing = entityManager
                .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                .setParameter("name", name)
                .getResultStream().findFirst().orElse(null);
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
        post.setData(data);
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }

    /** Minimal JSON writer for the fixture — pairs of key, value. */
    private String json(String... pairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(pairs[i]).append("\":\"").append(escape(pairs[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
