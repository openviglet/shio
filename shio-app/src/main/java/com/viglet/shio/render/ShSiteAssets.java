/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * SH437 — a {@code File} post, at the path the source it was captured from used.
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>A static file's bytes are public at {@code /file_source/<postId>/<fileName>} (SH38), keyed by
 * the post's id and by nothing a source ever chose. SH429 asked for a choice between keeping the
 * source's URL space and rewriting the replica's {@code src} attributes, and called the first
 * closer to what a fidelity replica means — then measuring it against the code showed it was not
 * <em>available</em>: nothing mapped {@code /viglet-logo.png} to a {@code File} post, so uploading
 * one left {@code <img src="/viglet-logo.png">} exactly as broken as leaving the bytes on disk.
 * That is a delivery feature and not a CLI change, which is why SH429 shipped the half the tree can
 * prove and stopped. This is the other half.</p>
 *
 * <h2>Why the folder chain is the address, and not an invention</h2>
 *
 * <p>A {@code File} post has <b>no friendly URL</b> — {@code ShStaticFileService.upload} never
 * assigns one, which is why SH172 needed the {@code id:} fallback — so SH145 already had to answer
 * "what addresses an asset" for the file projection, and answered <b>folder chain plus file
 * name</b>: the pair {@code fileExists} checks and the pair the console's pre-upload check uses.
 * {@code shio/assets/<site><chain>/<name>} is that answer written down. So this resolver is not a
 * new addressing scheme; it is the existing one read in the other direction, which is what makes
 * {@code /img/hero.png} in a captured page resolve to the asset {@code shio push --content} put at
 * {@code shio/assets/<site>/img/hero.png}.</p>
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It resolves <b>one</b> post and never a list, and it matches the file name exactly as the
 * storage layer sanitizes it, so a caller cannot walk a site's folders by probing. It is
 * case-insensitive on folder names for the same reason {@link com.viglet.shio.api.agent
 * .ShAddressResolver} is — a human types these — and case-<em>sensitive</em> on the file name,
 * because that is what the file system and every CDN in front of it will be.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShSiteAssets {

    /** DRAFT is included: a replica is converted in draft, and a preview of one has to show it. */
    private static final Set<ShPostState> READABLE = Set.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final com.viglet.shio.persistence.repository.site.ShSiteRepository shSiteRepository;

    public ShSiteAssets(ShFolderRepository shFolderRepository,
                        ShPostUnifiedRepository shPostUnifiedRepository,
                        com.viglet.shio.persistence.repository.site.ShSiteRepository shSiteRepository) {
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteRepository = shSiteRepository;
    }

    /** The same lookup from a site id, for a caller that has one and not the entity (SH437). */
    @Transactional(readOnly = true)
    public Optional<ShPostUnified> findInSite(String siteId, String path, ShPostState state) {
        return shSiteRepository.findById(siteId).flatMap(site -> lookup(site, path, state));
    }

    /**
     * The {@code File} post a source path names in this site, if there is one.
     *
     * @param site  the site the path is relative to
     * @param path  a source-relative path, with or without its leading slash — {@code /img/hero.png}
     * @param state the state to read; {@code PUBLISHED} never sees a draft asset (SH278's rule)
     */
    @Transactional(readOnly = true)
    public Optional<ShPostUnified> find(ShSite site, String path, ShPostState state) {
        return lookup(site, path, state);
    }

    /**
     * The walk itself, private so both entry points carry the transaction rather than one of them
     * borrowing the other's through {@code this} — which would not cross the proxy at all.
     */
    private Optional<ShPostUnified> lookup(ShSite site, String path, ShPostState state) {
        if (site == null || path == null || path.isBlank() || path.endsWith("/")) {
            return Optional.empty();
        }
        List<String> segments = segmentsOf(path);
        if (segments.isEmpty()) {
            return Optional.empty();
        }
        String fileName = ShFileStorageService.sanitizeFileName(segments.removeLast());
        Optional<ShFolder> folder = shFolderRepository.findRootFolder(site);
        for (String segment : segments) {
            if (folder.isEmpty()) {
                return Optional.empty();
            }
            folder = shFolderRepository.findByParentFolderOrderByPositionAsc(folder.get()).stream()
                    .filter(child -> segment.equalsIgnoreCase(child.getName() == null ? "" : child.getName()))
                    .findFirst();
        }
        return folder.flatMap(found -> fileIn(found, fileName, state));
    }

    private Optional<ShPostUnified> fileIn(ShFolder folder, String fileName, ShPostState state) {
        Set<ShPostState> states = state == ShPostState.PUBLISHED ? Set.of(ShPostState.PUBLISHED) : READABLE;
        return shPostUnifiedRepository.findByShFolderAndStateIn(folder, states).stream()
                .filter(post -> post.getShPostType() != null
                        && ShSystemPostType.FILE.equals(post.getShPostType().getName()))
                .filter(post -> fileName.equals(post.getTitle()))
                // A DRAFT and a PUBLISHED row of one asset are the same bytes on disk, so either
                // answers; taking the first keeps this a lookup rather than a state decision.
                .findFirst();
    }

    /**
     * A path's segments, minus the empties a leading or doubled slash produces.
     *
     * <p>{@code ..} is dropped rather than resolved: this walks a folder tree and not a file
     * system, so there is nothing above the site root to escape to, and honouring it would let a
     * path climb out of the site it named.</p>
     */
    private static List<String> segmentsOf(String path) {
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        for (String raw : path.split("/")) {
            String segment = raw.trim();
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                continue;
            }
            segments.add(segment);
        }
        return segments;
    }
}
