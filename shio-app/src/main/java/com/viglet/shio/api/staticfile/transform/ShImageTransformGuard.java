/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.viglet.shio.property.ShImageTransformProperties;

/**
 * Enforces the SH43 limits &amp; security policy on a parsed {@link ShImageTransformSpec}
 * before it reaches the transform pipeline. Shared by <em>both</em> delivery routes — the
 * {@code /api/v2/staticfile/{id}} controller injects it; the {@code /file_source/**}
 * resource transformer is constructed with it in {@code ShStaticResourceConfiguration} —
 * so the policy is identical wherever a transform is requested.
 *
 * <p>{@link #check} runs three gates, fail-fast in this order:</p>
 * <ol>
 *   <li><strong>Signature</strong> (when {@code signing.enabled}) — an unsigned/invalid
 *       request is a {@link ShImageTransformForbiddenException} (403), checked first so
 *       an unauthorised caller learns nothing about the limit policy.</li>
 *   <li><strong>Allowed-format policy</strong> — a {@code format} outside
 *       {@code shio.image-transform.allowed-formats} is a {@link ShImageTransformException}
 *       (400).</li>
 *   <li><strong>Max output dimensions</strong> — a {@code w}/{@code h} above the configured
 *       ceiling is a 400.</li>
 * </ol>
 *
 * <p>The SH39 syntactic validation (positive integers, parseable format/crop) has already
 * run in {@link ShImageTransformSpec#parse} by the time this is called; the decode-bomb
 * guard (max <em>source</em> pixels) lives in the pipeline itself, since only it has the
 * decoded dimensions.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShImageTransformGuard {

    private final ShImageTransformProperties properties;
    private final ShImageTransformSigner signer;

    public ShImageTransformGuard(ShImageTransformProperties properties, ShImageTransformSigner signer) {
        this.properties = properties;
        this.signer = signer;
    }

    /**
     * Authorise and policy-check a requested transform.
     *
     * @param resourceId the {@code File} post id the transform targets (the signature is
     *                   bound to it; identical on both delivery routes)
     * @param spec       the parsed, syntactically-valid transform request
     * @param providedSig the {@code sig} query parameter (may be {@code null})
     * @throws ShImageTransformForbiddenException when signing is enabled and the signature
     *         is missing/invalid (403)
     * @throws ShImageTransformException when the format is not permitted or a dimension
     *         exceeds its ceiling (400)
     */
    public void check(String resourceId, ShImageTransformSpec spec, String providedSig) {
        if (properties.getSigning().isEnabled() && !signer.verify(resourceId, spec, providedSig)) {
            throw new ShImageTransformForbiddenException("Missing or invalid image-transform signature");
        }

        if (spec.format() != null && !formatAllowed(spec.format())) {
            throw new ShImageTransformException("Format not permitted: " + spec.format());
        }

        if (spec.width() != null && spec.width() > properties.getMaxWidth()) {
            throw new ShImageTransformException(
                    "w exceeds the maximum of " + properties.getMaxWidth());
        }
        if (spec.height() != null && spec.height() > properties.getMaxHeight()) {
            throw new ShImageTransformException(
                    "h exceeds the maximum of " + properties.getMaxHeight());
        }
    }

    private boolean formatAllowed(String format) {
        Set<String> allowed = properties.getAllowedFormats().stream()
                .map(f -> f.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return allowed.contains(format.toLowerCase(Locale.ROOT));
    }
}
