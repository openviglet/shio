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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.ShContentPerspective;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.revision.ShPostRevision;
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
    /** SH516 — the gate, asked about the page this fixture can actually render. */
    @Autowired
    private com.viglet.shio.api.agent.verify.ShAgentVerifyService verifyService;
    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private com.viglet.shio.api.post.revision.ShPostRevisionService shPostRevisionService;
    @Autowired
    private com.viglet.shio.persistence.repository.post.revision.ShPostRevisionRepository shPostRevisionRepository;
    /** SH1014 — to ask the database whether a preview left anything behind. */
    @Autowired
    private com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private com.viglet.shio.persistence.repository.site.ShSiteRepository shSiteRepository;
    @Autowired
    private ShPreviewBanner previewBanner;

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

    // ── SH1014: a page rendered from values nobody has saved ────────────────────────

    /**
     * SH1014 — the overlay reaches the page, and only the fields it names.
     *
     * <p>Against the fixture that actually composes, because the question is whether the proposed
     * document survives the whole path — layout, region, theme, helpers — and not whether one
     * method copied a field.</p>
     */
    @Test
    @DisplayName("SH1014: proposed values render, and a field the override omits keeps its value")
    void aProposedRenderShowsWhatWasNotSaved() {
        String html = shPageRenderService.renderProposed(siteName, "/", ShPostState.DRAFT,
                objectMapper.readTree("{\"title\":\"Proposed headline\"}"), java.util.Map.of())
                .html();

        assertThat(html)
                .as("SH1014: the value nobody saved is on the page — this is the whole feature, and "
                        + "a curator could not see a change without first making it")
                .contains("<h1>Proposed headline</h1>")
                .contains("<title>Proposed headline</title>");
        assertThat(html)
                .as("SH1014: and TEXT, which the override never named, is the stored one — the "
                        + "merge rule (SH335) is what lets a caller ask about one headline without "
                        + "restating the document")
                .contains("<p>Hello world</p>");
        assertThat(html)
                .as("SH1014: the stored title is gone from the page, or the overlay did not "
                        + "actually replace anything")
                .doesNotContain("<h1>Welcome</h1>");
    }

    /**
     * SH1014 — and it persists nothing.
     *
     * <p>The assertion the design named, and it is worth more than the feature: the render path is
     * shared, and "it writes nothing" is true today because no writer is called — a claim about
     * code, which somebody who has not read the paragraph explaining it will edit. So this does not
     * inspect the implementation. It renders, then asks the database the questions a write would
     * betray.</p>
     */
    @Test
    @DisplayName("SH1014: a proposed render leaves the row, its title and its revisions alone")
    void aProposedRenderPersistsNothing() {
        // By the site's id and never by navigating `p.shSite.name`: @TenantId lives on the JOINED
        // root, so an implicit join past an ShObject association emits a predicate against a
        // subclass alias that has no tenant_id column (the agents.md invariant, and it fails at
        // query-prepare time rather than here).
        String siteId = shSiteRepository.findByName(siteName).getId();
        ShPostUnified before = shPostUnifiedRepository
                .findByShSite_IdAndFurlAndStateOrderByIdAsc(siteId, "/", ShPostState.DRAFT)
                .stream().findFirst().orElseThrow();
        String storedData = before.getData();
        String storedTitle = before.getTitle();
        long revisionsBefore = shPostRevisionRepository.count();

        shPageRenderService.renderProposed(siteName, "/", ShPostState.DRAFT,
                objectMapper.readTree("{\"title\":\"Never saved\",\"TEXT\":\"<p>nor this</p>\"}"),
                java.util.Map.of());

        ShPostUnified after = shPostUnifiedRepository
                .findByIdAndState(before.getId(), ShPostState.DRAFT).orElseThrow();
        assertThat(after.getData())
                .as("SH1014: an overlay that reached the row would replace a curator's saved work "
                        + "with something they only asked to look at")
                .isEqualTo(storedData);
        assertThat(after.getTitle()).isEqualTo(storedTitle);
        assertThat(shPostRevisionRepository.count())
                .as("SH1014: no revision captured — a preview that versioned what it previewed "
                        + "would fill the history with documents nobody chose to keep")
                .isEqualTo(revisionsBefore);
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
    @DisplayName("SH461: a theme's CSS reaches the page unescaped, so its url() is a URL")
    void aThemesCssIsNotHtmlEscaped() {
        // The defect, and the reason it hid for four tasks: `<style>{{theme.css}}</style>` is the
        // documented layout, the double stash escapes, and CSS decodes no entities — so
        // `url("/fonts/inter.woff2")` arrived as `url(&quot;/fonts/inter.woff2&quot;)`, four literal
        // characters inside the URL and an invalid @font-face whatever it pointed at. Nothing else
        // in a stylesheet carries " < > or &, so the page looked styled and only the references
        // were broken: the failure was invisible in proportion to how ordinary the CSS was.
        String site = "sh461-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh461Page" + UUID.randomUUID().toString().substring(0, 8);
        String css = "@font-face{font-family:\"Inter\";src:url(\"/fonts/inter.woff2\") format(\"woff2\")}"
                + "a{content:'a & b'}";

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH461_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);

            post(owner, root, themeType, "SH461_THEME", "/theme", json("CSS", css), when);
            post(owner, root, layoutType, "SH461_LAYOUT", "/l",
                    json("HTML", "<html><head><style>{{theme.css}}</style></head>"
                            + "<body><p>{{TEXT}}</p></body></html>",
                            "THEME", "SH461_THEME"),
                    when);
            post(owner, root, pageType, "Home", "/", json("TEXT", "body"), when);
        });

        String html = shPageRenderService.render(site, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the stylesheet is served as written — the stash escapes, and this is the one "
                        + "value in the model for which escaping is not the encoding of its context")
                .contains(css);
        assertThat(html)
                .as("and none of the three entities the escaper would have introduced is here; a "
                        + "single &quot; is enough to break every url() and every quoted family name")
                .doesNotContain("&quot;")
                .doesNotContain("&#x27;")
                .doesNotContain("&amp;");
    }

    @Test
    @DisplayName("SH829: {{theme.title}} names the sheet, and it is escaped where the CSS is not")
    void aThemesTitleNamesTheSheetItEmits() {
        // The pair, in one page: `{{theme.css}}` is raw because a `<style>` is a raw-text element
        // (SH461), and `{{theme.title}}` beside it is *not*, because it is written into an attribute
        // — the same reason SH297 put a theme's title on `<body>` instead of into a comment. Getting
        // this backwards is invisible on an ordinary title and breaks the digest's key on the one
        // title with an ampersand in it.
        String site = "sh829-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh829Page" + UUID.randomUUID().toString().substring(0, 8);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite owner = new ShSite();
            owner.setName(site);
            owner.setDate(when);
            owner.setOwner("admin");
            owner.setPostTypeLayout("{\"" + type + "\":\"SH829_LAYOUT\"}");
            entityManager.persist(owner);
            ShFolder root = folder(owner, null, "root", (byte) 1, when);
            ShPostType pageType = postType(type, (byte) 0, when);
            ShPostType layoutType = postType(ShSystemPostType.PAGE_LAYOUT, (byte) 1, when);
            ShPostType themeType = postType(ShSystemPostType.THEME, (byte) 1, when);

            post(owner, root, themeType, "VigR3 & co", "/theme",
                    json("CSS", ".shio-section{padding:2rem}"), when);
            post(owner, root, layoutType, "SH829_LAYOUT", "/l",
                    json("HTML", "<html><head><style data-shio-theme=\"{{theme.title}}\">"
                            + "{{theme.css}}</style></head>"
                            + "<body><section class=\"shio-section\">{{TEXT}}</section></body></html>",
                            "THEME", "VigR3 & co"),
                    when);
            post(owner, root, pageType, "Home", "/", json("TEXT", "body"), when);
        });

        String html = shPageRenderService.render(site, "/", ShPostState.DRAFT).html();

        assertThat(html)
                .as("the attribute carries the Theme post's title, escaped for its context")
                .contains("data-shio-theme=\"VigR3 &amp; co\"");
        assertThat(com.viglet.shio.api.agent.verify.ShHtmlAppearance.definedBySheet(html))
                .as("""
                        so the digest's region table prints the post's name where it printed an \
                        ordinal — which is the second call SH765's column was added to remove and \
                        was still costing (SH829).""")
                .containsOnlyKeys("VigR3 & co");
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
     * <p>Absolute and protocol-relative belong to whoever they name, and SH466's
     * {@code INSTANCE_ROUTES} keeps the paths the <em>instance</em> serves — {@code /file_source/…},
     * where {@code {{file_url}}} points, and the two prefixes this pass itself writes.</p>
     *
     * <p>SH512 took the document-relative case out of that list: it resolves correctly under the
     * prefix only when the replica's serving depth equals the source's, and the assertion below now
     * reads the other way for the same reason SH466's did.</p>
     */
    @Test
    @DisplayName("SH336: an internal link enters the preview's address space and nothing else moves")
    void internalLinksAreRewrittenAndNothingElseIs() {
        ShRenderedPage page = renderedPageOfMainFixture();

        String html = previewLinks.rewrite("""
                <a href="/">home</a>\
                <a href="/file_source/abc/logo.png">a real server path</a>\
                <a href="/api/v2/ping">the instance's own REST surface</a>\
                <a href="https://example.com/">absolute</a>\
                <a href="//cdn.example.com/x">protocol-relative</a>\
                <a href="/preview/Other/x">already in a preview</a>\
                <a href="mailto:someone@example.com">a scheme that is not a path</a>\
                <a href="#top">the page itself</a>\
                <a href="contato">document-relative</a>""", siteName, page);

        assertThat(html).contains("href=\"/preview/" + siteName + "/\"");
        assertThat(html)
                .as("a route the instance serves, and everything that is not a path of ours at all")
                .contains("href=\"/file_source/abc/logo.png\"")
                .contains("href=\"/api/v2/ping\"")
                .contains("href=\"https://example.com/\"")
                .contains("href=\"//cdn.example.com/x\"")
                .contains("href=\"/preview/Other/x\"")
                .contains("href=\"mailto:someone@example.com\"")
                .contains("href=\"#top\"");
        assertThat(html)
                .as("SH512: resolved against the page's own URL — this fixture is served at `/`, so "
                        + "`contato` means `/contato`, and that is what has to be prefixed")
                .contains("href=\"/preview/" + siteName + "/contato\"");
    }

    /**
     * SH512 — the arithmetic, on the page that produced the task.
     *
     * <p>Replicating {@code vilt-group.com/en/} gave a page whose logo, hero background and fourteen
     * images were missing while every one of them answered 200 at the path the tree held. The source
     * serves at {@code /en/}, one segment deep, so {@code ./../../img/x} means {@code /img/x}; the
     * replica serves at {@code /sites/<site>/default/en-us/en}, four deep, where the same string
     * means {@code /sites/<site>/img/x}. Forty-four references on that page were relative, and the
     * gate was clean because every asset <em>is</em> addressable — the question it never asked is
     * whether the page's own string reaches it.</p>
     */
    @Test
    @DisplayName("SH512: a relative link is resolved against the page's URL, not the serving depth")
    void aRelativeLinkIsResolvedInTheSourcesUrlSpace() {
        ShRenderedPage page = renderedPageOfMainFixture();
        String prefix = "/sites/" + siteName + "/default/en-us";

        String html = previewLinks.rewriteInto("""
                <a href="./../../about">two up from /en/</a>\
                <a href="../careers">one up</a>\
                <a href="team">a sibling</a>\
                <a href="?page=2">the page itself, another query</a>""", prefix, page, "/en/");

        assertThat(hrefsIn(html))
                .as("every one of them resolved as a browser reading the SOURCE would have, and "
                        + "then rebased — `..` clamps at the root rather than climbing above it")
                .containsExactly(prefix + "/about", prefix + "/careers", prefix + "/en/team",
                        prefix + "/en/?page=2");
        assertThat(previewLinks.rewriteInto(html, prefix, page, "/en/"))
                .as("and a second pass is still a no-op: what it wrote is root-relative and under "
                        + "an instance route")
                .isEqualTo(html);
    }

    @Test
    @DisplayName("SH512: the base is the request's path, because a trailing slash is one level of ..")
    void theTrailingSlashOfTheRequestedPathIsOneLevel() {
        ShRenderedPage page = renderedPageOfMainFixture();
        String prefix = "/sites/" + siteName + "/default/en-us";

        assertThat(hrefsIn(previewLinks.rewriteInto(
                "<a href=\"../x\">up</a>", prefix, page, "/en/blog/")))
                .as("a directory-shaped URL resolves inside itself")
                .containsExactly(prefix + "/en/x");
        assertThat(hrefsIn(previewLinks.rewriteInto(
                "<a href=\"../x\">up</a>", prefix, page, "/en/blog")))
                .as("and a file-shaped one resolves from its parent — the two differ by exactly the "
                        + "level that `ShAddress.normalizePath` drops, which is why the base is the "
                        + "request's path and not the post's furl")
                .containsExactly(prefix + "/x");
    }

    /**
     * SH466 — the case the assertion above used to make the other way round.
     *
     * <p>It read {@code contains("href=\"/nope\"")}, on the reasoning that a path this site does
     * not publish is not ours to move. But the page is served under {@code /preview/<site>/} or
     * {@code /sites/<furl>/<format>/<locale>/}, and a verbatim root-relative href is the source's
     * base — so "leave it alone" is the one output guaranteed to leave the site. Worse, whether a
     * link fell through depended on the index, which depends on <b>publication state</b>: a
     * replicated page carried {@code href="/turing/"} beside five rewritten links because
     * {@code turing} happened to be unpublished, and the same page on {@code /preview} had all six.
     * Rebasing it makes the failure a 404 <em>inside</em> the replica, which {@code verify}'s
     * {@code routes} check already reports.</p>
     */
    @Test
    @DisplayName("SH466: a path this site does not publish is rebased too, so it 404s inside the replica")
    void anUnresolvedLinkIsRebasedRatherThanLeftVerbatim() {
        ShRenderedPage page = renderedPageOfMainFixture();

        String html = previewLinks.rewrite(
                "<a href=\"/nope\">not a page of this site</a>"
                        + "<a href=\"/turing/\">unpublished at this moment</a>", siteName, page);

        assertThat(html)
                .contains("href=\"/preview/" + siteName + "/nope\"")
                .contains("href=\"/preview/" + siteName + "/turing/\"");
    }

    @Test
    @DisplayName("SH466: no root-relative href survives the pass except the instance's own routes")
    void everyNavigablePathEndsUpInTheSitesSpace() {
        ShRenderedPage page = renderedPageOfMainFixture();
        String prefix = "/sites/" + siteName + "/default/en-us";

        String html = previewLinks.rewriteInto("""
                <a href="/a">published</a><a href="/b/c">not published</a>\
                <a href="/d?q=1#top">with a tail</a><link rel="icon" href="/favicon.ico">\
                <a href="/api/v2/ping">the instance</a>""", prefix, page);

        assertThat(hrefsIn(html))
                .as("the one thing a replica must never emit is an href whose base is the source's "
                        + "— except that SH515 took the `<link rel=icon>` out of this rule. Its "
                        + "href asks for BYTES, and the capture's collector has always recorded it "
                        + "as an asset; the two halves of one contract disagreed about what a "
                        + "favicon is, and the unconditional rebase is the navigation argument "
                        + "(SH466) applied where the opposite one holds — this site serves nothing "
                        + "at /favicon.ico, so rebasing it makes a measurable reference point at "
                        + "nothing. `page-reference-broken` reports it either way (SH516)")
                .containsExactlyInAnyOrder(
                        prefix + "/a", prefix + "/b/c", prefix + "/d?q=1#top",
                        "/favicon.ico", "/api/v2/ping");
        assertThat(previewLinks.rewriteInto(html, prefix, page))
                .as("and the public prefix is an instance route too, which is what keeps a second "
                        + "pass a no-op now that the index no longer decides")
                .isEqualTo(html);
    }

    private static List<String> hrefsIn(String html) {
        List<String> found = new ArrayList<>();
        Matcher hrefs = Pattern.compile("href=\"([^\"]*)\"").matcher(html);
        while (hrefs.find()) {
            found.add(hrefs.group(1));
        }
        return found;
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

    // --- SH1036: the renderer's own parameters ride every navigation ---

    /** The preview prefix this fixture's site is served under, as the controller builds it. */
    private String previewPrefix() {
        return ShSiteFrontEnd.previewPrefix(siteName);
    }

    @Test
    @DisplayName("SH1173: a site named with a space is a path, not a query")
    void aSpaceInTheSiteNameIsAPathSegment() {
        // `URLEncoder` is form encoding: a space became `+`, which a path reads as a literal plus,
        // so every link on "Console Check" pointed at `/preview/Console+Check/` — a 404.
        assertThat(ShSiteFrontEnd.previewPrefix("Console Check")).isEqualTo("/preview/Console%20Check");
        assertThat(ShSiteFrontEnd.previewPrefix("a/b")).isEqualTo("/preview/a%2Fb");
    }

    @Test
    @DisplayName("SH1173: a link already inside the preview carries the reader's mode")
    void aLinkAlreadyInThePreviewCarriesTheMode() {
        ShRenderedPage page = renderedPageOfMainFixture();
        // The home link a template writes from `ShSiteFrontEnd.of` arrives already prefixed. It was
        // skipped as an instance route, so it never carried `editor=true`: the Universal Editor's
        // click-through landed there with no bridge.
        String home = "<a href=\"" + previewPrefix() + "/\">home</a>";
        assertThat(previewLinks.rewriteInto(home, previewPrefix(), page, "/", "editor=true"))
                .contains("href=\"" + previewPrefix() + "/?editor=true\"");
        // Another site's preview is not this page's address space, and is left as it was.
        String other = "<a href=\"/preview/Other/\">x</a>";
        assertThat(previewLinks.rewriteInto(other, previewPrefix(), page, "/", "editor=true"))
                .contains("href=\"/preview/Other/\"");
    }

    @Test
    @DisplayName("SH1036: editor and state ride every rewritten link, and no asset")
    void theRenderersParametersRideTheLinks() {
        ShRenderedPage page = renderedPageOfMainFixture();
        String html = "<a href=\"/contato\">c</a>"
                + "<img src=\"/img/hero.png\">"
                + "<link rel=\"stylesheet\" href=\"/css/site.css\">";

        String rewritten = previewLinks.rewriteInto(html, previewPrefix(), page, "/",
                "editor=true&state=rev%3A3");

        // The whole defect: `?editor=true` is what injects the bridge, so a link without it lands
        // the frame on a page with no editor while the console still reports `connected`.
        //
        // `&amp;` because this is HTML and Jsoup serializes it correctly — a raw `&` in an href is
        // the bug this assertion would otherwise have licensed.
        assertThat(rewritten)
                .as("a navigation carries the reader's mode")
                .contains("href=\"" + previewPrefix() + "/contato?editor=true&amp;state=rev%3A3\"");
        // And nothing else does. `?editor=true` on a stylesheet means nothing and busts its cache;
        // on an image it makes every variant a separate entry in every cache between here and the
        // reader. A `<link rel=stylesheet>` is an `href` the NAVIGABLE pass sees, which is exactly
        // why the asset/navigation split (SH515) has to be honoured here too — it is the one that
        // would have carried the parameter if this pass had keyed on the attribute name alone.
        assertThat(rewritten)
                .as("an asset does not, and a stylesheet is the one that looks like a link")
                .doesNotContain("hero.png?editor")
                .doesNotContain("site.css?editor");
    }

    @Test
    @DisplayName("SH1036: the link's own query wins, and no carry means no change")
    void theAuthorsQueryWins() {
        ShRenderedPage page = renderedPageOfMainFixture();

        // An author who wrote `state=published` means that. A carried parameter overriding it would
        // make the rewrite change what the page SAYS rather than where it goes.
        assertThat(previewLinks.rewriteInto("<a href=\"/contato?state=published\">c</a>",
                previewPrefix(), page, "/", "editor=true&state=rev%3A3"))
                .contains("/contato?state=published&amp;editor=true\"");

        // The public `/sites/**` delivery has no modes, so it passes no carry — and must render
        // byte-identically to what it did before this task.
        assertThat(previewLinks.rewriteInto("<a href=\"/contato\">c</a>", previewPrefix(), page,
                "/", null))
                .isEqualTo(previewLinks.rewriteInto("<a href=\"/contato\">c</a>", previewPrefix(),
                        page, "/"));
    }

    @Test
    @DisplayName("SH1036: a fragment stays a fragment, and the query goes before it")
    void theFragmentStaysLast() {
        ShRenderedPage page = renderedPageOfMainFixture();

        // `/a#b?c` is a fragment containing a question mark and not a query, so the split has to
        // take the fragment off first or the parameters land inside it and reach no server.
        assertThat(previewLinks.rewriteInto("<a href=\"/contato#section\">c</a>",
                previewPrefix(), page, "/", "editor=true"))
                .contains("/contato?editor=true#section\"");
    }

    // --- SH516: the gate reads the page it approved ---

    /**
     * The check that closes the gap every defect of this session lived in.
     *
     * <p>{@code verify --site VILT} said <b>ok — 0 errors, 17 warnings</b> over a page with 38
     * broken links of 39 and 43 broken references, because every check resolves against the content
     * graph: does a furl exist, is a {@code File} post addressable. All of that was true, and the
     * page still did not work. So the fixture below is a site whose content is <em>internally
     * consistent</em> — nothing points at a post that is missing — and whose rendered page asks for
     * two things this site does not serve. {@code content,routes} has to answer {@code ok} on it,
     * or the test is asserting nothing; {@code page} has to name both.</p>
     */
    @Test
    @DisplayName("SH516: the tree says ok and the page check names what the page actually asks for")
    void thePageCheckResolvesTheRenderedPagesOwnReferences() {
        String site = "sh516-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh516Page" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH516_LAYOUT";

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

            published(owner, root, layoutType, layoutTitle, "/l",
                    json("HTML", "<html><body>{{{TEXT}}}</body></html>"), when);
            // Relative, and that is deliberate: the strings a real capture leaves behind are
            // relative, and a check that filtered references on a leading slash would report zero
            // here — which is the mistake the hand-measurement made before this existed.
            published(owner, root, pageType, "Home", "/", json("TEXT",
                    "<a href=\"second\">a page this site publishes</a>"
                            + "<a href=\"../missing\">a page it does not</a>"
                            + "<img src=\"img/nope.png\">"), when);
            // Real content on purpose: SH521's `page-empty` reports a published page with no
            // heading, no link, no image and almost no words, and it is right to — this fixture is
            // about resolving references, so its second page has to be a page.
            published(owner, root, pageType, "Second", "/second", json("TEXT",
                    "<h1>Second</h1><p>Enough prose on this page that it is a page somebody wrote "
                            + "rather than an empty one the gate ought to be reporting.</p>"), when);
        });

        com.viglet.shio.api.agent.verify.dto.ShAgentReport tree = verifyService.verify(site, "content,routes", 100);
        assertThat(tree.ok())
                .as("the content is self-consistent — this is the answer the gate has been giving "
                        + "about pages whose every link is broken")
                .isTrue();

        com.viglet.shio.api.agent.verify.dto.ShAgentReport page = verifyService.verify(site, "page", 100);
        assertThat(page.findings())
                .extracting(com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::rule,
                        com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::address)
                .as("one finding per broken reference, addressed to the PAGE that emits it — which "
                        + "is the artefact somebody edits, not the path that is missing. And the "
                        + "one the site does publish is silent: `second` resolves to /second from "
                        + "a page served at /")
                .containsExactlyInAnyOrder(
                        tuple("page-link-broken", "post:" + site + "/"),
                        tuple("page-reference-broken", "post:" + site + "/"));
        assertThat(page.findings())
                .extracting(com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::message)
                .as("and each one quotes the string the page carries beside the path it resolves "
                        + "to, because the repair is made in the markup and found by the path")
                .anySatisfy(message -> assertThat(message).contains("'../missing'", "/missing"))
                .anySatisfy(message -> assertThat(message)
                        .contains("'img/nope.png'", "/img/nope.png"));
        assertThat(page.ok())
                .as("and they are errors: the route table this judged against belongs to the "
                        + "process that will serve the request, so it is proved rather than guessed")
                .isFalse();
        assertThat(page.notes())
                .as("a check that reports nothing has to say what it read, or a clean pass and an "
                        + "unrun pass are the same output (SH138)")
                .anySatisfy(note -> assertThat(note).contains("rendered 2 page(s)"));
    }

    /**
     * SH521 — the fact was already computed and nobody joined it to a judgement.
     *
     * <p>{@code verify} answered <b>ok</b> on a page that had lost its hero, its grid and its
     * footer, while {@code GET /agent/render} answered {@code links: 0, images: 0} at the same
     * moment and listed nine classes the page draws with and nothing styles. Shio knew. Both reads
     * are computed from the bytes the check has just rendered, so this is a threshold rather than
     * an analysis — and it ends the join I keep rebuilding by hand and have got wrong twice.</p>
     */
    /**
     * SH694 — a page serving two visual languages, which nothing could state.
     *
     * <p>One glance at the {@code www.viglet.org} replica showed two defects. SH687 made the first
     * sayable — the captured footer renders above every section. This is the other: the accepted
     * sections are presented by the Shio theme while the chrome keeps the captured one.
     * {@code unstyledClasses} answers <em>whether</em> a class is defined and never <b>by which
     * sheet</b>, because {@code definedIn} concatenated every stylesheet into one set.</p>
     *
     * <p>The finding is <b>disjointness</b> and not "two sheets exist" — the second test below is
     * the one that keeps this from firing on every page with a second stylesheet, which is most of
     * them.</p>
     */
    @Test
    @DisplayName("SH694: chrome and sections sharing no stylesheet is a finding")
    void twoThemesInOneDocumentIsAFinding() {
        String site = "sh694-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh694Page" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH694_LAYOUT";

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

            // The replica's shape: two stylesheets, the captured one styling the chrome and the
            // Shio one styling what an editor owns, and not one class in common.
            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head>"
                            + "<style>.site-head{color:#111}.site-foot{color:#222}</style>"
                            + "<style>.shio-section{padding:2rem}</style>"
                            + "</head><body>{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Split", "/split", json("TEXT",
                    "<header class=\"site-head\"><h1>Chrome the conversion kept</h1></header>"
                            + "<section sh-post=\"a1\" class=\"shio-section\">"
                            + "<p>Prose enough that this is not the empty page, which is a different "
                            + "finding and would confuse the one under test.</p></section>"
                            + "<footer class=\"site-foot\">Kept chrome</footer>"), when);
        });

        List<com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding> findings =
                verifyService.verify(site, "page", 100).findings();

        assertThat(findings)
                .filteredOn(finding -> "two-themes".equals(finding.rule()))
                .as("""
                        the chrome and the sections share no stylesheet, so a visitor sees two \
                        visual languages in one document — the defect a screenshot from a human \
                        found and no instrument could state (SH694).""")
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.address()).isEqualTo("post:" + site + "/split");
                    assertThat(finding.message()).contains("style[1]", "style[2]");
                    assertThat(finding.severity())
                            .as("a warning: two stylesheets with nothing in common is how a "
                                    + "mid-migration site legitimately looks, and finishing it is "
                                    + "the curator's call")
                            .isEqualTo("warning");
                });
    }

    @Test
    @DisplayName("SH694: two stylesheets the whole page shares are one design in two files")
    void twoSheetsSharedByEveryRegionIsNotAFinding() {
        // Guards the guard, and it is the assertion that keeps this rule usable: almost every real
        // page carries more than one stylesheet. The finding is DISJOINTNESS — the chrome and the
        // sections drawing from sets with nothing in common — not the existence of a second sheet.
        String site = "sh694ok-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh694Ok" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH694_OK_LAYOUT";

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

            // Two sheets, and both halves of the page draw from both: a reset plus a theme, which
            // is the ordinary way a design is split across files.
            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head>"
                            + "<style>.box{margin:0}</style>"
                            + "<style>.brand{color:#111}</style>"
                            + "</head><body>{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Shared", "/shared", json("TEXT",
                    "<header class=\"box brand\"><h1>Chrome</h1></header>"
                            + "<section sh-post=\"a1\" class=\"box brand\">"
                            + "<p>Prose enough that this is not the empty page, which is a different "
                            + "finding and would confuse the one under test.</p></section>"), when);
        });

        assertThat(verifyService.verify(site, "page", 100).findings())
                .as("one design in two files is not two designs — a rule that fired here would "
                        + "fire on almost every page and be the next thing nobody reads")
                .noneMatch(finding -> "two-themes".equals(finding.rule()));
    }

    @Test
    @DisplayName("SH521: a page that renders to nothing is a finding, and so is one nothing styles")
    void theGateJudgesTheArtefactRatherThanTheStore() {
        String site = "sh521-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh521Page" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH521_LAYOUT";

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

            // The layout carries its own stylesheet and loads no other, which is the one state in
            // which "this class is styled by nothing" is knowable rather than advisory.
            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head><style>.shio-page{margin:0}</style></head>"
                            + "<body class=\"shio-page\">{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Gone", "/", json("TEXT", ""), when);
            published(owner, root, pageType, "Unstyled", "/unstyled", json("TEXT",
                    "<h1 class=\"hero-title\">Still here</h1>"
                            + "<p class=\"lead\">Prose enough that this page is not the empty one, "
                            + "which is the other half of what this test tells apart.</p>"), when);
        });

        List<com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding> findings =
                verifyService.verify(site, "page", 100).findings();

        assertThat(findings)
                .extracting(com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::rule,
                        com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding::address)
                .as("a page with no heading, no link, no image AND almost no words is empty in "
                        + "every sense a reader has — any one of those alone is a page somebody "
                        + "may have meant, and all four at once is the SH520 symptom")
                .contains(tuple("page-empty", "post:" + site + "/"))
                .contains(tuple("page-unstyled", "post:" + site + "/unstyled"));
        assertThat(findings)
                .filteredOn(finding -> "page-unstyled".equals(finding.rule()))
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.message()).contains("hero-title", "lead");
                    assertThat(finding.severity()).isEqualTo("warning");
                });
    }

    @Test
    @DisplayName("SH521: a page loading a stylesheet this instance never saw is not judged on it")
    void anExternalStylesheetMakesTheUnstyledListUnknowable() {
        String site = "sh521x-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh521xPage" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH521X_LAYOUT";

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

            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head><style>.shio-page{margin:0}</style>"
                            + "<link rel=\"stylesheet\" href=\"https://cdn.test/bootstrap.css\">"
                            + "</head><body class=\"shio-page\">{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Bootstrapped", "/", json("TEXT",
                    "<h1 class=\"display-4\">Styled elsewhere</h1>"
                            + "<p class=\"lead\">The 2018 bootstrap-site package is exactly this "
                            + "page, and a rule over it would fire dozens of times.</p>"), when);
        });

        assertThat(verifyService.verify(site, "page", 100).findings())
                .as("ShHtmlAppearance keeps `unstyledClasses` advisory because a page may load a "
                        + "stylesheet this instance has never seen, and that reasoning is right. "
                        + "The guard is what makes the same list a finding without inheriting the "
                        + "false positives: only where the document's own CSS is the whole of what "
                        + "styles it")
                .noneMatch(finding -> "page-unstyled".equals(finding.rule()));
    }

    /**
     * SH851 — the instrument SH850 walked past, on the half of a stylesheet nothing was reading.
     *
     * <h3>Every gate green while a page lost its colour</h3>
     *
     * <p>SH850 took a replica's dangling {@code var()} references from <b>231 to 958</b> and this
     * service answered {@code 0 error(s), 2 warning(s)} on both rounds — as did {@code diff},
     * {@code judge} and {@code audit}. The gap is exact: {@code page-unstyled} counts <b>class
     * names</b>, and that page made 3,005 {@code var()} calls against 205 classes.</p>
     *
     * <p>The fixture is the shape a converted site actually has after SH424: a theme declaring the
     * tokens it needs, and a second page whose stylesheet reads one it does not declare — which is
     * what a lifted declaration leaves behind when nothing puts the compiled block back.</p>
     */
    @Test
    @DisplayName("SH851: a var() the page never declares is a finding, with the count that says how much")
    void aDanglingCustomPropertyIsAFinding() {
        String site = "sh851-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh851Page" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH851_LAYOUT";

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

            // Its own stylesheet and no other, which is the one state in which "nothing declares
            // this" is knowable rather than advisory — `page-unstyled`'s guard, for its reason.
            // `--ink` is declared and read; `--brand` and `--radius` are read and were lifted away.
            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head><style>:root{--ink:#111}"
                            + ".shio-page{margin:0;color:var(--ink)}"
                            + ".cta{background:var(--brand);border-radius:var(--radius)}"
                            + ".mark{color:var(--brand)}"
                            + ".safe{padding:var(--space,1rem)}</style></head>"
                            + "<body class=\"shio-page\">{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Tokens", "/", json("TEXT",
                    "<h1 class=\"shio-page\">Still here</h1>"
                            + "<p class=\"cta mark safe\">Prose enough that this page is not the "
                            + "empty one, which is a different finding and would confuse this "
                            + "one.</p>"), when);
        });

        assertThat(verifyService.verify(site, "page", 100).findings())
                .filteredOn(finding -> "page-dangling-token".equals(finding.rule()))
                .as("declared set minus referenced set, off the CSS the page already carries — no "
                        + "rendering and no browser, which is why this is cheaper than the class "
                        + "check it sits beside")
                .singleElement()
                .satisfies(finding -> {
                    assertThat(finding.address()).isEqualTo("post:" + site + "/");
                    assertThat(finding.message())
                            .as("the names, so the reader knows which; and the occurrence count "
                                    + "beside the distinct one, because 194 names is how much of "
                                    + "the palette went and 958 reads is how much of the page")
                            .contains("--brand", "--radius", "2 custom propert", "3 time(s)")
                            .doesNotContain("--space", "--ink");
                    assertThat(finding.severity())
                            .as("a warning, for `page-unstyled`'s reason: the premise is provable "
                                    + "and the consequence is presentational")
                            .isEqualTo("warning");
                });
    }

    @Test
    @DisplayName("SH851: a page loading a stylesheet this instance never saw is not judged on it either")
    void anExternalStylesheetMakesTheDanglingListUnknowable() {
        String site = "sh851x-" + UUID.randomUUID().toString().substring(0, 8);
        String type = "Sh851xPage" + UUID.randomUUID().toString().substring(0, 8);
        String layoutTitle = "SH851X_LAYOUT";

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

            published(owner, root, layoutType, layoutTitle, "/l", json("HTML",
                    "<html><head><style>.shio-page{color:var(--bs-body-color)}</style>"
                            + "<link rel=\"stylesheet\" href=\"https://cdn.test/bootstrap.css\">"
                            + "</head><body class=\"shio-page\">{{{TEXT}}}</body></html>"), when);
            published(owner, root, pageType, "Bootstrapped", "/", json("TEXT",
                    "<h1 class=\"shio-page\">Declared elsewhere</h1>"
                            + "<p class=\"shio-page\">Bootstrap declares --bs-body-color in the "
                            + "sheet this instance cannot see, and a rule over it would fire on "
                            + "every site that loads a framework.</p>"), when);
        });

        assertThat(verifyService.verify(site, "page", 100).findings())
                .as("the same guard as `page-unstyled` and for the same reason, which is worth an "
                        + "assertion of its own: a custom property is far MORE likely than a class "
                        + "to be declared by a framework sheet, since that is what a design-token "
                        + "framework is")
                .noneMatch(finding -> "page-dangling-token".equals(finding.rule()));
    }

    /** A published row — the state the SH516 check reads, and the fixture default is DRAFT. */
    private void published(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                           String data, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(ShPostState.PUBLISHED);
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

    // ── SH990: a revision, rendered ─────────────────────────────────────────────────

    @Test
    @DisplayName("SH990: a revision renders what the page said then, and the banner says it is one")
    void aRevisionRendersWhatThePageSaidThen() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        String siteId = tx.execute(status -> entityManager
                .createQuery("select s.id from ShSite s where s.name = :name", String.class)
                .setParameter("name", siteName)
                .getSingleResult());
        ShPostRevision captured = tx.execute(status -> {
            ShPostUnified home = entityManager.createQuery("select p from ShPostUnified p "
                            + "where p.shSite.id = :site and p.furl = '/' and p.state = :state",
                            ShPostUnified.class)
                    .setParameter("site", siteId)
                    .setParameter("state", ShPostState.DRAFT)
                    .getSingleResult();
            // What the page said before its present text: a copy of the row with an older body,
            // captured the way a publish captures one, and never saved as a row itself.
            ShPostUnified then = new ShPostUnified();
            then.setId(home.getId());
            then.setState(ShPostState.DRAFT);
            then.setShPostType(home.getShPostType());
            then.setTitle("Welcome, as it was");
            then.setFurl(home.getFurl());
            then.setData(json("TEXT", "<p>Said before</p>"));
            return shPostRevisionService.capture(then, ShPostRevision.Reason.SAVE, "admin");
        });
        try {
            ShRenderedPage rendered = shPageRenderService.render(siteName, "/",
                    ShContentPerspective.parse("rev:" + captured.getVersionNo()), Map.of());

            assertThat(rendered.html())
                    .as("SH990: the revision's document, through the page's present layout")
                    .contains("Said before")
                    .contains("Welcome, as it was")
                    .doesNotContain("Hello world");
            assertThat(rendered.revision()).isEqualTo(captured.getVersionNo());
            assertThat(previewBanner.wrap(rendered))
                    .as("SH990: P7 — a banner saying 'Draft preview' over an old revision would "
                            + "misdescribe the one thing it exists to describe")
                    .contains("Revision " + captured.getVersionNo() + " preview");
            assertThat(shPageRenderService.render(siteName, "/", ShPostState.DRAFT).html())
                    .as("SH990: and the draft is untouched — the revision was never written back")
                    .contains("Hello world");
            assertThatThrownBy(() -> shPageRenderService.render(siteName, "/",
                    ShContentPerspective.parse("rev:999999"), Map.of()))
                    .isInstanceOf(com.viglet.shio.api.post.unified.ShPostStateNotFoundException.class);
        } finally {
            tx.executeWithoutResult(status -> shPostRevisionRepository.delete(
                    entityManager.merge(captured)));
        }
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
                ShPostState.DRAFT, "post-id", siteId, null);
    }
}
