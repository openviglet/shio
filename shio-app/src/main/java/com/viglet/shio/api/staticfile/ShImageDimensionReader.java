/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads the pixel dimensions of an image from its <em>header only</em> (SH62) —
 * no full raster is decoded, so a multi-megapixel upload costs only the metadata
 * read. Used at upload time to stamp {@code data.WIDTH}/{@code data.HEIGHT} on a
 * {@code File} post so the Media Library can show dimensions without re-reading
 * the bytes on every listing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
@Slf4j
public class ShImageDimensionReader {

    /** {@code [width, height]} read from the image header, or {@code null} if undecodable. */
    public int[] read(InputStream input) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new int[] { reader.getWidth(0), reader.getHeight(0) };
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Could not read image dimensions", e);
            return null;
        }
    }
}
