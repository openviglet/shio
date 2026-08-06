/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.api.token;

import java.time.Instant;

/**
 * Where to go to look at a draft (SH13, SH184).
 *
 * <p><b>{@code previewUrl} is the field that is always there; the token is not.</b> The
 * response originally described one thing — a minted credential — and since SH184 it
 * describes one of two destinations, because a site with no front end of its own is
 * previewed by Shio's own renderer (SH183) and that route needs no token: {@code /preview/**}
 * is console-authenticated and already draft-preferred. Minting a credential for it would be
 * issuing a key that unlocks nothing, and the honest way to say "no token was needed" is a
 * null one rather than a spare token nobody uses.</p>
 *
 * <p>So a caller opens {@code previewUrl} and does not need to know which kind it got. One
 * that wants the distinction reads {@code token}: present means an external front end is
 * being handed a credential to turn its own draft mode on.</p>
 *
 * @param token      the minted token value (sent as the {@code Key} header / via the
 *                   front-end link), or {@code null} when Shio renders the preview itself
 * @param previewUrl a ready-to-open URL — the site's front end with Draft Mode enabled for
 *                   this token, or Shio's own {@code /preview/**} route
 * @param expiresAt  when the token stops authenticating; {@code null} when there is no token
 * @param ttlSeconds the effective lifetime granted (after clamping to the configured max);
 *                   {@code 0} when there is no token
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public record ShPreviewTokenResponse(String token, String previewUrl, Instant expiresAt, long ttlSeconds) {
}
