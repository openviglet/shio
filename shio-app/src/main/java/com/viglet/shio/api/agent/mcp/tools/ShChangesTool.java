/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentSiteAccess;
import com.viglet.shio.api.agent.ShTerse;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_changes} (SH83) — what has happened to this instance's content lately.
 *
 * <h2>What this is and is not</h2>
 *
 * <p>It is a read over the SH51 audit trail: the last N content transitions, who made
 * them, and — because SH75 stamps the agent session into the actor — which run each one
 * came from. That is enough to answer the question an agent actually asks at the start
 * of a session: <em>did a human change anything while I was away?</em></p>
 *
 * <p>It is <b>not</b> SH88's change feed. There is no cursor, so "everything since I
 * last looked" is not expressible yet and a caller must fall back on reading a window
 * and comparing. Building a fake cursor over {@code date} here would be worse than not
 * having one: it would look resumable and silently drop entries that share a timestamp.
 * SH88 adds the real one; until then this tool is honest about being a window.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShChangesTool implements ShMcpTool {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 200;

    private final ShHistoryRepository shHistoryRepository;
    private final ShAgentSiteAccess siteAccess;

    public ShChangesTool(ShHistoryRepository shHistoryRepository, ShAgentSiteAccess siteAccess) {
        this.shHistoryRepository = shHistoryRepository;
        this.siteAccess = siteAccess;
    }

    @Override
    public String name() {
        return "shio_changes";
    }

    @Override
    public String description() {
        return "Recent content changes: what was created, updated, published or deleted, by "
                + "whom, and from which agent session. Read it at the start of a session to see "
                + "what a human changed since you last worked here. This is a window, not a "
                + "cursor — there is no since= yet.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "limit", ShMcpTool.integer("How many entries (default 25, max 200).")),
                List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public String call(JsonNode arguments) {
        Integer requested = ShMcpArgs.integer(arguments, "limit");
        int limit = requested == null || requested <= 0 ? DEFAULT_LIMIT : Math.min(requested, MAX_LIMIT);
        String site = ShMcpArgs.string(arguments, "site");

        List<ShHistory> entries;
        if (site == null) {
            entries = shHistoryRepository.findRecent(PageRequest.of(0, limit));
        } else {
            // Resolved through the shared allow-list, so `site=` cannot be used to read
            // the audit trail of a site this credential cannot see.
            ShSite resolved = siteAccess.require(site);
            entries = shHistoryRepository.findRecentBySite(resolved.getId(), PageRequest.of(0, limit));
        }

        StringBuilder out = new StringBuilder(ShTerse.line("changes")
                .word(entries.size())
                .attr("site", site)
                .build());
        for (ShHistory entry : entries) {
            out.append(ShTerse.line("event")
                    .word(entry.getAction())
                    .attr("at", entry.getDate())
                    .attr("by", entry.getOwner())
                    .attr("what", entry.getDescription())
                    .build());
        }
        return out.toString();
    }
}
