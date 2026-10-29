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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import org.springframework.beans.factory.ObjectProvider;

import com.viglet.shio.api.agent.diagnostics.ShAgentIncidentLog;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;
import com.viglet.shio.utils.ShIoFailure;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The blueprint registry (SH92) — the packages this instance knows how to apply.
 *
 * <h2>Where blueprints come from (SH96)</h2>
 *
 * <p>Two sources, and the second one is opt-in. The <b>classpath</b>,
 * {@code /blueprints/<name>/blueprint.json}, is how the first-party set (SH94) ships. An
 * operator may add <b>one directory</b>, {@code shio.blueprint.path}, laid out the same way
 * — see {@link ShBlueprintProperties} for why that particular answer and not a URL or a
 * package registry: whoever can set the property can already put a jar on the classpath, so
 * it grants no new authority, and the person who set it is the person vouching for what is
 * in it.</p>
 *
 * <p>Three rules make the second source safe to have. <b>A name collision is refused</b>,
 * never resolved — a local package silently shadowing {@code blog} would change what every
 * existing automation applies, and neither winner is defensible, so the registry fails
 * naming both origins. <b>The same validation runs</b>, so a third-party package is held to
 * the format exactly as a first-party one is. And <b>every package remembers where it came
 * from</b>, which the listing reports for the local ones — an agent about to write to
 * somebody's site should be able to tell that the package it picked did not ship with the
 * CMS (P7).</p>
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

    /**
     * Keys a content document may carry — the SH82 document minus {@code site}, {@code prune}
     * and {@code moves}.
     *
     * <p>{@code moves} (SH150) is excluded for the same reason as the other two, and the
     * omission is deliberate rather than pending: a package is an appliable <em>starting
     * point</em>, so everything it declares is content it brings. A move's subject is content
     * that already exists and belongs to the caller — renaming somebody's live page, with the
     * URL change that implies, is not something a package the user did not write gets to do.
     * The unknown-key check below already refuses it as a 409; do not "complete" this list.</p>
     */
    /**
     * What a content document may declare (SH395).
     *
     * <p>`postTypes` and `postTypeLayout` joined `folders` and `posts` because six packages shipped
     * and <b>not one declared a Theme, a PageLayout or a binding</b> — so applying any of them left
     * the pages rendering through SH169's labelled fallback, which is a safety net and not a design.
     * What an agent then did by hand is the most trap-dense sequence in the product, learned one
     * teaching error at a time: P2 backwards, in the flow this product is named for.</p>
     *
     * <p>Still not `site` (it comes from the request) and still not `prune` (never a package's
     * decision). The two that were added are the two a package can answer for itself: which render
     * types its templates need, and which layout its own post-types bind to.</p>
     *
     * <p>SH1283 added `pageType`, the third thing a package can answer for itself: which of the types
     * it binds is the page a curator adds to an empty folder. It travels as the site settings'
     * `pageType`, after the binding, so the type it names is bound by the time it is checked.</p>
     */
    static final List<String> CONTENT_KEYS =
            List.of("folders", "posts", "postTypes", "postTypeLayout", "pageType");

    private final ShBlueprintCodec codec;
    private final ShBlueprintProperties properties;

    /** Sorted by name so the listing is byte-stable across boots and instances (P5). */
    private volatile Map<String, ShBlueprint> cache;

    /**
     * Local packages that would not load, and why (SH217).
     *
     * <p>Written by the same pass that fills {@link #cache}, so the two are always one snapshot:
     * a reader asking what is available and a reader asking what was refused are looking at the
     * same load.</p>
     */
    private volatile List<ShBlueprint.Rejected> rejected = List.of();

    /**
     * SH217 — an {@link ObjectProvider} so the two test constructors keep working.
     *
     * <p>The registry is built directly in several tests, and a required dependency would make
     * every one of them construct a diagnostics log to read a catalogue. The refusal is reported
     * when there is somewhere to report it and dropped when there is not, which is the right
     * failure for a channel whose whole purpose is that silence costs nothing.</p>
     */
    private final ObjectProvider<ShAgentIncidentLog> incidents;

    @org.springframework.beans.factory.annotation.Autowired
    public ShBlueprintRegistry(ShBlueprintCodec codec, ShBlueprintProperties properties,
                               ObjectProvider<ShAgentIncidentLog> incidents) {
        this.codec = codec;
        this.properties = properties;
        this.incidents = incidents;
    }

    public ShBlueprintRegistry(ShBlueprintCodec codec, ShBlueprintProperties properties) {
        this(codec, properties, null);
    }

    /** The classpath-only registry — the shape {@code ShBlueprintRegistryTest} constructs. */
    public ShBlueprintRegistry(ShBlueprintCodec codec) {
        this(codec, classpathOnly(), null);
    }

    /** No directory at all, not even the SH1257 default: the classpath is the whole answer. */
    private static ShBlueprintProperties classpathOnly() {
        ShBlueprintProperties properties = new ShBlueprintProperties();
        properties.setPath(null);
        return properties;
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
     * Re-reads the configured directory, and answers what changed (SH219).
     *
     * <h2>Explicit, because a watcher would put the moment in the wrong hands</h2>
     *
     * <p>The cache was free while the classpath was the only source: packages cannot change inside
     * a running JVM, so a cache with no invalidation was not a cache with a bug, it was a constant.
     * SH96 changed the premise — an operator can drop a package into {@code shio.blueprint.path}
     * and nothing sees it until a restart, which is the wrong default for a directory whose whole
     * point is iterating on a package.</p>
     *
     * <p><b>Not a watcher.</b> A registry that re-scanned itself would re-run validation at a
     * moment nobody chose, so a half-saved manifest would change what the catalogue says between
     * two applies — an author's editing state becoming every caller's problem. An explicit reload
     * puts the moment in a person's hands, which is the same argument SH96 makes about the
     * directory: the trust model is that somebody decided.</p>
     *
     * <p>SH217 made the failure mode smaller but not absent, and the difference is worth stating:
     * a half-saved <em>local</em> manifest is now refused on its own and the catalogue survives, so
     * the cost of a badly-timed reload is one package missing rather than all of them. What a
     * reload still cannot make safe is a <em>collision</em>, which fails the registry by design —
     * so the answer names what loaded, and a caller who gets fewer packages than they expected can
     * see it in the reply rather than by applying one and finding out.</p>
     *
     * @return what the instance carries now, and what it refused
     */
    public synchronized Reload reload() {
        cache = null;
        rejected = List.of();
        Map<String, ShBlueprint> loaded = all();
        return new Reload(loaded.size(), List.copyOf(rejected));
    }

    /**
     * The answer to a reload: what is there now, and what would not load.
     *
     * @param loaded   how many packages the catalogue holds, first-party included — the number a
     *                 caller compares against what they expected
     * @param rejected the local packages refused, each with its reason (SH217)
     */
    public record Reload(int loaded, List<ShBlueprint.Rejected> rejected) {
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
            // A first-party package that will not load is OUR build defect, and it still fails
            // the whole registry. That is not an inconsistency with the branch below — it is the
            // same rule read from both sides: a failure is isolated exactly when its author is
            // somebody the operator did not choose to trust with the catalogue.
            put(loaded, read(manifest, ShBlueprint.CLASSPATH));
        }
        List<ShBlueprint.Rejected> refused = new ArrayList<>();
        // The classpath first, always: a local package cannot be loaded before the
        // first-party one it collides with, so the error can name which is which.
        for (Resource manifest : localManifests()) {
            ShBlueprint blueprint;
            try {
                blueprint = read(manifest, properties.getPath().trim());
            } catch (ShTeachingException invalid) {
                // SH217 — the community half's load-bearing property. A package the operator
                // added is refused ON ITS OWN and named; before this, one malformed directory
                // made `GET /agent/blueprints` answer 4xx for the WHOLE catalogue, so a stranger's
                // package could take the first-party ones down with it. Measured, not imagined:
                // one first-party manifest missing one parameter did exactly that during SH216,
                // and forty-one tests failed on a registry that could not load.
                //
                // It is also the validation loop SH217 asks for, with no new contract: an author
                // points an instance at their directory and reads the refusal back out of the
                // listing they were going to publish against.
                ShBlueprint.Rejected row = new ShBlueprint.Rejected(packageName(manifest),
                        properties.getPath().trim(), reasonOf(invalid));
                refused.add(row);
                report(row);
                continue;
            }
            // OUTSIDE the catch, and that is the distinction SH96 drew: a package that will not
            // load is one package's problem, while a package that COLLIDES is a conflict about
            // what a name means. Shadowing would change what every existing `blog` apply does
            // without saying so, and ignoring would make the operator's package quietly do
            // nothing — so a collision still fails the registry, naming both origins.
            put(loaded, blueprint);
        }
        rejected = List.copyOf(refused);
        return java.util.Collections.unmodifiableMap(loaded);
    }

    /**
     * The directory name, which is all that is knowable about a package that would not parse.
     *
     * <p>Deliberately not the manifest's {@code name}: the manifest is the thing that failed, so
     * reading a field out of it is how a report about a broken file becomes a second failure.</p>
     */
    private static String packageName(Resource manifest) {
        try {
            Path file = Path.of(manifest.getURI());
            Path parent = file.getParent();
            return parent == null ? manifest.getFilename() : parent.getFileName().toString();
        } catch (IOException | RuntimeException unreadable) {
            return manifest.getDescription();
        }
    }

    /**
     * The teaching error's own words (P6), preferring the <b>detail</b> over the title.
     *
     * <p>The title is the class of problem — "Blueprint package is malformed" — and the detail is
     * the sentence that names which rule broke and where. A reader given only the title has to go
     * and reproduce the failure to learn anything, which is the round trip the whole error format
     * exists to remove.</p>
     */
    private static String reasonOf(ShTeachingException invalid) {
        var problem = invalid.problem().toProblemDetail();
        String detail = problem.getDetail();
        if (detail != null && !detail.isBlank()) {
            return detail;
        }
        String title = problem.getTitle();
        return title == null || title.isBlank() ? invalid.getMessage() : title;
    }

    /**
     * Local packages that would not load, newest load first (SH217).
     *
     * <p>Empty on almost every instance, which is why the listing omits it entirely rather than
     * reporting a zero: silence is the answer that costs nothing (P3), and its presence is the
     * signal.</p>
     */
    public List<ShBlueprint.Rejected> rejected() {
        all();
        return rejected;
    }

    /**
     * Puts one refusal where an operator will meet it (SH217).
     *
     * <p>{@code GET /agent/diagnostics} rather than the catalogue: a refused package is not
     * something this instance carries, so it does not belong in the list of what it carries, and
     * an envelope around that list would charge every caller for the answer "nothing was refused".
     * The incident's {@code fix} is deliberately about the <em>package</em> — for a stranger's
     * package the operator cannot fix it, and telling them to is worse than telling them who
     * can.</p>
     */
    private void report(ShBlueprint.Rejected row) {
        ShAgentIncidentLog log = incidents == null ? null : incidents.getIfAvailable();
        if (log == null) {
            return;
        }
        log.record(ShAgentIncidentLog.Kind.BLUEPRINT, row.name(),
                "this package would not load: " + row.reason(),
                "Fix the package and restart, or take it out of " + row.origin()
                        + ". The rest of the catalogue is unaffected — a package the operator "
                        + "added cannot stop the others loading (SH217).",
                null, null);
    }

    /**
     * Refuses a second package of the same name rather than picking a winner (SH96).
     *
     * <p>Overriding would let a directory silently change what {@code blog} means for every
     * automation already applying it; ignoring would make an operator's package quietly do
     * nothing. Neither is defensible, and both are discovered late — so the registry fails
     * loudly, at load, naming both origins and the one action that resolves it.</p>
     */
    private static void put(Map<String, ShBlueprint> loaded, ShBlueprint blueprint) {
        String key = blueprint.name().toLowerCase(Locale.ROOT);
        ShBlueprint existing = loaded.get(key);
        if (existing != null) {
            throw new DuplicateBlueprintException(blueprint.name(), existing.origin(),
                    blueprint.origin());
        }
        loaded.put(key, blueprint);
    }

    /**
     * The manifests in the operator-configured directory, if there is one (SH96).
     *
     * <p>A configured path that is not a readable directory is a failure, not an empty
     * result: an operator who named a path meant it, and a typo that degraded silently to
     * "the first-party set" is how a site gets built from the wrong packages and nobody
     * finds out until they read the content.</p>
     */
    private List<Resource> localManifests() {
        if (!properties.hasPath()) {
            return List.of();
        }
        Path root = Path.of(properties.getPath().trim());
        if (!Files.exists(root) && properties.isDefaultPath()) {
            // SH1257: the shipped default is created by the first install, not by the operator.
            return List.of();
        }
        if (!Files.isDirectory(root)) {
            throw new UnreadablePathException(properties.getPath(),
                    Files.exists(root) ? "is not a directory" : "does not exist");
        }
        List<Resource> manifests = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            // Sorted, so two instances with the same directory load in the same order and a
            // collision is reported against the same package on both (P5).
            for (Path child : children.filter(Files::isDirectory).sorted().toList()) {
                Path manifest = child.resolve(ShBlueprintManifest.FILE_NAME);
                if (Files.isRegularFile(manifest)) {
                    manifests.add(new FileSystemResource(manifest));
                }
                // A subdirectory without a manifest is skipped rather than refused: an
                // operator's package folder is a working directory, and a stray `.git` or
                // an editor's backup folder is not a malformed package.
            }
        } catch (IOException unreadable) {
            throw new UnreadablePathException(properties.getPath(), ShIoFailure.describe(unreadable));
        }
        return manifests;
    }

    /**
     * SH1251 — a package read from a directory, held to every rule a load holds it to, and
     * <b>not</b> registered.
     *
     * <p>For planning an apply of a package that has been downloaded but not installed: the plan
     * has to come from the same parsed and validated package the install would register, or the
     * dry run would describe a different package from the one confirm installs.</p>
     */
    public ShBlueprint readStaged(Path directory) {
        return read(new FileSystemResource(directory.resolve(ShBlueprintManifest.FILE_NAME)),
                directory.toString());
    }

    /**
     * SH1252 — the most a package's assets may weigh, together.
     *
     * <p>Half the installer's 64 MB unpacked bound, so a package cannot spend the whole archive on
     * images. The showcase, the package this exists for, plans under 8 MB (SH1250's spec); raise
     * this only for a package that measures above it, and record that number here.</p>
     */
    static final long MAX_ASSET_BYTES = 32L * 1024 * 1024;

    /**
     * One declared asset, read and digested. {@code assets/images/hero.webp} lands as
     * {@code hero.webp} in the site folder {@code /images}: the directory is what keeps an asset
     * apart from the package's own files, and a folder is required because a site's root is not a
     * folder an upload can address.
     */
    private ShBlueprint.Asset asset(Resource manifest, String path, String blueprint, String alt) {
        String prefix = ShBlueprintManifest.ASSETS_DIRECTORY;
        String rest = path == null ? "" : path.substring(Math.min(path.length(),
                path.startsWith(prefix) ? prefix.length() : 0));
        int slash = rest.lastIndexOf('/');
        if (path == null || !path.startsWith(prefix) || slash <= 0 || slash == rest.length() - 1) {
            throw new BlueprintFormatException(blueprint, "the asset '" + path + "' must be a file "
                    + "in a folder under " + prefix + ", e.g. " + prefix + "images/hero.webp");
        }
        byte[] bytes;
        try (InputStream in = sibling(manifest, path, blueprint).getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException unreadable) {
            throw new BlueprintFormatException(blueprint, "the asset '" + path + "' could not be read: "
                    + ShIoFailure.describe(unreadable));
        }
        return new ShBlueprint.Asset("/" + rest.substring(0, slash), rest.substring(slash + 1),
                sha256(bytes), bytes, alt);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Reads and validates one package, given its manifest resource. */
    private ShBlueprint read(Resource manifestResource, String origin) {
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

        List<ShBlueprint.Asset> assets = new ArrayList<>();
        Set<String> assetPaths = new LinkedHashSet<>();
        long assetBytes = 0;
        for (ShBlueprintManifest.AssetRef ref : manifest.assets()) {
            String path = ref == null ? null : ref.path();
            ShBlueprint.Asset asset = asset(manifestResource, path, manifest.name(),
                    ref == null ? null : ref.alt());
            if (!assetPaths.add(asset.path())) {
                throw new BlueprintFormatException(manifest.name(),
                        "declares the asset '" + path + "' twice");
            }
            assetBytes += asset.bytes().length;
            if (assetBytes > MAX_ASSET_BYTES) {
                throw new BlueprintFormatException(manifest.name(), "its assets come to more than "
                        + MAX_ASSET_BYTES + " bytes; keep a package's images under that, or "
                        + "deliver the rest through the asset library");
            }
            assets.add(asset);
        }

        // Same validation whichever source it came from (SH96): a package an operator added
        // is held to the format exactly as a first-party one is, so "it loaded" means the
        // same thing about both.
        ShBlueprint blueprint = new ShBlueprint(manifest, postTypes, content, files, origin,
                assets);
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
                                + "`folders`, `posts`, `postTypes`, `postTypeLayout` and `pageType` only: "
                                + "`site` comes from the request, and `prune` is never a package's "
                                + "decision to make");
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

    /**
     * The render fields, which hold a <b>Handlebars</b> template and not blueprint prose (SH395).
     *
     * <h2>Two templating languages, one delimiter</h2>
     *
     * <p>A blueprint parameter is `{{name}}` and so is a Handlebars expression, so a package that
     * carried a `PageLayout` was refused at load: `{{post.title}}`, `{{theme.css}}` and
     * `{{#query}}` all read as parameters nobody declared. <b>That is why not one of the six
     * packages shipped a layout</b> — the format could not express one, and the section that
     * described the gap read it as an authoring omission.</p>
     *
     * <p>So the unknown-placeholder check is lifted <em>inside these fields and nowhere else</em>.
     * Substitution still runs there, which is the point: a layout may say `{{blogTitle}}` in its
     * masthead and get the caller's value, while `{{post.title}}` passes through to the renderer
     * untouched.</p>
     *
     * <h2>What the exemption costs, stated rather than hidden</h2>
     *
     * <p>A <em>typo'd</em> parameter inside a template — `{{blogTitel}}` — is no longer caught
     * here. It cannot be: the check's whole basis is that an undeclared name is a mistake, and in
     * a Handlebars template an undeclared name is the normal case. The trade is bounded by being
     * field-scoped, and the renderer has its own answer for a field that resolves to nothing.</p>
     *
     * <p>Named explicitly rather than inferred from the post's type: a `Region`'s `HTML` and a
     * `Theme`'s `CSS` are the same kind of content wherever they appear, and a rule keyed on type
     * would need to know the render vocabulary here, which is the coupling P10 refuses.</p>
     */
    static final Set<String> TEMPLATE_FIELDS = ShBlueprintParams.TEMPLATE_FIELDS;

    private void walk(JsonNode node, String blueprint, Set<String> declared,
                      List<String> available) {
        if (node == null) {
            return;
        }
        if (node.isString()) {
            check(node.asString(), blueprint, declared, available);
        } else if (node.isArray()) {
            node.forEach(child -> walk(child, blueprint, declared, available));
        } else if (node.isObject()) {
            for (String key : node.propertyNames()) {
                if (TEMPLATE_FIELDS.contains(key)) {
                    continue;
                }
                walk(node.get(key), blueprint, declared, available);
            }
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
            throw new BlueprintFormatException(where, "cannot be read (" + ShIoFailure.describe(unreadable) + ")");
        }
    }

    private String text(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            // CRLF → LF, for the same reason the content projection normalizes it (SH86):
            // a checkout on Windows must not produce a different file from the same package.
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException unreadable) {
            throw new BlueprintFormatException(resource.getDescription(),
                    "cannot be read (" + ShIoFailure.describe(unreadable) + ")");
        }
    }

    /**
     * Whether a package-relative path walks out of the package.
     *
     * <p>Per <em>segment</em>, not per substring. {@code contains("..")} reads as the
     * obvious version of this check and is wrong: it also rejects
     * {@code files/app/[...slug]/page.tsx}, and a Next.js catch-all route is exactly the
     * kind of file the {@code nextjs-starter} package exists to carry (SH119). A dot run
     * inside a segment is a filename; only a segment that <em>is</em> {@code ..} traverses.
     * Both separators are treated as one because a package authored on Windows must not
     * mean something different from the same package on Linux.</p>
     */
    static boolean traverses(String path) {
        for (String segment : path.split("[/\\\\]")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private Resource sibling(Resource manifest, String path, String blueprint) {
        if (path == null || path.isBlank()) {
            throw new BlueprintFormatException(blueprint, "a referenced path is empty");
        }
        if (path.startsWith("/") || traverses(path)) {
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
                    "cannot resolve '" + path + "' (" + ShIoFailure.describe(unresolvable) + ")");
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

    /**
     * A 409: {@code shio.blueprint.path} names something this instance cannot scan (SH96).
     *
     * <p>Not an empty result. An operator who configured a path meant it, and a misspelling
     * that quietly degraded to "the first-party set" is how somebody builds a site out of the
     * wrong packages and only finds out by reading the content.</p>
     */
    public static class UnreadablePathException extends ShTeachingException {

        public UnreadablePathException(String path, String why) {
            super(ShProblem.conflict("blueprint-path-unreadable",
                            "The configured blueprint directory " + why)
                    .withDetail("`shio.blueprint.path` is set to '" + path + "', and a configured "
                            + "path that cannot be scanned is reported rather than treated as "
                            + "empty — degrading silently to the built-in packages would hide "
                            + "which packages an apply actually used.")
                    .withFix("Point `shio.blueprint.path` at a directory holding one subdirectory "
                            + "per package, each with a " + ShBlueprintManifest.FILE_NAME
                            + " — or unset it to load the built-in packages only."));
        }
    }

    /**
     * A 409: two packages claim the same name (SH96).
     *
     * <p>Neither winner is defensible. Letting the local one shadow a first-party package
     * changes what {@code blog} means for every automation already applying it, without
     * anything saying so; letting the first-party one win makes an operator's package do
     * nothing at all. So the registry refuses, at load, naming both origins — the failure is
     * loud, immediate, and fixed by renaming a directory.</p>
     */
    public static class DuplicateBlueprintException extends ShTeachingException {

        public DuplicateBlueprintException(String name, String first, String second) {
            super(ShProblem.of("duplicate-blueprint", HttpStatus.CONFLICT,
                            "Two packages are both named '" + name + "'")
                    .withDetail("One was loaded from " + first + " and another from " + second
                            + ". A blueprint is addressed by name, so the registry will not pick "
                            + "between them: shadowing a package would silently change what an "
                            + "existing apply does, and ignoring one would make it do nothing.")
                    .withFix("Rename the package directory in " + second + " — a third-party "
                            + "package conventionally prefixes its own scope, e.g. 'acme-" + name
                            + "'."));
        }
    }
}
