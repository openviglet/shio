/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.error;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * Nearest-match suggestion for a rejected value (SH80) — the {@code didYouMean}
 * half of a teaching error.
 *
 * <p>Matching is case- and separator-insensitive, which is what actually goes
 * wrong in practice: a caller writes {@code richText} or {@code HTML editor} for
 * {@code HTML Editor}. Comparison therefore runs over a normalized form (lower
 * case, spaces/underscores/hyphens stripped) while the <em>original</em>
 * candidate string is returned — the caller needs the exact accepted spelling.</p>
 *
 * <p>A suggestion is only offered when it is close enough to be more useful than
 * misleading: the edit distance must not exceed {@code max(2, len/3)} of the
 * normalized sent value. Ties resolve to the first candidate in the given order,
 * so the result is deterministic for a deterministic allow-list (P5).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShDidYouMean {

    private static final LevenshteinDistance DISTANCE = LevenshteinDistance.getDefaultInstance();

    private ShDidYouMean() {
        // utility
    }

    /**
     * @param sent       the value that was rejected
     * @param candidates the accepted values, in a deterministic order
     * @return the nearest candidate, or empty when nothing is close enough
     */
    public static Optional<String> nearest(String sent, Collection<String> candidates) {
        if (sent == null || sent.isBlank() || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        String needle = normalize(sent);
        if (needle.isEmpty()) {
            return Optional.empty();
        }
        int threshold = Math.max(2, needle.length() / 3);
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            int distance = DISTANCE.apply(needle, normalize(candidate));
            // Strictly-less keeps the first candidate on a tie (deterministic).
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return bestDistance <= threshold ? Optional.ofNullable(best) : Optional.empty();
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]", "");
    }
}
