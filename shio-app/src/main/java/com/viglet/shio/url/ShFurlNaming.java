/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.tenant.ShTenantContext;

/**
 * The one writer of "which friendly URL is free in this site" (SH651).
 *
 * <h2>Why this is a component and not a private method</h2>
 *
 * <p>The rule was written once, for the copy path (SH202), and lived as
 * {@code ShObjectWriteService.freeNaming}'s second half. When SH651 found that
 * {@code ShPostUnifiedWriteService.create} assigned no {@code furl} at all — every folder,
 * site and copy write derives one, post create was the single write that did not — the
 * choice was to reimplement the loop beside it or to have one writer. Four times already a
 * console copy of a shared verb was the careless one, which is the whole subject of SH245;
 * making the fifth copy to fix the fourth would have been a poor trade.
 *
 * <h2>What "free" means, and what it deliberately does not</h2>
 *
 * <p>Free is checked across <b>every spelling</b> a tolerant reader resolves (SH583), across
 * <b>both state rows</b> — a URL held only by a {@code PUBLISHED} row is taken, even though no
 * draft mentions it — and, since SH243, across the <b>trash</b>.
 *
 * <h2>Free and occupied are not each other's negation (SH243)</h2>
 *
 * <p>{@link #isFree} answers a <em>naming</em> question and {@link #liveOccupant} answers a
 * <em>refusal</em> question, and they deliberately disagree about a trashed post. Deriving a
 * name has to skip a URL that is only sleeping: a copy that took {@code home-2} while a trashed
 * {@code home-2} still held it produced the {@code duplicate-url} defect SH202 removed, the
 * moment somebody untrashed the original. Refusing an operation must not name one, because a
 * refusal points the curator at an occupant, and pointing them at a row no listing shows is a
 * dead end rather than an instruction (P6).
 *
 * <p>So the trash makes a URL <b>unusable for a new name</b> without making it <b>occupied</b>.
 * The gap that leaves — a URL a trashed post will want back — is closed on the other side, by
 * the restore refusing rather than by the naming loop guessing.
 *
 * <p>A <b>blank</b> URL is free. An uploaded file post never gets one (SH38, which is why
 * SH172 needed the {@code id:} address fallback), so blank is a legitimate shared value
 * rather than a collision — and a post whose title is blank is left blank rather than given
 * an invented address.
 *
 * <p>This says nothing about <b>titles</b>. {@code furl} and title are separate namespaces
 * (SH79), and SH139's point is that renaming a post does not re-derive its URL; the copy
 * path numbers both with two independent counters for that reason, and the title half stays
 * where it is because only a copy needs it.
 */
@Component
public class ShFurlNaming {

    private static final List<ShPostState> ALL_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShFurlNaming(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /**
     * The first free URL derived from {@code rawTitle}: the slug, then {@code -2},
     * {@code -3}, and so on.
     *
     * <p>Terminates because every {@code n} yields a distinct candidate while the set of
     * taken URLs is finite. The bound is the number of posts in the site already answering
     * at that slug, which is the honest one rather than an invented ceiling.</p>
     *
     * @return the free URL, or blank where there is no title to derive one from
     */
    public String freeFurl(String siteId, String rawTitle) {
        return freeFurl(siteId, null, rawTitle);
    }

    /**
     * SH1333 — the same, beneath {@code under}: {@code under = "about"} and a title of
     * {@code Our team} derive {@code about/our-team}, then {@code about/our-team-2}.
     *
     * <p>A page's URL is its friendly URL, not its folder's path, so a page "under" another one is
     * a URL that starts with the parent's. The console asks for it from a row of the pages view;
     * the loop stays here so the console never guesses whether a URL is free.</p>
     *
     * @param under the parent path, with or without slashes; blank derives at the site's root
     */
    public String freeFurl(String siteId, String under, String rawTitle) {
        String slug = rawTitle == null ? "" : ShURLFormatter.format(rawTitle);
        if (slug == null || slug.isBlank()) {
            return "";
        }
        String parent = under == null ? "" : under.strip().replaceAll("^/+|/+$", "");
        String base = parent.isEmpty() ? slug : parent + "/" + slug;
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? base : base + "-" + n;
            if (isFree(siteId, candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Whether nothing in {@code siteId} holds {@code furl} — <b>trash included</b> (SH243).
     *
     * <p>Ask this to <em>derive</em> a name. To refuse an operation, ask {@link #liveOccupant}
     * instead: the class comment says why the two answer differently about a trashed post.</p>
     */
    public boolean isFree(String siteId, String furl) {
        if (siteId == null || furl == null || furl.isBlank()) {
            return true;
        }
        if (liveOccupant(siteId, furl, null).isPresent()) {
            return false;
        }
        // SH243 — the trash, which every finder above is blind to by construction: the entity
        // carries @SQLRestriction("deleted_date is null"). Only the exact spelling is asked of
        // it, and on purpose: a trashed post is not reachable, so the SH583 hazard it guards
        // against — a stored `/x/` a tolerant reader also answers for `x` — cannot arise while
        // the row sleeps. What matters is the URL it will claim back on restore, which is the
        // one it stores.
        return shPostUnifiedRepository.countTrashedByFurl(siteId, furl, tenantId()) == 0;
    }

    /**
     * The post in {@code siteId} that <b>reachably</b> holds {@code furl}, other than
     * {@code exceptPostId} — the occupant a refusal can name (SH139, SH243).
     *
     * <p>Live only, and that is the point: this is what {@code ShObjectWriteService}'s move
     * refusal and {@link com.viglet.shio.migration.unified.ShPostUnifiedWriteService}'s restore
     * refusal both need, and neither can usefully tell a curator "a post you cannot see holds
     * this". SH245's subject is what happens when a shared verb is copied per call site; this
     * is the third caller of the SH583 spelling walk, so it stops being copied here.</p>
     *
     * @param exceptPostId the post being moved or restored, which does not collide with itself;
     *                     {@code null} when the question is about a URL nobody holds yet
     */
    public Optional<ShPostUnified> liveOccupant(String siteId, String furl, String exceptPostId) {
        if (siteId == null || furl == null || furl.isBlank()) {
            return Optional.empty();
        }
        // SH583 — every spelling: checking only the exact one would derive an "unused" URL
        // that a tolerant reader already resolves to something else.
        for (String candidate : ShFurlSpellings.candidates(furl)) {
            for (ShPostState state : ALL_STATES) {
                Optional<ShPostUnified> held = shPostUnifiedRepository
                        .findByShSite_IdAndFurlAndState(siteId, candidate, state)
                        .filter(other -> exceptPostId == null || !exceptPostId.equals(other.getId()));
                if (held.isPresent()) {
                    return held;
                }
            }
        }
        return Optional.empty();
    }

    /** Native queries bypass {@code @TenantId}, so the trash question carries its own tenant. */
    private static String tenantId() {
        String tenant = ShTenantContext.getTenantId();
        return tenant != null ? tenant : ShTenantContext.DEFAULT_TENANT;
    }
}
