/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Creates staging directories for site import/export under the system temp root,
 * but restricted to the owner so the (transient) package contents are not exposed
 * in a world-readable/writable location (S5443). On POSIX file systems the
 * directory is created atomically with {@code rwx------}; on non-POSIX file
 * systems (e.g. Windows/NTFS) the default ACL already confines it to the owner.
 *
 * @author Alexandre Oliveira
 */
final class ShExchangeTempDir {

    private ShExchangeTempDir() {
    }

    static Path create(String prefix) throws IOException {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return Files.createTempDirectory(prefix,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
        return Files.createTempDirectory(prefix);
    }
}
