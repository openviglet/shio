/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentChangeCursor;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.events.ShEventSink;
import com.viglet.shio.api.events.ShEventStreamHub;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;

/**
 * SH967 — what an open change stream costs the instance, measured before anything relies on it.
 *
 * <h2>Two numbers, and why these two</h2>
 *
 * <p>The stream is a pump: every pass reads the feed once for every open stream, whether or not
 * anything happened. So the cost has two shapes, and §LXIII.4 named both. <b>An idle stream</b> costs
 * the queries of one pass that finds nothing, paid every {@code shio.events.poll-millis} for as long
 * as a curator leaves a tab open. <b>A fan-out</b> — a hundred writes reaching fifty open screens —
 * costs whatever each entry costs each reader: an address lookup, and the content ACL walked up the
 * entry's folder chain for a reader who is not an administrator, which is the realistic reader.</p>
 *
 * <p><b>Statements prepared, not milliseconds</b> — SH264's rule, and {@code ShAgentBenchmark}'s: a
 * timing ceiling on a shared machine is a flake. The counts are per stream per pass and per entry
 * per stream, so they do not move when the fixture grows, and a change that makes a pass cost more
 * fails here by name with the number beside it.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shio-sh967;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "shio.schedule.enabled=false" })
@TestInstance(Lifecycle.PER_CLASS)
class ShEventStreamCostBenchmarkTest {

    private static final String BUDGETS = "/token-budgets.properties";

    /** The fan-out §LXIII.4 named: a hundred entries, fifty open screens. */
    private static final int ENTRIES = 100;
    private static final int STREAMS = 50;
    private static final int IDLE_PASSES = 10;

    @Autowired
    private ShEventStreamHub hub;
    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @PersistenceContext
    private EntityManager entityManager;

    private Statistics statistics;
    private Properties budgets;
    private String siteName;
    private final List<String> opened = new ArrayList<>();

    /** Counts events and drops them: the cost measured is the instance's, not a sink's. */
    private static final class CountingSink implements ShEventSink {

        int entries;

        @Override
        public void send(String id, String name, Object data) {
            entries += ((ShEventStreamHub.Page) data).changes().size();
        }

        @Override
        public void heartbeat() {
            // A heartbeat costs no query, and is not what is measured.
        }

        @Override
        public void close() {
            // Nothing is held.
        }
    }

    @BeforeAll
    void setup() throws IOException {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        budgets = new Properties();
        try (InputStream in = getClass().getResourceAsStream(BUDGETS)) {
            assertThat(in).as("missing %s", BUDGETS).isNotNull();
            budgets.load(in);
        }
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            siteName = "sh967-cost-" + UUID.randomUUID().toString().substring(0, 8);
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
        });
    }

    @AfterEach
    void closeStreams() {
        opened.forEach(hub::close);
        opened.clear();
        SecurityContextHolder.clearContext();
    }

    private int ceiling(String key) {
        String value = budgets.getProperty(key);
        assertThat(value).as("no budget declared for '%s' in %s", key, BUDGETS).isNotNull();
        return Integer.parseInt(value.trim());
    }

    /** A non-administrator, so the content ACL is walked rather than short-circuited. */
    private void asCurator() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("curator", "x", List.of()));
    }

    private CountingSink openStream() {
        CountingSink sink = new CountingSink();
        opened.add(hub.open(sink, new ShAgentChangeCursor(Instant.now(), "").toString()));
        return sink;
    }

    private long prepared(Runnable work) {
        statistics.clear();
        work.run();
        return statistics.getPrepareStatementCount();
    }

    @Test
    void sh967AnIdleStreamCostsAFixedNumberOfQueriesPerPass() {
        asCurator();
        openStream();

        long total = prepared(() -> {
            for (int pass = 0; pass < IDLE_PASSES; pass++) {
                hub.pump();
            }
        });
        double perPass = (double) total / IDLE_PASSES;

        System.out.printf("%n[SH967] idle stream: %d statements over %d passes = %.1f per pass%n",
                total, IDLE_PASSES, perPass);
        assertThat(perPass)
                .as("SH967: one open stream that finds nothing costs this many statements every"
                        + " shio.events.poll-millis. It is paid per open tab, for as long as the tab"
                        + " is open, which is why it is a budget")
                .isLessThanOrEqualTo(ceiling("events.idle.max-statements-per-stream-pass"));
    }

    @Test
    void sh967AFanOutCostsAFixedNumberOfQueriesPerEntryPerStream() {
        asCurator();
        List<CountingSink> sinks = new ArrayList<>();
        for (int stream = 0; stream < STREAMS; stream++) {
            sinks.add(openStream());
        }

        SecurityContextHolder.clearContext();
        List<ShAgentBatch.Op> ops = new ArrayList<>();
        String group = "fan-" + UUID.randomUUID().toString().substring(0, 8);
        for (int entry = 0; entry < ENTRIES; entry++) {
            ops.add(new ShAgentBatch.Op(ShAgentBatchService.FOLDER_UPSERT,
                    "folder:" + siteName + "/" + group + "-" + entry, null, null, null));
        }
        batchService.run(new ShAgentBatch(false, ops), false, "sh967-agent");

        long total = prepared(hub::pump);
        double perEntryPerStream = (double) total / (ENTRIES * STREAMS);

        assertThat(sinks).allSatisfy(sink -> assertThat(sink.entries)
                .as("every stream received every entry, or the cost below is of a smaller fan-out")
                .isEqualTo(ENTRIES));
        System.out.printf("[SH967] fan-out: %d entries to %d streams = %d statements, %.2f per entry"
                + " per stream%n", ENTRIES, STREAMS, total, perEntryPerStream);
        assertThat(perEntryPerStream)
                .as("SH967: each entry costs each open stream this many statements — the address"
                        + " lookup and the content ACL walked for a reader who is not an"
                        + " administrator. A hundred writes to fifty screens multiply it by 5000")
                .isLessThanOrEqualTo(ceiling("events.fanout.max-statements-per-entry-per-stream"));
    }
}
