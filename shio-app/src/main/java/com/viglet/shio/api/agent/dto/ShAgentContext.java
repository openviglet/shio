/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

// Jackson 3 (tools.jackson databind) keeps reusing the 2.x annotation artifact,
// so annotations stay under com.fasterxml.jackson.annotation — as elsewhere here.
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;

/**
 * The context pack (SH77) — everything an agent asks at the start of a session, in
 * one deterministic, budgeted payload.
 *
 * <p>Design law <b>P2: one call replaces a session</b>. Without this, learning an
 * instance costs a post-type listing (entity-shaped, all types, all fields), a site
 * listing, and then one folder-children call per folder — 10–30 requests and
 * 15–40k tokens before any work starts. The same knowledge here is a few thousand
 * tokens, and the {@link #digest()} makes the <em>second</em> session nearly free.</p>
 *
 * <p>Deliberately <b>not</b> a copy of the manifest (SH76): capabilities live there
 * and are referenced by {@link #manifest()}, so the two documents never disagree
 * (P10). This one answers "what content exists and what shape is it".</p>
 *
 * @param protocol    the agent-surface revision, same value the manifest reports
 * @param manifest    path to the capability document, rather than duplicating it
 * @param instance    minimal identification of the running instance
 * @param sites       every readable site (id, name, root folder, counts)
 * @param model       the content model — post-types with their fields, reusing the
 *                    CDA schema shape ({@link ShCdaSchemaField}) so an agent learns
 *                    one field shape across the whole product, not two
 * @param sitemap     the content tree in depth-first order; hierarchy is carried by
 *                    {@code path}, not by nesting, so rows cost no closing braces
 *                    and can be truncated at any point without breaking the document
 * @param conventions how this instance expects to be driven — derived from its own
 *                    state today; author-written conventions arrive with SH114
 * @param digest      stable fingerprint of the content of this pack, computed over
 *                    the canonical terse rendering, so it is identical for the JSON
 *                    and terse forms. An agent stores it and re-reads only on change
 * @param budget      what this response cost and whether anything was cut
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAgentContext(
        int protocol,
        String manifest,
        Instance instance,
        List<Site> sites,
        List<Type> model,
        List<Node> sitemap,
        List<String> conventions,
        String digest,
        Budget budget) {

    /**
     * @param product product name
     * @param version running build version
     * @param defaultLocale the locale new content gets when none is given
     */
    public record Instance(String product, String version, String defaultLocale) {
    }

    /**
     * @param id           site id — still needed for addressing until SH79 lands
     * @param name         site name
     * @param url          the site's public base URL, when configured
     * @param rootFolderId the folder every path in {@code sitemap} is relative to
     * @param posts        number of distinct posts in the site
     */
    public record Site(String id, String name, String url, String rootFolderId, long posts) {
    }

    /**
     * @param name    the post-type's API name — the identifier used in writes
     * @param title   human label
     * @param system  a platform type: read-only over the API
     * @param managed  owned by the repository (SH66): edit the code, not the console
     * @param posts   how many posts of this type exist, so an agent can tell the
     *                model's load-bearing types from its unused ones
     * @param fields  the field list, {@code Tab} containers already dropped
     */
    @JsonInclude(Include.NON_DEFAULT)
    public record Type(String name, String title, boolean system, boolean managed,
                       long posts, List<ShCdaSchemaField> fields) {
    }

    /**
     * One row of the content tree.
     *
     * @param kind  {@code folder} or {@code post}
     * @param path  structural path from the site root ({@code /}, {@code /blog}) —
     *              for a post, the path of its containing folder
     * @param id    object id
     * @param type  post-type name (posts only)
     * @param state {@code DRAFT} / {@code PUBLISHED} (posts only)
     * @param url   the post's friendly URL, i.e. how it is addressed and delivered
     * @param title the post's title (posts only)
     * @param folders child folder count (folders only)
     * @param posts   contained post count (folders only)
     */
    // The same rule the terse encoding follows, applied to JSON (SH78): a row does
    // not pay for what it has nothing to say about. Without this, every post row
    // shipped "folders":0,"posts":0 and every folder row four explicit nulls — pure
    // structural cost repeated per row, which is exactly what makes entity-shaped
    // JSON expensive for an agent in the first place.
    @JsonInclude(Include.NON_DEFAULT)
    public record Node(String kind, String path, String id, String type, String state,
                       String url, String title, long folders, long posts) {
    }

    /**
     * What this response cost and what it left out.
     *
     * <p>There is deliberately <b>no "omitted rows" count</b>. The walk stops at the
     * budget, so it does not know how much tree remains, and reporting the only
     * number it does know ("1 more") would understate the truth — a cut that
     * misrepresents its own size is worse than one that says "there is more". The
     * scale of what remains is already derivable from the emitted rows: every folder
     * row carries its own {@code folders} and {@code posts} counts.</p>
     *
     * @param estTokens      estimated size of this response
     * @param limitTokens    the ceiling applied ({@code 0} = unbounded)
     * @param truncated      whether the sitemap was cut short
     * @param truncatedAfter the last emitted object id — pass it back as {@code from=}
     *                       to resume. {@code null} while {@code truncated} is true
     *                       means <em>nothing</em> fit: raise {@code budget} or narrow
     *                       {@code include}, because there is no row to resume after
     * @param overBudget     the mandatory sections alone exceeded the limit
     */
    public record Budget(int estTokens, int limitTokens, boolean truncated,
                         String truncatedAfter, boolean overBudget) {
    }
}
