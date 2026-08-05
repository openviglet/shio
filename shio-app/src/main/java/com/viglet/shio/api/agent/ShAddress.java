/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A content address (SH79) — a stable, human-readable way to name a site, folder or
 * post, so a UUID stops being the agent's working currency.
 *
 * <h2>Why</h2>
 *
 * <p>Design law <b>P5</b>. Threading UUIDs across calls is a real and frequent
 * failure mode: the id from call 3 is needed in call 7, and any context compaction
 * loses it. It is also the single largest per-row cost in an agent read — 36 chars
 * that tokenize badly — which is what made this the biggest remaining token lever
 * after SH78. And an address makes a write naturally <b>idempotent</b>: "upsert this
 * address" instead of "create, remember the id, then update".</p>
 *
 * <h2>Grammar</h2>
 *
 * <pre>
 * address  := scheme ':' target
 * scheme   := 'site' | 'folder' | 'post' | 'id'
 * target   := site ( '/' path )?      // for site/folder/post
 *           | uuid                    // for id
 * site     := site name (case-insensitive) | site id
 * path     := '/'-separated folder names, or a post's friendly URL
 * </pre>
 *
 * <ul>
 *   <li>{@code site:Blog} — the site itself.</li>
 *   <li>{@code folder:Blog/2026/june} — a folder, by its chain of folder names.</li>
 *   <li>{@code post:Blog/blog/hello-world} — a post, by its friendly URL.</li>
 *   <li>{@code site:Blog/blog/hello-world} — <b>lenient</b>: whatever lives there.
 *       Resolves post-first, then folder, and reports genuine ambiguity as a 400
 *       rather than silently picking (P5) — see {@code ShAddressResolver}.</li>
 *   <li>{@code id:9f3c…} — the escape hatch. Ids remain valid forever; this is how
 *       you say one deliberately.</li>
 * </ul>
 *
 * <h2>Two namespaces, deliberately kept apart</h2>
 *
 * <p>A folder path and a post URL are <em>not</em> the same namespace: a folder path
 * is the chain of folder <em>names</em>, while a post's {@code furl} is arbitrary and
 * need not relate to the folder it lives in. Collapsing them into one guessable path
 * would make addressing ambiguous exactly where content is most likely to collide, so
 * the scheme names which namespace is meant. {@code site:} exists for the case where
 * the caller does not know or care.</p>
 *
 * <h2>Known limit</h2>
 *
 * <p>A site whose <em>name</em> contains {@code /} cannot be addressed by name — the
 * first slash after the scheme separates site from path. Such a site is still
 * addressable by its id in the site position ({@code post:<siteId>/blog/hello}). This
 * is a real case: SH72 provisions tenant copies as {@code "Blog (acme)"}, so spaces
 * are tolerated; slashes are not.</p>
 *
 * @param kind the addressed kind
 * @param site site name or id ({@code null} for {@link Kind#ID})
 * @param path normalized with a single leading {@code /}; {@code null} when the
 *             address names a site or an id
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShAddress(Kind kind, String site, String path) {

    /** What an address names. {@link #SITE} is the lenient "anything here" form. */
    public enum Kind {
        SITE, FOLDER, POST, ID;

        String scheme() {
            return name().toLowerCase(Locale.ROOT);
        }

        static List<String> schemes() {
            return Arrays.stream(values()).map(Kind::scheme).toList();
        }
    }

    /** The raw id, for an {@link Kind#ID} address. */
    public String id() {
        return kind == Kind.ID ? site : null;
    }

    /** @return {@code true} when this address names a site and nothing inside it. */
    public boolean isSiteRoot() {
        return kind == Kind.SITE && (path == null || "/".equals(path));
    }

    /**
     * Parses an address.
     *
     * @throws ShTeachingException 400 naming the schemes and a corrected example — a
     *         malformed address is a typo, and a typo should cost one message
     */
    public static ShAddress parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new MalformedAddressException(raw, "An address is required.");
        }
        String text = raw.trim();
        int colon = text.indexOf(':');
        if (colon <= 0) {
            throw new MalformedAddressException(text,
                    "An address starts with a scheme, e.g. `post:` or `folder:`.");
        }
        String scheme = text.substring(0, colon).toLowerCase(Locale.ROOT);
        String target = text.substring(colon + 1);
        Kind kind = Arrays.stream(Kind.values())
                .filter(candidate -> candidate.scheme().equals(scheme))
                .findFirst()
                .orElseThrow(() -> new MalformedAddressException(text,
                        "'" + scheme + "' is not an address scheme."));
        if (target.isBlank()) {
            throw new MalformedAddressException(text,
                    kind == Kind.ID ? "Give the object id after `id:`."
                            : "Give a site name or id after `" + scheme + ":`.");
        }
        if (kind == Kind.ID) {
            return new ShAddress(Kind.ID, target.trim(), null);
        }
        int slash = target.indexOf('/');
        if (slash < 0) {
            if (kind == Kind.POST || kind == Kind.FOLDER) {
                throw new MalformedAddressException(text,
                        "A " + scheme + " address needs a path: `" + scheme + ":<site>/<path>`."
                                // SH433 — the one path an agent cannot derive. A replication starts at
                                // the source's `/` every time, so this is the first address it needs,
                                // and `post:<site>` with the path left off is exactly how a caller
                                // reaches for it. Naming the form here costs a sentence on a refusal
                                // and saves the probing session P2 exists to remove.
                                + (kind == Kind.POST
                                        ? " The site's home page is `" + scheme + ":" + target.trim()
                                                + "/` — the post whose friendly URL is `/`."
                                        : ""));
            }
            return new ShAddress(Kind.SITE, target.trim(), null);
        }
        String site = target.substring(0, slash).trim();
        if (site.isEmpty()) {
            throw new MalformedAddressException(text, "The site part is empty.");
        }
        return new ShAddress(kind, site, normalizePath(target.substring(slash)));
    }

    /** Builds a post address from a site name and a friendly URL. */
    public static ShAddress post(String site, String furl) {
        return new ShAddress(Kind.POST, site, normalizePath(furl));
    }

    /** Builds a folder address from a site name and a folder-name path. */
    public static ShAddress folder(String site, String path) {
        return new ShAddress(Kind.FOLDER, site, normalizePath(path));
    }

    public static ShAddress site(String site) {
        return new ShAddress(Kind.SITE, site, null);
    }

    /**
     * Builds an {@code id:} address — the escape hatch, for an object that has no addressable
     * name. SH172 made it a fallback the readers use rather than only something a caller may send.
     */
    public static ShAddress id(String id) {
        return new ShAddress(Kind.ID, id, null);
    }

    /**
     * Collapses repeated slashes and guarantees exactly one leading slash, so
     * {@code blog//hello} and {@code /blog/hello} are the same address. Trailing
     * slashes are dropped (but the root stays {@code /}) — otherwise {@code /blog}
     * and {@code /blog/} would hash differently and break the digest's stability.
     *
     * <p>Public because it is the instance's one canonical path form, and every surface
     * that compares paths has to use the same one (P5). SH99's duplicate-URL rule and
     * SH100's link resolution both compare stored {@code furl}s against hrefs written by
     * hand; a second normalization written next to either of them would eventually
     * disagree with this one, and the symptom would be a lint that reports a defect that
     * is not there.</p>
     */
    public static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String collapsed = path.trim().replaceAll("/{2,}", "/");
        if (!collapsed.startsWith("/")) {
            collapsed = "/" + collapsed;
        }
        while (collapsed.length() > 1 && collapsed.endsWith("/")) {
            collapsed = collapsed.substring(0, collapsed.length() - 1);
        }
        return collapsed;
    }

    /** The canonical form — round-trips through {@link #parse}. */
    @Override
    public String toString() {
        if (kind == Kind.ID) {
            return "id:" + site;
        }
        return kind.scheme() + ":" + site + (path == null || "/".equals(path) && kind == Kind.SITE
                ? ""
                : path);
    }

    /** A 400 that teaches the address grammar (SH80). */
    public static class MalformedAddressException extends ShTeachingException {

        public MalformedAddressException(String sent, String why) {
            super(ShProblem.of("malformed-address", HttpStatus.BAD_REQUEST,
                            "Malformed address" + (sent == null ? "" : " '" + sent + "'"))
                    .withDetail(why)
                    .withAllowed(Kind.schemes())
                    .withFix("Use `<scheme>:<site>/<path>` — e.g. `post:Blog/blog/hello-world`, "
                            + "`post:Blog/` (the home page), `folder:Blog/blog`, `site:Blog`, or "
                            + "`id:<uuid>`.")
                    .withExample("post:Blog/blog/hello-world"));
        }
    }
}
