/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * The one place the agent surface decides <em>which sites this credential can see</em>.
 *
 * <h2>Why it is extracted</h2>
 *
 * <p>By the time SH83 needed it, the SH2 per-site allow-list was implemented three
 * times — in the context pack, the address resolver and the batch engine — identically
 * and by hand. That is fine right up until someone fixes one of them. An access rule
 * that can drift between call sites is the kind of duplication worth removing even
 * when every copy is currently correct, because the failure mode is silent and it is a
 * security one.</p>
 *
 * <p>The rule itself is unchanged and matches the CDA's: a site outside the allow-list
 * is <b>absent</b>, never forbidden, so a scoped credential cannot use the difference
 * between 403 and 404 to prove a site exists.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShAgentSiteAccess {

    private static final byte ROOT_FOLDER = 1;

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;

    public ShAgentSiteAccess(ShSiteRepository shSiteRepository, ShFolderRepository shFolderRepository) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
    }

    /** Every readable site, sorted by name so any listing built on it is deterministic. */
    public List<ShSite> readable() {
        Optional<ShApiTokenContext> token = ShApiTokenContext.current();
        return shSiteRepository.findAll().stream()
                .filter(candidate -> token.isEmpty() || token.get().allowsSite(candidate.getId()))
                .sorted(Comparator.comparing(candidate -> nullSafe(candidate.getName())))
                .toList();
    }

    /**
     * The readable sites a selector names.
     *
     * @param selector site name, site id, or a {@code site:} address (SH79) — one
     *                 vocabulary whichever surface the caller came from; {@code null}
     *                 or blank selects all
     * @throws ShAgentContextService.UnknownSiteException listing what <em>is</em> readable
     */
    public List<ShSite> readable(String selector) {
        List<ShSite> all = readable();
        if (selector == null || selector.isBlank()) {
            return all;
        }
        String wanted = selector.startsWith("site:") ? ShAddress.parse(selector).site() : selector;
        List<ShSite> matched = all.stream().filter(candidate -> matches(candidate, wanted)).toList();
        if (matched.isEmpty()) {
            throw new ShAgentContextService.UnknownSiteException(selector,
                    all.stream().map(ShSite::getName).toList());
        }
        return matched;
    }

    /** Exactly one readable site, or the teaching 404. */
    public ShSite require(String selector) {
        return readable(selector).get(0);
    }

    /** Present only when readable — used where absence is a normal outcome. */
    public Optional<ShSite> find(String selector) {
        if (selector == null || selector.isBlank()) {
            return Optional.empty();
        }
        return readable().stream().filter(candidate -> matches(candidate, selector)).findFirst();
    }

    /** The folder every sitemap path is relative to. */
    public Optional<ShFolder> rootFolder(ShSite site) {
        return shFolderRepository.findRootFolder(site);
    }

    private static boolean matches(ShSite site, String wanted) {
        return wanted.equals(site.getId()) || wanted.equalsIgnoreCase(nullSafe(site.getName()));
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
