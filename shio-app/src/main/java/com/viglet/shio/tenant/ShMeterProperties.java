/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.tenant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the API-call meter (SH45), bound to {@code shio.meter.*}. Mirrors
 * {@code ShScheduleProperties} — defaults shown.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.meter")
public class ShMeterProperties {

    /**
     * Master switch for API-call metering.
     *
     * <p><b>On by default, which is a decision.</b> A meter that ships off reads zero, and a zero
     * that nobody can distinguish from "no traffic" is exactly the failure SH751 records against
     * the storage half. The cost of being wrong the other way is one {@code UPDATE} per active
     * (tenant, surface) bucket per flush — bounded by the number of tenants, not by traffic.</p>
     */
    private boolean enabled = true;

    /**
     * Delay between flushes of the in-heap accumulator to the meter table. Also wired into the
     * {@code @Scheduled} fixed-delay via the {@code shio.meter.flush-interval-millis} placeholder,
     * so a change here must match the property used at startup.
     *
     * <p>This is also the bound on what a crash loses: increments accumulated since the last flush
     * are in heap and nowhere else. Stated rather than hidden — a shorter interval buys a smaller
     * loss window with more writes, and neither is the obviously right answer for every install.</p>
     */
    private long flushIntervalMillis = 60000;

    /**
     * Whether a period rollover hands the closed day to the registered
     * {@link ShBillingExporter} beans.
     *
     * <p>Off by default, unlike the meter itself: metering is an internal number and exporting
     * sends it somewhere. P7 — never surprise the curator — makes anything outward-facing opt-in,
     * and with no exporter bean registered this switch changes nothing anyway.</p>
     */
    private boolean exportOnRollover = false;
}
