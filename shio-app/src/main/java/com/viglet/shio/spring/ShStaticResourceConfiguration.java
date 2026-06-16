/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.viglet.shio.api.staticfile.transform.ShImageTransformGuard;
import com.viglet.shio.api.staticfile.transform.ShImageTransformPipeline;
import com.viglet.shio.api.staticfile.transform.ShImageTransformResourceTransformer;
import com.viglet.shio.api.staticfile.transform.ShImageTransformResourceTransformer.ShTransformedImageResource;
import com.viglet.shio.property.ShFileSourceProperties;

/**
 * Serves uploaded binaries from the on-disk file_source root (SH38).
 *
 * <p>{@code /file_source/**} maps to the storage root so a {@code File} post's
 * bytes are reachable at {@code /file_source/<postId>/<fileName>} (the path is
 * {@code permitAll} in {@code ShSecurityConfig}). Only the file_source root is
 * exposed — deliberately <em>not</em> the whole {@code store/} tree, which also
 * holds the H2 database and logs.</p>
 *
 * <p>SH39 adds a {@link ShImageTransformResourceTransformer} to this handler's
 * resource chain so {@code ?w=&h=&format=&crop=} on a {@code /file_source/**} URL
 * is intercepted and routed to the {@link ShImageTransformPipeline}. The chain is
 * created with {@code cacheResources=false} on purpose: the built-in resource
 * cache keys on the request path only (ignoring the query string), which would
 * serve one transform for a different one — proper per-params caching is SH42's
 * {@code ShImageTransformCache} (keyed by source + params, inside the pipeline).</p>
 *
 * <p>SH42 also registers an {@code etagGenerator} on the handler: for a transformed
 * resource it returns the cached result's stable ETag, so the handler emits an
 * {@code ETag} header and answers {@code If-None-Match} with a 304 on this public
 * path (a plain, untransformed download returns {@code null} → default behaviour).</p>
 *
 * <p>This {@code WebMvcConfigurer} only registers a resource handler, so it is
 * purely additive to Spring Boot's default static-resource handling and the
 * React SPA serving.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Configuration
public class ShStaticResourceConfiguration implements WebMvcConfigurer {

    private static final int CACHE_SECONDS = 3600 * 24;

    private final ShFileSourceProperties fileSourceProperties;
    private final ShImageTransformPipeline imageTransformPipeline;
    private final ShImageTransformGuard imageTransformGuard;

    public ShStaticResourceConfiguration(ShFileSourceProperties fileSourceProperties,
                                         ShImageTransformPipeline imageTransformPipeline,
                                         ShImageTransformGuard imageTransformGuard) {
        this.fileSourceProperties = fileSourceProperties;
        this.imageTransformPipeline = imageTransformPipeline;
        this.imageTransformGuard = imageTransformGuard;
    }

    @Override
    public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
        String fileSourceLocation = fileSourceProperties.getRootDir().toUri().toString();
        registry.addResourceHandler("/file_source/**")
                .addResourceLocations(fileSourceLocation)
                .setCachePeriod(CACHE_SECONDS)
                .setEtagGenerator(resource -> resource instanceof ShTransformedImageResource transformed
                        ? transformed.getEtag() : null)
                .resourceChain(false)
                // SH47: root the lookup at the current tenant's sub-directory so the
                // public delivery path mirrors the per-tenant on-disk layout.
                .addResolver(new ShTenantFileSourceResourceResolver())
                .addTransformer(new ShImageTransformResourceTransformer(imageTransformPipeline, imageTransformGuard));
    }
}
