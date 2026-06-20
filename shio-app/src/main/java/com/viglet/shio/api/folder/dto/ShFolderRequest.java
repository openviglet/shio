/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.folder.dto;

/**
 * Request DTO for console folder create/update (SonarQube java:S4684 — never bind a
 * persistence entity straight off the wire). The component name mirrors the exact JSON
 * field name the {@code ShFolder} entity used to bind, so the React console payload
 * still binds unchanged. {@code ShFolderWriteService.create/update} read only
 * {@code payload.getName()} off the entity (name + a {@code furl} derived from it via
 * {@code ShURLFormatter.format(name)}), and the controller's blank check also reads
 * {@code getName()} — so {@code name} is the only mirrored field.
 *
 * @author Alexandre Oliveira
 */
public record ShFolderRequest(String name) {
}
