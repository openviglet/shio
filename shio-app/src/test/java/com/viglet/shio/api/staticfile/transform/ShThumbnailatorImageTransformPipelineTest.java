/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class ShThumbnailatorImageTransformPipelineTest {

    private final ShThumbnailatorImageTransformPipeline pipeline = new ShThumbnailatorImageTransformPipeline();

    private static Resource image(int w, int h, String format, String fileName) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, format, baos), "fixture must encode");
        byte[] bytes = baos.toByteArray();
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private static BufferedImage decode(ShImageTransformResult result) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(result.bytes()));
        assertNotNull(img, "result must be a decodable image");
        return img;
    }

    @Test
    void fitPreservesAspectWithinBounds() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, 100, null, ShImageCropMode.FIT)).orElseThrow();

        BufferedImage img = decode(out);
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight(), "aspect ratio preserved → fits within 100x100");
        assertEquals("image/png", out.contentType());
    }

    @Test
    void fillCoversAndCropsToExactSize() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, 100, null, ShImageCropMode.FILL)).orElseThrow();

        BufferedImage img = decode(out);
        assertEquals(100, img.getWidth());
        assertEquals(100, img.getHeight(), "fill crops to exactly w x h");
    }

    @Test
    void scaleStretchesToExactSizeIgnoringAspect() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(80, 80, null, ShImageCropMode.SCALE)).orElseThrow();

        BufferedImage img = decode(out);
        assertEquals(80, img.getWidth());
        assertEquals(80, img.getHeight());
    }

    @Test
    void widthOnlyScalesByThatAxis() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, null, null, null)).orElseThrow();

        BufferedImage img = decode(out);
        assertEquals(100, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    void cropTakesRegionWithoutScaling() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(50, 50, null, ShImageCropMode.CROP)).orElseThrow();

        BufferedImage img = decode(out);
        assertEquals(50, img.getWidth());
        assertEquals(50, img.getHeight());
    }

    @Test
    void preservesSourceFormatOnJpeg() throws IOException {
        Resource src = image(200, 100, "jpeg", "photo.jpg");
        ShImageTransformResult out = pipeline.transform(src, "image/jpeg",
                new ShImageTransformSpec(100, null, null, null)).orElseThrow();

        assertEquals("image/jpeg", out.contentType());
        assertEquals(100, decode(out).getWidth());
    }

    @Test
    void formatOnlyRequestIsNoOpUntilSh41() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        Optional<ShImageTransformResult> out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(null, null, "webp", null));

        assertTrue(out.isEmpty(), "no dimension → SH40 has nothing to resize; format conversion is SH41");
    }

    @Test
    void nonImageSourcePassesThrough() {
        Resource notImage = new ByteArrayResource("not an image".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "notes.png";
            }
        };
        Optional<ShImageTransformResult> out = pipeline.transform(notImage, "image/png",
                new ShImageTransformSpec(100, 100, null, null));

        assertTrue(out.isEmpty(), "undecodable bytes fall back to the original");
    }

    @Test
    void unsupportedSourceTypePassesThrough() throws IOException {
        // A real image, but addressed as a type SH40 does not re-encode.
        Resource src = image(200, 100, "png", "data.pdf");
        Optional<ShImageTransformResult> out = pipeline.transform(src, "application/pdf",
                new ShImageTransformSpec(100, 100, null, null));

        assertTrue(out.isEmpty(), "non-image content type → serve original");
    }
}
