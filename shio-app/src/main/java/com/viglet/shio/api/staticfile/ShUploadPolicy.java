/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * The verdict on an upload, before anything is stored (SH951).
 *
 * <p>SH950 stopped uploaded bytes being served as documents. They were still <em>stored</em>,
 * still listed, and still copied into a content tree by {@code shio pull} — so the hole moved
 * rather than closed. This is the half that decides whether the bytes arrive at all.</p>
 *
 * <p>Three verdicts, because two would force a choice nobody wants to make about SVG: refuse a
 * logo, or keep accepting one that can carry script. {@link Verdict#SANITIZE} is the third answer,
 * and {@link ShSvgSanitizer} is what makes it one.</p>
 *
 * <p>The refusal is a teaching 400 (P6): it carries {@code allowed} — the verdicts, not a list of
 * types, since the list is per-instance — and a {@code fix} naming the exact property key that
 * would let this upload through. A refusal an operator cannot act on is one they route around.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShUploadPolicy {

    public enum Verdict {
        ALLOW,
        DENY,
        SANITIZE
    }

    private final ShUploadPolicyProperties properties;

    public ShUploadPolicy(ShUploadPolicyProperties properties) {
        this.properties = properties;
    }

    /**
     * What happens to bytes of this type.
     *
     * <p>Most specific first: the exact media type, then its {@code family/*}, then the instance
     * default. A type nobody named is allowed, because a policy is a list of exceptions and a
     * deny-by-default list would have to enumerate every font and image format anyone ships.</p>
     */
    public Verdict verdictFor(String contentType) {
        String bare = bare(contentType);
        Map<String, String> policy = properties.getPolicy();
        String exact = policy.get(bare);
        if (exact != null) {
            return parse(exact, bare);
        }
        int slash = bare.indexOf('/');
        if (slash > 0) {
            String family = policy.get(bare.substring(0, slash) + "/*");
            if (family != null) {
                return parse(family, bare);
            }
        }
        return parse(properties.getDefaultVerdict(), bare);
    }

    /**
     * Refuse now, or say the bytes need cleaning.
     *
     * @return {@code true} where the caller must sanitize before storing
     * @throws RefusedUploadException where the policy denies this type outright
     */
    public boolean check(String contentType, String fileName) {
        Verdict verdict = verdictFor(contentType);
        if (verdict == Verdict.DENY) {
            throw new RefusedUploadException(bare(contentType), fileName);
        }
        return verdict == Verdict.SANITIZE;
    }

    /** The media type without its parameters, lowercased; never {@code null}. */
    static String bare(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE;
        }
        int semicolon = contentType.indexOf(';');
        String bare = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();
        return bare.isEmpty() ? ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE : bare.toLowerCase(Locale.ROOT);
    }

    private static Verdict parse(String verdict, String forType) {
        try {
            return Verdict.valueOf(verdict.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnreadablePolicyException(forType, verdict);
        }
    }

    /** A 400 that names the property which would let these bytes through (P6). */
    public static class RefusedUploadException extends ShTeachingException {

        public RefusedUploadException(String contentType, String fileName) {
            super(ShProblem.of("upload-type-denied", HttpStatus.BAD_REQUEST,
                            "This instance does not accept '" + contentType + "' uploads")
                    .withDetail("'" + fileName + "' is " + contentType + ", which the upload policy "
                            + "denies. HTML and JavaScript are denied by default: neither is "
                            + "legitimately an asset, and stored bytes are still listed and still "
                            + "copied into a content tree even when they are served safely.")
                    .withAllowed(new TreeSet<>(List.of("allow", "deny", "sanitize")))
                    .withFix("If this instance really does serve " + contentType + " as an asset "
                            + "— `shio clone --scripts` uploads a captured site's own bundle — set "
                            + "`shio.upload.policy." + contentType + "=allow`. For a page, write "
                            + "content instead: an HTML file is not an asset.")
                    .withExample("shio.upload.policy." + contentType + "=allow"));
        }
    }

    /** A misconfigured instance says so at the first upload, naming the key and what it may say. */
    public static class UnreadablePolicyException extends ShTeachingException {

        public UnreadablePolicyException(String contentType, String verdict) {
            super(ShProblem.of("upload-policy-unreadable", HttpStatus.INTERNAL_SERVER_ERROR,
                            "The upload policy for '" + contentType + "' says '" + verdict + "'")
                    .withDetail("A policy value must be one of the three verdicts. This instance "
                            + "cannot decide whether to accept the upload, so it accepts nothing.")
                    .withAllowed(new TreeSet<>(List.of("allow", "deny", "sanitize")))
                    .withFix("Correct `shio.upload.policy." + contentType + "` or "
                            + "`shio.upload.default-verdict` in application.properties."));
        }
    }
}
