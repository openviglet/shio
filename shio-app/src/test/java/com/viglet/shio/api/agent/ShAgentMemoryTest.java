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

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.agent.memory.dto.ShAgentNoteRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH114 — the instance memory, and the property that makes it worth a table.
 *
 * <p>The thing under test is not CRUD. It is that <b>a note written now is read back by
 * the next session without the next session asking for it</b>: the context pack — the one
 * call an agent already makes first — carries it. Everything else here guards the ways
 * that promise degrades: a duplicate key accumulating near-identical notes the reader has
 * to reconcile, an unattributed note that cannot be weighed against a derived fact, a
 * site-scoped note leaking past the allow-list, and prose long enough to push the sitemap
 * out of the very pack it rode in on.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentMemoryTest {

    private static final String MEMORY_MARKER = "[memory:";

    @Autowired
    private ShAgentMemoryService shAgentMemoryService;
    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    @BeforeAll
    void setup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            siteName = "memory-site-" + UUID.randomUUID().toString().substring(0, 8);
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
        });
    }

    private ShAgentNote remember(String scope, String key, String note, String author) {
        return shAgentMemoryService.remember(new ShAgentNoteRequest(scope, key, note), author);
    }

    private List<String> conventions() {
        return shAgentContextService.context(siteName, null, 0, "conventions", null).conventions();
    }

    @Test
    void aRememberedNoteComesBackInTheContextPack() {
        // The whole point of the feature, stated as one assertion.
        remember(siteName, "blog.post-type", "This site's blog uses Article, not Post.", "claude");

        assertThat(conventions())
                .anySatisfy(line -> assertThat(line)
                        .contains("This site's blog uses Article, not Post.")
                        // Attributed: a reader must be able to weigh an agent's inference
                        // against a derived fact, and it cannot do that anonymously.
                        .contains("by claude")
                        .startsWith(MEMORY_MARKER));

        shAgentMemoryService.forget(siteName, "blog.post-type");
    }

    @Test
    void reStatingAConventionRewritesItRatherThanAddingASecond() {
        remember(null, "copy.source", "Copy is authored in the console.", "human");
        remember(null, "copy.source", "Copy is authored in files, never via the API.", "human");

        List<ShAgentNote> notes = shAgentMemoryService.notes(null).stream()
                .filter(note -> "copy.source".equals(note.key()))
                .toList();

        // One row, the newer text. Two rows would mean every future session pays to
        // reconcile a contradiction that nobody intended to state.
        assertThat(notes).singleElement().satisfies(note ->
                assertThat(note.note()).isEqualTo("Copy is authored in files, never via the API."));

        shAgentMemoryService.forget(null, "copy.source");
    }

    @Test
    void keysAreNormalizedSoTheUpsertIsNotCaseSensitive() {
        remember(null, "Deploy.Target", "Ship to staging first.", "human");
        remember(null, "deploy.target", "Ship to staging first, always.", "human");

        assertThat(shAgentMemoryService.notes(null).stream()
                .filter(note -> note.key().startsWith("deploy.target")).toList())
                .singleElement()
                .satisfies(note -> assertThat(note.note()).endsWith("always."));

        shAgentMemoryService.forget(null, "DEPLOY.TARGET");
    }

    @Test
    void instanceWideNotesComeBeforePerSiteOnes() {
        remember(null, "zz.instance-wide", "Holds everywhere.", "human");
        remember(siteName, "aa.site-scoped", "Holds here only.", "human");

        List<ShAgentNote> notes = shAgentMemoryService.notes(siteName);
        int instanceWide = indexOfKey(notes, "zz.instance-wide");
        int siteScoped = indexOfKey(notes, "aa.site-scoped");

        // Ordering is by scope, not alphabetical by key: the rules that hold everywhere
        // are read before the exceptions to them, whatever they happen to be called.
        assertThat(instanceWide).isGreaterThanOrEqualTo(0).isLessThan(siteScoped);

        shAgentMemoryService.forget(null, "zz.instance-wide");
        shAgentMemoryService.forget(siteName, "aa.site-scoped");
    }

    @Test
    void forgettingIsIdempotent() {
        remember(null, "gone", "Temporary.", "human");

        assertThat(shAgentMemoryService.forget(null, "gone")).isTrue();
        // A second delete is not a failure. A retry that reports one is a retry nobody
        // makes, and an agent that stops retrying leaves rows behind.
        assertThat(shAgentMemoryService.forget(null, "gone")).isFalse();
    }

    @Test
    void aNoteScopedToAnUnreadableSiteIsRefusedRatherThanFiled() {
        assertThatThrownBy(() -> remember("no-such-site-" + UUID.randomUUID(), "x", "y", "human"))
                .isInstanceOf(ShAgentContextService.UnknownSiteException.class);
    }

    @Test
    void aKeyThatIsNotASlugIsATeachingError() {
        assertThatThrownBy(() -> remember(null, "not a slug!", "y", "human"))
                .isInstanceOf(ShAgentMemoryService.InvalidNoteException.class)
                .hasMessageContaining("key");
    }

    @Test
    void anEmptyNoteIsRefusedWithTheDeleteItProbablyMeant() {
        assertThatThrownBy(() -> remember(null, "empty", "   ", "human"))
                .isInstanceOf(ShAgentMemoryService.InvalidNoteException.class);
    }

    @Test
    void aNoteLongerThanTheCeilingIsRefused() {
        // The ceiling is a token ceiling in disguise: the notes ride in the context pack,
        // and prose there costs every session, not just the one that wrote it.
        assertThatThrownBy(() -> remember(null, "essay", "x".repeat(2001), "human"))
                .isInstanceOf(ShAgentMemoryService.InvalidNoteException.class);
    }

    @Test
    void theListingReportsTheFullCountEvenWhenItReturnsFewerRows() {
        remember(null, "one", "First.", "human");
        remember(null, "two", "Second.", "human");

        var memory = shAgentMemoryService.memory(null, 1);

        // SH138's rule: `found` describes the run, `notes` describes the response. A cut
        // the caller cannot see is a cut the caller will not page past.
        assertThat(memory.notes()).hasSize(1);
        assertThat(memory.found()).isGreaterThanOrEqualTo(2);

        shAgentMemoryService.forget(null, "one");
        shAgentMemoryService.forget(null, "two");
    }

    @Test
    void theContextPackTellsTheAgentHowToWriteAMemory() {
        // A store nobody writes to never pays back the row it costs, so the instruction
        // is derived — always present, whether or not anything has been remembered yet.
        assertThat(conventions()).anySatisfy(line ->
                assertThat(line).contains("shio remember"));
    }

    @Test
    void aMemoryChangesThePackDigest() {
        ShAgentContext before = shAgentContextService.context(siteName, null, 0, null, null);
        remember(siteName, "digest.probe", "Something new to say.", "human");
        ShAgentContext after = shAgentContextService.context(siteName, null, 0, null, null);

        // SH116 hands the digest to If-None-Match. A memory that did not move it would
        // leave every cached client reading yesterday's conventions forever.
        assertThat(after.digest()).isNotEqualTo(before.digest());

        shAgentMemoryService.forget(siteName, "digest.probe");
    }

    private static int indexOfKey(List<ShAgentNote> notes, String key) {
        for (int i = 0; i < notes.size(); i++) {
            if (key.equals(notes.get(i).key())) {
                return i;
            }
        }
        return -1;
    }
}
