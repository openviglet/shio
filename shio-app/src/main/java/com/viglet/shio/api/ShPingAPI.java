/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lightweight liveness probe used by the frontend to detect when the backend
 * is unreachable. Public (no auth) so it can be polled while authentication
 * may be misconfigured. Returns a fixed JSON body and never touches state.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/ping")
@Tag(name = "Ping API", description = "Backend liveness probe")
public class ShPingAPI {

    @GetMapping
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
