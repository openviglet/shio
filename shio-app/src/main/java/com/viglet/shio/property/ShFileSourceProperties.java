/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.property;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the file_source upload/serve subsystem (SH38).
 *
 * <p>{@code shio.file-source.path} is the on-disk root where uploaded binaries
 * live. A relative value (the default {@code store/file_source}) resolves
 * against {@code user.dir}, matching the dev H2 database location
 * ({@code ./store/db/shioDB}). Each uploaded file is stored under
 * {@code <root>/<tenant>/<filePostId>/<fileName>} — keying by the (globally
 * unique) post id rather than the folder tree means a folder rename/move never
 * invalidates a stored path, and the per-tenant prefix (SH47, applied by
 * {@code ShFileStorageService}) isolates each tenant's bytes on disk. Files
 * uploaded before SH47 live in the old flat {@code <root>/<filePostId>/<fileName>}
 * layout and are still served via a read fallback.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.file-source")
public class ShFileSourceProperties {

    /** Root directory for stored binaries; relative paths resolve against {@code user.dir}. */
    private String path = "store/file_source";

    /**
     * The absolute, normalized root directory. A relative {@link #path} is
     * resolved against the process working directory ({@code user.dir}).
     */
    public Path getRootDir() {
        Path configured = Paths.get(path);
        Path resolved = configured.isAbsolute()
                ? configured
                : Paths.get(System.getProperty("user.dir")).resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }
}
