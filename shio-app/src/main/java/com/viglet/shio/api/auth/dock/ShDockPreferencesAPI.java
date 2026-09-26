/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.auth.dock;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.api.auth.ShCurrentUsername;
import com.viglet.shio.api.auth.dock.ShDockPreferencesService.ShDockPreferences;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * SH971 — the signed-in curator's dock preferences, read and replaced whole.
 *
 * <p>A console preference with no agent equivalent: an agent has no dock. It is classified as such
 * in the P1 declaration rather than argued here.</p>
 */
@RestController
@RequestMapping(value = "/api/v2/user/current/dock", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "User", description = "User API")
public class ShDockPreferencesAPI {

    private final ShDockPreferencesService service;

    public ShDockPreferencesAPI(ShDockPreferencesService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "The signed-in curator's dock preferences")
    public ShDockPreferences get() {
        return service.of(username());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the signed-in curator's dock preferences")
    public ShDockPreferences put(@RequestBody ShDockPreferences preferences) {
        return service.save(username(), preferences);
    }

    /** The same name `ShUserAPI.current` resolves the user record by, for both sign-in kinds. */
    private static String username() {
        return ShCurrentUsername.get();
    }
}
