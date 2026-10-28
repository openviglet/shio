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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

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
 * @param origin    where this package was loaded from (SH96) — {@link #CLASSPATH} for the
 *                  first-party set, or the operator-configured directory. Not the author's
 *                  to declare and therefore not a manifest key: it is a fact about *this
 *                  instance's* configuration, and a package that could name its own origin
 *                  could claim to be first-party
 * @param assets    SH1252 — the binary files the package writes into the site on apply, with
 *                  their bytes, read once at load like every other part of the package
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShBlueprint(ShBlueprintManifest manifest, List<JsonNode> postTypes,
                          List<JsonNode> content, List<File> files, String origin,
                          @JsonInclude(Include.NON_EMPTY) List<Asset> assets) {

    /** The origin of a package that shipped with the instance. */
    public static final String CLASSPATH = "classpath";

    public ShBlueprint {
        postTypes = postTypes == null ? List.of() : List.copyOf(postTypes);
        content = content == null ? List.of() : List.copyOf(content);
        files = files == null ? List.of() : List.copyOf(files);
        origin = origin == null || origin.isBlank() ? CLASSPATH : origin;
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    /** The pre-SH1252 form: a package that carries no assets. */
    public ShBlueprint(ShBlueprintManifest manifest, List<JsonNode> postTypes,
                       List<JsonNode> content, List<File> files, String origin) {
        this(manifest, postTypes, content, files, origin, List.of());
    }

    /** The pre-SH96 form: everything shipped with the instance. */
    public ShBlueprint(ShBlueprintManifest manifest, List<JsonNode> postTypes,
                       List<JsonNode> content, List<File> files) {
        this(manifest, postTypes, content, files, CLASSPATH);
    }

    /**
     * SH1252 — one binary file the package writes into the site.
     *
     * <p>Not a {@link File}: those are templates the CLI writes into a project, and these are
     * bytes the server writes into the CMS, which is where an image belongs (SH38). The bytes are
     * never serialized; a listing that carried them would put a package's photography in every
     * response that names it.</p>
     *
     * @param folder the site folder path it lands in, e.g. {@code /images}
     * @param name   the file name, e.g. {@code hero.webp}
     * @param sha256 the digest, which is what makes a re-apply of the same bytes a no-op (P5)
     * @param bytes  the content
     */
    public record Asset(String folder, String name, String sha256,
                        @JsonIgnore byte[] bytes) {

        /** Where it is addressed in the site, which is how content references it. */
        public String path() {
            return folder + "/" + name;
        }
    }

    /** True when this package did not ship with the instance — an operator added it. */
    public boolean isLocal() {
        return !CLASSPATH.equals(origin);
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
     *             ship images that belong in the asset subsystem (SH38) instead. An image a
     *             package needs is an {@link Asset} (SH1252), which the server writes there
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
                          int postTypes, int posts, int files, JsonNode params, String origin) {
    }

    /**
     * A package the operator added that would not load, and why (SH217).
     *
     * <p>The community half of the registry needs one property before it needs an index: <b>a
     * package a stranger wrote must not be able to disable the catalogue.</b> So a local package
     * that fails validation is refused on its own and reported here, while a first-party one
     * still fails the registry — the trust boundary SH96 drew, read from the other side.</p>
     *
     * <p>It is also the validation pass an author runs before publishing, with no new contract:
     * point an instance at the directory and read the refusal out of the listing.</p>
     *
     * @param name   the package's DIRECTORY name — the manifest is what failed, so reading a
     *               field out of it would make a report about a broken file fail twice
     * @param origin the configured path it came from, so a reader knows this is not first-party
     * @param reason the teaching error's own title, which already says what to fix (P6)
     */
    public record Rejected(String name, String origin, String reason) {
    }

    /**
     * Summarize for the listing.
     *
     * <p>{@code origin} is reported only for a package the operator added (SH96). Stamping
     * {@code "classpath"} on all six first-party rows would cost tokens on every listing to
     * say "normal" (P3), while its <em>presence</em> is the signal that matters: this package
     * did not ship with the instance, and whoever is about to run it should know that before
     * it writes to their site (P7).</p>
     */
    public Summary summary() {
        return new Summary(manifest.name(), manifest.title(), manifest.description(),
                manifest.version(), postTypes.size(), declaredPosts(), files.size(),
                manifest.params(), isLocal() ? origin : null);
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
