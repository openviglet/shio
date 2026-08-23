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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.turing.ShTuringProperties;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * {@code search.reindex} — indexing the content that was already there (SH413).
 *
 * <h2>The gap SH84's own seam could not close</h2>
 *
 * <p>SH84 indexes {@code AFTER_COMMIT} on a publish, which covers exactly one case: content
 * published <em>after</em> indexing was switched on. An operator switching it on is normally doing
 * so for a site that already exists, and for them the index stayed empty with no error anywhere.</p>
 *
 * <h2>Turing is enabled here, and pointed at a port nothing answers on</h2>
 *
 * <p>Deliberately, and it is what makes this test worth having. The alternative — a mock —
 * would assert that the op calls a method, which is the least interesting thing about it. With a
 * real service pointed at a dead address, {@code send} genuinely fails, so what is exercised is the
 * whole path: the site resolves, the page is read and bounded, every published row is mapped
 * through {@code indexItem}, and the report has to be <b>honest about a send that did not land</b>
 * rather than counting an attempt as a success. That last property is the one an operator's
 * decision rests on.</p>
 *
 * <h2>It costs no Spring context, and the first version did</h2>
 *
 * <p>Written first with a test-scoped property override turning Turing on, which keys a Spring
 * context of its own — {@code ShContextCacheHeadroomTest} priced that at one of the two slots left
 * under the cache ceiling. The overrides existed only to flip two fields on a
 * {@code @ConfigurationProperties} bean, so the bean is mutated instead and restored afterwards,
 * and the shared context is reused. SH315's rule permits it: a test that only reads <em>its own</em>
 * fixtures may share one, and this asserts about a site it created itself.</p>
 *
 * <p>(The prose above deliberately does not spell that annotation out. The headroom counter is a
 * source scan, so a comment quoting the literal form is counted as a declaration — which is how
 * this class was still charged for a context after the annotation was gone.)</p>
 *
 * <p>{@code ShAgentBatchTest} covers the other half — an instance with indexing off, where the op
 * must be a {@code noop} rather than a failure, because an agent running a standard build sequence
 * against an instance that does not index should not have its batch die.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentSearchReindexTest {

    /** More than one page at the limit the tests ask for, so "how many are left" has an answer. */
    private static final int PUBLISHED = 7;

    @Autowired
    private ShAgentBatchService batchService;
    @Autowired
    private ShTuringProperties turingProperties;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;

    /** What the shared context had, so it is put back and no other class sees this one's state. */
    private boolean wasEnabled;
    private String wasUrl;
    private String wasDefaultSite;
    private Duration wasRetryBackoff;

    @AfterAll
    void restore() {
        turingProperties.setEnabled(wasEnabled);
        turingProperties.setUrl(wasUrl);
        turingProperties.setDefaultSite(wasDefaultSite);
        turingProperties.setRetryBackoff(wasRetryBackoff);
    }

    @BeforeAll
    void setup() {
        wasEnabled = turingProperties.isEnabled();
        wasUrl = turingProperties.getUrl();
        wasDefaultSite = turingProperties.getDefaultSite();
        wasRetryBackoff = turingProperties.getRetryBackoff();
        turingProperties.setEnabled(true);
        // Reachable syntactically, dead in fact. A hostname that does not resolve would make this a
        // DNS test; a closed port on localhost fails fast and for the reason under test.
        turingProperties.setUrl("http://127.0.0.1:1");
        turingProperties.setDefaultSite("Sh413SN");
        // SH912 — every one of these pushes is *meant* to fail, and since the retry landed each one
        // waits 1s then 2s before giving up. Seven posts is 21 seconds of a suite sleeping to prove
        // something this class is not about: the bound is asserted in ShTuringIndexRetryTest, and
        // what is under test here is that a reindex dispatches at all. Zero keeps the failure fast
        // for the reason the closed port was chosen in the first place.
        turingProperties.setRetryBackoff(Duration.ZERO);

        String tag = UUID.randomUUID().toString().substring(0, 8);
        siteName = "sh413-" + tag;

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
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
            root.setPosition((byte) 1);
            root.setDate(when);
            root.setOwner("admin");
            entityManager.persist(root);

            ShPostType type = new ShPostType();
            type.setName("Sh413Article" + tag);
            type.setTitle("Sh413Article");
            type.setSystem((byte) 0);
            type.setDate(when);
            type.setOwner("admin");
            entityManager.persist(type);

            // Published rows — the ones the backfill is for. A draft is deliberately included and
            // must not be indexed: the index serves readers, and a draft is not for them.
            for (int index = 0; index < PUBLISHED; index++) {
                post(site, root, type, "Published " + index, "/p" + index,
                        ShPostState.PUBLISHED, when);
            }
            post(site, root, type, "A draft", "/draft", ShPostState.DRAFT, when);
        });
    }

    private ShAgentChangeSet run(ShAgentBatch batch) {
        return batchService.run(batch, false, "tester");
    }

    /** `site.upsert` with `data.reindex`, which is the shape SH413 landed as (see the class note). */
    private static ShAgentBatch.Op reindex(String site, String extra) {
        String data = "{\"reindex\":true" + (extra == null ? "" : "," + extra) + "}";
        return new ShAgentBatch.Op(ShAgentBatchService.SITE_UPSERT, "site:" + site,
                null, null, null, json(data), null, null, null, null);
    }

    private static JsonNode json(String raw) {
        try {
            return new ObjectMapper().readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("SH413: a dry run says how many it would index, and indexes nothing")
    void aDryRunCounts() {
        ShAgentChangeSet plan = batchService.run(
                new ShAgentBatch(true, List.of(reindex(siteName, null))), false, "tester");

        assertThat(plan.results()).singleElement().satisfies(change -> {
            assertThat(change.op()).isEqualTo("site.upsert");
            assertThat(change.detail())
                    .as("""
                            a plan that perturbs the instance is not a plan, and for a backfill \
                            that means counting without sending — an operator deciding whether to \
                            run this needs the number first (SH413)""")
                    .contains("would index " + PUBLISHED);
        });
    }

    @Test
    @DisplayName("SH413: only PUBLISHED rows are counted — a draft is not for readers")
    void draftsAreNotIndexed() {
        ShAgentChangeSet plan = batchService.run(
                new ShAgentBatch(true, List.of(reindex(siteName, null))), false, "tester");

        // PUBLISHED and not PUBLISHED+1: the fixture holds a draft, and the index serves readers.
        assertThat(plan.results().get(0).detail())
                .contains("would index " + PUBLISHED + " published post(s) of " + PUBLISHED);
    }

    @Test
    @DisplayName("SH413: a page is bounded, and the report says how many are left and where to resume")
    void thePageIsBoundedAndSaysWhatIsLeft() {
        // The bound is the whole point. SH84's sender is single-threaded and already reports a
        // queue-full incident when jobs arrive in bulk, so ten thousand posts becoming ten thousand
        // queued jobs is not a slow backfill — it is an outage of the publish path.
        ShAgentChangeSet applied = run(new ShAgentBatch(false,
                List.of(reindex(siteName, "\"limit\":3"))));

        assertThat(applied.results()).singleElement().satisfies(change -> {
            assertThat(change.detail())
                    .as("one call does one page of the size asked for, never the whole site")
                    .contains("of 3 published post(s)");
            assertThat(change.detail())
                    .as("""
                            and says how far it got — a silent truncation reads as "indexed \
                            everything", which is the failure this op exists to end (P9)""")
                    .contains(PUBLISHED - 3 + " left: call again with from=3");
        });
    }

    @Test
    @DisplayName("SH413: a send that does not land is reported as not accepted, never as indexed")
    void aFailedSendIsNotCountedAsIndexed() {
        // Turing is enabled and pointed at a dead port, so every send genuinely fails. The report
        // must say so: an operator who reads "indexed 7" and has an empty index has been told the
        // op works by the op itself, which is worse than no report at all.
        ShAgentChangeSet applied = run(new ShAgentBatch(false,
                List.of(reindex(siteName, "\"limit\":2"))));

        assertThat(applied.results().get(0).detail())
                .contains("indexed 0 of 2 published post(s)")
                .contains("2 not accepted");
    }

    @Test
    @DisplayName("SH413: an offset past the end is a noop that names the total")
    void anOffsetPastTheEndIsANoop() {
        ShAgentChangeSet applied = run(new ShAgentBatch(false,
                List.of(reindex(siteName, "\"from\":500,\"limit\":10"))));

        assertThat(applied.results().get(0).action()).isEqualTo("noop");
        assertThat(applied.results().get(0).detail()).contains("of " + PUBLISHED);
    }

    private void post(ShSite site, ShFolder folder, ShPostType type, String title, String furl,
                      ShPostState state, Instant when) {
        ShPostUnified post = new ShPostUnified();
        post.setId(UUID.randomUUID().toString());
        post.setState(state);
        post.setShSite(site);
        post.setShFolder(folder);
        post.setShPostType(type);
        post.setTitle(title);
        post.setFurl(furl);
        post.setData("{\"TITLE\":\"" + title + "\"}");
        post.setDate(when);
        post.setOwner("admin");
        entityManager.persist(post);
    }
}
