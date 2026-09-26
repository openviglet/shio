/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * SH1112 — the console's eager load, as the instance serving it measures it.
 *
 * @param raw         bytes the browser parses before the first render, summed over {@code assets}
 * @param gzip        the same, gzipped at level 9: what crosses the wire
 * @param ceiling     the eager ceilings the build shipped with ({@code console-size-budget.json}),
 *                    so the number arrives beside the line it is judged against; absent for a build
 *                    that did not ship one
 * @param assets      each eager asset, sorted by path: the entry, its preloads, its stylesheets
 * @param unavailable why nothing was measured, instead of a total of what was left
 */
@JsonInclude(Include.NON_NULL)
public record ShAgentConsoleCost(Long raw, Long gzip, Ceiling ceiling, List<Asset> assets, String unavailable) {

    public record Asset(String asset, long raw, long gzip) {
    }

    public record Ceiling(long raw, long gzip) {
    }

    public static ShAgentConsoleCost unavailable(String reason) {
        return new ShAgentConsoleCost(null, null, null, null, reason);
    }
}
