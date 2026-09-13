/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link ShAssetSecurityHeadersFilter} on the public byte route, and nowhere else
 * (SH950).
 *
 * <p>The URL pattern is the whole safety of this: {@code /file_source/*} matches the uploaded
 * bytes and cannot match {@code /sites/**}, where a delivered page is {@code text/html} by design.
 * Follows {@code ShCdaCacheConfig}'s shape — a named registration with an explicit order rather
 * than a {@code @Component} filter, which Boot would apply to every request in the application.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShAssetSecurityConfig {

    /**
     * {@code /file_source/*} in servlet terms: a servlet URL pattern's {@code *} is greedy after
     * the prefix, so this covers {@code /file_source/<id>/<name>} and anything deeper.
     */
    private static final String FILE_SOURCE_URL_PATTERN = "/file_source/*";

    @Bean
    FilterRegistrationBean<ShAssetSecurityHeadersFilter> shAssetSecurityHeadersFilter() {
        FilterRegistrationBean<ShAssetSecurityHeadersFilter> registration =
                new FilterRegistrationBean<>(new ShAssetSecurityHeadersFilter());
        registration.addUrlPatterns(FILE_SOURCE_URL_PATTERN);
        registration.setName("shAssetSecurityHeadersFilter");
        // Before the resource handler writes anything, and after security has decided.
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 30);
        return registration;
    }
}
