/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent;

import java.util.List;
import java.util.stream.Collectors;

import com.viglet.shio.api.agent.dto.ShAgentContext;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;

/**
 * Renders a context pack in the terse line format (SH78).
 *
 * <p>Two jobs, and they must agree: it produces the {@code format=terse} response,
 * and it produces the canonical string the pack's {@code digest} is computed over.
 * That is why {@link #body(ShAgentContext)} deliberately excludes the {@code digest}
 * and {@code budget} lines — hashing the measurements would make the digest depend
 * on the caller's budget parameter, so two identical instances would look different
 * and the "has anything changed?" check would be worthless.</p>
 *
 * <p>{@link #node(ShAgentContext.Node)} is also the unit the budget charges during
 * the walk, so what is measured is exactly what is emitted.</p>
 *
 * <p>Field notation is a compressed form of the same information the JSON carries:
 * {@code name:Widget} with {@code !} for required, {@code []} for multi-valued, and
 * {@code {…}} for a Relator's nested fields. Widget names are kept <em>verbatim</em>
 * (spaces and all) rather than compacted: they are the exact strings a write must
 * send, and inventing a short form here would be handing the agent a name the API
 * would then reject.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAgentContextTerse {

    private static final String FORMAT_HEADER = "shio-context";

    private ShAgentContextTerse() {
        // utility
    }

    /** The full response: the canonical body plus the digest and budget lines. */
    public static String render(ShAgentContext pack) {
        StringBuilder out = new StringBuilder(body(pack));
        out.append(ShTerse.line("digest").word(pack.digest()).build());
        ShAgentContext.Budget budget = pack.budget();
        if (budget != null) {
            out.append(ShTerse.line("budget")
                    .attr("est", budget.estTokens())
                    .attr("limit", budget.limitTokens() == 0 ? "none" : budget.limitTokens())
                    .flag("truncated", budget.truncated())
                    .attr("from", budget.truncatedAfter())
                    .flag("over-budget", budget.overBudget())
                    .build());
        }
        return out.toString();
    }

    /**
     * The canonical, measurement-free rendering — the digest input.
     *
     * <p>Section order is fixed (header, instance, sites, model, sitemap,
     * conventions) because the digest depends on it.</p>
     */
    public static String body(ShAgentContext pack) {
        StringBuilder out = new StringBuilder();
        out.append(ShTerse.line(FORMAT_HEADER)
                .word(pack.protocol())
                .attr("manifest", pack.manifest())
                .build());
        if (pack.instance() != null) {
            out.append(ShTerse.line("instance")
                    .word(pack.instance().product())
                    .word(pack.instance().version())
                    .attr("locale", pack.instance().defaultLocale())
                    .build());
        }
        for (ShAgentContext.Site site : nullSafe(pack.sites())) {
            out.append(ShTerse.line("site")
                    .word(site.name())
                    .attr("id", site.id())
                    .attr("root", site.rootFolderId())
                    .attr("url", site.url())
                    .count("posts", site.posts())
                    .build());
        }
        for (ShAgentContext.Type type : nullSafe(pack.model())) {
            out.append(type(type));
        }
        for (ShAgentContext.Node node : nullSafe(pack.sitemap())) {
            out.append(node(node));
        }
        for (String convention : nullSafe(pack.conventions())) {
            out.append(ShTerse.line("convention").word(convention).build());
        }
        return out.toString();
    }

    static String type(ShAgentContext.Type type) {
        return ShTerse.line("type")
                .word(type.name())
                .flag("system", type.system())
                .flag("managed", type.managed())
                .count("posts", type.posts())
                .attr("fields", fields(type.fields()))
                .build();
    }

    /** One sitemap row — also the budget's unit of measure during the walk. */
    static String node(ShAgentContext.Node node) {
        return ShTerse.line("node")
                .word(node.kind())
                .word(node.path())
                .attr("id", node.id())
                .attr("type", node.type())
                .attr("state", node.state())
                .attr("url", node.url())
                .attr("title", node.title())
                .count("folders", node.folders())
                .count("posts", node.posts())
                .build();
    }

    private static String fields(List<ShCdaSchemaField> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        return fields.stream().map(ShAgentContextTerse::field).collect(Collectors.joining(","));
    }

    private static String field(ShCdaSchemaField field) {
        StringBuilder out = new StringBuilder(nullSafe(field.name()));
        out.append(':').append(nullSafe(field.widget()));
        if (field.multiple()) {
            out.append("[]");
        }
        if (field.required()) {
            out.append('!');
        }
        String nested = fields(field.fields());
        if (nested != null) {
            out.append('{').append(nested).append('}');
        }
        return out.toString();
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
