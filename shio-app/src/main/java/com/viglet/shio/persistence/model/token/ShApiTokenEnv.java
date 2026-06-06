/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.token;

/**
 * Target content environment of a CDA (Content Delivery API) token (SH5).
 *
 * <p>Orthogonal to the token {@link ShApiTokenScope scope}: scope decides
 * <em>what operations</em> a key may perform, env decides <em>which content
 * state</em> it sees.</p>
 *
 * <ul>
 *   <li>{@code PROD} — read {@code PUBLISHED} content only (the SH1 behaviour).</li>
 *   <li>{@code PREVIEW} — read draft-preferred: prefer the {@code DRAFT} row of a
 *       post, falling back to {@code PUBLISHED}. Lets a preview deployment of the
 *       same front-end see unpublished edits with only a token swap. Carries the
 *       {@code ROLE_CDA_PREVIEW} authority that SH13's short-lived preview tokens
 *       build on.</li>
 * </ul>
 *
 * <p>A {@code null} value (pre-SH5 rows) is treated as {@code PROD} by the filter
 * and service, so existing tokens keep reading published content unchanged.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShApiTokenEnv {
    PROD,
    PREVIEW
}
