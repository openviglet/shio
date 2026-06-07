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

    public ShStaticResourceConfiguration(ShFileSourceProperties fileSourceProperties) {
        this.fileSourceProperties = fileSourceProperties;
    }

    @Override
    public void addResourceHandlers(@NotNull ResourceHandlerRegistry registry) {
        String fileSourceLocation = fileSourceProperties.getRootDir().toUri().toString();
        registry.addResourceHandler("/file_source/**")
                .addResourceLocations(fileSourceLocation)
                .setCachePeriod(CACHE_SECONDS);
    }
}
