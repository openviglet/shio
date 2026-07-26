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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.ObjectMapper;

/**
 * SH83's content reads ({@code find} / {@code read}) and SH123's field projection.
 *
 * <p>The properties under test: find returns <b>identifiers, not bodies</b> (the reason
 * it is a separate tool at all), reads are <b>DRAFT-preferred</b> so an agent can see
 * its own unpublished work, and a projection naming a field the type does not have is
 * <b>refused</b> rather than answered with an empty document — the one way this feature
 * could cause real damage.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentReadTest {

    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;
    private String base;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "read-site-" + UUID.randomUUID();
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

            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseGet(() -> {
                        ShWidget created = new ShWidget();
                        created.setName("Text");
                        created.setDescription("Text");
                        entityManager.persist(created);
                        return created;
                    });

            ShPostType postType = new ShPostType();
            postTypeName = "ReadArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Read Article");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);

            persistAttr(postType, widget, "headline", 1, (byte) 1);
            persistAttr(postType, widget, "body", 2, (byte) 0);
        });

        base = "/reading-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base,
                        null, null, null),
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base + "/deep",
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/alpha",
                        postTypeName, base, data("Alpha", "The alpha body")),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/beta",
                        postTypeName, base + "/deep", data("Beta", "The beta body")))),
                false, "tester"));
    }

    private void persistAttr(ShPostType postType, ShWidget widget, String name, int ordinal, byte title) {
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(name);
        attr.setLabel(name);
        attr.setOrdinal(ordinal);
        attr.setIsTitle(title);
        attr.setRequired((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        entityManager.persist(attr);
    }

    private tools.jackson.databind.JsonNode data(String headline, String body) {
        return objectMapper.valueToTree(Map.of("headline", headline, "body", body));
    }

    private String address(String suffix) {
        return "post:" + siteName + base + suffix;
    }

    // --- find ---

    @Test
    void findReturnsAddressesAndTitlesButNoContent() {
        ShAgentFound found = shAgentReadService.find(siteName, null, base, null, null, false);

        assertThat(found.count()).isEqualTo(2);
        assertThat(found.results()).extracting(ShAgentFound.Row::address)
                .containsExactly(address("/alpha"), address("/beta"));
        assertThat(found.results()).allSatisfy(row -> {
            assertThat(row.title()).isNotBlank();
            assertThat(row.state()).isEqualTo("DRAFT");
            // No body field exists on the row at all — that is the point of two tools.
            assertThat(row.id()).isNull();
        });
        assertThat(objectMapper.writeValueAsString(found)).doesNotContain("body");
    }

    @Test
    void aFolderFilterIncludesTheSubtree() {
        // "Everything under /section" is the question actually being asked; matching
        // only the exact folder would silently miss the pages one level down.
        assertThat(shAgentReadService.find(siteName, null, base, null, null, false).results())
                .extracting(ShAgentFound.Row::address).contains(address("/beta"));
        assertThat(shAgentReadService.find(siteName, null, base + "/deep", null, null, false).results())
                .extracting(ShAgentFound.Row::address).containsExactly(address("/beta"));
    }

    @Test
    void findFiltersByPostType() {
        assertThat(shAgentReadService.find(siteName, postTypeName, base, null, null, false).count())
                .isEqualTo(2);
    }

    @Test
    void findMatchesFullText() {
        assertThat(shAgentReadService.find(siteName, null, base, "Alpha", null, false).results())
                .extracting(ShAgentFound.Row::address).containsExactly(address("/alpha"));
    }

    @Test
    void findSaysWhenItCutTheResultSet() {
        ShAgentFound found = shAgentReadService.find(siteName, null, base, null, 1, false);

        assertThat(found.count()).isEqualTo(1);
        assertThat(found.truncated()).isTrue();
    }

    @Test
    void anUnknownPostTypeTeachesTheVocabulary() {
        assertThatThrownBy(() ->
                shAgentReadService.find(siteName, "Artcle", null, null, null, false))
                .isInstanceOf(ShAgentBatchService.UnknownPostTypeException.class);
    }

    // --- read ---

    @Test
    void readReturnsTheDraftWithItsDataAndNoAuthoringMetadata() {
        ShAgentDoc doc = shAgentReadService.read(List.of(address("/alpha")), null, false).get(0);

        assertThat(doc.address()).isEqualTo(address("/alpha"));
        assertThat(doc.type()).isEqualTo(postTypeName);
        assertThat(doc.state()).isEqualTo("DRAFT");
        assertThat(doc.folder()).isEqualTo(base);
        assertThat(doc.data().get("headline").asString()).isEqualTo("Alpha");
        // The SH78 standing rule, asserted on the shape rather than trusted.
        String json = objectMapper.writeValueAsString(doc);
        assertThat(json).doesNotContain("owner").doesNotContain("publishStatus").doesNotContain("date");
    }

    @Test
    void readTakesSeveralAddressesInOneCall() {
        List<ShAgentDoc> docs = shAgentReadService.read(
                List.of(address("/alpha"), address("/beta")), null, false);

        assertThat(docs).extracting(ShAgentDoc::address)
                .containsExactly(address("/alpha"), address("/beta"));
    }

    // --- SH123: projection ---

    @Test
    void fieldsProjectsDownToWhatWasAskedFor() {
        ShAgentDoc doc = shAgentReadService
                .read(List.of(address("/alpha")), List.of("headline"), false).get(0);

        assertThat(doc.data().propertyNames()).containsExactly("headline");
        // Saying which fields came back removes the "is this all of it?" round trip.
        assertThat(doc.fields()).containsExactly("headline");
    }

    @Test
    void aProjectionIsMateriallyCheaperThanTheWholeDocument() {
        int full = ShTokenBudget.estimate(ShAgentReadTerse.docs(
                shAgentReadService.read(List.of(address("/alpha")), null, false)));
        int projected = ShTokenBudget.estimate(ShAgentReadTerse.docs(
                shAgentReadService.read(List.of(address("/alpha")), List.of("headline"), false)));

        assertThat(projected).isLessThan(full);
    }

    @Test
    void projectingAnUnknownFieldIsRefusedRatherThanAnsweredEmpty() {
        // The one way this feature could do real damage: an empty projection looks
        // exactly like an empty post, and the agent would overwrite content with
        // nothing. So a typo is a teaching error, not a result.
        assertThatThrownBy(() ->
                shAgentReadService.read(List.of(address("/alpha")), List.of("headine"), false))
                .isInstanceOf(ShAgentReadService.UnknownFieldException.class)
                .satisfies(thrown -> assertThat(
                        ((ShAgentReadService.UnknownFieldException) thrown)
                                .toProblemDetail().getProperties())
                        .containsEntry("didYouMean", "headline"));
    }

    @Test
    void aFieldTheTypeHasButThePostHasNotSetIsSimplyAbsent() {
        // Data, not a mistake — the distinction the previous test depends on.
        // Written outside `base` so it cannot perturb the find assertions, which are
        // counts over that folder and would otherwise depend on test order.
        String sparse = "post:" + siteName + "/sparse-" + UUID.randomUUID().toString().substring(0, 8);
        shAgentBatchService.run(new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.POST_UPSERT, sparse, postTypeName, "/",
                        objectMapper.valueToTree(Map.of("headline", "Sparse"))))), false, "tester");

        ShAgentDoc doc = shAgentReadService
                .read(List.of(sparse), List.of("headline", "body"), false).get(0);

        assertThat(doc.data().propertyNames()).containsExactly("headline");
    }

    // --- teaching errors ---

    @Test
    void readingAFolderExplainsWhatToUseInstead() {
        assertThatThrownBy(() -> shAgentReadService
                .read(List.of("folder:" + siteName + base), null, false))
                .isInstanceOf(ShAgentReadService.NotAPostException.class)
                .satisfies(thrown -> assertThat(thrown.getMessage()).contains("folder"));
    }

    @Test
    void readingNothingSaysHowToFindAnAddress() {
        assertThatThrownBy(() -> shAgentReadService.read(List.of(), null, false))
                .isInstanceOf(ShAgentReadService.NoAddressException.class);
    }
}
