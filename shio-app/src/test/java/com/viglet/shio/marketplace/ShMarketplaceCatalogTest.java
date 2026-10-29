/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.SpringBootTest;

import com.sun.net.httpserver.HttpServer;
import com.viglet.shio.blueprint.ShBlueprintCodec;
import com.viglet.shio.blueprint.ShBlueprintProperties;
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.marketplace.dto.ShMarketplaceItem;

/**
 * SH646 — the catalogue lists what this instance could install, and says where the list came from.
 *
 * <h2>What is asserted at the boundary, and why a real socket</h2>
 *
 * <p>The three answers that matter are about a <em>network</em>: a catalogue that is off, one that
 * is read, and one that is configured and cannot be reached. Mocking the fetch would assert the
 * branch and not the behaviour — in particular it would not catch the failure this is most likely
 * to have, which is an unreachable catalogue emptying the listing instead of being reported beside
 * it. A loopback {@link HttpServer} costs milliseconds and asserts the real path.</p>
 *
 * <h2>The rule being defended</h2>
 *
 * <p>SH217 established that a package the operator did not write must not be able to disable the
 * catalogue. One level up, a URL that is slow, moved or serving nonsense is the same hazard, and
 * the six first-party packages must survive all of it. Every test here is a way of asking that one
 * question.</p>
 *
 * @author Alexandre Oliveira
 */
@SpringBootTest
class ShMarketplaceCatalogTest {

    @Autowired
    private ShMarketplaceCatalog catalog;

    @Autowired
    private ShMarketplaceProperties properties;

    @Autowired
    private ShBlueprintRegistry registry;

    private HttpServer server;

    @BeforeEach
    void reset() {
        properties.setEnabled(false);
        properties.setUrl(ShMarketplaceProperties.DEFAULT_URL);
        properties.setTimeoutMillis(5_000);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        reset();
    }

    @Test
    @DisplayName("SH678: the MCP tool is read-only, because installing there would be an escalation")
    void theMcpToolCannotInstall() {
        var schema = new com.viglet.shio.api.agent.mcp.tools.ShMarketplaceTool(catalog)
                .inputSchema();
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) schema.get("properties");

