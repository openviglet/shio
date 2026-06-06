/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.viglet.shio.api.cda.ShCdaRateLimiter.Decision;

/**
 * Deterministic coverage of the SH3 token-bucket limiter using an injected
 * clock — no wall-clock waits.
 *
 * @author Alexandre Oliveira
 */
class ShCdaRateLimiterTest {

    private static final long ONE_SECOND = 1_000_000_000L;

    private ShCdaProperties props(long capacity, long refillTokens, long refillPeriodSeconds) {
        ShCdaProperties p = new ShCdaProperties();
        p.getRateLimit().setCapacity(capacity);
        p.getRateLimit().setRefillTokens(refillTokens);
        p.getRateLimit().setRefillPeriodSeconds(refillPeriodSeconds);
        return p;
    }

    @Test
    void allowsUpToCapacityThenDenies() {
        AtomicLong clock = new AtomicLong(0);
        ShCdaRateLimiter limiter = new ShCdaRateLimiter(props(3, 3, 60), clock::get);

        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();

        Decision denied = limiter.tryConsume("k", clock.get());
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void refillsAfterPeriod() {
        AtomicLong clock = new AtomicLong(0);
        // 2 tokens / 60s ≈ refill 1 token every 30s.
        ShCdaRateLimiter limiter = new ShCdaRateLimiter(props(2, 2, 60), clock::get);

        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isFalse();

        // Advance a full refill period → bucket back to capacity.
        clock.set(60 * ONE_SECOND);
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("k", clock.get()).allowed()).isFalse();
    }

    @Test
    void perKeyBudgetsAreIndependent() {
        AtomicLong clock = new AtomicLong(0);
        ShCdaRateLimiter limiter = new ShCdaRateLimiter(props(1, 1, 60), clock::get);

        assertThat(limiter.tryConsume("a", clock.get()).allowed()).isTrue();
        assertThat(limiter.tryConsume("a", clock.get()).allowed()).isFalse();
        // A different token has its own bucket.
        assertThat(limiter.tryConsume("b", clock.get()).allowed()).isTrue();
    }

    @Test
    void disabledOrZeroCapacityAlwaysAllows() {
        AtomicLong clock = new AtomicLong(0);

        ShCdaProperties disabled = props(1, 1, 60);
        disabled.getRateLimit().setEnabled(false);
        ShCdaRateLimiter off = new ShCdaRateLimiter(disabled, clock::get);
        for (int i = 0; i < 10; i++) {
            assertThat(off.tryConsume("k", clock.get()).allowed()).isTrue();
        }

        ShCdaRateLimiter zero = new ShCdaRateLimiter(props(0, 0, 60), clock::get);
        for (int i = 0; i < 10; i++) {
            assertThat(zero.tryConsume("k", clock.get()).allowed()).isTrue();
        }
    }
}
