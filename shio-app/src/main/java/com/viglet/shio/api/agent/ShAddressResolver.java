/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.spring.security.auth.ShApiTokenContext;

/**
 * Resolves a {@link ShAddress} to a concrete object (SH79).
 *
 * <p>This is what makes an address <em>actionable</em>. The context pack (SH77)
 * already gives an agent everything needed to <b>construct</b> an address — the site
 * name, each folder's path, each post's url — so nothing new is emitted there (that
 * would just duplicate information and cost tokens). What was missing is the other
 * direction: turning the address back into the id that today's id-keyed console and
 * CDA endpoints still require. Once the write surfaces land (SH81/SH82) they take the
 * address directly and this becomes an internal step rather than a call.</p>
 *
 * <p><b>Enforcement.</b> Resolution honors the SH2 per-site allow-list exactly as the
 * CDA does, and an address outside it resolves to "not found" rather than "forbidden"
 * — a scoped key must not be able to prove that a site exists (the same rule
 * {@code ShCdaService} follows).</p>
 *
 * <p><b>Determinism over convenience.</b> The lenient {@code site:} form tries post
 * then folder; if <em>both</em> exist at that path it raises a 400 naming the two
 * explicit addresses instead of silently preferring one. A guess here would be a
 * coin-flip that only shows up as wrong content much later.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
/*
 * `noRollbackFor` is load-bearing for SH171. Every method here is a read-only lookup, and a
 * teaching exception from one means "not found" — never a partial write. Without it, a caller
 * that legitimately *handles* an unresolvable address (a multi-address read reports it per row
 * rather than failing the call) would still see its own commit blow up with
 * UnexpectedRollbackException, because this method's interceptor had already marked the shared
 * transaction rollback-only on the way out.
 *
 * An enclosing *write* transaction is unaffected: the batch service's own boundary has no such
 * exemption, so an op that fails to resolve still rolls the whole batch back — which
 * ShAgentBatchTest asserts directly.
 */
@Service
@Transactional(readOnly = true, noRollbackFor = ShTeachingException.class)
public class ShAddressResolver {

