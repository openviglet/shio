/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.turing;

/**
 * SH1063 — the routing table as lines, for the readers that read lines.
 *
 * <p>The same judgement every other terse renderer here makes (SH596): this is read when something
 * is wrong, and a reader wants the table, not a JSON envelope around it. A site that routes nowhere
 * is spelled {@code -> (nowhere)} rather than left blank, because a blank column reads as a
 * rendering problem and this is the one row the reader is looking for.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTuringIndexTerse {

    private ShTuringIndexTerse() {
    }

    public static String status(ShTuringIndexStatus status) {
        StringBuilder out = new StringBuilder();
        out.append(status.enabled() ? "indexing on" : "indexing off");
        if (status.target() != null && !status.target().isBlank()) {
            out.append(" -> ").append(status.target());
        }
        if (status.defaultSite() != null) {
            out.append(" (default ").append(status.defaultSite()).append(')');
        }
        out.append('\n');
        for (ShTuringIndexStatus.Route route : status.sites()) {
            out.append("  ").append(route.site()).append(" -> ")
                    .append(route.searchSite() == null ? "(nowhere)" : route.searchSite())
                    .append("  ").append(route.published()).append(" published");
            if (status.enabled() && route.searchSite() == null && route.published() > 0) {
                // The line that is the whole point: published content with no route out.
                out.append("  UNROUTABLE");
            }
            out.append('\n');
        }
        if (status.failures() > 0) {
            out.append(status.failures()).append(" indexing failure(s) in the recent window; ")
                    .append("read shio://diagnostics for the causes\n");
        }
        return out.toString();
    }
}
