/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.spring;

import java.io.IOException;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

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

    /** Forward an SPA entry route to the React shell so client-side routing takes over. */
    public static final String FORWARD_INDEX_HTML = "forward:/index.html";

    private static final int CACHE_SECONDS = 3600 * 24;

    /**
     * React SPA contexts served from {@code classpath:/public/} (mirrors Turing's TurStaticResourceConfiguration).
     *
     * <p>SH926 — {@code /bento} and {@code /bento-vocabulary} were missing, and that made every
     * surface Block AR migrated <b>404 on a packaged instance</b>. The console's parallel bento
     * route tree (SH888) is a sibling of {@code /console}, not a child, so nothing here matched
     * it: measured on a running jar, {@code /console} answered 200 and {@code /bento} answered
     * 404, in the same second.</p>
     *
     * <p>Fourteen tasks reported green live probes over it, because every one of them ran against
     * the Vite dev server, which serves {@code index.html} for any path that is not a file. That
     * is what a dev server is for, and it is why the whole block could be proved on a server
     * nobody ships. <b>A surface is not migrated until the thing that serves it in production
     * serves it</b> — {@code ShSpaContextIsServedTest} asks that of every entry here, and
     * {@code spa-contexts.test.mjs} asks the other direction: that every top-level route tree the
     * React app declares appears in this array.</p>
     *
     * <p>{@code /bento-vocabulary} is its own entry rather than a path under {@code /bento}: it is
     * a sibling segment (SH887 mounts it outside every shell, so the tenant-segment reader cannot
     * mistake it for a tenant), and {@code /bento/**} does not match it.</p>
     */
    private static final String[] REACT_CONTEXTS = { "/login", "/setup", "/console", "/bento", "/bento-vocabulary" };

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

    /**
     * Forward the React SPA entry routes (exact + trailing slash) to {@code /index.html}
     * so the client-side router renders them. Deeper paths are covered by the
     * {@code /<context>/**} resource handlers below (with their index.html fallback).
     * Mirrors Turing's {@code TurStaticResourceConfiguration}.
     */
    @Override
    public void addViewControllers(@NotNull ViewControllerRegistry registry) {
        for (String context : REACT_CONTEXTS) {
            registry.addViewController(context).setViewName(FORWARD_INDEX_HTML);
            registry.addViewController(context + "/").setViewName(FORWARD_INDEX_HTML);
        }
    }

    @Override
    public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
        // React SPA: each context's sub-paths resolve from classpath:/public/, falling
        // back to the SPA shell so client-side routes (and their assets) are served.
        for (String context : REACT_CONTEXTS) {
            registryReact(registry, context);
        }

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

    /** Map {@code <context>/**} to {@code classpath:/public/}, falling back to the SPA shell. */
    private static void registryReact(ResourceHandlerRegistry registry, String context) {
        registryFrontend(registry, "%s/**".formatted(context), "classpath:/public/", "/public/index.html");
    }

    private static void registryFrontend(ResourceHandlerRegistry registry, String context, String location,
            String path) {
        registry.addResourceHandler(context).addResourceLocations(location)
                .resourceChain(true).addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(@NotNull String resourcePath, @NotNull Resource location)
                            throws IOException {
                        Resource requestedResource = location.createRelative(resourcePath);
                        return requestedResource.exists() && requestedResource.isReadable() ? requestedResource
                                : new ClassPathResource(path);
                    }
                });
    }
}
