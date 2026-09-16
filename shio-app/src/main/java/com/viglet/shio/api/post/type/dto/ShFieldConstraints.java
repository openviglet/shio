/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * What a field will accept, said by the model (SH1010).
 *
 * <h2>The defect</h2>
 *
 * <p>A field could declare one rule — {@code required} — and nothing else. So a slug field accepted
 * spaces, a rating accepted eleven, a locale code accepted {@code "english"}, and every rule beyond
 * presence lived in somebody's head: enforced by whoever remembered it, on the surface they happened
 * to be writing, and nowhere on the other three.</p>
 *
 * <h2>Additive, so nothing existing moves</h2>
 *
 * <p>Every member is nullable and an absent block means no constraints. A post-type written before
 * this round-trips byte for byte, which is the property {@code shio pull}/{@code push} is built on
 * (P5): the canonical serialization materializes every key a field <em>has</em>, so a key that is
 * absent has to stay absent rather than appear as {@code null}.</p>
 *
 * <h2>What is here, and what is deliberately not</h2>
 *
 * <p>These are the kinds that can be decided by looking at <b>one value</b>. {@code unique} needs a
 * query across the site and {@code default} is a write-time fill rather than a check, so both are a
 * slice of their own — a rule evaluated per value and a rule that reads other rows are different
 * mechanisms, and shipping them together would hide that behind one word.</p>
 *
 * @param minLength         shortest accepted text, in characters
 * @param maxLength         longest accepted text, in characters
 * @param pattern           a regular expression the whole value must match
 * @param patternMessage    what to say when it does not — a pattern quoted back at a curator is a
 *                          refusal only its author can act on, which is P6's whole complaint
 * @param min               smallest accepted number
 * @param max               largest accepted number
 * @param values            the accepted values, when the field is a closed vocabulary
 * @param minItems          fewest accepted entries, for a field that holds a list
 * @param maxItems          most accepted entries
 * @param recommendedLength advisory only: reported by {@code verify} and shown in the form, and it
 *                          never refuses a write. A title over sixty characters is a judgement about
 *                          search results, not an error, and a rule that refused it would teach
 *                          people to raise the number rather than shorten the title
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShFieldConstraints(
        Integer minLength,
        Integer maxLength,
        String pattern,
        String patternMessage,
        Double min,
        Double max,
        List<String> values,
        Integer minItems,
        Integer maxItems,
        Integer recommendedLength) {

    /** Nothing declared — which is what an absent block means, and what most fields have. */
    public static final ShFieldConstraints NONE =
            new ShFieldConstraints(null, null, null, null, null, null, null, null, null, null);

    /**
     * Whether this block says anything at all.
     *
     * <p>Used to decide whether to <em>store</em> one: a block whose every member is absent is
     * written as no block, so a field that had its constraints cleared serializes back to what it
     * was before anybody added them.</p>
     */
    public boolean declaresNothing() {
        return minLength == null && maxLength == null && pattern == null && patternMessage == null
                && min == null && max == null && (values == null || values.isEmpty())
                && minItems == null && maxItems == null && recommendedLength == null;
    }
}
