/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.object;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * The one place that answers "which site does this folder belong to?" (SH128).
 *
 * <h2>The invariant</h2>
 *
 * <p>Only a site's <b>root</b> folder carries {@code shSite}. {@code ShFolderWriteService}
 * deliberately nulls it on every child, and {@code ShObjectWriteService} does the same
 * when a subtree is moved. So the owning site of an arbitrary folder is found by
 * <em>walking to the root</em>, never by reading {@code folder.getShSite()}.</p>
 *
 * <h2>Why this is a class and not a three-line loop inlined where it is needed</h2>
 *
 * <p>Because it already was, four times over, and one of the copies was missing. Reading
 * {@code folder.getShSite()} directly is valid Java, passes review, and is wrong only
 * when the data is later queried along an axis the author was not thinking about —
 * {@code ShPostUnifiedWriteService#create} did exactly that and produced a site-less post
 * for every post created below a site's root. Those posts were invisible in the console
 * (which navigates by folder) and unreachable from the CDA {@code /query}, the by-url
 * lookup and the SH79 address resolver, all of which key on site.</p>
 *
 * <p>That is the fourth defect of the shape "compiles, reviews clean, wrong at runtime"
 * — after SH122's three {@code @TenantId} join hazards. The lesson recorded in
 * §VIII.10 is that this class of defect deserves a guard <em>per invariant</em>, added
 * the first time the invariant is violated. Centralising the walk is half of that guard;
 * {@code ShFolderSiteInvariantTest} is the other half, and it fails the build if a new
 * write path reintroduces the direct read.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFolderSite {

    private ShFolderSite() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The site that owns {@code folder}, found by walking up the parent chain.
     *
     * <p>Returns the first {@code shSite} encountered rather than insisting on reaching
     * a parent-less folder: a detached or partially-built subtree still has a defensible
     * answer, and returning {@code null} because the walk ran past the site would
     * reintroduce exactly the bug this exists to prevent.</p>
     *
     * @param folder any folder in a tree, or {@code null}
     * @return the owning site, or {@code null} when the tree carries none
     */
    public static ShSite of(ShFolder folder) {
        ShFolder current = folder;
        while (current != null) {
            if (current.getShSite() != null) {
                return current.getShSite();
            }
            current = current.getParentFolder();
        }
        return null;
    }

    /** The owning site's id, or {@code null}. Convenience for audit/event payloads. */
    public static String idOf(ShFolder folder) {
        ShSite site = of(folder);
        return site == null ? null : site.getId();
    }
}
