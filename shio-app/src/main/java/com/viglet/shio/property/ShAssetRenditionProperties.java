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
 * Where an asset's previous bytes are kept after a replace (SH1035).
 *
 * <h2>Outside {@code file_source}, and that is the whole reason this exists</h2>
 *
 * <p>The obvious home for a rendition is a sub-directory of the asset's own
 * {@code <root>/<tenant>/<fileId>/}, and it is the one place they must not go:
 * {@code ShStaticResourceConfiguration} maps {@code /file_source/**} at the root of that tree, so
 * anything under it is <b>publicly downloadable</b>. A superseded logo, a corrected screenshot, a
 * document replaced because it said the wrong thing — every one of those would stay fetchable by
 * anyone who guessed the path. So renditions live in a sibling directory with no resource handler
 * over it, reached only through the authenticated console API, which is the same arrangement
 * {@code shio.image-cache} already uses for derived bytes.</p>
 *
 * <h2>Bounded, because they are not counted</h2>
 *
 * <p>These bytes do <b>not</b> go through {@code ShFileStorageService}'s tenant meter, and the
 * reason is SH110's: a replace charges the plan the <em>difference</em>, so counting a rendition
 * would make replacing a 900 MB video with a 1 MB one <em>increase</em> the tenant's usage, and a
 * tenant near its quota could not fix a mistake. What keeps that honest is {@link #getKeep}: an
 * asset holds at most that many superseded copies and the oldest is dropped, so the safety net's
 * cost is bounded by the asset's own size rather than by how often somebody replaces it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.asset-renditions")
public class ShAssetRenditionProperties {

    /**
     * Whether a replace keeps the bytes it supersedes.
     *
     * <p>On by default: §LXX.7's argument is that the copy is what makes replacing <em>safe enough
     * to encourage</em>, and a safety net nobody enables is not one. It is switchable for the
     * instance that genuinely cannot afford the disk, and that instance's replace then behaves
     * exactly as it did before this shipped.</p>
     */
    private boolean enabled = true;

    /** Root directory; relative paths resolve against {@code user.dir}, like the image cache's. */
    private String path = "store/asset_renditions";

    /**
     * How many superseded copies an asset keeps.
     *
     * <p>Three, not one: the mistake a revert exists for is often noticed after the second
     * replace, and "the version before the one before this" is a real request. Not unbounded,
     * because these bytes are deliberately outside the tenant meter — see the class note.</p>
     */
    private int keep = 3;

    public Path getRootDir() {
        Path configured = Paths.get(path);
        Path resolved = configured.isAbsolute()
                ? configured
                : Paths.get(System.getProperty("user.dir")).resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }
}
