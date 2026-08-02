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
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
    private ShPreviewLinks previewLinks;
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

    // ── SH278: which row of a template renders, and in what order ───────────────────

    @Test
    @DisplayName("SH278: a draft preview renders the draft layout, not the published one")
    void aPreviewRendersTheDraftTemplate() {
        // The defect: `postOfSystemType` read both states and took whichever row the database
        // returned, with no DRAFT preference — so editing a layout and previewing the page could
        // show the *published* layout. Exactly the failure SH247 extracted `preferDraft` to prevent
        // for listings, in the one place nobody applied it. A curator's whole reason for opening a
        // preview is to see the change they have not published yet.
        String site = "sh278-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh278Page" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH278_LAYOUT";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"" + layoutTitle + "\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            // One layout, two rows: published says LIVE, the unpublished draft says DRAFTED.
            String layoutId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(layoutId);
                row.setState(state);
                row.setShSite(owner);
                row.setShFolder(root);
                row.setShPostType(layoutType);
                row.setTitle(layoutTitle);
                row.setFurl("/l");
                row.setData(json("HTML", "<html><body><h1>"
                        + (state == ShPostState.PUBLISHED ? "LIVE" : "DRAFTED")
                        + "</h1>{{{TEXT}}}</body></html>"));
                row.setDate(when);
                row.setOwner("admin");
                entityManager.persist(row);
            }
            post(owner, root, pageType, "Home", "/", json("TEXT", "<p>body</p>"), when);
        });

        assertThat(shPageRenderService.render(site, "/", ShPostState.DRAFT).html())
                .as("a preview shows the layout the curator is editing")
                .contains("DRAFTED")
                .doesNotContain("LIVE");
    }

    @Test
    @DisplayName("SH278: a published render never picks up an unpublished template edit")
    void aPublishedRenderIgnoresTheDraftTemplate() {
        // The other direction, and the one that would be a live incident: what a visitor gets must
        // not depend on a layout edit nobody has published.
        String site = "sh278-pub-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh278Pub" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH278_PUB_LAYOUT";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"" + layoutTitle + "\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            String layoutId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(layoutId);
                row.setState(state);
                row.setShSite(owner);
                row.setShFolder(root);
                row.setShPostType(layoutType);
                row.setTitle(layoutTitle);
                row.setFurl("/l");
                row.setData(json("HTML", "<html><body><h1>"
                        + (state == ShPostState.PUBLISHED ? "LIVE" : "DRAFTED")
                        + "</h1>{{{TEXT}}}</body></html>"));
                row.setDate(when);
                row.setOwner("admin");
                entityManager.persist(row);
            }
            // SH324 — one id for both rows. Publishing keeps the draft's id, so a fixture that
            // mints one per state is two posts on one URL: the duplicate-URL defect wearing a
            // published page's clothes. Found by ShFixtureShapeGuard, in the class §XVI.31 had
            // recorded as the one that got this right.
            String homeId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified page = new ShPostUnified();
                page.setId(homeId);
                page.setState(state);
                page.setShSite(owner);
                page.setShFolder(root);
                page.setShPostType(pageType);
                page.setTitle("Home");
                page.setFurl("/");
                page.setData(json("TEXT", "<p>body</p>"));
                page.setDate(when);
                page.setOwner("admin");
                entityManager.persist(page);
            }
        });

        assertThat(shPageRenderService.render(site, "/", ShPostState.PUBLISHED).html())
                .contains("LIVE")
                .doesNotContain("DRAFTED");
    }

    @Test
    @DisplayName("SH279: {{file_url}} reads the same row the rest of the render does")
    void fileUrlFollowsTheRenderState() {
        // `findById` returns a row per state and this took whichever came first. The bytes of a File
        // post are immutable (SH145), but `data.FILE` is the *name* in the path — so a draft that
        // renamed the file could produce either path depending on what the database returned.
        // Its own site, not the class fixture: SH263's lesson about PER_CLASS.
        String site = "sh279-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh279Page" + UUID.randomUUID().toString().substring(0, 8);
        String fileId = UUID.randomUUID().toString();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH279_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType fileType = postType("File", (byte) 1, when);

            // Both states of the layout too. Worth knowing why: since SH278 a PUBLISHED render reads
            // only published rows at every level, so a draft-only layout now raises
            // MissingLayoutException where it used to silently render the draft. That is the correct
            // trade — what a visitor gets must not depend on an unpublished edit — and it is a
            // behaviour change for any instance whose templates were never published (SH280).
            String layoutId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(layoutId);
                row.setState(state);
                row.setShSite(owner);
                row.setShFolder(root);
                row.setShPostType(layoutType);
                row.setTitle("SH279_LAYOUT");
                row.setFurl("/l");
                row.setData(json("HTML",
                        "<html><body><img src=\"{{file_url HERO}}\"></body></html>"));
                row.setDate(when);
                row.setOwner("admin");
                entityManager.persist(row);
            }
            // One File post, two rows, two names.
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified row = new ShPostUnified();
                row.setId(fileId);
                row.setState(state);
                row.setShSite(owner);
                row.setShFolder(root);
                row.setShPostType(fileType);
                row.setTitle("hero");
                row.setData(json("FILE",
                        state == ShPostState.PUBLISHED ? "live.png" : "drafted.png"));
                row.setDate(when);
                row.setOwner("admin");
                entityManager.persist(row);
            }
            // Both states of the page, so the published render has a page to render at all — the
            // fixture's own first version only made a draft and the PUBLISHED assertion failed on
            // `UnknownPageException`, which is the renderer being right rather than a defect.
            String pageId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified page = new ShPostUnified();
                page.setId(pageId);
                page.setState(state);
                page.setShSite(owner);
                page.setShFolder(root);
                page.setShPostType(pageType);
                page.setTitle("Home");
                page.setFurl("/");
                page.setData(json("HERO", fileId));
                page.setDate(when);
                page.setOwner("admin");
                entityManager.persist(page);
            }
        });

        assertThat(shPageRenderService.render(site, "/", ShPostState.DRAFT).html())
                .as("a preview resolves the asset the curator is editing")
                .contains("drafted.png")
                .doesNotContain("live.png");
        assertThat(shPageRenderService.render(site, "/", ShPostState.PUBLISHED).html())
                .as("and a published render never picks up the unpublished name")
                .contains("live.png")
                .doesNotContain("drafted.png");
    }

    @Test
    @DisplayName("SH280: an unpublished layout says so, rather than 'no such layout'")
    void anUnpublishedLayoutSaysSo() {
        // The situation SH278 turned from silent into a failure, and it is the *normal* state of a
        // site built through the SH183 flow: post.upsert writes a DRAFT and publishing is separate
        // (P7). Telling that operator "no such layout" sends them to create a second one.
        String site = "sh280-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh280Page" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH280_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            // The layout exists as a draft only — exactly what post.upsert leaves behind.
            post(owner, root, layoutType, "SH280_LAYOUT", "/l",
                    json("HTML", "<html><body>{{{TEXT}}}</body></html>"), when);
            // The page is published, so the site has something that cannot render.
            String pageId = UUID.randomUUID().toString();
            for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
                ShPostUnified page = new ShPostUnified();
                page.setId(pageId);
                page.setState(state);
                page.setShSite(owner);
                page.setShFolder(root);
                page.setShPostType(pageType);
                page.setTitle("Home");
                page.setFurl("/");
                page.setData(json("TEXT", "<p>body</p>"));
                page.setDate(when);
                page.setOwner("admin");
                entityManager.persist(page);
            }
        });

        ShPageRenderService.UnpublishedLayoutException thrown =
                catchThrowableOfType(ShPageRenderService.UnpublishedLayoutException.class,
                        () -> shPageRenderService.render(site, "/", ShPostState.PUBLISHED));

        // The type is the contract — a caller that branches on the problem should be able to tell
        // "you have not written this" from "you have not published it". Asserted on the
        // ProblemDetail rather than on getMessage(), because ShTeachingException's message is the
        // *detail* and the distinguishing sentence is the title.
        assertThat(thrown).isNotNull();
        assertThat(thrown.toProblemDetail().getTitle()).contains("never been published");
        assertThat(thrown.toProblemDetail().getStatus())
                .as("409, not 404: the layout exists, the request conflicts with its state")
                .isEqualTo(409);
        assertThat(thrown.toProblemDetail().getProperties().get("fix").toString())
                .as("P6 — the action is `publish it`, which the 404's fix could not have said")
                .contains("post.publish");

        assertThat(shPageRenderService.render(site, "/", ShPostState.DRAFT).html())
                .as("and the preview still works, which is what makes the message's advice true")
                .contains("<p>body</p>");
    }

    @Test
    @DisplayName("SH281: an unpublished region is marked as unpublished, not as missing")
    void anUnpublishedRegionIsMarkedAsSuch() {
        // SH183 marks an unfilled slot "no Region post". Since SH278 a PUBLISHED render reads only
        // published rows, so a *drafted* region produces that same marker — which is not a missing
        // diagnosis but a wrong one: the author is looking at the region in the console while the
        // page says it does not exist. SH169's rule is that different mistakes must not look alike,
        // and this is a third state beside missing and empty.
        String site = "sh281-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh281Page" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH281_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType regionType = postType(ShSystemPostType.REGION, (byte) 1, when);

            String layoutHtml = "<html><body>"
                    + "<div sh-region=\"SH281_DRAFTED\"></div>"
                    + "<div sh-region=\"SH281_ABSENT\"></div></body></html>";
            publishedAndDraft(owner, root, layoutType, "SH281_LAYOUT", "/l",
                    json("HTML", layoutHtml), when);
            // Exists as a draft only: the case that used to read as "no Region post".
            post(owner, root, regionType, "SH281_DRAFTED", "/r-drafted",
                    json("HTML", "<nav>drafted</nav>"), when);
            publishedAndDraft(owner, root, pageType, "Home", "/", json("TEXT", "b"), when);
        });

        String html = shPageRenderService.render(site, "/", ShPostState.PUBLISHED).html();

        assertThat(html)
                .as("the drafted region says so, and says what to do about it")
                .contains("data-shio-region-unpublished")
                .contains("not published");
        assertThat(html)
                .as("""
                        and the genuinely absent one keeps the SH183 marker, so the two remain                         distinguishable in the delivered DOM and therefore in SH101's digest.""")
                .contains("data-shio-region-missing")
                .contains("no Region post");
    }

    @Test
    @DisplayName("SH297: a layout naming a theme that is not there says so on the page")
    void aMissingThemeIsMarked() {
        // The third edge's failure mode, and the one that was silent: themeOf returns null, the
        // page renders with no stylesheet, and nothing anywhere says why — which reads as a CSS
        // fault rather than a missing post, so the search starts in the wrong place.
        String site = "sh297-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh297Page" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH297_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            post(owner, root, layoutType, "SH297_LAYOUT", "/l",
                    json("HTML", "<html><head><style>{{theme.css}}</style></head>"
                            + "<body><p>{{TEXT}}</p></body></html>",
                            "THEME", "SH297_NO_SUCH_THEME"),
                    when);
            post(owner, root, pageType, "Home", "/", json("TEXT", "body"), when);
        });

        String html = shPageRenderService.render(site, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("""
                        marked rather than refused, because a theme degrades the page the way a \
                        region does (SH281): an unstyled page still serves, which is right for a \
                        preview — a layout that is absent is the one that 409s (SH280).""")
                .contains("data-shio-theme-missing")
                .contains("SH297_NO_SUCH_THEME")
                .contains("names a Theme that does not exist");
        assertThat(html)
                .as("the title travels as an attribute, where jsoup escapes it; the comment is a "
                        + "constant, because a title is content and `-->` inside one would close "
                        + "it (SH183's rule, third application)")
                .doesNotContain("<!-- shio: this layout names a Theme that does not exist; "
                        + "see data-shio-theme SH297");
    }

    @Test
    @DisplayName("SH297: a layout with no theme at all is not marked")
    void noThemeNamedIsNotADefect() {
        // "No theme named" and "a theme named that is not there" are different facts, and only the
        // second is a defect. A layout that brings its own styles is entirely ordinary.
        String site = "sh297b-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh297bPage" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH297B_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);

            post(owner, root, layoutType, "SH297B_LAYOUT", "/l",
                    json("HTML", "<html><body><p>{{TEXT}}</p></body></html>"), when);
            post(owner, root, pageType, "Home", "/", json("TEXT", "body"), when);
        });

        assertThat(shPageRenderService.render(site, "/", ShPostState.DRAFT).html())
                .doesNotContain("data-shio-theme-missing");
    }

    /** A post with both rows sharing one id — the shape a published template actually has. */
    private void publishedAndDraft(ShSite site, ShFolder folder, ShPostType type, String title,
                                   String furl, String data, Instant when) {
        String id = UUID.randomUUID().toString();
        for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
            ShPostUnified row = new ShPostUnified();
            row.setId(id);
            row.setState(state);
            row.setShSite(site);
            row.setShFolder(folder);
            row.setShPostType(type);
            row.setTitle(title);
            row.setFurl(furl);
            row.setData(data);
            row.setDate(when);
            row.setOwner("admin");
            entityManager.persist(row);
        }
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

    // --- SH336: the preview is walkable ---

    /**
     * The rule is narrow on purpose, so the cases that must be left alone are the assertion.
     *
     * <p>Rewriting every root-relative href would break the page: {@code {{file_url}}} emits a real
     * server path, SH141's declared front-end routes are real, and a document-relative link already
     * resolves under the preview prefix. Only a path this site actually publishes is ours to move.</p>
     */
    @Test
    @DisplayName("SH336: an internal link enters the preview's address space and nothing else moves")
    void internalLinksAreRewrittenAndNothingElseIs() {
        ShRenderedPage page = renderedPageOfMainFixture();

        String html = previewLinks.rewrite("""
                <a href="/">home</a>\
                <a href="/file_source/abc/logo.png">a real server path</a>\
                <a href="https://example.com/">absolute</a>\
                <a href="//cdn.example.com/x">protocol-relative</a>\
                <a href="/preview/Other/x">already in a preview</a>\
                <a href="contato">document-relative</a>\
                <a href="/nope">not a page of this site</a>""", siteName, page);

        assertThat(html).contains("href=\"/preview/" + siteName + "/\"");
        assertThat(html)
                .as("everything the site does not publish is left exactly as the author wrote it")
                .contains("href=\"/file_source/abc/logo.png\"")
                .contains("href=\"https://example.com/\"")
                .contains("href=\"//cdn.example.com/x\"")
                .contains("href=\"/preview/Other/x\"")
                .contains("href=\"contato\"")
                .contains("href=\"/nope\"");
    }

    @Test
    @DisplayName("SH336: rewriting twice changes nothing, so the pass is idempotent")
    void theRewriteIsIdempotent() {
        ShRenderedPage page = renderedPageOfMainFixture();
        String once = previewLinks.rewrite("<a href=\"/\">home</a>", siteName, page);

        assertThat(previewLinks.rewrite(once, siteName, page)).isEqualTo(once);
    }

    @Test
    @DisplayName("SH336: a query string and a fragment survive the rewrite")
    void theTailOfAUrlIsPreserved() {
        ShRenderedPage page = renderedPageOfMainFixture();

        assertThat(previewLinks.rewrite("<a href=\"/?q=1#top\">home</a>", siteName, page))
                .as("SH259 will need `?q=`, and a rewrite that ate it would be a defect nobody "
                        + "looked for until then")
                .contains("href=\"/preview/" + siteName + "/?q=1#top\"");
    }

    /**
     * Built rather than rendered: the unit under test is the rewrite, and rendering first would
     * make the assertion depend on the fixture's layout as well.
     */
    private ShRenderedPage renderedPageOfMainFixture() {
        String siteId = entityManager
                .createQuery("select s.id from ShSite s where s.name = :name", String.class)
                .setParameter("name", siteName)
                .getSingleResult();
        return new ShRenderedPage("", siteName, "/", "layout", List.of(),
                ShPostState.DRAFT, "post-id", siteId);
    }
}
