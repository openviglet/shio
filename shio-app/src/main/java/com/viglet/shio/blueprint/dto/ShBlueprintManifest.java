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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import tools.jackson.databind.JsonNode;

/**
 * A blueprint's {@code blueprint.json} (SH92) — the manifest of an <em>appliable</em>
 * unit of a site.
 *
 * <h2>Why a format at all</h2>
 *
 * <p>Design law P8: skipping steps needs appliable units. A prose recipe an agent has
 * to read and reimplement saves nothing — it moves the work from the human to the model
 * and adds a translation step that can go wrong silently. A blueprint is the same
 * starting point expressed as something the instance itself can apply: a content model,
 * a folder tree, sample content, the parameters that make it somebody's rather than
 * generic, and the promise it makes about what exists afterwards.</p>
 *
 * <h2>What it is made of, and why each part is already a shipped format</h2>
 *
 * <p>Nothing here is a new serialization. {@code postTypes} are SH16
 * {@link com.viglet.shio.api.post.type.dto.ShPostTypeDef}s — the same JSON
 * {@code shio pull} writes, so a blueprint's model can be authored with
 * {@code @viglet/shio-model} and diffed against a live instance with
 * {@code shio push --check}. {@code content} files are SH82 desired-state documents
 * minus their {@code site} key, so applying a blueprint is the plan/apply engine that
 * already exists rather than a second write path (law P10). That is what keeps the
 * format from becoming a third contract.</p>
 *
 * <h2>The two things a blueprint deliberately cannot say</h2>
 *
 * <p><b>Which site it applies to.</b> The site is the caller's, not the package's —
 * the common case is applying a blueprint <em>into</em> an existing instance, so a
 * blueprint that named its own site could only ever be used once. A {@code site} key in
 * a content document is therefore refused at load time rather than ignored, because an
 * ignored key looks like it worked.</p>
 *
 * <p><b>{@code prune}.</b> A blueprint adds; it never decides that content it has never
 * heard of should be deleted. Prune's blast radius depends on what the author of the
 * document forgot to mention (P7), and the author here is a package the user did not
 * write. Removing what a blueprint added is the caller's own {@code shio apply --prune}.</p>
 *
 * @param formatVersion the format revision this package is written against. Present so
 *                      an old instance rejects a newer package with a message rather
 *                      than mis-reading it; {@code 1} is the only version so far
 * @param name          the package's identity — the argument to
 *                      {@code shio apply --blueprint}. Lower-kebab, unique per registry
 * @param title         one-line human name, for a picker
 * @param description   what applying it gives you, in one or two sentences. Read by an
 *                      agent choosing between blueprints, so it says what the result
 *                      <em>is</em> rather than how the package is built
 * @param version       the package's own version, so a change to a blueprint is visible
 *                      in the change set that applied it
 * @param params        a JSON Schema object ({@code type}/{@code properties}/{@code
 *                      required}) describing the substitutions the package accepts.
 *                      Schema rather than an ad-hoc list because it is handed straight
 *                      to the model in the MCP tool result and to {@code shio
 *                      blueprints} on the terminal — one shape, two readers
 * @param postTypes     paths, relative to the package root, of {@code ShPostTypeDef}
 *                      JSON files to provision before the content
 * @param content       paths of desired-state content documents, merged in the order
 *                      given (declaration order is the apply order, so it is fixed — P5)
 * @param files         front-end files the package carries. The server never writes
 *                      these — they belong in the caller's project, not in the CMS — so
 *                      it returns them and the CLI writes them. Reported either way, so
 *                      an MCP-only caller knows what it has <em>not</em> got
 * @param verify        what the package promises exists once it has been applied. A
 *                      blueprint that cannot prove it worked is a liability (SH94), and
 *                      this is the proof, checked against the real instance after apply
 * @param assets        SH1252 — binary files under the package's {@code assets/} directory,
 *                      written into the target site as {@code File} posts on apply:
 *                      {@code assets/images/hero.webp} becomes {@code /images/hero.webp}. Unlike
 *                      {@code files}, these belong in the CMS, so the server writes them; the
 *                      plan lists each one, and identical bytes already there are left alone (P5).
 *                      Omitted from JSON when empty, so a package without any costs nothing
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@JsonInclude(Include.NON_NULL)
public record ShBlueprintManifest(int formatVersion, String name, String title,
                                  String description, String version, JsonNode params,
                                  List<String> postTypes, List<String> content,
                                  List<FileRef> files, Verify verify,
                                  @JsonInclude(Include.NON_EMPTY) List<String> assets) {

    /** The only format revision that exists. */
    public static final int FORMAT_VERSION = 1;

    /** The manifest file's name inside a package directory. */
    public static final String FILE_NAME = "blueprint.json";

    /** Every key the manifest may carry — anything else is a typo, and typos are 400s. */
    public static final List<String> KEYS = List.of("formatVersion", "name", "title",
            "description", "version", "params", "postTypes", "content", "files", "verify",
            "assets");

    /** SH1252 — the directory every asset path lives under, which is also what it loses in the site. */
    public static final String ASSETS_DIRECTORY = "assets/";

    public ShBlueprintManifest {
        formatVersion = formatVersion == 0 ? FORMAT_VERSION : formatVersion;
        postTypes = postTypes == null ? List.of() : List.copyOf(postTypes);
        content = content == null ? List.of() : List.copyOf(content);
        files = files == null ? List.of() : List.copyOf(files);
        assets = assets == null ? List.of() : List.copyOf(assets);
    }

    /** The pre-SH1252 form: a package that carries no assets. */
    public ShBlueprintManifest(int formatVersion, String name, String title, String description,
                               String version, JsonNode params, List<String> postTypes,
                               List<String> content, List<FileRef> files, Verify verify) {
        this(formatVersion, name, title, description, version, params, postTypes, content, files,
                verify, List.of());
    }

    /**
     * One front-end file the package carries.
     *
     * @param path where it goes in the caller's project, relative to the project root
     * @param from where it lives in the package; defaults to {@code path} when omitted
     */
    public record FileRef(String path, String from) {

        /** The package-relative source path — {@code from} when given, else {@code path}. */
        public String source() {
            return from == null || from.isBlank() ? path : from;
        }
    }

    /**
     * The package's post-apply promise.
     *
     * @param posts  friendly URLs that must resolve to a post once the blueprint has been
     *               applied. Checked against the instance, not against the document, so a
     *               package whose content and promise disagree fails on itself
     * @param checks which SH99 check groups to run over the target site afterwards
     *               ({@code content,routes,diagnostics}); omit for the content lint alone
     */
    @JsonInclude(Include.NON_NULL)
    public record Verify(List<String> posts, String checks) {

        public Verify {
            posts = posts == null ? List.of() : List.copyOf(posts);
        }
    }
}
