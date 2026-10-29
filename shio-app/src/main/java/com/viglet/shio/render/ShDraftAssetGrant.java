/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAddress;

/**
 * SH1282 — a short-lived grant to read one draft asset of one site, without a session.
 *
 * <h2>Why a capability and not a cookie</h2>
 *
 * <p>The editor's live preview renders into a sandboxed {@code srcdoc} frame whose origin is
 * {@code null}, and SH1273 rebased its references into the public {@code /sites/**} space. That route
 * serves PUBLISHED bytes only, and the frame sends no session cookie, so an image or font a curator
 * has uploaded and not published, which is exactly what they are previewing, rendered broken. The
 * render that writes the frame's markup is an authenticated call, so it may hand the frame a grant
 * for the draft bytes it names, the way a preview link authorises one read without an account.</p>
 *
 * <h2>What a grant is bound to</h2>
 *
 * <p>The site key as the URL spells it, the asset's path, and an expiry {@link #TTL} out, under
 * HMAC-SHA256 and compared in constant time, so a grant for one asset reads no other and stops
 * working on its own. The key is random per process rather than configured: nothing has to be set
 * for the preview to work, and a restart only expires grants that last minutes anyway.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShDraftAssetGrant {

    /** The query parameter a granted asset URL carries. */
    public static final String PARAM = "sh-draft";

    /** Long enough for an editing session's frame to load, short enough to be no kind of link. */
    static final Duration TTL = Duration.ofMinutes(10);

    private static final String HMAC = "HmacSHA256";

    private final byte[] key;
    private final Clock clock;

    public ShDraftAssetGrant() {
        this(randomKey(), Clock.systemUTC());
    }

    ShDraftAssetGrant(byte[] key, Clock clock) {
        this.key = key.clone();
        this.clock = clock;
    }

    /** A grant for {@code path} in {@code siteKey}, as {@code <expiry>.<signature>}. */
    public String mint(String siteKey, String path) {
        long expires = clock.instant().plus(TTL).getEpochSecond();
        return expires + "." + sign(siteKey, path, expires);
    }

    /** Whether {@code grant} admits a read of {@code path} in {@code siteKey} now. */
    public boolean admits(String siteKey, String path, String grant) {
        if (grant == null || siteKey == null || path == null) {
            return false;
        }
        int dot = grant.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        String expiry = grant.substring(0, dot);
        // Digits only, and few enough to fit a long, so the parse below cannot throw.
        if (expiry.length() > 18 || !expiry.chars().allMatch(Character::isDigit)) {
            return false;
        }
        long expires = Long.parseLong(expiry);
        if (clock.instant().getEpochSecond() > expires) {
            return false;
        }
        byte[] expected = sign(siteKey, path, expires).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected,
                grant.substring(dot + 1).getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String siteKey, String path, long expires) {
        String subject = siteKey.trim() + "|" + ShAddress.normalizePath(path) + "|" + expires;
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(subject.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException unreachable) {
            // HmacSHA256 is mandated by every JCE and the key is never empty.
            throw new IllegalStateException("HmacSHA256 unavailable", unreachable);
        }
    }

    private static byte[] randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }
}
