/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

/**
 * A response's token allowance (SH78) — the mechanism behind design law
 * <b>P3: tokens are a measured budget</b>.
 *
 * <p>Token count is <em>estimated</em>, not measured: a server cannot know the
 * caller's tokenizer, so this uses the conventional ≈4-characters-per-token
 * heuristic. That is accurate enough for its purpose — bounding an unbounded
 * section — and every value derived from it is labelled {@code est} on the wire so
 * no caller mistakes it for a guarantee.</p>
 *
 * <p><b>What the budget may and may not truncate.</b> It gates only the section
 * whose size is unbounded (the sitemap). Bounded sections — the instance header,
 * the content model, the conventions — are always emitted in full, because a
 * <em>partial model</em> is not a smaller answer, it is a wrong one: an agent that
 * sees 6 of 9 post-types will confidently invent the missing three. A caller that
 * cannot afford the model drops it explicitly with {@code include=} instead.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTokenBudget {

    /** Conventional characters-per-token ratio for English + punctuation. */
    private static final int CHARS_PER_TOKEN = 4;

    private final int limitTokens;
    private int spentChars;
    private boolean exhausted;

    public ShTokenBudget(int limitTokens) {
        this.limitTokens = Math.max(limitTokens, 0);
    }

    /** @return the estimated token count of {@code text}, rounded up. */
    public static int estimate(CharSequence text) {
        return text == null ? 0 : estimateChars(text.length());
    }

    /** @return the estimated token count of a payload of {@code chars} characters. */
    public static int estimateChars(int chars) {
        return chars <= 0 ? 0 : (chars + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    /**
     * Charges {@code chunk} against the budget only if it fits.
     *
     * @return {@code true} when the chunk was charged and may be emitted;
     *         {@code false} when emitting it would exceed the limit — the caller
     *         must then stop and record an explicit truncation marker, never
     *         silently drop the rest
     */
    public boolean tryAdd(CharSequence chunk) {
        int projected = spentChars + (chunk == null ? 0 : chunk.length());
        // An unbounded budget still *measures*. Short-circuiting here without
        // charging is what made `estTokens` under-report the whole sitemap on any
        // `budget=0` call — the reported cost must be the real cost, or the P3
        // instrumentation (and the SH112 benchmark reading it) is worthless.
        if (limitTokens > 0 && estimateChars(projected) > limitTokens) {
            exhausted = true;
            return false;
        }
        spentChars = projected;
        return true;
    }

    /** Charges {@code chunk} unconditionally — for sections that must be complete. */
    public void add(CharSequence chunk) {
        spentChars += chunk == null ? 0 : chunk.length();
    }

    public int spentTokens() {
        return estimateChars(spentChars);
    }

    public int limitTokens() {
        return limitTokens;
    }

    /** @return {@code true} when a {@link #tryAdd} was refused, i.e. output was cut. */
    public boolean exhausted() {
        return exhausted;
    }

    /**
     * @return {@code true} when the mandatory sections alone overran the limit —
     *         reported rather than hidden, so the caller can raise the budget or
     *         narrow {@code include=}
     */
    public boolean overBudget() {
        return limitTokens > 0 && spentTokens() > limitTokens;
    }

}
