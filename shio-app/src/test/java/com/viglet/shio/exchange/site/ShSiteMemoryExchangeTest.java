/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH161 — <b>a site's memory travels with the site.</b>
 *
 * <p>Until this, exporting a site dropped everything earlier sessions had worked out about
 * it: the SH68 package carried the content, the folders and the model, and none of the notes.
 * The compounding saving the instance memory exists to produce only compounds if it survives
 * a move, so an export that silently leaves it behind is the one that matters most —
 * migrating an instance is precisely when a site's accumulated conventions are most
 * valuable and least reproducible.</p>
 *
 * <p>Two rules are asserted here rather than merely written down. <b>Only the site-scoped
 * notes travel</b>: an instance-wide note describes the <em>installation</em>, and importing
 * another instance's house rules alongside somebody's blog is exactly the surprise P7 exists
 * to prevent. And <b>a note is re-scoped on import</b>, because a note names its site by name
 * and an import renames the site — carried through verbatim, every note would be filed
 * against a site that does not exist here, which is indistinguishable from losing them.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShSiteMemoryExchangeTest {

    @Autowired
    private ShSiteExportService exportService;
    @Autowired
    private ShSiteImportService importService;
    @Autowired
    private ShAgentMemoryService memoryService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("SH161: a site's notes survive an export and land re-scoped on import")
    void notesTravelWithTheSite() {
        String siteName = "sh161-" + UUID.randomUUID().toString().substring(0, 8);
        String siteId = seedSite(siteName);

        memoryService.remember(new ShAgentNoteRequest(siteName, "blog.post-type",
                "This site's blog uses Article, not Post."), "alex");
        memoryService.remember(new ShAgentNoteRequest(siteName, "tone",
                "Headlines are sentence case."), "alex");
        // Instance-wide. It must not travel.
        memoryService.remember(new ShAgentNoteRequest(null, "sh161.house-rule",
                "Copy is authored in files, never through the API."), "alex");

        ShExchangePackage pkg = exportService.buildPackage(siteId);

        assertThat(pkg.notes())
                .as("the site's own notes are in the package")
                .extracting(ShAgentNote::key)
                .containsExactlyInAnyOrder("blog.post-type", "tone");
        assertThat(pkg.notes())
                .as("attribution travels — SH114 keeps it so a human's instruction and an "
                        + "agent's inference stay distinguishable, and that survives a move")
                .allMatch(note -> "alex".equals(note.author()));
        assertThat(pkg.notes())
                .as("an instance-wide note describes the installation, not the site (P7)")
                .noneMatch(note -> "sh161.house-rule".equals(note.key()));

        ShSite clone = new TransactionTemplate(transactionManager)
                .execute(status -> importService.importPackage(pkg, "importer"));

        assertThat(clone.getName())
                .as("a clone uniquifies the name, which is what makes re-scoping necessary")
                .isNotEqualTo(siteName);
        assertThat(memoryService.scopedNotes(clone.getName()))
                .extracting(ShAgentNote::key)
                .containsExactlyInAnyOrder("blog.post-type", "tone");
        assertThat(memoryService.scopedNotes(clone.getName()))
                .extracting(ShAgentNote::note)
                .contains("This site's blog uses Article, not Post.");
        assertThat(memoryService.scopedNotes(siteName))
                .as("the original site keeps its own notes — an import copies, it does not move")
                .hasSize(2);
    }

    /**
     * The compatibility story, and the reason {@code notes} has to default to empty rather
     * than being required: a package written before SH161 has no such key at all, and it must
     * still import.
     */
    @Test
    @DisplayName("SH161: a package with no notes imports exactly as it did before")
    void aPackageWithoutNotesStillImports() {
        String siteName = "sh161-bare-" + UUID.randomUUID().toString().substring(0, 8);
        String siteId = seedSite(siteName);

        ShExchangePackage pkg = exportService.buildPackage(siteId);
        ShExchangePackage legacy = new ShExchangePackage(pkg.version(), pkg.exportedAt(),
                pkg.generator(), pkg.sites(), pkg.postTypes(), pkg.folders(), pkg.posts(),
                pkg.files());

        assertThat(legacy.notes()).isEmpty();
        ShSite clone = new TransactionTemplate(transactionManager)
                .execute(status -> importService.importPackage(legacy, "importer"));
        assertThat(clone).isNotNull();
        assertThat(memoryService.scopedNotes(clone.getName())).isEmpty();
    }

    @Test
    @DisplayName("SH161: importing the same package twice rewrites the notes, never duplicates them")
    void reimportIsIdempotent() {
        String siteName = "sh161-twice-" + UUID.randomUUID().toString().substring(0, 8);
        String siteId = seedSite(siteName);
        memoryService.remember(new ShAgentNoteRequest(siteName, "tone", "First wording."), "alex");

        ShExchangePackage pkg = exportService.buildPackage(siteId);
        ShSite first = new TransactionTemplate(transactionManager)
                .execute(status -> importService.importPackage(pkg, "importer"));

        // Same package, same target name: the notes are upserted by (scope, key).
        List<ShAgentNote> after = memoryService.importNotes(first.getName(), pkg.notes()) > 0
                ? memoryService.scopedNotes(first.getName())
                : List.of();
        assertThat(after).hasSize(1);
        assertThat(after.get(0).note()).isEqualTo("First wording.");
    }

    private String seedSite(String siteName) {
        return new TransactionTemplate(transactionManager).execute(status -> {
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
            return site.getId();
        });
    }
}
