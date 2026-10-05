/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH1217 — one row of {@code GET /api/v2/site}: the site as it always serialized, plus whether it
 * is alive.
 *
 * <p>The site is unwrapped rather than nested so every field a caller already reads stays where it
 * was; {@code pages} and {@code lastChange} are additions. Built by {@link ShSiteListingService}
 * from two grouped reads over every site, never one read per row.</p>
 */
public final class ShSiteListing {

    /**
     * Pages by state. A post with a pending draft counts under both, because it is live and it is
     * waiting to be published.
     */
    public record Pages(long published, long draft) {
        static final Pages NONE = new Pages(0, 0);
    }

    /**
     * The site's newest audit row.
     *
     * @param agent whether an agent run made it — a console session is the curator's own (SH996)
     */
    public record LastChange(Instant at, String actor, boolean agent) {
    }

    /**
     * SH1218 — the stored picture of the site's published home.
     *
     * @param digest the home's appearance digest the picture was taken at
     */
    public record Thumbnail(String url, String digest, Instant at) {
    }

    private final ShSite site;
    private final Pages pages;
    private final LastChange lastChange;
    private final Thumbnail thumbnail;

    public ShSiteListing(ShSite site, Pages pages, LastChange lastChange, Thumbnail thumbnail) {
        this.site = site;
        this.pages = pages;
        this.lastChange = lastChange;
        this.thumbnail = thumbnail;
    }

    /** {@code null} until a picture of the published home is stored; the grid shows initials. */
    public Thumbnail getThumbnail() {
        return thumbnail;
    }

    @JsonUnwrapped
    public ShSite getSite() {
        return site;
    }

    public Pages getPages() {
        return pages;
    }

    /** {@code null} for a site nothing has been recorded against yet. */
    public LastChange getLastChange() {
        return lastChange;
    }
}
