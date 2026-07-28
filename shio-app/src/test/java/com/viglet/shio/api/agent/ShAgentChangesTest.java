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
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH88 — the change feed and its cursor.
 *
 * <p>The properties under test are the ones that decide whether a caller can trust the
 * feed to <b>catch up without losing anything</b>: entries that share a timestamp are not
 * skipped (the reason the cursor carries an id at all), a re-read from the returned cursor
 * is empty rather than a repeat of the same page, an entry younger than the lag is held
 * back, an entry whose object is gone still travels with its title, and a site-scoped read
 * cannot see another site's trail. Everything else about the payload is presentation.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentChangesTest {

    /** Old enough to clear the lag window in every mode under test. */
    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T11:00:00Z");

    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShHistoryRepository shHistoryRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String otherSiteName;
    private String siteId;
    private String otherSiteId;
    private String postTypeName;
    private String base;
    private String livePostId;
    private String liveFolderId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            siteName = "changes-site-" + UUID.randomUUID().toString().substring(0, 8);
            otherSiteName = "changes-other-" + UUID.randomUUID().toString().substring(0, 8);
            siteId = persistSite(siteName);
            otherSiteId = persistSite(otherSiteName);

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
            postTypeName = "ChangesArticle" + UUID.randomUUID().toString().substring(0, 8);
            postType.setName(postTypeName);
            postType.setTitle("Changes Article");
            postType.setSystem((byte) 0);
            postType.setDate(T1);
            postType.setOwner("admin");
            entityManager.persist(postType);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("headline");
            attr.setLabel("Headline");
            attr.setOrdinal(1);
            attr.setIsTitle((byte) 1);
            attr.setRequired((byte) 0);
            attr.setShWidget(widget);
            attr.setShPostType(postType);
            entityManager.persist(attr);
        });

        base = "/changes-" + UUID.randomUUID().toString().substring(0, 8);
        tx.executeWithoutResult(status -> {
            var result = shAgentBatchService.run(new ShAgentBatch(false, List.of(
                    new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + base,
                            null, null, null),
                    new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + base + "/live",
                            postTypeName, base, headline("Live page")))),
                    true, "tester");
            liveFolderId = result.results().get(0).id();
            livePostId = result.results().get(1).id();
        });

        // The audit rows are written by hand rather than by exercising a write, because
        // every property here is about *positions* — ties, ordering and the lag — and a
        // real write stamps Instant.now(), which sits inside the lag window at first and
        // outside it a second later. The rows the setup's own batch recorded are removed
        // for the same reason: a feed test whose fixture ages is a flake, not a test.
        tx.executeWithoutResult(status -> {
            shHistoryRepository.findAll().stream()
                    .filter(entry -> siteId.equals(entry.getShSite())
                            || otherSiteId.equals(entry.getShSite()))
                    .map(ShHistory::getId)
                    .toList()
                    .forEach(shHistoryRepository::delete);
            record(ShContentAction.CREATE, livePostId, siteId, T1, "aaaa", "Created \"Live page\"");
            record(ShContentAction.UPDATE, livePostId, siteId, T1, "bbbb", "Updated \"Live page\"");
            record(ShContentAction.PUBLISH, livePostId, siteId, T2, "cccc", "Published \"Live page\"");
            record(ShContentAction.DELETE, "gone-" + UUID.randomUUID(), siteId, T2, "dddd",
                    "Deleted \"Ghost page\"");
            record(ShContentAction.CREATE, "other-" + UUID.randomUUID(), otherSiteId, T2, "eeee",
                    "Created \"Elsewhere\"");
            // SH200: a folder delete is audited now, so the feed carries entries whose object
            // is a folder. Written by hand for the same reason as the rest.
            record(ShContentAction.DELETE, liveFolderId, siteId, T2, "ddee",
                    "Deleted \"changes-folder\"");
            // Inside the lag window, and stamped ahead of it so the assertion cannot age
            // out mid-run: the feed must not report it yet.
            record(ShContentAction.UPDATE, livePostId, siteId, Instant.now().plusSeconds(3600),
                    "ffff", "Updated \"Live page\" just now");
        });
    }

    private String persistSite(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(T1);
        site.setOwner("admin");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(T1);
        root.setOwner("admin");
        entityManager.persist(root);
        return site.getId();
    }

    private void record(ShContentAction action, String objectId, String site, Instant at,
                        String idPrefix, String description) {
        ShHistory entry = new ShHistory();
        // Deterministic ids so the tie-break has a known order (the ids sort as written).
        entry.setId(idPrefix + "-0000-4000-8000-" + UUID.randomUUID().toString().substring(0, 12));
        entry.setAction(action.name());
        entry.setShObject(objectId);
        entry.setShSite(site);
        entry.setOwner("tester");
        entry.setDate(at);
        entry.setDescription(description);
        shHistoryRepository.save(entry);
    }

    private JsonNode headline(String value) {
        return objectMapper.valueToTree(java.util.Map.of("headline", value));
    }

    private List<String> descriptions(ShAgentChanges changes) {
        return changes.changes().stream().map(ShAgentChanges.Change::what).toList();
    }

    // --- the window ---

    @Test
    void aCallWithoutSinceIsAWindowThatSaysSo() {
        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 10, false);

        assertThat(changes.window()).isTrue();
        assertThat(changes.cursor()).isNotBlank();
        assertThat(changes.notes()).anyMatch(note -> note.startsWith("window:"));
        // Oldest first in both modes, so the cursor is always the last row.
        assertThat(descriptions(changes)).startsWith("Created \"Live page\"");
    }

    @Test
    void theWindowHoldsBackAnEntryYoungerThanTheLag() {
        // Not a nicety: SH51 stamps the audit row inside the writer's transaction, so a
        // slow commit can land an older timestamp after the cursor has passed it. The lag
        // is what keeps that from being a silent loss.
        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(descriptions(changes)).noneMatch(what -> what.contains("just now"));
        assertThat(changes.notes()).anyMatch(note -> note.contains("held back"));
    }

    /**
     * SH200 — a deleted folder is addressed as a folder, not dropped.
     *
     * <p>The feed's address lookup was post-only, so a folder entry would have arrived with
     * {@code address: null}: a change nobody can act on, in the one place where knowing
     * <em>what</em> was destroyed matters most. It resolves through the trash on purpose —
     * the folder the entry names is, by then, deleted.</p>
     */
    @Test
    void aDeletedFolderTravelsWithAFolderAddress() {
        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(changes.changes())
                .filteredOn(change -> "Deleted \"changes-folder\"".equals(change.what()))
                .singleElement()
                .extracting(ShAgentChanges.Change::address)
                .isEqualTo("folder:" + siteName + base);
    }

    // --- the cursor ---

    @Test
    void reReadingFromTheReturnedCursorIsEmpty() {
        ShAgentChanges first = shAgentChangesService.changes(siteName, null, 50, false);

        ShAgentChanges again = shAgentChangesService.changes(siteName, first.cursor(), 50, false);

        assertThat(again.window()).isFalse();
        assertThat(again.found()).isZero();
        // The position survives an empty read — otherwise a quiet instance would send the
        // caller back to a window and it would lose its place.
        assertThat(again.cursor()).isEqualTo(first.cursor());
    }

    @Test
    void entriesSharingATimestampAreNotSkipped() {
        // Two rows share T1. A timestamp-only cursor would deliver the first and drop the
        // second for ever; the id tie-break is the whole reason it does not. Paged from an
        // explicit anchor before T1, because the window starts at the *newest* entry and
        // would have nothing left to walk forward to.
        String anchor = new ShAgentChangeCursor(T1.minusSeconds(1), "").toString();
        ShAgentChanges firstOnly = shAgentChangesService.changes(siteName, anchor, 1, false);

        assertThat(descriptions(firstOnly)).containsExactly("Created \"Live page\"");

        ShAgentChanges rest = shAgentChangesService.changes(siteName, firstOnly.cursor(), 50, false);

        assertThat(descriptions(rest)).first().isEqualTo("Updated \"Live page\"");
        assertThat(descriptions(rest)).doesNotContain("Created \"Live page\"");
    }

    @Test
    void aPageThatEndedEarlySaysThereIsMore() {
        String anchor = new ShAgentChangeCursor(T1.minusSeconds(1), "").toString();

        ShAgentChanges page = shAgentChangesService.changes(siteName, anchor, 1, false);

        assertThat(page.found()).isEqualTo(1);
        assertThat(page.more()).isTrue();
        assertThat(shAgentChangesService.changes(siteName, page.cursor(), 50, false).more()).isFalse();
    }

    @Test
    void aCursorWithoutAnIdIsRefusedRatherThanInterpreted() {
        assertThatThrownBy(() -> shAgentChangesService.changes(siteName, "2026-03-01T10:00:00Z",
                10, false))
                .isInstanceOf(ShAgentChangeCursor.MalformedCursorException.class)
                // The teaching part is the detail: it says *why* guessing would lose
                // entries, which is the whole reason the id is in the cursor.
                .hasMessageContaining("share its timestamp would be silently skipped");
    }

    @Test
    void aCursorRoundTripsThroughItsWireForm() {
        ShAgentChangeCursor cursor = new ShAgentChangeCursor(T1, "abc-123");

        assertThat(ShAgentChangeCursor.parse(cursor.toString())).isEqualTo(cursor);
        // The empty id is the anchor form: it sorts before every real entry at that instant.
        assertThat(ShAgentChangeCursor.parse(T1 + "|").id()).isEmpty();
    }

    // --- rows ---

    @Test
    void aLiveEntryCarriesAnAddressAndAGoneOneCarriesItsTitle() {
        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(changes.changes())
                .filteredOn(change -> "PUBLISH".equals(change.action()))
                .singleElement()
                .satisfies(change -> assertThat(change.address())
                        .isEqualTo("post:" + siteName + base + "/live"));
        // Narrowed to the ghost row on purpose: since SH200 a folder deletion is audited too,
        // and that one *does* carry an address (it is the trashed folder's).
        assertThat(changes.changes())
                .filteredOn(change -> "DELETE".equals(change.action())
                        && change.what().contains("Ghost page"))
                .singleElement()
                .satisfies(change -> assertThat(change.address()).isNull());
        assertThat(changes.notes()).anyMatch(note -> note.contains("no longer exists"));
    }

    @Test
    void idsAreOffUntilAskedFor() {
        assertThat(shAgentChangesService.changes(siteName, null, 5, false).changes())
                .allSatisfy(change -> assertThat(change.id()).isNull());
        assertThat(shAgentChangesService.changes(siteName, null, 5, true).changes())
                .anySatisfy(change -> assertThat(change.id()).isNotBlank());
    }

    // --- scope ---

    @Test
    void aSiteScopedReadCannotSeeAnotherSitesTrail() {
        ShAgentChanges scoped = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(descriptions(scoped)).noneMatch(what -> what.contains("Elsewhere"));
        assertThat(descriptions(shAgentChangesService.changes(otherSiteName, null, 50, false)))
                .contains("Created \"Elsewhere\"");
    }

    @Test
    void theTerseRenderingPutsTheCursorOnTheHeaderLine() {
        ShAgentChanges changes = shAgentChangesService.changes(siteName, null, 3, false);

        String terse = ShAgentChangesTerse.render(changes);

        assertThat(terse.lines().findFirst().orElseThrow())
                .startsWith("changes 3 window")
                .contains("cursor=" + changes.cursor());
        assertThat(terse).contains("event PUBLISH post:" + siteName + base + "/live");
    }
}
