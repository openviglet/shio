/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.schedule;

import java.util.Date;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final ShPostUnifiedWriteService writeService;
    private final ShScheduleProperties properties;

    public ShPostScheduler(ShPostUnifiedWriteService writeService,
                           ShScheduleProperties properties) {
        this.writeService = writeService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${shio.schedule.sweep-interval-millis:60000}")
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        Date now = new Date();

        for (String id : writeService.findScheduledPublishDue(now)) {
            try {
                writeService.publishScheduled(id);
                log.info("Scheduled publish fired for post {}", id);
            } catch (Exception e) {
                log.warn("Scheduled publish failed for post {}: {}", id, e.getMessage());
            }
        }

        for (String id : writeService.findScheduledUnpublishDue(now)) {
            try {
                writeService.unpublishScheduled(id);
                log.info("Scheduled unpublish fired for post {}", id);
            } catch (Exception e) {
                log.warn("Scheduled unpublish failed for post {}: {}", id, e.getMessage());
            }
        }
    }
}
