/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.url;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A create declared an address a live site already answers at (SH744).
 *
 * <h2>Why refused rather than numbered</h2>
 *
 * <p>SH81/SH651's rule, one level up from a post: <b>a declared address is identity</b>. The caller
 * did not ask for "somewhere sensible", it asked for <em>this</em> URL — the one in the link they
 * are about to send — so quietly saving it as {@code acme-2} is the surprise P7 exists to prevent.
 * A site whose furl was <em>derived</em> from its name is the other case and is numbered without
 * comment, because a name that was never promised has no claim on any particular address.</p>
 *
 * <p>The alternative was what {@code ShSiteImportService} used to do alone: append a short id and
 * say nothing. That is right for a clone, where the export's furl is a starting point rather than a
 * promise this instance made, and wrong for a curator naming a site in front of a form.</p>
 *
 * <p>Mirrors {@code ShSiteRestoreConflictException} (SH648), which refuses the other direction —
 * a restore whose sleeping address was taken while it slept.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShSiteFurlConflictException extends ShTeachingException {

    public ShSiteFurlConflictException(String furl, String occupantName) {
        super(ShProblem.conflict("site-furl-conflict",
                        "Another site already answers at /" + furl)
                .withDetail(occupantName == null || occupantName.isBlank()
                        ? "A live site holds the address /" + furl + "."
                        : "The site '" + occupantName + "' holds the address /" + furl + ".")
                .withFix("Choose a different `furl`, or leave it out and Shio derives a free one "
                        + "from the name — a declared address is kept as given rather than "
                        + "numbered, which is why this is refused instead of renamed.")
                .withExample("{\"name\": \"Acme\", \"furl\": \"" + furl + "-2\"}"));
    }
}
