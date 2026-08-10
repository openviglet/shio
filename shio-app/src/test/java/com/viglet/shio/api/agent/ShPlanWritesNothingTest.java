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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.dto.ShAgentBatch.Op;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.model.widget.ShWidget;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * A plan writes nothing, and the database says so rather than a reviewer (SH221).
 *
 * <h2>Why the property needed a mechanism</h2>
 *
 * <p>SH153 removed the last write from a blueprint plan, and the reasoning generalises further than
 * the fix did. {@code POST /agent/plan} and a {@code dryRun} batch write nothing today, but nothing
 * <em>enforced</em> it: the property was maintained by every author remembering it, and the last
 * time it was not, <b>the reason was good</b> — the planner needed to resolve a post-type the run
 * would create, and a rollback-only transaction was the obvious way to get one. That reason will
 * recur, and it will be just as locally defensible the next time.</p>
 *
 * <h2>Why this is a measurement and not a {@code readOnly} flag</h2>
 *
 * <p>§XI.15 proposed wrapping the dry-run paths in a {@code readOnly} transaction. That was the
 * right instinct and the wrong instrument, for two reasons found by trying to scope it:</p>
 *
 * <p><b>The entry points are shared.</b> All three {@code run(...)} overloads serve plan and apply
 * alike, and {@code dryRun} is a field of the body rather than a different call — so the flag
 * cannot move onto the class, and dispatching to a second annotated method from inside the service
 * is a self-invocation that never crosses the proxy.</p>
 *
 * <p><b>And {@code readOnly} does not mean the same thing on every database this product supports.</b>
 * Spring marks the JDBC connection read-only and Hibernate stops flushing; PostgreSQL then refuses
 * the write outright, while H2 and MariaDB accept a connection that is merely <em>advertised</em> as
 * read-only. A guarantee that holds on one of three engines is not structural — it is a flag that
 * happens to be enforced where CI runs Postgres, which is the shape of a rule people trust further
 * than it goes.</p>
 *
 * <p>Counting the mutations Hibernate performed is engine-independent and states the property
 * directly: <b>a plan inserted, updated and deleted nothing.</b> The next author who reaches for a
 * rollback-only transaction to resolve something that does not exist yet fails this, by name, on
 * every database.</p>
 *
 * <h2>Its own database</h2>
 *
 * <p>Statistics are cumulative on the {@code SessionFactory} and this counts writes across the whole
 * instance, so a sibling test writing content in another thread would be indistinguishable from the
 * defect (SH315).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh221;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true" })
@TestInstance(Lifecycle.PER_CLASS)
class ShPlanWritesNothingTest {

    @Autowired
    private ShAgentBatchService shAgentBatchService;
    @Autowired
    private ShAgentDesiredStateService shAgentDesiredStateService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String postTypeName;

    @BeforeAll
    void setup() {
        siteName = "sh221-" + UUID.randomUUID().toString().substring(0, 8);
        postTypeName = "Sh221Page" + UUID.randomUUID().toString().substring(0, 8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            ShSite site = new ShSite();
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

            // Its own type rather than a seeded one: `Text` is a widget name, and the seeds carry
            // the render vocabulary rather than a plain content type. Created here, before any
            // measurement starts, so the fixture's writes are not the writes under test.
            ShWidget widget = entityManager
                    .createQuery("select w from ShWidget w where w.name = 'Text'", ShWidget.class)
                    .getResultStream().findFirst().orElseThrow();

            ShPostType type = new ShPostType();
            type.setName(postTypeName);
            type.setTitle(postTypeName);
            type.setSystem((byte) 0);
            type.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            type.setOwner("admin");
            entityManager.persist(type);

            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName("title");
            attr.setLabel("Title");
            attr.setShPostType(type);
            attr.setShWidget(widget);
            attr.setIsTitle((byte) 1);
            attr.setIsSummary((byte) 0);
            attr.setRequired((byte) 1);
            attr.setOrdinal(1);
            entityManager.persist(attr);
        });
    }

    @Test
    @DisplayName("SH221: a dryRun batch that would create a folder and a post writes nothing")
    void aDryRunBatchWritesNothing() {
        // The shape SH153's defect had: ops that resolve against things the run itself would
        // create. A planner that reached for a rollback-only transaction to answer them would
        // pass every functional test in this repository and fail this one.
        ShAgentBatch batch = new ShAgentBatch(true, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/planned",
                        null, null, null),
                new Op(ShAgentBatchService.POST_UPSERT, "post:" + siteName + "/planned/page",
                        postTypeName, "/planned", null)));

        Mutations wrote = whileRunning(() -> shAgentBatchService.run(batch, false, "tester"));

        assertThat(wrote.total())
                .as("""
                        A plan writes nothing, and this is the database saying so rather than a \
                        reviewer. Counted rather than declared with `readOnly = true`, because that \
                        flag means different things on the three engines this product supports — \
                        PostgreSQL refuses the write, H2 and MariaDB accept a connection merely \
                        advertised as read-only (SH221). %s""".formatted(wrote))
                .isZero();
    }

    @Test
    @DisplayName("SH221: a desired-state plan writes nothing either")
    void aDesiredStatePlanWritesNothing() {
        // The other dry-run path, and the one whose whole contract is "diff, do not apply": a
        // `plan` that wrote would make `shio apply --check` a command that changes the thing it
        // was asked to report on.
        ShAgentDesiredState desired = new ShAgentDesiredState(siteName,
                List.of(new ShAgentDesiredState.Folder("/declared")),
                List.of(new ShAgentDesiredState.Post("/declared/page", postTypeName, "/declared",
                        null, null, null)),
                List.of(), false, null, false, null, false);

        Mutations wrote = whileRunning(
                () -> shAgentDesiredStateService.plan(desired, false, "tester"));

        assertThat(wrote.total())
                .as("`plan` is the read-only half of apply, and a write here turns a report into "
                        + "an edit (SH221). %s".formatted(wrote))
                .isZero();
    }

    @Test
    @DisplayName("SH221: the counter is not vacuous — a real apply moves it")
    void theCounterSeesARealWrite() {
        // Red by construction. A statistics reader that counted nothing would pass both assertions
        // above for ever and report a guarantee nobody has, which is the failure mode every lint
        // in this repository is written against.
        ShAgentBatch batch = new ShAgentBatch(false, List.of(
                new Op(ShAgentBatchService.FOLDER_UPSERT, "folder:" + siteName + "/real",
                        null, null, null)));

        Mutations wrote = whileRunning(() -> shAgentBatchService.run(batch, false, "tester"));

        assertThat(wrote.total())
                .as("an apply that wrote nothing would mean this instrument cannot see writes at "
                        + "all, and the two assertions above would be measuring silence")
                .isPositive();
    }

    /** What Hibernate wrote while the work ran. */
    private record Mutations(long inserts, long updates, long deletes) {

        long total() {
            return inserts + updates + deletes;
        }

        @Override
        public String toString() {
            return "inserts=%d updates=%d deletes=%d".formatted(inserts, updates, deletes);
        }
    }

    private Mutations whileRunning(Runnable work) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long inserts = statistics.getEntityInsertCount();
        long updates = statistics.getEntityUpdateCount();
        long deletes = statistics.getEntityDeleteCount();
        work.run();
        return new Mutations(
                statistics.getEntityInsertCount() - inserts,
                statistics.getEntityUpdateCount() - updates,
                statistics.getEntityDeleteCount() - deletes);
    }
}
