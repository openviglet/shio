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
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Where a resumable upload keeps its bytes until it finishes (SH1355).
 *
 * <p>Outside {@code file_source} for {@link ShAssetRenditionProperties}'s reason: that tree is
 * mapped at {@code /file_source/**}, and a half-received file must not be publicly readable. Nor is
 * it metered: the tenant is charged when the finished file becomes an asset, through the same
 * {@code upload} every other caller uses, and a session that never finishes is swept instead.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.upload-sessions")
public class ShUploadSessionProperties {

    /** Root directory; relative paths resolve against {@code user.dir}, like the renditions'. */
    private String path = "store/upload_sessions";

    /**
     * How long a session lives after it was begun. A day: long enough for a phone that lost its
     * signal on a train to come back, short enough that abandoned halves do not pile up.
     */
    private Duration ttl = Duration.ofHours(24);

    public Path getRootDir() {
        Path configured = Paths.get(path);
        Path resolved = configured.isAbsolute()
                ? configured
                : Paths.get(System.getProperty("user.dir")).resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }
}