    private static final byte ROOT_FOLDER = 1;
    private static final List<ShPostState> BOTH_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShAddressResolver(ShSiteRepository shSiteRepository,
                             ShFolderRepository shFolderRepository,
                             ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    /** Parses and resolves in one step. */
    public ShAgentResolved resolve(String raw) {
        return resolve(ShAddress.parse(raw));
    }

    /**
     * @throws ShTeachingException 404 when nothing is there (listing what is, with a
     *         nearest-match suggestion), or 400 when a lenient address is ambiguous
     */
    public ShAgentResolved resolve(ShAddress address) {
        return switch (address.kind()) {
            case ID -> resolveId(address);
            case SITE -> address.isSiteRoot() ? resolveSite(address) : resolveLenient(address);
            case FOLDER -> resolveFolder(address).orElseThrow(() -> notFound(address, folderPaths(address)));
            case POST -> resolvePost(address).orElseThrow(() -> notFound(address, postUrls(address)));
        };
    }

    /** Resolution without the exceptions — for callers that treat absence as normal. */
    public Optional<ShAgentResolved> find(ShAddress address) {
        try {
            return Optional.of(resolve(address));
        } catch (ShTeachingException e) {
            return Optional.empty();
        }
    }

    // --- per-kind resolution ---

    private ShAgentResolved resolveId(ShAddress address) {
        String id = address.id();
        Optional<ShSite> site = shSiteRepository.findById(id).filter(this::readable);
        if (site.isPresent()) {
            return resolved("site", site.get().getId(), ShAddress.site(site.get().getName()),
                    site.get().getName(), null);
        }
        Optional<ShFolder> folder = shFolderRepository.findById(id)
                .filter(candidate -> readable(candidate.getShSite()));
        if (folder.isPresent()) {
            return folderResolved(folder.get());
        }
        List<ShPostUnified> posts = shPostUnifiedRepository.findById(id).stream()
                .filter(post -> readable(post.getShSite()))
                .toList();
        if (!posts.isEmpty()) {
            return postResolved(draftPreferred(posts));
        }
        throw notFound(address, List.of());
    }

    private ShAgentResolved resolveSite(ShAddress address) {
        ShSite site = site(address.site()).orElseThrow(() -> notFound(address, siteNames()));
        return resolved("site", site.getId(), ShAddress.site(site.getName()), site.getName(),
                rootFolder(site).map(ShFolder::getId).orElse(null));
    }

    /**
     * The {@code site:} form: post first (the common intent — a URL an agent read off
     * a page), folder second, and a hard stop if both match.
     */
    private ShAgentResolved resolveLenient(ShAddress address) {
        Optional<ShAgentResolved> post = resolvePost(
                new ShAddress(ShAddress.Kind.POST, address.site(), address.path()));
        Optional<ShAgentResolved> folder = resolveFolder(
                new ShAddress(ShAddress.Kind.FOLDER, address.site(), address.path()));
        if (post.isPresent() && folder.isPresent()) {
            throw new AmbiguousAddressException(address, post.get().address(), folder.get().address());
        }
        return post.or(() -> folder).orElseThrow(() -> notFound(address, postUrls(address)));
    }

    private Optional<ShAgentResolved> resolvePost(ShAddress address) {
        Optional<ShSite> site = site(address.site());
        if (site.isEmpty()) {
            return Optional.empty();
        }
        // Both leading-slash spellings, mirroring the CDA's own furl candidates so an
        // address and a delivery URL never disagree about the same post.
        for (String candidate : furlCandidates(address.path())) {
            List<ShPostUnified> rows = BOTH_STATES.stream()
                    .map(state -> shPostUnifiedRepository
                            .findByShSite_IdAndFurlAndState(site.get().getId(), candidate, state))
                    .flatMap(Optional::stream)
                    .toList();
            if (!rows.isEmpty()) {
                return Optional.of(postResolved(draftPreferred(rows)));
            }
        }
        return Optional.empty();
    }

    private Optional<ShAgentResolved> resolveFolder(ShAddress address) {
        Optional<ShSite> site = site(address.site());
        if (site.isEmpty()) {
            return Optional.empty();
        }
        Optional<ShFolder> current = rootFolder(site.get());
        if (current.isEmpty()) {
            return Optional.empty();
        }
        for (String segment : segments(address.path())) {
            current = children(current.get()).stream()
                    .filter(child -> segment.equalsIgnoreCase(nullSafe(child.getName())))
                    .findFirst();
            if (current.isEmpty()) {
                return Optional.empty();
            }
        }
        return current.map(this::folderResolved);
    }

    // --- emitting addresses (the inverse direction) ---

    /** The canonical address of a folder — its chain of folder names under its site. */
    public ShAddress addressOf(ShFolder folder) {
        List<String> names = new ArrayList<>();
        ShFolder current = folder;
        while (current != null && current.getRootFolder() != ROOT_FOLDER) {
            names.add(0, nullSafe(current.getName()));
            current = current.getParentFolder();
        }
        // SH128: the owning site is on the *root* folder, so it is walked to, never
        // read off this folder — a nested folder read directly yields `folder:null/...`,
        // an address that resolves to nothing and teaches the agent nothing either.
        ShSite owner = ShFolderSite.of(folder);
        return ShAddress.folder(owner == null ? null : owner.getName(),
                "/" + String.join("/", names));
    }

    /**
     * The canonical address of a post — its site plus its friendly URL, or {@code id:} when it
     * has no URL to be addressed by (SH172).
     *
     * <h2>Why the fallback is here and not in each reader</h2>
     *
     * <p>SH79's premise is that an address names <em>one</em> post. A post with no friendly URL
     * has no address under that scheme, and rendering it as {@code post:<site>/} anyway broke the
     * premise silently: three URL-less {@code File} posts came back from {@code find} as three
     * rows carrying the same address, differing only in title. Nothing an agent does with those
     * rows can work — a read returns one arbitrary post, a write is a guess — and the rows did
     * not say anything was wrong.</p>
     *
     * <p>SH100's lint already answered this correctly: it reports such posts as
     * {@code url-missing} and addresses them as {@code id:<uuid>}, because that is the only thing
     * that names them. The two surfaces disagreeing was the actual defect, so the fallback lives
     * in the one place both of them ask — the invariant is enforced rather than restated.</p>
     *
     * <p>A site-less post (the SH128 class) falls back for the same reason: {@code post:null/x}
     * resolves to nothing and teaches nothing.</p>
     */
    public ShAddress addressOf(ShPostUnified post) {
        String site = post.getShSite() == null ? null : post.getShSite().getName();
        String url = post.getFurl();
        if (site == null || site.isBlank() || url == null || url.isBlank()) {
            return ShAddress.id(post.getId());
        }
        return ShAddress.post(site, url);
    }

    // --- helpers ---

    private ShAgentResolved folderResolved(ShFolder folder) {
        return resolved("folder", folder.getId(), addressOf(folder), folder.getName(), null);
    }

    private ShAgentResolved postResolved(ShPostUnified post) {
        return new ShAgentResolved("post", post.getId(), addressOf(post).toString(),
                post.getTitle(),
                post.getShFolder() == null ? null : post.getShFolder().getId(),
                post.getShPostType() == null ? null : post.getShPostType().getName(),
                post.getState() == null ? null : post.getState().name());
    }

    private static ShAgentResolved resolved(String kind, String id, ShAddress address,
                                            String title, String parentId) {
        return new ShAgentResolved(kind, id, address.toString(), title, parentId, null, null);
    }

    private Optional<ShSite> site(String siteNameOrId) {
        if (siteNameOrId == null || siteNameOrId.isBlank()) {
            return Optional.empty();
        }
        return shSiteRepository.findAll().stream()
                .filter(candidate -> siteNameOrId.equals(candidate.getId())
                        || siteNameOrId.equalsIgnoreCase(nullSafe(candidate.getName())))
                .filter(this::readable)
                .min(Comparator.comparing(ShSite::getId));
    }

    /**
     * The SH2 allow-list, applied identically to the CDA. A disallowed site is
     * <em>absent</em>, never forbidden — so a scoped key cannot use a 403 to discover
     * that a site exists.
     */
    private boolean readable(ShSite site) {
        if (site == null) {
            return false;
        }
        return ShApiTokenContext.current()
                .map(token -> token.allowsSite(site.getId()))
                .orElse(true);
    }

    private Optional<ShFolder> rootFolder(ShSite site) {
        return shFolderRepository.findByShSiteAndRootFolder(site, ROOT_FOLDER).stream()
                .min(Comparator.comparing(ShFolder::getId));
    }

    private List<ShFolder> children(ShFolder folder) {
        return shFolderRepository.findByParentFolderOrderByPositionAsc(folder);
    }

    private static ShPostUnified draftPreferred(List<ShPostUnified> rows) {
        return rows.stream()
                .filter(row -> row.getState() == ShPostState.DRAFT)
                .findFirst()
                .orElse(rows.get(0));
    }

    private static List<String> furlCandidates(String path) {
        String normalized = ShAddress.normalizePath(path);
        return normalized.length() > 1
                ? List.of(normalized, normalized.substring(1))
                : List.of(normalized);
    }

    private static List<String> segments(String path) {
        if (path == null || "/".equals(path)) {
            return List.of();
        }
        return Arrays.stream(path.split("/")).filter(segment -> !segment.isBlank()).toList();
    }

    /** Sibling folder paths under the addressed site, for a {@code didYouMean}. */
    private List<String> folderPaths(ShAddress address) {
        return site(address.site())
                .flatMap(this::rootFolder)
                .map(root -> {
                    List<String> paths = new ArrayList<>();
                    collectFolderPaths(root, "", paths);
                    return paths;
                })
                .orElseGet(this::siteNames);
    }

    private void collectFolderPaths(ShFolder folder, String prefix, List<String> paths) {
        for (ShFolder child : children(folder)) {
            String path = prefix + "/" + nullSafe(child.getName());
            paths.add(path);
            if (paths.size() < 50) {
                collectFolderPaths(child, path, paths);
            }
        }
    }

    /** Sibling post URLs in the addressed folder's site, for a {@code didYouMean}. */
    private List<String> postUrls(ShAddress address) {
        return site(address.site())
                .map(site -> shPostUnifiedRepository
                        .findByShSite_IdAndStateIn(site.getId(), BOTH_STATES).stream()
                        .map(ShPostUnified::getFurl)
                        .filter(furl -> furl != null && !furl.isBlank())
                        .distinct()
                        .sorted()
                        .limit(50)
                        .toList())
                .orElseGet(this::siteNames);
    }

    private List<String> siteNames() {
        return shSiteRepository.findAll().stream()
                .filter(this::readable)
                .map(ShSite::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ShTeachingException notFound(ShAddress address, List<String> candidates) {
        return new UnresolvedAddressException(address, candidates);
    }

    /** A 404 that lists what <em>is</em> addressable, with the nearest match (SH80). */
    public static class UnresolvedAddressException extends ShTeachingException {

        public UnresolvedAddressException(ShAddress address, List<String> candidates) {
            super(ShProblem.of("unresolved-address", HttpStatus.NOT_FOUND,
                            "Nothing at '" + address + "'")
                    .withDetail("No " + address.kind().name().toLowerCase(Locale.ROOT)
                            + " is addressable there, for this credential.")
                    .withAllowed(candidates)
                    .withDidYouMeanFrom(address.path() != null ? address.path() : address.site())
                    .withFix(candidates.isEmpty()
                            ? "Read GET /api/v2/agent/context to see what exists."
                            : "Pick one of the listed paths, or read GET /api/v2/agent/context.")
                    .withExample("post:<site>/<friendly-url>"));
        }
    }

    /** A 400 for a lenient address that matches both a post and a folder (SH80). */
    public static class AmbiguousAddressException extends ShTeachingException {

        public AmbiguousAddressException(ShAddress address, String postAddress, String folderAddress) {
            super(ShProblem.of("ambiguous-address", HttpStatus.BAD_REQUEST,
                            "'" + address + "' matches both a post and a folder")
                    .withDetail("A post's friendly URL and a folder path are separate namespaces, "
                            + "and both are occupied here.")
                    .withAllowed(List.of(postAddress, folderAddress))
                    .withFix("Say which one you mean by using the `post:` or `folder:` scheme.")
                    .withExample(postAddress));
        }
    }
}
