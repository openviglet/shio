/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.api.token;

import java.time.Instant;

/**
 * Result of minting a short-lived preview token (SH13).
 *
 * @param token      the minted token value (sent as the {@code Key} header / via the front-end link)
 * @param previewUrl a ready-to-open URL at the site front-end that enables Draft Mode for this token
 * @param expiresAt  when the token stops authenticating
 * @param ttlSeconds the effective lifetime granted (after clamping to the configured max)
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPreviewTokenResponse(String token, String previewUrl, Instant expiresAt, long ttlSeconds) {
}
