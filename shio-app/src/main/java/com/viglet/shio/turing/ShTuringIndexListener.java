/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

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
            executor.execute(() -> run(item, what, siteId));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // The queue is full, which means Turing has been unreachable long enough for publishes
            // to pile up. Dropping is the right call — the alternatives are blocking the editor or
            // growing without bound — and the operator is told rather than left to notice.
            incidentLog.record(ShAgentIncidentLog.Kind.INDEX, what,
                    "the indexing queue is full, so this change was not sent", FIX,
                    ShAgentIncidentFrames.of(e), siteId);
        }
    }

    private void run(TurSNJobItem item, String what, String siteId) {
        try {
            if (!indexService.send(item, what)) {
                // No frame: the SDK completed and Turing refused, so there is no exception of ours
                // and nothing in Shio to point at. SH190's rule — absent beats empty.
                incidentLog.record(ShAgentIncidentLog.Kind.INDEX, what,
                        "Turing did not accept the job", FIX, null, siteId);
            }
        } catch (RuntimeException e) {
            log.warn("Turing indexing errored for {}: {}", what, e.getMessage());
            incidentLog.record(ShAgentIncidentLog.Kind.INDEX, what,
                    "errored: " + e.getMessage(), FIX, ShAgentIncidentFrames.of(e), siteId);
        }
    }
}
