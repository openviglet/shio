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
 * The name a create asked for belongs to a site in the trash (SH648).
 *
 * <h2>The refusal a soft-delete has to write</h2>
 *
 * <p>{@code ShSite.name} is {@code unique}, so a trashed site keeps holding its name — that is the
 * price of the row outliving the delete, and it is the second question SH648's design named. Left
 * alone it is the worst kind of failure: the create passes every service check, reaches the
 * database and comes back as a constraint violation with no body, which the caller meets after
 * they have already decided the old site was gone.</p>
 *
 * <p>So the honest answer names <b>where the name went</b>, which the caller cannot see from any
 * listing: the site is in the trash and the two ways out are both one action they already have.
 * Restoring it is usually what they meant — a curator who re-creates a site they deleted an hour
 * ago is asking for their content back, not for a blank one.</p>
 *
 * <p>409 rather than 400: nothing about the request is malformed, and the same name will work
 * once the conflict is resolved. Same status the live-name collision has always answered, which
 * keeps a caller that only reads the code on the path it was already on.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShSiteNameHeldByTrashException extends ShTeachingException {

    public ShSiteNameHeldByTrashException(String name, String field) {
        super(ShProblem.conflict("site-name-held-by-trash",
                        "A site in the trash still holds that " + field)
                .withDetail("The " + field + " '" + name + "' belongs to a site that was deleted "
                        + "but not purged. A trashed site keeps its name and its friendly URL, so "
                        + "nothing else can take them while it is recoverable.")
                .withFix("Restore it with POST /api/v2/site/{id}/untrash if you want its content "
                        + "back, or purge it with DELETE /api/v2/site/{id}/purge to free the "
                        + field + " for good. GET /api/v2/site/trash lists what is in there."));
    }
}
