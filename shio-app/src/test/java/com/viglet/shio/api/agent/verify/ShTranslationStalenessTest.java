/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.api.post.unified.dto.ShConsolePost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.UpdateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.ShDocumentDigest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1058 — a translation knows which version of its source it was written against.
 *
 * <p>Before this, {@code translate} copied the source's document and recorded the group, and nothing
 * else: a site could be fully translated, then wrong, and the only translation rule asked about a
 * translation that was absent. Now the translation stamps a digest of the source at write time, and
 * {@code verify} reports {@code translation-stale} when the source reads differently since. It is
 * driven through the real write service rather than seeded rows, because the stamp is the write's
 * behaviour and a fixture that set it by hand would be asserting the fixture.</p>
 *
 * <p>Shared context: every read here is scoped to a site this class creates.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShTranslationStalenessTest {

    private static final String RULE = "translation-stale";

    @Autowired
    private ShPostUnifiedWriteService write;
    @Autowired
    private com.viglet.shio.api.agent.ShAgentReadService shAgentReadService;
    @Autowired
    private com.viglet.shio.api.post.unified.ShConsolePostReadService consoleReads;
    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String rootId;
    private String typeId;

    @BeforeAll
    void seed() {
        siteName = "sh1058-" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);
            rootId = root.getId();

            ShPostType type = new ShPostType();
            type.setName("Sh1058Page" + UUID.randomUUID().toString().substring(0, 6));
            type.setTitle("SH1058 page");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);
            typeId = type.getId();
        });
    }

    @Test
    @DisplayName("SH1058: a source edit makes its translation stale, and the translation's own edit clears it")
    void aSourceEditIsReportedUntilTheTranslationAnswersIt() {
        ShPostUnified source = create("/about", "en", "{\"body\":\"hello\"}", null);
        ShPostUnified translation = create("/sobre", "pt-br", "{\"body\":\"ola\"}", source.getId());

        assertThat(translation.getSourceDigest())
                .as("SH1058: the translation records which version of the source it was written against")
                .isEqualTo(ShDocumentDigest.of(source.getData(), objectMapper));
        assertThat(source.getSourceDigest()).as("and a source records nothing: it translates no one")
                .isNull();
        assertThat(staleAt("sobre"))
                .as("SH1058: a translation of the source as it reads now is current").isEmpty();

        write.update(source.getId(), new UpdateRequest(json("{\"body\":\"hello again\"}"),
                ShPostState.DRAFT, "admin"));

        List<Finding> found = staleAt("sobre");
        assertThat(found).as("SH1058: the source moved on and nothing said so").hasSize(1);
        assertThat(found.getFirst().severity()).isEqualTo("warning");
        assertThat(found.getFirst().address()).isEqualTo("post:" + siteName + "/sobre");
        assertThat(found.getFirst().message()).contains("post:" + siteName + "/about");

        write.update(translation.getId(), new UpdateRequest(json("{\"body\":\"ola de novo\"}"),
                ShPostState.DRAFT, "admin"));

        assertThat(staleAt("sobre"))
                .as("SH1058: the translator's own save records the source as it reads then")
                .isEmpty();
    }

    @Test
    @DisplayName("SH1058: the agent read and the console say it, and say nothing when it is current")
    void everyReadSurfaceReportsIt() {
        ShPostUnified source = create("/terms", "en", "{\"body\":\"terms\"}", null);
        ShPostUnified translation = create("/termos", "pt-br", "{\"body\":\"termos\"}", source.getId());
        String address = "post:" + siteName + "/termos";

        assertThat(read(address).translationStale())
                .as("SH1058: a translation of the source as it reads now says nothing, so a "
                        + "monolingual instance's reads are byte-identical")
                .isNull();
        assertThat(console(translation.getId()).translationStale()).isNull();

        write.update(source.getId(), new UpdateRequest(json("{\"body\":\"terms, revised\"}"),
                ShPostState.DRAFT, "admin"));

        assertThat(read(address).translationStale())
                .as("SH1058: shio_read is where an agent finds out it is about to edit a page whose "
                        + "source moved")
                .isTrue();
        assertThat(console(translation.getId()).translationStale())
                .as("SH1058: and the console badges the post from the same rule")
                .isTrue();
        assertThat(console(source.getId()).translationStale())
                .as("SH1058: the source translates nothing, so it is never stale")
                .isNull();
    }

    @Test
    @DisplayName("SH1058: the translations list reads its source once, whatever the language count")
    void theTranslationsListSaysWhichSiblingsLag() {
        ShPostUnified source = create("/help", "en", "{\"body\":\"help\"}", null);
        ShPostUnified portuguese = create("/ajuda", "pt-br", "{\"body\":\"ajuda\"}", source.getId());
        create("/aide", "fr", "{\"body\":\"aide\"}", source.getId());

        write.update(source.getId(), new UpdateRequest(json("{\"body\":\"help, revised\"}"),
                ShPostState.DRAFT, "admin"));
        write.update(portuguese.getId(), new UpdateRequest(json("{\"body\":\"ajuda revisada\"}"),
                ShPostState.DRAFT, "admin"));

        List<ShConsolePost> siblings = consoleReads.translations(source.getId());

        assertThat(siblings).extracting(ShConsolePost::furl, ShConsolePost::translationStale)
                .as("SH1058: the menu has to say WHICH language needs a pass, not that one does")
                .contains(tuple("ajuda", null), tuple("aide", true), tuple("help", null));
    }

    @Test
    @DisplayName("SH1058: the published row carries the stamp the draft had")
    void publishingKeepsTheStamp() {
        ShPostUnified source = create("/pricing", "en", "{\"body\":\"price\"}", null);
        ShPostUnified translation = create("/precos", "pt-br", "{\"body\":\"preco\"}", source.getId());

        write.publish(translation.getId(), "admin");

        ShPostUnified published = shPostUnifiedRepository
                .findByIdAndState(translation.getId(), ShPostState.PUBLISHED).orElseThrow();
        assertThat(published.getSourceDigest())
                .as("SH1058: a publish that dropped the stamp would make every live translation unknowable")
                .isEqualTo(translation.getSourceDigest());
    }

    @Test
    @DisplayName("SH1058: the digest is of what a document says, not how its keys are ordered")
    void theDigestIgnoresKeyOrder() {
        String digest = ShDocumentDigest.of("{\"a\":1,\"b\":{\"x\":[1,2],\"y\":\"z\"}}", objectMapper);

        assertThat(ShDocumentDigest.of("{\"b\":{\"y\":\"z\",\"x\":[1,2]},\"a\":1}", objectMapper))
                .as("SH1058: a re-save that reorders keys is not an edit, or every translation of a "
                        + "re-saved page would read stale")
                .isEqualTo(digest);
        assertThat(ShDocumentDigest.of("{\"a\":1,\"b\":{\"x\":[2,1],\"y\":\"z\"}}", objectMapper))
                .as("SH1058: but a list in another order is a different page")
                .isNotEqualTo(digest);
        assertThat(digest).hasSize(64);
    }

    private ShAgentDoc read(String address) {
        return shAgentReadService.read(List.of(address), List.of(), false).getFirst();
    }

    private ShConsolePost console(String postId) {
        return consoleReads.get(postId, ShPostState.DRAFT).orElseThrow();
    }

    /**
     * The staleness findings for one page.
     *
     * <p>By page and not by site: the tests share this class's site, and each leaves its own pair
     * behind — an assertion on the whole site's findings would be an assertion about which test ran
     * first (SH315's rule, inside one class).</p>
     */
    private List<Finding> staleAt(String furl) {
        return shAgentVerifyService.verify(siteName, "content", 200).findings().stream()
                .filter(finding -> RULE.equals(finding.rule()))
                .filter(finding -> ("post:" + siteName + "/" + furl).equals(finding.address()))
                .toList();
    }

    private ShPostUnified create(String furl, String locale, String data, String group) {
        return write.create(new CreateRequest(rootId, typeId, json(data), ShPostState.DRAFT, "admin",
                locale, group, furl));
    }

    private JsonNode json(String text) {
        return objectMapper.readTree(text);
    }
}
