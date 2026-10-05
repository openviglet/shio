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
 * SH983 — <b>close the perception loop</b>, asked of every write's answer.
 *
 * <h2>Good instruments, and no census</h2>
 *
 * <p>P9 asks for cheap textual proof instead of asking somebody to look, and the instruments exist:
 * the render digest, the address-scoped verify, {@code include=impact}, {@code shio audit}. Every one
 * of them is something a caller has to <em>request</em>, and nothing enumerates which mutating
 * responses carry proof at all. So a task can notice that one save answers with a toast while the
 * agent path answers with two digests, and the noticing stays inside that task.</p>
 *
 * <h2>The four axes, and the low bar</h2>
 *
 * <p><b>A change set</b> — does the answer say what changed rather than that something did. <b>A
 * digest</b> — does it carry a value the caller can compare with what it held. <b>Findings</b> —
 * does it report what is now wrong, or would be. <b>A route delta</b> — does it say which URLs a
 * visitor can now reach. The bar is deliberately low: the point is not that every write renders a
 * page, it is that a write answering with a bare 200 is a decision somebody wrote down.</p>
 *
 * <p>Evidence is a token in the <b>response type</b> rather than in the controller, which is where
 * this differs from SH982's census: the question is what the caller is handed, and a controller that
 * exposes a digest on a second endpoint has not answered its own write with one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
class ShAgentP9ConformanceTest {

    // --- why an answer carries no proof. Families, so twenty-eight rows are not twenty-eight essays

    private static final String THE_ROW_IS_THE_ANSWER =
            "the answer is the row it wrote, which the console re-renders from — proof that it is "
            + "what the caller sent, and nothing more. The agent's own read carries the digest, one "
            + "call away, which is where a caller that wants to compare goes";
    private static final String ONE_OBJECT_NO_SET =
            "one object, so there is nothing to summarise: the change set exists where a call writes "
            + "many things and the caller cannot see them all";
    private static final String NOT_CONTENT_NO_FINDINGS =
            "verify is over content and its routes; this write touches neither, so a finding list "
            + "would always be empty and would read as a claim that nothing is wrong";
    private static final String NO_ROUTE_MOVED =
            "no visitor-facing URL changes here, so a route delta would report the empty set on "
            + "every call";
    private static final String IDENTITY_OR_CONFIG =
            "identity or configuration: what it changed is what the caller sent, and the proof a "
            + "person needs is the screen they are on";

    private record Proof(Axis changeSet, Axis digest, Axis findings, Axis routes) {
    }

