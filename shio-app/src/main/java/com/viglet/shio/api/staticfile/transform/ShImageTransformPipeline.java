/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Optional;

import org.springframework.core.io.Resource;

/**
 * The seam SH39 routes a parsed {@link ShImageTransformSpec} into. The query-param
 * interception layer (controller + resource transformer) decides <em>whether</em> a
 * transform was requested; this pipeline decides whether it can <em>fulfil</em> it.
 *
 * <p>SH39 ships only a no-op implementation ({@code ShNoOpImageTransformPipeline})
 * that always returns {@link Optional#empty()} — the interception is wired and
 * tested, but the bytes pass through untouched until SH40 supplies the real
 * resize/crop pipeline (imageio/Thumbnailator).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public interface ShImageTransformPipeline {

    /**
     * Apply {@code spec} to {@code source}.
     *
     * @param source            the original bytes (a file-backed or byte-array resource)
     * @param sourceContentType the source content type as best determined by the
     *                          caller (a hint; may be {@code null})
     * @param spec              the requested transform (never {@code null})
     * @return the transformed result, or {@link Optional#empty()} to signal "not
     *         transformed — serve the original" (e.g. the source is not an image,
     *         or no transform pipeline is available yet)
     */
    Optional<ShImageTransformResult> transform(Resource source, String sourceContentType, ShImageTransformSpec spec);
}
