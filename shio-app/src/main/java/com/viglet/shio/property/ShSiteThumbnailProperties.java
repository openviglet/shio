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
 * Where each site's home-page picture is kept (SH1218).
 *
 * <p>A sibling of {@code file_source} with no resource handler over it, for the reason
 * {@link ShAssetRenditionProperties} gives: the picture of a home that is not public yet must not
 * be fetchable by anyone who guesses a path. It is served through the authenticated console API.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Setter
@Getter
@Component
@ConfigurationProperties("shio.site-thumbnails")
public class ShSiteThumbnailProperties {

    /** Root directory; relative paths resolve against {@code user.dir}, like the image cache's. */
    private String path = "store/site_thumbnails";

    /**
     * The largest picture a site may store, in bytes.
     *
     * <p>A card in a grid is a few hundred pixels wide; 512 KB is several times what a compressed
     * picture of that size needs, and small enough that a whole page of them is not the cost.</p>
     */
    private int maxBytes = 512 * 1024;

    public Path getRootDir() {
        Path configured = Paths.get(path);
        Path resolved = configured.isAbsolute()
                ? configured
                : Paths.get(System.getProperty("user.dir")).resolve(configured);
        return resolved.toAbsolutePath().normalize();
    }
}
