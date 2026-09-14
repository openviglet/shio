/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

/**
 * An untrash would put a second post with the same title in one folder (SH691).
 *
 * <h2>Why the counter was not taught about the trash instead</h2>
 *
 * <p>SH243 taught {@code ShFurlNaming} about the trash, so a copy no longer takes a friendly URL a
 * deleted post is sleeping on. {@code freeNaming}'s other counter — the one that numbers the
 * <em>title</em> — was deliberately left alone, and SH691 kept that decision rather than
 * reversing it. Numbering a title against content no listing shows produces "Home (3)" in a folder
 * holding one visible "Home (2)": a rename the curator cannot account for, and exactly what
 * SH243's {@code liveOccupant} split refuses to do.</p>
 *
 * <p>So the collision is allowed to happen and refused where it becomes real — at the restore,
 * which is the same shape SH243 gave the URL. The cost is a refusal on a name nobody addresses by;
 * the alternative was an unexplainable number on every copy.</p>
 *
 * <h2>Why a title is worth refusing at all</h2>
 *
 * <p>Because it is the one the curator reads. SH202's design says the identical title is <b>the
 * symptom they actually see</b> — a folder listing two rows spelled the same, with no way to tell
 * which is the page they were looking for. The URL is the address and the title is the handle, and
 * a restore that hands back an indistinguishable handle is the surprise P7 exists to prevent.</p>
 *
 * <p>Mirrors {@link ShRestoreUrlConflictException}: same refusal, same reason, the other of the two
 * names a copy moves.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShRestoreTitleConflictException extends ShTeachingException {

    public ShRestoreTitleConflictException(String title, String folderName) {
        super(ShProblem.conflict("restore-title-conflict",
                        "Another post in this folder is already called '" + title + "'")
                // The folder is named because the collision is per-folder: the same title in a
                // sibling folder is not a conflict and telling a curator to go and rename it would
                // send them somewhere nothing is wrong (P6).
                .withDetail(folderName == null || folderName.isBlank()
                        ? "A live post took the title '" + title + "' while this post was in the "
                                + "trash, and a folder cannot show two rows a curator can only tell "
                                + "apart by opening them."
                        : "A live post in '" + folderName + "' took the title '" + title
                                + "' while this post was in the trash, and a folder cannot show two "
                                + "rows a curator can only tell apart by opening them.")
                .withFix("Rename either post and restore again, or purge this one if the live post "
                        + "is the copy you meant to keep — a restore returns the post under its own "
                        + "title rather than numbering it.")
                // SH955 — the field a console form pins this on.
                .withAt("data.title"));
    }
}
