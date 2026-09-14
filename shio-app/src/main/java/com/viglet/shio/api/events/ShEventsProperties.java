/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the change stream (SH967), bound to {@code shio.events.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.events")
public class ShEventsProperties {

    /**
     * Whether the scheduled pump runs. Off, an open stream receives nothing until something calls
     * {@link ShEventStreamHub#pump()} — which is what the tests do, so a background tick cannot race
     * the assertion it is supposed to be measured by.
     */
    private boolean enabled = true;

    /** Delay between two passes over the open streams, in milliseconds. */
    private long pollMillis = 1000;

    /**
     * How long a stream may go without a byte before a heartbeat comment is sent. Proxies close an
     * idle connection they cannot tell from a dead one, and 15s is under every default we know of.
     */
    private long heartbeatMillis = 15000;

    /**
     * How long one connection lives before the server closes it. The client reconnects with
     * {@code Last-Event-ID}, which is where the session is checked again — a stream that outlived a
     * logout would otherwise keep delivering.
     */
    private long timeoutMillis = 1800000;

    /** Feed entries read per page on one pass; a busier feed takes several pages in one pass. */
    private int pageLimit = 100;

    /** Pages read for one stream on one pass, so one busy reader cannot hold the pump. */
    private int maxPagesPerPass = 5;
}
