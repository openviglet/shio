/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentChanges;
import com.viglet.shio.persistence.model.history.ShHistory;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.history.ShHistoryRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * The change feed (SH88) — a resumable read over the SH51 audit trail.
 *
 * <h2>What it is for</h2>
 *
 * <p>Two jobs, and both of them are Block I's: a human's console edits have to flow
 * <em>back</em> into the file tree ({@code shio pull --content}), and a follow-up session
 * has to be able to read the delta instead of re-reading the world. Both need a position
 * that survives between calls, which is precisely what SH83's window could not give —
 * see {@link com.viglet.shio.api.agent.dto.ShAgentChanges} for why a timestamp alone is
 * not one.</p>
 *
 * <h2>The lag rule, and what it does not fix</h2>
 *
 * <p>Audit rows are written <b>inside</b> the writer's transaction (SH51 chose that
 * deliberately, so an audit row commits with the change it describes). The timestamp is
 * therefore assigned before the commit, and a slow transaction can commit an <em>older</em>
 * timestamp after a faster one has already been read — a reader whose cursor moved past it
 * would never see it. So the feed refuses to report anything younger than
 * {@code now - lagMillis}: by the time an entry is eligible, any transaction that had
 * already stamped an earlier timestamp has had that long to commit.</p>
 *
 * <p>This narrows the hole to transactions that take longer than the lag between
 * stamping and committing — a bulk apply of hundreds of posts can. It is stated in the
 * response {@code notes} rather than hidden, and the real fix (commit-ordered sequencing)
 * is its own task, because it changes how the audit row is written and not how it is
 * read.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>A site-scoped credential sees the trail of the sites it can read, and nothing else.
 * That is not a refinement of SH83's tool — that tool read the whole trail regardless of
 * the SH2 allow-list, so a scoped token could see the titles of content it could not
 * fetch. Both surfaces now route through here, which is why the leak closes for both.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentChangesService {

    private final ShHistoryRepository shHistoryRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShAgentSiteAccess siteAccess;
    private final ShAddressResolver shAddressResolver;
    private final ShAgentProperties properties;

    public ShAgentChangesService(ShHistoryRepository shHistoryRepository,
                                 ShPostUnifiedRepository shPostUnifiedRepository,
                                 ShAgentSiteAccess siteAccess,
                                 ShAddressResolver shAddressResolver,
                                 ShAgentProperties properties) {
        this.shHistoryRepository = shHistoryRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.siteAccess = siteAccess;
        this.shAddressResolver = shAddressResolver;
        this.properties = properties;
    }

    /**
     * Reads the feed.
     *
     * @param site  site name / id / {@code site:} address to scope to; {@code null} for
     *              every readable site
     * @param since a cursor from a previous call; {@code null} starts from a window
     * @param limit entries to return; {@code null} uses the configured default
     * @param ids   include object ids (SH121 default: off)
     */
    public ShAgentChanges changes(String site, String since, Integer limit, boolean ids) {
        ShAgentProperties.Changes config = properties.getChanges();
        int cap = limit == null || limit <= 0
                ? config.getDefaultLimit()
                : Math.min(limit, config.getMaxLimit());
        Instant ceiling = Instant.now().minusMillis(Math.max(0, config.getLagMillis()));

        Scope scope = scope(site);
        ShAgentChangeCursor from = since == null || since.isBlank()
                ? null
                : ShAgentChangeCursor.parse(since);

        // limit + 1 on the feed so `more` is a fact rather than an inference from a full
        // page — a full page that happens to be the last one would otherwise send the
        // caller back for an empty round trip every time.
        List<ShHistory> entries = from == null
                ? newestFirst(scope, ceiling, cap)
                : afterCursor(scope, ceiling, from, cap + 1);
        boolean more = from != null && entries.size() > cap;
        if (more) {
            entries = entries.subList(0, cap);
        }
        if (from == null) {
            // The window query returns newest first; the response is always oldest first
            // so the cursor is always the last row (see the DTO's "two modes" note).
            entries = new ArrayList<>(entries);
            Collections.reverse(entries);
        }

        List<ShAgentChanges.Change> changes = shape(entries, scope, ids);
        String cursor = entries.isEmpty()
                ? (from == null ? new ShAgentChangeCursor(ceiling, "").toString() : from.toString())
                : new ShAgentChangeCursor(last(entries).getDate(), last(entries).getId()).toString();

        return new ShAgentChanges(changes.size(), from == null, since, cursor, more, changes,
                notes(from == null, scope, ceiling, config, changes, cap));
    }

    // --- querying ---

    private List<ShHistory> newestFirst(Scope scope, Instant ceiling, int cap) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findWindow(ceiling, page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findWindowInSites(ceiling, scope.siteIds(), page);
    }

    private List<ShHistory> afterCursor(Scope scope, Instant ceiling, ShAgentChangeCursor from,
                                        int cap) {
        PageRequest page = PageRequest.of(0, cap);
        if (scope.unrestricted()) {
            return shHistoryRepository.findSince(ceiling, from.at(), from.id(), page);
        }
        return scope.siteIds().isEmpty()
                ? List.of()
                : shHistoryRepository.findSinceInSites(ceiling, scope.siteIds(), from.at(),
                        from.id(), page);
    }

    /**
     * Which sites this call may read.
     *
     * <p>{@code unrestricted} means "no allow-list applies", which is the console case and
     * the reason entries whose site could not be recorded are still visible there: an
     * admin reading their own instance's audit trail should not silently lose rows. A
     * token-scoped read never gets that fallback, because an unattributable row cannot be
     * proven to be inside the allow-list.</p>
     */
    private Scope scope(String site) {
        if (site != null && !site.isBlank()) {
            List<ShSite> sites = siteAccess.readable(site);
            return new Scope(false, sites.stream().map(ShSite::getId).toList(), byId(sites));
        }
        List<ShSite> readable = siteAccess.readable();
        boolean unrestricted = ShApiTokenContext.current().isEmpty();
        return new Scope(unrestricted, readable.stream().map(ShSite::getId).toList(), byId(readable));
    }

    private record Scope(boolean unrestricted, List<String> siteIds, Map<String, String> names) {
    }

    private static Map<String, String> byId(List<ShSite> sites) {
        Map<String, String> names = new HashMap<>();
        for (ShSite site : sites) {
            names.put(site.getId(), site.getName());
        }
        return names;
    }

    // --- shaping ---

    private List<ShAgentChanges.Change> shape(List<ShHistory> entries, Scope scope, boolean ids) {
        // One lookup per distinct object, not per row: a post that was created, updated
        // and published appears three times in a feed and has one address.
        Map<String, Optional<String>> addresses = new LinkedHashMap<>();
        List<ShAgentChanges.Change> changes = new ArrayList<>(entries.size());
        for (ShHistory entry : entries) {
            String objectId = entry.getShObject();
            Optional<String> address = objectId == null
                    ? Optional.empty()
                    : addresses.computeIfAbsent(objectId, this::addressOf);
            changes.add(new ShAgentChanges.Change(
                    entry.getAction(),
                    address.orElse(null),
                    entry.getDate() == null ? null : entry.getDate().toString(),
                    entry.getOwner(),
                    entry.getDescription(),
                    entry.getShSite() == null ? null : scope.names().get(entry.getShSite()),
                    ids ? objectId : null));
        }
        return List.copyOf(changes);
    }

    /** DRAFT-preferred, like every other agent read, so the address matches what a read returns. */
    private Optional<String> addressOf(String postId) {
        List<ShPostUnified> rows = shPostUnifiedRepository.findById(postId);
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .or(() -> rows.stream().findFirst())
                .map(row -> shAddressResolver.addressOf(row).toString());
    }

    /**
     * What the response cannot show. Every note is here because its absence would make a
     * true statement read as a stronger one (the SH103 rule, and SH138's).
     */
    private List<String> notes(boolean window, Scope scope, Instant ceiling,
                              ShAgentProperties.Changes config,
                              List<ShAgentChanges.Change> changes, int cap) {
        List<String> notes = new ArrayList<>();
        if (window) {
            notes.add("window: the most recent " + cap + " entries at most, not a delta — pass "
                    + "since=<cursor> to get everything after this position");
        }
        if (config.getLagMillis() > 0) {
            notes.add("entries after " + ceiling + " are held back for " + config.getLagMillis()
                    + "ms so a transaction still committing cannot land behind the cursor; a "
                    + "write that takes longer than that can still be reported late");
        }
        if (!scope.unrestricted()) {
            notes.add("scoped to " + scope.siteIds().size() + " readable site(s); entries for "
                    + "other sites, and entries whose site was not recorded, are not shown");
        }
        if (changes.stream().anyMatch(change -> change.address() == null)) {
            notes.add("an entry with no address refers to an object that no longer exists — "
                    + "usually its own deletion; its title is in `what`");
        }
        return List.copyOf(notes);
    }

    private static ShHistory last(List<ShHistory> entries) {
        return entries.get(entries.size() - 1);
    }
}
