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
 *   <li>{@code AGENT} — the credential a coding agent runs on (SH106). Reads
 *       draft-preferred like {@code PREVIEW} and writes like {@code WRITE}, but is
 *       <b>bounded</b>: it may be denied the publish verb ({@code mayPublish}, SH107)
 *       and carries per-session call/write ceilings (SH110). Carries
 *       {@code ROLE_AGENT} on top of the CDA authorities.</li>
 * </ul>
 *
 * <h2>Why {@code AGENT} is a scope and not a role on {@code WRITE}</h2>
 *
 * <p>Because the interesting question about an agent credential is not <em>what</em> it
 * may touch but <em>how much</em> and <em>how irreversibly</em>. A {@code WRITE} token
 * is a delivery-side integration key with no budget and no publish gate; giving it those
 * fields would change the meaning of every token already issued. §XII's invariant — the
 * agent path is never a privilege escalation — is easier to hold when the agent path has
 * a credential of its own that starts more restricted, not less.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public enum ShApiTokenScope {
    READ,
    PREVIEW,
    WRITE,
    AGENT;

    /**
     * @return {@code true} when a token of this scope may use methods other than
     *         {@code GET}. Asked as a question rather than compared against a literal so
     *         that adding a fourth writable scope cannot leave one call site behind — the
     *         SH2 method gate is the boundary the whole confinement rests on.
     */
    public boolean canWrite() {
        return this == WRITE || this == AGENT;
    }

    /** @return {@code true} when a token of this scope reads draft-preferred content. */
    public boolean canPreview() {
        return this == PREVIEW || this == AGENT;
    }
}
