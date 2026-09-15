/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.schedule;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.release.ShReleaseRunner;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.release.ShRelease;
import com.viglet.shio.persistence.model.tenant.ShTenant;
import com.viglet.shio.persistence.model.tenant.ShTenantStatus;
import com.viglet.shio.persistence.repository.tenant.ShTenantRepository;
import com.viglet.shio.property.ShAppProperties;
import com.viglet.shio.tenant.ShTenantHandoff;

import lombok.extern.slf4j.Slf4j;

/**
 * Scheduled publish/unpublish sweep (SH15). On a fixed delay it asks the unified
 * write service for posts whose {@code publishScheduledDate} /
 * {@code unpublishScheduledDate} is now due and fires the matching transition.
 *
 * <p>Each transition is delegated through the write-service proxy, so it runs in
 * its <b>own</b> transaction: one failing post is logged and skipped without
 * rolling back the rest, and each commit triggers its own SH12 outbound webhook
 * exactly as a manual publish/unpublish would.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Slf4j
@Component
public class ShPostScheduler {

    /** The start of a failed publish's incident message, which the retry is read from (SH969). */
    static final String PUBLISH_FAILED = "scheduled publish failed: ";

    /** The start of a failed unpublish's incident message (SH969). */
    static final String UNPUBLISH_FAILED = "scheduled unpublish failed: ";

    /** The start of a failed release run's incident message (SH993). */
    static final String RELEASE_FAILED = "scheduled release run failed: ";

    /** One remedy for both halves of the sweep: the post's state is not what was asked. */
    /** SH993 — what a curator does about a release that did not run. */
    private static final String RELEASE_FIX =
            "The release is still SCHEDULED and its instant has passed, so the next sweep tries "
                    + "again. Read what it holds (GET /api/v2/agent/find?site=<site>&release=<name>) "
                    + "and run it explicitly with release.run once the post it failed on is fixed.";

    private static final String SCHEDULE_FIX =
            "The post is still in its previous state. Read it (GET /api/v2/agent/read) and "
                    + "publish or unpublish it explicitly, then re-schedule if the timing still "
                    + "matters.";

    /** SH993 — the actor a release the sweep fires is recorded as, beside SH15's per-post one. */
    private static final String SCHEDULER = "scheduler";

    private final ShPostUnifiedWriteService writeService;
    private final ShReleaseService shReleaseService;
    private final ShReleaseRunner shReleaseRunner;
    private final ShScheduleProperties properties;
    private final ShAgentIncidentLog incidentLog;
    private final ShTenantRepository shTenantRepository;
    private final ShAppProperties shAppProperties;

    /**
     * The batch op that attempts a failed transition again, read from its incident's message, or
     * {@code null} for a message this sweep did not write — a whole tenant's pass failing names no
     * post to retry (SH969).
     *
     * <p>Read from the message because the incident row has no column for the transition, and the two
     * prefixes are written here and nowhere else, so this is the one method that has to know them.</p>
     */
    public static String retryOp(String message) {
        if (message == null) {
            return null;
        }
        if (message.startsWith(PUBLISH_FAILED)) {
            return ShAgentBatchService.POST_PUBLISH;
        }
        return message.startsWith(UNPUBLISH_FAILED) ? ShAgentBatchService.POST_UNPUBLISH : null;
    }

    public ShPostScheduler(ShPostUnifiedWriteService writeService,
                           ShReleaseService shReleaseService,
                           ShReleaseRunner shReleaseRunner,
                           ShScheduleProperties properties,
                           ShAgentIncidentLog incidentLog,
                           ShTenantRepository shTenantRepository,
                           ShAppProperties shAppProperties) {
        this.writeService = writeService;
        this.shReleaseService = shReleaseService;
        this.shReleaseRunner = shReleaseRunner;
        this.properties = properties;
        this.incidentLog = incidentLog;
        this.shTenantRepository = shTenantRepository;
        this.shAppProperties = shAppProperties;
    }

    /**
     * One pass per tenant (SH639).
     *
     * <h2>The sweep that ran as nobody</h2>
     *
     * <p>A timer runs on a thread that never had a tenant bound, and {@code ShPostUnified} carries
     * {@code @TenantId} — so Hibernate appended the default discriminator to the finders and this
     * asked only for {@code shio}'s due posts. A post another tenant scheduled was not published
     * late; it was <b>never found</b>, and the incident log had nothing to say because nothing
     * threw. That is the same absence SH612 found in the two off-thread incident recorders,
     * arriving one layer earlier and with a larger consequence: SH612 misfiled the record of a
     * failure, this one silently declined to do the work.</p>
     *
     * <h2>Iterating, because a timer has no one tenant to carry</h2>
     *
     * <p>SH612's fix was to capture the tenant on the committing thread. There is nothing to
     * capture here, so the sweep <em>iterates</em> — and it reuses {@code ShTenantHandoff.runAs}
     * rather than setting the context itself, because the pool thread is reused and leaving a
     * tenant bound would stamp the next tick's work with the last tenant of this one.</p>
     *
     * <p>Three questions the shape had to answer:</p>
     *
     * <ul>
     *   <li><b>A suspended tenant is skipped.</b> Publishing on behalf of an account the platform
     *       has stopped is the one outcome worse than publishing late — the request path already
     *       answers them 403, and a timer that ignored that would be a way around it.</li>
     *   <li><b>A tenant provisioned mid-sweep waits for the next tick.</b> The list is read once,
     *       so the pass is over a consistent set; the next one is sixty seconds away and a post
     *       due now was due before that tenant existed.</li>
     *   <li><b>One tenant's failure does not abandon the rest.</b> Each pass is caught on its own,
     *       for the reason each post already is: a sweep that stopped at the first bad row would
     *       let one tenant's broken content silence every other tenant's schedule.</li>
     * </ul>
     *
     * <p><b>A single-tenant install still runs exactly one pass, with nothing bound</b>, which is
     * what it did before and keeps the default discriminator doing the work. The tenant table is
     * not read at all in that mode, so the common install pays nothing for this.</p>
     */
    @Scheduled(fixedDelayString = "${shio.schedule.sweep-interval-millis:60000}")
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!shAppProperties.isMultiTenant()) {
            sweepCurrentTenant();
            return;
        }
        for (String tenant : sweepable()) {
            try {
                ShTenantHandoff.runAs(tenant, this::sweepCurrentTenant);
            } catch (RuntimeException e) {
                // Not per post — that is handled below. This is the pass itself failing, which
                // means the finder threw, and the only thing worse than one tenant's schedule not
                // firing is every tenant's not firing because one of them is broken.
                log.warn("Scheduled sweep failed for tenant {}: {}", tenant, e.getMessage());
                incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "tenant:" + tenant,
                        "the scheduled sweep failed for this tenant: " + e.getMessage(),
                        SCHEDULE_FIX, ShAgentIncidentFrames.of(e), null);
            }
        }
    }

    /**
     * The tenants a pass runs for, read once so the sweep covers a consistent set.
     *
     * <p>Ordered by name, so two instances reading the same table sweep in the same order (P5) and
     * a log of one run can be diffed against another.</p>
     */
    private List<String> sweepable() {
        return shTenantRepository.findAllByOrderByNameAsc().stream()
                .filter(tenant -> tenant.getStatus() != ShTenantStatus.SUSPENDED)
                .map(ShTenant::getName)
                .toList();
    }

    private void sweepCurrentTenant() {
        Instant now = Instant.now();

        // SH204 — the finders return the site beside the id now. Resolving it here instead would
        // mean a query inside a `catch`, in a job nobody is watching: a lookup that threw would
        // abandon the rest of the sweep, and guarding it would add the one thing
        // ShSilentCatchInventoryTest counts. The finder already had the row loaded.
        for (ShPostUnifiedWriteService.ShScheduledPost due : writeService.findScheduledPublishDue(now)) {
            String id = due.id();
            try {
                writeService.publishScheduled(id);
                log.info("Scheduled publish fired for post {}", id);
            } catch (Exception e) {
                log.warn("Scheduled publish failed for post {}: {}", id, e.getMessage());
                // SH103: nobody is watching at 03:00, and the post is silently not in
                // the state it was told to be in. Recorded so /agent/diagnostics says so.
                // SH269: this is the case where nobody was watching, so the frame is worth most
                // — the message names the symptom and the frame names the writer it threw inside.
                incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "id:" + id,
                        PUBLISH_FAILED + e.getMessage(), SCHEDULE_FIX,
                        ShAgentIncidentFrames.of(e), due.siteId());
            }
        }

        for (ShPostUnifiedWriteService.ShScheduledPost due : writeService.findScheduledUnpublishDue(now)) {
            String id = due.id();
            try {
                writeService.unpublishScheduled(id);
                log.info("Scheduled unpublish fired for post {}", id);
            } catch (Exception e) {
                log.warn("Scheduled unpublish failed for post {}: {}", id, e.getMessage());
                incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "id:" + id,
                        UNPUBLISH_FAILED + e.getMessage(), SCHEDULE_FIX,
                        ShAgentIncidentFrames.of(e), due.siteId());
            }
        }

        // SH993 — a release due now goes live as one transaction, through the same runner the
        // `release.run` op calls. A launch that behaved differently at midnight than on the call
        // would be the worst kind of surprise to debug, so there is one implementation of running.
        for (ShRelease due : shReleaseService.dueRuns(now)) {
            try {
                shReleaseRunner.run(due, SCHEDULER);
                log.info("Scheduled release run fired for {}", due.getName());
            } catch (Exception e) {
                log.warn("Scheduled release run failed for {}: {}", due.getName(), e.getMessage());
                // The release stays SCHEDULED and still due, so the next pass tries again — the same
                // shape SH969 gave a failed post transition, and the incident is what says so while
                // nobody is watching.
                incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "release:" + due.getName(),
                        RELEASE_FAILED + e.getMessage(), RELEASE_FIX,
                        ShAgentIncidentFrames.of(e), due.getSiteId());
            }
        }
    }
}
