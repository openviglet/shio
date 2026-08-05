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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.viglet.shio.api.agent.diagnostics.ShAgentErrorRecordingFilter;
import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;

/**
 * Servlet wiring for the agent surface (Block H) and the delivery-path cost
 * instrumentation (SH124).
 *
 * <p>Ordered after the Spring Security chain, following the SH3 precedent in
 * {@code ShCdaCacheConfig}: a {@code 401}/{@code 403}/{@code 429} must not be
 * measured or annotated as if it were a real response.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShAgentWebConfig implements WebMvcConfigurer {

    /**
     * Prefix mappings — each also covers nested paths ({@code /api/v2/agent/context},
     * {@code /api/v2/cda/object/x/list}).
     */
    private static final String AGENT_URL_PATTERN = "/api/v2/agent/*";
    private static final String CDA_URL_PATTERN = "/api/v2/cda/*";
    private static final String API_URL_PATTERN = "/api/*";

    /**
     * One filter, both surfaces. Registered at {@code LOWEST_PRECEDENCE - 30} — that
     * is <em>outside</em> the SH3 ETag filter ({@code -20}), so a conditional
     * {@code 304} is measured as the zero-byte response it really is rather than as
     * the body that was never sent. That distinction is the whole point of SH116:
     * "an unchanged context costs nothing" has to be visible in the number.
     */
    @Bean
    FilterRegistrationBean<ShEstTokensHeaderFilter> shEstTokensHeaderFilter() {
        FilterRegistrationBean<ShEstTokensHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShEstTokensHeaderFilter());
        registration.addUrlPatterns(AGENT_URL_PATTERN, CDA_URL_PATTERN);
        registration.setName("shEstTokensHeaderFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 30);
        return registration;
    }

    /**
     * SH430 — the unknown-parameter refusal, mounted on the agent surface.
     *
     * <p>An interceptor rather than a check in each handler, because a check in each handler is a
     * rule the next endpoint has to remember: this reads the allow-list off the handler's own
     * {@code @RequestParam} declarations, so a mutating endpoint is covered before it is written.
     * It runs after the security chain — a 401 must not be turned into a 400 about a parameter —
     * and only on {@code POST}/{@code PUT}/{@code PATCH}/{@code DELETE}, which is
     * {@link ShAgentQueryParams}'s declared scope.</p>
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ShAgentQueryParams())
                .addPathPatterns(ShAgentQueryParams.PATH_PATTERN);
    }

    /**
     * SH103's incident recorder, over the whole API. Registered at the same precedence
     * as the cost header and for the same reason: inside the security chain a 401 or a
     * 429 would be recorded as an instance fault, and a diagnostics window full of
     * rejected scans is a window that gets ignored.
     */
    @Bean
    FilterRegistrationBean<ShAgentErrorRecordingFilter> shAgentErrorRecordingFilter(
            ShAgentIncidentLog incidentLog) {
        FilterRegistrationBean<ShAgentErrorRecordingFilter> registration =
                new FilterRegistrationBean<>(new ShAgentErrorRecordingFilter(incidentLog));
        registration.addUrlPatterns(API_URL_PATTERN);
        registration.setName("shAgentErrorRecordingFilter");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 30);
        return registration;
    }
}
