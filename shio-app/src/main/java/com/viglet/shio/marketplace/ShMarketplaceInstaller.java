/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.marketplace;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.ShBlueprintCodec;
import com.viglet.shio.blueprint.ShBlueprintProperties;
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;
import com.viglet.shio.marketplace.dto.ShMarketplaceItem;
import com.viglet.shio.utils.ShIoFailure;

/**
 * Installing a catalogue item (SH675) — the fetch and the unpacking, and little else.
 *
 * <h2>Three of the four steps already existed</h2>
 *
 * <p>Download the zip, expand it into {@code shio.blueprint.path}, <b>validate</b> and
 * <b>report</b>. The last two are not new work: {@link ShBlueprintRegistry} already reads that
 * directory, already holds a package to the same format checks as a first-party one, already
 * refuses a name collision naming both origins, already isolates a package that will not load
 * (SH217), and already records where each package came from. So what is here is the transport
 * and the refusals only a download can need.</p>
 *
 * <h2>Install is not apply</h2>
 *
 * <p>This puts a package where the registry can see it. Applying it to a site stays
 * {@code POST /agent/blueprints/{name}/apply}, unchanged. Keeping them apart is what makes an
 * install auditable and re-appliable: the package survives, {@code /agent/blueprints} lists it, a
 * second site can use it, and an operator can read what they installed <em>before</em> anything
 * writes to content — which is P7 read at the moment it actually matters.</p>
 *
 * <h2>A package that will not load is rolled back</h2>
 *
 * <p>SH217 makes a broken local package harmless to the <em>catalogue</em>; it does not make it
 * harmless to the <em>operator</em>, who asked for an install and would otherwise be told it
 * worked while the registry quietly refuses the package on every read. So the reload happens
 * inside the install: if the newly written directory comes back rejected, it is removed and the
 * reason is the refusal. An install either leaves a package the registry loads, or leaves
 * nothing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShMarketplaceInstaller {

    /** Guards a decompression bomb: a package is source text, not a disk image. */
    private static final long MAX_UNPACKED_BYTES = 64L * 1024 * 1024;

    /** And a package is a handful of files, not an archive of them. */
    private static final int MAX_ENTRIES = 2_000;

    private final ShMarketplaceProperties properties;
    private final ShMarketplaceCatalog catalog;
    private final ShBlueprintProperties blueprintProperties;
    private final ShBlueprintRegistry registry;
    private final ShBlueprintCodec codec;

    public ShMarketplaceInstaller(ShMarketplaceProperties properties, ShMarketplaceCatalog catalog,
                                  ShBlueprintProperties blueprintProperties,
                                  ShBlueprintRegistry registry, ShBlueprintCodec codec) {
        this.properties = properties;
        this.catalog = catalog;
        this.blueprintProperties = blueprintProperties;
        this.registry = registry;
        this.codec = codec;
    }

    /**
     * What an install did, or would do.
     *
     * @param id        the package
     * @param version   the version its own manifest declares, which is the one that arrived
     *                  rather than the one the catalogue advertised
     * @param origin    the directory it now lives in
     * @param installed false for a dry run, which validates everything and writes nothing
     * @param loaded    how many packages the registry holds afterwards
     */
    public record Installed(String id, String version, String origin, boolean installed,
                            int loaded) {
    }

    /**
     * Download, validate and install one catalogue item.
     *
     * @param id     the catalogue entry's id
     * @param dryRun when true, everything happens except the move into place
     */
    public Installed install(String id, boolean dryRun) {
        ShMarketplaceItem item = require(id);
        Path root = blueprintDirectory();

        if (item.builtIn() || item.downloadUrl() == null || item.downloadUrl().isBlank()) {
            throw new NothingToDownloadException(item.id(), item.builtIn());
        }
        if (registry.names().contains(item.id())) {
            throw new AlreadyInstalledException(item.id());
        }

        Path staging = null;
        try {
            staging = Files.createTempDirectory("shio-marketplace-");
            Path unpacked = expand(item, staging);
            ShBlueprintManifest manifest = manifestOf(unpacked, item);

            if (dryRun) {
                return new Installed(item.id(), manifest.version(), root.toString(), false,
                        registry.names().size());
            }

            Path destination = root.resolve(item.id());
            Files.createDirectories(root);
            moveInto(unpacked, destination);
            return settle(item, manifest, destination, root);
        } catch (IOException failed) {
            // SH756 — the class and the reason, not just the message. A FileSystemException's
            // message IS the two paths, so this line used to answer "src -> dst" and nothing
            // else, and a move that found the target taken read exactly like one that lost a race.
            throw new InstallFailedException(item.id(), ShIoFailure.describe(failed));
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * SH1251 — download and validate a package exactly as a dry-run install does, then hand the
     * unpacked directory to {@code use} before it is deleted.
     *
     * <p>What lets the console's dry run show the apply plan of a package that is not installed:
     * the plan is read from these bytes, the ones a confirm would install, and nothing is left on
     * disk afterwards. {@code use} receives the dry-run {@link Installed} too, so the caller reports
     * the install half without downloading the package a second time.</p>
     */
    public <T> T staged(String id, BiFunction<Path, Installed, T> use) {
        ShMarketplaceItem item = require(id);
        Path root = blueprintDirectory();
        if (item.builtIn() || item.downloadUrl() == null || item.downloadUrl().isBlank()) {
            throw new NothingToDownloadException(item.id(), item.builtIn());
        }
        if (registry.names().contains(item.id())) {
            throw new AlreadyInstalledException(item.id());
        }
        Path staging = null;
        try {
            staging = Files.createTempDirectory("shio-marketplace-");
            Path unpacked = expand(item, staging);
            ShBlueprintManifest manifest = manifestOf(unpacked, item);
            return use.apply(unpacked, new Installed(item.id(), manifest.version(), root.toString(),
                    false, registry.names().size()));
        } catch (IOException failed) {
            throw new InstallFailedException(item.id(), ShIoFailure.describe(failed));
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * SH1093 — move a freshly unpacked package into place, copying when the move is refused.
     *
     * <p>A move across directories is a rename where the file system allows one, and on Windows a
     * handle another process holds for a moment on the source (an indexer or a scanner looking at
     * files that appeared a millisecond ago) makes the rename fail with {@code AccessDeniedException}
     * instead of waiting. That is what failed {@code aPackageInstalls} once in a suite run with
     * nothing wrong in the code. A copy only has to <em>read</em> the source, which such a handle
     * does not prevent, so a refused move falls back to copy-then-delete. The delete is best effort:
     * the staging directory is removed again, quietly, when the install finishes.</p>
     *
     * <p>A destination that already exists is still a refusal, not a copy: two installs of one id
     * racing is a conflict, and copying over the loser would hide it.</p>
     */
    static void moveInto(Path from, Path to) throws IOException {
        moveInto(from, to, (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE));
    }

    /** The rename step, injectable so a test can refuse it the way a held handle does. */
    @FunctionalInterface
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    static void moveInto(Path from, Path to, Mover mover) throws IOException {
        try {
            mover.move(from, to);
        } catch (java.nio.file.FileAlreadyExistsException taken) {
            throw taken;
        } catch (java.nio.file.AtomicMoveNotSupportedException | java.nio.file.AccessDeniedException refused) {
            if (Files.exists(to)) {
                throw new java.nio.file.FileAlreadyExistsException(to.toString());
            }
            copyTree(from, to);
            deleteQuietly(from);
        }
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (var walk = Files.walk(from)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(source, target);
                }
            }
        }
    }

    /**
     * Remove an installed package.
     *
     * <p>The same door in reverse, and it is here rather than in a follow-up because a catalogue
     * you can only add to is one nobody will experiment with. A package that shipped with the
     * instance is refused: it is on the classpath, so there is nothing on disk to remove and
     * "uninstalled" would be a lie the next restart exposes.</p>
     */
    public Installed uninstall(String id) {
        Path root = blueprintDirectory();
        ShBlueprint present = registry.all().get(id == null ? "" : id.trim().toLowerCase(Locale.ROOT));
        if (present == null) {
            throw new ShBlueprintRegistry.UnknownBlueprintException(id, registry.names());
        }
        if (!present.isLocal()) {
            throw new NotRemovableException(present.name());
        }

        Path directory = root.resolve(present.name());
        try {
            deleteRecursively(directory);
        } catch (IOException failed) {
            throw new InstallFailedException(present.name(), ShIoFailure.describe(failed));
        }
        ShBlueprintRegistry.Reload reload = registry.reload();
        return new Installed(present.name(), present.manifest().version(), root.toString(), false,
                reload.loaded());
    }

    /** Reload, and undo the install if the package the operator asked for will not load. */
    private Installed settle(ShMarketplaceItem item, ShBlueprintManifest manifest,
                             Path destination, Path root) throws IOException {
        ShBlueprintRegistry.Reload reload = registry.reload();
        Optional<ShBlueprint.Rejected> refused = reload.rejected().stream()
                .filter(each -> item.id().equals(each.name())).findFirst();
        if (refused.isPresent()) {
            deleteRecursively(destination);
            registry.reload();
            throw new PackageRefusedException(item.id(), refused.get().reason());
        }
        return new Installed(item.id(), manifest.version(), root.toString(), true, reload.loaded());
    }

    /** The configured directory, or a refusal naming the property that creates one. */
    private Path blueprintDirectory() {
        if (!blueprintProperties.hasPath()) {
            throw new NoBlueprintPathException();
        }
        return Path.of(blueprintProperties.getPath().trim()).normalize();
    }

    private ShMarketplaceItem require(String id) {
        if (!properties.hasRemote()) {
            throw new ShMarketplaceCatalog.DisabledException();
        }
        List<ShMarketplaceItem> items = catalog.catalogue().items();
        return items.stream().filter(each -> each.id().equals(id)).findFirst()
                .orElseThrow(() -> new UnknownItemException(id,
                        items.stream().map(ShMarketplaceItem::id).toList()));
    }

    /**
     * Expand the download into {@code staging} and answer the directory holding the manifest.
     *
     * <p>The zip-slip guard is {@code ShExchangePackageCodec}'s, for its reason: an entry named
     * {@code ../../etc/whatever} resolves outside the target and a copy would write there. The
     * wrapped case is handled the same way too — {@code zip -r blog.zip blog/} produces one
     * sub-directory, and refusing that shape would refuse the way people actually make zips.</p>
     */
    private Path expand(ShMarketplaceItem item, Path staging) throws IOException {
        long written = 0;
        int entries = 0;
        Path root = staging.normalize();
        try (InputStream download = catalog.openDownload(item.downloadUrl());
             ZipInputStream zip = new ZipInputStream(download)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw new TooLargeException(item.id(), MAX_ENTRIES + " entries");
                }
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    throw new UnsafeArchiveException(item.id(), entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                written += Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                if (written > MAX_UNPACKED_BYTES) {
                    throw new TooLargeException(item.id(), MAX_UNPACKED_BYTES + " bytes");
                }
                zip.closeEntry();
            }
        }
        return manifestRoot(root, item);
    }

    /** {@code staging}, or its single sub-directory carrying {@code blueprint.json}. */
    private static Path manifestRoot(Path staging, ShMarketplaceItem item) throws IOException {
        if (Files.exists(staging.resolve(ShBlueprintManifest.FILE_NAME))) {
            return staging;
        }
        try (var children = Files.list(staging)) {
            List<Path> directories = children.filter(Files::isDirectory).toList();
            if (directories.size() == 1
                    && Files.exists(directories.getFirst().resolve(ShBlueprintManifest.FILE_NAME))) {
                return directories.getFirst();
            }
        }
        throw new NotAPackageException(item.id());
    }

    /**
     * The manifest, held to the one claim the catalogue cannot make on the package's behalf.
     *
     * <p>Only the {@code name} is checked here. Everything else the format requires is the
     * registry's to enforce on reload, identically for this package and a first-party one — a
     * second validator would be a second opinion about one format, and the pair would disagree.
     * The name is different because it is the only field the <em>catalogue</em> also asserts, and
     * nothing downstream can notice them disagreeing.</p>
     */
    private ShBlueprintManifest manifestOf(Path unpacked, ShMarketplaceItem item)
            throws IOException {
        ShBlueprintManifest manifest;
        try (InputStream in = Files.newInputStream(unpacked.resolve(
                ShBlueprintManifest.FILE_NAME))) {
            manifest = codec.convert(codec.readTree(in), ShBlueprintManifest.class);
        } catch (RuntimeException unreadable) {
            throw new NotAPackageException(item.id());
        }
        if (manifest == null || !item.id().equals(manifest.name())) {
            throw new NameMismatchException(item.id(),
                    manifest == null ? null : manifest.name());
        }
        return manifest;
    }

    private static void deleteQuietly(Path directory) {
        if (directory == null) {
            return;
        }
        try {
            deleteRecursively(directory);
        } catch (IOException ignored) {
            // ShMarketplaceInstaller-noise: a temp directory that will not delete is the OS's
            // problem and reporting it would replace the real answer — the install — with it.
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    // --- refusals, each an instruction (P6) ---

    /** No directory is configured, so an install has nowhere it may legally write. */
    public static class NoBlueprintPathException extends ShTeachingException {

        public NoBlueprintPathException() {
            super(ShProblem.of("no-blueprint-path", HttpStatus.CONFLICT,
                            "This instance has no directory to install packages into")
                    .withDetail("Packages install into shio.blueprint.path, the directory an "
                            + "operator names. Without one there is nowhere a downloaded package "
                            + "may be written, and writing to the classpath is not that place.")
                    .withFix("Set shio.blueprint.path to a directory this instance may write to.")
                    .withExample("shio.blueprint.path=/var/lib/shio/blueprints"));
        }
    }

    /** The catalogue has no such entry, and the caller is told which ones it has. */
    public static class UnknownItemException extends ShTeachingException {

        public UnknownItemException(String sent, List<String> allowed) {
            super(ShProblem.of("unknown-marketplace-item", HttpStatus.NOT_FOUND,
                            "No catalogue entry with id '" + (sent == null ? "" : sent) + "'")
                    .withDetail("Entries are addressed by the `id` the catalogue declares.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use an id from GET /api/v2/marketplace."));
        }
    }

    /** Already here — either it shipped with the instance, or somebody installed it. */
    public static class NothingToDownloadException extends ShTeachingException {

        public NothingToDownloadException(String id, boolean builtIn) {
            super(ShProblem.of("nothing-to-download", HttpStatus.CONFLICT,
                            "'" + id + "' has nothing to download")
                    .withDetail(builtIn
                            ? "It shipped with this instance, so it is already on the classpath."
                            : "Its catalogue entry declares no downloadUrl.")
                    .withFix(builtIn
                            ? "Apply it directly: POST /api/v2/agent/blueprints/" + id + "/apply."
                            : "Ask whoever publishes the catalogue to add a downloadUrl."));
        }
    }

    /** A name collision, refused rather than resolved — the registry's rule, one step earlier. */
    public static class AlreadyInstalledException extends ShTeachingException {

        public AlreadyInstalledException(String id) {
            super(ShProblem.of("already-installed", HttpStatus.CONFLICT,
                            "A package named '" + id + "' is already here")
                    .withDetail("Names are how a blueprint is applied, so two packages cannot "
                            + "share one: whichever won, an existing automation would silently "
                            + "start applying something else.")
                    .withFix("Uninstall it first: DELETE /api/v2/marketplace/" + id + "."));
        }
    }

    /** The download is not a blueprint package. */
    public static class NotAPackageException extends ShTeachingException {

        public NotAPackageException(String id) {
            super(ShProblem.of("not-a-package", HttpStatus.BAD_REQUEST,
                            "The download for '" + id + "' is not a blueprint package")
                    .withDetail("A package is a zip holding " + ShBlueprintManifest.FILE_NAME
                            + " at its root, or in a single wrapping directory.")
                    .withFix("Publish the package directory zipped, so the manifest is at the "
                            + "root or one level down."));
        }
    }

    /** The package disagrees with the catalogue about its own name. */
    public static class NameMismatchException extends ShTeachingException {

        public NameMismatchException(String id, String declared) {
            super(ShProblem.of("package-name-mismatch", HttpStatus.BAD_REQUEST,
                            "The package calls itself '" + declared + "', the catalogue calls it '"
                                    + id + "'")
                    .withDetail("The id is how it was chosen and the manifest name is how it is "
                            + "applied. If they differ, an operator installs one thing and applies "
                            + "another.")
                    .withFix("Fix the catalogue entry's id, or the package's manifest name, so "
                            + "they agree."));
        }
    }

    /** An entry that would write outside the directory it was given. */
    public static class UnsafeArchiveException extends ShTeachingException {

        public UnsafeArchiveException(String id, String entry) {
            super(ShProblem.of("unsafe-archive", HttpStatus.BAD_REQUEST,
                            "The download for '" + id + "' tries to write outside its directory")
                    .withDetail("Entry: " + entry + ". A package is expanded into one directory "
                            + "and may not reach past it.")
                    .withFix("Repackage without absolute or parent paths in the entry names."));
        }
    }

    /** A package is source text, not a disk image. */
    public static class TooLargeException extends ShTeachingException {

        public TooLargeException(String id, String limit) {
            super(ShProblem.of("package-too-large", HttpStatus.BAD_REQUEST,
                            "The download for '" + id + "' is larger than a package may be")
                    .withDetail("The limit is " + limit + ". A blueprint carries a model, some "
                            + "content and front-end source; anything at this size is either an "
                            + "asset dump or a decompression bomb.")
                    .withFix("Publish assets through the file storage rather than in the package."));
        }
    }

    /** It arrived, and the registry will not have it. */
    public static class PackageRefusedException extends ShTeachingException {

        public PackageRefusedException(String id, String reason) {
            super(ShProblem.of("package-refused", HttpStatus.BAD_REQUEST,
                            "'" + id + "' downloaded, and this instance will not load it")
                    .withDetail(reason + " Nothing was left behind: a package the registry refuses "
                            + "would be invisible on every read while the install said it worked.")
                    .withFix("Ask whoever published it to fix the package, then install again."));
        }
    }

    /** It is on the classpath, so there is nothing on disk to remove. */
    public static class NotRemovableException extends ShTeachingException {

        public NotRemovableException(String id) {
            super(ShProblem.of("not-removable", HttpStatus.CONFLICT,
                            "'" + id + "' shipped with this instance and cannot be uninstalled")
                    .withDetail("It lives on the classpath rather than in the configured "
                            + "directory, so removing it is a build decision rather than a "
                            + "runtime one.")
                    .withFix("Only packages installed from a catalogue can be uninstalled."));
        }
    }

    /** The disk or the network failed, and the caller still gets a sentence. */
    public static class InstallFailedException extends ShTeachingException {

        public InstallFailedException(String id, String reason) {
            super(ShProblem.of("install-failed", HttpStatus.BAD_GATEWAY,
                            "'" + id + "' could not be installed")
                    .withDetail(reason == null ? "The download or the disk failed." : reason)
                    .withFix("Check the catalogue's downloadUrl is reachable and that "
                            + "shio.blueprint.path is writable, then try again."));
        }
    }
}
