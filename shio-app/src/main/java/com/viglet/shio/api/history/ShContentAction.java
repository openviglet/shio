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
    RESTORE("Restored");

    private final String verb;

    ShContentAction(String verb) {
        this.verb = verb;
    }

    public String verb() {
        return verb;
    }
}
