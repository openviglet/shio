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
        long cacheMaxAgeSeconds,
        /**
         * SH266 — the most pages a write's {@code ?digest=<n>} proof will render.
         *
         * <p>Here because a ceiling the caller cannot see is one it learns by being cut off. SH121's
         * whole argument for this section is that a limit an agent knows in advance shapes the call
         * it makes: an agent that knows the number is 100 asks for what it needs, and one that does
         * not either asks for the default forever or discovers the bound by reading a cut row.</p>
         */
        int maxDigestPages) {
}
// SH723 considered adding the change feed's token ceiling here and did not. `maxDigestPages`
// belongs because it appears in no response; the feed's ceiling appears in *every* one of its
// own — `budget.limit` in JSON, `max-tokens` on the terse header — so a caller learns it on the
// first read rather than by being cut off, and the manifest is paid for on every session start.
// The context pack made the same choice for the same reason: it declares its budget in its own
// payload, not in `limits`.
