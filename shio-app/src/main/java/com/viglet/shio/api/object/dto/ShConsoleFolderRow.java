/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.object.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * One folder in a console listing (SH120) — {@code ShCdaFolder} plus the two things a
 * content browser has to show that a delivery API has no reason to carry.
 *
 * <p><b>The first three components are the CDA's, name for name and type for type.</b>
 * That is the whole point of SH120: a caller who has learnt {@code {id, name, root}} from
 * the delivery API does not have to learn {@code {id, name, rootFolder}} again from the
 * console. {@code ShConsoleShapeOverlapTest} fails the build if they drift apart.</p>
 *
 * <p>{@code date} and {@code position} are not a hedge against that rule — they are the
 * console's own axis. The browser sorts by date and renders the manual ordering as a
 * position badge; the CDA delivers published content in query order and has neither. A
 * superset is honest where a second vocabulary for the same three fields would not be.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShConsoleFolderRow(String id, String name, boolean root, Instant date, int position,
                                 Boolean membersOnly) {

    /** The five-field form, before SH1302 marked a members-only folder: true or absent, never false. */
    public ShConsoleFolderRow(String id, String name, boolean root, Instant date, int position) {
        this(id, name, root, date, position, null);
    }
}
