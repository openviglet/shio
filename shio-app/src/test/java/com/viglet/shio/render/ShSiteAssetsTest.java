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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH437 — a captured asset resolves at the path the source used.
 *
 * <h2>What this pins, and why it is not obvious from either side</h2>
 *
 * <p>SH429 shipped {@code source-asset-stranded} — the finding that says an asset is in the tree
 * and nothing serves it — and left the delivery unbuilt, because keeping the source's URL space
 * needed a route that did not exist. Both halves of it look correct in isolation: the storage layer
 * serves bytes at {@code /file_source/<postId>/<name>} and the file projection addresses an asset by
 * <b>folder chain plus file name</b> (SH145). Nothing joined them, so a replica's
 * {@code <img src="/img/hero.png">} was as broken with the bytes uploaded as without them.</p>
 *
 * <p>So the assertions here are about the join, in both directions: the resolver walks a source path
 * to the post SH145's addressing would have put there, and the route answers with the bytes. The
 * traversal cases are asserted too — a path may not climb out of the site it named, and a folder
 * that is not there is a miss rather than a fall-through to the site root.</p>
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteAssetsTest {

    private static final Path FILE_SOURCE_DIR = tempRoot();

    private static Path tempRoot() {
        try {
            return Files.createTempDirectory("sh437-file-source");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void fileSourcePath(DynamicPropertyRegistry registry) {
        registry.add("shio.file-source.path", () -> FILE_SOURCE_DIR.toAbsolutePath().toString());
    }

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private ShSiteAssets shSiteAssets;
    @Autowired
    private ShPreviewLinks shPreviewLinks;
    @Autowired
    private ShStaticFileService shStaticFileService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private MockMvc mockMvc;
    private String siteName;
    private ShSite site;
    private String assetId;

    @BeforeAll
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        siteName = "sh437-" + UUID.randomUUID().toString().substring(0, 8);

        String imgFolderId = new TransactionTemplate(transactionManager).execute(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite created = new ShSite();
            created.setName(siteName);
            created.setDate(when);
            created.setOwner("admin");
            entityManager.persist(created);

            ShFolder root = new ShFolder();
            root.setName(siteName);
            root.setRootFolder((byte) 1);
            root.setShSite(created);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            // `/img/hero.png` is a folder chain of one plus a file name — SH145's addressing, which
            // is the thing this resolver reads backwards.
            ShFolder img = new ShFolder();
            img.setName("img");
            img.setParentFolder(root);
            img.setRootFolder((byte) 0);
            img.setDate(when);
            img.setOwner("admin");
            entityManager.persist(img);
            return img.getId();
        });

        site = new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("select s from ShSite s where s.name = :name", ShSite.class)
                        .setParameter("name", siteName).getSingleResult());

        assetId = shStaticFileService.upload(new MockMultipartFile(
                "file", "hero.png", "image/png",
                "PNG-BYTES".getBytes(StandardCharsets.UTF_8)), imgFolderId, "admin").getId();
    }

    @Test
    @DisplayName("SH437: the source's own path resolves to the File post SH145 addressed")
    void aSourcePathResolvesToItsFilePost() {
        assertThat(shSiteAssets.find(site, "/img/hero.png", ShPostState.DRAFT))
                .as("the join SH429 measured as missing: nothing mapped a source path to a File post")
                .isPresent()
                .get()
                .extracting(post -> post.getId())
                .isEqualTo(assetId);

        // The leading slash is a URL's convention and not part of the address, so both forms answer.
        assertThat(shSiteAssets.find(site, "img/hero.png", ShPostState.DRAFT)).isPresent();
    }

    @Test
    @DisplayName("SH437: a path that names no folder, no file or the site root itself is a miss")
    void everythingElseResolvesToNothing() {
        assertThat(shSiteAssets.find(site, "/img/missing.png", ShPostState.DRAFT)).isEmpty();
        assertThat(shSiteAssets.find(site, "/nope/hero.png", ShPostState.DRAFT))
                .as("an absent folder is a miss, never a fall-through to the root — which would make "
                        + "every file in a site reachable at every path in it")
                .isEmpty();
        assertThat(shSiteAssets.find(site, "/hero.png", ShPostState.DRAFT))
                .as("and the chain is exact: the asset is in /img, so the root does not serve it")
                .isEmpty();
        assertThat(shSiteAssets.find(site, "/img/", ShPostState.DRAFT)).isEmpty();
        assertThat(shSiteAssets.find(site, "", ShPostState.DRAFT)).isEmpty();
    }

    @Test
    @DisplayName("SH437: `..` cannot climb out of the site the path named")
    void traversalIsDroppedRatherThanResolved() {
        // Dropped, not honoured, and the difference is visible: dropping leaves `img/img/hero.png`,
        // which is a folder that does not exist, so the path misses. Resolving would leave
        // `img/hero.png` and hit. **Dropping is the stricter of the two and that is the point** —
        // this walks a folder tree rather than a file system, and a resolver that applied `..`
        // would let a path spell a chain that is not the one it appears to name. A root-relative
        // URL with `..` in it is not something a source emits; it is something a prober sends.
        assertThat(shSiteAssets.find(site, "/img/../img/hero.png", ShPostState.DRAFT))
                .as("what remains is img + img + hero.png — a miss, not the hit `..` was aiming for")
                .isEmpty();
        assertThat(shSiteAssets.find(site, "/../../etc/passwd", ShPostState.DRAFT)).isEmpty();
        assertThat(shSiteAssets.find(site, "/img/./hero.png", ShPostState.DRAFT))
                .as("`.` is dropped too, and it names the folder it is already in, so this one hits")
                .isPresent();
    }

    @Test
    @DisplayName("SH437: a PUBLISHED read never sees an asset that only exists as a draft")
    void aPublishedReadIsStateMatched() {
        // `upload` writes PUBLISHED (SH38), so this asserts the rule holds in the direction it can:
        // the published asset is visible to both reads. The inverse is SH278's rule, already pinned.
        assertThat(shSiteAssets.find(site, "/img/hero.png", ShPostState.PUBLISHED)).isPresent();
    }

    @Test
    @DisplayName("SH437: the route answers with the bytes, cacheable and never indexed")
    void theRouteServesTheBytes() throws Exception {
        mockMvc.perform(get("/preview/" + siteName + "/img/hero.png").accept("image/png"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("PNG-BYTES"))
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .startsWith("image/"))
                // An asset is not a page: the preview's `no-store` exists so a draft page is never
                // remembered, and immutable bytes behind an authenticated route are a different case.
                .andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control"))
                        .contains("max-age").contains("private"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-Robots-Tag"))
                        .isEqualTo("noindex, nofollow"));
    }

    /**
     * SH461 — the same page, the same asset, the two rules that used to disagree about it.
     *
     * <p>A fidelity replica's markup is verbatim, so its {@code <img src="/img/hero.png">} is the
     * source's own path and SH437's pass points it here. Its stylesheet was rewritten by the capture
     * to the asset <b>tree</b>'s path, which is addressable in {@code shio/assets/} and nowhere a
     * browser can ask for — so the logo loaded and the webfonts 404'd, on one page, from one
     * capture. This asserts the two agree: the {@code url()} and the {@code src} name the same
     * served path, and everything the site does not serve is left exactly as the author wrote it.</p>
     */
    @Test
    @DisplayName("SH461: a url() inside <style> is rewritten by the rule that already works for src")
    void aStylesheetsUrlGetsTheSameTreatmentAsAnImgSrc() {
        String prefix = "/preview/" + siteName;
        String served = prefix + "/img/hero.png";

        String html = shPreviewLinks.rewriteInto("""
                <html><head><style>\
                @font-face{src:url("/img/hero.png")}\
                a{background:URL(/img/hero.png)}\
                b{background:url('/img/hero.png')}\
                c{background:url(/img/missing.png)}\
                d{background:url(https://cdn.example.com/x.png)}\
                e{background:url(data:image/gif;base64,R0lGOD)}\
                </style></head><body><img src="/img/hero.png"></body></html>""",
                prefix, pageOfThisSite());

        assertThat(html)
                .as("all three quotings resolve, and each keeps the quoting the author used — a "
                        + "bare url() that gained quotes would be a rewrite editing more than the path")
                .contains("url(\"" + served + "\")")
                .contains("url(" + served + ")")
                .contains("url('" + served + "')")
                .as("and the markup's own reference agrees with them, which is the whole finding")
                .contains("src=\"" + served + "\"");
        assertThat(html)
                .as("everything this site does not serve is left alone, exactly as the src rule "
                        + "already leaves it: a missing path, somebody else's host, an inline datum")
                .contains("url(/img/missing.png)")
                .contains("url(https://cdn.example.com/x.png)")
                .contains("url(data:image/gif;base64,R0lGOD)");

        assertThat(shPreviewLinks.rewriteInto(html, prefix, pageOfThisSite()))
                .as("and the pass is idempotent here too — a second render must not stack prefixes")
                .isEqualTo(html);
    }

    /** Built rather than rendered: the unit under test is the rewrite, not this fixture's layout. */
    private ShPageRenderService.ShRenderedPage pageOfThisSite() {
        return new ShPageRenderService.ShRenderedPage("", siteName, "/", "layout", List.of(),
                ShPostState.DRAFT, "post-id", site.getId());
    }

    @Test
    @DisplayName("SH437: a path that is not an asset teaches where an asset comes from")
    void anUnknownAssetTeaches() {
        ShSiteAssetDelivery.UnknownAssetException problem =
                new ShSiteAssetDelivery.UnknownAssetException(siteName, "/img/gone.png");

        assertThat(problem.problem().toProblemDetail().getProperties())
                .extractingByKey("fix").asString()
                .contains("shio/assets/" + siteName + "/img/gone.png")
                .contains("shio push --content")
                // P6: the finding that already reports this state by name, so a reader who hits the
                // 404 and a reader who runs the gate arrive at one sentence.
                .contains("shio verify --checks replication");
    }
}
