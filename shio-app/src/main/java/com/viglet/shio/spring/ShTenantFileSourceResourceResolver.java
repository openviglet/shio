/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring;

import java.util.List;
import java.util.Locale;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import com.viglet.shio.tenant.ShTenantContext;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Roots every {@code /file_source/**} lookup at the current tenant's
 * sub-directory (SH47), so the public delivery path serves the same
 * {@code <root>/<tenant>/<fileId>/<fileName>} layout that
 * {@code ShFileStorageService} writes.
 *
 * <p>It rewrites the request path to {@code <tenant>/<path>} and delegates to the
 * rest of the chain (the built-in {@code PathResourceResolver}). When the
 * tenant-scoped resource is absent it retries the un-prefixed path, so bytes
 * uploaded before SH47 (the flat {@code <root>/<fileId>/<fileName>} layout) keep
 * resolving. Inserted ahead of the default resolver in
 * {@link ShStaticResourceConfiguration}; the tenant is bound on the request
 * thread by {@code ShTenantFilter} before resource handling runs.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShTenantFileSourceResourceResolver extends AbstractResourceResolver {

    @Override
    protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
                                               List<? extends Resource> locations,
                                               ResourceResolverChain chain) {
        String tenant = tenantSegment();
        Resource tenantScoped = chain.resolveResource(request, tenant + "/" + requestPath, locations);
        if (tenantScoped != null) {
            return tenantScoped;
        }
        // Backward-compat: pre-SH47 flat <root>/<fileId>/<fileName> layout.
        return chain.resolveResource(request, requestPath, locations);
    }

    @Override
    protected String resolveUrlPathInternal(String resourceUrlPath, List<? extends Resource> locations,
                                            ResourceResolverChain chain) {
        return chain.resolveUrlPath(resourceUrlPath, locations);
    }

    /** The current tenant reduced to a safe single path segment (defaults to {@code shio}). */
    private static String tenantSegment() {
        String tenant = ShTenantContext.getTenantId();
        String value = tenant != null && !tenant.isBlank() ? tenant : ShTenantContext.DEFAULT_TENANT;
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")
                ? "_" : cleaned.toLowerCase(Locale.ROOT);
    }
}
