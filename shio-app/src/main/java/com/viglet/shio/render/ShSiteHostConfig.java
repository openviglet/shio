/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers {@link ShSiteHostFilter} (SH1354) ahead of every other filter.
 *
 * <p>First, because the whole point is that each later filter, Spring Security's chain included,
 * sees the re-addressed {@code /sites/…} request and decides about that one. With
 * {@code shio.sites.host-domain} unset the filter answers nothing and passes every request on.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Configuration(proxyBeanMethods = false)
public class ShSiteHostConfig {

    @Bean
    FilterRegistrationBean<ShSiteHostFilter> shSiteHostFilter(ShSiteHost siteHost) {
        FilterRegistrationBean<ShSiteHostFilter> registration =
                new FilterRegistrationBean<>(new ShSiteHostFilter(siteHost));
        registration.setName("shSiteHostFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
