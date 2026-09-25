/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.note;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** SH1006 — whether the dock may offer its chat: is an agent around to answer. */
@RestController
@RequestMapping("/api/v2/note/relay")
@Tag(name = "Note", description = "Corrections anchored to a page, a field or a revision (SH1003)")
public class ShNoteRelayAPI {

    private final ShNoteRelay shNoteRelay;

    public ShNoteRelayAPI(ShNoteRelay shNoteRelay) {
        this.shNoteRelay = shNoteRelay;
    }

    @GetMapping
    @Operation(summary = "Whether an agent run started recently enough to answer the dock's chat")
    public ShNoteRelay.Status status() {
        return shNoteRelay.status();
    }
}
