/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.dto.ShAgentChangeSet;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The confirm token for a destructive write (SH108).
 *
 * <h2>Why a destructive op takes two calls</h2>
 *
 * <p>Everything else on the write surface is idempotent and recoverable: an upsert that got
 * it wrong is fixed by another upsert. A delete is not, and a {@code prune} is a delete whose
 * blast radius depends on what the caller forgot to mention. So the second step exists to make
 * two different promises at once:</p>
 *
 * <ul>
 *   <li><b>An agent cannot destroy what it did not just render.</b> The token comes from a
 *       {@code dryRun}/{@code plan}, which lists every deletion; a caller that never planned
 *       has nothing to send and is told so.</li>
 *   <li><b>An agent cannot destroy something that changed underneath it.</b> The digest covers
 *       the deletions <em>as computed now</em>. A prune re-derives its deletions from current
 *       state, so a page somebody else added since the plan changes the digest and the apply
 *       stops instead of taking it out.</li>
 * </ul>
 *
 * <h2>What it covers, exactly — and what it does not</h2>
 *
 * <p>The digest is over the deletions <b>the plan actually reports</b> — their addresses, sorted
 * so the same intent always hashes the same (P5). A delete of something that is not there
 * reports {@code noop} and needs no confirming, which is what keeps a document re-runnable
 * (SH82) instead of refusing on the deletes it already made. It says nothing
 * about the non-destructive half of the same batch, on purpose: re-confirming a document
 * because a headline changed would train callers to paste the token back without reading, and
 * a confirmation nobody reads is worse than none. Non-destructive batches never need a token
 * at all.</p>
 *
 * <p>It is <b>not</b> an authorization check. The SH54 ACL and the SH2 site allow-list still
 * decide what this caller may delete; this only decides whether the caller has seen it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentConfirm {

    /** How many addresses a refusal names before it defers to the plan (SH138's rule). */
    private static final int NAMED_IN_ERROR = 20;

    /** Digest length in hex characters: short enough to paste, long enough not to collide. */
    private static final int DIGEST_CHARS = 16;

    private ShAgentConfirm() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Whether this batch could destroy anything — i.e. whether it is worth planning first.
     *
     * <p>Asked of the <em>requested</em> ops, because it only decides whether to compute the
     * plan. What the confirmation covers is the plan (see {@link #ofPlan}).</p>
     */
    public static boolean mayDestroy(List<ShAgentBatch.Op> ops) {
        return ops.stream()
                .filter(op -> op != null && op.op() != null)
                .anyMatch(ShAgentConfirm::needsConfirm);
    }

    /**
     * SH993 — and a release run, which destroys nothing and is the widest thing on the surface.
     *
     * <p>Two steps for the same reason a delete takes them: what the run puts in front of visitors is
     * the whole set, and the caller has to have seen it. A run carrying {@code when} or {@code cancel}
     * only moves a date, so it confirms nothing — the token would be asked for at the moment nothing
     * goes live, and not asked for at midnight when it does.</p>
     */
    private static boolean needsConfirm(ShAgentBatch.Op op) {
        String name = op.op().trim().toLowerCase(Locale.ROOT);
        if (ShAgentBatchService.DESTRUCTIVE_OPS.contains(name)) {
            return true;
        }
        // SH994 — and a rollback, which has the run's blast radius pointed the other way.
        if (ShAgentBatchService.RELEASE_ROLLBACK.equals(name)) {
            return true;
        }
        return ShAgentBatchService.RELEASE_RUN.equals(name)
                && (op.when() == null || op.when().isBlank())
                && !Boolean.TRUE.equals(op.cancel());
    }

    /**
     * What a plan would destroy or put live, in a canonical order.
     *
     * <p>A release row carries its own detail into the token (SH993), which is what makes the token
     * cover the <em>set</em>: the detail counts the posts, so adding one between the plan and the run
     * makes the confirm stale instead of silently launching a page the caller never saw.</p>
     */
    public static List<String> deletions(ShAgentChangeSet plan) {
        if (plan == null || plan.results() == null) {
            return List.of();
        }
        return plan.results().stream()
                .filter(row -> ShAgentChangeSet.Change.DELETE.equals(row.action())
                        || ShAgentBatchService.RELEASE_RUN.equals(row.op())
                        || ShAgentBatchService.RELEASE_ROLLBACK.equals(row.op()))
                .map(row -> ShAgentChangeSet.Change.DELETE.equals(row.action()) ? row.address()
                        : row.address() + " — " + (row.detail() == null ? "" : row.detail()))
                .sorted()
                .toList();
    }

    /**
     * The token for a plan, or {@code null} when it destroys nothing.
     *
     * <p><b>Over the plan, not the request.</b> A {@code post.delete} of something that is not
     * there reports {@code noop} and destroys nothing, so demanding a token for it would be
     * friction with no safety behind it — and worse, it would break SH82's re-runnability: an
     * agent re-sending the same document after a compaction would be refused for deletes that
     * already happened. §XII.3 asks for the <em>plan</em> hash, and this is why.</p>
     *
     * <p>{@code null} rather than a digest of the empty list: a token on a batch that destroys
     * nothing is a token a caller would learn to send always, and the point is that its
     * presence means something.</p>
     */
    public static String ofPlan(ShAgentChangeSet plan) {
        List<String> rows = deletions(plan);
        return rows.isEmpty() ? null : digest(String.join("\n", rows));
    }

    /**
     * Enforces the token on a real run.
     *
     * @param sent     what the caller supplied, possibly {@code null}
     * @param expected the digest of what this run would destroy — never {@code null} here
     * @param plan     the plan, for naming what would go
     */
    public static void require(String sent, String expected, ShAgentChangeSet plan) {
        List<String> rows = deletions(plan);
        if (sent == null || sent.isBlank()) {
            throw new ConfirmRequiredException(expected, rows);
        }
        if (!expected.equalsIgnoreCase(sent.trim())) {
            throw new ConfirmStaleException(expected, rows);
        }
    }

    private static String digest(String canonical) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.substring(0, DIGEST_CHARS);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every JVM; there is no recovery and no fallback that
            // would still be a confirmation.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Names what would be destroyed, bounded — and says when it is a subset.
     *
     * <p>A truncated list of deletions that reads as complete is the SH138 hazard, so the
     * count is always the real one and the message says the plan is where the whole list is.</p>
     */
    private static String naming(List<String> rows) {
        String head = String.join(", ", rows.subList(0, Math.min(rows.size(), NAMED_IN_ERROR)));
        return rows.size() <= NAMED_IN_ERROR
                ? head
                : head + " … and " + (rows.size() - NAMED_IN_ERROR) + " more";
    }

    /** A 428: this run destroys things and the caller has not seen the list. */
    public static class ConfirmRequiredException extends ShTeachingException {

        public ConfirmRequiredException(String expected, List<String> rows) {
            super(ShProblem.of("confirm-required", HttpStatus.PRECONDITION_REQUIRED,
                            "this run changes " + rows.size() + " thing(s) and needs confirming")
                    .withDetail("A delete, and since SH993 a release run, takes two steps (SH108). "
                            + "What this one covers: " + naming(rows))
                    .withFix("Run it again with `dryRun` to see the whole list, then repeat the "
                            + "call with `confirm` set to the value below.")
                    .withAllowed(List.of(expected))
                    .withExample("{\"confirm\":\"" + expected + "\", …}"));
        }
    }

    /** A 409: the caller confirmed a different set of deletions than this run would make. */
    public static class ConfirmStaleException extends ShTeachingException {

        public ConfirmStaleException(String expected, List<String> rows) {
            super(ShProblem.conflict("confirm-stale",
                            "what this run covers changed since you confirmed it")
                    .withDetail("The confirm token covers a different set of " + rows.size()
                            + " change(s) than this run would make. What it covers now: "
                            + naming(rows))
                    .withFix("Re-plan with `dryRun`, read the new list, and confirm that one. "
                            + "The content changed underneath you — that is what this check is for.")
                    .withAllowed(List.of(expected))
                    .withExample("{\"confirm\":\"" + expected + "\", …}"));
        }
    }
}
