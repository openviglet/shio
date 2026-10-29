/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import com.viglet.shio.api.post.revision.ShRevisionRow;

import tools.jackson.databind.JsonNode;

/**
 * One post as an agent reads it (SH83's {@code read}, projected by SH123's
 * {@code fields}).
 *
 * <p>Deliberately <b>not</b> the {@code ShPostUnified} entity and not the CDA's
 * {@code ShCdaPost}: this is the authoring view (DRAFT-preferred, `data` verbatim as
 * the write side expects it) with none of the authoring metadata an agent read never
 * includes by default — no owner, date, publisher or publishStatus (the SH78 standing
 * rule). What is left is exactly what you need to change the post and write it back.</p>
 *
 * @param address the canonical {@code post:} address
 * @param type    post-type name — the vocabulary a write uses
 * @param state   {@code DRAFT} / {@code PUBLISHED}: which row this is — <b>not</b> whether the
 *                post is live. Reads are DRAFT-preferred, so this is {@code DRAFT} for any post
 *                that has a draft row, published or not; see {@code published}
 * @param folder  the containing folder's path, needed to create a sibling
 * @param fields  the field names actually returned when a projection was applied;
 *                {@code null} when the whole document is here. Stating it costs a few
 *                tokens and removes the "is this all of it?" round trip
 * @param data    field values keyed by post-type attribute name
 * @param id      omitted unless the caller asked for ids (SH121)
 * @param published <b>is this page live</b> (SH545) — {@code true} when a {@code PUBLISHED} row
 *                exists, and <b>absent otherwise</b>. {@code state} answers which row was read
 *                and cannot answer this, because a published post keeps its draft and the read
 *                prefers it; before SH545 every published page on every site reported
 *                {@code DRAFT} and an agent had to call the public route and infer publication
 *                from an HTTP code. Conditional rather than always-present because the
 *                measurement said so: an unconditional key cost 2.6% of a single-address read
 *                and dropped {@code attribution.addressing@scale.min-token-ratio} below its
 *                floor. That makes it SH348's pattern (absent where it carries nothing) rather
 *                than SH299's (present and zero), and it is the right one here — publication has
 *                a knowable default, since P7 makes DRAFT what a write lands in and the context
 *                pack states that on the first call a session makes
 * @param url     the post's <b>stored</b> friendly URL, and <b>absent whenever it is the address's
 *                own path</b> — which is every post that does not carry a trailing slash (SH585).
 *                SH467 made the declared spelling storable and this is the read half of it:
 *                {@code address} is the identity and collapses {@code /x/} to {@code /x}, so
 *                without this the one fact SH467 preserved is invisible to every reader, and a
 *                {@code shio pull} rebuilds the site one character short. Conditional for
 *                {@code locale}'s reason and with a stronger version of its argument: the default
 *                is not merely knowable, it is <em>already on the row</em> — a reader holding
 *                {@code address} and no {@code url} has the answer in its hand
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentDoc(String address, String type, String state, String folder,
                         java.util.List<String> fields, JsonNode data, String id,
                         Problem error, Impact impact, String locale, String translationOf,
                         Boolean published, String url, String digest, String curatorUrl,
                         String previewUrl,
                         java.util.List<ShRevisionRow> revisions,
                         Schedule schedule,
                         java.util.List<Note> notes,
                         Working working,
                         Boolean translationStale,
                         Index index,
                         String visibility,
                         java.util.List<String> groups) {

    /**
     * The twenty-two-field form, before SH1301 put a post's own members-only gate on its read:
     * {@code visibility: "members"} and the groups, both absent on a post with no gate of its own,
     * so the files projection can round-trip one and every other row reads as it did.
     */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl, java.util.List<ShRevisionRow> revisions,
                      Schedule schedule, java.util.List<Note> notes, Working working,
                      Boolean translationStale, Index index) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, revisions, schedule, notes, working,
                translationStale, index, null, null);
    }

    /** SH1301 — the same row, with the post's own gate; applied last, like {@code translationStale}. */
    public ShAgentDoc withGate(java.util.List<String> gate) {
        return gate == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, curatorUrl, previewUrl,
                        revisions, schedule, notes, working, translationStale, index, "members",
                        gate.isEmpty() ? null : gate);
    }

    /** The twenty-field form, before SH1058 said a translation had fallen behind its source. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl, java.util.List<ShRevisionRow> revisions,
                      Schedule schedule, java.util.List<Note> notes, Working working) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, revisions, schedule, notes, working,
                null, null);
    }

    /**
     * SH1063 — whether this page reached the search index, and where it would go.
     *
     * <p>Only with {@code include=index}, like every other rider on this read. An instance that
     * does not index says so once ({@code enabled: false}) rather than leaving a reader to infer
     * it from an absent site, because "indexing is off" and "this page routes nowhere" are
     * different answers and only the second is a defect.</p>
     *
     * @param enabled     whether this instance indexes at all — configured and pointed somewhere
     * @param searchSite  the search site this page's content belongs in, or null when it routes
     *                    nowhere, which is the failure that raises no error
     * @param indexed     whether this page is one that gets sent: published, routed, and of a type
     *                    the mapper indexes
     * @param lastFailure the most recent indexing failure recorded for this address, or null
     * @param failedAt    when that failure was last seen
     */
    public record Index(Boolean enabled, String searchSite, Boolean indexed, String lastFailure,
                        String failedAt) {
    }

    /** The same row, saying whether it is findable (SH1063). */
    public ShAgentDoc withIndex(Index status) {
        return status == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, curatorUrl, previewUrl,
                        revisions, schedule, notes, working, translationStale, status, visibility, groups);
    }

    /**
     * SH1058 — the same row, saying the source has changed since this translation was written.
     *
     * <p>{@code true} or absent, never {@code false}: the flag is a fact when it is there, and a
     * monolingual instance — every row of it — pays nothing, which is the shape {@code locale} and
     * {@code translationOf} beside it already have. Applied last in the read's chain, as
     * {@code notes} and {@code working} are, because the {@code with…} methods before it rebuild
     * the row from the fields they know.</p>
     */
    public ShAgentDoc withTranslationStale(Boolean stale) {
        return !Boolean.TRUE.equals(stale) ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, curatorUrl, previewUrl,
                        revisions, schedule, notes, working, Boolean.TRUE, index, visibility, groups);
    }

    /** The nineteen-field form, before SH1008 said who else is on this page. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl, java.util.List<ShRevisionRow> revisions,
                      Schedule schedule, java.util.List<Note> notes) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, revisions, schedule, notes, null);
    }

    /** The eighteen-field form, before SH1003 attached the open notes. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl, java.util.List<ShRevisionRow> revisions,
                      Schedule schedule) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, revisions, schedule, null, null);
    }

    /**
     * One open note on this page (SH1003) — only with {@code include=notes}.
     *
     * <p>Open ones only, and that is the whole shape of it: a resolved note is history, and a read
     * an agent makes before rewriting a page wants the list of things still being asked of it. The
     * thread in full is {@code GET /api/v2/note?object=<id>}.</p>
     *
     * @param id    so a run can close it with {@code note.resolve}
     * @param field the field it is about, when it is about one
     * @param kind  {@code COMMENT}, {@code REQUEST} or {@code REPLY}
     * @param body  what was said, which is the reason the read exists
     * @param by    who said it
     */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Note(String id, String field, String kind, String body, String by) {
    }

    /** The same doc, carrying what is still being asked of this page (SH1003). */
    public ShAgentDoc withNotes(java.util.List<Note> attached) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact, locale,
                translationOf, published, url, digest, curatorUrl, previewUrl, revisions, schedule,
                attached, working);
    }

    /**
     * Another run that wrote to this page inside the last minute (SH1008) — only with
     * {@code include=working}, and only while it is true.
     *
     * <p>Absent is the ordinary answer and says the useful thing by saying nothing: a page nobody
     * else is on reads exactly as it always did. It is a fact about the recent past rather than a
     * claim about the present, which is why it carries both ends — {@code at} is what happened and
     * {@code until} is when this stops being worth saying.</p>
     *
     * <p><b>It is not a lock.</b> A read that returns one does not mean a write will be refused, and
     * a write is not refused because one is here: the concurrency contract is SH985's {@code expect}
     * and this changes nothing about it.</p>
     *
     * @param intent what that run declared it was for (SH1000), when it declared one
     */
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Working(String session, String agentName, String intent, String at,
                          String until) {
    }

    /** The same doc, saying who else is on this page (SH1008). */
    public ShAgentDoc withWorking(Working mark) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact, locale,
                translationOf, published, url, digest, curatorUrl, previewUrl, revisions, schedule,
                notes, mark);
    }

    /** The seventeen-field form, before SH991 let a read carry the post's schedule. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl, java.util.List<ShRevisionRow> revisions) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, revisions, null, null, null);
    }

    /**
     * When this post goes live or comes down, as it is scheduled now (SH991) — only with
     * {@code include=schedule}, and only when one is set.
     *
     * <p>Read off the draft row whichever row the read returned, because a schedule is set on the
     * draft (SH15) and is a fact about the post rather than about a state of it: a read of what is
     * published still wants to know that it is due to change.</p>
     *
     * @param publishAt   ISO-8601, or absent
     * @param unpublishAt ISO-8601, or absent
     * @param requestedBy who set it, which the Activity row names beside the scheduler that fires it
     */
    @JsonInclude(Include.NON_NULL)
    public record Schedule(String publishAt, String unpublishAt, String requestedBy) {
    }

    /** The same row with its schedule attached, or unchanged when there is none to attach. */
    public ShAgentDoc withSchedule(Schedule due) {
        return due == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, curatorUrl, previewUrl,
                        revisions, due);
    }

    /** The sixteen-field form, before SH987 let a read carry the page's history. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest, String curatorUrl,
                      String previewUrl) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, curatorUrl, previewUrl, null);
    }

    /**
     * The same row with SH987's revision list attached, or unchanged when nobody asked.
     *
     * <p>Opt-in on {@code include=revisions}, and terse by construction: the rows carry numbers,
     * dates, actors and the field names each revision changed, never a body. A caller reads the
     * list to decide which revision to fetch, and fifty bodies is the page fifty times for a
     * question the metadata answers (P3).</p>
     */
    public ShAgentDoc withRevisions(java.util.List<ShRevisionRow> history) {
        return history == null || history.isEmpty() ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, curatorUrl, previewUrl,
                        history, schedule);
    }

    /** The fourteen-field form, before SH981 attached the console's own links. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url, String digest) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, digest, null, null);
    }

    /** The thirteen-field form, before SH721 made a write conditional on what was read. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published, String url) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, url, null, null, null);
    }

    /** The twelve-field form, before SH585 made the stored spelling readable. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact, String locale, String translationOf,
                      Boolean published) {
        this(address, type, state, folder, fields, data, id, error, impact, locale, translationOf,
                published, null);
    }

    /**
     * The same row with SH721's state digest attached — what {@code expect} on a write op takes.
     *
     * <p>Attached only where a caller asked for it ({@code digest=true}), which is SH121's shape
     * and not timidity: it is sixteen hex characters plus a key on <em>every</em> row of every
     * read, and the caller who needs it is the one about to write. A field every reader pays for
     * and one in fifty uses is the P3 regression this surface measures for.</p>
     */
    public ShAgentDoc withDigest(String stateDigest) {
        return stateDigest == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, stateDigest, curatorUrl, previewUrl, revisions, schedule);
    }

    /**
     * The same row with SH585's stored spelling attached, or unchanged when there is nothing to
     * say — {@code declared} equal to the address's own path is the overwhelming case, and a key
     * repeating what is already on the row is one every reader pays for and none of them uses.
     */
    public ShAgentDoc withUrl(String declared) {
        if (declared == null || declared.isBlank() || address == null) {
            return this;
        }
        int slash = address.indexOf('/');
        String path = slash < 0 ? null : address.substring(slash);
        return declared.equals(path)
                ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, declared, digest, curatorUrl, previewUrl, revisions, schedule);
    }

    /**
     * The nine-field form, before SH348 made a post's locale visible.
     *
     * <p><b>{@code locale} is present only when it is not the instance default</b>, which is the P3
     * shape: a monolingual instance — the overwhelming case — pays nothing per row, and the field is
     * always there where it carries information. Absence is unambiguous rather than merely cheap,
     * because the context pack publishes the default on the one call SH77 tells a session to make
     * first, so a reader interpreting an absent locale already holds the answer. That is what makes
     * this SH121's pattern (ids off by default) rather than SH299's (present and zero): a marker
     * count has no knowable default, and this does.</p>
     */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id,
                      Problem error, Impact impact) {
        this(address, type, state, folder, fields, data, id, error, impact, null, null, null);
    }

    /** The same row with SH348's locale attached. */
    public ShAgentDoc withLocale(String postLocale) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                postLocale, translationOf, published, url, digest, curatorUrl, previewUrl, revisions, schedule);
    }

    /**
     * The address of the post this one is a translation <b>of</b> (SH348), or {@code null}.
     *
     * <p>Always the group's <em>anchor</em> — the member whose id is the group id, which is the post
     * the others were created from. Pointing every translation at the anchor rather than at each other
     * makes the relationship a forest instead of a graph: acyclic, deterministic, and expressible in
     * one field per file. The anchor itself reports nothing, which is how a reader tells the original
     * from its translations without a second call.</p>
     *
     * <p>Absent for a post with no siblings, which is almost every post — so this costs nothing on a
     * monolingual instance, the same P3 shape as {@code locale} beside it.</p>
     */
    public ShAgentDoc withTranslationOf(String anchorAddress) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact, locale,
                anchorAddress, published, url, digest, curatorUrl, previewUrl, revisions, schedule);
    }

    /** The seven-field form: a row that resolved, which is every row before SH171. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id) {
        this(address, type, state, folder, fields, data, id, null, null, null, null, null);
    }

    /** The eight-field form: SH171's failed row, before SH262 added the impact projection. */
    public ShAgentDoc(String address, String type, String state, String folder,
                      java.util.List<String> fields, JsonNode data, String id, Problem error) {
        this(address, type, state, folder, fields, data, id, error, null, null, null, null);
    }

    /**
     * The same row with SH545's publication state attached, when it is live.
     *
     * <p>Callers pass {@code null} for a draft rather than {@code FALSE}: absence is the answer,
     * grounded in the convention the context pack states on a session's first call — <i>writes land
     * in DRAFT; publishing is a separate, explicit call</i>. A {@link Problem} row carries nothing
     * here either, and for a second reason: a read that could not resolve the post has no
     * publication state to report, and {@code false} would assert one.</p>
     */
    public ShAgentDoc withPublished(Boolean live) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                locale, translationOf, live, url, digest, curatorUrl, previewUrl, revisions, schedule);
    }

    /**
     * The same row with the console's own link attached (SH981).
     *
     * <p>An agent's closing move used to be a sentence — "change it in the console" with no URL —
     * and the console's routes live in TypeScript the backend cannot read. They are declared now
     * ({@code console/surfaces.json}), so this is the route that declaration carries rather than a
     * string concatenated here, which is what makes it survive the next time the prefix moves.</p>
     *
     * <p>Both fields are absent when there is nothing to point at: a folder has no preview, and an
     * instance whose console has no page for a kind hands back no link rather than a broken one.</p>
     */
    public ShAgentDoc withLinks(String console, String preview) {
        return console == null && preview == null ? this
                : new ShAgentDoc(address, type, state, folder, fields, data, id, error, impact,
                        locale, translationOf, published, url, digest, console, preview, revisions, schedule);
    }

    /** The same row with SH262's blast radius attached. */
    public ShAgentDoc withImpact(Impact blastRadius) {
        return new ShAgentDoc(address, type, state, folder, fields, data, id, error, blastRadius,
                locale, translationOf, published, url, digest, curatorUrl, previewUrl, revisions, schedule);
    }

    /**
     * What editing this post would affect — SH262, and only when {@code include=impact} asked.
     *
     * <h2>Why it rides a read rather than getting an endpoint</h2>
     *
     * <p>SH193 answers "what did I just touch" on the change feed and SH194 answers it on a write.
     * The question an agent asks <em>first</em> — before rewriting a shared region, what does it
     * reach — had no surface. A {@code GET /agent/impact} would have been the clearest thing to read
     * and a fourth contract restating a graph two other surfaces already expose (P10), and it would
     * have been REST-only: the MCP tool count is capped at 12 with 11 used, so a new endpoint with no
     * tool is a capability law P1 forbids. Riding {@code /agent/read} means {@code shio_read} carries
     * it with no new schema, and the subject of a read is already an address.</p>
     *
     * <p><b>Opt-in, and absent otherwise.</b> Most reads want the fields; a caller that has not asked
     * about the blast radius should not pay for the graph walk or the tokens. Absent rather than
     * empty for a post that is not a template, the rule SH121 set for ids.</p>
     *
     * @param kind      {@code region}, {@code layout} or {@code theme} (SH286)
     * @param layouts   the layouts involved — a region's slotters, or the layout itself
     * @param postTypes the post-types whose pages render through them
     * @param pages     draft pages of those types in this site
     * @param published published pages — the number that decides whether to edit now
     */
    @JsonInclude(Include.NON_NULL)
    public record Impact(String kind, java.util.List<String> layouts,
                         java.util.List<String> postTypes, int pages, int published,
                         Routes routes) {

        /** The five-field form, before SH996 answered which URLs a publish moves. */
        public Impact(String kind, java.util.List<String> layouts, java.util.List<String> postTypes,
                      int pages, int published) {
            this(kind, layouts, postTypes, pages, published, null);
        }

        /** The same impact, carrying what publishing this draft would do to the site's URLs. */
        public Impact withRoutes(Routes value) {
            return new Impact(kind, layouts, postTypes, pages, published, value);
        }
    }

    /**
     * What publishing this draft would do to the URLs visitors use (SH996).
     *
     * <p>{@code include=impact} answered <em>template</em> blast radius — which layouts and
     * post-types a region touches — and a curator about to press Publish is asking a different
     * question: which addresses start resolving, which move, and what breaks. So the same opt-in
     * carries both, because they are one question asked of two kinds of post.</p>
     *
     * <p><b>Absent when a publish changes no URL</b>, which is almost every publish: an edit to a
     * page already live moves nothing. That keeps an ordinary read byte-identical to what it was
     * before this shipped (SH121's rule for ids, applied to a section).</p>
     *
     * @param goesLive  the URL this publish makes resolve for the first time, or {@code null}
     * @param moves     the URL this publish moves the live page <em>to</em>, when the draft was
     *                  renamed since it went live
     * @param stops     the URL that stops resolving because of the move
     * @param inbound   how many <b>published</b> pages link to the URL that appears or disappears —
     *                  the number that says whether this is a quiet change or a hundred broken links.
     *                  Counted with the SH140 scanner the move lint uses, so the two cannot disagree
     */
    @JsonInclude(Include.NON_NULL)
    public record Routes(String goesLive, String moves, String stops, Integer inbound) {

        /** Whether anything about the site's URLs would change — the test for attaching it at all. */
        public boolean any() {
            return goesLive != null || moves != null || stops != null;
        }
    }

    /**
     * Why one row failed (SH171).
     *
     * <p>A multi-address read exists so N posts cost one round trip; answering the whole call
     * with a 404 because one address was wrong inverted the saving, because the agent then
     * re-read them one at a time to find out which. So a row that cannot resolve carries the
     * SH80 teaching fields <em>in place of</em> its content, and the reader keeps every row it
     * asked for. The <b>single</b>-address form still 404s — that is correct, and callers
     * depend on it.</p>
     *
     * @param type the problem slug's URI, the same value the 4xx body carries
     * @param title what is wrong, in one line
     * @param fix   what to do instead
     */
    public record Problem(String type, String title, String fix) {
    }
}
