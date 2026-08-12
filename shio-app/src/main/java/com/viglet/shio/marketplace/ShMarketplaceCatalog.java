/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.marketplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.ShBlueprintCodec;
import com.viglet.shio.blueprint.ShBlueprintRegistry;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.marketplace.dto.ShMarketplaceItem;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * The package catalogue (SH646) — what this instance could install, and what it already has.
 *
 * <h2>The built-in entries are derived, not written down</h2>
 *
 * <p>The six first-party packages are already loaded, already validated and already carry a
 * title, a description, a version and a {@code params} schema. So the catalogue reads them out
 * of {@link ShBlueprintRegistry} rather than restating them in a JSON file beside them. A
 * hand-maintained duplicate of a list the program already holds is a list that goes stale, and
 * nothing would fail when it did — which is the defect class this project keeps paying for.</p>
 *
 * <p>They carry no {@code downloadUrl}, because there is nothing to fetch: they are here. What
 * seeds the *remote* catalogue with the same six is SH676, and that is a publishing step rather
 * than a second source of truth.</p>
 *
 * <h2>A remote catalogue that fails does not empty the listing</h2>
 *
 * <p>SH217 established that a package the operator did not write must not be able to disable the
 * catalogue; a URL that is slow, moved, or serving something that is not a catalogue is the same
 * hazard one level up. So a fetch failure is <b>reported beside the built-in packages</b> rather
 * than thrown: the console shows what is true, and says what it could not reach.</p>
 *
 * <p>The one thing that <em>is</em> refused outright is a duplicate id, and for
 * {@code ShBlueprintRegistry}'s reason: a remote entry shadowing {@code blog} would change what
 * an install means without anybody choosing it, and neither winner is defensible. A remote entry
 * whose id collides with a built-in package is dropped and named in the source report.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShMarketplaceCatalog {

    /** The catalogue was not read because the operator has not enabled it. */
    public static final String BUILTIN_ONLY = "builtin-only";

    /** The catalogue was read. */
    public static final String REMOTE = "remote";

    /** The catalogue was configured and could not be read; the built-in packages still list. */
    public static final String UNREACHABLE = "unreachable";

    private final ShMarketplaceProperties properties;
    private final ShBlueprintRegistry registry;
    private final ShBlueprintCodec codec;

    /** Created on first fetch; {@link HttpClient} is thread-safe and reusable. */
    private volatile HttpClient client;

    public ShMarketplaceCatalog(ShMarketplaceProperties properties, ShBlueprintRegistry registry,
                                ShBlueprintCodec codec) {
        this.properties = properties;
        this.registry = registry;
        this.codec = codec;
    }

    /**
     * Where the listing's entries came from.
     *
     * @param url    the configured catalogue, or null when the feature is off
     * @param state  one of {@link #BUILTIN_ONLY}, {@link #REMOTE}, {@link #UNREACHABLE}
     * @param reason why a configured catalogue is not in the listing, in one sentence
     */
    public record Source(String url, String state, String reason) {
    }

    /**
     * The catalogue.
     *
     * @param items  every package, built-in first, then whatever the remote catalogue added
     * @param source what happened when this instance tried to read the catalogue
     */
    public record Catalogue(List<ShMarketplaceItem> items, Source source) {
    }

    /** Every package this instance could install, with the built-in ones always present. */
    public Catalogue catalogue() {
        List<ShMarketplaceItem> builtIn = builtInItems();
        if (!properties.hasRemote()) {
            return new Catalogue(builtIn,
                    new Source(null, BUILTIN_ONLY, "shio.marketplace.enabled is false"));
        }

        String url = properties.getUrl();
        List<ShMarketplaceItem> remote;
        try {
            remote = fetch(url);
        } catch (RuntimeException unreachable) {
            return new Catalogue(builtIn, new Source(url, UNREACHABLE, unreachable.getMessage()));
        }

        Set<String> taken = new LinkedHashSet<>();
        builtIn.forEach(item -> taken.add(item.id()));
        List<ShMarketplaceItem> items = new ArrayList<>(builtIn);
        List<String> shadowed = new ArrayList<>();
        Set<String> installed = new LinkedHashSet<>(registry.names());
        for (ShMarketplaceItem item : remote) {
            if (!taken.add(item.id())) {
                shadowed.add(item.id());
                continue;
            }
            items.add(item.asInstalled(installed.contains(item.id())));
        }

        String reason = shadowed.isEmpty() ? null
                : "dropped, because a package of this name already exists here: "
                        + String.join(", ", shadowed);
        return new Catalogue(items, new Source(url, REMOTE, reason));
    }

    /**
     * The README of one catalogue entry.
     *
     * <h2>Why the allow-list is the catalogue itself</h2>
     *
     * <p>An endpoint that fetches whatever URL it is handed is a server-side request forgery,
     * and the usual answers — a scheme check, a host allow-list — are guesses about which
     * addresses are safe. There is an exact answer available here and it costs nothing: the
     * catalogue already declares every README it has, so <b>a URL the catalogue does not name is
     * refused</b>. The reachable set is exactly the set the operator's own configured catalogue
     * offered, which is the same authority that put the entries there.</p>
     *
     * @param url the {@code readmeUrl} of an entry in the current catalogue
     * @return the README body
     * @throws ShTeachingException when the URL is not one the catalogue declares
     */
    public String readme(String url) {
        if (!properties.hasRemote()) {
            // Not "no such README": with no catalogue configured there is no README this could
            // legitimately fetch, and the caller needs to know it is a setting rather than a
            // missing document — the two look identical from a 400 (P6).
            throw new DisabledException();
        }
        Set<String> declared = new LinkedHashSet<>();
        for (ShMarketplaceItem item : catalogue().items()) {
            if (item.readmeUrl() != null && !item.readmeUrl().isBlank()) {
                declared.add(item.readmeUrl());
            }
        }
        if (url == null || !declared.contains(url)) {
            throw new UndeclaredReadmeException(url);
        }
        return new String(download(url), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** The packages that shipped with the instance, read from the registry rather than restated. */
    private List<ShMarketplaceItem> builtInItems() {
        List<ShMarketplaceItem> items = new ArrayList<>();
        for (ShBlueprint.Summary summary : registry.summaries()) {
            items.add(new ShMarketplaceItem(summary.name(), summary.title(), summary.description(),
                    summary.version(), ShMarketplaceItem.VIGLET, ShMarketplaceItem.FIRST_PARTY,
                    List.of(), summary.name(), null, null, summary.params(), List.of(),
                    true, true));
        }
        return items;
    }

    /** Read and parse the configured catalogue. Any failure is one exception with a sentence. */
    private List<ShMarketplaceItem> fetch(String url) {
        JsonNode root;
        try {
            root = codec.readTree(new java.io.ByteArrayInputStream(download(url)));
        } catch (JacksonException | java.io.IOException malformed) {
            throw new IllegalStateException(
                    "the catalogue is not JSON: " + malformed.getMessage(), malformed);
        }
        if (root == null || !root.isArray()) {
            throw new IllegalStateException(
                    "the catalogue is not a JSON array of entries");
        }

        List<ShMarketplaceItem> items = new ArrayList<>();
        for (JsonNode entry : root) {
            items.add(itemOf(entry));
        }
        return items;
    }

    /** One entry, with an unknown key refused the way a blueprint manifest refuses one. */
    private ShMarketplaceItem itemOf(JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            throw new IllegalStateException("a catalogue entry is not an object");
        }
        for (String key : entry.propertyNames()) {
            if (!ShMarketplaceItem.KEYS.contains(key)) {
                throw new IllegalStateException("a catalogue entry carries an unknown key '"
                        + key + "'; allowed: " + String.join(", ", ShMarketplaceItem.KEYS));
            }
        }
        String id = text(entry, "id");
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("a catalogue entry has no id");
        }
        return new ShMarketplaceItem(id, text(entry, "title"), text(entry, "description"),
                text(entry, "version"), text(entry, "author"), text(entry, "category"),
                strings(entry, "tags"), text(entry, "icon"), text(entry, "downloadUrl"),
                text(entry, "readmeUrl"), entry.get("params"), strings(entry, "requires"),
                false, false);
    }

    private static String text(JsonNode entry, String key) {
        JsonNode value = entry.get(key);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static List<String> strings(JsonNode entry, String key) {
        JsonNode value = entry.get(key);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        value.forEach(each -> out.add(each.asString()));
        return out;
    }

    /** One GET, with the configured timeout, and any failure as a sentence rather than a stack. */
    private byte[] download(String url) {
        URI target;
        try {
            target = URI.create(url);
        } catch (IllegalArgumentException malformed) {
            throw new IllegalStateException("'" + url + "' is not a URL", malformed);
        }
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response =
                    client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(url + " answered " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(url + " was interrupted", interrupted);
        } catch (java.io.IOException unreachable) {
            throw new IllegalStateException(
                    url + " could not be read: " + unreachable.getMessage(), unreachable);
        }
    }

    private HttpClient client() {
        HttpClient existing = client;
        if (existing == null) {
            synchronized (this) {
                existing = client;
                if (existing == null) {
                    existing = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMillis(properties.getTimeoutMillis()))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    client = existing;
                }
            }
        }
        return existing;
    }

    /** The feature is off, and the caller is told which property turns it on. */
    public static class DisabledException extends ShTeachingException {

        public DisabledException() {
            super(ShProblem.of("marketplace-disabled", HttpStatus.NOT_FOUND,
                            "The package marketplace is not enabled on this instance")
                    .withDetail("A catalogue is read only when an operator has turned it on and "
                            + "named it, because doing so is the act of vouching for what it "
                            + "offers (SH646).")
                    .withFix("Set shio.marketplace.enabled=true, and shio.marketplace.url if the "
                            + "catalogue is not the default one.")
                    .withExample("shio.marketplace.enabled=true"));
        }
    }

    /** A README this catalogue never offered, which is the whole of the SSRF answer. */
    public static class UndeclaredReadmeException extends ShTeachingException {

        public UndeclaredReadmeException(String sent) {
            super(ShProblem.of("undeclared-readme", HttpStatus.BAD_REQUEST,
                            "That README is not one this catalogue offers")
                    .withDetail("Sent: " + (sent == null ? "(nothing)" : sent)
                            + ". This endpoint fetches only a URL the configured catalogue "
                            + "declares, so it cannot be pointed at an arbitrary host.")
                    .withFix("Use the readmeUrl of an entry from GET /api/v2/marketplace."));
        }
    }
}