        assertThat(properties)
                .as("SH678: MCP tools call services in process, and an API token reaches /mcp while "
                        + "being barred from /api/v2/marketplace — so an install verb here would "
                        + "hand that token what the URL layer denies it. 'The agent path is not a "
                        + "privilege escalation' is a non-goal by name, and this is the assertion "
                        + "that would catch somebody adding the verb later because it looked like "
                        + "a gap.")
                .containsOnlyKeys("id");
    }

    @Test
    @DisplayName("SH678: the manifest flag follows the property, so `false` is not a guess")
    void theManifestFlagFollowsTheProperty() {
        assertThat(properties.hasRemote())
                .as("off in this context (the test properties turn it off) — an agent that assumed "
                        + "a marketplace would be choosing from a list nobody enabled")
                .isFalse();

        properties.setEnabled(true);
        properties.setUrl("https://example.invalid/marketplace.json");
        assertThat(properties.hasRemote()).isTrue();

        properties.setEnabled(false);
        assertThat(properties.hasRemote())
                .as("SH678: `enabled` alone decides, so turning it off cannot leave the manifest "
                        + "claiming a catalogue this instance no longer reads")
                .isFalse();
    }

    @Test
    @DisplayName("SH1257: an instance that sets nothing reads the published catalogue and can install")
    void aFreshInstanceReadsThePublishedCatalogue() {
        Binder nothing = new Binder(new MapConfigurationPropertySource(Map.of()));
        ShMarketplaceProperties fresh = nothing
                .bindOrCreate("shio.marketplace", ShMarketplaceProperties.class);
        ShBlueprintProperties store = nothing
                .bindOrCreate("shio.blueprint", ShBlueprintProperties.class);

        assertThat(fresh.hasRemote())
                .as("SH1257: the showcase sells Shio only if a first boot reaches it, so a fresh "
                        + "instance reads the catalogue with no property set")
                .isTrue();
        assertThat(fresh.getUrl()).isEqualTo(ShMarketplaceProperties.DEFAULT_URL);
        assertThat(store.hasPath())
                .as("SH1257: and an install has somewhere to write, or the installer still refuses "
                        + "on a fresh boot")
                .isTrue();
        assertThat(store.getPath()).isEqualTo(ShBlueprintProperties.DEFAULT_PATH);

        ShMarketplaceProperties off = new Binder(new MapConfigurationPropertySource(
                Map.of("shio.marketplace.enabled", "false")))
                .bindOrCreate("shio.marketplace", ShMarketplaceProperties.class);
        assertThat(off.hasRemote())
                .as("SH1257: one property is how an air-gapped operator keeps nothing leaving the JVM")
                .isFalse();
    }

    @Test
    @DisplayName("SH1257: the default directory may be absent, a named one may not")
    void theDefaultDirectoryMayBeAbsent(@TempDir Path scratch) {
        ShBlueprintProperties named = new ShBlueprintProperties();
        named.setPath(scratch.resolve("typo").toString());
        assertThatThrownBy(() -> new ShBlueprintRegistry(new ShBlueprintCodec(), named).all())
                .as("an operator who named a path meant it")
                .isInstanceOf(ShBlueprintRegistry.UnreadablePathException.class);

        ShBlueprintProperties shipped = new ShBlueprintProperties();
        assertThat(shipped.isDefaultPath()).isTrue();
        assumeFalse(Files.exists(Path.of(ShBlueprintProperties.DEFAULT_PATH)),
                "this working directory already holds an install, so it is not a fresh boot");
        assertThat(new ShBlueprintRegistry(new ShBlueprintCodec(), shipped).all().keySet())
                .as("SH1257: a fresh boot has installed nothing, so the default reads as empty "
                        + "rather than failing startup, and the built-in packages are the answer")
                .isEqualTo(registry.all().keySet());
    }

    @Test
    @DisplayName("SH646: with no catalogue configured, the built-in packages are still the answer")
    void theBuiltInPackagesAreTheAnswerWithNoCatalogue() {
        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.source().state())
                .as("SH646: off is a state the listing reports, not an error it throws — a console "
                        + "showing an error where there is content is the failure this avoids")
                .isEqualTo(ShMarketplaceCatalog.BUILTIN_ONLY);
        assertThat(catalogue.items())
                .as("SH646: an instance always has packages, so the listing always has an answer")
                .isNotEmpty();
        assertThat(catalogue.items()).allSatisfy(item -> {
            assertThat(item.builtIn()).isTrue();
            assertThat(item.installed()).isTrue();
            assertThat(item.downloadUrl())
                    .as("a package that is already here has nothing to download")
                    .isNull();
        });
    }

    @Test
    @DisplayName("SH646: the built-in entries are derived from the registry, never restated")
    void theBuiltInEntriesAreDerivedFromTheRegistry() {
        List<String> listed = catalog.catalogue().items().stream()
                .filter(ShMarketplaceItem::builtIn).map(ShMarketplaceItem::id).sorted().toList();

        assertThat(listed)
                .as("SH646: a hand-written copy of a list the program already holds is a list that "
                        + "goes stale with nothing failing when it does. These come from "
                        + "ShBlueprintRegistry, so a package added or renamed there cannot leave "
                        + "the catalogue behind.")
                .isEqualTo(registry.names().stream().sorted().toList());
        assertThat(listed).isNotEmpty();
    }

    @Test
    @DisplayName("SH646: a configured catalogue is read, and its entries join the built-in ones")
    void aConfiguredCatalogueIsRead() throws IOException {
        serve("""
                [
                  {
                    "id": "shio-blueprint-newsroom",
                    "title": "Newsroom",
                    "description": "A wire-service front page.",
                    "version": "1.2.0",
                    "author": "Somebody Else",
                    "category": "community",
                    "tags": ["news"],
                    "icon": "newspaper",
                    "downloadUrl": "https://example.invalid/newsroom.zip",
                    "readmeUrl": "https://example.invalid/newsroom/README.md",
                    "requires": ["createSite"]
                  }
                ]
                """);

        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.source().state()).isEqualTo(ShMarketplaceCatalog.REMOTE);
        ShMarketplaceItem newsroom = catalogue.items().stream()
                .filter(item -> "shio-blueprint-newsroom".equals(item.id())).findFirst()
                .orElseThrow();
        assertThat(newsroom.builtIn()).isFalse();
        assertThat(newsroom.installed())
                .as("SH646: whether a package is here is a fact about this instance, so the server "
                        + "stamps it — a catalogue that could declare it could claim to be "
                        + "first-party")
                .isFalse();
        assertThat(newsroom.requires())
                .as("SH646: the opt-ins ride in the LISTING, so a console can ask for consent "
                        + "before an install rather than after (P7)")
                .containsExactly("createSite");
        assertThat(catalogue.items()).anySatisfy(item -> assertThat(item.builtIn()).isTrue());
    }

    @Test
    @DisplayName("SH646: an unreachable catalogue is reported beside the built-in packages, not thrown")
    void anUnreachableCatalogueDoesNotEmptyTheListing() {
        properties.setEnabled(true);
        // A port nothing is listening on, and a short timeout so the test is not a wait.
        properties.setUrl("http://127.0.0.1:1/marketplace.json");
        properties.setTimeoutMillis(750);

        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.source().state())
                .as("SH646: this is the assertion the task exists for. SH217 held that a package "
                        + "the operator did not write cannot disable the catalogue; a URL that is "
                        + "down is the same hazard one level up, and the six first-party packages "
                        + "have to survive it.")
                .isEqualTo(ShMarketplaceCatalog.UNREACHABLE);
        assertThat(catalogue.items()).isNotEmpty();
        assertThat(catalogue.source().reason())
                .as("and it says WHY, because a listing that silently shrinks is "
                        + "indistinguishable from a catalogue that shrank")
                .isNotBlank();
    }

    @Test
    @DisplayName("SH646: a catalogue that is not a catalogue is a reason, not a stack trace")
    void aMalformedCatalogueIsReported() throws IOException {
        serve("{\"this\": \"is an object, not an array of entries\"}");

        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.source().state()).isEqualTo(ShMarketplaceCatalog.UNREACHABLE);
        assertThat(catalogue.source().reason()).contains("array");
        assertThat(catalogue.items()).isNotEmpty();
    }

    @Test
    @DisplayName("SH646: an entry that would shadow a built-in package is dropped and named")
    void aShadowingEntryIsDroppedAndNamed() throws IOException {
        String first = registry.names().getFirst();
        serve("""
                [
                  {
                    "id": "%s",
                    "title": "Not the real one",
                    "downloadUrl": "https://example.invalid/impostor.zip"
                  }
                ]
                """.formatted(first));

        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.items().stream().filter(item -> first.equals(item.id())).toList())
                .as("SH646: a remote entry shadowing a first-party name would change what an "
                        + "install means without anybody choosing it, which is ShBlueprintRegistry's "
                        + "collision rule read at catalogue level")
                .hasSize(1)
                .allSatisfy(item -> assertThat(item.builtIn()).isTrue());
        assertThat(catalogue.source().reason()).contains(first);
    }

    @Test
    @DisplayName("SH646: an unknown key in an entry is refused the way a manifest refuses one")
    void anUnknownKeyIsRefused() throws IOException {
        serve("[{\"id\": \"x\", \"titel\": \"a typo\"}]");

        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();

        assertThat(catalogue.source().state()).isEqualTo(ShMarketplaceCatalog.UNREACHABLE);
        assertThat(catalogue.source().reason()).contains("titel");
    }

    @Test
    @DisplayName("SH646: the README proxy fetches only a URL the catalogue itself declares")
    void theReadmeProxyIsNotAnOpenProxy() throws IOException {
        String base = serve("""
                [
                  {
                    "id": "shio-blueprint-newsroom",
                    "title": "Newsroom",
                    "readmeUrl": "%s/README.md"
                  }
                ]
                """);

        assertThat(catalog.readme(base + "/README.md"))
                .as("a declared README is fetched")
                .contains("the readme body");

        assertThatThrownBy(() -> catalog.readme("http://169.254.169.254/latest/meta-data/"))
                .as("SH646: the exact allow-list is the catalogue itself. A scheme or host check "
                        + "is a guess about which addresses are safe; the set of URLs the "
                        + "operator's own catalogue offered is not a guess.")
                .isInstanceOf(ShMarketplaceCatalog.UndeclaredReadmeException.class);
    }

    @Test
    @DisplayName("SH1253: the screenshot proxy fetches only a declared raster image, and an entry keeps four")
    void theScreenshotProxyIsNotAnOpenProxy() throws IOException {
        String base = serve("""
                [
                  {
                    "id": "shio-blueprint-showcase",
                    "title": "Showcase",
                    "screenshots": ["%1$s/home.png", "%1$s/logo.svg", "%1$s/c.png", "%1$s/d.png",
                                    "%1$s/e.png"]
                  }
                ]
                """);
        server.createContext("/home.png", exchange -> respond(exchange, "png bytes"));
        server.createContext("/logo.svg", exchange -> respond(exchange, "<svg onload='x()'/>"));

        var item = catalog.catalogue().items().stream()
                .filter(each -> "shio-blueprint-showcase".equals(each.id())).findFirst().orElseThrow();
        assertThat(item.screenshots())
                .as("SH1253: a card and a dialog's worth; the fifth is dropped, not fetched")
                .hasSize(ShMarketplaceItem.MAX_SCREENSHOTS);

        ShMarketplaceCatalog.Screenshot shot = catalog.screenshot(base + "/home.png");
        assertThat(shot.contentType()).isEqualTo("image/png");
        assertThat(new String(shot.bytes(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("png bytes");

        assertThatThrownBy(() -> catalog.screenshot("http://169.254.169.254/latest/meta-data/x.png"))
                .as("SH1253: the README's allow-list, applied to images: undeclared is refused")
                .isInstanceOf(ShMarketplaceCatalog.UndeclaredScreenshotException.class);
        assertThatThrownBy(() -> catalog.screenshot(base + "/logo.svg"))
                .as("SH1253: an SVG can carry script, so even a declared one is not served under the "
                        + "console's origin (the hole SH951 closed for uploads)")
                .isInstanceOf(ShMarketplaceCatalog.UndeclaredScreenshotException.class);
    }

    @Test
    @DisplayName("SH646: with the feature off, the README proxy names the property rather than 404ing blankly")
    void theReadmeProxyTeachesWhenDisabled() {
        assertThatThrownBy(() -> catalog.readme("https://example.invalid/README.md"))
                .isInstanceOf(ShMarketplaceCatalog.DisabledException.class)
                .satisfies(thrown -> assertThat(
                        ((ShMarketplaceCatalog.DisabledException) thrown).problem().toProblemDetail()
                                .getProperties().get("fix").toString())
                        .as("P6: a refusal that does not name the property is indistinguishable "
                                + "from the endpoint not existing")
                        .contains("shio.marketplace.enabled"));
    }

    /**
     * Serve one catalogue body on loopback, and point the properties at it.
     *
     * <p>{@code %s} in the body is replaced with the server's own base URL, so a fixture can
     * declare a README this server also answers.</p>
     *
     * @return the base URL
     */
    private String serve(String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        String catalogue = body.contains("%s") || body.contains("%1$s") ? body.formatted(base) : body;

        server.createContext("/marketplace.json", exchange -> respond(exchange, catalogue));
        server.createContext("/README.md", exchange -> respond(exchange, "# the readme body"));
        server.start();

        properties.setEnabled(true);
        properties.setUrl(base + "/marketplace.json");
        return base;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
