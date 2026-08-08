/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.diagnostics.dto.ShAgentDiagnostics;
import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * SH204 — an incident names its site where one is knowable, and is scoped by it.
 *
 * <h2>What this is the assertion for</h2>
 *
 * <p>SH144 merged incidents into the SH88 feed and shipped a note admitting {@code site=} did not
 * narrow them, because an incident carried no site: a site-scoped read of the feed answered with
 * the whole instance's failures. The note was honest and it described a hole. This closes it, and
 * these are the four properties that decide whether it is closed correctly rather than
 * approximately.</p>
 *
 * <p><b>The fold key is the one that is easy to get wrong</b>, and it is asserted first. Adding a
 * site column without adding the site to the fold key is worse than not attributing at all: one
 * webhook URL that two sites subscribe produces the same {@code kind}, the same {@code subject} and
 * the same message, so the two failures fold into one row that keeps whichever site failed first —
 * and a scoped read then shows one operator a failure that is not theirs while hiding it from the
 * operator whose it is. The row would be confidently wrong, with a count of two.</p>
 *
 * <p><b>The scoping rule is the audit trail's, and it has to be.</b> The two streams are merged
 * under one SH88 cursor, so a row one of them hides and the other shows would make a single
 * {@code since=} mean two different things depending on which stream answered. An unattributed
 * incident therefore reaches an unrestricted read and no scoped one — which falls out of
 * {@code null in (…)} rather than being written as a rule anybody has to remember.</p>
 *
 * <p>Its own database, for {@code ShAgentDurableIncidentTest}'s reason: these assertions count
 * incidents on an instance-wide read, so any other test that recorded one would break them in
 * whatever order the suite happened to run.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-incident-sites;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentIncidentSiteTest {

    private static final Instant T1 = Instant.parse("2026-03-01T10:00:00Z");

    /** The one subject two sites can genuinely share — which is what makes the fold key matter. */
    private static final String SHARED_HOOK = "https://shared.example.com/hook";
    private static final String SAME_FAILURE = "gave up after 3 attempt(s), last status 502";

    @Autowired
    private ShAgentIncidentLog incidentLog;
    @Autowired
    private ShAgentChangesService shAgentChangesService;
    @Autowired
    private ShAgentDiagnosticsService shAgentDiagnosticsService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String otherSiteName;
    private String siteId;
    private String otherSiteId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            siteName = "incident-site-" + UUID.randomUUID().toString().substring(0, 8);
            otherSiteName = "incident-other-" + UUID.randomUUID().toString().substring(0, 8);
            siteId = persistSite(siteName);
            otherSiteId = persistSite(otherSiteName);
        });

        incidentLog.clear();
        // The same failure, the same URL, two sites. Before SH204 these were one row.
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, SHARED_HOOK, SAME_FAILURE, null, null,
                siteId);
        incidentLog.record(ShAgentIncidentLog.Kind.WEBHOOK, SHARED_HOOK, SAME_FAILURE, null, null,
                otherSiteId);
        // And one that belongs to no site at all — the case that must stay unattributed.
        incidentLog.record(ShAgentIncidentLog.Kind.API_ERROR, "POST /api/v2/agent/batch",
                "NullPointerException", null, null, null);
    }

    @Test
    @DisplayName("two sites failing the same way stay two rows, each naming its own site")
    void theSiteIsPartOfTheFoldKey() {
        ShAgentDiagnostics all = shAgentDiagnosticsService.diagnostics(50);

        assertThat(all.incidents())
                .as("""
                        the fold key is (kind, subject, message, site). One webhook URL subscribed \
                        by two sites, failing identically, is TWO problems with two operators. \
                        Folding them on (kind, subject, message) alone keeps whichever site failed \
                        first and increments a count — a row that is confidently wrong, and that a \
                        scoped read then shows to the wrong operator while hiding it from the right \
                        one. A site column that is not in the fold key is worse than no column.""")
                .filteredOn(row -> SHARED_HOOK.equals(row.subject()))
                .hasSize(2)
                .extracting(ShAgentDiagnostics.Incident::site)
                .containsExactlyInAnyOrder(siteName, otherSiteName);
    }

    @Test
    @DisplayName("a site-scoped feed read answers with that site's failures and no others")
    void aScopedReadIsNarrowedByTheSite() {
        ShAgentChanges scoped = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(scoped.incidents())
                .as("""
                        the whole payoff: `shio report --site` used to show one site's changes next \
                        to the whole instance's failures, which is the kind of mismatch a reviewer \
                        notices once and then distrusts.""")
                .isNotNull()
                .hasSize(1)
                .first()
                .satisfies(row -> {
                    assertThat(row.site()).isEqualTo(siteName);
                    assertThat(row.subject()).isEqualTo(SHARED_HOOK);
                });
    }

    @Test
    @DisplayName("an unattributed incident reaches an unrestricted read and no scoped one")
    void anUnattributedIncidentFollowsTheAuditRule() {
        ShAgentChanges unrestricted = shAgentChangesService.changes(null, null, 50, false);

        assertThat(unrestricted.incidents())
                .as("""
                        `unrestricted` means no allow-list applies, and an admin reading their own \
                        instance must not silently lose rows — the rule ShHistoryRepository's \
                        findWindow half already applies to an audit row whose site was not \
                        recorded.""")
                .extracting(ShAgentChanges.Incident::subject)
                .contains("POST /api/v2/agent/batch");

        ShAgentChanges scoped = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(scoped.incidents())
                .as("""
                        and a scoped read never gets it, because an unattributable row cannot be \
                        proven inside the allow-list. This is not written as a clause: `null in \
                        (:siteIds)` is never true, so it falls out of the same query shape the \
                        audit half uses — which is what keeps the two streams answering one cursor \
                        consistently.""")
                .extracting(ShAgentChanges.Incident::subject)
                .doesNotContain("POST /api/v2/agent/batch");
    }

    @Test
    @DisplayName("a scoped read says where the instance-level failures went")
    void theNoteNamesDiagnostics() {
        ShAgentChanges scoped = shAgentChangesService.changes(siteName, null, 50, false);

        assertThat(scoped.notes())
                .as("""
                        an agent's own 5xx is the incident it most wants and the last one it would \
                        think to look elsewhere for, so the note names the surface that still has \
                        it rather than leaving the caller to conclude it was never recorded. The \
                        note SH144 shipped said incidents carry no site and `site=` does not narrow \
                        them; both halves are false now, so keeping it would be a false statement \
                        about a closed hole rather than an honest one about an open one.""")
                .anySatisfy(note -> assertThat(note).contains("/api/v2/agent/diagnostics"));

        assertThat(scoped.notes())
                .noneSatisfy(note -> assertThat(note).contains("carry no site"));
    }

    @Test
    @DisplayName("diagnostics stays instance-wide, and labels each row with its site")
    void diagnosticsIsNotNarrowedButIsLabelled() {
        ShAgentDiagnostics diagnostics = shAgentDiagnosticsService.diagnostics(50);

        assertThat(diagnostics.incidents())
                .as("""
                        the deliberate difference from /agent/changes: this view takes no site \
                        argument, so it is not narrowed by one and the field labels a row rather \
                        than filtering it. The instance-level failure is here, with no site, which \
                        is what makes the feed's note true.""")
                .filteredOn(row -> "api-error".equals(row.kind()))
                .singleElement()
                .satisfies(row -> assertThat(row.site())
                        .as("absent means it belongs to no one site, not that nobody recorded one")
                        .isNull());
    }

    private String persistSite(String name) {
        ShSite site = new ShSite();
        site.setName(name);
        site.setDate(T1);
        site.setOwner("admin");
        entityManager.persist(site);

        ShFolder root = new ShFolder();
        root.setName("root");
        root.setRootFolder((byte) 1);
        root.setShSite(site);
        root.setDate(T1);
        root.setOwner("admin");
        entityManager.persist(root);
        return site.getId();
    }
}
