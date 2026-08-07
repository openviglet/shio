/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static org.assertj.core.api.Assertions.assertThat;

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

import com.viglet.shio.api.cda.ShCdaService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH583 — the readers agree on purpose, against a row that carries a trailing slash.
 *
 * <h2>Why this fixture and not a unit test</h2>
 *
 * <p>{@code ShFurlSpellingsTest} asserts the generator; {@code ShFurlSpellingLintTest} asserts
 * nobody writes a second one. Neither would have caught the defect SH583 exists for, because the
 * defect is not in any one reader — it is that three of them were asked the same question about
 * <b>one stored value</b> and were free to answer differently.</p>
 *
 * <p>So this stores the value. Nothing in the product writes a trailing-slash {@code furl} yet —
 * SH467 is the commit that starts — so the row is persisted directly, which is exactly the state
 * the instance will be in one task from now. Before SH583, the CDA found this post and
 * {@code ShAgentBatchService} did not, on the same site, from the same URL.</p>
 *
 * <p>Per SH527 the assertion names its id and sits at the boundary the defect crosses: three
 * public entry points, one stored post, both spellings.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShFurlSpellingAgreementTest {

    @Autowired
    private ShAddressResolver resolver;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShCdaService cdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;

    /** Stored the way SH467's source declared it — with the slash. */
    private String directoryPostId;
    /** Stored the way the grammar collapses it — without. */
    private String filePostId;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "slash-site-" + UUID.randomUUID();
            site.setName(siteName);
            site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType postType = new ShPostType();
            postType.setName("Slash" + UUID.randomUUID().toString().substring(0, 8));
            postType.setTitle("Slash");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            directoryPostId = post(site, root, postType, "Catalogue", "/model-catalog/");
            filePostId = post(site, root, postType, "About", "/about");
        });
    }

    @Test
    @DisplayName("SH583: a post stored with the trailing slash resolves from either spelling")
    void aStoredTrailingSlashResolvesBothWays() {
        for (String spelling : new String[] {"/model-catalog", "/model-catalog/"}) {
            assertThat(resolver.resolve("post:" + siteName + spelling).id())
                    .as("ShAddressResolver on '%s'", spelling)
                    .isEqualTo(directoryPostId);

            assertThat(cdaService.getPostByUrl(siteId, spelling).map(post -> post.id()))
                    .as("""
                            ShCdaService on '%s' — the reader whose disagreement is invisible: \
                            the page renders in /preview and the CDA answers 404, on the same \
                            row (SH583)""", spelling)
                    .contains(directoryPostId);

            assertThat(batchService.findPost(site(), spelling).map(ShPostUnified::getId))
                    .as("ShAgentBatchService on '%s' — a write that misses here creates a second "
                            + "post one character away rather than updating this one", spelling)
                    .contains(directoryPostId);
        }
    }

    @Test
    @DisplayName("SH583: the collapsed spelling still resolves from either, unchanged")
    void aStoredCollapsedFurlStillResolvesBothWays() {
        // The direction that already worked. Asserted so the tolerance added for SH467 is not
        // paid for by the case the whole instance is built on.
        for (String spelling : new String[] {"/about", "/about/"}) {
            assertThat(resolver.resolve("post:" + siteName + spelling).id()).isEqualTo(filePostId);
            assertThat(cdaService.getPostByUrl(siteId, spelling).map(post -> post.id()))
                    .contains(filePostId);
            assertThat(batchService.findPost(site(), spelling).map(ShPostUnified::getId))
                    .contains(filePostId);
        }
    }

    /**
     * SH583 — a delivery URL keeps its spelling; an address does not, and that is the design.
     *
     * <p>Two posts one character apart is a state SH467 makes reachable for the first time: a
     * re-capture stores {@code /docs/} beside a {@code /docs} an earlier run left. The CDA is
     * handed the URL a visitor asked for, so it must answer with the row that spelling names —
     * a coin-flip here is content served at random.</p>
     *
     * <p>{@code post:} is deliberately <b>not</b> asserted on the same pair. An address collapses
     * ({@code ShFurlSpellings.canonical}, via {@code ShAddress}) because the render digest hashes
     * the address and {@code /docs} hashing differently from {@code /docs/} would break its
     * stability — so both spellings of the address name the canonical row, and the declared
     * spelling rides on the write instead (SH467's third door). Asserting otherwise here would
     * pin the identity split that design exists to avoid.</p>
     */
    @Test
    @DisplayName("SH583: a delivery URL resolves to the row its own spelling names")
    void theRequestedSpellingWins() {
        String withSlash = "/docs/";
        String withoutSlash = "/docs";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = entityManager.find(ShSite.class, siteId);
            ShFolder root = entityManager
                    .createQuery("select f from ShFolder f where f.shSite = :s and f.rootFolder = 1",
                            ShFolder.class)
                    .setParameter("s", site)
                    .getSingleResult();
            ShPostType type = entityManager
                    .createQuery("select t from ShPostType t where t.name like 'Slash%'",
                            ShPostType.class)
                    .setMaxResults(1)
                    .getSingleResult();
            post(site, root, type, "Docs directory", withSlash);
            post(site, root, type, "Docs file", withoutSlash);
        });

        assertThat(cdaService.getPostByUrl(siteId, withSlash).map(post -> post.title()))
                .contains("Docs directory");
        assertThat(cdaService.getPostByUrl(siteId, withoutSlash).map(post -> post.title()))
                .contains("Docs file");
    }

    private ShSite site() {
        return new TransactionTemplate(transactionManager)
                .execute(status -> entityManager.find(ShSite.class, siteId));
    }

    private String post(ShSite site, ShFolder folder, ShPostType type, String title, String furl) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        // PUBLISHED so the CDA's default read state sees it without a preview token.
        post.setState(ShPostState.PUBLISHED);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        post.setData("{}");
        entityManager.persist(post);
        return post.getId();
    }
}
