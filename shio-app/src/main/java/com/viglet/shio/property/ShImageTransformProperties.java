/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.property;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Limits &amp; security policy for the image-transform delivery surface (SH43) —
 * the final slice of the SH25 epic. SH39 parses {@code ?w=&h=&format=&crop=} and
 * SH40/SH41 transform; this gates <em>which</em> transforms a caller may request.
 *
 * <p>Three controls, all enforced by
 * {@link com.viglet.shio.api.staticfile.transform.ShImageTransformGuard} on both
 * delivery routes (the {@code /api/v2/staticfile/{id}} controller and the public
 * {@code /file_source/**} resource transformer):</p>
 * <ul>
 *   <li><strong>Max output dimensions</strong> ({@link #maxWidth}/{@link #maxHeight})
 *       — a requested {@code w}/{@code h} above the ceiling is a 400, so a single URL
 *       can't ask for a 50000×50000 re-encode and pin a CPU.</li>
 *   <li><strong>Allowed-format policy</strong> ({@link #allowedFormats}) — narrows the
 *       syntactic allow-list the SH39 parser accepts down to what this install permits
 *       (e.g. drop {@code bmp}); a disallowed {@code format} is a 400.</li>
 *   <li><strong>Decode-bomb guard</strong> ({@link #maxSourcePixels}) — the transform
 *       pipeline probes the <em>source</em> pixel count <em>before</em> decoding and
 *       declines (serves the original, never 500s) when it exceeds the ceiling, so a
 *       tiny but enormously-dimensioned upload can't exhaust the heap.</li>
 *   <li><strong>Signed URLs</strong> ({@link #signing}) — opt-in HMAC gate so only
 *       transform URLs minted with the shared secret are honoured (a missing/invalid
 *       signature is a 403). Stops an open transform endpoint from being abused to
 *       flood the SH42 cache with arbitrary param combinations.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.image-transform")
public class ShImageTransformProperties {

    /** Maximum requested output width in pixels; a larger {@code ?w=} is rejected (400). */
    private int maxWidth = 5000;

    /** Maximum requested output height in pixels; a larger {@code ?h=} is rejected (400). */
    private int maxHeight = 5000;

    /**
     * Maximum <em>source</em> image area (width × height) the pipeline will decode.
     * Probed before decoding; a larger source declines to the original bytes rather
     * than allocating the raster. Default 40 MP (≈ 8000×5000).
     */
    private long maxSourcePixels = 40_000_000L;

    /**
     * The output formats this install permits on {@code ?format=}. A subset of the
     * SH39 syntactic allow-list ({@code jpg,jpeg,png,gif,webp,avif}); a request for a
     * parseable-but-not-permitted format is a 400. Matched case-insensitively.
     */
    private List<String> allowedFormats = List.of("jpg", "jpeg", "png", "gif", "webp", "avif");

    /** Signed-URL (HMAC) gate; disabled by default. */
    private Signing signing = new Signing();

    @Setter
    @Getter
    public static class Signing {

        /**
         * When {@code true}, every transform request must carry a valid {@code sig}
         * (HMAC over the resource id + params, see
         * {@link com.viglet.shio.api.staticfile.transform.ShImageTransformSigner});
         * a missing/invalid signature is a 403. Plain (untransformed) downloads are
         * unaffected — only the transform surface is gated.
         */
        private boolean enabled = false;

        /**
         * The shared HMAC-SHA256 secret. Required when {@link #enabled} is {@code true}
         * — with signing on and the secret blank the gate fails closed (everything 403),
         * since unsigned transforms can't be authorised.
         */
        private String secret = "";
    }
}
