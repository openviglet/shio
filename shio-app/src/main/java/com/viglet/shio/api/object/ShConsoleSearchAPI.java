/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.object.dto.ShConsoleSearch;
import com.viglet.shio.api.query.ShContentQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The console's content search (SH1027).
 *
 * <p>Separate from {@code /api/v2/object/**} because that root is keyed by an object id and this
 * read has no object; separate from {@code /api/v2/cda/search} because that is the delivery API,
 * which serves what a <em>visitor</em> may see. A curator is not a visitor: they need the drafts,
 * and they need only the rows their ACL admits.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/content")
@Tag(name = "Content", description = "Console content search")
public class ShConsoleSearchAPI {

    /** This listing's own parameters, outside the shared content-query vocabulary. */
    private static final List<String> SEARCH_OWN = List.of("limit", "after", "trash");

    /** Where the words this listing does not serve do work. */
    private static final String SEARCH_ELSEWHERE =
            "This search pages with `limit` and `after`, and its cursor is an address — so it "
                    + "cannot be ordered by anything else. `sort`, `page`, `size`, `folderPage` "
                    + "and `folderSize` are served by the folder listing, "
                    + "GET /api/v2/object/{id}/list.";

    private final ShConsoleSearchService shConsoleSearchService;

    public ShConsoleSearchAPI(ShConsoleSearchService shConsoleSearchService) {
        this.shConsoleSearchService = shConsoleSearchService;
    }

    /**
     * Find content this reader may open, in any state.
     *
     * <p>The filters are the one content-query vocabulary (SH1022), spelled here as they are on
     * {@code /agent/find}, in {@code shio_find} and on the folder listing. {@code state} absent
     * means <b>both</b>, which is the whole point: the delivery search this replaced answered
     * published-only, so a curator could not find the drafts an agent had just written.</p>
     *
     * @param params the request's own parameters, which is how an unknown one is refused at all:
     *               Spring binds what it recognises and drops the rest, so a typo would come back
     *               as every row with a 200
     * @param limit  row cap
     * @param after  the previous page's cursor
     * @param trash  the trash instead of the live listing (SH222's read, on this surface too)
     */
    @GetMapping("/search")
    @Operation(summary = "Search content in any state, filtered to what the reader may open")
    public ShConsoleSearch search(
            @RequestParam Map<String, String> params,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "after", required = false) String after,
            @RequestParam(name = "trash", required = false, defaultValue = "false") boolean trash) {

        ShContentQuery query = ShContentQuery.of(params, ShContentQuery.FILTERS, SEARCH_OWN,
                SEARCH_ELSEWHERE);
        return shConsoleSearchService.search(query, limit, after, trash);
    }
}
