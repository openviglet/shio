/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.release;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentConfirm;
import com.viglet.shio.api.agent.ShAgentContextService;
import com.viglet.shio.api.agent.ShAgentContextTerse;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.token.ShApiTokenEnv;
import com.viglet.shio.persistence.model.token.ShApiTokenScope;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseItemRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH993 — running a release: the set goes live together, or not at all.
 *
 * <p>The half that makes a release worth having. Every post publishes in one transaction, the run
 * takes SH108's confirm token from its own plan because what it puts in front of visitors is a set,
 * each item keeps the revision it was at as the anchor SH994 will roll back to, and {@code when}
 * hands the run to the SH15 sweep.</p>
 *
 * @author Alexandre Oliveira
 */
// ShScheduleRequesterTest's context (SH423), under names nobody else uses.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShReleaseRunTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh993run-" + RUN;
    private static final String WHEN = "2099-10-01T09:00:00Z";

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentContextService shAgentContextService;
    @Autowired
    private ShAgentReadService shAgentReadService;
    @Autowired
    private ShReleaseService shReleaseService;
    @Autowired
    private ShReleaseRunner shReleaseRunner;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private ShReleaseRepository shReleaseRepository;
    @Autowired
    private ShReleaseItemRepository shReleaseItemRepository;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> posts = new HashSet<>();
    private String siteId;

    @BeforeAll
    void seed() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Instant then = Instant.parse("2026-01-01T00:00:00Z");
            ShSite site = new ShSite();
            site.setName(SITE);
            site.setDate(then);
            site.setOwner("admin");
            entityManager.persist(site);
            siteId = site.getId();

            ShFolder root = new ShFolder();
            root.setName("root");
            root.setRootFolder((byte) 1);
            root.setShSite(site);
            root.setDate(then);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("PT-SH993RUN-" + RUN);
            type.setTitle("SH993 Run Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);

            // One page per test, because a publish in one of them is visible to the next: they
            // share a database, and a release RUNS, so nothing here can be re-used across tests.
            for (String url : List.of("/a1", "/a2", "/b1", "/b2", "/c1", "/d1", "/e1", "/f1",
                    "/g1", "/h1")) {
                ShPostUnified draft = new ShPostUnified();
                draft.setId(SITE + url.replace('/', '-'));
                draft.setState(ShPostState.DRAFT);
                draft.setShSite(site);
                draft.setShFolder(root);
                draft.setShPostType(type);
                draft.setTitle(url);
                draft.setFurl(url);
                draft.setLocale("en");
                draft.setOwner("admin");
                draft.setDate(then);
                draft.setData("{\"title\":\"before\"}");
                entityManager.persist(draft);
                posts.add(draft.getId());
            }
        });
    }

    @AfterAll
    void tearDown() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            shReleaseRepository.findBySiteIdOrderByNameAsc(siteId).forEach(release -> {
                shReleaseItemRepository.deleteAll(
                        shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(release.getId()));
                shReleaseRepository.delete(release);
            });
            posts.forEach(id -> shPostUnifiedRepository.findById(id).forEach(shPostUnifiedRepository::delete));
        });
    }

    private ShAgentBatch.Op op(String op, String name, String... urls) {
        ObjectNode data = null;
        if (urls.length > 0) {
            data = objectMapper.createObjectNode();
            var list = data.putArray("posts");
            for (String url : urls) {
                list.add(url);
            }
        }
        return new ShAgentBatch.Op(op, "release:" + SITE + "/" + name, null, null, null, data);
    }

    private ShAgentBatch.Op runOp(String name, String when, Boolean cancel) {
        return new ShAgentBatch.Op(ShAgentBatchService.RELEASE_RUN, "release:" + SITE + "/" + name,
                null, null, null, null, when, null, null, null, null, null, cancel);
    }

    private ShAgentChangeSet run(boolean dryRun, String confirm, ShAgentBatch.Op... ops) {
        return shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(ops), confirm), false,
                "ut-sh993run");
    }

    /** A release holding the named URLs, planned and filled. */
    private String plan(String name, String... urls) {
        run(false, null, op(ShAgentBatchService.RELEASE_UPSERT, name), op(ShAgentBatchService.RELEASE_ADD, name, urls));
        return name;
    }

    private ShRelease release(String name) {
        return shReleaseService.require(siteId, SITE, name);
    }

    private boolean live(String url) {
        return shPostUnifiedRepository.findByIdAndState(SITE + url.replace('/', '-'),
                ShPostState.PUBLISHED).isPresent();
    }

    @Test
    @DisplayName("SH993: a run takes a confirm token from its own plan, and then the set is live")
    void aRunIsConfirmedAndAtomic() {
        String name = plan("go-" + RUN, "/a1", "/a2");

        assertThatThrownBy(() -> run(false, null, runOp(name, null, null)))
                .as("SH993: what a run puts in front of visitors is a set, so the caller has to have "
                        + "seen the set — SH108's two steps, for a publish rather than a delete")
                .isInstanceOf(ShAgentConfirm.ConfirmRequiredException.class);
        assertThat(live("/a1")).isFalse();

        ShAgentChangeSet plan = run(true, null, runOp(name, null, null));
        assertThat(plan.results().get(0).detail()).isEqualTo("2 live");
        assertThat(live("/a1")).as("SH993: a plan writes nothing").isFalse();

        run(false, plan.confirm(), runOp(name, null, null));

        assertThat(live("/a1")).isTrue();
        assertThat(live("/a2")).isTrue();
        assertThat(release(name).getState()).isEqualTo("PUBLISHED");
        assertThatThrownBy(() -> run(false, null, runOp(name, null, null)))
                .as("SH993: a release that ran is the record of what went live, not a button")
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(String.valueOf(((ShAgentBatchService.BatchOpException)
                        failure).problem().toProblemDetail().getType())).endsWith("release-closed"));
    }

    @Test
    @DisplayName("SH993: a confirm taken before a post joined the release is refused")
    void theTokenCoversTheSet() {
        String name = plan("grew-" + RUN, "/b1");
        String stale = run(true, null, runOp(name, null, null)).confirm();

        run(false, null, op(ShAgentBatchService.RELEASE_ADD, name, "/b2"));

        assertThatThrownBy(() -> run(false, stale, runOp(name, null, null)))
                .as("SH993: the token covers the SET; a page added after the plan would otherwise go "
                        + "live on a confirmation nobody gave for it")
                .isInstanceOf(ShAgentConfirm.ConfirmStaleException.class);
    }

    @Test
    @DisplayName("SH993: a run with when hands the release to the sweep, and cancel calls it off")
    void aScheduledRunWaits() {
        String name = plan("later-" + RUN, "/c1");

        assertThat(run(false, null, runOp(name, WHEN, null)).results().get(0).detail())
                .isEqualTo("scheduled to run at " + WHEN);
        assertThat(release(name).getState()).isEqualTo("SCHEDULED");
        assertThat(live("/c1")).as("SH993: scheduled is not run").isFalse();
        assertThat(shReleaseService.dueRuns(Instant.parse("2099-09-01T00:00:00Z")))
                .as("SH993: and it is not due yet")
                .noneMatch(due -> due.getName().equals(name));

        assertThat(shReleaseService.dueRuns(Instant.parse("2099-11-01T00:00:00Z")))
                .as("SH993: the sweep asks this for what to fire, so a release that is never due "
                        + "never runs — the failure a curator would read as the launch not happening")
                .anyMatch(due -> due.getName().equals(name));

        assertThat(run(false, null, runOp(name, null, true)).results().get(0).detail())
                .isEqualTo("cancelled the run scheduled for " + WHEN);
        assertThat(release(name).getState()).isEqualTo("PLANNED");
        assertThat(release(name).getScheduledAt()).isNull();
    }

    @Test
    @DisplayName("SH993: running records each post's revision as the anchor a rollback returns to")
    void everyItemKeepsItsAnchor() {
        String url = "/d1";
        String postId = SITE + url.replace('/', '-');
        shPostUnifiedWriteService.publish(postId, "admin");
        shPostUnifiedWriteService.unpublish(postId, "admin");
        String name = plan("anchored-" + RUN, url);

        shReleaseRunner.run(release(name), "ut-sh993run");

        assertThat(shReleaseItemRepository.findByReleaseIdOrderByAddedAtAsc(release(name).getId()))
                .as("SH993: version_before is what SH994 rolls back to, and an item without one is "
                        + "the page a rollback would silently skip")
                .allSatisfy(item -> assertThat(item.getVersionBefore()).isNotNull());
    }

    @Test
    @DisplayName("SH993: state=release:<name> reads the page as that release found it")
    void theReleasePerspectiveReadsWhatTheRunReplaced() {
        String url = "/f1";
        String postId = SITE + url.replace('/', '-');
        // A page that was live, saying one thing, before a release publishes it saying another.
        shPostUnifiedWriteService.publish(postId, "admin");
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified draft = shPostUnifiedRepository
                    .findByIdAndState(postId, ShPostState.DRAFT).orElseThrow();
            draft.setData("{\"title\":\"after\"}");
            shPostUnifiedRepository.save(draft);
        });
        String name = plan("history-" + RUN, url);
        String address = "post:" + SITE + url;

        assertThatThrownBy(() -> shAgentReadService.read(List.of(address), null, false, false,
                "release:" + name, false))
                .as("SH993: a release that has not run replaced nothing, and the draft is what the "
                        + "page will become rather than what it was")
                .isInstanceOf(ShReleaseService.ReleaseHasNotRunException.class);

        shReleaseRunner.run(release(name), "ut-sh993run");

        assertThat(shAgentReadService.read(List.of(address), null, false, false,
                "release:" + name, false).get(0).data().toString())
                .as("SH993: state=published is what the launch PUT live; the version only the "
                        + "release knows is the one it replaced")
                .contains("before");
        assertThat(shAgentReadService.read(List.of(address), null, false, false, "published", false)
                .get(0).data().toString()).contains("after");
        assertThatThrownBy(() -> shAgentReadService.read(List.of("post:" + SITE + "/a1"), null,
                false, false, "release:" + name, false))
                .isInstanceOf(ShReleaseService.PostNotInReleaseException.class);
    }

    @Test
    @DisplayName("SH993: archiving takes a release out of the listing and out of the sweep, reversibly")
    void archivingHidesAndStops() {
        String name = plan("done-" + RUN, "/g1");
        run(false, null, runOp(name, WHEN, null));

        run(false, null, new ShAgentBatch.Op(ShAgentBatchService.RELEASE_ARCHIVE,
                "release:" + SITE + "/" + name, null, null, null, null));

        assertThat(release(name).getArchivedAt()).isNotNull();
        assertThat(shReleaseService.listed(siteId))
                .as("SH993: archiving is what keeps the pack a reader scrolls short")
                .noneMatch(listed -> listed.getName().equals(name));
        assertThat(shReleaseService.dueRuns(Instant.parse("2099-11-01T00:00:00Z")))
                .as("SH993: archiving a scheduled release is how a launch is called off for good, "
                        + "so a sweep that still fired it would publish a set somebody put away")
                .noneMatch(due -> due.getName().equals(name));
        assertThatThrownBy(() -> run(false, null, runOp(name, null, null)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(String.valueOf(((ShAgentBatchService.BatchOpException)
                        failure).problem().toProblemDetail().getType())).endsWith("release-archived"));

        run(false, null, new ShAgentBatch.Op(ShAgentBatchService.RELEASE_ARCHIVE,
                "release:" + SITE + "/" + name, null, null, null, null, null, null, null, null,
                null, null, true));

        assertThat(release(name).getArchivedAt()).as("SH993: and it is not a delete").isNull();
        assertThat(shReleaseService.listed(siteId)).anyMatch(listed -> listed.getName().equals(name));
    }

    @Test
    @DisplayName("SH993: the context pack lists a site's releases when asked for them")
    void thePackListsThem() {
        String name = plan("packed-" + RUN, "/h1");

        String terse = ShAgentContextTerse.render(
                shAgentContextService.context(SITE, 1, null, "releases", null));

        assertThat(terse)
                .as("SH993: a release is addressed by a name, and find?release= answers only a name "
                        + "it is given — without a listing an agent cannot discover one")
                .contains("release release:" + SITE + "/" + name + " state=PLANNED posts=1");
        assertThat(ShAgentContextTerse.render(shAgentContextService.context(SITE, 1, null, null, null)))
                .as("SH993: and a default pack is unchanged, because it is opt-in")
                .doesNotContain("release release:" + SITE + "/" + name);
    }

    @Test
    @DisplayName("SH993: a token without mayPublish cannot use a release to publish")
    void theCapabilityGateCoversTheRelease() {
        String name = plan("gated-" + RUN, "/e1");
        var authentication = new UsernamePasswordAuthenticationToken("alice", null,
                List.of(new SimpleGrantedAuthority("ROLE_AGENT")));
        authentication.setDetails(new ShApiTokenContext("tok-sh993", ShApiTokenScope.AGENT,
                Set.of(), ShApiTokenEnv.PROD, false));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            assertThatThrownBy(() -> run(true, null, runOp(name, null, null)))
                    .as("SH993: a release that published without mayPublish would be the way around "
                            + "SH107, and the credential would never learn it had one")
                    .isInstanceOf(ShAgentBatchService.PublishNotPermittedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
