/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * SH1217 — the sites listing, with each site's pages by state and its last change.
 *
 * <p>Three statements whatever the number of sites: the sites, one grouped count over the unified
 * post table, and one newest-row read over the audit trail. Both grouped reads key on the site's
 * <b>id</b>, never a further field of it, which is the {@code @TenantId} JOINED-join trap agents.md
 * names. {@code ShSiteListingCostTest} counts the statements at 15 and at 400 sites.</p>
 */
@Service
public class ShSiteListingService {

    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShHistoryRepository shHistoryRepository;

    public ShSiteListingService(ShSiteRepository shSiteRepository,
                                ShPostUnifiedRepository shPostUnifiedRepository,
                                ShHistoryRepository shHistoryRepository) {
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shHistoryRepository = shHistoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ShSiteListing> list() {
        Map<String, long[]> counts = new HashMap<>();
        for (Object[] row : shPostUnifiedRepository.countDistinctBySiteIdPerState()) {
            long[] byState = counts.computeIfAbsent((String) row[0], id -> new long[2]);
            byState[row[1] == ShPostState.PUBLISHED ? 0 : 1] += ((Number) row[2]).longValue();
        }
        Map<String, ShSiteListing.LastChange> newest = new HashMap<>();
        for (ShHistory row : shHistoryRepository.findNewestPerSite()) {
            // Ordered by id within a site, so the first row kept is the same one on every read.
            newest.putIfAbsent(row.getShSite(), new ShSiteListing.LastChange(row.getDate(),
                    row.getOwner(), isAgent(row.getAgentSessionId())));
        }
        return shSiteRepository.findAllForListing().stream().map(site -> {
            long[] byState = counts.get(site.getId());
            return new ShSiteListing(site,
                    byState == null ? ShSiteListing.Pages.NONE
                            : new ShSiteListing.Pages(byState[0], byState[1]),
                    newest.get(site.getId()));
        }).toList();
    }

    /** {@code ShHistoryRepository.AGENT_SESSION}, in Java: a session, and not a console one. */
    static boolean isAgent(String session) {
        return session != null && !session.startsWith("console:");
    }
}
