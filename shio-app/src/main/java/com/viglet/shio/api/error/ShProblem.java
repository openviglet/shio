/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * A <em>teaching</em> error (SH80) — an RFC 9457 problem extended with the four
 * fields that turn a rejection into an instruction the caller can act on without
 * a second round trip:
 *
 * <ul>
 *   <li>{@code fix} — one imperative sentence: what to do instead.</li>
 *   <li>{@code allowed} — the closed set of acceptable values, when there is one.</li>
 *   <li>{@code didYouMean} — the nearest match to what was sent (see {@link ShDidYouMean}).</li>
 *   <li>{@code example} — a minimal correct payload/value.</li>
 *   <li>{@code at} — which part of the request failed, once a request can carry
 *       many (SH81's batch: {@code ops[3]}).</li>
 * </ul>
 *
 * <p>Design law <b>P6 — errors are instructions</b>. Every field present here is
 * a round trip an agent does not spend re-deriving the contract, so a 4xx that
 * carries none of them is considered incomplete. Empty/blank fields are omitted
 * from the wire so the payload never pays for what it doesn't say (P3).</p>
 *
 * <p>Immutable; build with {@link #of(String, HttpStatusCode, String)} and the
 * {@code with*} methods, then render with {@link #toProblemDetail()}.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShProblem {

    /** Base URI for problem {@code type}s; the slug is appended. */
    public static final String TYPE_BASE = "https://shio.viglet.com/errors/";

    private final String slug;
    private final HttpStatusCode status;
    private final String title;
    private final String detail;
    private final String fix;
    private final List<String> allowed;
    private final String didYouMean;
    private final Object example;
    private final String at;

    private ShProblem(String slug, HttpStatusCode status, String title, String detail,
                      String fix, List<String> allowed, String didYouMean, Object example,
                      String at) {
        this.slug = slug;
        this.status = status;
        this.title = title;
        this.detail = detail;
        this.fix = fix;
        this.allowed = allowed;
        this.didYouMean = didYouMean;
        this.example = example;
        this.at = at;
    }

    /**
     * @param slug   kebab-case problem identifier, appended to {@link #TYPE_BASE}
     *               (e.g. {@code unknown-widget}) — stable, so a caller can branch on it
     * @param status the HTTP status to answer with
     * @param title  a short human-readable summary, independent of the occurrence
     */
    public static ShProblem of(String slug, HttpStatusCode status, String title) {
        return new ShProblem(slug, status, title, null, null, List.of(), null, null, null);
    }

    /** @param detail what went wrong in <em>this</em> occurrence. */
    public ShProblem withDetail(String detail) {
        return new ShProblem(slug, status, title, detail, fix, allowed, didYouMean, example, at);
    }

    /** @param fix one imperative sentence telling the caller what to do instead. */
    public ShProblem withFix(String fix) {
        return new ShProblem(slug, status, title, detail, fix, allowed, didYouMean, example, at);
    }

    /**
     * @param allowed the closed set of acceptable values. Copied and left in the
     *                order given — callers pass a deterministic order (P5) so two
     *                identical failures produce byte-identical bodies.
     */
    public ShProblem withAllowed(Collection<String> allowed) {
        List<String> copy = allowed == null ? List.of() : List.copyOf(allowed);
        return new ShProblem(slug, status, title, detail, fix, copy, didYouMean, example, at);
    }

    /** @param didYouMean the nearest match to the rejected value, or {@code null}. */
    public ShProblem withDidYouMean(String didYouMean) {
        return new ShProblem(slug, status, title, detail, fix, allowed, didYouMean, example, at);
    }

    /**
     * Resolves {@code didYouMean} from {@link #allowed} by nearest match, so a
     * caller that already supplied the allow-list gets the suggestion for free.
     *
     * @param sent the rejected value
     */
    public ShProblem withDidYouMeanFrom(String sent) {
        return withDidYouMean(ShDidYouMean.nearest(sent, allowed).orElse(null));
    }

    /** @param example a minimal correct payload or value (serialized as-is). */
    public ShProblem withExample(Object example) {
        return new ShProblem(slug, status, title, detail, fix, allowed, didYouMean, example, at);
    }

    /**
     * Points at the part of the request that failed, e.g. {@code ops[3]}.
     *
     * <p>The fifth teaching field, added when batch writes (SH81) made a request
     * capable of failing in one of many places. Without it a caller has to re-derive
     * <em>which</em> operation was rejected from the message text — a parse an agent
     * will get wrong — and the "one message, one fix" property of P6 quietly stops
     * holding as request bodies get bigger.</p>
     *
     * @param at a pointer into the request, in the sender's own vocabulary
     */
    public ShProblem withAt(String at) {
        return new ShProblem(slug, status, title, detail, fix, allowed, didYouMean, example, at);
    }

    public String slug() {
        return slug;
    }

    public HttpStatusCode status() {
        return status;
    }

    /** What the reader was told they might have meant, or {@code null} — SH602's strongest signal. */
    public String didYouMean() {
        return didYouMean;
    }

    /** What the refusal was about, which carries the reader's own wording. */
    public String title() {
        return title;
    }

    /**
     * Renders the RFC 9457 body. Blank/empty extensions are omitted entirely
     * rather than serialized as {@code null}/{@code []}, so the response carries
     * only what it actually teaches.
     */
    public ProblemDetail toProblemDetail() {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        if (hasText(detail)) {
            problem.setDetail(detail);
        }
        if (hasText(fix)) {
            problem.setProperty("fix", fix);
        }
        if (allowed != null && !allowed.isEmpty()) {
            problem.setProperty("allowed", new ArrayList<>(allowed));
        }
        if (hasText(didYouMean)) {
            problem.setProperty("didYouMean", didYouMean);
        }
        if (example != null) {
            problem.setProperty("example", example);
        }
        if (hasText(at)) {
            problem.setProperty("at", at);
        }
        return problem;
    }

    /** Convenience for the common {@code 400} case. */
    public static ShProblem badRequest(String slug, String title) {
        return of(slug, HttpStatus.BAD_REQUEST, title);
    }

    /** Convenience for the common {@code 409} case. */
    public static ShProblem conflict(String slug, String title) {
        return of(slug, HttpStatus.CONFLICT, title);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
