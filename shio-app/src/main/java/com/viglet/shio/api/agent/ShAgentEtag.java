/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Conditional reads for the agent surface (SH116) — strong {@code ETag}s and the
 * {@code 304} that makes re-reading the context nearly free.
 *
 * <h2>Why this is a token feature, not a bandwidth one</h2>
 *
 * <p>The context pack is the payload law P2 asks an agent to read <em>first, every
 * session</em>. Re-reading it is therefore the most repeated cost on the surface,
 * and until now it was a decision the agent had to weigh: pay a few thousand tokens
 * to be sure, or work from a remembered digest and risk being wrong. A {@code 304}
 * removes the trade — an unchanged pack costs a header — so "re-check the context"
 * becomes the cheap default rather than a judgement call.</p>
 *
 * <p>Note what is <em>not</em> saved: the server still builds the pack to know its
 * digest, so the database work is unchanged. That is the correct trade here. The
 * scarce resource is the caller's context window, not this instance's CPU, and a
 * cached digest that could go stale would break the one guarantee that makes the
 * whole scheme usable.</p>
 *
 * <h2>Representation, not just content</h2>
 *
 * <p>An entity tag identifies a <em>representation</em>. The SH77 digest deliberately
 * covers only the pack's content, so the JSON and terse renderings of one state share
 * a digest — correct for "has anything changed?", wrong for {@code If-None-Match},
 * where handing a terse-cached client a tag it earned from JSON would let it keep the
 * wrong bytes. Every tag built here therefore mixes in the representation
 * discriminators ({@code format}, {@code ids}, and anything else that changes the
 * bytes) alongside the content digest.</p>
 *
 * <h2>Strong, and revalidated</h2>
 *
 * <p>Tags are strong: the pack is byte-deterministic (P5), so two identical states
 * really do produce identical bytes, and a weak tag would understate that. Responses
 * carry {@code Cache-Control: no-cache}, which does not mean "do not store" — it
 * means "store it, but ask me before reusing it". That is exactly the behaviour
 * wanted: content can change at any moment, so a freshness lifetime would serve
 * stale content, while revalidation costs one round trip and no tokens.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentEtag {

    /** Hex characters of SHA-256 kept. 16 is far past collision relevance here. */
    private static final int HEX_LENGTH = 16;

    /** ASCII unit separator - cannot occur in any joined value, so no two part lists collide. */
    private static final String SEPARATOR = "\u001F";

    private ShAgentEtag() {
        // utility
    }

    /**
     * A strong entity tag over the given parts.
     *
     * <p>Parts are joined with a control character rather than a printable one so no
     * combination of values can forge another combination's tag — {@code ("a","bc")}
     * and {@code ("ab","c")} must not collide, and with a naive delimiter they would.</p>
     */
    public static String of(String... parts) {
        StringBuilder canonical = new StringBuilder();
        for (String part : parts) {
            canonical.append(part == null ? "" : part).append(SEPARATOR);
        }
        return "\"" + sha256(canonical.toString()).substring(0, HEX_LENGTH) + "\"";
    }

    /**
     * Whether an {@code If-None-Match} header matches this tag.
     *
     * <p>Handles the list form and {@code *}, and compares ignoring a {@code W/}
     * prefix: a client that received a strong tag and echoes it weakened still means
     * "I have this representation", and refusing that just costs a full response.</p>
     */
    public static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank() || etag == null) {
            return false;
        }
        String header = ifNoneMatch.trim();
        if ("*".equals(header)) {
            return true;
        }
        for (String candidate : header.split(",")) {
            if (normalize(candidate).equals(normalize(etag))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The zero-body answer. It still carries the tag and the revalidation directive,
     * because a {@code 304} that dropped them would make the client's next request
     * unconditional and undo the saving on the call after this one.
     */
    public static ResponseEntity<Object> notModified(String etag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .build();
    }

    /** Adds the tag + revalidation directive to a real response. */
    public static ResponseEntity.BodyBuilder ok(String etag) {
        return ResponseEntity.ok().eTag(etag).cacheControl(CacheControl.noCache());
    }

    /** The header name, so callers and tests do not spell it twice. */
    public static String ifNoneMatchHeader() {
        return HttpHeaders.IF_NONE_MATCH;
    }

    private static String normalize(String etag) {
        String value = etag.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        return value;
    }

    private static String sha256(String canonical) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
