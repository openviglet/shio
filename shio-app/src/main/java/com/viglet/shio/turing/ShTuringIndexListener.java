/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.tenant.ShTenantHandoff;
import com.viglet.shio.webhook.ShPostLifecycleEvent;
import com.viglet.turing.client.sn.job.TurSNJobItem;

import lombok.extern.slf4j.Slf4j;

/**
 * Keeps Turing's index in step with what Shio publishes (SH84).
 *
 * <h2>The same seam the webhook uses, for the same reason</h2>
 *
 * <p>An {@code AFTER_COMMIT} listener on {@link ShPostLifecycleEvent}, exactly like
 * {@code ShWebhookDispatcher}: a rolled-back publish must not reach the index, and a document
 * indexed from an uncommitted transaction would be a search result for a page that does not
 * exist. Composing on the existing event rather than adding a hook inside the post writer is
 * P10 one level down — the writer already announces what it did, and a second announcement is a
 * second thing to keep in sync.</p>
 *
 * <h2>What is read on which thread, and why it matters</h2>
 *
 * <p>Everything that needs the database or the tenant — the published row, the site's name — is
 * read <b>here</b>, on the committing thread, where {@code ShTenantContext} is still set. Only
 * the HTTP call is handed to the executor. That ordering is not a preference: an async task that
 * queried would run with no tenant, and Hibernate's {@code @TenantId} would either fail or, worse,
 * answer with another tenant's row.</p>
 *
 * <p>The executor is <b>single-threaded on purpose</b> (see {@link ShTuringConfig}): publish then
 * unpublish of the same post must arrive at Turing in that order, and two threads racing them
 * would leave a de-published page searchable with no error anywhere.</p>
 *
 * <h2>Failures are reported, not logged</h2>
 *
 * <p>An index push that failed means the page is <em>published but not findable</em> — the same
 * class of half-done state SH12's incident recording exists for, and invisible in exactly the same
 * way. It lands as an {@link ShAgentIncidentLog.Kind#INDEX} incident, so
 * {@code GET /api/v2/agent/diagnostics} and the SH144 change feed report it. A log line is not a
 * surface an agent has (SH103).</p>
 *
 * <p><b>And since SH912 it is retried first.</b> SH84 recorded the failure and stopped there, so a
 * Turing restart mid-publish — seconds — left a page permanently unfindable until somebody read the
 * feed and re-published by hand. See {@link #sendWithRetries} for the bound and for why the wait
 * happens on the worker.</p>
 *
 * <h2>A restart does not keep pending retries, and that is the design (SH912)</h2>
 *
 * <p>The queue is in memory and the retries are in memory with it, so stopping Shio loses both.
 * Nothing here tries to change that, because persisting them would be a <em>new durability
 * guarantee in the one subsystem that deliberately makes none</em>: the queue is bounded and drops
 * on overflow, the worker is a daemon so an unreachable Turing cannot delay a shutdown, and a
 * dropped change is already reported rather than hidden. A durable retry store would have to
 * survive the same outage it exists for, be drained in order, and be reconciled against posts that
 * changed while it waited — which is a reindex, and a reindex is SH413.</p>
 *
 * <p>What makes that acceptable is that the index is <b>recoverable from content</b>, and the
 * incident says so: the page is still published, the remedy is still to re-publish, and SH413's
 * reindex covers the general case. The retry is here to absorb a restart, not to be a queue.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Component
public class ShTuringIndexListener {

    /** One remedy for every failure below, said once. */
    private static final String FIX =
            "The page is published but not searchable. Check Turing is reachable at "
                    + "`shio.turing.url` and that the SN site exists, then re-publish the post to "
                    + "re-send it.";

    private final ShTuringProperties properties;
    private final ShTuringIndexService indexService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShAgentIncidentLog incidentLog;
    private final Executor executor;

    public ShTuringIndexListener(ShTuringProperties properties,
                                 ShTuringIndexService indexService,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShSiteRepository shSiteRepository,
                                 ShAgentIncidentLog incidentLog,
                                 Executor shTuringIndexExecutor) {
        this.properties = properties;
        this.indexService = indexService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteRepository = shSiteRepository;
        this.incidentLog = incidentLog;
        this.executor = shTuringIndexExecutor;
    }

    /**
     * {@code REQUIRES_NEW}, and it has to be: an {@code AFTER_COMMIT} listener runs with the
     * writer's transaction already closed, so a plain {@code @Transactional} is refused outright by
     * Spring ("must not be annotated with @Transactional unless REQUIRES_NEW or NOT_SUPPORTED") —
     * and without a session the lazy {@code shSite} / {@code shPostType} reads below would throw
     * from a thread nobody is watching. Read-only, because nothing here writes.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onPostLifecycle(ShPostLifecycleEvent event) {
        if (!indexService.active()) {
            return;
        }
        switch (event.type()) {
            case PUBLISH -> onPublished(event);
            case UNPUBLISH -> {
                // The one switch that is a judgement rather than a fact: an unpublish may be a
                // five-minute correction, and an operator can choose to keep search warm through it.
                if (properties.isDeIndexOnUnpublish()) {
                    onRemoved(event);
                }
            }
            case DELETE -> onRemoved(event);
        }
    }

    /**
     * Indexes the {@code PUBLISHED} row, which is the only state a search index may serve.
     *
     * <p>Reading the row rather than trusting the event: the event is a routing snapshot and
     * carries no content, and a document has to carry the title, summary and text a reader will
     * see. If the row is gone by now — published and immediately purged — nothing is sent and
     * nothing is reported, because the correct index state for a page that no longer exists is
     * "absent", which the {@code DELETE} event is already on its way to arrange.</p>
     */
    private void onPublished(ShPostLifecycleEvent event) {
        Optional<ShPostUnified> published =
                shPostUnifiedRepository.findByIdAndState(event.postId(), ShPostState.PUBLISHED);
        if (published.isEmpty()) {
            return;
        }
        // Built here, on the thread that still has the tenant and the session; only the send is
        // deferred. A plain TurSNJobItem crosses the thread boundary, never the entity.
        indexService.indexItem(published.get())
                .ifPresent(item -> submit(item, "index " + event.postId(), event.siteId()));
    }

    private void onRemoved(ShPostLifecycleEvent event) {
        indexService.deIndexItem(event.postId(), siteNameOf(event.siteId()), event.locale())
                .ifPresent(item -> submit(item, "de-index " + event.postId(), event.siteId()));
    }

    /** The site's name, read now because the async task must not query. */
    private String siteNameOf(String siteId) {
        return siteId == null ? null
                : shSiteRepository.findById(siteId).map(site -> site.getName()).orElse(null);
    }

    /**
     * @param siteId SH204 — carried alongside {@code item} for the same reason {@code what} is:
     *               these two methods run on the executor, where there is no session and no event,
     *               so anything an incident needs has to be a value that crossed the boundary with
     *               it. It is a plain String taken from the event on the committing thread, so
     *               nothing here queries and nothing lazily initialises
     */
    private void submit(TurSNJobItem item, String what, String siteId) {
        try {
            // SH612 — and the tenant is the third such value, for exactly the reason the two
            // above are: the executor thread never had one, so the incident `run` may record was
            // being written under the default discriminator and filed away from the operator
            // whose site it belongs to. Captured here, on the thread that still has it.
            executor.execute(ShTenantHandoff.capture(() -> sendWithRetries(item, what, siteId)));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The queue is full, which means Turing has been unreachable long enough for publishes
            // to pile up. Dropping is the right call — the alternatives are blocking the editor or
            // growing without bound — and the operator is told rather than left to notice.
            incidentLog.record(ShAgentIncidentLog.Kind.INDEX, what,
                    "the indexing queue is full, so this change was not sent", FIX,
                    ShAgentIncidentFrames.of(e), siteId);
        }
    }

    /**
     * Sends, and keeps sending until it works or the bound is spent (SH912).
     *
     * <h2>Why the retry sleeps here rather than re-queueing</h2>
     *
     * <p>Re-queueing a failed push at the tail would break the one property this whole subsystem is
     * built around: a publish followed by an unpublish must reach Turing <em>in that order</em>, and
     * a retry that goes to the back of the queue lets the de-index overtake the index and leaves a
     * withdrawn page searchable. Waiting on the single worker keeps the ordering by construction,
     * which is the same reason {@link ShTuringConfig} gives this executor one thread.</p>
     *
     * <p>It costs nothing a publish can feel. The committing thread has already returned by the time
     * this runs; what a long outage does is fill the bounded queue, and that case is already decided
     * and already reported — the rejection in {@code submit} names what was dropped. So a Turing
     * that is down for the whole window still never blocks or slows a publish.</p>
     *
     * <h2>One incident, at the end</h2>
     *
     * <p>Nothing is recorded until the attempts are spent. That is what makes a succeeded retry
     * silent — there is no stale incident left in the diagnostics window to contradict a working
     * index — and it is also why an exhausted push leaves <b>one</b> incident rather than one per
     * attempt. Recording per attempt would coalesce into a single {@code Incident} with a count of
     * three, which reads as three failed publishes rather than one that was retried twice.</p>
     */
    void sendWithRetries(TurSNJobItem item, String what, String siteId) {
        int attempts = Math.max(1, properties.getMaxAttempts());
        String refusal = null;
        RuntimeException error = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                if (indexService.send(item, what)) {
                    return;
                }
                refusal = "Turing did not accept the job";
                error = null;
            } catch (RuntimeException e) {
                log.warn("Turing indexing errored for {} (attempt {}/{}): {}",
                        what, attempt, attempts, e.getMessage());
                refusal = "errored: " + e.getMessage();
                error = e;
            }
            if (attempt < attempts && !pause(backoffBefore(attempt + 1, properties.getRetryBackoff()))) {
                // Interrupted: the JVM is going down, and a shutdown is not a diagnosis. Report
                // what the last attempt actually said rather than inventing an outcome for the
                // attempts that never ran.
                break;
            }
        }

        // No frame when Turing merely refused: the SDK completed and nothing of ours threw, so
        // there is no stack to point at. SH190's rule — absent beats empty.
        incidentLog.record(ShAgentIncidentLog.Kind.INDEX, what,
                attempts == 1 ? refusal : refusal + " (" + attempts + " attempts)",
                FIX, error == null ? null : ShAgentIncidentFrames.of(error), siteId);
    }

    /**
     * The wait before {@code attempt}, doubling from the configured base — pure, so the schedule is
     * assertable without a clock or a sleeping test.
     */
    static Duration backoffBefore(int attempt, Duration base) {
        if (base == null || base.isZero() || base.isNegative() || attempt < 2) {
            return Duration.ZERO;
        }
        return base.multipliedBy(1L << (attempt - 2));
    }

    /** @return false when interrupted, which is the signal to stop retrying rather than to log */
    private static boolean pause(Duration wait) {
        if (wait.isZero()) {
            return true;
        }
        try {
            Thread.sleep(wait.toMillis());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
