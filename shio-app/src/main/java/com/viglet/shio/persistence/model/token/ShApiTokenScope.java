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
    AGENT,

    /**
     * The credential a rendered form carries (SH631), and the first scope bounded by <b>data</b>
     * rather than by verb.
     *
     * <h2>The token is public by construction, so the scope is the control</h2>
     *
     * <p>SH364 settled that Shio receives submissions token-scoped and <b>never anonymously</b>.
     * This token sits in a page a stranger can read, so secrecy protects nothing: what protects the
     * instance is that the credential can do exactly one thing — create a {@code DRAFT} post of an
     * eligible type in one site's configured folder. It cannot publish, cannot read, cannot move and
     * cannot touch a second site.</p>
     *
     * <h2>It carries neither {@code ROLE_CDA} nor {@code ROLE_USER}, unlike every scope above</h2>
     *
     * <p>That is the load-bearing difference and it is a departure from the shape the other four
     * share. Every one of them grants {@code ROLE_CDA} + {@code ROLE_USER} and then <em>adds</em>
     * capability, which is right when the weakest scope is still allowed to read the delivery API.
     * A form token is not: granting it {@code ROLE_CDA} would let anyone who viewed the page's
     * source read the whole site through {@code GET /api/v2/cda/**}, which is the privilege
     * escalation §XII's invariant forbids — the same reasoning that keeps {@code AGENT} away from
     * {@code ROLE_CDA_WRITE}, one scope further down.</p>
     *
     * <h2>Eligibility excludes every system post-type, and that predicate carries the severity</h2>
     *
     * <p>{@code PageLayout}, {@code Region} and {@code Theme} are Handlebars, compiled and executed
     * by the renderer. A form that could create one is not a spam problem — it is a stranger writing
     * a template this instance runs. So eligibility is the site's allow-list <b>intersected with</b>
     * "not a system type" ({@code ShFormEligibility}); an allow-list alone would still let an
     * operator name {@code Theme} by mistake, and that is not a mistake worth leaving available.</p>
     */
    FORM
}
