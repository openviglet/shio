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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH16 post-types-as-code admin surface ({@link ShPostTypeAdminService}) — the
 * engine behind the {@code shio} CLI's {@code pull}/{@code push}. Verifies the
 * lossless round-trip (Tab kept, {@code widgetSettings} preserved, Relator
 * nesting), the cascade-safe update (orphan removal must not delete the shared
 * widget catalog), and system-type protection.
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShPostTypeAdminServiceTest {

    @Autowired
    private ShPostTypeAdminService service;
    @Autowired
    private ShWidgetRepository shWidgetRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private static ShPostTypeAttrDef field(String name, String widget, int ordinal,
                                           boolean isTitle, String widgetSettings,
                                           List<ShPostTypeAttrDef> children) {
        return new ShPostTypeAttrDef(name, name, null, widget, false, isTitle, false, ordinal,
                widgetSettings, children);
    }

    @Test
    void upsertCreatesAndPullIsLossless() {
        String name = "PT-ADMIN-" + UUID.randomUUID();

        ShPostTypeDef created = service.upsert(name, new ShPostTypeDef(
                name, "Admin Test", "Admin Tests", "round-trip", "EntityWorkflow", false,
                List.of(
                        field("title", "Text", 0, true, null, List.of()),
                        field("tags", "Multi Select", 1, false, "{\"choices\":\"a:A\"}", List.of()),
                        field("layout", "Tab", 2, false, null, List.of()),
                        field("blocks", "Relator", 3, false, null, List.of(
                                field("heading", "Text", 0, false, null, List.of()))))));

        assertThat(created.title()).isEqualTo("Admin Test");

        ShPostTypeDef pulled = service.get(name).orElseThrow();
        assertThat(pulled.namePlural()).isEqualTo("Admin Tests");
        assertThat(pulled.workflowPublishEntity()).isEqualTo("EntityWorkflow");
        // Lossless: Tab is kept (unlike the SH11 schema), ordered by ordinal.
        assertThat(pulled.fields()).extracting(ShPostTypeAttrDef::name)
                .containsExactly("title", "tags", "layout", "blocks");
        // widgetSettings preserved verbatim.
        assertThat(pulled.fields().get(1).widgetSettings()).isEqualTo("{\"choices\":\"a:A\"}");
        // Relator children nested.
        ShPostTypeAttrDef blocks = pulled.fields().get(3);
        assertThat(blocks.widget()).isEqualTo("Relator");
        assertThat(blocks.fields()).extracting(ShPostTypeAttrDef::name).containsExactly("heading");
        assertThat(pulled.fields().get(0).isTitle()).isTrue();
    }

    @Test
    void upsertUpdateReplacesFieldsAndPreservesSharedWidget() {
        String name = "PT-UPD-" + UUID.randomUUID();
        service.upsert(name, new ShPostTypeDef(name, "v1", "v1s", null, null, false,
                List.of(field("a", "Text", 0, true, null, List.of()),
                        field("b", "Text", 1, false, null, List.of()))));

        // Update: drop "b". orphanRemoval deletes its attr — but must NOT cascade
        // a REMOVE into the shared "Text" widget catalog row.
        ShPostTypeDef updated = service.upsert(name, new ShPostTypeDef(name, "v2", "v2s", null, null, false,
                List.of(field("a", "Text", 0, true, null, List.of()))));

        assertThat(updated.title()).isEqualTo("v2");
        assertThat(updated.fields()).extracting(ShPostTypeAttrDef::name).containsExactly("a");
        // The shared widget survived the orphan removal. (findFirstByName, not
        // findByName: other suites seed duplicate-named widgets in the shared
        // context, so a unique-result query would throw — see ShWidgetRepository.)
        assertThat(shWidgetRepository.findFirstByName("Text")).isNotNull();
        // And the remaining field still resolves it.
        assertThat(updated.fields().get(0).widget()).isEqualTo("Text");
    }

    @Test
    void upsertRejectsUnknownWidget() {
        String name = "PT-BADW-" + UUID.randomUUID();
        assertThatExceptionOfType(ShUnknownWidgetException.class).isThrownBy(() ->
                service.upsert(name, new ShPostTypeDef(name, "x", "xs", null, null, false,
                        List.of(field("f", "No Such Widget", 0, false, null, List.of())))));
    }

    @Test
    void systemPostTypeIsReadOnly() {
        String name = "PT-SYS-" + UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            ShPostType pt = new ShPostType();
            pt.setName(name);
            pt.setTitle("System");
            pt.setSystem((byte) 1);
            pt.setDate(Instant.now());
            pt.setOwner("admin");
            entityManager.persist(pt);
        });

        assertThatExceptionOfType(ShSystemPostTypeException.class).isThrownBy(() ->
                service.upsert(name, new ShPostTypeDef(name, "hacked", null, null, null, false, List.of())));
        assertThatExceptionOfType(ShSystemPostTypeException.class).isThrownBy(() -> service.delete(name));
    }

    @Test
    void deleteRemovesNonSystemType() {
        String name = "PT-DEL-" + UUID.randomUUID();
        service.upsert(name, new ShPostTypeDef(name, "doomed", null, null, null, false,
                List.of(field("a", "Text", 0, true, null, List.of()))));

        assertThat(service.delete(name)).isTrue();
        assertThat(service.get(name)).isEmpty();
        assertThat(service.delete("missing-" + UUID.randomUUID())).isFalse();
    }
}
