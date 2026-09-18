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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.acl.ShContentAclService;
import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.object.dto.ShConsoleSearch;
import com.viglet.shio.api.query.ShContentQuery;

/**
 * The console's search, over the read the agent already has (SH1027).
 *
 * <h2>What was wrong</h2>
 *
 * <p>The search page called {@code GET /api/v2/cda/search} — the <b>delivery</b> API, whose state
 * is {@code PUBLISHED} unless the caller holds a preview token, and the console has never sent
 * one. So an agent could find the drafts it had just written and the curator reviewing them could
 * not, which is exactly backwards: searching for what the run made is the first thing a curator
 * does after an agent run.</p>
 *
 * <p>Two more rode along. The CDA enforces a <em>token's</em> site allow-list and knows nothing
 * about {@code sh_content_acl}, so on an instance with an ACL set the console listed titles from
 * folders the reader could not open. And its folder scope was direct children while the agent's
 * is recursive, so the same words meant two different questions.</p>
 *
 * <h2>Why a composition and not a search of its own</h2>
 *
 * <p>P10. {@code /agent/find} already searches both states, already scopes a folder subtree, and
 * since SH1022 already speaks the vocabulary all four surfaces speak. A second search would be a
 * third implementation of "what matches", and the two would come to disagree about it the way the
 * console's own filter and the delivery search once did (SH240). What this adds is the one thing
 * the agent surface does not have and must not: a <b>per-object permission filter</b>, because a
 * console session is a person with an ACL and an API credential is a site allow-list.</p>
 *
 * <p>The filter runs <em>after</em> the cap rather than as a predicate, which is the honest order
 * and not a shortcut: the ACL lives in a table keyed by object with inheritance up the folder
 * chain, so pushing it into the query would be the third thing this file is not allowed to become.
 * The cost of that choice is that a page can come back short — which is why it comes back with a
 * count of what was removed.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShConsoleSearchService {

    private final ShAgentReadService shAgentReadService;
    private final ShContentAclService shContentAclService;

    public ShConsoleSearchService(ShAgentReadService shAgentReadService,
                                  ShContentAclService shContentAclService) {
        this.shAgentReadService = shAgentReadService;
        this.shContentAclService = shContentAclService;
    }

    /**
     * Search, as this reader may see it.
     *
     * @param query the SH1022 vocabulary. {@code state} absent means <b>both</b> — the default
     *              this endpoint exists to provide, and the difference between a reviewer who can
     *              find an agent's drafts and one who cannot
     * @param trash the listing's other half, spelled as {@code /agent/find} spells it: the trash
     *              is a list of posts, and this is a call that lists posts
     */
    @Transactional(readOnly = true)
    public ShConsoleSearch search(ShContentQuery query, Integer limit, String after,
                                  boolean trash) {
        // `ids` on: the console opens a post by id, and an address it would have to resolve again
        // is a second round trip for something this answer already knows.
        ShAgentFound found = shAgentReadService.find(query, limit, true, after, trash, null, null);
        List<ShAgentFound.Row> visible = found.results().stream()
                .filter(row -> row.id() == null || shContentAclService.canRead(row.id()))
                .toList();
        return new ShConsoleSearch(visible, visible.size(),
                found.results().size() - visible.size(), found.truncated(), found.cursor());
    }
}
