/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.history;

/**
 * The content-lifecycle transitions recorded in the audit trail (SH51). The
 * enum {@code name()} is stored verbatim in {@code ShHistory.action}; the verb
 * is only used to phrase the human-readable description.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public enum ShContentAction {

    CREATE("Created"),
    UPDATE("Updated"),
    PUBLISH("Published"),
    UNPUBLISH("Unpublished"),
    DELETE("Deleted"),
    RESTORE("Restored"),

    /**
     * SH224 — a permanent deletion, which is not a {@link #DELETE}.
     *
     * <p>Its own verb because the two are the states a reader most needs to tell apart: a `DELETE`
     * is the reversible trash and a caller walking the feed can still act on it, while this one
     * cannot be undone by anybody. Folding them would make the feed say a post left twice and never
     * say which time was final.</p>
     */
    PURGE("Destroyed");

    private final String verb;

    ShContentAction(String verb) {
        this.verb = verb;
    }

    public String verb() {
        return verb;
    }
}
