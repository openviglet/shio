/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sun.net.httpserver.HttpServer;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.ShBlueprintProperties;
import com.viglet.shio.blueprint.ShBlueprintRegistry;

import tools.jackson.databind.node.JsonNodeFactory;

/**
 * SH675 — an install leaves a package the registry loads, or it leaves nothing.
 *
 * <h2>Why a real zip over a real socket</h2>
 *
 * <p>Everything interesting here is about bytes arriving from somewhere else: an archive whose
 * entry names reach outside the directory, a package that disagrees with the catalogue about its
 * own name, an archive with no manifest, a package the registry then refuses. A mock of the fetch
 * asserts the branch and not the behaviour, and the two failures that would actually hurt — a zip
 * slip that writes outside {@code shio.blueprint.path}, and a rollback that does not roll back —
 * are exactly the ones a mock cannot see. Building the zips in-memory costs microseconds.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMarketplaceInstallerTest {

    @Autowired
    private ShMarketplaceInstaller installer;

    @Autowired
    private ShMarketplaceProperties properties;

    @Autowired
    private ShBlueprintProperties blueprintProperties;

    @Autowired
    private ShBlueprintRegistry registry;

    @Autowired
    private ShMarketplaceCatalog catalog;

    @Autowired
    private ShMarketplaceSetup setup;

    @TempDir
    private Path packages;

    private HttpServer server;
    private String base;
    private String originalPath;

    @BeforeEach
    void configure() throws IOException {
        originalPath = blueprintProperties.getPath();
        blueprintProperties.setPath(packages.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.start();
        properties.setEnabled(true);
        properties.setTimeoutMillis(5_000);
    }

    @AfterEach
    void restore() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        blueprintProperties.setPath(originalPath);
        properties.setEnabled(false);
        properties.setUrl(ShMarketplaceProperties.DEFAULT_URL);
        try (var walk = Files.walk(packages)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (!path.equals(packages)) {
                    Files.deleteIfExists(path);
                }
            }
        }
        registry.reload();
    }

    @Test
    @DisplayName("SH675: a package installs, and the registry can then apply it by name")
    void aPackageInstalls() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));

        ShMarketplaceInstaller.Installed installed =
                installer.install("shio-blueprint-newsroom", false);

        assertThat(installed.installed()).isTrue();
        assertThat(installed.version())
                .as("SH675: the version reported is the one that ARRIVED — the manifest's — not "
                        + "the one the catalogue advertised, because those can disagree and only "
                        + "one of them is now on disk")
                .isEqualTo("2.0.0");
        assertThat(Files.exists(packages.resolve("shio-blueprint-newsroom")
                .resolve("blueprint.json"))).isTrue();
        assertThat(registry.names()).contains("shio-blueprint-newsroom");
        assertThat(registry.require("shio-blueprint-newsroom").isLocal())
                .as("and it is marked as not first-party, which is what a curator needs to know "
                        + "before it writes to their site (P7)")
                .isTrue();
    }

    @Test
    @DisplayName("SH677: an installed package is not reported as one that shipped with the instance")
    void anInstalledPackageIsNotReportedAsBuiltIn() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));
        installer.install("shio-blueprint-newsroom", false);

        var entry = catalog.catalogue().items().stream()
                .filter(item -> "shio-blueprint-newsroom".equals(item.id())).findFirst()
                .orElseThrow();

        // The provenance rule this protects is the registry's own (a package may not declare where
        // it came from, because one that could would claim to be first-party). Cited rather than
        // asserted: what is asserted below is the catalogue's report, not that rule's boundary.
        assertThat(entry.installed()).isTrue();
        assertThat(entry.builtIn())
                .as("SH677: the catalogue derived `builtIn` from 'the registry has it', which stops "
                        + "being the same thing at the first install — a package downloaded from a "
                        + "stranger was then reported as having shipped with the instance, which is "
                        + "the server making that claim on the package's behalf. Found by "
                        + "installing one and reading the listing back, so the assertion is the "
                        + "read that found it.")
                .isFalse();
        assertThat(catalog.catalogue().items())
                .filteredOn(com.viglet.shio.marketplace.dto.ShMarketplaceItem::builtIn)
                .allSatisfy(item -> assertThat(registry.require(item.id()).isLocal())
                        .as("%s is reported as built-in and the registry calls it local", item.id())
                        .isFalse());
    }

    @Test
    @DisplayName("SH675: a dry run validates everything and writes nothing")
    void aDryRunWritesNothing() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));

        ShMarketplaceInstaller.Installed planned =
                installer.install("shio-blueprint-newsroom", true);

        assertThat(planned.installed()).isFalse();
        assertThat(planned.version()).isEqualTo("2.0.0");
        assertThat(Files.exists(packages.resolve("shio-blueprint-newsroom")))
                .as("SH675: P7 — a dry run that leaves the package behind is not a dry run")
                .isFalse();
        assertThat(registry.names()).doesNotContain("shio-blueprint-newsroom");
    }

    @Test
    @DisplayName("SH675: an entry that escapes the directory is refused before anything is written")
    void aZipSlipIsRefused() throws IOException {
        // The classic: an entry name that resolves above the directory it was handed. Without the
        // guard this writes into the parent of shio.blueprint.path, which on a real instance is
        // wherever the operator happened to point.
        publish("shio-blueprint-evil", zipOf(null,
                Map.entry("../escaped.txt", "owned"),
                Map.entry("blueprint.json", manifestBody("shio-blueprint-evil", "1.0.0"))));

        assertThatThrownBy(() -> installer.install("shio-blueprint-evil", false))
                .isInstanceOf(ShMarketplaceInstaller.UnsafeArchiveException.class);

        assertThat(Files.exists(packages.getParent().resolve("escaped.txt")))
                .as("SH675: this is the assertion the guard exists for — the file must not be "
                        + "anywhere, and 'the install failed' is not the same claim")
                .isFalse();
        assertThat(Files.exists(packages.resolve("escaped.txt"))).isFalse();
    }

    @Test
    @DisplayName("SH675: a package that disagrees with the catalogue about its name is refused")
    void aNameMismatchIsRefused() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("something-else", "1.0.0")));

        assertThatThrownBy(() -> installer.install("shio-blueprint-newsroom", false))
                .as("SH675: the id is how it was chosen, the manifest name is how it is applied. "
                        + "If they differ, an operator installs one thing and applies another.")
                .isInstanceOf(ShMarketplaceInstaller.NameMismatchException.class);
        assertThat(registry.names()).doesNotContain("shio-blueprint-newsroom", "something-else");
    }

    @Test
    @DisplayName("SH675: an archive with no manifest is not a package")
    void anArchiveWithoutAManifestIsRefused() throws IOException {
        publish("shio-blueprint-newsroom",
                zipOf(null, Map.entry("readme.txt", "no manifest here")));

        assertThatThrownBy(() -> installer.install("shio-blueprint-newsroom", false))
                .isInstanceOf(ShMarketplaceInstaller.NotAPackageException.class);
    }

    @Test
    @DisplayName("SH675: a package the registry will not load is rolled back, not left behind")
    void aRefusedPackageIsRolledBack() throws IOException {
        // Valid zip, valid manifest name, and a manifest the FORMAT refuses: it points at a
        // post-type file the package does not carry. SH217 makes this harmless to the catalogue;
        // it does not make it harmless to the operator, who was told the install worked.
        String broken = """
                {
                  "formatVersion": 1,
                  "name": "shio-blueprint-broken",
                  "title": "Broken",
                  "version": "1.0.0",
                  "postTypes": ["post-types/Missing.json"]
                }
                """;
        publish("shio-blueprint-broken", zipOf("shio-blueprint-broken/",
                Map.entry("blueprint.json", broken)));

        assertThatThrownBy(() -> installer.install("shio-blueprint-broken", false))
                .isInstanceOf(ShMarketplaceInstaller.PackageRefusedException.class);

        assertThat(Files.exists(packages.resolve("shio-blueprint-broken")))
                .as("SH675: an install leaves a package the registry loads, or it leaves nothing. "
                        + "A directory the registry refuses on every read, while the install "
                        + "reported success, is the state this rollback exists to prevent.")
                .isFalse();
        assertThat(registry.names()).doesNotContain("shio-blueprint-broken");
    }

    @Test
    @DisplayName("SH675: installing over an existing name is refused rather than resolved")
    void aCollisionIsRefused() throws IOException {
        String firstParty = registry.names().getFirst();
        publish(firstParty, zipOf(firstParty + "/", pkg(firstParty, "9.9.9")));

        assertThatThrownBy(() -> installer.install(firstParty, false))
                .as("SH675: a catalogue entry that shadows an installed name would change what an "
                        + "existing automation applies, which is ShBlueprintRegistry's collision "
                        + "rule one step earlier")
                .isInstanceOfAny(ShMarketplaceInstaller.AlreadyInstalledException.class,
                        ShMarketplaceInstaller.NothingToDownloadException.class);
    }

    @Test
    @DisplayName("SH675: uninstall removes an installed package and leaves a first-party one alone")
    void uninstallRemovesOnlyWhatWasInstalled() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));
        installer.install("shio-blueprint-newsroom", false);
        assertThat(registry.names()).contains("shio-blueprint-newsroom");

        installer.uninstall("shio-blueprint-newsroom");

        assertThat(registry.names()).doesNotContain("shio-blueprint-newsroom");
        assertThat(Files.exists(packages.resolve("shio-blueprint-newsroom"))).isFalse();

        assertThatThrownBy(() -> installer.uninstall(registry.names().getFirst()))
                .as("SH675: a first-party package is on the classpath, so 'uninstalled' would be a "
                        + "claim the next restart contradicts")
                .isInstanceOf(ShMarketplaceInstaller.NotRemovableException.class);
    }

    @Test
    @DisplayName("SH675: with no shio.blueprint.path, the refusal names the property that makes one")
    void noConfiguredDirectoryIsTaught() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));
        blueprintProperties.setPath(null);

        assertThatThrownBy(() -> installer.install("shio-blueprint-newsroom", false))
                .isInstanceOf(ShMarketplaceInstaller.NoBlueprintPathException.class)
                .satisfies(thrown -> assertThat(((ShMarketplaceInstaller.NoBlueprintPathException)
                        thrown).problem().toProblemDetail().getProperties().get("fix").toString())
                        .as("P6: a refusal an operator cannot act on is a stack trace with better "
                                + "manners")
                        .contains("shio.blueprint.path"));
    }

    @Test
    @DisplayName("SH675: a built-in package has nothing to download, and the refusal says what to do")
    void aBuiltInPackageHasNothingToDownload() throws IOException {
        publish("nothing", zipOf(null, Map.entry("readme.txt", "unused")));
        String firstParty = registry.names().getFirst();

        assertThatThrownBy(() -> installer.install(firstParty, false))
                .isInstanceOfAny(ShMarketplaceInstaller.NothingToDownloadException.class,
                        ShMarketplaceInstaller.AlreadyInstalledException.class);
    }

    @Test
    @DisplayName("SH1251: a dry run plans the apply of a package not yet installed, and leaves nothing")
    void aSetupDryRunPlansTheApplyOfAPackageNotYetInstalled() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));

        ShMarketplaceSetup.Result planned = setup.run("shio-blueprint-newsroom",
                new ShMarketplaceSetup.Request("SH1251 Newsroom", JsonNodeFactory.instance.objectNode(),
                        true, false, true), "admin");

        assertThat(planned.install()).isNotNull();
        assertThat(planned.install().installed()).isFalse();
        assertThat(planned.apply().dryRun()).isTrue();
        assertThat(planned.apply().blueprint()).isEqualTo("shio-blueprint-newsroom");
        assertThat(planned.apply().content().changed())
                .as("SH1251: the plan is the staged package's, so it already names the folder the "
                        + "package creates; a plan that waited for the install would make the curator "
                        + "write a package to see what it does")
                .isPositive();
        assertThat(Files.exists(packages.resolve("shio-blueprint-newsroom")))
                .as("SH1251: P7 — the dry run downloaded the package to plan it and kept nothing")
                .isFalse();
        assertThat(registry.names()).doesNotContain("shio-blueprint-newsroom");
    }

    @Test
    @DisplayName("SH1251: an apply that refuses after the install keeps the installed package")
    void aSetupWhoseApplyRefusesKeepsThePackage() throws IOException {
        publish("shio-blueprint-newsroom", zipOf("shio-blueprint-newsroom/",
                pkg("shio-blueprint-newsroom", "2.0.0")));

        assertThatThrownBy(() -> setup.run("shio-blueprint-newsroom",
                new ShMarketplaceSetup.Request("SH1251 No Such Site", JsonNodeFactory.instance.objectNode(),
                        false, false, false), "admin"))
                .as("an unknown site without createSite is the apply's refusal, as it always was")
                .isInstanceOf(ShTeachingException.class);

        assertThat(registry.names())
                .as("SH1251: the package is valid and installed, and the refusal was about the apply; "
                        + "rolling the install back would make the next attempt download it again")
                .contains("shio-blueprint-newsroom");
    }

    // --- fixtures ---

    /** Serve one zip, and a catalogue with a single entry pointing at it. */
    private void publish(String id, byte[] zip) throws IOException {
        String catalogue = """
                [
                  {
                    "id": "%s",
                    "title": "%s",
                    "version": "1.0.0",
                    "author": "Somebody Else",
                    "category": "community",
                    "downloadUrl": "%s/%s.zip"
                  }
                ]
                """.formatted(id, id, base, id);

        server.createContext("/marketplace.json", exchange -> respond(exchange,
                catalogue.getBytes(StandardCharsets.UTF_8)));
        server.createContext("/" + id + ".zip", exchange -> respond(exchange, zip));
        properties.setUrl(base + "/marketplace.json");
    }

    /**
     * A manifest and the content document it declares — the smallest package the format accepts.
     *
     * <p>Both entries, because the registry refuses *"a blueprint with neither `postTypes` nor
     * `content`"*: it would apply nothing. Writing the fixture without it is how this test first
     * ran, and the rollback below caught it — which is a fair demonstration that the rollback
     * works, and a bad way to assert a successful install.</p>
     */
    private static Map.Entry<String, String>[] pkg(String name, String version) {
        return entries(Map.entry("blueprint.json", manifestBody(name, version)),
                Map.entry("content/site.json", CONTENT));
    }

    /** A desired-state document that creates one folder and nothing else. */
    private static final String CONTENT = """
            {
              "folders": [
                { "path": "/newsroom" }
              ]
            }
            """;

    private static String manifestBody(String name, String version) {
        return """
                {
                  "formatVersion": 1,
                  "name": "%s",
                  "title": "A package",
                  "description": "For the installer's tests.",
                  "version": "%s",
                  "content": ["content/site.json"]
                }
                """.formatted(name, version);
    }

    @SafeVarargs
    private static Map.Entry<String, String>[] entries(Map.Entry<String, String>... all) {
        return all;
    }

    /**
     * A zip of the given entries.
     *
     * @param wrapper a directory to nest every entry under, or null for a flat archive — both
     *                shapes exist in the wild, because {@code zip -r x.zip x/} produces the first
     */
    @SafeVarargs
    private static byte[] zipOf(String wrapper, Map.Entry<String, String>... entries)
            throws IOException {
        Map<String, String> ordered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries) {
            ordered.put(wrapper == null ? entry.getKey() : wrapper + entry.getKey(),
                    entry.getValue());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> entry : ordered.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, byte[] body)
            throws IOException {
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Test
    @DisplayName("SH1093: a move a held handle refuses is completed as a copy, not failed")
    void aRefusedMoveIsCopied(@TempDir Path scratch) throws IOException {
        // The one red run: AccessDeniedException renaming a freshly unpacked package, because a
        // Windows indexer held a handle on files that had appeared a millisecond earlier. The mover
        // is refused here the way that handle refuses it, which a real handle cannot do on demand.
        Path source = Files.createDirectories(scratch.resolve("staging/pkg/post-types"));
        Files.writeString(source.resolve("Page.json"), "{\"name\":\"Page\"}");
        Files.writeString(scratch.resolve("staging/pkg/blueprint.json"), "{\"name\":\"pkg\"}");
        Path destination = scratch.resolve("installed/pkg");
        Files.createDirectories(destination.getParent());

        ShMarketplaceInstaller.moveInto(scratch.resolve("staging/pkg"), destination, (from, to) -> {
            throw new java.nio.file.AccessDeniedException(from.toString());
        });

        assertThat(destination.resolve("blueprint.json"))
                .as("SH1093: a refused rename failed the install outright, a flake a copy completes")
                .hasContent("{\"name\":\"pkg\"}");
        assertThat(destination.resolve("post-types/Page.json")).hasContent("{\"name\":\"Page\"}");
        assertThat(scratch.resolve("staging/pkg")).as("and the source is removed after the copy")
                .doesNotExist();
    }

    @Test
    @DisplayName("SH1093: a taken destination is still a refusal, never a copy over it")
    void aTakenDestinationIsNotCopiedOver(@TempDir Path scratch) throws IOException {
        Path source = Files.createDirectories(scratch.resolve("staging/pkg"));
        Files.writeString(source.resolve("blueprint.json"), "{\"name\":\"new\"}");
        Path destination = Files.createDirectories(scratch.resolve("installed/pkg"));
        Files.writeString(destination.resolve("blueprint.json"), "{\"name\":\"old\"}");

        assertThatThrownBy(() -> ShMarketplaceInstaller.moveInto(source, destination, (from, to) -> {
            throw new java.nio.file.AccessDeniedException(from.toString());
        })).as("SH1093: two installs of one id racing is a conflict a copy would hide")
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(destination.resolve("blueprint.json")).hasContent("{\"name\":\"old\"}");
    }
}
