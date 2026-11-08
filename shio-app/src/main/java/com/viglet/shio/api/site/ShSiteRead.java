/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * SH1361 — {@code GET /api/v2/site/{id}}: the site as it always serialized, plus where a visitor
 * sees it.
 *
 * <p>Unwrapped, as {@link ShSiteListing} is, so every field a caller already reads stays where it
 * was. {@code publishedUrl} is the server's answer ({@code ShSiteHost.publishedUrl}), which the
 * console used to mirror by hand and could not, once a site had a host of its own whose service
 * domain only the server knows.</p>
 */
public final class ShSiteRead {

    private final ShSite site;
    private final String publishedUrl;

    public ShSiteRead(ShSite site, String publishedUrl) {
        this.site = site;
        this.publishedUrl = publishedUrl;
    }

    @JsonUnwrapped
    public ShSite getSite() {
        return site;
    }

    public String getPublishedUrl() {
        return publishedUrl;
    }
}
