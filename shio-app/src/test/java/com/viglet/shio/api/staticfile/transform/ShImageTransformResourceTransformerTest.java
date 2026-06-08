/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.HttpResource;
import org.springframework.web.servlet.resource.ResourceResolverChain;
import org.springframework.web.servlet.resource.ResourceTransformerChain;

import jakarta.servlet.http.HttpServletRequest;

class ShImageTransformResourceTransformerTest {

    private static final Resource ORIGINAL = new ByteArrayResource("original".getBytes(StandardCharsets.UTF_8)) {
        @Override
        public String getFilename() {
            return "photo.png";
        }

        @Override
        public long lastModified() {
            // The real delivery resource is file-backed; a byte-array stand-in has no
            // file to derive a timestamp from, which TransformedResource needs.
            return 0L;
        }
    };

    /** A chain whose only step is to echo back the resolved resource. */
    private static ResourceTransformerChain echoChain() {
        return new ResourceTransformerChain() {
            @Override
            public Resource transform(HttpServletRequest request, Resource resource) {
                return resource;
            }

            @Override
            public ResourceResolverChain getResolverChain() {
                return null;
            }
        };
    }

    @Test
    void passesThroughWhenNoTransformParams() throws IOException {
        ShImageTransformPipeline pipeline = (s, ct, spec) -> {
            throw new AssertionError("pipeline must not be called without transform params");
        };
        var transformer = new ShImageTransformResourceTransformer(pipeline);

        Resource out = transformer.transform(new MockHttpServletRequest(), ORIGINAL, echoChain());

        assertSame(ORIGINAL, out, "an un-parametrised request returns the original resource untouched");
    }

    @Test
    void passesThroughWhenPipelineDeclines() throws IOException {
        ShImageTransformPipeline pipeline = (s, ct, spec) -> Optional.empty();
        var transformer = new ShImageTransformResourceTransformer(pipeline);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("w", "100");

        Resource out = transformer.transform(request, ORIGINAL, echoChain());

        assertSame(ORIGINAL, out, "an empty pipeline result serves the original bytes");
    }

    @Test
    void wrapsTransformedBytesWhenPipelineProduces() throws IOException {
        byte[] produced = "resized".getBytes(StandardCharsets.UTF_8);
        ShImageTransformPipeline pipeline =
                (s, ct, spec) -> Optional.of(new ShImageTransformResult(produced, "image/png"));
        var transformer = new ShImageTransformResourceTransformer(pipeline);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("w", "100");

        Resource out = transformer.transform(request, ORIGINAL, echoChain());

        assertArrayEquals(produced, out.getContentAsByteArray(),
                "a non-empty pipeline result replaces the served bytes");
    }

    @Test
    void exposesConvertedContentTypeViaHttpResource() throws IOException {
        // SH41: a jpeg→webp conversion must override the file-name-derived Content-Type.
        ShImageTransformPipeline pipeline = (s, ct, spec) ->
                Optional.of(new ShImageTransformResult("webp-bytes".getBytes(StandardCharsets.UTF_8), "image/webp"));
        var transformer = new ShImageTransformResourceTransformer(pipeline);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("format", "webp");

        Resource out = transformer.transform(request, ORIGINAL, echoChain());

        HttpResource httpResource = assertInstanceOf(HttpResource.class, out,
                "the transformed resource carries response headers");
        assertEquals("image/webp", httpResource.getResponseHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void malformedParamRaisesBadRequest() {
        ShImageTransformPipeline pipeline = (s, ct, spec) -> Optional.empty();
        var transformer = new ShImageTransformResourceTransformer(pipeline);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("w", "-1");

        assertThrows(ShImageTransformException.class,
                () -> transformer.transform(request, ORIGINAL, echoChain()));
    }
}
