/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * A blueprint as loaded from its package (SH92) — the manifest plus everything it
 * referenced, already read and parsed.
 *
 * <p>Loaded once and cached, and <b>validated at load</b>: a package that names a
 * post-type file that is not there, or promises a URL its own content documents never
 * declare, fails when the registry reads it rather than half-way through somebody's
 * apply. That is the difference between a format and a convention.</p>
 *
 * <p>The two payload lists stay as raw JSON rather than being mapped to their DTOs
 * here on purpose: parameter substitution (SH92's {@code {{param}}}) happens over the
 * <em>tree</em>, so a value the format does not know about — a nested object inside a
 * post's {@code data}, a verbatim {@code widgetSettings} string — is substituted and
 * carried through unchanged instead of being silently dropped by a mapping step. The
 * post-type defs are additionally parsed once at load, purely so that a broken model
 * file is a load-time failure too.</p>
 *
 * @param manifest  the package's {@code blueprint.json}
 * @param postTypes the {@code ShPostTypeDef} JSON trees, in declared order
 * @param content   the desired-state content documents, in declared order
 * @param files     the front-end files the package carries, with their bodies
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShBlueprint(ShBlueprintManifest manifest, List<JsonNode> postTypes,
                          List<JsonNode> content, List<File> files) {

    public ShBlueprint {
        postTypes = postTypes == null ? List.of() : List.copyOf(postTypes);
        content = content == null ? List.of() : List.copyOf(content);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public String name() {
        return manifest.name();
    }

    /**
     * One carried front-end file.
     *
     * @param path project-relative destination
     * @param text the body. Text-only, deliberately: a blueprint's front-end files are
     *             source, and a format that could carry a binary would invite packages to
     *             ship images that belong in the asset subsystem (SH38) instead
     */
    public record File(String path, String text) {
    }

    /**
     * The row {@code GET /agent/blueprints} returns — everything needed to choose a
     * blueprint and nothing else (P3). The counts stand in for the bodies: an agent
     * picking a starting point wants to know a package brings four post-types and eleven
     * pages, not what is in them.
     */
    public record Summary(String name, String title, String description, String version,
                          int postTypes, int posts, int files, JsonNode params) {
    }

    /** Summarize for the listing. */
    public Summary summary() {
        return new Summary(manifest.name(), manifest.title(), manifest.description(),
                manifest.version(), postTypes.size(), declaredPosts(), files.size(),
                manifest.params());
    }

    /** How many posts the content documents declare, across all of them. */
    public int declaredPosts() {
        int total = 0;
        for (JsonNode document : content) {
            JsonNode posts = document.get("posts");
            if (posts != null && posts.isArray()) {
                total += posts.size();
            }
        }
        return total;
    }

    /** How many folders the content documents declare, across all of them. */
    public int declaredFolders() {
        int total = 0;
        for (JsonNode document : content) {
            JsonNode folders = document.get("folders");
            if (folders != null && folders.isArray()) {
                total += folders.size();
            }
        }
        return total;
    }

    /** The post-type names this package provisions, in declared order. */
    public List<String> postTypeNames() {
        return postTypes.stream().map(node -> {
            JsonNode name = node.get("name");
            return name == null ? "" : name.asString();
        }).filter(name -> !name.isBlank()).toList();
    }
}
