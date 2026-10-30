/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.token.ShPreviewTokenAPI;
import com.viglet.shio.api.token.ShPreviewTokenResponse;
import com.viglet.shio.api.token.ShShareLinksAPI;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.spring.security.auth.ShApiTokenFilter;

import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A blueprint-built site can be looked at (SH518).
 *
 * <h2>What was backwards</h2>
 *
 * <p>{@code shio apply --blueprint marketing-site --site X} wrote everything it promised — eight
 * post-types, four folders, thirteen posts, three pages composed of ten sections, every field
 * authorable — and then {@code /preview/X/} answered <b>400, binds no layout for post-type
 * 'Page'</b>. The generator that writes a {@code Theme} and a {@code PageLayout} existed and lived
 * on the <em>replication</em> path, so a site that started from a blueprint could not be rendered
 * inside Shio. Getting one to render meant harvesting those two posts from a throwaway conversion,
 * retitling them and binding the site by hand.</p>
 *
 * <p>That is backwards for the product: replication is the unusual case, and a blueprint is how
 * something new starts. SH395 made a package able to carry its design; this is the section packages
 * using it.</p>
 *
 * <h2>Its own database, and the reason is a sibling</h2>
 *
 * <p>Post-types are instance-wide. {@code ShBlueprintApplyTest} deliberately creates a mismatched
 * one to prove the mismatch is reported, so every later apply in that class meets it — this
 * assertion was written there first and failed on a {@code Page} type another test had bent on
 * purpose. SH315's rule: a test measuring what a whole apply produces may not share.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh518;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShBlueprintRendersTest {

    @Autowired
    private ShBlueprintService shBlueprintService;
    @Autowired
    private ShPageRenderService shPageRenderService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private com.viglet.shio.api.agent.verify.ShAgentRenderService shAgentRenderService;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShPreviewTokenAPI shPreviewTokenAPI;
    @Autowired
    private ShShareLinksAPI shShareLinksAPI;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentBatchService shAgentBatchService;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    /** SH1110 — a second site, for the site bound SH1042 claimed and nothing asserted. */
    private String otherSiteName;

    @BeforeAll
    void setup() {
        siteName = "sh518-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShSite other = new ShSite();
            otherSiteName = siteName + "-other";
            other.setName(otherSiteName);
            other.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            other.setOwner("admin");
            entityManager.persist(other);
            ShFolder otherRoot = new ShFolder();
            otherRoot.setName("root");
            otherRoot.setRootFolder((byte) 1);
            otherRoot.setShSite(other);
            otherRoot.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            otherRoot.setOwner("admin");
            entityManager.persist(otherRoot);
        });
        shBlueprintService.apply("marketing-site", siteName,
                new ObjectMapper().readTree("{\"brand\":\"Acme\"}"), "admin");
    }

    @Test
    @DisplayName("SH518: applying a section package leaves a page that renders, annotated")
    void aSectionPackageIsVisible() {
        String html = shPageRenderService.render(siteName, "/", ShPostState.PUBLISHED).html();

        assertThat(html)
                .as("""
                        the composed page rendered through the package's own layout: the sections \
                        it declares are on it, not a labelled fallback saying nothing is bound. \
                        This is the assertion SH518 names — apply a blueprint to a fresh site and \
                        /preview answers 200 (SH518).""")
                .contains("data-shio-section")
                .doesNotContain("data-shio-fallback");
        assertThat(html)
                .as("""
                        and it is EDITABLE: `sh-field` becomes `data-shio-prop`, which is what the \
                        Universal Editor attaches to. A page that renders and cannot be edited \
                        puts the curator back in the console form the visual editor exists to \
                        remove (SH518).""")
                .contains("data-shio-prop");
        assertThat(html)
                .as("the theme the layout names resolved, so `theme.css` is a stylesheet rather "
                        + "than an empty <style> — the trap SH280 records for a THEME that names "
                        + "a post the site has not got")
                .contains("--accent");

        // SH873 — the assertion that would have caught it, at the boundary it crossed: the page a
        // new user gets from the package. The nav was `{{#query type="Page" sort="title"}}`, so a
        // page titled for search became the menu entry and every page was one, alphabetically —
        // measured on a pharmacy site as `Contato, Farmacêutico responsável — Farmácia Vitalis,
        // Farmácia Vitalis, Produtos — Farmácia Vitalis`. Here, `About Acme` and `Acme` would have
        // sorted ahead of `Pricing` and put the home page in the middle under the brand name.
        String nav = html.substring(html.indexOf("<nav"), html.indexOf("</nav>"));
        assertThat(nav)
                .as("the labels are the menu's, written for a menu")
                .contains(">Home<").contains(">Pricing<").contains(">About<")
                .doesNotContain("About Acme");
        assertThat(nav.indexOf(">Home<"))
                .as("and the order is the menu's — editorial, not alphabetical, which is the "
                        + "second half a page listing has no way to express")
                .isLessThan(nav.indexOf(">About<"));

        // SH884 — the assertion that would have caught it, at the boundary it crossed: the page a
        // new user gets from the package. Every SECTION on it was annotated and the page's own
        // fields were annotated nowhere, so a curator could edit a hero's heading and not the
        // title of the page it sits on. Measured live: `Page | 0/4 | DRAWN: description, title`.
        assertThat(html)
                .as("""
                        `title` is annotated where the layout already draws it, so the one field \
                        every page has is editable in place and no pixel moved. `description` \
                        reaches only <meta>, where an editor cannot attach, so it is declared in a \
                        hidden element — SH766's rule: annotate without drawing, rather than \
                        inventing a visible element to make a field reachable.""")
                .contains("data-shio-prop=\"title\"")
                .contains("data-shio-prop=\"description\"");
    }

    /**
     * SH1109 — what the visual editor's save proof costs, measured before it is spent.
     *
     * <p>A save that names what moved takes a digest of the page before the write and another after,
     * which is two renders on the curator's clock. This is the composed home page the marketing
     * package builds — ten sections through a layout and its regions — digested through the same
     * path the console asks. The median is printed and recorded in docs/agents/renderer.md; it is
     * what decided that the pair is taken on every save rather than on request.</p>
     */
    @Test
    @DisplayName("SH1109: the digest pair a save takes is measured on a composed page")
    void aSaveProofIsMeasured() {
        String address = "id:" + aHomePageId();
        for (int warm = 0; warm < 5; warm++) {
            shAgentRenderService.digest(address, null, null);
        }
        long[] nanos = new long[21];
        String first = null;
        for (int run = 0; run < nanos.length; run++) {
            long start = System.nanoTime();
            String digest = shAgentRenderService.digest(address, null, null).digest();
            nanos[run] = System.nanoTime() - start;
            first = first == null ? digest : first;
            assertThat(digest).as("the digest is stable across reads, or a delta would invent a move")
                    .isEqualTo(first);
        }
        java.util.Arrays.sort(nanos);
        System.out.printf("SH1109: one digest of the composed home page: median %.1f ms, max %.1f ms%n",
                nanos[nanos.length / 2] / 1e6, nanos[nanos.length - 1] / 1e6);
        assertThat(nanos[nanos.length / 2]).isPositive();
    }

    /**
     * SH1110 — a share link is one page wide, and that page still renders whole.
     *
     * <p>Here because the page has to render for the answer to mean anything: this class has a
     * composed site on its own database, and the suite has no context left to spend (SH423). The
     * link is minted through the real endpoint and exchanged through the real route, so the
     * credential the render sees is the one a recipient's browser would carry.</p>
     */
    @Test
    @DisplayName("SH1110: a share link renders its page whole and refuses every other page of the site")
    void aShareLinkIsOnePageWide() throws Exception {
        MockMvc secured = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        // In a transaction because a request has one (open-in-view) and a direct call does not.
        String pricing = pageAt("/pricing");
        ShPreviewTokenResponse minted = new TransactionTemplate(transactionManager).execute(status ->
                (ShPreviewTokenResponse) shPreviewTokenAPI
                        .mint(new ShPreviewTokenAPI.MintRequest(pricing, 600L), () -> "admin").getBody());
        assertThat(minted).isNotNull();
        MvcResult exchanged = secured.perform(get(URI.create(minted.previewUrl())))
                .andExpect(status().is3xxRedirection()).andReturn();
        Cookie share = exchanged.getResponse().getCookie(ShApiTokenFilter.SHARE_COOKIE);
        assertThat(share).isNotNull();
        String landing = exchanged.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(landing).isEqualTo("/preview/" + siteName + "/pricing");

        String html = secured.perform(get(landing).cookie(share)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html)
                .as("the shared page renders whole: the banner that says it is a draft (SH1110)")
                .contains("data-shio-preview=\"true\"")
                .as("and its stylesheet, which the theme inlines")
                .contains("--accent");
        for (org.jsoup.nodes.Element image : org.jsoup.Jsoup.parse(html).select("img[src], link[href][rel=stylesheet]")) {
            String source = image.hasAttr("src") ? image.attr("src") : image.attr("href");
            if (source.startsWith("/preview/")) {
                secured.perform(get(source).cookie(share))
                        .andExpect(status().isOk());
            }
        }

        for (String sibling : new String[] { "/", "/about", "/menu/home" }) {
            secured.perform(get("/preview/" + siteName + sibling).cookie(share))
                    .andExpect(result -> assertThat(result.getResponse().getStatus())
                            .as("SH1110: a link for /pricing refused on %s, another page of the same site",
                                    sibling)
                            .isEqualTo(404));
        }
        secured.perform(get("/preview/" + otherSiteName + "/").cookie(share))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("SH1042: and on another site entirely, which nothing had asserted")
                        .isEqualTo(404));
    }

    /**
     * SH1322 — a share link renders the page it was minted for when that page has never been published.
     *
     * <p>Measured live, it was not about drafts: on a site named "Console Check" every page's link
     * answered Spring's Whitelabel 404, because the renderer looked for the page after the site's
     * <em>decoded</em> name in a path that still carried it encoded, found nothing, and asked for
     * `/`, which a one-page link refuses. A curator's own preview of any page rendered the home. So
     * this renames the site to need encoding for its duration, and previews a draft-only page.</p>
     */
    @Test
    @DisplayName("SH1322: a share link renders its draft-only page on a site whose name needs encoding")
    void aShareLinkRendersANeverPublishedDraft() throws Exception {
        String spaced = siteName + " spaced";
        renameSite(siteName, spaced);
        try {
            aShareLinkRendersADraftOn(spaced);
        } finally {
            renameSite(spaced, siteName);
        }
    }

    private void renameSite(String from, String to) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> entityManager
                .createQuery("update ShSite s set s.name = :to where s.name = :from")
                .setParameter("to", to).setParameter("from", from).executeUpdate());
    }

    private void aShareLinkRendersADraftOn(String siteName) throws Exception {
        MockMvc secured = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        shAgentBatchService.run(new com.viglet.shio.api.agent.dto.ShAgentBatch(false, List.of(
                new com.viglet.shio.api.agent.dto.ShAgentBatch.Op(com.viglet.shio.api.agent.ShAgentBatchService.POST_UPSERT,
                        "post:" + siteName + "/sh1322-draft", "Page", "/", null,
                        new ObjectMapper().readTree("{\"title\":\"SH1322 draft only\"}")))), false, "ut-sh1322");
        String draft = pageAt(siteName, "/sh1322-draft");
        ShPreviewTokenResponse minted = new TransactionTemplate(transactionManager).execute(status ->
                (ShPreviewTokenResponse) shPreviewTokenAPI
                        .mint(new ShPreviewTokenAPI.MintRequest(draft, 600L), () -> "admin").getBody());
        assertThat(minted).isNotNull();
        MvcResult exchanged = secured.perform(get(URI.create(minted.previewUrl()))).andReturn();
        Cookie share = exchanged.getResponse().getCookie(ShApiTokenFilter.SHARE_COOKIE);
        String landing = exchanged.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(landing).as("SH1322: the landing path carries the site's name encoded").contains("%20");
        MvcResult page = secured.perform(get(URI.create(landing)).cookie(share)).andReturn();
        assertThat(page.getResponse().getStatus())
                .as("SH1322: the share link for a never-published draft answered %s at %s, so Preview "
                        + "fails on the one page that has no other way to be seen", page.getResponse().getStatus(), landing)
                .isEqualTo(200);
        assertThat(page.getResponse().getContentAsString()).contains("SH1322 draft only");
    }

    /**
     * SH1111 — a live link is listed, and a revoked one is refused at the preview.
     *
     * <p>Revocation already worked in the filter; what was missing was a way to reach it. So the
     * assertion is the loop a person runs: find the link they sent in the listing, stop it, and the
     * reader's next request is refused.</p>
     */
    @Test
    @DisplayName("SH1111: a live share link is listed, and once revoked it is refused at the preview")
    void aRevokedShareLinkIsRefused() throws Exception {
        MockMvc secured = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        String about = pageAt("/about");
        ShPreviewTokenResponse minted = new TransactionTemplate(transactionManager).execute(status ->
                (ShPreviewTokenResponse) shPreviewTokenAPI
                        .mint(new ShPreviewTokenAPI.MintRequest(about, 600L), () -> "admin").getBody());
        assertThat(minted).isNotNull();
        MvcResult exchanged = secured.perform(get(URI.create(minted.previewUrl()))).andReturn();
        Cookie share = exchanged.getResponse().getCookie(ShApiTokenFilter.SHARE_COOKIE);
        String landing = exchanged.getResponse().getHeader(HttpHeaders.LOCATION);
        secured.perform(get(landing).cookie(share)).andExpect(status().isOk());

        List<ShShareLinksAPI.ShShareLink> live = shShareLinksAPI.live();
        ShShareLinksAPI.ShShareLink link = live.stream()
                .filter(each -> siteName.equals(each.site()) && "/about".equals(each.page()))
                .findFirst().orElseThrow();
        assertThat(link.owner()).isEqualTo("admin");
        assertThat(new ObjectMapper().writeValueAsString(live))
                .as("the listing names a link by its row id and never carries the credential")
                .doesNotContain(minted.token());

        shShareLinksAPI.revoke(link.id());

        secured.perform(get(landing).cookie(share))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("SH1111: a revoked link is refused at the preview, from the next request")
                        .isEqualTo(401));
        assertThat(shShareLinksAPI.live()).extracting(ShShareLinksAPI.ShShareLink::id)
                .as("and it is no longer live")
                .doesNotContain(link.id());
    }

    private String pageAt(String furl) {
        return pageAt(siteName, furl);
    }

    private String pageAt(String site, String furl) {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select p from ShPostUnified p where p.furl = :furl", ShPostUnified.class)
                .setParameter("furl", furl).getResultList().stream()
                .filter(post -> post.getShSite() != null && site.equals(post.getShSite().getName()))
                .map(ShPostUnified::getId).findFirst().orElseThrow());
    }

    private String aHomePageId() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select p from ShPostUnified p where p.furl = '/' and p.shPostType.id in "
                        + "(select t.id from ShPostType t where t.name = 'Page')", ShPostUnified.class)
                .getResultList().stream()
                .filter(post -> post.getShSite() != null && siteName.equals(post.getShSite().getName()))
                .map(ShPostUnified::getId).findFirst().orElseGet(this::aPageId));
    }

    // --- SH1105: the inserter rail's thumbnail, on the page this package builds ---
    // Here and not in a class of its own: an apply needs its own database (SH315), the suite has no
    // context slot left (SH423), and two classes on one database is what ShBenchmarkIsolationLintTest
    // refuses.

    private String aPageId() {
        return new TransactionTemplate(transactionManager).execute(status -> entityManager
                .createQuery("select p from ShPostUnified p where p.shPostType.id in "
                        + "(select t.id from ShPostType t where t.name = 'Page')", ShPostUnified.class)
                .getResultList().stream()
                .filter(post -> post.getShSite() != null && siteName.equals(post.getShSite().getName()))
                .map(ShPostUnified::getId).findFirst().orElseThrow());
    }

    @Test
    @DisplayName("SH1105: a thumbnail renders the type through the page's own layout, cropped to it")
    void aThumbnailIsThePreviewPathCropped() {
        String html = shPageRenderService.sectionPreview(aPageId(), "sections", "SectionHero").orElseThrow();

        assertThat(html)
                .as("""
                        one section, of the type asked for, carrying its fields' labels as placeholder \
                        text — the shape a curator is choosing, not a sentence about it (SH1105)""")
                .contains("data-shio-section=\"SectionHero\"")
                .contains("Heading")
                .contains("Subheading");
        assertThat(html)
                .as("and the page's theme came with it: the layout rendered it, not a second renderer")
                .contains("--accent");
        assertThat(html)
                .as("a thumbnail runs nothing: it is drawn in a frame beside the page being edited")
                .doesNotContainIgnoringCase("<script");
        assertThat(html.split("data-shio-section=\"", -1))
                .as("the other sections of the page are cropped away")
                .hasSize(2);
    }

    @Test
    @DisplayName("SH1105: an unknown type or page answers nothing rather than a guess")
    void anUnknownThumbnailIsEmpty() {
        assertThat(shPageRenderService.sectionPreview(aPageId(), "sections", "NoSuchType")).isEmpty();
        assertThat(shPageRenderService.sectionPreview("no-such-page", "sections", "SectionHero")).isEmpty();
    }

    @Test
    @DisplayName("SH1323: a section's unsaved values are drawn on the page that uses it, and stored nowhere")
    void aSectionIsPreviewedOnItsPage() {
        String hero = "post:" + siteName + "/sections/pricing/hero";
        var proposed = new ObjectMapper().createObjectNode().put("heading", "SH1323 unsaved headline");

        String html = shAgentRenderService.proposed(hero, "draft", proposed, "post:" + siteName + "/pricing",
                true).html();

        assertThat(html)
                .as("SH1323: the hero's edit is not on the Pricing page drawn for it, so the curator "
                        + "edits a headline without seeing the page it appears on")
                .contains("SH1323 unsaved headline");
        assertThat(html)
                .as("SH1323: the page is drawn whole, its other sections around the one being edited")
                .contains("data-shio-section=\"SectionFaq\"");
        assertThat(shPageRenderService.renderProposed(siteName, "/pricing", ShPostState.DRAFT, null, java.util.Map.of())
                        .html())
                .as("SH1323: the proposal reached the stored page")
                .doesNotContain("SH1323 unsaved headline");
    }
}
