/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
package com.viglet.shio.persistence.model.token;

/**
 * Access scope of a CDA (Content Delivery API) token (SH2).
 *
 * <ul>
 *   <li>{@code READ} — read published content only (GET on {@code /api/v2/cda/**}).</li>
 *   <li>{@code PREVIEW} — read + (later, SH13) see unpublished drafts. Carries the
 *       {@code ROLE_CDA_PREVIEW} authority so the draft hook can flip the pinned
 *       state without changing the public URL contract.</li>
 *   <li>{@code WRITE} — read + (later, SH10) write back through the CDA, e.g. the
 *       Universal Editor save path. Carries {@code ROLE_CDA_WRITE} and is not
 *       method-gated to GET.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShApiTokenScope {
    READ,
    PREVIEW,
    WRITE
}
