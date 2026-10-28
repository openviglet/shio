/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.marketplace;

import java.security.Principal;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.marketplace.ShMarketplaceCatalog;
import com.viglet.shio.marketplace.ShMarketplaceInstaller;
import com.viglet.shio.marketplace.ShMarketplaceSetup;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The package marketplace (SH646, SH675) — browse the catalogue, and install from it.
 *
 * <h2>Reading and writing are separate doors</h2>
 *
 * <p>Listing and reading a README never write. Installing (SH675) puts a stranger's package where
 * the registry will load it, which is a different act from looking at a list — so it is its own
 * verb, it is a {@code POST}, and it takes {@code dryRun} because P7 says the moment a thing
 * happens is the human's to choose. What it still does not do is <b>apply</b>: that stays
 * {@code POST /api/v2/agent/blueprints/&#123;name&#125;/apply}, so an operator can read what they
 * installed before anything touches content.</p>
 *
 * <h2>Why the listing answers even with the feature off</h2>
 *
 * <p>An instance always has packages — the six that ship with it — so "what can I build from"
 * has a true answer whether or not an operator configured a catalogue. Answering 404 for the
 * whole endpoint would make the console show an error where there is content, and would make
 * "the feature is off" indistinguishable from "this build has no marketplace". The
 * <em>source</em> report carries the difference instead, in one object the caller can read.</p>
 *
 * <p>The README proxy is the one that does 404 when disabled: it exists to reach a remote
 * catalogue's documents, so with no catalogue there is nothing it could legitimately fetch, and
 * the refusal names the property that turns it on (P6).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@RestController
@RequestMapping("/api/v2/marketplace")
@Tag(name = "Marketplace", description = "Browse the blueprint packages this instance can install")
public class ShMarketplaceAPI {

    private final ShMarketplaceCatalog catalog;
    private final ShMarketplaceInstaller installer;
    private final ShMarketplaceSetup setup;

    public ShMarketplaceAPI(ShMarketplaceCatalog catalog, ShMarketplaceInstaller installer,
                            ShMarketplaceSetup setup) {
        this.catalog = catalog;
        this.installer = installer;
        this.setup = setup;
    }

    /**
     * Every package this instance could install, with the built-in ones always present.
     *
     * <p>The {@code source} object says where the entries came from: {@code builtin-only} when no
     * catalogue is configured, {@code remote} when one was read, and {@code unreachable} when one
     * is configured and could not be — the last carrying the reason, because a listing that
     * silently shrinks is indistinguishable from a catalogue that shrank.</p>
     */
    @GetMapping
    @Operation(summary = "The packages this instance can install, and where the list came from")
    public ShMarketplaceCatalog.Catalogue list() {
        return catalog.catalogue();
    }

    /**
     * One entry's README, fetched by the server rather than the browser.
     *
     * <p>Proxied for the reason Turing's is — a catalogue on another host is a CORS failure in a
     * browser — and restricted to a URL the catalogue itself declares, which is what keeps a
     * proxy from being an open one.</p>
     */
    @GetMapping(value = "/readme", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "The README of a catalogue entry, by its declared readmeUrl")
    public ResponseEntity<String> readme(@RequestParam(name = "url") String url) {
        return ResponseEntity.ok(catalog.readme(url));
    }

    /**
     * Download a catalogue entry and put it where the registry will load it (SH675).
     *
     * <p>{@code dryRun=true} does everything except the move into place: it fetches, expands,
     * checks the archive is safe and that the package agrees with the catalogue about its own
     * name, and answers what would be installed. That is the same courtesy a blueprint apply
     * already extends, at the step before it.</p>
     *
     * <p>An install that succeeds leaves a package the registry loads, and one that fails leaves
     * nothing — including the case where the download was fine and the <em>package</em> is not,
     * which is rolled back rather than left for the operator to discover on the next read.</p>
     */
    @PostMapping("/{id}/install")
    @Operation(summary = "Install a catalogue package; dryRun validates it and writes nothing")
    public ShMarketplaceInstaller.Installed install(
            @PathVariable String id,
            @RequestParam(name = "dryRun", required = false, defaultValue = "false")
            boolean dryRun) {
        return installer.install(id, dryRun);
    }

    /**
     * SH1251 — install if needed, then apply to a site: the console dialog's one call.
     *
     * <p>{@code dryRun=true} in the body plans both halves and writes nothing, including the apply
     * plan of a package not yet installed, which is read from the download and then discarded. A
     * real run installs first and keeps the package if the apply then refuses.</p>
     */
    @PostMapping("/{id}/setup")
    @Operation(summary = "Install a package if needed and apply it to a site; dryRun plans both")
    public ShMarketplaceSetup.Result setup(@PathVariable String id,
                                           @RequestBody ShMarketplaceSetup.Request request,
                                           Principal principal) {
        return setup.run(id, request, principal == null ? null : principal.getName());
    }

    /**
     * Remove an installed package.
     *
     * <p>Only one that came from a catalogue: a package that shipped with the instance is on the
     * classpath, so "uninstalled" would be a claim the next restart contradicts.</p>
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Uninstall a package that was installed from a catalogue")
    public ShMarketplaceInstaller.Installed uninstall(@PathVariable String id) {
        return installer.uninstall(id);
    }
}
