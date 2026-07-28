/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.schedule;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentFrames;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;

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

    public ShPostScheduler(ShPostUnifiedWriteService writeService,
                           ShScheduleProperties properties,
                           ShAgentIncidentLog incidentLog) {
        this.writeService = writeService;
        this.properties = properties;
        this.incidentLog = incidentLog;
    }

    @Scheduled(fixedDelayString = "${shio.schedule.sweep-interval-millis:60000}")
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        Instant now = Instant.now();

        for (String id : writeService.findScheduledPublishDue(now)) {
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
                        ShAgentIncidentFrames.of(e));
            }
        }

        for (String id : writeService.findScheduledUnpublishDue(now)) {
            try {
                writeService.unpublishScheduled(id);
                log.info("Scheduled unpublish fired for post {}", id);
            } catch (Exception e) {
                log.warn("Scheduled unpublish failed for post {}: {}", id, e.getMessage());
                incidentLog.record(ShAgentIncidentLog.Kind.SCHEDULE, "id:" + id,
                        "scheduled unpublish failed: " + e.getMessage(), SCHEDULE_FIX,
                        ShAgentIncidentFrames.of(e));
            }
        }
    }
}
