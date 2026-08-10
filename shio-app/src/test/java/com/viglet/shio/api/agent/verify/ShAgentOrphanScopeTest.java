/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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

import com.viglet.shio.api.agent.verify.dto.ShAgentReport;
import com.viglet.shio.api.agent.verify.dto.ShAgentReport.Finding;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * A post belonging to no site is not the problem of a site that asked (SH635).
 *
 * <h2>The defect, and the shape it arrived in</h2>
 *
 * <p>{@code readable(...)} appended <b>every site-less post in the instance</b> to every scope,
 * including a run that named a site. So a caller asking about their own site was handed an
 * {@code error} about a post at an address in no site of theirs, carrying a {@code fix} they cannot
 * carry out — the post is not theirs to move. It is the rule {@code folders(...)} states one method
 * away and has always applied: <i>an orphan belongs to no site, so no scope can name it</i>. Posts
 * simply never got it.</p>
 *
 * <p>It reached this repository as a <b>flake</b>. {@code ShBlueprintApplyTest} asserts its
 * blueprint site verifies clean, and whether it did depended on whether some other class sharing
 * the context had left a site-less row behind — which depends on suite order. Two intermittent
 * reds, then a deterministic one after SH613 added classes and reordered the run; none of the three
 * looked like a scoping bug, and the second and third were only readable because SH635's earlier
 * half made the capture permanent rather than something to add after the next occurrence.</p>
 *
 * <h2>Its own database, and that is the point rather than a precaution</h2>
 *
 * <p>The second assertion is an <b>instance-wide</b> read — {@code verify(null, …)} — which is the
 * only way an orphan is visible at all, and exactly what {@code ShBenchmarkIsolationLintTest}
 * refuses on the shared context (SH315). That lint caught this test on its first full run, in the
 * class it was first written into, which is a better outcome than the assertion it was guarding:
 * the fix could otherwise have been "never report orphans", and nothing here would have noticed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:shio-sh635;DB_CLOSE_DELAY=-1;MODE=LEGACY;"
                + "DATABASE_TO_UPPER=false;CASE_INSENSITIVE_IDENTIFIERS=true")
@TestInstance(Lifecycle.PER_CLASS)
class ShAgentOrphanScopeTest {

    @Autowired
    private ShAgentVerifyService shAgentVerifyService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private String siteName;
    private String orphanId;

    @BeforeAll
    void setup() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        siteName = "sh635-site-" + UUID.randomUUID();
        orphanId = UUID.randomUUID().toString();
        tx.executeWithoutResult(status -> {
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

            // No site and no folder: the SH128 defect's signature, and the one row shape nothing
            // else in the service can see — which is why `findWithoutSite` has to ask for it by
            // name, and why it must not then be handed to a caller who asked about something else.
            ShPostUnified orphan = new ShPostUnified();
            orphan.setId(orphanId);
            orphan.setState(ShPostState.DRAFT);
            orphan.setTitle("An orphan nobody owns");
            orphan.setData("{}");
            orphan.setDate(Instant.parse("2026-01-01T00:00:00Z"));
            orphan.setOwner("tester");
            entityManager.persist(orphan);
        });
    }

    @Test
    @DisplayName("SH635: a site-scoped run reports no post that belongs to no site")
    void aScopedRunDoesNotReportAnOrphan() {
        assertThat(orphans(shAgentVerifyService.verify(siteName, "content", 500)))
                .as("""
                        A run that named a site is asking about that site. Handing it an error \
                        about a post in no site is a finding the caller cannot act on and did not \
                        ask for — and this is the rule `folders(...)` already applies one method \
                        away, which makes it an omission rather than a judgement (SH635).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH635: the full pass is still the run that has to see it")
    void theFullPassStillReportsIt() {
        assertThat(orphans(shAgentVerifyService.verify(null, "content", 500)))
                .as("""
                        Nothing stops being reported, and this is the half that keeps the fix \
                        honest: "never report orphans" would satisfy the assertion above and lose \
                        the only view of a row the CDA cannot deliver and no address can \
                        reach (SH635).""")
                .contains("id:" + orphanId);
    }

    private static List<String> orphans(ShAgentReport report) {
        return report.findings().stream()
                .filter(finding -> "orphan-post".equals(finding.rule()))
                .map(Finding::address).toList();
    }
}
