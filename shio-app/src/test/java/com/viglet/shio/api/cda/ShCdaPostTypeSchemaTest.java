/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH11 CDA post-type schema surface ({@link ShCdaService#getPostType} /
 * {@code listPostTypes}). The schema drops container-only widgets ({@code Tab}),
 * marks array widgets and Relators as {@code multiple}, and recurses into a
 * Relator's sub-fields — the shape {@code shio-types} code generation relies on.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShCdaPostTypeSchemaTest {

    @Autowired
    private ShCdaService shCdaService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String postTypeName;

    // No teardown: the test DB is a fresh in-memory H2 built create-drop per run
    // (and the attr→widget CascadeType.ALL makes an explicit delete brittle), so
    // the model is simply left in place — mirrors ShCdaWriteTest.

    @BeforeAll
    void setup() {
        postTypeName = "PT-SCHEMA-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType postType = new ShPostType();
            postType.setName(postTypeName);
            postType.setTitle("Schema Test");
            postType.setNamePlural("Schema Tests");
            postType.setDescription("a content model");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.now());
            postType.setOwner("admin");
            entityManager.persist(postType);

            // A distinct widget per attr — `ShPostTypeAttr.shWidget` cascades ALL,
            // so a shared widget would be deleted twice on teardown.
            ShPostTypeAttr title = attr(postType, "title", widget("Text"), 0, (byte) 1, (byte) 0, (byte) 1);
            ShPostTypeAttr tags = attr(postType, "tags", widget("Multi Select"), 1, (byte) 0, (byte) 0, (byte) 0);
            ShPostTypeAttr layoutTab = attr(postType, "layout", widget("Tab"), 2, (byte) 0, (byte) 0, (byte) 0);
            ShPostTypeAttr blocks = attr(postType, "blocks", widget("Relator"), 3, (byte) 0, (byte) 0, (byte) 0);
            entityManager.persist(title);
            entityManager.persist(tags);
            entityManager.persist(layoutTab);
            entityManager.persist(blocks);

            ShPostTypeAttr heading = attr(postType, "heading", widget("Text"), 0, (byte) 0, (byte) 0, (byte) 1);
            heading.setShParentPostTypeAttr(blocks);
            entityManager.persist(heading);
            blocks.setShPostTypeAttrs(Set.of(heading));
            entityManager.merge(blocks);

            postType.setShPostTypeAttrs(Set.of(title, tags, layoutTab, blocks));
            entityManager.merge(postType);
        });
    }

    private ShWidget widget(String name) {
        ShWidget w = new ShWidget();
        w.setName(name);
        entityManager.persist(w);
        return w;
    }

    private ShPostTypeAttr attr(ShPostType postType, String name, ShWidget widget,
                                int ordinal, byte isTitle, byte isSummary, byte required) {
        ShPostTypeAttr a = new ShPostTypeAttr();
        a.setName(name);
        a.setLabel(name);
        a.setOrdinal(ordinal);
        a.setRequired(required);
        a.setIsTitle(isTitle);
        a.setIsSummary(isSummary);
        a.setShWidget(widget);
        a.setShPostType(postType);
        return a;
    }

    @Test
    void getPostTypeReturnsTheContentModel() {
        ShCdaPostTypeSchema schema = shCdaService.getPostType(postTypeName).orElseThrow();

        assertThat(schema.name()).isEqualTo(postTypeName);
        assertThat(schema.title()).isEqualTo("Schema Test");
        assertThat(schema.namePlural()).isEqualTo("Schema Tests");

        // Tab is a layout-only widget — dropped from the schema.
        assertThat(schema.fields()).extracting(ShCdaSchemaField::name)
                .containsExactly("title", "tags", "blocks");

        ShCdaSchemaField title = schema.fields().get(0);
        assertThat(title.required()).isTrue();
        assertThat(title.multiple()).isFalse();
        assertThat(title.isTitle()).isTrue();

        ShCdaSchemaField tags = schema.fields().get(1);
        assertThat(tags.multiple()).as("Multi Select delivers an array").isTrue();

        ShCdaSchemaField blocks = schema.fields().get(2);
        assertThat(blocks.multiple()).as("Relator delivers an item list").isTrue();
        assertThat(blocks.fields()).extracting(ShCdaSchemaField::name).containsExactly("heading");
    }

    @Test
    void listPostTypesIncludesTheModel() {
        assertThat(shCdaService.listPostTypes())
                .extracting(ShCdaPostTypeSchema::name)
                .contains(postTypeName);
    }

    @Test
    void getPostTypeIsEmptyForUnknownName() {
        assertThat(shCdaService.getPostType("does-not-exist-" + UUID.randomUUID())).isEmpty();
    }
}
