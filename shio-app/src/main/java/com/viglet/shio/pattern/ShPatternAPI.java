/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.pattern;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH1108 — the patterns pushed to this instance, for the console's rail to place.
 *
 * <p>An agent works from the files: {@code shio patterns save|list|apply} read
 * {@code shio/patterns/} and never ask this (P4). What reaches the server is a copy for the one
 * caller that cannot read a project — the console — so the reads are for it and for
 * {@code shio patterns push}, which compares digests to say whether a copy is stale. The push is
 * admin-gated, as installing a package is: a pattern pushed here is offered to every curator on the
 * instance.</p>
 */
@RestController
@RequestMapping("/api/v2/agent/patterns")
@Tag(name = "Agent", description = "The agent surface")
public class ShPatternAPI {

    private final ShPatternStore shPatternStore;

    public ShPatternAPI(ShPatternStore shPatternStore) {
        this.shPatternStore = shPatternStore;
    }

    @GetMapping
    @Operation(summary = "The patterns pushed to this instance, with the digest of each file")
    public List<ShPatternStore.Summary> list() {
        return shPatternStore.list();
    }

    @GetMapping("/{name}")
    @Operation(summary = "One pushed pattern, as the file that was pushed")
    public ShPatternStore.Stored read(@PathVariable String name) {
        return shPatternStore.read(name).orElseThrow(() -> new UnknownPatternException(name,
                shPatternStore.list().stream().map(ShPatternStore.Summary::name).toList()));
    }

    @PutMapping(value = "/{name}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Push shio/patterns/<name>.json, replacing the copy this instance holds (admin)")
    public ShPatternStore.Stored push(@PathVariable String name, @RequestBody byte[] body) {
        return shPatternStore.push(name, body);
    }

    /** No pushed pattern by that name, and the caller is told which ones there are. */
    public static class UnknownPatternException extends ShTeachingException {

        public UnknownPatternException(String name, List<String> known) {
            super(ShProblem.of("unknown-pattern", HttpStatus.NOT_FOUND,
                            "No pattern named '" + name + "' has been pushed to this instance")
                    .withDetail(known.isEmpty() ? "None has been pushed yet."
                            : "Pushed: " + String.join(", ", known) + ".")
                    .withFix("Push it from the project: shio patterns push " + name)
                    .withAllowed(known)
                    .withExample("GET /api/v2/agent/patterns"));
        }
    }
}
