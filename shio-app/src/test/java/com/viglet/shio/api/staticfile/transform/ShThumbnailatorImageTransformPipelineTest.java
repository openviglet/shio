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

import com.viglet.shio.property.ShImageTransformProperties;

class ShThumbnailatorImageTransformPipelineTest {

    private final ShImageTransformProperties properties = new ShImageTransformProperties();
    private final ShThumbnailatorImageTransformPipeline pipeline =
            new ShThumbnailatorImageTransformPipeline(properties);

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
    void formatOnlyRequestConvertsWithoutResizing() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(null, null, "jpeg", null)).orElseThrow();

        assertEquals("image/jpeg", out.contentType(), "format-only request transcodes (SH41)");
        BufferedImage img = decode(out);
        assertEquals(200, img.getWidth(), "no dimension → original size preserved");
        assertEquals(100, img.getHeight());
    }

    @Test
    void convertsPngToJpegChangingContentType() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, null, "jpeg", null)).orElseThrow();

        assertEquals("image/jpeg", out.contentType(), "output content type follows ?format=");
        assertEquals(100, decode(out).getWidth());
    }

    @Test
    void convertsToWebp() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, null, "webp", null)).orElseThrow();

        assertEquals("image/webp", out.contentType());
        BufferedImage img = decode(out); // webp-imageio registers a reader too
        assertEquals(100, img.getWidth());
    }

    @Test
    void flattensAlphaWhenConvertingToJpeg() throws IOException {
        // A transparent ARGB source converted to JPEG (which has no alpha) must
        // flatten cleanly rather than garble — the result must stay decodable.
        BufferedImage argb = new BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.setColor(new Color(0, 0, 255, 128));
        g.fillRect(0, 0, 120, 80);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(argb, "png", baos));
        Resource src = new ByteArrayResource(baos.toByteArray()) {
            @Override
            public String getFilename() {
                return "alpha.png";
            }
        };

        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(null, null, "jpeg", null)).orElseThrow();

        assertEquals("image/jpeg", out.contentType());
        assertNotNull(decode(out));
    }

    @Test
    void avifFormatGracefullyDegrades() throws IOException {
        // No Java AVIF writer is bundled, so ?format=avif serves the original bytes.
        Resource src = image(200, 100, "png", "wide.png");
        Optional<ShImageTransformResult> out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(100, null, "avif", null));

        assertTrue(out.isEmpty(), "avif has no Java writer → graceful passthrough");
    }

    @Test
    void noParametersIsNoOp() throws IOException {
        Resource src = image(200, 100, "png", "wide.png");
        Optional<ShImageTransformResult> out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(null, null, null, null));

        assertTrue(out.isEmpty(), "neither dimension nor format → nothing to do");
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

    @Test
    void sourceOverPixelCeilingDeclines() throws IOException {
        // SH43 decode-bomb guard: a source whose declared dimensions exceed
        // max-source-pixels is refused before decode → original bytes serve.
        properties.setMaxSourcePixels(10_000L); // 100x100 = 10_000, so 200x100 is over
        Resource src = image(200, 100, "png", "big.png");
        Optional<ShImageTransformResult> out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(50, 50, null, null));

        assertTrue(out.isEmpty(), "over-ceiling source declines without decoding");
    }

    @Test
    void sourceWithinPixelCeilingTransforms() throws IOException {
        // The same ceiling still permits a within-bounds source.
        properties.setMaxSourcePixels(100_000L); // 200x100 = 20_000, under
        Resource src = image(200, 100, "png", "ok.png");
        ShImageTransformResult out = pipeline.transform(src, "image/png",
                new ShImageTransformSpec(50, null, null, null)).orElseThrow();

        assertEquals(50, decode(out).getWidth());
    }
}
