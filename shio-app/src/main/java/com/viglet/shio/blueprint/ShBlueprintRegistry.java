/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The blueprint registry (SH92) — the packages this instance knows how to apply.
 *
 * <h2>Where blueprints come from, and where they will come from</h2>
 *
 * <p>Today: the classpath, {@code /blueprints/<name>/blueprint.json}, which is how the
 * first-party set (SH94) ships. That is deliberately the whole story for now. A
 * blueprint is code the instance executes against its own content, so "where may a
 * package come from" is a trust question, and SH96 is the task that answers it (naming
 * convention, manifest validation, a curated index). Inventing an answer here — a
 * scanned directory, a URL to fetch — would be shipping the distribution channel without
 * the trust model.</p>
 *
 * <h2>Loaded eagerly, validated once</h2>
 *
 * <p>Every package is read and checked at first access and then cached, and the checks
 * are the point: a manifest with an unknown key, a referenced file that is not in the
 * package, a content document that names its own site, a promise about a URL the package
 * never creates. All of these are the <em>author's</em> mistakes, and all of them would
 * otherwise surface as somebody else's half-applied site. A format whose violations are
 * discovered at apply time is a convention with a schema attached.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShBlueprintRegistry {

    /** Where packages live on the classpath. */
    static final String LOCATION = "classpath*:/blueprints/*/" + ShBlueprintManifest.FILE_NAME;

    /** Keys a content document may carry — the SH82 document minus {@code site}/{@code prune}. */
    private static final List<String> CONTENT_KEYS = List.of("folders", "posts");

    private final ShBlueprintCodec codec;

    /** Sorted by name so the listing is byte-stable across boots and instances (P5). */
    private volatile Map<String, ShBlueprint> cache;

    public ShBlueprintRegistry(ShBlueprintCodec codec) {
        this.codec = codec;
    }

    /** Every blueprint this instance carries, by name, in name order. */
    public Map<String, ShBlueprint> all() {
        Map<String, ShBlueprint> loaded = cache;
        if (loaded == null) {
            synchronized (this) {
                loaded = cache;
                if (loaded == null) {
                    loaded = load();
                    cache = loaded;
                }
            }
        }
        return loaded;
    }

    /** The summaries, for {@code GET /agent/blueprints}. */
    public List<ShBlueprint.Summary> summaries() {
        return all().values().stream().map(ShBlueprint::summary).toList();
    }

    /** The names, for a teaching error's {@code allowed} set. */
    public List<String> names() {
        return List.copyOf(all().keySet());
    }

    /**
     * One blueprint by name.
     *
     * @throws UnknownBlueprintException naming every blueprint that does exist, with the
     *         nearest match — the whole reason a caller never has to list first
     */
    public ShBlueprint require(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return Optional.ofNullable(all().get(key))
                .orElseThrow(() -> new UnknownBlueprintException(name, names()));
    }

    private Map<String, ShBlueprint> load() {
        Map<String, ShBlueprint> loaded = new TreeMap<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                getClass().getClassLoader());
        Resource[] manifests;
        try {
            manifests = resolver.getResources(LOCATION);
        } catch (IOException unreadable) {
            // No classpath match is not an error (an instance may carry no packages); a
            // classpath that cannot be *read* is, and it must not look like emptiness.
            throw new UnreadableRegistryException(unreadable);
        }
        for (Resource manifest : manifests) {
            ShBlueprint blueprint = read(manifest);
            loaded.put(blueprint.name().toLowerCase(Locale.ROOT), blueprint);
        }
        return java.util.Collections.unmodifiableMap(loaded);
    }

    /** Reads and validates one package, given its manifest resource. */
    private ShBlueprint read(Resource manifestResource) {
        String base = baseUri(manifestResource);
        JsonNode raw = parse(manifestResource, base + ShBlueprintManifest.FILE_NAME);
        validateManifestKeys(raw, base);
        ShBlueprintManifest manifest = convert(raw, base);
        validateManifest(manifest, base);

        List<JsonNode> postTypes = new ArrayList<>();
        for (String path : manifest.postTypes()) {
            JsonNode def = parse(sibling(manifestResource, path, manifest.name()), base + path);
            // Parsed to the DTO purely so a broken model file fails here rather than on
            // somebody's apply; the tree is what gets substituted and pushed.
            toDef(def, manifest.name(), path);
            postTypes.add(def);
        }
        List<JsonNode> content = new ArrayList<>();
        for (String path : manifest.content()) {
            JsonNode document = parse(sibling(manifestResource, path, manifest.name()), base + path);
            validateContent(document, manifest.name(), path);
            content.add(document);
        }
        List<ShBlueprint.File> files = new ArrayList<>();
        for (ShBlueprintManifest.FileRef file : manifest.files()) {
            files.add(new ShBlueprint.File(file.path(),
                    text(sibling(manifestResource, file.source(), manifest.name()))));
        }

        ShBlueprint blueprint = new ShBlueprint(manifest, postTypes, content, files);
        validatePlaceholders(blueprint);
        validatePromise(blueprint);
        return blueprint;
    }

    // --- validation ---

    private void validateManifestKeys(JsonNode raw, String base) {
        if (!raw.isObject()) {
            throw new BlueprintFormatException(base, "the manifest is not a JSON object");
        }
        for (String key : raw.propertyNames()) {
            if (!ShBlueprintManifest.KEYS.contains(key)) {
                throw new BlueprintFormatException(base, "unknown manifest key '" + key
                        + "' (allowed: " + String.join(", ", ShBlueprintManifest.KEYS) + ")");
            }
        }
    }

    private void validateManifest(ShBlueprintManifest manifest, String base) {
        if (manifest.name() == null || manifest.name().isBlank()) {
            throw new BlueprintFormatException(base, "`name` is required — it is how a caller "
                    + "asks for this package");
        }
        if (manifest.formatVersion() != ShBlueprintManifest.FORMAT_VERSION) {
            throw new UnsupportedFormatException(manifest.name(), manifest.formatVersion());
        }
        if (manifest.content().isEmpty() && manifest.postTypes().isEmpty()) {
            throw new BlueprintFormatException(base, "a blueprint with neither `postTypes` nor "
                    + "`content` would apply nothing");
        }
        if (ShBlueprintParams.properties(manifest.params()).containsKey(ShBlueprintParams.SITE)) {
            throw new BlueprintFormatException(base, "`site` is reserved — the target site comes "
                    + "from the request, so a package cannot declare its own");
        }
        for (String required : ShBlueprintParams.required(manifest.params())) {
            if (!ShBlueprintParams.properties(manifest.params()).containsKey(required)) {
                throw new BlueprintFormatException(base, "`params.required` names '" + required
                        + "', which `params.properties` does not declare");
            }
        }
    }

    private void validateContent(JsonNode document, String blueprint, String path) {
        if (!document.isObject()) {
            throw new BlueprintFormatException(blueprint + "/" + path,
                    "a content document must be a JSON object");
        }
        for (String key : document.propertyNames()) {
            if (!CONTENT_KEYS.contains(key)) {
                throw new BlueprintFormatException(blueprint + "/" + path,
                        "unknown key '" + key + "'. A blueprint's content document declares "
                                + "`folders` and `posts` only: `site` comes from the request, and "
                                + "`prune` is never a package's decision to make");
            }
        }
    }

    /**
     * Every {@code {{placeholder}}} in the package resolves to a declared parameter.
     *
     * <p>Checked at load rather than at substitution time so that the failure belongs to
     * whoever wrote the package. Substitution would raise the same error later, but by
     * then it is a stranger's apply that broke.</p>
     */
    private void validatePlaceholders(ShBlueprint blueprint) {
        Set<String> declared = new LinkedHashSet<>(
                ShBlueprintParams.properties(blueprint.manifest().params()).keySet());
        declared.add(ShBlueprintParams.SITE);
        List<String> available = declared.stream().sorted().toList();
        for (JsonNode tree : blueprint.postTypes()) {
            walk(tree, blueprint.name(), declared, available);
        }
        for (JsonNode tree : blueprint.content()) {
            walk(tree, blueprint.name(), declared, available);
        }
        for (ShBlueprint.File file : blueprint.files()) {
            check(file.text(), blueprint.name(), declared, available);
        }
    }

    private void walk(JsonNode node, String blueprint, Set<String> declared,
                      List<String> available) {
        if (node == null) {
            return;
        }
        if (node.isString()) {
            check(node.asString(), blueprint, declared, available);
        } else if (node.isArray() || node.isObject()) {
            node.forEach(child -> walk(child, blueprint, declared, available));
        }
    }

    private void check(String text, String blueprint, Set<String> declared,
                       List<String> available) {
        for (String name : ShBlueprintParams.placeholders(text)) {
            if (!declared.contains(name)) {
                throw new ShBlueprintParams.UnknownPlaceholderException(blueprint, name, available);
            }
        }
    }

    /**
     * Every URL the package promises in {@code verify.posts} is one its own content
     * declares.
     *
     * <p>This is the check that makes SH94's "a blueprint that cannot prove it worked is
     * a liability" true rather than aspirational. The post-apply verification checks the
     * promise against the <em>instance</em>; this checks it against the package, so a
     * promise nobody could ever keep is caught before it ships.</p>
     */
    private void validatePromise(ShBlueprint blueprint) {
        List<String> promised = blueprint.manifest().verify() == null
                ? List.of() : blueprint.manifest().verify().posts();
        if (promised.isEmpty()) {
            return;
        }
        Set<String> declared = new LinkedHashSet<>();
        for (JsonNode document : blueprint.content()) {
            JsonNode posts = document.get("posts");
            if (posts == null || !posts.isArray()) {
                continue;
            }
            posts.forEach(post -> {
                JsonNode url = post.get("url");
                if (url != null && url.isString()) {
                    declared.add(normalize(url.asString()));
                }
            });
        }
        for (String url : promised) {
            if (!declared.contains(normalize(url))) {
                throw new BlueprintFormatException(blueprint.name(),
                        "`verify.posts` promises '" + url + "', which none of the content "
                                + "documents declares. A promise the package cannot keep would "
                                + "report a failure on every apply");
            }
        }
    }

    private static String normalize(String url) {
        String trimmed = url == null ? "" : url.trim();
        String withSlash = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        return withSlash.length() > 1 && withSlash.endsWith("/")
                ? withSlash.substring(0, withSlash.length() - 1).toLowerCase(Locale.ROOT)
                : withSlash.toLowerCase(Locale.ROOT);
    }

    // --- resource plumbing ---

    private ShPostTypeDef toDef(JsonNode node, String blueprint, String path) {
        try {
            return codec.convert(node, ShPostTypeDef.class);
        } catch (JacksonException malformed) {
            throw new BlueprintFormatException(blueprint + "/" + path,
                    "not a post-type definition (" + malformed.getOriginalMessage() + ")");
        }
    }

    private JsonNode parse(Resource resource, String where) {
        try (InputStream in = resource.getInputStream()) {
            return codec.readTree(in);
        } catch (JacksonException malformed) {
            throw new BlueprintFormatException(where, "invalid JSON ("
                    + malformed.getOriginalMessage() + ")");
        } catch (IOException unreadable) {
            throw new BlueprintFormatException(where, "cannot be read (" + unreadable.getMessage() + ")");
        }
    }

    private String text(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            // CRLF → LF, for the same reason the content projection normalizes it (SH86):
            // a checkout on Windows must not produce a different file from the same package.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException unreadable) {
            throw new BlueprintFormatException(resource.getDescription(),
                    "cannot be read (" + unreadable.getMessage() + ")");
        }
    }

    private Resource sibling(Resource manifest, String path, String blueprint) {
        if (path == null || path.isBlank()) {
            throw new BlueprintFormatException(blueprint, "a referenced path is empty");
        }
        if (path.startsWith("/") || path.contains("..")) {
            // A package addresses its own contents and nothing else. Absolute or
            // parent-relative paths are how a package would read the rest of the classpath.
            throw new BlueprintFormatException(blueprint, "'" + path + "' escapes the package "
                    + "— referenced paths are relative to the blueprint directory");
        }
        try {
            Resource resource = manifest.createRelative(path);
            if (!resource.exists()) {
                throw new BlueprintFormatException(blueprint,
                        "references '" + path + "', which the package does not contain");
            }
            return resource;
        } catch (IOException unresolvable) {
            throw new BlueprintFormatException(blueprint,
                    "cannot resolve '" + path + "' (" + unresolvable.getMessage() + ")");
        }
    }

    private ShBlueprintManifest convert(JsonNode raw, String base) {
        try {
            return codec.convert(raw, ShBlueprintManifest.class);
        } catch (JacksonException malformed) {
            throw new BlueprintFormatException(base, malformed.getOriginalMessage());
        }
    }

    /** {@code .../blueprints/blog/} — used only in messages, so a failure says where. */
    private String baseUri(Resource manifest) {
        String description = manifest.getDescription();
        int cut = description.lastIndexOf(ShBlueprintManifest.FILE_NAME);
        return cut < 0 ? description : description.substring(0, cut);
    }

    // --- teaching errors (SH80) ---

    /** A 404 that names the blueprints this instance has, with the nearest match. */
    public static class UnknownBlueprintException extends ShTeachingException {

        public UnknownBlueprintException(String sent, List<String> allowed) {
            super(ShProblem.of("unknown-blueprint", org.springframework.http.HttpStatus.NOT_FOUND,
                            "No blueprint named '" + (sent == null ? "" : sent) + "'")
                    .withDetail("Blueprints are addressed by the `name` in their manifest.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix(allowed.isEmpty()
                            ? "This instance carries no blueprints."
                            : "Use one of the listed names; GET /api/v2/agent/blueprints "
                                    + "describes each one and its parameters.")
                    .withExample(allowed.isEmpty() ? "blog" : allowed.get(0)));
        }
    }

    /**
     * A 500-shaped 409: the package is malformed.
     *
     * <p>Deliberately not a 400. Nothing the caller sent caused it, so a 400 would send
     * an agent into a retry loop over its own arguments; the fix belongs to whoever
     * packaged the blueprint, and the message says which file and what about it.</p>
     */
    public static class BlueprintFormatException extends ShTeachingException {

        public BlueprintFormatException(String where, String why) {
            super(ShProblem.conflict("malformed-blueprint", "Blueprint package is malformed")
                    .withDetail(where + ": " + why)
                    .withFix("Fix the package — this is not something the request can work "
                            + "around. GET /api/v2/agent/blueprints lists the packages that load.")
                    .withExample("{\"formatVersion\":1,\"name\":\"blog\",\"content\":[\"content/blog.json\"]}"));
        }
    }

    /** A 409: a package written against a format revision this instance does not know. */
    public static class UnsupportedFormatException extends ShTeachingException {

        public UnsupportedFormatException(String name, int sent) {
            super(ShProblem.conflict("unsupported-blueprint-format",
                            "Blueprint '" + name + "' is format version " + sent)
                    .withDetail("This instance understands version "
                            + ShBlueprintManifest.FORMAT_VERSION + ". Reading a newer package "
                            + "with older rules is how a package half-applies.")
                    .withFix("Upgrade Shio, or use a package written for format version "
                            + ShBlueprintManifest.FORMAT_VERSION + ".")
                    .withExample(ShBlueprintManifest.FORMAT_VERSION));
        }
    }

    /** A 409: the classpath itself could not be scanned. */
    public static class UnreadableRegistryException extends ShTeachingException {

        public UnreadableRegistryException(IOException cause) {
            super(ShProblem.conflict("blueprint-registry-unreadable",
                            "The blueprint registry could not be read")
                    .withDetail(String.valueOf(cause.getMessage()))
                    .withFix("Check the deployment — an unreadable registry is reported rather "
                            + "than treated as an empty one."));
        }
    }
}
