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

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
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

    /** One remedy for both halves of the sweep: the post's state is not what was asked. */
    private static final String SCHEDULE_FIX =
            "The post is still in its previous state. Read it (GET /api/v2/agent/read) and "
                    + "publish or unpublish it explicitly, then re-schedule if the timing still "
                    + "matters.";

    private final ShPostUnifiedWriteService writeService;
    private final ShScheduleProperties properties;
    private final ShAgentIncidentLog incidentLog;
    private final ShTenantRepository shTenantRepository;
    private final ShAppProperties shAppProperties;

    public ShPostScheduler(ShPostUnifiedWriteService writeService,
                           ShScheduleProperties properties,
                           ShAgentIncidentLog incidentLog,
                           ShTenantRepository shTenantRepository,
                           ShAppProperties shAppProperties) {
        this.writeService = writeService;
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
                        "scheduled publish failed: " + e.getMessage(), SCHEDULE_FIX,
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
                        "scheduled unpublish failed: " + e.getMessage(), SCHEDULE_FIX,
                        ShAgentIncidentFrames.of(e), due.siteId());
            }
        }
    }
}
