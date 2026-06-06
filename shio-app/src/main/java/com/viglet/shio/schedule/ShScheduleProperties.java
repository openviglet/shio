/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.schedule;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the scheduled publish/unpublish sweep (SH15). Mirrors
 * {@code ShWebhookProperties} — bound to {@code shio.schedule.*}, defaults shown.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.schedule")
public class ShScheduleProperties {

    /** Master switch for the scheduled publish/unpublish sweep. */
    private boolean enabled = true;

    /**
     * Delay between sweeps. Also wired into the {@code @Scheduled} fixed-delay via
     * the {@code shio.schedule.sweep-interval-millis} placeholder, so a change here
     * must match the property used at startup.
     */
    private long sweepIntervalMillis = 60000;
}
