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

import tools.jackson.databind.JsonNode;

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
 * @param capabilities SH157 — the manifest's {@code features}, {@code limits} and
 *                    {@code startHere}, carried here when {@code include=manifest} names
 *                    them, so a session's two opening reads can be one. <b>Absent unless
 *                    asked for</b>: every session that already knows this instance would
 *                    otherwise pay for it again, and the endpoint index — the expensive part
 *                    — deliberately stays on {@code /agent/manifest}, where it is worth an
 *                    ETag of its own because it changes only when the build does
 * @param ops         SH346 — the write vocabulary, verbatim: every op {@code /agent/batch} accepts
 *                    and the keys each one takes, the same bytes the {@code shio://ops} MCP
 *                    resource serves. <b>Absent unless {@code include=ops} names it</b>, and, like
 *                    {@code capabilities}, attached after the digest is taken — a session that is
 *                    only reading must not pay for the document that explains writing, and asking
 *                    for it must not invalidate a pack somebody else has cached
 * @param budget      what this response cost and whether anything was cut
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentContext(
        int protocol,
        String manifest,
        Instance instance,
        List<Site> sites,
        List<Type> model,
        List<Node> sitemap,
        List<String> conventions,
        ShAgentManifest capabilities,
        String ops,
        String digest,
        Budget budget,
        List<String> omitted,
        /**
         * SH754 — this tenant's spend today, per surface, written and pending, the same bytes
         * {@code shio://usage} serves. <b>Absent unless {@code include=usage} names it</b>, and
         * attached after the digest for a reason the other two opt-in sections only share by
         * accident: this one changes on <em>every</em> call, so letting it into the digest would
         * make a conditional read never match.
         */
        String usage,
        /**
         * SH993 — the site's releases, newest first, absent unless {@code include=releases} names
         * them. Attached after the digest like the other opt-in sections: a caller asking for them
         * must not invalidate a pack somebody else has cached.
         */
        List<Release> releases,
        /**
         * SH1050 — what is going on here, absent unless {@code include=status} names it.
         *
         * <p>The pack says what a caller CAN do; this says what is happening. An agent arriving
         * mid-project wants both, and composing the second from the review surface, the scheduled
         * listing, the note list and the change feed is four calls for one question.</p>
         *
         * <p>Opt-in for `usage`'s reason rather than `releases`': <b>it changes constantly</b>, so
         * letting it into the default pack would put a number that moves on every publish inside a
         * document SH116's conditional read exists to keep matching. A caller who wants the state
         * of the instance asks for it, and pays for it then.</p>
         */
        Status status) {

    /**
     * The instance's own state, the same numbers `GET /api/v2/console/summary` answers with.
     *
     * <p>Counts rather than rows, and the attention half rather than the trail: what an agent does
     * with this is decide whether to look somewhere before it writes. The trail itself is
     * `/agent/changes`, which is a different question with a cursor on it.</p>
     */
    public record Status(long sites, long postTypes, int reviewSessions, int reviewChanges,
                         long scheduledSoon, long openNotes) {
    }

    /** One release, as the pack lists it: what it is called, where it is, and how big. */
    @JsonInclude(Include.NON_NULL)
    public record Release(String address, String state, String description, String scheduledAt,
                          int posts) {
    }

    /**
     * The sections {@code include=} removed (SH608), or {@code null} when the pack is whole.
     *
     * <h3>Names, where the manifest reports counts</h3>
     *
     * <p>SH600 gave the manifest {@code omitted} as section → entry count, because a manifest is
     * assembled and <em>then</em> projected, so the counts were already in hand. The pack is the
     * other way round: a section {@code include=} drops is never built, so counting it would cost
     * exactly what the caller's projection saved — the fix would be an argument against itself
     * (P3). A name is what tells a caller its question was not answered, which is the whole job.</p>
     *
     * <h3>Separate from {@code budget.truncated}, and that is the decision</h3>
     *
     * <p>Both say "you did not get all of it" and they are not the same statement.
     * {@code omitted} is <b>the caller's own doing</b> — a section they asked not to have — and the
     * remedy is to ask for it. {@code truncated} is <b>the instance's</b> — the sitemap walk hit
     * the budget — and the remedy is a bigger budget or a cursor. One field would tell a caller
     * that the pack is incomplete without telling them which lever is theirs, and a reader who
     * raises the budget to recover a section they excluded gets the same answer twice.</p>
     */
    public ShAgentContext {
    }

    /** The twelve-field form, before SH993 gave the pack a release listing. */
    public ShAgentContext(int protocol, String manifest, Instance instance, List<Site> sites,
                          List<Type> model, List<Node> sitemap, List<String> conventions,
                          ShAgentManifest capabilities, String ops, String digest, Budget budget,
                          List<String> omitted, String usage) {
        this(protocol, manifest, instance, sites, model, sitemap, conventions, capabilities, ops,
                digest, budget, omitted, usage, null, null);
    }

    /** Every caller from before SH608, which is every one that cannot be projected. */
    public ShAgentContext(int protocol, String manifest, Instance instance, List<Site> sites,
                          List<Type> model, List<Node> sitemap, List<String> conventions,
                          ShAgentManifest capabilities, String ops, String digest, Budget budget) {
        this(protocol, manifest, instance, sites, model, sitemap, conventions, capabilities, ops,
                digest, budget, null, null);
    }

    /** The same pack carrying the site's releases (SH993). */
    public ShAgentContext withReleases(List<Release> value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, capabilities, ops, digest, budget, omitted, usage, value, status);
    }

    /** SH1050 — the instance's state, attached after the digest like every other opt-in section. */
    public ShAgentContext withStatus(Status value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, capabilities, ops, digest, budget, omitted, usage, releases, value);
    }

    /** Every caller from before SH157, which is every one that does not ask for capabilities. */
    public ShAgentContext(int protocol, String manifest, Instance instance, List<Site> sites,
                          List<Type> model, List<Node> sitemap, List<String> conventions,
                          String digest, Budget budget) {
        this(protocol, manifest, instance, sites, model, sitemap, conventions, null, null,
                digest, budget);
    }

    /** The same pack carrying capabilities — the one thing SH157 adds to this shape. */
    public ShAgentContext withCapabilities(ShAgentManifest value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, value, ops, digest, budget, omitted, usage, releases, status);
    }

    /** The same pack, saying which sections `include=` removed (SH608). */
    public ShAgentContext withOmitted(List<String> value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, capabilities, ops, digest, budget, value, usage, releases, status);
    }

    /** The same pack carrying the write vocabulary (SH346). */
    public ShAgentContext withOps(String value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, capabilities, value, digest, budget, omitted, usage, releases, status);
    }

    /** The same pack carrying this tenant's spend (SH754). */
    public ShAgentContext withUsage(String value) {
        return new ShAgentContext(protocol, manifest, instance, sites, model, sitemap,
                conventions, capabilities, ops, digest, budget, omitted, value, releases, status);
    }

    /**
     * @param product product name
     * @param version running build version
     * @param defaultLocale the locale new content gets when none is given
     */
    @JsonInclude(Include.NON_NULL)
    public record Instance(String product, String version, String defaultLocale) {
    }

    /**
     * @param id           site id — omitted unless {@code ids=true} (SH121); the site
     *                     <em>name</em> is what an address uses
     * @param name         site name
     * @param url          the site's public base URL, when configured
     * @param rootFolderId the folder every path in {@code sitemap} is relative to;
     *                     omitted unless {@code ids=true}, since {@code folder:<site>/}
     *                     resolves to the same thing
     * @param posts        number of distinct posts in the site
     * @param scratch      SH591 — declared disposable by whoever created it. Omitted when it is
     *                     not, because the common case is a site somebody keeps and a
     *                     {@code false} on every row would cost tokens to say nothing (P3). The
     *                     second half of SH591 is precisely this: a listing that shows thirteen
     *                     sites and no difference between the two a human curates and the eleven
     *                     an agent left behind is a listing the human cannot act on.
     * @param defaultLocale SH1056 — the locale this site is written in, omitted when it is the
     *                     instance default {@code instance.defaultLocale} already published once
     *                     per pack. The common site says nothing here (P3).
     * @param locales      the locales it offers, omitted while there is only one: a list of one
     *                     naming the default repeats two facts to add none
     * @param localesDeclared whether somebody chose the two above, or they were read off the
     *                     content. Present only when true, and it is the distinction this whole
     *                     field exists for: "offers Portuguese" means one thing as a decision and
     *                     another as a description of what has been translated so far
     * @param furl         SH1122 — the site's own URL segment, and {@code description} beside it.
     *                     Both are here so the tree can <em>project</em> a site's settings: the
     *                     marker file carries `url`, `furl`, `description` and the locale policy,
     *                     and the first and last were already on this row while these two were
     *                     readable only from the console's whole-entity site read, which answers
     *                     the 5 MB {@code postTypeLayout} column along with them. Omitted when
     *                     absent, like everything else here, so a site that has neither costs
     *                     nothing and the pack is byte-identical to before (P3)
     * @param description  see {@code furl}
     * @param localePolicy SH1122 — the site's <b>stored</b> locale document, exactly as it is held,
     *                     or absent while the site has declared none. The three fields above are the
     *                     <em>discovery</em> answer and stay as they are: each is omitted when it
     *                     says the ordinary thing, and {@code locales} carries no {@code fallback}
     *                     at all — so a tree reconstructing a policy from them would have to invent
     *                     the chain and guess a default the pack left out because it matched the
     *                     instance's. A projection that wrote four of the marker's five keys would
     *                     be lying by omission, and one more field that is absent for every
     *                     undeclared site is the cheaper half of that trade
     * @param pageType     SH1283 — the type a new page in this site starts as, so an agent creating
     *                     a page reads the answer the console's New button gives. Absent while the
     *                     site names none, which keeps every such pack byte-identical (P3)
     */
    @JsonInclude(Include.NON_NULL)
    public record Site(String id, String name, String url, String rootFolderId, long posts,
                       Boolean scratch, String defaultLocale, List<String> locales,
                       Boolean localesDeclared, String furl, String description,
                       JsonNode localePolicy, String pageType) {

        /** The twelve-field form: every caller before SH1283 named no page type. */
        public Site(String id, String name, String url, String rootFolderId, long posts,
                    Boolean scratch, String defaultLocale, List<String> locales,
                    Boolean localesDeclared, String furl, String description,
                    JsonNode localePolicy) {
            this(id, name, url, rootFolderId, posts, scratch, defaultLocale, locales,
                    localesDeclared, furl, description, localePolicy, null);
        }

        /** The nine-field form: every caller before SH1122 had no file to project a site into. */
        public Site(String id, String name, String url, String rootFolderId, long posts,
                    Boolean scratch, String defaultLocale, List<String> locales,
                    Boolean localesDeclared) {
            this(id, name, url, rootFolderId, posts, scratch, defaultLocale, locales,
                    localesDeclared, null, null, null);
        }

        /** The five-field form, before SH591 made a disposable site visible as one. */
        public Site(String id, String name, String url, String rootFolderId, long posts) {
            this(id, name, url, rootFolderId, posts, null);
        }

        /** The six-field form, before SH1056 gave a site a locale policy of its own. */
        public Site(String id, String name, String url, String rootFolderId, long posts,
                    Boolean scratch) {
            this(id, name, url, rootFolderId, posts, scratch, null, null, null);
        }
    }

    /**
     * @param name    the post-type's API name — the identifier used in writes
     * @param title   human label
     * @param system  a platform type: read-only over the API
     * @param managed  owned by the repository (SH66): edit the code, not the console
     * @param posts   how many posts of this type exist, so an agent can tell the
     *                model's load-bearing types from its unused ones
     * @param scratch declared disposable by whoever created it (SH768) — a fixture, not a
     *                convention. This is the field the whole flag exists for: the pack is the one
     *                call P2 promises replaces a session and the model is most of its payload, so
     *                two types left behind by a conformance run read as <i>this instance has a
     *                convention I should follow</i>, and deciding otherwise costs a second call and
     *                knowledge that was nowhere in the pack. Omitted when false, per
     *                {@code NON_DEFAULT}: a reader asks which types are debris, and 20 rows saying
     *                "not debris" are 20 rows of nothing (P3).
     * @param fields  the field list, {@code Tab} containers already dropped
     */
    @JsonInclude(Include.NON_DEFAULT)
    public record Type(String name, String title, boolean system, boolean managed,
                       long posts, boolean scratch, List<ShCdaSchemaField> fields) {

        /** The six-field form: every caller before SH768 made a type declarable as disposable. */
        public Type(String name, String title, boolean system, boolean managed, long posts,
                    List<ShCdaSchemaField> fields) {
            this(name, title, system, managed, posts, false, fields);
        }
    }

    /**
     * One row of the content tree.
     *
     * @param kind  {@code folder} or {@code post}
     * @param path  structural path from the site root ({@code /}, {@code /blog}) —
     *              for a post, the path of its containing folder
     * @param id    object id — omitted unless {@code ids=true} (SH121). A row is
     *              addressable from {@code site + path/url} alone, and the UUID was
     *              measured as the single largest per-row cost in this payload
     * @param type  post-type name (posts only)
     * @param state {@code DRAFT} / {@code PUBLISHED} (posts only)
     * @param url   the post's friendly URL, i.e. how it is addressed and delivered
     * @param title the post's title (posts only)
     * @param folders child folder count (folders only)
     * @param posts   contained post count (folders only)
     * @param published SH553 — whether this post is also live, which {@code state} cannot say.
     *                  {@code folderPosts} prefers the DRAFT row, so a page serving publicly for
     *                  weeks reported {@code state: DRAFT} and a session that opened with the pack
     *                  was misinformed about every page of a fully published site. {@code null}
     *                  rather than {@code false} when it is not: the row does not pay for what it
     *                  has nothing to say about, which is this record's own rule one line down
     */
    // The same rule the terse encoding follows, applied to JSON (SH78): a row does
    // not pay for what it has nothing to say about. Without this, every post row
    // shipped "folders":0,"posts":0 and every folder row four explicit nulls — pure
    // structural cost repeated per row, which is exactly what makes entity-shaped
    // JSON expensive for an agent in the first place.
    @JsonInclude(Include.NON_DEFAULT)
    public record Node(String kind, String path, String id, String type, String state,
                       String url, String title, long folders, long posts, Boolean published,
                       String visibility, List<String> groups) {

        /** The nine-field form, before SH553 gave the sitemap the word SH545 replaced. */
        public Node(String kind, String path, String id, String type, String state,
                    String url, String title, long folders, long posts) {
            this(kind, path, id, type, state, url, title, folders, posts, null);
        }

        /**
         * The ten-field form, before SH1301 put a folder's own members-only gate on its row:
         * {@code visibility: "members"} and the groups, both absent on a public folder, so a site
         * with no gate pays nothing per row and the files projection can round-trip one.
         */
        public Node(String kind, String path, String id, String type, String state,
                    String url, String title, long folders, long posts, Boolean published) {
            this(kind, path, id, type, state, url, title, folders, posts, published, null, null);
        }
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
    // NON_NULL, not NON_DEFAULT: `truncated: false` and `overBudget: false` are the
    // *answer* to "was anything cut?", so they must stay on the wire. Only the absent
    // cursor is dropped. See ShAgentJsonLeanTest for why this distinction is a policy.
    @JsonInclude(Include.NON_NULL)
    public record Budget(int estTokens, int limitTokens, boolean truncated,
                         String truncatedAfter, boolean overBudget) {
    }
}