    /** What each mutating path hands back, on the four axes. Sorted by path, so a diff reads. */
    private static Map<String, Proof> census() {
        Map<String, Proof> byPath = new TreeMap<>();

        // The agent surface, which is what the rest are read against: every write answers with what
        // changed, a read carries the digest a conditional write takes, and verify reports both.
        byPath.put("/api/v2/agent", new Proof(
                yes("ShAgentChangeSet.java", "Change"),
                yes("ShAgentDoc.java", "digest"),
                yes("ShAgentCheck.java", "CONTENT"),
                yes("ShAgentCheck.java", "ROUTES")));
        byPath.put("/mcp", new Proof(
                yes("ShWriteTool.java", "changes"),
                yes("ShReadTool.java", "digest"),
                yes("ShAgentCheck.java", "CONTENT"),
                yes("ShAgentCheck.java", "ROUTES")));

        // The console's content writes.
        byPath.put("/api/v2/post-unified", new Proof(
                no("ShConsolePost.java", "changed", ONE_OBJECT_NO_SET),
                no("ShConsolePost.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShConsolePost.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShConsolePost.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/folder", new Proof(
                no("ShFolderAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShFolderAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShFolderAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShFolderAPI.java", "routes",
                        "a folder rename moves no post's URL (SH137), which is the property the "
                        + "move was designed around rather than a gap in what it reports")));
        byPath.put("/api/v2/site", new Proof(
                no("ShSiteAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShSiteAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShSiteAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShSiteAPI.java", "routes", NO_ROUTE_MOVED)));
        // SH1218 — a site's home picture. Its answer names the appearance digest the picture is
        // stored at, which is the comparison the console makes before it captures another one.
        byPath.put("/api/v2/site/{id}/thumbnail", new Proof(
                no("ShSiteThumbnailAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                yes("ShSiteThumbnailService.java", "digest"),
                no("ShSiteThumbnailAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShSiteThumbnailAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/object", new Proof(
                no("ShObjectAPI.java", "ChangeSet",
                        "copy and move answer with the objects they touched, which is the list — "
                        + "what is missing beside it is what each one cost, and that is the agent "
                        + "batch's change set"),
                no("ShObjectAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShObjectAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                // SH1078 — a move answers with the published addresses it retired and created.
                yes("ShMoveOutcome.java", "routes")));
        // The one non-agent write whose answer already carries a digest: SH398 stamped the bytes'
        // sha256 on the item, so a replace tells the caller what it now holds without a second call.
        byPath.put("/api/v2/staticfile", new Proof(
                no("ShStaticFileItem.java", "changed", ONE_OBJECT_NO_SET),
                yes("ShStaticFileItem.java", "sha256"),
                no("ShStaticFileItem.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShStaticFileItem.java", "routes", NO_ROUTE_MOVED)));
        // SH1003 — the answer is the note, which is the whole of what was asked for.
        byPath.put("/api/v2/note", new Proof(
                no("ShNoteAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShNoteAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShNoteAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShNoteAPI.java", "routes", NO_ROUTE_MOVED)));
        // SH1079 — what arrived, by kind, and what the import itself could not do.
        byPath.put("/api/v2/import", new Proof(
                yes("ShImportOutcome.java", "Arrived"),
                no("ShExchangeImportAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                yes("ShImportOutcome.java", "Problem"),
                no("ShExchangeImportAPI.java", "routes", NO_ROUTE_MOVED)));

        // The model, whose proposal IS a finding list — the one non-agent path that answers with one.
        byPath.put("/api/v2/post-type", new Proof(
                no("ShPostTypeDef.java", "changed", ONE_OBJECT_NO_SET),
                no("ShPostTypeDef.java", "digest", THE_ROW_IS_THE_ANSWER),
                yes("ShModelProposal.java", "finding"),
                no("ShPostTypeDef.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/render/post-types", new Proof(
                no("ShRenderPostTypesAPI.java", "ChangeSet",
                        "a reconcile answers with what it seeded, and its dry run is where a caller "
                        + "sees that before it happens"),
                no("ShRenderPostTypesAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShRenderPostTypesAPI.java", "ShAgentCheck", NOT_CONTENT_NO_FINDINGS),
                no("ShRenderPostTypesAPI.java", "routes", NO_ROUTE_MOVED)));

        byPath.put("/api/v2/review", new Proof(
                no("ShReviewOutcome.java", "ChangeSet",
                        "the outcome counts what it approved or reverted, which is the summary; "
                        + "what each row was is the queue the curator was just looking at"),
                no("ShReviewOutcome.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShReviewOutcome.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShReviewOutcome.java", "routes",
                        "a revert restores what was live, so the routes are the ones that were "
                        + "there before the run — unchanged by definition")));
        byPath.put("/api/v2/marketplace", new Proof(
                no("ShMarketplaceAPI.java", "ChangeSet",
                        "installing answers with the package it installed; applying it is a batch, "
                        + "which is where the change set is"),
                no("ShMarketplaceAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShMarketplaceAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShMarketplaceAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/agent/patterns", new Proof(
                no("ShPatternAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                // SH1108 — the proof is the stored file's digest, which `shio patterns list --remote`
                // compares with the one on disk: the textual answer to "did my copy land".
                yes("ShPatternAPI.java", "digest"),
                no("ShPatternAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShPatternAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/cda/form", new Proof(
                no("ShFormSubmissionAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShFormSubmissionAPI.java", "digest",
                        "the caller is a visitor's browser: it is told the submission was received "
                        + "and deliberately nothing else, because a honeypot refusal answers the "
                        + "same way (SH633)"),
                no("ShFormSubmissionAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShFormSubmissionAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/cda", new Proof(
                no("ShCdaAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShCdaAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShCdaAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShCdaAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/site-webhook", new Proof(
                no("ShSiteWebhookAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShSiteWebhookAPI.java", "digest", THE_ROW_IS_THE_ANSWER),
                no("ShSiteWebhookAPI.java", "finding",
                        "whether the subscriber answers is not knowable at write time; the delivery "
                        + "that fails becomes an incident on the feed instead (SH144)"),
                no("ShSiteWebhookAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/preview-token", new Proof(
                no("ShPreviewTokenAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShPreviewTokenAPI.java", "digest",
                        "it answers with the token, which is the only thing it made"),
                no("ShPreviewTokenAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShPreviewTokenAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/tenant", new Proof(
                no("ShTenantAPI.java", "ChangeSet",
                        "provisioning answers with what it seeded, and its dry run shows that first"),
                no("ShTenantAPI.java", "digest", IDENTITY_OR_CONFIG),
                no("ShTenantAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShTenantAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/setup", new Proof(
                no("ShSetupAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShSetupAPI.java", "digest", IDENTITY_OR_CONFIG),
                no("ShSetupAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShSetupAPI.java", "routes", NO_ROUTE_MOVED)));
        byPath.put("/api/v2/login", new Proof(
                no("ShLoginAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShLoginAPI.java", "digest", IDENTITY_OR_CONFIG),
                no("ShLoginAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShLoginAPI.java", "routes", NO_ROUTE_MOVED)));

        // Identity and configuration: the screen the operator is on is the proof.
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
            byPath.put(entry.getKey(), new Proof(
                    no(entry.getValue(), "ChangeSet", ONE_OBJECT_NO_SET),
                    no(entry.getValue(), "digest", IDENTITY_OR_CONFIG),
                    no(entry.getValue(), "finding", NOT_CONTENT_NO_FINDINGS),
                    no(entry.getValue(), "routes", NO_ROUTE_MOVED)));
        }
        // SH1024 — a curator's own saved views, the dock's kind (Map.of holds ten pairs, hence here).
        byPath.put("/api/v2/user/current/views", new Proof(
                no("ShSavedViewAPI.java", "ChangeSet", ONE_OBJECT_NO_SET),
                no("ShSavedViewAPI.java", "digest", IDENTITY_OR_CONFIG),
                no("ShSavedViewAPI.java", "finding", NOT_CONTENT_NO_FINDINGS),
                no("ShSavedViewAPI.java", "routes", NO_ROUTE_MOVED)));

        return byPath;
    }

    @Test
    @DisplayName("SH983: every mutating path says what proof its answer carries")
    void everyMutatingPathIsClassified() {
        Set<String> mutating = new TreeSet<>(
                ShControllerScan.of(Set.of("POST", "PUT", "DELETE", "PATCH")).byRoot().keySet());

        assertThat(mutating)
                .as("SH983: the scan found no mutating roots, so every loop below is empty")
                .hasSizeGreaterThanOrEqualTo(20);
        assertThat(census().keySet())
                .as("""
                        SH983: a write that answers with a bare 200 is either a decision or an \
                        omission, and until this census the two looked the same. Classify it: a \
                        change set, a digest, findings or a route delta — present with its \
                        evidence, or absent with the reason the caller needs none.""")
                .containsExactlyInAnyOrderElementsOf(mutating);
    }

    @Test
    @DisplayName("SH983: every claim names a file and a token, and the source agrees with it")
    void everyClaimIsFalsifiable() {
        List<String> problems = new ArrayList<>();
        census().forEach((path, proof) -> problems.addAll(ShGuardCensus.problems(path, axes(proof))));

        assertThat(problems)
                .as("""
                        SH983: the evidence is a token in the response type, so a write that starts \
                        answering with a digest fails here until its row says so — which is the \
                        half that keeps a held list from rotting (SH142).""")
                .isEmpty();
    }

    @Test
    @DisplayName("SH983: the census is the deliverable, and the run prints it")
    void theCensusIsTheDeliverable() {
        Map<String, Proof> census = census();
        Map<String, Integer> present = new LinkedHashMap<>();
        census.forEach((path, proof) -> axes(proof).forEach((axis, claim) -> {
            if (claim.present()) {
                present.merge(axis, 1, Integer::sum);
            }
        }));

        System.out.printf("%n[SH983] P9 across %d mutating paths — close the perception loop%n",
                census.size());
        Map<String, Object> counts = new TreeMap<>();
        for (String axis : List.of("change-set", "digest", "findings", "route-delta")) {
            int with = present.getOrDefault(axis, 0);
            System.out.printf("  %-12s %2d answer with it, %2d without%n",
                    axis, with, census.size() - with);
            counts.put(axis, with);
        }
        System.out.println();
        ShP1Inventory.record("ShAgentP9ConformanceTest", counts);

        assertThat(present.keySet())
                .as("""
                        the census is the worklist, and the round that follows is where it is spent: \
                        release runs, review decisions, bulk verbs, asset metadata and rollback each \
                        invent a response shape, and several of them propose a digest delta \
                        independently. Naming the missing ones first is what turns those \
                        improvisations into one contract.""")
                .containsExactlyInAnyOrder("change-set", "digest", "findings", "route-delta");
    }

    private static Map<String, Axis> axes(Proof proof) {
        Map<String, Axis> byName = new LinkedHashMap<>();
        byName.put("change-set", proof.changeSet());
        byName.put("digest", proof.digest());
        byName.put("findings", proof.findings());
        byName.put("route-delta", proof.routes());
        return byName;
    }
}
