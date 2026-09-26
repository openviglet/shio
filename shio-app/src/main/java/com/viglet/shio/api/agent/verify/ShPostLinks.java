/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.verify;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Who links to a URL, answered from authored markup (SH140).
 *
 * <h2>The thing an SH79 address cannot fix</h2>
 *
 * <p>The reference index ({@code sh_object_reference}, SH1034) is id-keyed, so a reference stored <em>as</em> a reference survives a
 * move: the row points at the post whatever its address becomes. A URL typed into another post's
 * rich text is not that. It is a string in a field, it names the address rather than the object,
 * and {@code post.move} rewrites the address underneath it — so a move breaks pages nowhere near
 * the one that moved. That will always be possible: the broken thing is authored prose rather than
 * a modelled relationship.</p>
 *
 * <h2>Why the answer belongs to the write and not to the lint (SH140's open question)</h2>
 *
 * <p>The obvious home is {@code verify}, which already reports {@code link-unresolved} and already
 * holds a whole-site URL index. It cannot work there, and the reason is worth stating: <b>by the
 * time the lint runs, the old address is gone.</b> A path that matches no published URL is
 * indistinguishable from a route the front end owns — which is exactly why that rule is a
 * <em>warning</em> whose fix offers four possibilities. The move is the one moment the CMS knows
 * the answer: it holds the old path, it knows a live page served it, and it knows where that page
 * went. So a stale inbound link is not a guess here but a fact with a fix, and it is reported on
 * the row of the op that caused it.</p>
 *
 * <h2>Narrow in SQL, confirm in the markup</h2>
 *
 * <p>The candidate query is the CDA's own full-text predicate (SH52/SH131 — through the
 * {@code search*} entry point, never the raw {@code …Like} query), which is a portable {@code LIKE}
 * over title, summary and the attribute {@code data}. That is a <em>substring</em> match, so on its
 * own it would claim {@code /blog/old} is linked by a page that mentions {@code /blog/older} or that
 * merely names the path in prose. Every candidate is therefore re-read with {@link ShHtmlOutline},
 * the same scanner {@code verify} extracts links with, and kept only when a real {@code href}
 * normalizes to the path. One extraction, two callers — a second definition of "what counts as a
 * link" is how the two surfaces would come to disagree.</p>
 *
 * <p><b>Published rows only</b>, which is the rule {@code verify} already applies to markup: a
 * draft's broken link is work in progress, and a visitor cannot reach it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShPostLinks {

    /**
     * Widgets whose stored value is markup worth scanning for links and images.
     *
     * <p>Public and here rather than private to the lint, because SH140 gave it a second reader and
     * the set is the definition of "a field that can hold a link".</p>
     */
    public static final Set<String> MARKUP_WIDGETS =
            Set.of(ShSystemWidget.HTML_EDITOR, ShSystemWidget.ACE_HTML);

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShPostLinks(ShPostUnifiedRepository shPostUnifiedRepository, ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * What links to a path, and how many did — a cut this type can state (SH138's rule).
     *
     * @param addresses the SH79 addresses, sorted and capped
     * @param found     how many there are, so a capped list never reads as the whole answer
     */
    public record Inbound(List<String> addresses, int found) {

        static final Inbound NONE = new Inbound(List.of(), 0);

        public boolean any() {
            return found > 0;
        }

        public boolean truncated() {
            return found > addresses.size();
        }
    }

    /**
     * The published posts of {@code site} whose markup links to {@code path}, as SH79 addresses.
     *
     * <p>Sorted, because this rides a write response an agent may diff (P5), and capped, because a
     * shared page can be linked from every post on a site while the caller asked to move one.</p>
     *
     * @param excludePostId the post being moved: a page that links to its own old URL is the same
     *                      edit as the move, and reporting it would send the agent to fix the thing
     *                      it just asked for
     */
    public Inbound linkingTo(ShSite site, String path, String excludePostId, int cap) {
        List<String> addresses = confirmed(site, path, excludePostId).stream()
                .map(post -> ShAddress.post(site.getName(), post.getFurl()).toString())
                .distinct()
                .sorted()
                .toList();
        return addresses.isEmpty()
                ? Inbound.NONE
                : new Inbound(addresses.stream().limit(cap).toList(), addresses.size());
    }

    /** Whether this href is a path into this instance rather than an external or protocol URL. */
    public static boolean isInternal(String href) {
        return href != null && href.startsWith("/") && !href.startsWith("//");
    }

    /**
     * An href as a comparable path: query and fragment dropped, normalized, lower-cased.
     *
     * <p>The comparison has to be case-insensitive because a friendly URL is, and it has to drop
     * the query because {@code /page?ref=nav} and {@code /page} are the same page — a move breaks
     * both or neither.</p>
     */
    public static String normalizedPath(String href) {
        return ShAddress.normalizePath(stripQuery(href)).toLowerCase(Locale.ROOT);
    }

    /**
     * The candidates, confirmed. Not cached: a move is rare, and a stale answer here would name
     * pages that are fine or miss pages that are not.
     */
    private List<ShPostUnified> confirmed(ShSite site, String path, String excludePostId) {
        String wanted = normalizedPath(path);
        if (site == null || site.getId() == null || wanted.isBlank() || "/".equals(wanted)) {
            return List.of();
        }
        List<ShPostUnified> candidates = shPostUnifiedRepository.searchBySitesAndStateIn(
                List.of(site.getId()), List.of(ShPostState.PUBLISHED), wanted);
        List<ShPostUnified> confirmed = new ArrayList<>();
        for (ShPostUnified candidate : candidates) {
            if (candidate.getId() != null && candidate.getId().equals(excludePostId)) {
                continue;
            }
            if (candidate.getFurl() != null && !candidate.getFurl().isBlank()
                    && linksTo(candidate, wanted)) {
                confirmed.add(candidate);
            }
        }
        return confirmed;
    }

    /** Whether any markup field of this post holds an {@code href} that resolves to the path. */
    private boolean linksTo(ShPostUnified post, String wanted) {
        JsonNode data = data(objectMapper, post);
        for (ShPostTypeAttr attr : declared(post.getShPostType())) {
            if (!MARKUP_WIDGETS.contains(widgetName(attr))) {
                continue;
            }
            JsonNode value = data.get(attr.getName());
            if (value == null || value.isNull() || !value.isString()) {
                continue;
            }
            for (ShHtmlOutline.Link link : ShHtmlOutline.of(value.asString()).links()) {
                if (isInternal(link.href()) && wanted.equals(normalizedPath(link.href()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String stripQuery(String href) {
        int cut = href.length();
        int query = href.indexOf('?');
        int fragment = href.indexOf('#');
        if (query >= 0) {
            cut = query;
        }
        if (fragment >= 0 && fragment < cut) {
            cut = fragment;
        }
        return href.substring(0, cut);
    }

    /**
     * The post-type's own fields. Unordered on purpose — the question here is whether <em>any</em>
     * field holds the link, so the ordinal sort {@code verify} needs for stable findings would be
     * work with no reader.
     */
    private static Collection<ShPostTypeAttr> declared(ShPostType postType) {
        return postType == null || postType.getShPostTypeAttrs() == null
                ? List.of() : postType.getShPostTypeAttrs();
    }

    private static String widgetName(ShPostTypeAttr attr) {
        return attr.getShWidget() == null || attr.getShWidget().getName() == null
                ? "" : attr.getShWidget().getName();
    }

    /**
     * A post's attribute payload as an object node — the one lenient parse on this surface.
     *
     * <p>The literal string {@code "null"} is a value the backend really stores, and
     * {@code readTree("null")} answers a node that passes every non-null check — the console's
     * {@code widgetSettings} trap one layer down. {@code isObject()} is what excludes it.</p>
     *
     * <p><b>Why the catch is silent, and why there is only one of it.</b> Malformed {@code data}
     * yields no fields, so this scan finds no links in it and the lint's rules find no values —
     * and that degradation is <em>not</em> invisible: the SH99 pass reports the post, and the render
     * path reports the page. What would be wrong is two copies of the decision, so SH140 made this
     * static and {@code ShAgentVerifyService} calls it instead of holding its own (SH270's
     * inventory is a population, and a second entry here would have been the same entry twice).</p>
     */
    static JsonNode data(ObjectMapper objectMapper, ShPostUnified post) {
        String json = post.getData();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (JacksonException e) {
            return objectMapper.createObjectNode();
        }
    }
}
