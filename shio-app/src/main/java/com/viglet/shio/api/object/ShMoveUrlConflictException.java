/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * A console move would land a post on a friendly URL another post in the
 * destination site already holds (SH139).
 *
 * <p>Before SH139 this case could not arise, because {@code moveTo} re-slugged
 * every moved post from its title — which is precisely the defect: the move
 * changed a live URL nobody asked it to change. Now that the URL travels with
 * the post, a genuine collision is possible on the one move that crosses a site
 * boundary, and the honest answer is to refuse it. The alternative — quietly
 * uniquifying the URL — is the old behaviour wearing a smaller hat.</p>
 *
 * <p>Mirrors the agent path's {@code MoveTargetOccupiedException} (SH134): same
 * refusal, same reason, on the surface a human drives.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShMoveUrlConflictException extends ShTeachingException {

    public ShMoveUrlConflictException(String furl, String occupantTitle) {
        super(ShProblem.conflict("move-url-conflict",
                        "The destination site already has a post at /" + furl)
                .withDetail(occupantTitle == null || occupantTitle.isBlank()
                        ? "Another post in the destination site holds this friendly URL."
                        : "'" + occupantTitle + "' already holds this friendly URL in the destination site.")
                .withFix("Rename the post (which changes its URL) before moving it, or move it "
                        + "into a folder of the site it already belongs to — a move keeps the "
                        + "post's URL rather than re-slugging it.")
                // SH955 — the field a console form pins this on.
                .withAt("data.furl"));
    }
}
