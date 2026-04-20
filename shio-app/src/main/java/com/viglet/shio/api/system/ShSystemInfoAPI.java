/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.shio.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Exposes runtime system information (currently only the application version)
 * used by the frontend footer. The version is read from the JAR manifest's
 * Implementation-Version, falling back to "dev" when running from the IDE.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/system/info")
@Tag(name = "System Information", description = "System Information API")
public class ShSystemInfoAPI {

    @Operation(summary = "Get system information overview")
    @GetMapping
    public ShSystemInfoBean getSystemInfo() {
        return new ShSystemInfoBean(getAppVersion());
    }

    private String getAppVersion() {
        String version = getClass().getPackage().getImplementationVersion();
        return version != null ? version : "dev";
    }

    public record ShSystemInfoBean(String appVersion) {
    }
}
