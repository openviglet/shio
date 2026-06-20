/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.spring;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a single application-wide {@link Clock} bean.
 *
 * <p>Defaults to {@link Clock#systemUTC()} so production behavior is unchanged;
 * tests may inject a fixed clock for deterministic time-dependent logic.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration
public class ShClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
