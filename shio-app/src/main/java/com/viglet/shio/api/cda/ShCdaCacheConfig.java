/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * HTTP edge-caching wiring for the Content Delivery API (SH3).
 *
 * <p>Two servlet filters scoped to {@code /api/v2/cda/*} (a prefix mapping that
 * also covers nested paths like {@code /api/v2/cda/object/x/list}):</p>
 * <ol>
 *   <li>{@link ShallowEtagHeaderFilter} — body-hash {@code ETag} +
 *       conditional {@code 304} on {@code If-None-Match}. Registered outer so it
 *       buffers the full controller response.</li>
 *   <li>{@link ShCdaCacheControlFilter} — {@code Cache-Control} header.</li>
 * </ol>
 *
 * <p>Both order after the Spring Security chain (order {@code -100}), so token
 * auth + rate limiting (SH2/SH3) decide before any caching work happens — a
 * {@code 401}/{@code 403}/{@code 429} never gets an {@code ETag}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Configuration(proxyBeanMethods = false)
public class ShCdaCacheConfig {

    private static final String CDA_URL_PATTERN = "/api/v2/cda/*";

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> shCdaEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns(CDA_URL_PATTERN);
        registration.setName("shCdaEtagFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
        return registration;
    }

    @Bean
    FilterRegistrationBean<ShCdaCacheControlFilter> shCdaCacheControlFilter(ShCdaProperties properties) {
        FilterRegistrationBean<ShCdaCacheControlFilter> registration =
                new FilterRegistrationBean<>(new ShCdaCacheControlFilter(properties));
        registration.addUrlPatterns(CDA_URL_PATTERN);
        registration.setName("shCdaCacheControlFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
