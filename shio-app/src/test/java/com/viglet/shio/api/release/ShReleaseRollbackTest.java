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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentConfirm;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseItemRepository;
import com.viglet.shio.persistence.repository.release.ShReleaseRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH994 — a release that went live wrongly goes back as the unit it went live as.
 *
 * <p>SH993 recorded an anchor per item and declared a ROLLED_BACK state, and nothing reached either:
 * a column written and never read is the shape that rots, and atomicity without a reverse is half the
 * promise. The assertion the design asked for is here — run a release, roll it back, and every item's
 * live bytes are its anchor's again, with the release reading ROLLED_BACK.</p>
 *
 * @author Alexandre Oliveira
 */
// ShScheduleRequesterTest's context (SH423), under names nobody else uses.
@SpringBootTest(properties = "shio.schedule.enabled=false")
@TestInstance(Lifecycle.PER_CLASS)
class ShReleaseRollbackTest {

    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);
    private static final String SITE = "sh994-" + RUN;

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShReleaseService shReleaseService;
    @Autowired
    private ShReleaseRunner shReleaseRunner;
    @Autowired
    private ShPostUnifiedWriteService shPostUnifiedWriteService;
    @Autowired
    private com.viglet.shio.api.post.revision.ShPostRevisionService shPostRevisionService;
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
            type.setName("PT-SH994-" + RUN);
            type.setTitle("SH994 Page");
            type.setSystem((byte) 0);
            type.setDate(then);
            type.setOwner("admin");
            entityManager.persist(type);

            for (String url : List.of("/old", "/new", "/second")) {
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

    private ShAgentChangeSet run(boolean dryRun, String confirm, ShAgentBatch.Op... ops) {
        return shAgentBatchService.run(new ShAgentBatch(dryRun, List.of(ops), confirm), false,
                "ut-sh994");
    }

    private ShRelease release(String name) {
        return shReleaseService.require(siteId, SITE, name);
    }

    private String liveData(String url) {
        return shPostUnifiedRepository
                .findByIdAndState(SITE + url.replace('/', '-'), ShPostState.PUBLISHED)
                .map(ShPostUnified::getData).orElse(null);
    }

    private void rewrite(String url, String title) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShPostUnified draft = shPostUnifiedRepository
                    .findByIdAndState(SITE + url.replace('/', '-'), ShPostState.DRAFT).orElseThrow();
            draft.setData("{\"title\":\"" + title + "\"}");
            shPostUnifiedRepository.save(draft);
        });
    }

    @Test
    @DisplayName("SH994: a release rolls back to every item's anchor, and reads ROLLED_BACK")
    void aReleaseGoesBackAsAUnit() {
        // `/old` was live before the launch; `/new` is a page the launch itself put live.
        String postId = SITE + "-old";
        shPostUnifiedWriteService.publish(postId, "admin");
        rewrite("/old", "launched");
        String name = "wrong-" + RUN;
        run(false, null, op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "/old", "/new"));
        String confirm = run(true, null, op(ShAgentBatchService.RELEASE_RUN, name)).confirm();
        run(false, confirm, op(ShAgentBatchService.RELEASE_RUN, name));
        assertThat(liveData("/old")).contains("launched");
        assertThat(liveData("/new")).isNotNull();

        ShAgentBatch.Op back = op(ShAgentBatchService.RELEASE_ROLLBACK, name);
        assertThatThrownBy(() -> run(false, null, back))
                .as("SH994: a rollback has the run's blast radius pointed the other way, so it takes "
                        + "the same two steps")
                .isInstanceOf(ShAgentConfirm.ConfirmRequiredException.class);
        ShAgentChangeSet plan = run(true, null, back);
        assertThat(plan.results().get(0).detail()).isEqualTo("1 back, 1 down");

        run(false, plan.confirm(), back);

        assertThat(liveData("/old"))
                .as("SH994: the page a launch replaced is what a rollback puts in front of visitors "
                        + "again — a rollback that only fixed the draft would answer 'put it back' "
                        + "with 'the draft is correct now'")
                .contains("before");
        assertThat(liveData("/new"))
                .as("SH994: a page the release published for the first time has no earlier version, "
                        + "so its undo is a takedown; leaving it live is the page a rollback missed")
                .isNull();
        assertThat(release(name).getState())
                .as("SH994: the state BA4 declared and nothing reached")
                .isEqualTo("ROLLED_BACK");
    }

    @Test
    @DisplayName("SH994: a release that never ran has nothing to roll back, and says so")
    void aPlannedReleaseRefuses() {
        String name = "never-" + RUN;
        run(false, null, op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "/second"));

        assertThatThrownBy(() -> run(true, null, op(ShAgentBatchService.RELEASE_ROLLBACK, name)))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(String.valueOf(((ShAgentBatchService.BatchOpException)
                        failure).problem().toProblemDetail().getType())).endsWith("release-not-run"));
    }

    @Test
    @DisplayName("SH994: rolling back keeps what it undid, so the launch can come forward again")
    void theLaunchIsStillThere() {
        String postId = SITE + "-second";
        shPostUnifiedWriteService.publish(postId, "admin");
        rewrite("/second", "take two");
        String name = "twice-" + RUN;
        run(false, null, op(ShAgentBatchService.RELEASE_UPSERT, name),
                op(ShAgentBatchService.RELEASE_ADD, name, "/second"));
        run(false, run(true, null, op(ShAgentBatchService.RELEASE_RUN, name)).confirm(),
                op(ShAgentBatchService.RELEASE_RUN, name));

        ShAgentBatch.Op back = op(ShAgentBatchService.RELEASE_ROLLBACK, name);
        run(false, run(true, null, back).confirm(), back);

        assertThat(shReleaseRunner.undo(release(name)).restores())
                .as("SH994: the anchors are still on the items, so what the rollback undid is a "
                        + "question that can still be asked")
                .contains(postId);
        assertThat(shPostRevisionService.revisions(postId).size())
                .as("SH994: SH987's rule holds — a rollback captures a revision rather than deleting "
                        + "one, so the launch it undid is there to come forward to")
                .isGreaterThanOrEqualTo(2);
    }
}
