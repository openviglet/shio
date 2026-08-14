/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

/**
 * The one writer of "what address a new site gets" (SH744).
 *
 * <h2>Why it had to be extracted</h2>
 *
 * <p>There were two, and they disagreed. {@code ShSiteImportService.uniqueFurl} asked whether the
 * furl was taken and appended a short id; {@code ShSiteWriteService.create} took
 * {@code payload.getFurl()}, or the name's slug, and saved it unchecked — so the address a new site
 * got depended on which door it came in through, and the writer's own javadoc recorded the result
 * as a fact rather than a defect: <em>furl is not unique</em>.</p>
 *
 * <p>It reaches delivery. {@link ShSiteRepository#findByFurl} returns <b>one</b> {@code ShSite} and
 * both {@code ShPageRenderService} and {@code ShSiteAssetDelivery} resolve through it, so two sites
 * at one furl means the database chooses which one answers — the SH278/SH279 shape this project has
 * twice paid for: invisible on H2, wrong on another engine.</p>
 *
 * <h2>The rule is the posts' rule, one level up</h2>
 *
 * <p>SH81/SH651 already settled this for a post and SH744 did not re-decide it:</p>
 *
 * <ul>
 *   <li><b>A declared address is identity</b> and is never renumbered. If it is taken, the write is
 *       <em>refused</em> — {@link #requireFree} — because keeping the rule and keeping the caller's
 *       value would mean putting a second site on one address.</li>
 *   <li><b>A derived address has no claim</b>, so it is numbered until free — {@link #freeFurl}.
 *       That is SH202's reasoning for a copy: a new thing has no right to any particular URL.</li>
 * </ul>
 *
 * <p>An import is the second case, not the first. It is a <em>clone</em> (SH68), so the exported
 * furl is a base to derive from rather than a promise this instance made — which is why the import
 * keeps suffixing and does not start refusing halfway through a package.</p>
 *
 * <p>Numbered {@code -2}, {@code -3} rather than the import's old random short id: a suffix a human
 * can predict is one they can type, and it matches what a post already does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShSiteFurlNaming {

    private final ShSiteRepository shSiteRepository;

    public ShSiteFurlNaming(ShSiteRepository shSiteRepository) {
        this.shSiteRepository = shSiteRepository;
    }

    /** Whether no site answers at {@code furl}. A blank furl is nobody's address. */
    public boolean isFree(String furl) {
        if (furl == null || furl.isBlank()) {
            return true;
        }
        return shSiteRepository.findByFurl(furl) == null;
    }

    /**
     * A free address derived from {@code base}, or from {@code name} when there is no base.
     *
     * <p>Terminates for {@link ShFurlNaming#freeFurl}'s reason: every {@code n} yields a distinct
     * candidate and the set of taken addresses is finite.</p>
     *
     * @return the free furl, or blank when there is nothing to derive one from
     */
    public String freeFurl(String base, String name) {
        String start = base == null || base.isBlank() ? ShURLFormatter.format(name) : base;
        if (start == null || start.isBlank()) {
            return "";
        }
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? start : start + "-" + n;
            if (isFree(candidate)) {
                return candidate;
            }
        }
    }

    /**
     * Refuse when a site already answers at a <b>declared</b> address (SH744).
     *
     * @throws ShSiteFurlConflictException when {@code furl} is taken
     */
    public void requireFree(String furl) {
        ShSite occupant = furl == null || furl.isBlank() ? null : shSiteRepository.findByFurl(furl);
        if (occupant != null) {
            throw new ShSiteFurlConflictException(furl, occupant.getName());
        }
    }
}
