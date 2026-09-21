/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.site.ShSiteCoverage.Cell;
import com.viglet.shio.api.site.ShSiteCoverage.Count;
import com.viglet.shio.api.site.ShSiteCoverage.Group;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.ShDocumentDigest;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1131 — what a site has in each of its languages, and what it has not.
 *
 * <h2>Two queries, whatever the site holds</h2>
 *
 * <p>The question is per <em>group</em> and the answer is per <em>language</em>, which is the shape
 * that tempts a lookup per group — a site with four hundred pages then asks four hundred times to
 * draw one table, and the surface that was supposed to make the backlog visible is the one nobody
 * opens. So: one projection of the site's rows, and one read of the sources' documents, only for
 * the groups where some member carries an SH1058 stamp. A site whose translations predate that
 * column reads no documents at all.</p>
 *
 * <h2>The rows are the answer and the totals are derived from them</h2>
 *
 * <p>Not two queries answering one question: a summary counted separately from the list it
 * summarises is how a board comes to say seven and show six. The totals are the same pass.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShSiteCoverageService {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteLocaleService shSiteLocaleService;
    private final ObjectMapper objectMapper;

    public ShSiteCoverageService(ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShSiteLocaleService shSiteLocaleService,
                                 ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteLocaleService = shSiteLocaleService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param page zero-based; the rows are ordered by title so a board pages deterministically (P5)
     */
    @Transactional(readOnly = true)
    public ShSiteCoverage of(ShSite site, int page, int size) {
        ShSiteLocales policy = shSiteLocaleService.of(site);
        List<String> locales = new ArrayList<>(policy.enabled());
        Map<String, Map<String, Row>> byGroup = rowsByGroup(site);
        Map<String, String> sourceDigests = sourceDigests(byGroup);

        List<Group> groups = new ArrayList<>();
        Map<String, int[]> tally = new LinkedHashMap<>();
        locales.forEach(locale -> tally.put(locale, new int[3]));
        for (Map.Entry<String, Map<String, Row>> entry : byGroup.entrySet()) {
            Row source = entry.getValue().values().stream()
                    .filter(row -> row.id().equals(entry.getKey()))
                    .findFirst()
                    .orElseGet(() -> entry.getValue().values().iterator().next());
            Map<String, Cell> cells = new LinkedHashMap<>();
            for (String locale : locales) {
                Row member = entry.getValue().get(locale);
                String state = member == null ? ShSiteCoverage.MISSING
                        : stale(member, sourceDigests.get(entry.getKey()))
                                ? ShSiteCoverage.STALE : ShSiteCoverage.CURRENT;
                cells.put(locale, new Cell(state,
                        member == null ? null : address(site, member)));
                int[] counts = tally.get(locale);
                counts[ShSiteCoverage.CURRENT.equals(state) ? 0
                        : ShSiteCoverage.STALE.equals(state) ? 1 : 2]++;
            }
            groups.add(new Group(address(site, source), source.title(), cells));
        }
        groups.sort(Comparator.comparing((Group group) -> group.title() == null ? "" : group.title())
                .thenComparing(Group::address));

        int from = Math.min(Math.max(page, 0) * Math.max(size, 1), groups.size());
        int to = Math.min(from + Math.max(size, 1), groups.size());
        Map<String, Count> totals = new LinkedHashMap<>();
        tally.forEach((locale, counts) ->
                totals.put(locale, new Count(counts[0], counts[1], counts[2])));
        return new ShSiteCoverage(site.getName(), List.copyOf(locales),
                List.copyOf(groups.subList(from, to)), totals, Math.max(page, 0), Math.max(size, 1),
                groups.size());
    }

    /** The site's rows, draft-preferred, as group → locale → row. */
    private Map<String, Map<String, Row>> rowsByGroup(ShSite site) {
        Map<String, Map<String, Row>> byGroup = new LinkedHashMap<>();
        for (Object[] row : shPostUnifiedRepository.findCoverageRowsBySiteId(site.getId())) {
            String group = (String) row[0];
            String id = (String) row[1];
            String locale = (String) row[2];
            if (group == null || locale == null) {
                // A template carries no locale and belongs to no group's board (SH459's rule, one
                // surface along): it is not content anybody translates.
                continue;
            }
            Row candidate = new Row(id, locale, (String) row[3], (String) row[4], (String) row[5],
                    (ShPostState) row[6]);
            byGroup.computeIfAbsent(group, key -> new LinkedHashMap<>())
                    .merge(locale, candidate, ShSiteCoverageService::draftPreferred);
        }
        return byGroup;
    }

    /** The current digest of each group's source, for the groups where staleness can be known. */
    private Map<String, String> sourceDigests(Map<String, Map<String, Row>> byGroup) {
        Set<String> sources = new LinkedHashSet<>();
        byGroup.forEach((group, members) -> {
            if (members.values().stream().anyMatch(row -> row.sourceDigest() != null)) {
                sources.add(group);
            }
        });
        if (sources.isEmpty()) {
            return Map.of();
        }
        Map<String, Object[]> best = new LinkedHashMap<>();
        for (Object[] row : shPostUnifiedRepository.findDocumentsByIdIn(sources)) {
            best.merge((String) row[0], row, (kept, other) ->
                    kept[1] == ShPostState.DRAFT ? kept : other);
        }
        Map<String, String> digests = new LinkedHashMap<>();
        best.forEach((id, row) -> digests.put(id, ShDocumentDigest.of((String) row[2], objectMapper)));
        return digests;
    }

    private static boolean stale(Row member, String sourceDigest) {
        return member.sourceDigest() != null && sourceDigest != null
                && !sourceDigest.equals(member.sourceDigest());
    }

    private static Row draftPreferred(Row kept, Row other) {
        return kept.state() == ShPostState.DRAFT ? kept : other;
    }

    private static String address(ShSite site, Row row) {
        return ShAddress.post(site.getName(), row.furl()).toString();
    }

    /** One row of the projection, named. */
    private record Row(String id, String locale, String furl, String title, String sourceDigest,
                       ShPostState state) {
    }
}
