/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.verify.ShAgentRenderService;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH1218 — a site's published home, kept as a picture the sites grid can show.
 *
 * <h2>Who takes the picture</h2>
 *
 * <p>Not the instance: it does not run a browser (a standing non-goal). The console captures the
 * home it already renders in a frame and sends the bytes here. What the instance owns is the
 * question of <em>whether</em> a picture is needed, and that it can answer without a browser: the
 * published home's appearance digest, the same hash review compares (SH1002). A picture is stored
 * with the digest it was taken at, so publishing a home that looks the same stores nothing, and a
 * picture of a home that has since changed is refused rather than stored as current.</p>
 */
@Service
public class ShSiteThumbnailService {

    /** What a browser can capture a frame as and an {@code <img>} can show. */
    static final List<String> TYPES = List.of("image/webp", "image/png", "image/jpeg");

    /** Whether the stored picture shows the home as it is published now. */
    public record Status(String home, String stored, boolean fresh) {
    }

    /** What a send did. */
    public record Outcome(boolean stored, String digest) {
    }

    private final ShAgentRenderService shAgentRenderService;
    private final ShSiteThumbnailStore store;

    public ShSiteThumbnailService(ShAgentRenderService shAgentRenderService,
                                  ShSiteThumbnailStore store) {
        this.shAgentRenderService = shAgentRenderService;
        this.store = store;
    }

    public Status status(ShSite site) {
        String home = homeDigest(site).orElse(null);
        String stored = store.read(site.getId()).map(ShSiteThumbnailStore.Stored::digest).orElse(null);
        return new Status(home, stored, home != null && home.equals(stored));
    }

    /**
     * Keeps {@code bytes} as the site's picture if they were taken of the home as published now.
     *
     * @param digest the appearance digest the picture was taken at, from {@link #status}
     */
    public Outcome put(ShSite site, String digest, String contentType, byte[] bytes) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            throw ShSiteThumbnailRefusedException.type(type.isEmpty() ? "nothing" : type, TYPES);
        }
        if (bytes.length > store.maxBytes()) {
            throw ShSiteThumbnailRefusedException.size(bytes.length, store.maxBytes());
        }
        String home = homeDigest(site).orElseThrow(
                () -> ShSiteThumbnailRefusedException.noHome(site.getName()));
        if (!home.equals(digest)) {
            throw ShSiteThumbnailRefusedException.stale(digest, home);
        }
        if (store.read(site.getId()).map(stored -> home.equals(stored.digest())).orElse(false)) {
            return new Outcome(false, home);
        }
        store.write(site.getId(), home, type, bytes);
        return new Outcome(true, home);
    }

    /** The published home's appearance hash, absent when there is no published home. */
    Optional<String> homeDigest(ShSite site) {
        try {
            return Optional.ofNullable(shAgentRenderService.digest(
                    ShAddress.post(site.getName(), "/").toString(), null, "published")
                    .appearanceDigest());
        } catch (RuntimeException e) {
            // No home, or no published version of it: both mean there is nothing to picture yet.
            return Optional.empty();
        }
    }
}
