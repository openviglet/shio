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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH467 — the write half: a replica stores the trailing slash its source declared.
 *
 * <h2>The defect, measured</h2>
 *
 * <p>{@code openviglet.github.io} serves {@code /model-catalog/} with a <b>200</b> and answers
 * {@code /model-catalog} with a <b>301</b> to it. The replica did the exact mirror. It was filed as
 * a cost — every captured link and bookmark pays a redirect — and it is a <b>correctness</b>
 * defect: a document-relative {@code ./app/main.js} resolves against the URL the page is
 * <em>served at</em>, so it lands on {@code /model-catalog/app/main.js} at the source and on
 * {@code /app/main.js} at the replica. {@code verify} reported it as {@code page-reference-broken},
 * the only reference error on an otherwise clean authorable replica.</p>
 *
 * <h2>Where it died, and what this asserts</h2>
 *
 * <p>Two lines, not thirteen. {@code convert} passes {@code url: page.path} with the slash intact
 * and it reached {@code ShAgentDesiredState.Post.url()} intact; it died at
 * {@code ShAddress.post(site, post.url())}, whose {@code normalizePath} collapses it, and again at
 * {@code setFurl(created.getId(), address.path())}, where the path was already collapsed. So the
 * boundary this asserts at is <b>apply</b>: declare the slash, and the row carries it.</p>
 *
 * <p>The route half shipped earlier and {@code ShRenderRedirectTest} covers it against a
 * hand-persisted {@code /docs/} row, whose fixture comment says in as many words that it is
 * <em>what the capture half needs</em>. This is that half: the two together are the claim the task
 * makes — a page whose source is canonically {@code /x/} is served at {@code /x/}, and {@code /x}
 * 301s to it.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentCanonicalSlashTest {

    @Autowired
    private ShAgentDesiredStateService desiredStateService;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShAddressResolver resolver;
    @Autowired
    private ShAgentReadService readService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String siteId;
    private String postTypeName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "slash-write-" + UUID.randomUUID();
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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow();

            ShPostType postType = new ShPostType();
            postTypeName = "SlashPage" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Slash Page");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setRequired((byte) 1);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });
    }

    @Test
    @DisplayName("SH467: an apply that declares the trailing slash stores it")
    void aDeclaredTrailingSlashIsStored() {
        apply("/model-catalog/", "Model catalogue");

        assertThat(furlOf("/model-catalog"))
                .as("""
                        the row the whole task is about. Before SH467 this was `/model-catalog`, \
                        the source's path with one character removed, and every document-relative \
                        reference on the page then resolved one level too high.""")
                .isEqualTo("/model-catalog/");
    }

    @Test
    @DisplayName("SH467: the address stays collapsed — the spelling is a write, not an identity")
    void theAddressIsUnchangedByTheStoredSpelling() {
        apply("/guides/", "Guides");

        // The design's other half, and the reason the field is on the op rather than a fourth
        // component of ShAddress: a record has value equality, so `post:S/guides` and
        // `post:S/guides/` being unequal would be one post with two identities — and the render
        // digest hashes the address. Both spellings therefore resolve, to one address (SH583).
        assertThat(resolver.resolve("post:" + siteName + "/guides").address())
                .isEqualTo("post:" + siteName + "/guides");
        assertThat(resolver.resolve("post:" + siteName + "/guides/").address())
                .isEqualTo("post:" + siteName + "/guides");
    }

    @Test
    @DisplayName("SH467: applying the same document again converges — the slash is not drift")
    void applyingTwiceIsStillEmpty() {
        // The property that makes a desired-state document worth having. A create that stores `/x/`
        // and a plan that then reads `/x` would report a change forever, which is the failure a
        // reader would call "the apply is not idempotent" rather than "the slash was dropped".
        ShAgentDesiredState desired = document("/handbook/", "Handbook");
        apply(desired);

        assertThat(plan(desired).changed()).isZero();
        assertThat(furlOf("/handbook")).isEqualTo("/handbook/");
    }

    @Test
    @DisplayName("SH467: a collapsed declaration still stores the collapsed form")
    void noSlashDeclaredIsNoSlashStored() {
        // The direction that already worked, and the one every caller before SH467 sends. The field
        // defaults to absent, so nothing about a monolingual, slash-free tree changes.
        apply("/about", "About");

        assertThat(furlOf("/about")).isEqualTo("/about");
    }

    @Test
    @DisplayName("SH467: a declared url that is not a spelling of the address is ignored")
    void aDeclaredUrlNamingAnotherPostIsIgnored() {
        // The guard. `apply` derives the address *from* the url one line earlier, so the only way
        // to reach a genuine disagreement is a hand-built op naming both — and honouring it would
        // let one op create a post at an address it does not name, which is the SH79 write rule.
        // Ignored rather than refused: every pre-SH467 caller sends no url at all, and a 400 on the
        // field's own compatibility default is a refusal aimed at nobody.
        String address = "post:" + siteName + "/pricing";
        run(new ShAgentBatch.Op(ShAgentBatchService.POST_UPSERT, address, postTypeName, "/",
                null, data("Pricing"), null, null, null, "/somewhere-else/"));

        assertThat(furlOf("/pricing")).isEqualTo("/pricing");
    }

    @Test
    @DisplayName("SH585: the read reports the stored spelling, so a pull can write it down")
    void theReadReportsTheStoredSpelling() {
        // SH467 made the spelling storable and nothing could read it back: `address` is the
        // identity and collapses, so the one fact SH467 preserved was invisible to every reader
        // downstream. A `shio pull` of a replica therefore produced a tree that, re-applied to an
        // empty instance, rebuilt every directory-style page one character short — Block I's
        // promise that the tree describes a site whole, quietly false.
        apply("/downloads/", "Downloads");

        assertThat(readService.read(List.of("post:" + siteName + "/downloads"), List.of(), false))
                .singleElement()
                .satisfies(doc -> {
                    assertThat(doc.address())
                            .as("the address is unchanged — it is the identity, and it collapses")
                            .isEqualTo("post:" + siteName + "/downloads");
                    assertThat(doc.url()).isEqualTo("/downloads/");
                });
    }

    @Test
    @DisplayName("SH585: and says nothing when the address already says it")
    void theReadIsSilentWhenThereIsNothingToAdd() {
        // P3, and a stronger version of `locale`'s argument: the default is not merely knowable,
        // it is already on the row. A key repeating the address's own path is one every reader
        // pays for on every post and none of them uses.
        apply("/contact", "Contact");

        assertThat(readService.read(List.of("post:" + siteName + "/contact"), List.of(), false))
                .singleElement()
                .satisfies(doc -> assertThat(doc.url()).isNull());
    }

    // --- helpers ---

    private String furlOf(String path) {
        return new TransactionTemplate(transactionManager).execute(status ->
                shPostUnifiedRepository
                        .findByShSite_IdAndFurlAndState(siteId, path, ShPostState.DRAFT)
                        .or(() -> shPostUnifiedRepository.findByShSite_IdAndFurlAndState(
                                siteId, path + "/", ShPostState.DRAFT))
                        .map(ShPostUnified::getFurl)
                        .orElse(null));
    }

    private JsonNode data(String headline) {
        return objectMapper.valueToTree(Map.of("headline", headline));
    }

    private ShAgentDesiredState document(String url, String headline) {
        return new ShAgentDesiredState(siteName, List.of(),
                List.of(new ShAgentDesiredState.Post(url, postTypeName, "/", data(headline), null)),
                false);
    }

    private void apply(String url, String headline) {
        apply(document(url, headline));
    }

    private void apply(ShAgentDesiredState desired) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                desiredStateService.apply(desired, false, "tester"));
    }

    private com.viglet.shio.api.agent.dto.ShAgentChangeSet plan(ShAgentDesiredState desired) {
        return new TransactionTemplate(transactionManager).execute(status ->
                desiredStateService.plan(desired, false, "tester"));
    }

    private void run(ShAgentBatch.Op op) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                batchService.run(new ShAgentBatch(false, List.of(op)), true, "tester"));
    }
}
