/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Tunables for the Content Delivery API edge behaviour (SH3): HTTP caching
 * headers and the per-token rate limiter. Bound from {@code shio.cda.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Getter
@Setter
@Component
@ConfigurationProperties("shio.cda")
public class ShCdaProperties {

    private Cache cache = new Cache();
    private RateLimit rateLimit = new RateLimit();
    private Preview preview = new Preview();

    @Getter
    @Setter
    public static class Cache {
        /**
         * {@code Cache-Control: public, max-age=N} on CDA reads. {@code 0} emits
         * {@code no-cache} (the response still revalidates cheaply via the ETag).
         */
        private long maxAgeSeconds = 60;
    }

    @Getter
    @Setter
    public static class RateLimit {
        /** Master switch for the per-token limiter. */
        private boolean enabled = true;
        /** Bucket size — the maximum burst a single token may spend at once. */
        private long capacity = 120;
        /** Tokens replenished every {@link #refillPeriodSeconds}. */
        private long refillTokens = 120;
        /** Refill cadence in seconds. */
        private long refillPeriodSeconds = 60;
    }

    /**
     * Short-lived preview tokens (SH13): the console mints an expiring PREVIEW-env
     * token for a single post and hands the editor a ready-to-open preview URL at
     * the site's front-end.
     */
    @Getter
    @Setter
    public static class Preview {
        /** Master switch for minting preview tokens (the mint endpoint 404s when off). */
        private boolean enabled = true;
        /** Default lifetime of a minted preview token, in seconds. */
        private long ttlSeconds = 900;
        /** Hard ceiling on a requested ttl, in seconds. */
        private long maxTtlSeconds = 3600;
        /** Front-end Draft-Mode route the preview URL points at (SH9 convention). */
        private String path = "/api/preview";
        /** Query parameter carrying the minted token to the front-end. */
        private String tokenParam = "token";
        /** Query parameter carrying the target page URL to the front-end. */
        private String urlParam = "url";
    }
}
