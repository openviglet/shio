/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A site restore would put a second site on a friendly URL that is already answered (SH648).
 *
 * <h2>Why the furl and not the name</h2>
 *
 * <p>A trashed site's <b>name</b> cannot be taken while it sleeps: the column is unique and
 * {@link ShSiteNameHeldByTrashException} refuses the create that would have taken it. Its
 * {@code furl} carries no such constraint, so a second site can be created at the same address
 * and the collision only becomes visible at the moment somebody asks for the first one back.</p>
 *
 * <p>Refused rather than renamed, which is SH243's reason one level up: a restore asks for
 * <em>the thing that was there</em>, and every link, sitemap entry and search result that made
 * it worth restoring points at the address it had. Returning it somewhere else is the surprise
 * P7 exists to prevent — and unlike a copy (SH202), a restore has a claim on its own URL.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShSiteRestoreConflictException extends ShTeachingException {

    public ShSiteRestoreConflictException(String furl, String occupantName) {
        super(ShProblem.conflict("site-restore-url-conflict",
                        "Another site already answers at /" + furl)
                .withDetail("'" + occupantName + "' took the friendly URL /" + furl
                        + " while this site was in the trash.")
                .withFix("Change the friendly URL of '" + occupantName + "' and restore again, or "
                        + "purge this one with DELETE /api/v2/site/{id}/purge if the live site is "
                        + "the one you meant to keep — a restore returns the site to its own URL "
                        + "rather than numbering it."));
    }
}
