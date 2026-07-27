/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint.dto;

import tools.jackson.databind.JsonNode;

/**
 * The body of {@code POST /api/v2/agent/blueprints/{name}/apply} (SH93).
 *
 * <p>Three fields, and the shape of the request is the whole argument for why blueprints
 * are worth having: a starting point that would otherwise be a session of discovery,
 * modelling and writing is a package name, a site, and the handful of values that make
 * the result somebody's rather than generic.</p>
 *
 * @param site   the target site's name or id. Required, and never carried by the package:
 *               a blueprint applies <em>into</em> an instance
 * @param params the parameter values, validated against the package's declared schema.
 *               An unknown name is a 400, not a silent no-op
 * @param dryRun report what would happen and write nothing. The plan is complete —
 *               including the post-types the package would create — so a human can
 *               approve it before anything moves (P7)
 *
 * <p>{@code createSite} (SH151) and {@code mergePostTypes} (SH152) are both <b>opt-in</b>, and
 * for the same reason: each one lets an apply change something it otherwise only reports.
 * Creating a site is the most visible side effect in the system, and merging fields into a
 * post-type somebody else owns is the second — so neither may be inferred from "the thing you
 * named is not there". A plan names both before they happen.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShBlueprintRequest(String site, JsonNode params, boolean dryRun,
                                 boolean createSite, boolean mergePostTypes) {

    /** The three-field form: every caller from before SH151/SH152, and every default. */
    public ShBlueprintRequest(String site, JsonNode params, boolean dryRun) {
        this(site, params, dryRun, false, false);
    }
}
