/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

import java.util.Locale;

/**
 * What kind of thing an audit row is about (SH196).
 *
 * <h2>Why a subject and not more actions</h2>
 *
 * <p>Folder writes are recorded with the same {@link ShContentAction} vocabulary a post
 * write uses — a rename is an {@code UPDATE}, not a {@code FOLDER_RENAME}. Adding an action
 * per subject would double the enum every time a new kind of object becomes auditable and
 * would break every consumer that switches on {@code action}; one dimension per column is
 * the same argument that kept {@code agent_session_id} out of {@code owner} (§XII).</p>
 *
 * <h2>Why it is stored rather than inferred</h2>
 *
 * <p>It <em>was</em> inferred: SH200 asked the folder repository whether the audited id is a
 * folder, once per row. That costs a query per row on the review queue, and it stops working
 * the moment the answer matters most — after a purge the folder row is gone, so its own
 * {@code DELETE} entry is silently reclassified as a post and the queue reports "the post no
 * longer exists". A row that describes an object has to keep describing it after the object
 * is gone.</p>
 *
 * <p>Rows written before the column existed are {@code null}, and a reader treats {@code null}
 * as {@link #POST} — the only subject that was recorded then.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShContentSubject {

    /** A unified post — the subject of every audit row written before SH196. */
    POST,

    /** A folder, i.e. a section of a site: addressed {@code folder:<site>/<name-chain>} (SH79). */
    FOLDER;

    /** The lower-case form used on the wire, which is also its SH79 address scheme. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The stored value, tolerating {@code null} and anything unrecognised.
     *
     * @return the subject, or {@link #POST} for a row written before the column existed
     */
    public static ShContentSubject of(String stored) {
        if (stored == null || stored.isBlank()) {
            return POST;
        }
        // Compared rather than valueOf'd-and-caught: this runs once per audit row on the review
        // queue and the change feed, and a swallowed IllegalArgumentException would be one more
        // silent catch in the SH270 inventory for a case that has an honest default.
        for (ShContentSubject subject : values()) {
            if (subject.name().equals(stored)) {
                return subject;
            }
        }
        return POST;
    }
}
