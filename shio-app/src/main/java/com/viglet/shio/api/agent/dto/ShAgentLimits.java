/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

/**
 * The instance's operating limits, as declared to an agent (SH76).
 *
 * <p>Published so a caller can pace itself instead of discovering a ceiling by
 * being rejected: a client that knows the bucket is 120/min does not need to be
 * taught by a {@code 429}.</p>
 *
 * @param rateLimitEnabled       whether per-token rate limiting is active
 * @param rateLimitCapacity      burst capacity, in requests
 * @param rateLimitRefillTokens  requests replenished per period
 * @param rateLimitPeriodSeconds the replenish period
 * @param defaultPageSize        the {@code size} used when a listing omits it
 * @param cacheMaxAgeSeconds     the {@code Cache-Control: max-age} on CDA reads
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentLimits(
        boolean rateLimitEnabled,
        long rateLimitCapacity,
        long rateLimitRefillTokens,
        long rateLimitPeriodSeconds,
        int defaultPageSize,
        long cacheMaxAgeSeconds) {
}
