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
                name, "Admin Test", "Admin Tests", "round-trip", "EntityWorkflow", false, false,
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
        service.upsert(name, new ShPostTypeDef(name, "v1", "v1s", null, null, false, false,
                List.of(field("a", "Text", 0, true, null, List.of()),
                        field("b", "Text", 1, false, null, List.of()))));

        // Update: drop "b". orphanRemoval deletes its attr — but must NOT cascade
        // a REMOVE into the shared "Text" widget catalog row.
        ShPostTypeDef updated = service.upsert(name, new ShPostTypeDef(name, "v2", "v2s", null, null, false, false,
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
    void upsertMarksTypeManagedAndPullReportsIt() {
        String name = "PT-MNG-" + UUID.randomUUID();

        // SH66/SH117: `managed` absent is the CLI's contract — `shio push` owns the
        // type, so the flag is set server-side. The key is omitted from the canonical
        // serialization, which is exactly why absent (not `true`) has to mean this.
        ShPostTypeDef created = service.upsert(name, new ShPostTypeDef(
                name, "Managed", "Managed", null, null, false, null,
                List.of(field("title", "Text", 0, true, null, List.of()))));
        assertThat(created.managed()).as("a write with no `managed` claim owns the type").isTrue();

        // ...and it is delivered on the next pull so the console can lock it.
        assertThat(service.get(name).orElseThrow().managed()).isTrue();
        // managed is orthogonal to system — this is a custom type.
        assertThat(created.system()).isFalse();
    }

    /**
     * SH768 — the assertion that would have caught it, at the boundary it crossed.
     *
     * <p>Two conformance fixtures — {@code ShioConfRendermsgmgaup2qjcArticle} and
     * {@code ShioConfRendermsgmgrcoqdq2Article} — sat in a working instance's model as two of 22
     * types with nothing marking them as debris, so every cold {@code shio context} presented them
     * as conventions to follow. The write path is where the declaration has to be made and where it
     * has to stop being accepted, which is what these two assertions pin.</p>
     */
    @Test
    void aTypeIsDeclaredDisposableAtCreationAndNeverAfterwards() {
        String name = "PT-SCRATCH-" + UUID.randomUUID();

        ShPostTypeDef created = service.upsert(name, new ShPostTypeDef(
                name, "Fixture", "Fixtures", null, null, false, null, true,
                List.of(field("title", "Text", 0, true, null, List.of()))));
        assertThat(created.scratch())
                .as("SH768: a creator that said the word gets a type the sweep can reach")
                .isTrue();
        assertThat(service.get(name).orElseThrow().scratch())
                .as("and it is delivered on the next read, which is where a reader meets it")
                .isTrue();

        // The guarantee, and the whole reason this is a column and not a naming convention: a later
        // write cannot raise the flag, or `post-type rm --scratch` becomes a way to sweep a model
        // somebody meant. SH591 makes exactly this promise for a scratch site.
        String kept = "PT-KEPT-" + UUID.randomUUID();
        service.upsert(kept, new ShPostTypeDef(kept, "Real", "Reals", null, null, false, null,
                List.of(field("title", "Text", 0, true, null, List.of()))));
        ShPostTypeDef reclaimed = service.upsert(kept, new ShPostTypeDef(
                kept, "Real", "Reals", null, null, false, null, true,
                List.of(field("title", "Text", 0, true, null, List.of()))));
        assertThat(reclaimed.scratch())
                .as("SH768: an update claiming scratch on a kept type changes nothing — the "
                        + "declaration has to be one its creator made before there was anything "
                        + "written against it")
                .isNull();
    }

    /**
     * SH768 — and absent is <b>kept</b>, which is the opposite default to SH66's {@code managed}.
     *
     * <p>Worth its own assertion because the two boxed flags on this record read alike and mean
     * opposite things when omitted: a write with no {@code managed} claim owns the type, and a write
     * with no {@code scratch} claim is emphatically not disposable. Guessing the other way would let
     * the sweep reach a model nobody declared.</p>
     */
    @Test
    void aTypeNobodyCalledDisposableIsKept() {
        String name = "PT-DEFAULT-" + UUID.randomUUID();

        ShPostTypeDef created = service.upsert(name, new ShPostTypeDef(
                name, "Ordinary", "Ordinaries", null, null, false, null,
                List.of(field("title", "Text", 0, true, null, List.of()))));

        assertThat(created.scratch())
                .as("SH768: scratch is never a default — the failure mode of guessing wrong is a "
                        + "sweep deleting a model somebody meant")
                .isNull();
        assertThat(created.declaresScratch()).isFalse();
    }

    @Test
    void aHandEditIsRefusedAgainstACodeOwnedType() {
        String name = "PT-HAND-" + UUID.randomUUID();
        ShPostTypeDef codeOwned = new ShPostTypeDef(name, "Owned", "Owned", null, null, false, null,
                List.of(field("title", "Text", 0, true, null, List.of())));
        service.upsert(name, codeOwned);

        // SH117: the console shares this surface, so `managed:false` is what makes a
        // write a hand edit. Against a code-owned model that is a conflict — the
        // server-side backstop behind the editor's disabled save button.
        ShPostTypeDef handEdit = new ShPostTypeDef(name, "Hand edited", "Owned", null, null, false, false,
                List.of(field("title", "Text", 0, true, null, List.of())));
        assertThatExceptionOfType(ShManagedPostTypeException.class)
                .isThrownBy(() -> service.upsert(name, handEdit));
        assertThat(service.get(name).orElseThrow().title()).isEqualTo("Owned");

        // The owner may keep converging, and the flag survives the round trip.
        assertThat(service.upsert(name, codeOwned).managed()).isTrue();
    }

    @Test
    void aConsoleWriteLeavesTheTypeEditable() {
        String name = "PT-CONS-" + UUID.randomUUID();
        ShPostTypeDef created = service.create(new ShPostTypeDef(
                name, "Hand made", "Hand made", null, null, false, false,
                List.of(field("title", "Text", 0, true, null, List.of()))));
        // A type the console made must not lock itself out of its own editor.
        assertThat(created.managed()).isFalse();

        assertThatExceptionOfType(ShPostTypeExistsException.class)
                .isThrownBy(() -> service.create(new ShPostTypeDef(
                        name, "Again", null, null, null, false, false, List.of())));
    }

    @Test
    void upsertRejectsUnknownWidget() {
        String name = "PT-BADW-" + UUID.randomUUID();
        ShPostTypeDef def = new ShPostTypeDef(name, "x", "xs", null, null, false, false,
                List.of(field("f", "No Such Widget", 0, false, null, List.of())));
        assertThatExceptionOfType(ShUnknownWidgetException.class)
                .isThrownBy(() -> service.upsert(name, def));
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
            pt.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            pt.setOwner("admin");
            entityManager.persist(pt);
        });

        ShPostTypeDef def = new ShPostTypeDef(name, "hacked", null, null, null, false, false, List.of());
        assertThatExceptionOfType(ShSystemPostTypeException.class)
                .isThrownBy(() -> service.upsert(name, def));
        assertThatExceptionOfType(ShSystemPostTypeException.class).isThrownBy(() -> service.delete(name));
    }

    @Test
    void deleteRemovesNonSystemType() {
        String name = "PT-DEL-" + UUID.randomUUID();
        service.upsert(name, new ShPostTypeDef(name, "doomed", null, null, null, false, false,
                List.of(field("a", "Text", 0, true, null, List.of()))));

        assertThat(service.delete(name)).isTrue();
        assertThat(service.get(name)).isEmpty();
        assertThat(service.delete("missing-" + UUID.randomUUID())).isFalse();
    }
}
