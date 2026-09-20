/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.console;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * SH1048 — where {@link ShConsoleCacheFilter} is mounted, and why it sits where it does.
 *
 * <p>One prefix mapping over the whole versioned API. The filter excludes the two surfaces that
 * already carry validators and the two endpoints that must not be buffered, so the registration
 * stays a single pattern rather than a list somebody has to keep in step with the controllers —
 * a new console endpoint is covered the day it is written, which is the opposite of how the
 * console came to have no validators at all.</p>
 *
 * <p><b>Ordered after the Spring Security chain</b> (order {@code -100}), following the SH3
 * precedent in {@code ShCdaCacheConfig}: authentication and rate limiting decide before any
 * hashing happens, so a {@code 401}, {@code 403} or {@code 429} never receives an {@code ETag}.
 * A validator on a refusal is a client that caches the refusal.</p>
 *
 * <p>It is registered <em>inside</em> {@code ShEstTokensHeaderFilter} ({@code -30}) on purpose:
 * SH124's cost header must measure a conditional {@code 304} as the zero-byte response it really
 * is, and it can only do that from outside the filter that produced it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShConsoleCacheConfig {

    /** A prefix mapping, so nested paths like {@code /api/v2/object/x/list} are covered too. */
    static final String API_URL_PATTERN = "/api/v2/*";

    /** Inside SH124's cost header ({@code -30}), outside nothing else on this surface. */
    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 20;

    @Bean
    FilterRegistrationBean<ShConsoleCacheFilter> shConsoleCacheFilter() {
        FilterRegistrationBean<ShConsoleCacheFilter> registration =
                new FilterRegistrationBean<>(new ShConsoleCacheFilter());
        registration.addUrlPatterns(API_URL_PATTERN);
        registration.setName("shConsoleCacheFilter");
        registration.setOrder(ORDER);
        return registration;
    }
}
