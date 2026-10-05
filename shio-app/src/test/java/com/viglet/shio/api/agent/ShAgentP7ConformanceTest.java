/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import static com.viglet.shio.lint.ShGuardCensus.no;
import static com.viglet.shio.lint.ShGuardCensus.yes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.shio.lint.ShControllerScan;
import com.viglet.shio.lint.ShGuardCensus;
import com.viglet.shio.lint.ShGuardCensus.Axis;
import com.viglet.shio.lint.ShP1Inventory;

/**
 * SH982 — <b>never surprise the curator</b>, asked of every path that writes.
 *
 * <h2>The law with the most citations and no census</h2>
 *
 * <p>P1 has two instruments and P2 has one. P7 is the law this round leans on hardest — a release
 * needs a confirm token, the console publish gains a dry run, bulk verbs report per item,
 * attribution becomes visible — and each of those tasks asserts <em>its own</em> fix. None asks
 * SH338's question first: which other mutating paths have none of these? A law argued task by task
 * is a law nobody sweeps, and the next console write arrives with none of the four.</p>
 *
 * <h2>The four axes</h2>
 *
 * <p><b>A dry run</b> — can a caller see the plan before it happens. <b>A confirm token</b> — does a
 * destructive branch require one taken from its own plan (SH108), which is what makes the deletion
 * something a person was shown. <b>An audit row</b> — does the write leave a trail carrying the
 * actor, which {@code ShContentAuditService} stamps with the agent session too (SH106). <b>A review
 * entry</b> — does an agent's write become something a curator can approve or revert, which
 * {@code ShReviewService} reads off exactly those audit rows.</p>
 *
 * <p>Each axis is <b>falsifiable in both directions</b>: a guard that is present names the file and
 * the token that prove it, and an absence names the same pair and asserts the token is <em>not</em>
 * there. So a path that gains a dry run fails this test until the row says so, which is the half
 * SH142 keeps having to add to a held list.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAgentP7ConformanceTest {

    // --- why a guard is absent. Families, so twenty-eight rows are not twenty-eight essays ------

    private static final String PLAN_IS_THE_CALL =
            "a single-object write whose plan is the call itself: there is one row, the caller "
            + "already holds what it is sending, and a dry run would be a second shape for the same "
            + "request. The agent surface has one because a batch is many writes at once";
    private static final String NOT_DESTRUCTIVE =
            "nothing here destroys content: the confirm token exists for a delete that a person was "
            + "shown (SH108), and a write that cannot remove anything has nothing to show";
    private static final String DELETE_IS_THE_CONSOLE_GESTURE =
            "a delete here is one object a curator selected in front of them, which is the "
            + "confirmation. The token exists for a batch, where the deletions are a list nobody "
            + "typed";
    private static final String NOT_CONTENT =
            "not content: the audit trail is over posts, folders and sites, and the review queue "
            + "reads exactly those rows. An identity or configuration change is an operator act "
            + "with its own record, and putting it in a curator's review queue would be asking "
            + "them to approve something they cannot judge";
    private static final String AUDITED_ELSEWHERE =
            "the write lands in the content services, which audit it — this controller is the door "
            + "rather than the writer, so the row it leaves is the one they write";
    private static final String NO_WRITE_TO_REVIEW =
            "it changes no content, so there is nothing for a curator to approve or revert";

    private record Guards(Axis dryRun, Axis confirm, Axis audit, Axis review) {
    }

    /**
     * Every mutating controller root, on the four axes.
     *
     * <p>Sorted by path, so a diff reads as an addition or a removal rather than a reshuffle.</p>
     */
    private static Map<String, Guards> census() {
        Map<String, Guards> byPath = new TreeMap<>();

        // The agent surface: the one path with all four, which is what the other rows are read
        // against rather than a compliment to it.
        byPath.put("/api/v2/agent", new Guards(
                yes("ShAgentBatchService.java", "dryRun"),
                yes("ShAgentConfirm.java", "confirm"),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        byPath.put("/mcp", new Guards(
                yes("ShWriteTool.java", "dryRun"),
                yes("ShAgentConfirm.java", "confirm"),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));

        // The console's content writes: audited, reviewable, and deliberately without the batch's
        // two guards — one object at a time, in front of somebody.
        byPath.put("/api/v2/folder", new Guards(
                no("ShFolderAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShFolderAPI.java", "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                yes("ShFolderWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        // SH996 — the one console write whose plan is NOT the call: publishing is the act that
        // changes what visitors see, and what a curator wants before it is the list of URLs it
        // moves, which the request does not carry. The row moved because the guard arrived, which
        // is this census doing the job SH142 gave it.
        // SH1021 moved the second row for the second time, and the reason is the reverse of the
        // one above it: a bulk verb IS the case a confirm token was built for. Deleting one post
        // is a gesture a curator makes on the row in front of them; deleting forty is a plan they
        // read and then carry out, and the token is what binds the second call to the first.
        byPath.put("/api/v2/post-unified", new Guards(
                yes("ShPostUnifiedAPI.java", "dryRun"),
                yes("ShPostUnifiedAPI.java", "confirm"),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        byPath.put("/api/v2/site", new Guards(
                no("ShSiteAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShSiteAPI.java", "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                yes("ShSiteWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        byPath.put("/api/v2/object", new Guards(
                no("ShObjectAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShObjectAPI.java", "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                yes("ShFolderWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        byPath.put("/api/v2/staticfile", new Guards(
                no("ShStaticFileAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShStaticFileAPI.java", "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                yes("ShStaticFileService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        // SH1003 — a note is a remark about content, not content. It leaves no audit row and no
        // review entry because the note IS the record: the row a curator wrote is the durable
        // statement, and putting "somebody commented" in a queue of things to approve or revert
        // would ask a curator to approve their own sentence.
        byPath.put("/api/v2/note", new Guards(
                no("ShNoteAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShNoteAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShNoteAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShNoteAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));
        byPath.put("/api/v2/import", new Guards(
                no("ShExchangeImportAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShExchangeImportAPI.java", "confirm", NOT_DESTRUCTIVE),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));

        // The model, which has a dry run because a type change reaches every post of that type.
        byPath.put("/api/v2/post-type", new Guards(
                yes("ShPostTypeAPI.java", "dryRun"),
                no("ShPostTypeAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShPostTypeAPI.java", "shContentAuditService",
                        "the audit trail is over content; a model change is reported by the dry run "
                        + "it offers, which says what it would do to every post of that type"),
                no("ShReviewService.java", "postTypeEntries", NO_WRITE_TO_REVIEW)));

        byPath.put("/api/v2/marketplace", new Guards(
                yes("ShMarketplaceAPI.java", "dryRun"),
                no("ShMarketplaceAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShMarketplaceAPI.java", "shContentAuditService", AUDITED_ELSEWHERE),
                no("ShMarketplaceAPI.java", "findAgentEntries",
                        "installing a package writes no content by itself; what it then applies "
                        + "goes through the batch, which is reviewed")));
        byPath.put("/api/v2/agent/patterns", new Guards(
                no("ShPatternAPI.java", "dryRun",
                        "SH1108: a push copies one file the caller already holds; there is nothing "
                        + "a plan could show that the file does not"),
                no("ShPatternAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShPatternAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShPatternAPI.java", "findAgentEntries",
                        "a pushed pattern writes no content; placing one is an apply, which is "
                        + "reviewed")));
        byPath.put("/api/v2/tenant", new Guards(
                yes("ShTenantAPI.java", "dryRun"),
                no("ShTenantAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShTenantAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShTenantAPI.java", "findAgentEntries", NOT_CONTENT)));

        // Identity and configuration: an operator's own acts, with none of the four and a reason.
        for (Map.Entry<String, String> entry : Map.of(
                "/api/v2/api-token", "ShApiTokenAPI.java",
                "/api/v2/user", "ShUserAPI.java",
                // SH971 — a curator's own dock preferences; surfaces.json classifies it console-only.
                "/api/v2/user/current/dock", "ShDockPreferencesAPI.java",
                "/api/v2/group", "ShGroupAPI.java",
                "/api/v2/role", "ShRoleAPI.java",
                "/api/v2/provider/auth", "ShAuthProviderAPI.java",
                "/api/v2/provider/exchange", "ShExchangeProviderAPI.java",
                "/api/v2/config/email", "ShEmailConfigAPI.java",
                "/api/v2/site-form", "ShSiteFormAPI.java",
                "/api/v2/widget", "ShWidgetAPI.java").entrySet()) {
            byPath.put(entry.getKey(), new Guards(
                    no(entry.getValue(), "dryRun", PLAN_IS_THE_CALL),
                    no(entry.getValue(), "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                    no(entry.getValue(), "shContentAuditService", NOT_CONTENT),
                    no(entry.getValue(), "findAgentEntries", NOT_CONTENT)));
        }
        // SH1024 — a curator's own saved views, the dock's kind (Map.of holds ten pairs, hence here).
        byPath.put("/api/v2/user/current/views", new Guards(
                no("ShSavedViewAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShSavedViewAPI.java", "confirm", DELETE_IS_THE_CONSOLE_GESTURE),
                no("ShSavedViewAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShSavedViewAPI.java", "findAgentEntries", NOT_CONTENT)));

        // SH1218 — a picture of a site's published home, refused unless it was taken of the home
        // as it is published now. It changes no content and no visitor sees it.
        byPath.put("/api/v2/site/{id}/thumbnail", new Guards(
                no("ShSiteThumbnailAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShSiteThumbnailAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShSiteThumbnailAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShSiteThumbnailAPI.java", "findAgentEntries", NOT_CONTENT)));

        // The rest: surfaces that write something other than content.
        byPath.put("/api/v2/cda", new Guards(
                no("ShCdaAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShCdaAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShCdaAPI.java", "shContentAuditService",
                        "the delivery surface: its writes are a form submission and a token spend, "
                        + "and the submission lands as a draft post that IS audited by the content "
                        + "service that writes it"),
                no("ShCdaAPI.java", "findAgentEntries",
                        "a visitor is not an agent, so a submission has no run to review — SH970 "
                        + "reports its arrival on the change feed instead")));
        byPath.put("/api/v2/cda/form", new Guards(
                no("ShFormSubmissionAPI.java", "dryRun",
                        "the caller is a browser on a public page; a dry run there would be a "
                        + "second public write path for no reader"),
                no("ShFormSubmissionAPI.java", "confirm", NOT_DESTRUCTIVE),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                no("ShFormSubmissionAPI.java", "findAgentEntries",
                        "a visitor is not an agent; the arrival is on the change feed (SH970) and "
                        + "the draft waits in the folder either way")));
        byPath.put("/api/v2/site-webhook", new Guards(
                no("ShSiteWebhookAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShSiteWebhookAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShSiteWebhookAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShSiteWebhookAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));
        byPath.put("/api/v2/preview-token", new Guards(
                no("ShPreviewTokenAPI.java", "dryRun", PLAN_IS_THE_CALL),
                no("ShPreviewTokenAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShPreviewTokenAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShPreviewTokenAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));
        byPath.put("/api/v2/render/post-types", new Guards(
                // The census found this one rather than being told it: `?dryRun=true` reports what
                // a reconcile would seed, which is exactly the guard this axis is about.
                yes("ShRenderPostTypesAPI.java", "dryRun"),
                no("ShRenderPostTypesAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShRenderPostTypesAPI.java", "shContentAuditService",
                        "it seeds the render post-types this instance executes, which is a model "
                        + "act and not a content one"),
                no("ShRenderPostTypesAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));
        byPath.put("/api/v2/review", new Guards(
                no("ShReviewAPI.java", "dryRun",
                        "approving is the decision, not a proposal: a dry run of a verdict would be "
                        + "a verdict"),
                no("ShReviewAPI.java", "confirm",
                        "the queue IS the confirmation — a revert here is a person acting on a list "
                        + "they are looking at"),
                yes("ShPostUnifiedWriteService.java", "shContentAuditService"),
                yes("ShReviewService.java", "findAgentEntries")));
        byPath.put("/api/v2/login", new Guards(
                no("ShLoginAPI.java", "dryRun", NOT_CONTENT),
                no("ShLoginAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShLoginAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShLoginAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));
        byPath.put("/api/v2/setup", new Guards(
                no("ShSetupAPI.java", "dryRun",
                        "the first-run wizard, which runs once on an empty instance and has nothing "
                        + "to surprise anybody with"),
                no("ShSetupAPI.java", "confirm", NOT_DESTRUCTIVE),
                no("ShSetupAPI.java", "shContentAuditService", NOT_CONTENT),
                no("ShSetupAPI.java", "findAgentEntries", NO_WRITE_TO_REVIEW)));

        return byPath;
    }

    @Test
    @DisplayName("SH982: every mutating path is classified on all four P7 axes")
    void everyMutatingPathIsClassified() {
        Set<String> mutating = new TreeSet<>(
                ShControllerScan.of(Set.of("POST", "PUT", "DELETE", "PATCH")).byRoot().keySet());

        assertThat(mutating)
                .as("SH982: the scan found no mutating roots, so every loop below is empty — the "
                        + "failure mode this file's siblings are all written against")
                .hasSizeGreaterThanOrEqualTo(20);
        assertThat(census().keySet())
                .as("""
                        SH982: a path that writes and is not classified here is the next one to \
                        arrive with none of P7's guards and nobody noticing. Classify it: a dry run, \
                        a confirm token, an audit row and a review entry, each present with its \
                        evidence or absent with the reason it does not apply.""")
                .containsExactlyInAnyOrderElementsOf(mutating);
    }

    @Test
    @DisplayName("SH982: every claim names a file and a token, and the source agrees with it")
    void everyClaimIsFalsifiable() {
        List<String> problems = new ArrayList<>();
        census().forEach((path, guards) -> problems.addAll(ShGuardCensus.problems(path, axes(guards))));

        assertThat(problems)
                .as("""
                        SH982: a census is worth what its rows can be wrong about. A guard that is                         present names the file and the token that prove it; an absence names the                         same pair and asserts the token is NOT there, so a path that gains a dry run                         or starts auditing fails here until the row says so.""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH982: the census is the deliverable, and the run prints it")
    void theCensusIsTheDeliverable() {
        Map<String, Guards> census = census();
        Map<String, List<String>> gaps = new LinkedHashMap<>();
        Map<String, Integer> present = new LinkedHashMap<>();
        census.forEach((path, guards) -> axes(guards).forEach((axis, claim) -> {
            if (claim.present()) {
                present.merge(axis, 1, Integer::sum);
            } else {
                gaps.computeIfAbsent(axis, ignored -> new ArrayList<>()).add(path);
            }
        }));

        System.out.printf("%n[SH982] P7 across %d mutating paths — never surprise the curator%n",
                census.size());
        Map<String, Object> counts = new TreeMap<>();
        for (String axis : List.of("dry-run", "confirm-token", "audit-row", "review-entry")) {
            int with = present.getOrDefault(axis, 0);
            System.out.printf("  %-14s %2d with, %2d without%n", axis, with, census.size() - with);
            counts.put(axis, with);
        }
        System.out.println();
        ShP1Inventory.record("ShAgentP7ConformanceTest", counts);

        assertThat(gaps.keySet())
                .as("""
                        the census is the worklist: run before a feature block it says which paths \
                        need which guard, and run after it is what stops the next console write \
                        arriving with none of the four. An axis with no gaps at all would mean the \
                        law is met everywhere, which is a claim worth making deliberately.""")
                .containsExactlyInAnyOrder("dry-run", "confirm-token", "audit-row", "review-entry");
    }

    private static Map<String, Axis> axes(Guards guards) {
        Map<String, Axis> byName = new LinkedHashMap<>();
        byName.put("dry-run", guards.dryRun());
        byName.put("confirm-token", guards.confirm());
        byName.put("audit-row", guards.audit());
        byName.put("review-entry", guards.review());
        return byName;
    }
}
