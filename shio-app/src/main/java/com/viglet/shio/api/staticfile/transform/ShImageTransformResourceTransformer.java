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
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.resource.HttpResource;
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
 * <p>SH41 format conversion can change the result's content type (e.g. jpeg → webp).
 * Because the resource handler derives the served {@code Content-Type} from the
 * resource file name, the wrapper implements {@link HttpResource} to pin the
 * transform's content type as a response header, overriding the file-name guess.</p>
 *
 * <p>SH42: a cached result carries a stable ETag. The wrapper exposes it via
 * {@link ShTransformedImageResource#getEtag()} so the resource handler's
 * {@code etagGenerator} (wired in {@code ShStaticResourceConfiguration}) can emit an
 * {@code ETag} header and short-circuit {@code If-None-Match} to a 304 on this
 * public delivery path too.</p>
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
        return new ShTransformedImageResource(resolved, result.get());
    }

    /**
     * A {@link TransformedResource} that also advertises the transform's content type
     * via {@link HttpResource#getResponseHeaders()}. The resource handler resolves
     * {@code Content-Type} from the (unchanged) file name first, then applies these
     * headers — so a SH41 format conversion (jpeg → webp) is served as the correct
     * type even though the file name still ends in {@code .jpg}. SH42 additionally
     * carries the cached result's ETag (consumed by the handler's {@code etagGenerator}).
     */
    public static final class ShTransformedImageResource extends TransformedResource implements HttpResource {

        private final String contentType;
        private final String etag;

        ShTransformedImageResource(Resource original, ShImageTransformResult result) {
            super(original, result.bytes());
            this.contentType = result.contentType();
            this.etag = result.etag();
        }

        /** The cached result's ETag (SH42), or {@code null} when the cache is disabled. */
        public String getEtag() {
            return etag;
        }

        @Override
        public HttpHeaders getResponseHeaders() {
            HttpHeaders headers = new HttpHeaders();
            if (contentType != null) {
                headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            }
            return headers;
        }
    }
}
