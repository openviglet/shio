/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 *   <li>{@code AGENT} — the credential an agent runs on (SH106). Reads like
 *       {@code PREVIEW} (an agent works on drafts) and writes on the <b>agent surface
 *       only</b>: {@code /api/v2/agent/**} and {@code /mcp}. It carries
 *       {@code ROLE_AGENT} and deliberately <b>not</b> {@code ROLE_CDA_WRITE}, so it
 *       cannot reach the CDA write path that patches published content directly.</li>
 * </ul>
 *
 * <p>{@code AGENT} exists because the alternative was worse in both directions: an agent
 * either held a {@code WRITE} key — the Universal Editor's credential, which can patch
 * live content outside the agent protocol's guards — or a console username and password,
 * which is every capability the human has and cannot expire. A scope of its own is what
 * lets §XII's invariant hold: the agent path is never a privilege escalation, and now it
 * is also not a privilege <em>superset</em>.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShApiTokenScope {
    READ,
    PREVIEW,
    WRITE,
    AGENT
}
