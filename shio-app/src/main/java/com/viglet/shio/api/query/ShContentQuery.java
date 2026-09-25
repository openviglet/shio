/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.viglet.shio.api.post.ShContentPerspective;

/**
 * The one vocabulary every content listing filters by (SH1022).
 *
 * <h2>Why one record rather than a parameter list per surface</h2>
 *
 * <p>Four surfaces list content — {@code shio_find} over MCP, {@code shio find} over the CLI,
 * {@code GET /api/v2/agent/find} over REST and {@code GET /api/v2/object/&#123;id&#125;/list} in the
 * console — and before this they filtered by four different sets. The console took
 * {@code page, size, sort, q}; {@code /agent/find} took {@code site, type, folder, q} and, later,
 * {@code state}, {@code scheduled} and {@code release}. Neither could answer "drafts changed this
 * week by one person", and the two disagreed about what a listing even <em>is</em>.</p>
 *
 * <p>The fix is not a fifth endpoint. It is that the words mean one thing: {@code type} is a
 * post-type name everywhere, {@code author} is the {@code owner} column everywhere,
 * {@code modifiedSince} is inclusive everywhere. A surface that cannot serve a word says so
 * (P6) rather than ignoring it, which is the behaviour {@link ShUnknownQueryKeyException}
 * exists for: Spring drops an unbound parameter silently, and a dropped filter answers 200 with
 * every row.</p>
 *
 * <h2>What is <em>not</em> here</h2>
 *
 * <p>The agent surface's own parameters — {@code limit}, {@code after}, {@code ids},
 * {@code trash}, {@code scheduled}, {@code release}, {@code format} — are not part of this
 * vocabulary. They are not filters on content, they are how one surface pages, addresses and
 * serialises. Each surface declares its own beside the words it serves.</p>
 *
 * @param site           site name, id or {@code site:} address; absent means every readable site
 * @param folder         folder path or {@code folder:} address
 * @param subtree        whether {@code folder} means the subtree; <b>null is "not said"</b>, and
 *                       each surface keeps the default it already had — {@code /agent/find} has
 *                       always meant the subtree, the console listing has always meant direct
 *                       children, and a word that changed one of them would break a caller to
 *                       make two defaults agree
 * @param type           post-type name, e.g. {@code Article}
 * @param state          {@code published} or {@code draft}; the spellings
 *                       {@link ShContentPerspective} accepts, validated here so every surface
 *                       refuses the same typo
 * @param locale         a locale tag as stored, e.g. {@code en} or {@code pt_BR}
 * @param missingLocale  SH1060 — the pages whose translation group has <b>no</b> member in this
 *                       locale, which is "what is still to translate into pt-br" as a filter
 *                       rather than a scan. Answered on the group's source alone, so one gap is
 *                       one row however many languages the page already has
 * @param modifiedSince  inclusive lower bound on the last change
 * @param modifiedBefore exclusive upper bound on the last change
 * @param author         who created the post — the {@code owner} column, matched case-insensitively
 * @param modifier       who last changed it
 * @param submission     SH1066 — {@code true} for only what a site's form wrote, {@code false} for
 *                       none of it; absent is both
 * @param agent          SH1024 — {@code true} for only what an agent run wrote, {@code false} for
 *                       nothing it did; a {@code console:} session is a curator's and never counts
 * @param q              full text over title, summary and the attribute data
 * @param sort           {@code title}, {@code date} or {@code modifiedDate}
 * @param page           zero-based page index
 * @param size           rows per page
 * @param folderPage     zero-based page index for the <em>folder</em> half of a listing that has
 *                       one (SH1025). Absent means the whole set, which is what the console
 *                       listing has always answered and what its move/copy picker reads
 * @param folderSize     child folders per page
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShContentQuery(String site, String folder, Boolean subtree, String type,
        String state, String locale, Instant modifiedSince, Instant modifiedBefore, String author,
        String modifier, String q, String sort, Integer page, Integer size, Integer folderPage,
        Integer folderSize, String missingLocale, Boolean submission, Boolean agent) {

    /** The vocabulary, in the order the design names it — the {@code allowed} set of a refusal. */
    public static final List<String> KEYS = List.of("site", "folder", "subtree", "type", "state",
            "locale", "missingLocale", "modifiedSince", "modifiedBefore", "author", "modifier",
            "submission", "agent", "q", "sort", "page", "size", "folderPage", "folderSize");

    /** The words that page or order rather than filter — every listing has its own answer. */
    public static final List<String> PAGING =
            List.of("sort", "page", "size", "folderPage", "folderSize");

    /** Everything a listing filters by, which is {@link #KEYS} without the paging words. */
    public static final List<String> FILTERS = KEYS.stream()
            .filter(key -> !PAGING.contains(key)).toList();

    /** No filter at all: every readable row. */
    public static final ShContentQuery ALL = new ShContentQuery(null, null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null, null);

    /** The most common starting point, and the one a test writes. */
    public static ShContentQuery ofSite(String site) {
        return ALL.withSite(site);
    }

    public ShContentQuery withSite(String value) {
        return new ShContentQuery(value, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withFolder(String value) {
        return new ShContentQuery(site, value, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withSubtree(Boolean value) {
        return new ShContentQuery(site, folder, value, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withType(String value) {
        return new ShContentQuery(site, folder, subtree, value, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withState(String value) {
        return new ShContentQuery(site, folder, subtree, type, value, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withLocale(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, value, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    /** SH1060 — the groups with no translation in this locale, answered on the source. */
    public ShContentQuery withMissingLocale(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize,
                value, submission, agent);
    }

    /**
     * SH1066 — only what a site's form wrote ({@code true}), or none of it ({@code false}).
     *
     * <p>A submission is a post (SH633), so it is found by the words every post is found by; this
     * is the one word that says <em>which</em> posts arrived through a form, read off the owner the
     * form endpoint writes ({@code ShFormSubmitter}) rather than a column of its own.</p>
     */
    public ShContentQuery withSubmission(Boolean value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize,
                missingLocale, value, agent);
    }

    /**
     * SH1024 — only what an agent run wrote ({@code true}), or nothing it did ({@code false}).
     *
     * <p>Read off the audit trail's {@code agent_session_id} (SH106), the column the review queue
     * and the activity page's attribution filter already read, and never a {@code console:} session:
     * that is a curator publishing their own work (SH996), and filing it under the machines is the
     * one mistake this word must not make.</p>
     */
    public ShContentQuery withAgent(Boolean value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, folderPage, folderSize,
                missingLocale, submission, value);
    }

    public ShContentQuery withModifiedSince(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale,
                instant("modifiedSince", value), modifiedBefore, author, modifier, q, sort, page,
                size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withModifiedBefore(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                instant("modifiedBefore", value), author, modifier, q, sort, page, size,
                folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withAuthor(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, value, modifier, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withModifier(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, value, q, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withQ(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, value, sort, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withSort(String value) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, value, page, size, folderPage, folderSize, missingLocale, submission, agent);
    }

    public ShContentQuery withPaging(Integer pageValue, Integer sizeValue) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, pageValue, sizeValue, folderPage,
                folderSize, missingLocale, submission, agent);
    }

    /**
     * SH1025 — ask for one page of the folder half; absent means the whole set.
     *
     * <p>Two words rather than reusing {@code page}/{@code size}, because a listing with two
     * collections in it has two answers and one pair of words could only give it one. Absent is
     * the meaning that matters: the move/copy picker reads a listing's folders whole and would be
     * handed one page of a site by a default that paged them.</p>
     */
    public ShContentQuery withFolderPaging(Integer pageValue, Integer sizeValue) {
        return new ShContentQuery(site, folder, subtree, type, state, locale, modifiedSince,
                modifiedBefore, author, modifier, q, sort, page, size, pageValue, sizeValue, missingLocale, submission, agent);
    }

    /** Whether the caller asked for a page of folders rather than all of them. */
    public boolean pagesFolders() {
        return folderPage != null || folderSize != null;
    }

    /**
     * Parse one surface's request.
     *
     * <p>Every key is checked against the vocabulary <b>before</b> a value is read, because the
     * refusal that matters is the one for a word nobody serves. A value that parses to nothing
     * useful — a blank {@code type=}, a blank {@code q=} — is dropped rather than refused: an
     * empty parameter is what a form submits for an unset field, and refusing it would make the
     * console's own filter bar unusable.</p>
     *
     * @param params    the request's whole parameter map, keys as they arrived
     * @param served    which of {@link #KEYS} this surface answers
     * @param own       the surface's own parameters, outside the vocabulary
     * @param elsewhere what to tell a caller who sent a vocabulary word this surface cannot serve
     */
    public static ShContentQuery of(Map<String, String> params, Collection<String> served,
            Collection<String> own, String elsewhere) {
        Set<String> allowed = new LinkedHashSet<>(served);
        Set<String> everything = new LinkedHashSet<>(allowed);
        everything.addAll(own);
        List<String> visible = new ArrayList<>(allowed);
        visible.addAll(own);

        ShContentQuery query = ALL;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            if (everything.contains(key)) {
                query = allowed.contains(key) ? query.with(key, entry.getValue()) : query;
                continue;
            }
            if (KEYS.contains(key)) {
                throw new ShUnknownQueryKeyException(key, visible, elsewhere);
            }
            throw new ShUnknownQueryKeyException(key, visible);
        }
        return query;
    }

    /** One key, applied. Kept beside {@link #KEYS} so a word added there has to land here too. */
    private ShContentQuery with(String key, String value) {
        return switch (key) {
            case "site" -> withSite(text(value));
            case "folder" -> withFolder(text(value));
            case "subtree" -> withSubtree(flag(value));
            case "type" -> withType(text(value));
            case "state" -> withState(validState(text(value)));
            case "locale" -> withLocale(text(value));
            case "missingLocale" -> withMissingLocale(text(value));
            case "modifiedSince" -> withModifiedSince(text(value));
            case "modifiedBefore" -> withModifiedBefore(text(value));
            case "author" -> withAuthor(text(value));
            case "modifier" -> withModifier(text(value));
            case "submission" -> withSubmission(bool("submission", value));
            case "agent" -> withAgent(bool("agent", value));
            case "q" -> withQ(text(value));
            case "sort" -> withSort(text(value));
            case "page" -> withPaging(number("page", value, 0), size);
            case "size" -> withPaging(page, number("size", value, 1));
            case "folderPage" -> withFolderPaging(number("folderPage", value, 0), folderSize);
            case "folderSize" -> withFolderPaging(folderPage, number("folderSize", value, 1));
            default -> this;
        };
    }

    /** Whether anything at all was asked for — a listing with no filter reads the whole scope. */
    public boolean isEmpty() {
        return equals(ALL);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Boolean flag(String value) {
        String text = text(value);
        if (text == null || "true".equalsIgnoreCase(text) || "".equals(text)) {
            // A bare `?subtree` arrives as the empty string, which every CLI and every HTML form
            // means as "yes". Only an explicit `false` turns it off.
            return text == null ? null : Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw new ShInvalidQueryValueException("subtree", text, "true or false");
    }

    /**
     * SH1066 — {@code submission} as a caller outside the REST parse sends it (MCP's arguments are
     * text), refused with the same teaching error so the two transports cannot disagree.
     */
    public static Boolean submissionOf(String value) {
        return bool("submission", value);
    }

    /** SH1024 — {@code agent} as MCP sends it, refused the way the REST parse refuses it. */
    public static Boolean agentOf(String value) {
        return bool("agent", value);
    }

    /**
     * A yes or a no, and nothing for an unset field. Unlike {@code subtree}, a bare key is not a
     * yes: {@code submission} narrows in both directions, and guessing which was meant would answer
     * with the wrong half of a site.
     */
    private static Boolean bool(String key, String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        throw new ShInvalidQueryValueException(key, text, "true or false");
    }

    private static String validState(String value) {
        if (value != null) {
            // Parsed for its refusal and then thrown away: the surfaces narrow it differently
            // (`/agent/find` serves rows, a revision is a read) and what must not differ is which
            // spellings are accepted at all.
            ShContentPerspective.parse(value);
        }
        return value;
    }

    private static Integer number(String key, String value, int floor) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(text);
            if (parsed < floor) {
                throw new ShInvalidQueryValueException(key, text, floor + " or more");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new ShInvalidQueryValueException(key, text, "a whole number");
        }
    }

    /**
     * An instant, from the two spellings a caller actually types.
     *
     * <p>{@code 2026-09-01} is what a person writes and what a CLI flag carries; it is read as
     * midnight UTC, which is the only reading that does not depend on where the server is. The
     * full ISO instant is what a machine writes and what {@code find} returns, so a value read
     * out of one answer can be passed straight back into the next question.</p>
     */
    private static Instant instant(String key, String value) {
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException notAnInstant) {
            try {
                return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException notADate) {
                throw new ShInvalidQueryValueException(key, text,
                        "an ISO instant like 2026-09-01T00:00:00Z, or a date like 2026-09-01");
            }
        }
    }
}
