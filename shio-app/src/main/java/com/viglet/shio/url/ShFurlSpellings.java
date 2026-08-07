/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SH583 — the one place that decides which spellings of a path name the same <b>post</b>.
 *
 * <h2>Why this exists before SH467 and not after it</h2>
 *
 * <p>Measured at {@code 077b115}: thirteen post-{@code furl} lookups across seven classes, every
 * one comparing by exact equality. The tolerance meant to bridge {@code /shio} and {@code /shio/}
 * was <b>not one function</b> — it was {@code ShAddressResolver}'s private {@code furlCandidates},
 * {@code ShAgentBatchService}'s private duplicate of it, {@code ShCdaService}'s
 * {@code urlCandidates} under a third name, and {@code ShPageRenderService}'s own redirect half.
 * They agreed by <em>accident</em>: nothing was ever stored with a trailing slash, so all four
 * were asked the same question and gave the same answer.</p>
 *
 * <p>SH467 stores one. From that commit they have to agree <b>on purpose</b>, and the failure mode
 * is the worst kind available — a page that renders in {@code /preview}, answers 404 on the CDA and
 * verifies clean, because three readers disagreed about which spellings match one stored value.
 * {@code ShAddress.normalizePath}'s own javadoc predicted it: <em>a second normalization written
 * next to either of them would eventually disagree with this one, and the symptom would be a lint
 * that reports a defect that is not there</em>. There were three, and the lint it named did not
 * exist. It does now — {@code ShFurlSpellingLintTest} — and it landed with this class rather than
 * with the fixes, which is SH322's order.</p>
 *
 * <h2>Scope: posts only, deliberately</h2>
 *
 * <p>A <b>folder</b> {@code furl} ({@code findByParentFolderAndFurl}, {@code findByShSiteAndFurl})
 * and a <b>site</b> {@code furl} ({@code findByFurl}) are a different namespace — SH79 keeps a
 * folder's chain of names apart from a post's arbitrary friendly URL — and neither is touched by
 * SH467. They are named in the lint's exclusions rather than left for a reader to wonder about.</p>
 *
 * <h2>What is a spelling, and what is not</h2>
 *
 * <p>Four spellings of one non-root path: the leading slash is optional (legacy rows predate
 * {@code ShPostUnifiedWriteService.normalizeUrl}, which guarantees it) and so is the trailing one
 * (SH467 stores whichever the source declared). The empty string is <b>not</b> a spelling of
 * {@code /} — it is an <em>absent</em> URL, which SH38 says an uploaded file post legitimately has,
 * and matching it would resolve a site's home page to whichever unnamed row sorted first.</p>
 *
 * @author Alexandre Oliveira
 */
public final class ShFurlSpellings {

    private ShFurlSpellings() {
        // Static — this is a vocabulary, not a collaborator.
    }

    /**
     * The identity form: repeated slashes collapsed, exactly one leading slash, no trailing one
     * (the root stays {@code /}).
     *
     * <p>This is the body {@code ShAddress.normalizePath} used to carry, moved here so the
     * collapser and the candidate generator cannot drift apart — {@code ShAddress} now delegates.
     * The reason it collapses is unchanged and still binding: the render digest hashes the
     * <em>address</em>, so {@code /blog} and {@code /blog/} hashing differently would break its
     * stability. That argument is what separates the identity form from the <b>stored</b> form,
     * which is SH467's whole design: the declared spelling is a property of the write, not of the
     * address.</p>
     */
    public static String canonical(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String collapsed = collapse(path);
        while (collapsed.length() > 1 && collapsed.endsWith("/")) {
            collapsed = collapsed.substring(0, collapsed.length() - 1);
        }
        return collapsed;
    }

    /**
     * A declared spelling as it will be <b>stored</b>: repeated slashes collapsed, exactly one
     * leading slash, and the trailing one <em>kept</em>.
     *
     * <p>This is {@link #canonical}'s sibling and its opposite where it matters. SH467's design
     * turns on the two being different things: the address is the identity and collapses, the
     * stored {@code furl} is what the source declared and does not. Both still normalise the parts
     * of a path nobody declares on purpose — {@code //x} and {@code /x} are one spelling, not two —
     * so the only difference two calls can produce is the character SH467 is about.</p>
     *
     * <p>{@code ShPostUnifiedWriteService.normalizeUrl} would add the leading slash on its own and
     * nothing else, which is why the storage layer needed no change; this exists so the value
     * handed to it is already the one form, rather than each caller trimming differently.</p>
     */
    public static String stored(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return collapse(path);
    }

    /**
     * Every stored {@code furl} spelling that names the same post as {@code path}, in the order a
     * reader must try them.
     *
     * <p><b>Requested spelling first</b>, then the canonical form, then the remaining two in a
     * fixed order. The order is not cosmetic and it is not arbitrary (P5): once SH467 stores a
     * trailing slash, a site can hold a post at {@code /x/} <em>and</em> a stale one at {@code /x},
     * and asking in a fixed alphabetical order would resolve a request for {@code /x/} to the row
     * that is not it. Asking for what was actually requested first makes the answer the caller's
     * own spelling whenever that spelling exists, and a tolerated variant only when it does not —
     * which is the same rule {@code ShPageRenderService} applies at the route, one layer down.</p>
     *
     * <p>Two posts genuinely sharing one spelling is a different defect and is already reported:
     * {@code duplicate-url}, an SH99 error since SH100, refused on the write side by
     * {@code ShAddressResolver.DuplicateUrlException}.</p>
     */
    public static List<String> candidates(String path) {
        String canonical = canonical(path);
        if (canonical.length() <= 1) {
            // The root has no other spelling — "" is an absent URL, not an empty one.
            return List.of(canonical);
        }
        String bare = canonical.substring(1);
        List<String> ordered =
                new ArrayList<>(List.of(canonical, canonical + "/", bare, bare + "/"));
        int requested = ordered.indexOf(collapse(path));
        if (requested > 0) {
            ordered.add(0, ordered.remove(requested));
        }
        return List.copyOf(ordered);
    }

    /**
     * The other trailing-slash spelling of a stored {@code furl}, or empty when there is none.
     *
     * <p>SH467's route half asks this: a request that misses looks for the page under the opposite
     * spelling and answers a permanent redirect <em>toward the stored form</em> rather than toward
     * a fixed one, so the canonical URL is whichever one the content declares. {@code /} has no
     * other form, which is why the home page falls through to the ordinary 404.</p>
     */
    public static Optional<String> otherSlashForm(String furl) {
        if (furl == null || furl.length() <= 1) {
            return Optional.empty();
        }
        return Optional.of(furl.endsWith("/")
                ? furl.substring(0, furl.length() - 1)
                : furl + "/");
    }

    /** Trims and collapses repeated slashes, guaranteeing a leading one and preserving the rest. */
    private static String collapse(String path) {
        String collapsed = path.trim().replaceAll("/{2,}", "/");
        return collapsed.startsWith("/") ? collapsed : "/" + collapsed;
    }
}
