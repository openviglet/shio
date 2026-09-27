/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SH1165 — open-in-view for every request except the change stream.
 *
 * <p>Spring Boot's own registration ({@code spring.jpa.open-in-view}, on by default) covers every
 * path and takes no exclusion. On {@code GET /api/v2/events} that is a leak with a thirty-minute
 * life: the stream's opening read takes a connection, and the EntityManager open-in-view bound
 * keeps it until the async request completes — on disconnect, or at
 * {@code shio.events.timeout-millis}. Measured: ten open console tabs held all ten of the default
 * pool, and every other request — sign-in included — then waited thirty seconds and failed.</p>
 *
 * <p>So {@code application.properties} turns Boot's registration off and this puts the same
 * interceptor back with the stream path excluded. Everything else keeps the session it had, which
 * several reads still rely on ({@code ShFolderRepository}, {@code ShContentAclService}); turning
 * it off everywhere would trade this for lazy-loading failures nobody has measured. The stream
 * needs none: its opening read and every pass run inside {@code ShAgentChangesService}'s own
 * transaction, and the passes already run on a thread open-in-view never reached.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShEventsOpenInViewConfig implements WebMvcConfigurer {

    /** The paths that hold a request open, and so must not hold a connection with it. */
    static final String[] HELD_OPEN = {"/api/v2/events", "/api/v2/events/**"};

    private final OpenEntityManagerInViewInterceptor openInView;

    public ShEventsOpenInViewConfig(OpenEntityManagerInViewInterceptor openInView) {
        this.openInView = openInView;
    }

    /** A bean, not a {@code new} in the registry: it finds the EntityManagerFactory as one. */
    @Bean
    static OpenEntityManagerInViewInterceptor shOpenEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addWebRequestInterceptor(openInView).excludePathPatterns(HELD_OPEN);
    }
}
