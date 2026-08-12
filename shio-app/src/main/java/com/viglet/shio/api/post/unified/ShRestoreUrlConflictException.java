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
 * An untrash would put a second post on a friendly URL that is already answered (SH243).
 *
 * <h2>Why a refusal and not a numbered URL</h2>
 *
 * <p>A copy that meets an occupied URL takes {@code -2} (SH202) and that is right, because a copy
 * is a <em>new</em> thing and has no claim on any particular address. A restore is the opposite
 * request: it asks for <b>the thing that was there</b>, and quietly returning it at a different
 * address is precisely the surprise P7 exists to prevent — the curator's link, the sitemap entry
 * and the search result all point at the URL they are asking to get back.</p>
 *
 * <p>So the honest answer is to refuse, name the occupant, and let the human choose which of the
 * two posts owns the address. Both ways out are one action they can already take.</p>
 *
 * <h2>What it costs, said plainly</h2>
 *
 * <p>A folder restore (SH200) cascades this over every post the deletion trashed, and one
 * collision therefore refuses the <em>whole</em> subtree: the restore is one transaction, so
 * nothing comes back until the conflict is resolved. That is deliberate rather than a limitation
 * we did not notice. Restoring 49 of 50 posts and reporting the 50th is a partial success the
 * caller has to read carefully to notice, and a curator who asked for their section back and got
 * most of it is the reader P7 is written for.</p>
 *
 * <p>Mirrors {@code ShMoveUrlConflictException} (SH139) — same refusal, same reason, a different
 * operation reaching the same occupied address.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShRestoreUrlConflictException extends ShTeachingException {

    public ShRestoreUrlConflictException(String furl, String occupantTitle) {
        super(ShProblem.conflict("restore-url-conflict",
                        "Another post already answers at /" + furl)
                // The URL is in BOTH branches. The first version left it out of the untitled one —
                // "a live post holds the friendly URL this one would come back to", which names
                // neither the address nor the occupant and is therefore a refusal a reader cannot
                // act on at all (P6). A post with no title is ordinary here: an uploaded file has
                // none (SH38), and it is the case where the address is the only handle there is.
                .withDetail(occupantTitle == null || occupantTitle.isBlank()
                        ? "Another live post in this site took the friendly URL /" + furl
                                + " while this post was in the trash."
                        : "'" + occupantTitle + "' took the friendly URL /" + furl
                                + " while this post was in the trash.")
                .withFix("Rename or delete the post that holds /" + furl + " and restore again, "
                        + "or purge this one if the occupant is the copy you meant to keep — a "
                        + "restore returns the post to its own URL rather than numbering it."));
    }
}
