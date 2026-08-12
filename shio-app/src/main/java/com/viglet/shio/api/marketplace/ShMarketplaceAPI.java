/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.marketplace;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.marketplace.ShMarketplaceCatalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The package marketplace (SH646) — browse the catalogue this instance can install from.
 *
 * <h2>Two verbs, and neither one writes</h2>
 *
 * <p>Listing and reading a README is the whole of this task. Installing is SH675 and stays a
 * separate door on purpose: an install puts a stranger's package where the registry will load
 * it, which is a different act from looking at a list, and P7 says the moment it happens is the
 * human's to choose.</p>
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

    public ShMarketplaceAPI(ShMarketplaceCatalog catalog) {
        this.catalog = catalog;
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
}
