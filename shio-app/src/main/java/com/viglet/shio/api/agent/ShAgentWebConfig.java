/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Servlet wiring for the agent surface (Block H).
 *
 * <p>Ordered after the Spring Security chain, following the SH3 precedent in
 * {@code ShCdaCacheConfig}: a {@code 401}/{@code 403}/{@code 429} must not be
 * measured or annotated as if it were a real response.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShAgentWebConfig {

    /** Prefix mapping — also covers nested paths such as {@code /api/v2/agent/context}. */
    private static final String AGENT_URL_PATTERN = "/api/v2/agent/*";

    @Bean
    FilterRegistrationBean<ShAgentTokenHeaderFilter> shAgentTokenHeaderFilter() {
        FilterRegistrationBean<ShAgentTokenHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShAgentTokenHeaderFilter());
        registration.addUrlPatterns(AGENT_URL_PATTERN);
        registration.setName("shAgentTokenHeaderFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
        return registration;
    }
}
