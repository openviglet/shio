/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.widget.ShSystemWidget;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1018 — a model change applied to the content it governs.
 *
 * <p>Over real rows, because every claim here is about stored content: that a dry run writes
 * nothing, that a rename never silently eats a value, and that a default does not overwrite one
 * somebody typed. None of those can be shown against a mock of the thing being changed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
class ShPostTypeMigrateServiceTest {

    @Autowired
    private ShPostTypeMigrateService service;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ObjectMapper objectMapper;

    private record Fixture(String typeName, List<String> postIds) {
    }

    private Fixture seed(String... documents) {
        String typeName = "Sh1018m" + UUID.randomUUID().toString().substring(0, 8);
        List<String> ids = new java.util.ArrayList<>();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShWidget widget = new ShWidget();
            widget.setId(UUID.randomUUID().toString());
            widget.setName(ShSystemWidget.TEXT);
            entityManager.persist(widget);

            ShPostType postType = new ShPostType();
            postType.setId(UUID.randomUUID().toString());
            postType.setName(typeName);
            postType.setDate(Instant.now());
            postType.setSystem((byte) 0);
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setId(UUID.randomUUID().toString());
            attr.setName("summary");
            attr.setLabel("Summary");
            attr.setOrdinal(0);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            postType.setShPostTypeAttrs(new HashSet<>(java.util.Set.of(attr)));
            entityManager.persist(postType);

            for (String document : documents) {
                ShPostUnified post = new ShPostUnified();
                post.setId(UUID.randomUUID().toString());
                post.setState(ShPostState.DRAFT);
                post.setTitle("SH1018 migrate fixture");
                post.setFurl("/sh1018m-" + UUID.randomUUID().toString().substring(0, 8));
                post.setDate(Instant.now());
                post.setOwner("admin");
                post.setShPostType(postType);
                post.setData(document);
                entityManager.persist(post);
                ids.add(post.getId());
            }
        });
        return new Fixture(typeName, ids);
    }

    private String dataOf(String postId) {
        return shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT)
                .orElseThrow().getData();
    }

    @Test
    @DisplayName("SH1018: a dry run counts what it would change and writes nothing")
    void aDryRunChangesNothing() {
        Fixture fixture = seed("{\"subtitle\":\"moved\"}");

        ShPostTypeMigrateService.Plan plan = service.migrate(fixture.typeName(),
                objectMapper.readTree("{\"rename\":{\"subtitle\":\"summary\"}}"), true);

        assertThat(plan.renamed()).containsEntry("subtitle", 1);
        assertThat(dataOf(fixture.postIds().getFirst()))
                .as("SH1018: a dry run that writes is worse than no dry run, because it is the "
                        + "flag somebody reaches for when they are unsure about a migration")
                .isEqualTo("{\"subtitle\":\"moved\"}");
    }

    @Test
    @DisplayName("SH1018: rename moves the value, drop removes the key, default fills the gap")
    void theThreeMappingsApply() {
        Fixture fixture = seed("{\"subtitle\":\"moved\",\"legacy\":\"gone\"}");

        service.migrate(fixture.typeName(), objectMapper.readTree(
                "{\"rename\":{\"subtitle\":\"summary\"},\"drop\":[\"legacy\"],"
                        + "\"default\":{\"author\":\"Editorial\"}}"), false);

        String after = dataOf(fixture.postIds().getFirst());
        assertThat(objectMapper.readTree(after).path("summary").asString()).isEqualTo("moved");
        assertThat(objectMapper.readTree(after).has("subtitle")).isFalse();
        assertThat(objectMapper.readTree(after).has("legacy")).isFalse();
        assertThat(objectMapper.readTree(after).path("author").asString()).isEqualTo("Editorial");
    }

    @Test
    @DisplayName("SH1018: a default never overwrites a value somebody typed")
    void aDefaultOnlyFillsWhatIsEmpty() {
        Fixture fixture = seed("{\"author\":\"A real person\"}", "{\"author\":\"\"}");

        ShPostTypeMigrateService.Plan plan = service.migrate(fixture.typeName(),
                objectMapper.readTree("{\"default\":{\"author\":\"Editorial\"}}"), false);

        assertThat(plan.defaulted())
                .as("SH1018: only the empty one — a default that overwrote a value would be the "
                        + "migration deleting content to satisfy a rule that content already met")
                .containsEntry("author", 1);
        assertThat(objectMapper.readTree(dataOf(fixture.postIds().getFirst()))
                .path("author").asString()).isEqualTo("A real person");
        assertThat(objectMapper.readTree(dataOf(fixture.postIds().get(1)))
                .path("author").asString()).isEqualTo("Editorial");
    }

    @Test
    @DisplayName("SH1018: a rename onto an occupied key refuses the whole migration")
    void aCollisionIsRefusedRatherThanResolved() {
        Fixture fixture = seed("{\"subtitle\":\"old\",\"summary\":\"new\"}",
                "{\"subtitle\":\"would have moved\"}");

        assertThatThrownBy(() -> service.migrate(fixture.typeName(),
                objectMapper.readTree("{\"rename\":{\"subtitle\":\"summary\"}}"), false))
                .as("""
                        SH1018: both values are somebody's content, and the two rules that would \
                        resolve it — the old wins, the new wins — are each silent data loss half \
                        the time. Refusing and naming the rows is what lets a caller decide.""")
                .isInstanceOf(ShPostTypeMigrateService.RenameCollisionException.class);

        assertThat(dataOf(fixture.postIds().get(1)))
                .as("SH1018: and nothing was written, including the row that had no collision — a "
                        + "migration that failed part way leaves a type half in one shape and half "
                        + "in another, with no way to tell which rows are which")
                .isEqualTo("{\"subtitle\":\"would have moved\"}");
    }

    @Test
    @DisplayName("SH1018: an empty request is a no-op rather than an error")
    void nothingToDoIsNotAFailure() {
        Fixture fixture = seed("{\"summary\":\"fine\"}");

        ShPostTypeMigrateService.Plan plan = service.migrate(fixture.typeName(),
                objectMapper.readTree("{}"), false);

        assertThat(plan.isEmpty()).isTrue();
        assertThat(plan.postIds()).isEmpty();
        assertThat(plan.describe()).isEqualTo("nothing to migrate");
    }
}
