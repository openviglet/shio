/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.io.IOException;
import java.net.URLConnection;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceTransformer;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Intercepts {@code ?w=&h=&format=&crop=} on the public {@code /file_source/**}
 * delivery path (SH39). It is registered on that resource handler's chain in
 * {@code ShStaticResourceConfiguration}; the parallel {@code /api/v2/staticfile/{id}}
 * controller route does its own interception inline.
 *
 * <p>When no transform parameter is present, or the {@link ShImageTransformPipeline}
 * declines (returns empty), it returns the resolved resource untouched — so a plain
 * download is unaffected. When the pipeline produces bytes, it wraps them in a
 * {@link TransformedResource} (which preserves the original file name and
 * last-modified). A malformed query string raises {@link ShImageTransformException}
 * (→ 400) from {@link ShImageTransformSpec#parse}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class ShImageTransformResourceTransformer implements ResourceTransformer {

    private final ShImageTransformPipeline pipeline;

    public ShImageTransformResourceTransformer(ShImageTransformPipeline pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource,
                              ResourceTransformerChain transformerChain) throws IOException {
        Resource resolved = transformerChain.transform(request, resource);

        Optional<ShImageTransformSpec> spec = ShImageTransformSpec.parse(
                request.getParameter("w"),
                request.getParameter("h"),
                request.getParameter("format"),
                request.getParameter("crop"));
        if (spec.isEmpty()) {
            return resolved;
        }

        String fileName = resolved.getFilename();
        String contentType = fileName != null ? URLConnection.guessContentTypeFromName(fileName) : null;

        Optional<ShImageTransformResult> result = pipeline.transform(resolved, contentType, spec.get());
        if (result.isEmpty()) {
            return resolved;
        }
        return new TransformedResource(resolved, result.get().bytes());
    }
}
