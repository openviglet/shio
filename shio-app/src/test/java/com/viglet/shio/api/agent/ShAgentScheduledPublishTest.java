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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH342 — scheduled publishing has an agent-native form.
 *
 * <h2>What was wrong</h2>
 *
 * <p>The manifest advertised {@code scheduledPublish: true} and the only way to use it was
 * {@code POST /api/v2/post-unified/{id}/schedule} — id-keyed, undeclared in the endpoint index, and
 * session-only. So an agent was told the instance could do this and had no call to make: SH338's
 * inventory named it and SH339's confirmed the CSRF half. It is now a {@code when} on
 * {@code post.publish}, which is the smallest possible shape — the op already names the subject.</p>
 *
 * <h2>The decision, and why it is a refusal</h2>
 *
 * <p>A {@code when} in the <b>past</b> is refused rather than treated as "now". Publishing now is
 * what the op means with no {@code when} at all, so accepting a past instant would make two different
 * calls mean the same thing — and would swallow the mistake this actually catches, which is a
 * timezone slip or a stale timestamp. The failure mode of the tempting behaviour is silence: the page
 * goes live immediately and the response says exactly what was asked for.</p>
 *
 * @author Alexandre Oliveira
 */
// Its own database (SH315): the assertions read a post's scheduled instant back, and a plan resolves
// against whatever sites exist.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-schedule;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentScheduledPublishTest {

    private static final String SITE = "SchedSite";
    private static final String URL = "/soon";
    private static final String FUTURE = "2099-08-01T09:00:00Z";

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShPostUnifiedRepository shPostUnifiedRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String postId;

    @BeforeAll
    void seedADraft() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
            site.setName(SITE);
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

            ShPostType type = new ShPostType();
            type.setName("SchedArticle");
            type.setTitle("Sched Article");
            type.setSystem((byte) 0);
            type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostUnified draft = new ShPostUnified();
            postId = "sched-soon";
            draft.setId(postId);
            draft.setState(ShPostState.DRAFT);
            draft.setShSite(site);
            draft.setShFolder(root);
            draft.setShPostType(type);
            draft.setTitle("Soon");
            draft.setFurl(URL);
            draft.setLocale("en");
            draft.setOwner("admin");
            draft.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            draft.setData("{\"title\":\"Soon\"}");
            entityManager.persist(draft);
        });
    }

    private ShAgentBatch batch(boolean dryRun, String when) {
        return new ShAgentBatch(dryRun, List.of(new ShAgentBatch.Op(
                ShAgentBatchService.POST_PUBLISH, "post:" + SITE + URL,
                null, null, null, null, when)));
    }

    @Test
    @DisplayName("SH342: a dry run reports the schedule and writes nothing")
    void aDryRunReportsTheSchedule() {
        var changes = shAgentBatchService.run(batch(true, FUTURE), false, "ut-sh342");

        assertThat(changes.results()).singleElement().satisfies(change ->
                assertThat(change.toString())
                        .as("the plan must name the instant, because 'published' and 'scheduled to "
                                + "publish' are different outcomes and a plan that said the first "
                                + "would be a plan the run does not honour")
                        .contains(FUTURE));
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT).orElseThrow()
                .getPublishScheduledDate())
                .as("a dry run writes nothing")
                .isNull();
    }

    @Test
    @DisplayName("SH342: a real run schedules through the SH15 writer, and does not publish now")
    void aRealRunSchedules() {
        shAgentBatchService.run(batch(false, FUTURE), false, "ut-sh342");

        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.DRAFT).orElseThrow()
                .getPublishScheduledDate())
                .as("set on the draft, by the same ShPostUnifiedWriteService#schedule the console "
                        + "route calls — P10: this is a shape over SH15, not a second scheduler")
                .isEqualTo(Instant.parse(FUTURE));
        assertThat(shPostUnifiedRepository.findByIdAndState(postId, ShPostState.PUBLISHED))
                .as("""
                        and it did NOT publish. A scheduled publish that also published immediately \
                        would be the worst possible reading of the argument: the caller asked for \
                        later and got now, with a response that says it worked.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH342: a past instant is refused, not treated as now")
    void aPastInstantIsRefused() {
        // BatchOpException is the wrapper SH81 puts round a per-op failure so the report can name
        // `ops[n]`; the refusal's own fields survive inside it, which is the part that matters.
        assertThatThrownBy(() -> shAgentBatchService.run(
                batch(true, "2020-01-01T00:00:00Z"), false, "ut-sh342"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> {
                    var problem = ((ShAgentBatchService.BatchOpException) failure)
                            .problem().toProblemDetail();
                    assertThat(problem.getStatus()).isEqualTo(400);
                    assertThat(problem.getDetail())
                            .as("the message has to say why a past date is not 'now', or the caller "
                                    + "reads the refusal as pedantry and reaches for the console")
                            .contains("already passed");
                    assertThat(problem.getProperties().get("fix").toString())
                            .contains("drop `when`");
                });
    }

    @Test
    @DisplayName("SH342: an unparseable instant is refused with the form it wanted")
    void anUnparseableInstantIsRefused() {
        assertThatThrownBy(() -> shAgentBatchService.run(
                batch(true, "next tuesday"), false, "ut-sh342"))
                .isInstanceOf(ShAgentBatchService.BatchOpException.class)
                .satisfies(failure -> assertThat(((ShAgentBatchService.BatchOpException) failure)
                        .problem().toProblemDetail().getProperties().get("example").toString())
                        .as("SH80: the example is a call that would work")
                        .contains("2026-08-01T09:00:00Z"));
    }
}
