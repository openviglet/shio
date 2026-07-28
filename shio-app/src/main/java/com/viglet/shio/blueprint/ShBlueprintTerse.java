/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.util.List;
import java.util.Map;

import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.ShTerse;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;

import tools.jackson.databind.JsonNode;

/**
 * Terse renderings for the blueprint surface (SH78's encoding over SH92/SH93).
 *
 * <p>The catalogue is what an agent reads to <em>choose</em>, so it carries the
 * description and the parameter names and nothing else: a JSON Schema per package would
 * be several hundred tokens spent on packages the agent is not going to apply. The
 * schema for the one it picks is a second, cheap call.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShBlueprintTerse {

    private static final String FILES = "files";
    private static final String PLACEHOLDERS = "placeholders";

    private ShBlueprintTerse() {
        // utility
    }

    /** The catalogue: one {@code blueprint} line per package. */
    public static String catalogue(List<ShBlueprint.Summary> summaries) {
        StringBuilder out = new StringBuilder(ShTerse.line("blueprints")
                .word(summaries.size())
                .build());
        for (ShBlueprint.Summary summary : summaries) {
            out.append(ShTerse.line("blueprint")
                    .word(summary.name())
                    .attr("version", summary.version())
                    .count("types", summary.postTypes())
                    .count("posts", summary.posts())
                    .count(FILES, summary.files())
                    .attr("params", params(summary.params()))
                    .attr("about", summary.description())
                    .build());
        }
        return out.toString();
    }

    /** One package in detail, parameter by parameter — what the caller has to fill in. */
    public static String detail(ShBlueprint blueprint) {
        ShBlueprint.Summary summary = blueprint.summary();
        StringBuilder out = new StringBuilder(ShTerse.line("blueprint")
                .word(summary.name())
                .attr("version", summary.version())
                .attr("title", summary.title())
                .count("types", summary.postTypes())
                .count("folders", blueprint.declaredFolders())
                .count("posts", summary.posts())
                .count(FILES, summary.files())
                .attr("about", summary.description())
                .build());
        List<String> required = ShBlueprintParams.required(summary.params());
        for (Map.Entry<String, JsonNode> entry
                : ShBlueprintParams.properties(summary.params()).entrySet()) {
            JsonNode spec = entry.getValue();
            out.append(ShTerse.line("param")
                    .word(entry.getKey())
                    .attr("type", text(spec, "type"))
                    .attr("default", text(spec, "default"))
                    .attr("enum", list(spec))
                    .flag("required", required.contains(entry.getKey()))
                    .attr("about", text(spec, "description"))
                    .build());
        }
        for (String name : blueprint.postTypeNames()) {
            out.append(ShTerse.line("type").word(name).build());
        }
        for (ShBlueprint.File file : blueprint.files()) {
            out.append(ShTerse.line("file")
                    .word(file.path())
                    .attr(PLACEHOLDERS, placeholders(file))
                    .build());
        }
        return out.toString();
    }

    /**
     * The carried files with their bodies, for a caller that has no CLI to fetch them
     * (SH166) — {@code shio://blueprint/<name>/files}.
     *
     * <p>Served verbatim, placeholders standing, because that is what they are: SH119 leaves
     * carried-file substitution to the client on the ground that a project's npm name is
     * local identity the server has no business being told, and pretending otherwise here
     * would mean either inventing values or accepting parameters on a read. So each body is
     * preceded by the line that says which names are still in it, and the {@code param} rows
     * from {@code shio://blueprint/<name>} say what to put there.</p>
     */
    public static String fileBodies(ShBlueprint blueprint) {
        StringBuilder out = new StringBuilder(ShTerse.line(FILES)
                .word(blueprint.name())
                .count(FILES, blueprint.files().size())
                .build());
        for (ShBlueprint.File file : blueprint.files()) {
            out.append(ShTerse.line("file")
                    .word(file.path())
                    .attr(PLACEHOLDERS, placeholders(file))
                    .build());
            // The body is not a terse line — it is source, and a line-oriented encoding that
            // tried to escape it would make it unusable for the one thing it is for.
            out.append(file.text());
            if (!file.text().endsWith("\n")) {
                out.append('\n');
            }
            out.append("--\n");
        }
        return out.toString();
    }

    /** The distinct {@code {{name}}}s standing in a carried file, or {@code null} if it is literal. */
    private static String placeholders(ShBlueprint.File file) {
        List<String> names = ShBlueprintParams.placeholders(file.text()).stream()
                .distinct().sorted().toList();
        return names.isEmpty() ? null : String.join(",", names);
    }

    /**
     * An apply (or a plan): the header, the model rows, the SH82 change set unchanged,
     * then the promise. The change set is rendered by {@link ShAgentReadTerse} rather
     * than re-formatted here — an agent that has read one plan can read this one.
     */
    public static String applied(ShBlueprintApplied applied) {
        StringBuilder out = new StringBuilder(ShTerse.line("blueprint")
                .word(applied.dryRun() ? "plan" : "applied")
                .word(applied.blueprint())
                .attr("version", applied.version())
                .attr("site", applied.site())
                .build());
        if (applied.params() != null) {
            applied.params().forEach((name, value) ->
                    out.append(ShTerse.line("param").word(name).word(value).build()));
        }
        for (ShBlueprintApplied.PostType postType : applied.postTypes()) {
            out.append(ShTerse.line("type")
                    .word(postType.action())
                    .word(postType.name())
                    // Named, not hidden: an existing type that lacks a field the package's
                    // content writes is a half-success, and the caller has to know now.
                    .attr("missing", postType.missing() == null
                            ? null : String.join(",", postType.missing()))
                    .build());
        }
        out.append(ShAgentReadTerse.changes(applied.content()));
        files(out, applied);
        ShBlueprintApplied.Verified verified = applied.verify();
        if (verified != null) {
            out.append(ShTerse.line("verify")
                    .word(verified.ok() ? "ok" : "failed")
                    .count("errors", verified.errors())
                    .count("warnings", verified.warnings())
                    .attr("checks", String.join(",", verified.checks()))
                    .build());
            for (String address : verified.missing()) {
                out.append(ShTerse.line("missing").word(address).build());
            }
        }
        return out.toString();
    }

    /**
     * The carried files: one line each, saying they were not written and what is still
     * standing in them (SH166).
     *
     * <p>Both words earn their place. <b>not-written</b> stops an agent reporting a finished
     * front end that does not exist; <b>placeholders</b> stops the agent that goes and fetches
     * the bytes from committing {@code {{projectName}}} into somebody's project. Every name
     * listed has its resolved value in the {@code param} lines above, so the caller has
     * everything it needs to substitute for itself — which is the whole answer for a client
     * with no CLI to do it for them.</p>
     */
    private static void files(StringBuilder out, ShBlueprintApplied applied) {
        if (applied.files() == null) {
            return;
        }
        for (ShBlueprintApplied.CarriedFile file : applied.files()) {
            out.append(ShTerse.line("file")
                    .word(file.path())
                    .word("not-written")
                    .attr(PLACEHOLDERS, file.placeholders() == null
                            ? null : String.join(",", file.placeholders()))
                    .build());
        }
    }

    private static String params(JsonNode schema) {
        List<String> required = ShBlueprintParams.required(schema);
        List<String> names = ShBlueprintParams.properties(schema).keySet().stream()
                .map(name -> required.contains(name) ? name + "!" : name)
                .toList();
        return names.isEmpty() ? null : String.join(",", names);
    }

    private static String text(JsonNode spec, String key) {
        JsonNode value = spec == null ? null : spec.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static String list(JsonNode spec) {
        JsonNode choices = spec == null ? null : spec.get("enum");
        if (choices == null || !choices.isArray()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        choices.forEach(choice -> out.append(out.isEmpty() ? "" : "|").append(choice.asString()));
        return out.toString();
    }
}
