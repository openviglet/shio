/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * SH1131 — a site's translation coverage: a row per page, a cell per language.
 *
 * <h2>Why the rows and not only the counts</h2>
 *
 * <p>"73% translated" is a number nobody can act on. What a curator or an agent needs is the work
 * list — which page, in which language, and whether it is missing or merely behind — so the rows
 * are the answer and {@link #totals} is derived from them rather than fetched separately. That also
 * makes the two impossible to disagree: a summary read from one query and a list from another is
 * how a board comes to say seven and show six.</p>
 *
 * @param site    the site's name, as an SH79 address uses it
 * @param locales the languages the site offers, declared or derived (SH1056) — the board's columns
 * @param rows    one per translation group, in a stable order, paged
 * @param totals  per locale, how many groups are current, stale or missing, over the whole site
 * @param page    zero-based index of the page {@link #rows} holds
 * @param size    rows per page
 * @param total   how many groups the site has in all
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShSiteCoverage(String site, List<String> locales, List<Group> rows,
                             Map<String, Count> totals, int page, int size, long total) {

    /**
     * One translation group — the page, in every language the site offers.
     *
     * @param address the group's source, which is the page a translation is made from
     * @param id      the source's id, which is what the console routes by and what a translation
     *               is created from; the address is what an agent addresses it by
     * @param title   the source's title, so a board row reads as a page rather than as a URL
     * @param cells   locale → what exists in it; a locale with no entry is missing
     */
    public record Group(String address, String id, String title, Map<String, Cell> cells) {
    }

    /**
     * What one language holds for one page.
     *
     * <p>Both identifiers, and the reason is that the two readers of this board address a page
     * differently: the console opens {@code /content/post/{id}} and an agent writes to
     * {@code post:<site>/<url>}. A board carrying only one of them sends the other reader back for
     * a lookup per cell.</p>
     *
     * @param state     {@code current}, {@code stale} (SH1058) or {@code missing}
     * @param address   the translation's own address, absent when there is nothing to open
     * @param id        the translation's id, absent for the same reason
     * @param requested SH1062 — {@code true} while the translation carries an open request note,
     *                  which is how the board asks the connected agent to write or refresh it; absent
     *                  otherwise, so a board with no requests costs no bytes for the word
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cell(String state, String address, String id, Boolean requested) {

        /** A cell nobody has asked about. */
        public Cell(String state, String address, String id) {
            this(state, address, id, null);
        }

        /** The same cell, marked as asked for. */
        Cell asked() {
            return new Cell(state, address, id, Boolean.TRUE);
        }
    }

    /** How many groups are in each state, for one language. */
    public record Count(int current, int stale, int missing) {
    }

    /** The three words a cell may carry, so no surface spells them for itself. */
    public static final String CURRENT = "current";
    public static final String STALE = "stale";
    public static final String MISSING = "missing";
}
