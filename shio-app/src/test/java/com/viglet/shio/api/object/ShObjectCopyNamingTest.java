/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

import com.viglet.shio.api.folder.ShFolderCopyException;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.api.post.unified.ShRestoreTitleConflictException;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH202 — a console copy takes a name and a URL that are free (and SH203's copy cycle).
 *
 * <p>The defect this pins is the one every downstream mechanism already called broken:
 * "clone" is copy-to-the-same-folder, the copy's {@code furl} was re-slugged from the
 * source's title, and two posts in one site then claimed one URL — a {@code duplicate-url}
 * <em>error</em> from {@code shio verify}, and an address that resolves to whichever row is
 * reached first, so the original became unaddressable too. The curator's most casual action
 * produced it.</p>
 *
 * <p>Exercised as a bean rather than through the API because the question is what the writer
 * names things, not who is allowed to ask — {@link ShObjectMoveAuthorizationTest} owns that
 * half. <b>Every test builds its own folders</b>: the names under test are unique <em>within
 * a folder or a site</em>, so a shared fixture would make each assertion depend on which
 * tests had already written into it, which is the confound SH239 had to go back and remove
 * from the source-attribution benchmark.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShObjectCopyNamingTest {

    private static final List<ShPostState> BOTH = List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    @Autowired
    private ShObjectWriteService shObjectWriteService;
    /** SH243 — the trash half of the same question: what a copy must skip, and what a restore claims. */
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteId;
    private String rootId;
    /** A second site's root, so "copied where nothing collides" is assertable. */
    private String otherRootId;
    private String postTypeId;

    @BeforeAll
    void setup() {
        tx().executeWithoutResult(status -> {
            ShSite site = site("ut-copy-site-" + UUID.randomUUID());
            siteId = site.getId();
            rootId = root(site, "root").getId();

            otherRootId = root(site("ut-copy-other-" + UUID.randomUUID()), "root").getId();

            ShPostType postType = new ShPostType();
            postType.setName("PT-COPY-" + UUID.randomUUID());
            postType.setTitle("Copy Naming Post Type");
            postType.setSystem((byte) 0);
            postType.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            postType.setOwner("admin");
            entityManager.persist(postType);
            postTypeId = postType.getId();
        });
    }

    /**
     * The headline case. Cloning is copy-to-the-same-folder, so both names collide and both
     * have to move — a {@code -2} URL under an identical title still leaves two rows the
     * curator cannot tell apart, which is the symptom they actually see.
     */
    @Test
    void cloningAPostNumbersBothItsTitleAndItsUrl() {
        String folderId = folder("clone-one");
        String sourceId = post(folderId, siteId, "Home", "home", ShPostState.DRAFT);

        shObjectWriteService.copyTo(folderId, List.of(sourceId));

        ShPostUnified copy = onlyCopyIn(folderId, sourceId);
        assertThat(copy.getTitle()).isEqualTo("Home (2)");
        assertThat(copy.getFurl()).isEqualTo("home-2");
    }

    /** Clone twice and the counter keeps going instead of colliding with the first copy. */
    @Test
    void aSecondCloneTakesTheNextNumber() {
        String folderId = folder("clone-twice");
        String sourceId = post(folderId, siteId, "Pricing", "pricing", ShPostState.DRAFT);

        shObjectWriteService.copyTo(folderId, List.of(sourceId));
        shObjectWriteService.copyTo(folderId, List.of(sourceId));

        assertThat(furlsIn(folderId)).containsExactlyInAnyOrder("pricing", "pricing-2", "pricing-3");
        assertThat(titlesIn(folderId))
                .containsExactlyInAnyOrder("Pricing", "Pricing (2)", "Pricing (3)");
    }

    /**
     * A friendly URL is site-scoped, so a copy into a <em>different</em> folder of the same
     * site still has to move it — the collision was never limited to cloning.
     */
    @Test
    void aCopyIntoAnotherFolderOfTheSameSiteStillNeedsAFreeUrl() {
        String from = folder("cross-from");
        String to = folder("cross-to");
        String sourceId = post(from, siteId, "Contact", "contact", ShPostState.DRAFT);

        shObjectWriteService.copyTo(to, List.of(sourceId));

        ShPostUnified copy = onlyCopyIn(to, sourceId);
        assertThat(copy.getFurl()).isEqualTo("contact-2");
        // A title is a per-folder concern and this folder holds no other "Contact", so it is
        // left alone: numbering it here would be inventing a collision in order to fix it.
        assertThat(copy.getTitle()).isEqualTo("Contact");
    }

    /** Nothing collides across sites, so nothing is renamed. */
    @Test
    void aCopyIntoAnEmptyFolderOfAnotherSiteKeepsItsNameVerbatim() {
        String from = folder("verbatim-from");
        String to = folderUnder(otherRootId, "verbatim-to");
        String sourceId = post(from, siteId, "About Us", "about-us", ShPostState.DRAFT);

        shObjectWriteService.copyTo(to, List.of(sourceId));

        ShPostUnified copy = onlyCopyIn(to, sourceId);
        assertThat(copy.getTitle()).isEqualTo("About Us");
        assertThat(copy.getFurl()).isEqualTo("about-us");
    }

    /**
     * DRAFT and PUBLISHED are two rows of one logical post. Numbering them independently
     * would give the copy a published URL its own draft does not claim.
     */
    @Test
    void bothStateRowsOfACopyShareOneName() {
        String folderId = folder("states");
        String sourceId = post(folderId, siteId, "Launch", "launch", ShPostState.DRAFT);
        addRow(sourceId, folderId, siteId, "Launch", "launch", ShPostState.PUBLISHED);

        shObjectWriteService.copyTo(folderId, List.of(sourceId));

        assertThat(copiesIn(folderId, sourceId))
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.getTitle()).isEqualTo("Launch (2)");
                    assertThat(row.getFurl()).isEqualTo("launch-2");
                });
    }

    /**
     * SH243 — the URL a trashed post is still holding is not free.
     *
     * <p>The whole failure in one method, and it is built out of operations that are each
     * correct: clone ({@code home} → {@code home-2}), delete the copy, clone again. Every
     * free-URL finder reads through {@code @SQLRestriction("deleted_date is null")}, so the
     * second clone saw nothing holding {@code home-2} and took it — and the moment anybody
     * untrashed the first copy the site had two posts at one address, which is the
     * {@code duplicate-url} error this whole class exists to prevent.</p>
     *
     * <p>Asserted on the URLs only. A <b>title</b> is a per-folder display concern (see
     * {@link #aCopyIntoAnotherFolderOfTheSameSiteStillNeedsAFreeUrl}) and the live folder holds
     * no other "Home (2)" while the first copy sleeps, so the second copy is allowed to take
     * that title back. Two rows a curator cannot tell apart is a smaller problem than two rows
     * the resolver cannot tell apart, and correcting it would mean numbering a name against
     * content no listing shows.</p>
     */
    @Test
    @DisplayName("SH243: a copy skips a friendly URL a trashed post is only sleeping on")
    void aCopySkipsAUrlHeldByATrashedPost() {
        // Its own slug, not "home": a friendly URL is unique per *site*, so borrowing another
        // test's would make both depend on which ran first — the confound the class note names.
        String folderId = folder("sleeping-url");
        String sourceId = post(folderId, siteId, "Sleeper", "sleeper", ShPostState.DRAFT);

        shObjectWriteService.copyTo(folderId, List.of(sourceId));
        String sleepingId = onlyCopyIn(folderId, sourceId).getId();
        shPostUnifiedWriteService.delete(sleepingId, "curator");

        shObjectWriteService.copyTo(folderId, List.of(sourceId));

        assertThat(furlsIn(folderId))
                .as("""
                        `sleeper-2` is taken by a post in the trash, which is invisible to every \
                        derived finder and is exactly why this went unnoticed: the second clone \
                        must skip to `sleeper-3` (SH243).""")
                .containsExactlyInAnyOrder("sleeper", "sleeper-3");

        // SH691 — and now the restore is REFUSED, where it used to go through and leave the folder
        // holding two rows both called "Sleeper (2)".
        //
        // This is the assertion that would have caught it, and it is the same shape SH243 gave the
        // URL one step earlier: the title counter is trash-blind on purpose — numbering against
        // content no listing shows would produce "Sleeper (3)" beside a single visible
        // "Sleeper (2)", a rename the curator cannot account for — so the collision is allowed to
        // happen and refused where it becomes real. The URL is free by now (`sleeper-2`); the name
        // the curator reads is not.
        assertThatThrownBy(() -> shPostUnifiedWriteService.restoreFromTrash(sleepingId, "curator"))
                .as("SH691: the restore used to succeed and leave the folder holding two rows both "
                        + "called 'Sleeper (2)' — the symptom SH202 says is the one a curator "
                        + "actually sees, and the half SH243 left when it taught the URL counter "
                        + "about the trash")
                .isInstanceOf(ShRestoreTitleConflictException.class)
                .hasMessageContaining("Sleeper (2)")
                .as("SH691: the folder is named because the collision is per-folder — sending a "
                        + "curator to rename a same-titled post in a sibling folder is sending "
                        + "them where nothing is wrong")
                .hasMessageContaining("sleeping-url");
    }

    @Test
    void aRenamedOccupantLetsTheSleeperBackToItsOwnUrl() {
        // SH691's other half, and the reason the refusal is a refusal rather than a wall: it names
        // one action, the curator takes it, and the restore then does what SH243 promised — the
        // post returns to the address it always held rather than to a numbered one.
        // Its own slug, for the reason the class note gives and the reason this test first failed:
        // a friendly URL is unique per SITE, so reusing the neighbour's `sleeper` made the two
        // depend on which ran first — and SH719's unique index refused the write rather than
        // letting it pass quietly, which is that guard earning its place.
        String folderId = folder("renamed-occupant");
        String sourceId = post(folderId, siteId, "Dozer", "dozer", ShPostState.DRAFT);

        shObjectWriteService.copyTo(folderId, List.of(sourceId));
        String sleepingId = onlyCopyIn(folderId, sourceId).getId();
        shPostUnifiedWriteService.delete(sleepingId, "curator");
        shObjectWriteService.copyTo(folderId, List.of(sourceId));

        // The one action the refusal asks for, on the live post that took the name — inside a
        // transaction, so the row is managed and Hibernate updates it. A detached `save()` here
        // merged into an INSERT and SH719's unique index on `url_key` refused it, which is that
        // guard doing exactly its job one layer under this one.
        tx().executeWithoutResult(status -> shPostUnifiedRepository
                .findByShFolderAndStateIn(shFolderRepository.findById(folderId).orElseThrow(), BOTH)
                .stream()
                .filter(row -> "Dozer (2)".equals(row.getTitle()))
                .forEach(row -> row.setTitle("Dozer (renamed)")));

        assertThat(shPostUnifiedWriteService.restoreFromTrash(sleepingId, "curator")).isTrue();
        assertThat(furlsIn(folderId))
                .as("the sleeper comes back to `dozer-2`, its own address, which is what SH243 "
                        + "refuses to renumber")
                .containsExactlyInAnyOrder("dozer", "dozer-2", "dozer-3");
    }

    /**
     * The same defect with the addressing scheme swapped: a folder is addressed by its name
     * chain (SH79), so two siblings called "docs" make {@code folder:<site>/docs} resolve to
     * whichever the walk reaches first.
     */
    @Test
    void cloningAFolderNumbersItsName() {
        String parentId = folder("folder-clone");
        folderUnder(parentId, "docs");

        shObjectWriteService.copyTo(parentId, List.of(childIdOf(parentId, "docs")));

        assertThat(namesUnder(parentId)).containsExactlyInAnyOrder("docs", "docs (2)");
    }

    /**
     * SH203's server-side backstop, and <em>not</em> the move guard's reason: nothing would
     * be detached here. {@code copyFolder} saves the copy and then walks the source's
     * children, so a destination inside the source makes the walk descend into the copies it
     * is still making. Before this guard the call did not return at all.
     */
    @Test
    void copyingAFolderIntoItsOwnSubtreeIsRefused() {
        String parentId = folder("section");
        String childId = folderUnder(parentId, "inside");

        assertThatThrownBy(() -> shObjectWriteService.copyTo(childId, List.of(parentId)))
                .isInstanceOf(ShFolderCopyException.Cycle.class);

        assertThat(namesUnder(childId)).isEmpty();
    }

    /** And into itself, which is the same walk one step shorter. */
    @Test
    void copyingAFolderIntoItselfIsRefused() {
        String folderId = folder("self");

        assertThatThrownBy(() -> shObjectWriteService.copyTo(folderId, List.of(folderId)))
                .isInstanceOf(ShFolderCopyException.Cycle.class);
    }

    // --- fixtures ---

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private ShSite site(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setUrl("http://" + name + ".example.test");
        site.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        site.setOwner("admin");
        entityManager.persist(site);
        return site;
    }

    private ShFolder root(ShSite site, String name) {
        ShFolder folder = new ShFolder();
        folder.setName(name);
        folder.setRootFolder((byte) 1);
        folder.setShSite(site);
        folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
        folder.setOwner("admin");
        entityManager.persist(folder);
        return folder;
    }

    /** A fresh child of this site's root — one per test, see the class note. */
    private String folder(String name) {
        return folderUnder(rootId, name);
    }

    private String folderUnder(String parentId, String name) {
        return tx().execute(status -> {
            ShFolder folder = new ShFolder();
            folder.setName(name);
            folder.setRootFolder((byte) 0);
            folder.setParentFolder(entityManager.find(ShFolder.class, parentId));
            folder.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            folder.setOwner("admin");
            entityManager.persist(folder);
            return folder.getId();
        });
    }

    private String post(String folderId, String site, String title, String furl, ShPostState state) {
        String id = UUID.randomUUID().toString();
        addRow(id, folderId, site, title, furl, state);
        return id;
    }

    private void addRow(String id, String folderId, String site, String title, String furl,
                        ShPostState state) {
        tx().executeWithoutResult(status -> {
            ShPostUnified post = new ShPostUnified();
            post.setId(id);
            post.setState(state);
            post.setShFolder(entityManager.find(ShFolder.class, folderId));
            post.setShSite(entityManager.find(ShSite.class, site));
            post.setShPostType(entityManager.find(ShPostType.class, postTypeId));
            post.setTitle(title);
            post.setFurl(furl);
            post.setOwner("admin");
            post.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            post.setData("{}");
            entityManager.persist(post);
        });
    }

    private List<ShPostUnified> rowsIn(String folderId) {
        return tx().execute(status -> shPostUnifiedRepository.findByShFolderAndStateIn(
                shFolderRepository.findById(folderId).orElseThrow(), BOTH));
    }

    private List<ShPostUnified> copiesIn(String folderId, String sourceId) {
        return rowsIn(folderId).stream().filter(row -> !sourceId.equals(row.getId())).toList();
    }

    private ShPostUnified onlyCopyIn(String folderId, String sourceId) {
        List<ShPostUnified> copies = copiesIn(folderId, sourceId);
        assertThat(copies).hasSize(1);
        return copies.get(0);
    }

    private List<String> furlsIn(String folderId) {
        return rowsIn(folderId).stream().map(ShPostUnified::getFurl).toList();
    }

    private List<String> titlesIn(String folderId) {
        return rowsIn(folderId).stream().map(ShPostUnified::getTitle).toList();
    }

    private List<String> namesUnder(String parentId) {
        return tx().execute(status -> shFolderRepository
                .findByParentFolder(shFolderRepository.findById(parentId).orElseThrow())
                .stream().map(ShFolder::getName).toList());
    }

    private String childIdOf(String parentId, String name) {
        return tx().execute(status -> shFolderRepository
                .findByParentFolder(shFolderRepository.findById(parentId).orElseThrow())
                .stream().filter(f -> name.equals(f.getName())).findFirst().orElseThrow().getId());
    }
}
