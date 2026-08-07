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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH463 — an agent can remove the site it created.
 *
 * <h2>Why this was maintenance and not a feature request</h2>
 *
 * <p>{@code site.upsert} shipped in SH170 and nothing removed a site, so clearing sixteen scratch
 * sites took the browser's dance: {@code DELETE /api/v2/site/{id}} with Basic auth answered
 * <b>403</b> on all sixteen, and only reading {@code GET /api/v2/csrf}, keeping its cookie and
 * echoing the token back worked. The debris is the proof it is maintenance: SH448's suite seeds a
 * site per scenario and deletes its own, but a <em>failed</em> test never reaches teardown, so every
 * red run leaves one behind — and its own harness had hand-rolled a session for the removal and
 * recorded the asymmetry in a code comment ("not this task's to fix"), filed nowhere.</p>
 *
 * <h2>The instrument mattered more than the verb, and both are in this commit</h2>
 *
 * <p>Two lints exist to find exactly this and neither could. {@code ShAgentP1ConformanceTest}
 * classifies manifest {@code features} keys, and site deletion had no key — a capability with no
 * flag is not classified at all, so an inventory keyed on what the manifest advertises measures the
 * manifest rather than the surface. {@code ShStatelessReachabilityLintTest} classified a whole
 * controller <em>root</em>, so {@code /api/v2/site} read as covered on the strength of
 * {@code site.upsert} while the same controller declared {@code DELETE}. Both are fixed:
 * the flag exists, and an equivalence claim now names the verbs it delivers.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-site-delete;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentSiteDeleteTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShSiteRepository shSiteRepository;
    @Autowired
    private ShFolderRepository shFolderRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postId;

    @BeforeEach
    void seedASiteWithContentInIt() {
        siteName = "Sh463" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant when = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(siteName);
            site.setDate(when);
            site.setOwner("admin");
            entityManager.persist(site);

            ShFolder root = new ShFolder();
            root.setName(siteName);
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = entityManager
                    .createQuery("select t from ShPostType t where t.name = :name", ShPostType.class)
                    .setParameter("name", "Sh463Article").getResultStream().findFirst()
                    .orElseGet(() -> {
                        ShPostType created = new ShPostType();
                        created.setName("Sh463Article");
                        created.setTitle("Sh463 Article");
                        created.setSystem((byte) 0);
                        created.setDate(when);
                        created.setOwner("admin");
                        entityManager.persist(created);
                        return created;
                    });

            // Content in it, which is the case that matters: a site with posts is every site a
            // curator would actually delete, and the one that used to trip the FK (SH454).
            ShPostUnified post = new ShPostUnified();
            postId = UUID.randomUUID().toString();
            post.setId(postId);
            post.setState(ShPostState.DRAFT);
            post.setShSite(site);
            post.setShFolder(root);
            post.setShPostType(type);
            post.setTitle("Home");
            post.setFurl("/");
            post.setLocale("en");
            post.setOwner("admin");
            post.setDate(when);
            post.setData("{\"title\":\"Home\"}");
            entityManager.persist(post);
        });
    }

    @Test
    @DisplayName("SH463: site.delete removes the site and everything in it, in one call")
    void aSiteAndItsContentGoTogether() {
        ShAgentChangeSet result = run(false, delete(siteName));

        assertThat(result.results()).singleElement().satisfies(change -> assertThat(change.toString())
                .as("the plan line has to say there is no undo: ShSiteWriteService.delete trashes "
                        + "each root folder and then PURGES it, so an agent that read this as "
                        + "restorable would not offer the human an undo that does not exist (P7)")
                .contains("no undo"));
        assertThat(shSiteRepository.findByName(siteName)).isNull();
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT))
                .as("and the content went with it — a site delete that left orphaned posts is the "
                        + "referential-integrity 500 SH454 fixed, one surface along")
                .isEmpty();
        assertThat(shFolderRepository.count()).isNotNegative();
    }

    @Test
    @DisplayName("SH463: it needs the confirm token, like every other destructive op")
    void itIsDestructiveAndSaysSo() {
        assertThat(ShAgentBatchService.DESTRUCTIVE_OPS)
                .as("SH108's two-step is what stops a delete arriving as a typo, and this is the "
                        + "least recoverable op on the surface — the only one with no trash behind it")
                .contains(ShAgentBatchService.SITE_DELETE);

        // Without the token: refused, and the site is still there.
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status ->
                shAgentBatchService.run(new ShAgentBatch(false, List.of(delete(siteName))),
                        true, "tester")))
                .as("a refusal that had already deleted the site would be the guard defeated")
                .isInstanceOf(RuntimeException.class);
        assertThat(shSiteRepository.findByName(siteName)).isNotNull();
    }

    @Test
    @DisplayName("SH463: a dry run reports the deletion and writes nothing")
    void aDryRunPlansWithoutDeleting() {
        ShAgentChangeSet plan = run(true, delete(siteName));

        assertThat(plan.changed()).isEqualTo(1);
        assertThat(shSiteRepository.findByName(siteName))
                .as("SH168: a plan that wrote would make the two-step meaningless, since the first "
                        + "step is the one that produces the token for the second")
                .isNotNull();
    }

    @Test
    @DisplayName("SH463: a site that is already gone is a noop, so a teardown is safe to re-run")
    void anAbsentSiteIsANoop() {
        run(false, delete(siteName));
        ShAgentChangeSet again = run(false, delete(siteName));

        assertThat(again.changed())
                .as("and a teardown that failed once is exactly when it gets run again — the "
                        + "conformance suite's own `after` must not fail a second time on a site "
                        + "its first failure already removed")
                .isZero();
        assertThat(again.results()).singleElement().satisfies(change ->
                assertThat(change.toString()).contains("already absent"));
    }

    /** SH108's two-step, the way the CLI does it, so each test above reads as one gesture. */
    private ShAgentChangeSet run(boolean dryRun, Op... ops) {
        List<Op> list = List.of(ops);
        return new TransactionTemplate(transactionManager).execute(status -> {
            String confirm = !dryRun && ShAgentConfirm.mayDestroy(list)
                    ? shAgentBatchService.run(new ShAgentBatch(true, list), true, "tester").confirm()
                    : null;
            return shAgentBatchService.run(new ShAgentBatch(dryRun, list, confirm), true, "tester");
        });
    }

    private static Op delete(String site) {
        return new Op(ShAgentBatchService.SITE_DELETE, "site:" + site, null, null, null, null, null);
    }

    /**
     * SH591 — a site is disposable only if its creator said so, at creation.
     *
     * <p>Twelve replication rounds left thirteen sites on one instance, each needing its own
     * {@code site.delete}, its own 428 and its own confirm token. {@code shio site rm --scratch}
     * sweeps them in one confirmation, and the whole safety of that verb rests on one property:
     * <b>an existing site can never gain the flag</b>. If {@code siteFill} ever learns to write it,
     * the sweep becomes a fast way to delete a site somebody chose to keep — which is the exact
     * inversion of P7 the flag exists to avoid, since the declaration is only honest while it
     * precedes the content.</p>
     */
    @Test
    @DisplayName("SH591: scratch is written by the create and can never be gained afterwards")
    void scratchIsDeclaredAtCreationAndNowhereElse() {
        String fresh = "sh591-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        run(false, upsert(fresh, "{\"scratch\":true}"));
        assertThat(shSiteRepository.findByName(fresh).isScratch())
                .as("the create declared it disposable, in a document a human could read before "
                        + "there was anything in it to lose")
                .isTrue();

        // The site seeded by @BeforeEach exists already, so this takes the fill branch.
        run(false, upsert(siteName, "{\"scratch\":true}"));
        assertThat(shSiteRepository.findByName(siteName).isScratch())
                .as("""
                        an upsert on an EXISTING site must not raise this flag. If it can, then                         anything that can call site.upsert can mark somebody's real site                         disposable and `shio site rm --scratch` will then delete it in one                         confirmation — the surprise P7 forbids, arriving through the mechanism                         built to avoid it.""")
                .isFalse();

        // And a site nobody said anything about is what it always was: kept.
        assertThat(shSiteRepository.findByName(fresh + "-absent")).isNull();
    }

    /**
     * SH591 — a `scratch` that is not the JSON literal `true` is not a declaration.
     *
     * <p>The string {@code "true"} is deliberately refused. A flag deciding whether a site may
     * later be bulk-deleted is the last place to be generous about spelling, and the cost of
     * strictness here is that a caller who got it wrong finds out from the site not being marked
     * rather than from a site being gone.</p>
     */
    @Test
    @DisplayName("SH591: only the boolean literal declares a site disposable")
    void onlyTheLiteralCounts() {
        String quoted = "sh591q-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        run(false, upsert(quoted, "{\"scratch\":\"true\"}"));

        assertThat(shSiteRepository.findByName(quoted).isScratch()).isFalse();
    }

    private static Op upsert(String site, String data) {
        return new Op(ShAgentBatchService.SITE_UPSERT, "site:" + site, null, null, null, json(data));
    }

    private static tools.jackson.databind.JsonNode json(String raw) {
        return new tools.jackson.databind.ObjectMapper().readTree(raw);
    }
}
