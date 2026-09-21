/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.turing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * SH1063 — the routing table, as one read every surface can show.
 *
 * <p>One service and not a computation per surface: the console card, the {@code shio://index}
 * resource and {@code shio index status} answer the same question, and three readings of
 * "which search site does this one go to" would be three chances to disagree about the
 * case-insensitive match {@link ShTuringIndexService#snSiteFor} makes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShTuringIndexStatusService {

    private final ShTuringIndexService shTuringIndexService;
    private final ShTuringProperties properties;
    private final ShSiteRepository shSiteRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShAgentIncidentLog incidentLog;

    public ShTuringIndexStatusService(ShTuringIndexService shTuringIndexService,
                                      ShTuringProperties properties,
                                      ShSiteRepository shSiteRepository,
                                      ShPostUnifiedRepository shPostUnifiedRepository,
                                      ShAgentIncidentLog incidentLog) {
        this.shTuringIndexService = shTuringIndexService;
        this.properties = properties;
        this.shSiteRepository = shSiteRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.incidentLog = incidentLog;
    }

    /** Every site this instance holds, with the search site it routes to. */
    @Transactional(readOnly = true)
    public ShTuringIndexStatus status() {
        List<ShTuringIndexStatus.Route> routes = new ArrayList<>();
        Map<String, Long> published = publishedBySite();
        for (ShSite site : shSiteRepository.findAll()) {
            routes.add(new ShTuringIndexStatus.Route(site.getName(),
                    shTuringIndexService.snSiteFor(site.getName()).orElse(null),
                    published.getOrDefault(site.getId(), 0L)));
        }
        routes.sort(Comparator.comparing(route -> route.site() == null ? "" : route.site()));
        return new ShTuringIndexStatus(shTuringIndexService.active(), properties.getUrl(),
                blankToNull(properties.getDefaultSite()), List.copyOf(routes), failures());
    }

    /** How many published pages each site has, in one grouped query rather than one per site. */
    private Map<String, Long> publishedBySite() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Object[] row : shPostUnifiedRepository.countDistinctBySiteIdAndState(
                ShPostState.PUBLISHED)) {
            counts.put((String) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Indexing failures the incident window still holds — a count, since the detail is per page. */
    private int failures() {
        int failures = 0;
        for (ShAgentIncidentLog.Incident incident : incidentLog.recent(500)) {
            if (incident.kind() == ShAgentIncidentLog.Kind.INDEX) {
                failures++;
            }
        }
        return failures;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
