/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * The one writer of "which friendly URL is free in this site" (SH651).
 *
 * <h2>Why this is a component and not a private method</h2>
 *
 * <p>The rule was written once, for the copy path (SH202), and lived as
 * {@code ShObjectWriteService.freeNaming}'s second half. When SH651 found that
 * {@code ShPostUnifiedWriteService.create} assigned no {@code furl} at all — every folder,
 * site and copy write derives one, post create was the single write that did not — the
 * choice was to reimplement the loop beside it or to have one writer. Four times already a
 * console copy of a shared verb was the careless one, which is the whole subject of SH245;
 * making the fifth copy to fix the fourth would have been a poor trade.
 *
 * <h2>What "free" means, and what it deliberately does not</h2>
 *
 * <p>Free is checked across <b>every spelling</b> a tolerant reader resolves (SH583) and
 * across <b>both state rows</b>: a URL held only by a {@code PUBLISHED} row is taken, even
 * though no draft mentions it.
 *
 * <p>A <b>blank</b> URL is free. An uploaded file post never gets one (SH38, which is why
 * SH172 needed the {@code id:} address fallback), so blank is a legitimate shared value
 * rather than a collision — and a post whose title is blank is left blank rather than given
 * an invented address.
 *
 * <p>This says nothing about <b>titles</b>. {@code furl} and title are separate namespaces
 * (SH79), and SH139's point is that renaming a post does not re-derive its URL; the copy
 * path numbers both with two independent counters for that reason, and the title half stays
 * where it is because only a copy needs it.
 */
@Component
public class ShFurlNaming {

    private static final List<ShPostState> ALL_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShFurlNaming(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * The first free URL derived from {@code rawTitle}: the slug, then {@code -2},
     * {@code -3}, and so on.
     *
     * <p>Terminates because every {@code n} yields a distinct candidate while the set of
     * taken URLs is finite. The bound is the number of posts in the site already answering
     * at that slug, which is the honest one rather than an invented ceiling.</p>
     *
     * @return the free URL, or blank where there is no title to derive one from
     */
    public String freeFurl(String siteId, String rawTitle) {
        String base = rawTitle == null ? "" : ShURLFormatter.format(rawTitle);
        if (base == null || base.isBlank()) {
            return "";
        }
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? base : base + "-" + n;
            if (isFree(siteId, candidate)) {
                return candidate;
            }
        }
    }

    /** Whether no live post in {@code siteId} already answers at {@code furl}. */
    public boolean isFree(String siteId, String furl) {
        if (siteId == null || furl == null || furl.isBlank()) {
            return true;
        }
        // SH583 — every spelling: checking only the exact one would derive an "unused" URL
        // that a tolerant reader already resolves to something else.
        for (String candidate : ShFurlSpellings.candidates(furl)) {
            for (ShPostState state : ALL_STATES) {
                if (shPostUnifiedRepository
                        .findByShSite_IdAndFurlAndState(siteId, candidate, state).isPresent()) {
                    return false;
                }
            }
        }
        return true;
    }
}
