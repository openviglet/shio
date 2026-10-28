/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory, per-token token-bucket rate limiter for the Content Delivery API
 * (SH3). One bucket per token id; lazy refill from a monotonic clock.
 *
 * <p><strong>Single-instance.</strong> Buckets live in this JVM's heap, so the
 * budget is enforced per node. A multi-node deployment behind a load balancer
 * gets {@code N ×} the configured budget; a shared bucket (a distributed cache,
 * brought in by its own task since SH1261 removed the unused Hazelcast) is the
 * later hardening step, intentionally out of SH3 scope.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShCdaRateLimiter {

    /** Outcome of a {@link #tryConsume(String)} call. */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }

    private static final Decision UNLIMITED = new Decision(true, Long.MAX_VALUE, 0);

    private final ShCdaProperties properties;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ShCdaRateLimiter(ShCdaProperties properties) {
        this(properties, System::nanoTime);
    }

    ShCdaRateLimiter(ShCdaProperties properties, LongSupplier nanoClock) {
        this.properties = properties;
        this.nanoClock = nanoClock;
    }

    public Decision tryConsume(String key) {
        return tryConsume(key, nanoClock.getAsLong());
    }

    Decision tryConsume(String key, long nowNanos) {
        ShCdaProperties.RateLimit cfg = properties.getRateLimit();
        if (!cfg.isEnabled() || cfg.getCapacity() <= 0
                || cfg.getRefillTokens() <= 0 || cfg.getRefillPeriodSeconds() <= 0) {
            return UNLIMITED;
        }
        double refillPerSecond = (double) cfg.getRefillTokens() / cfg.getRefillPeriodSeconds();
        Bucket bucket = buckets.computeIfAbsent(key,
                k -> new Bucket(cfg.getCapacity(), nowNanos));
        return bucket.tryConsume(cfg.getCapacity(), refillPerSecond, nowNanos);
    }

    /** Drops all buckets — test/maintenance hook. */
    void reset() {
        buckets.clear();
    }

    private static final class Bucket {
        private static final double NANOS_PER_SECOND = 1_000_000_000.0;

        private double tokens;
        private long lastRefillNanos;

        Bucket(long capacity, long nowNanos) {
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
        }

        synchronized Decision tryConsume(long capacity, double refillPerSecond, long nowNanos) {
            refill(capacity, refillPerSecond, nowNanos);
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new Decision(true, (long) Math.floor(tokens), 0);
            }
            double deficit = 1.0 - tokens;
            long retryAfter = (long) Math.max(1, Math.ceil(deficit / refillPerSecond));
            return new Decision(false, 0, retryAfter);
        }

        private void refill(long capacity, double refillPerSecond, long nowNanos) {
            long elapsed = nowNanos - lastRefillNanos;
            if (elapsed <= 0) {
                return;
            }
            double replenished = (elapsed / NANOS_PER_SECOND) * refillPerSecond;
            if (replenished > 0) {
                tokens = Math.min(capacity, tokens + replenished);
                lastRefillNanos = nowNanos;
            }
        }
    }
}
