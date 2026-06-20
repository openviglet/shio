/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.viglet.shio.property.ShImageTransformProperties;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import lombok.extern.slf4j.Slf4j;

/**
 * The SH40 resize/crop transform pipeline, built on Thumbnailator (over ImageIO),
 * extended in SH41 with output-format conversion. The actual transform engine: SH42
 * fronts it with the {@code @Primary} {@link ShCachingImageTransformPipeline}, which
 * delegates here on a cache miss, so the {@code /api/v2/staticfile/{id}} controller
 * and the {@code /file_source/**} resource transformer ultimately route through it.
 *
 * <p><strong>Resize/crop (SH40)</strong> — the {@link ShImageCropMode} decides how
 * {@code w}/{@code h} constrain the resize:</p>
 * <ul>
 *   <li>{@code FIT} (default) — scale to fit inside {@code w}×{@code h}, preserve aspect.</li>
 *   <li>{@code FILL} — scale to cover {@code w}×{@code h}, then centre-crop the overflow.</li>
 *   <li>{@code CROP} — centre-crop a {@code w}×{@code h} region with no scaling.</li>
 *   <li>{@code SCALE} — stretch to exactly {@code w}×{@code h}, ignore aspect.</li>
 * </ul>
 *
 * <p><strong>Format conversion (SH41)</strong> — when {@code ?format=} is present the
 * output is re-encoded in that format (the served content type changes to match);
 * otherwise the <em>source</em> format is preserved. A format-only request (no
 * dimension) is now a valid transcode — the image is decoded and re-encoded without
 * resizing. Supported writers: jpeg/png/gif/bmp (ImageIO/jai-imageio) and
 * <strong>webp</strong> (the {@code webp-imageio} SPI). {@code avif} has no Java
 * writer, so {@code ?format=avif} falls through to the graceful passthrough below.
 * Converting an image with alpha to a format that can't carry it (jpeg/bmp) flattens
 * onto a white background first, otherwise the alpha channel garbles the colours.</p>
 *
 * <p>Anything that cannot be safely transformed — a non-raster/unreadable source,
 * a target format with no ImageIO writer, or an I/O failure — falls back to
 * {@link Optional#empty()} so the delivery path serves the original bytes rather
 * than 500ing. SH43 adds the <strong>decode-bomb guard</strong> here: the source
 * pixel dimensions are read off the image header and a source over
 * {@code shio.image-transform.max-source-pixels} declines the same way, before any
 * raster is allocated (the requested-output ceilings and signed-URL gate live in
 * {@link ShImageTransformGuard}, upstream of this pipeline).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShThumbnailatorImageTransformPipeline implements ShImageTransformPipeline {

    private static final String IMAGE_PREFIX = "image/";

    private final ShImageTransformProperties properties;

    public ShThumbnailatorImageTransformPipeline(ShImageTransformProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<ShImageTransformResult> transform(Resource source, String sourceContentType,
                                                      ShImageTransformSpec spec) {
        boolean resizes = spec.width() != null || spec.height() != null;
        boolean converts = spec.format() != null;
        // Nothing to do: neither a dimension nor a target format was requested.
        if (!resizes && !converts) {
            return Optional.empty();
        }

        // Target ImageIO format name: the requested format (SH41) or, absent that, the source's.
        String outputFormat = converts
                ? imageFormatOfParam(spec.format())
                : imageFormatOf(sourceContentType, source.getFilename());
        if (outputFormat == null) {
            log.debug("No re-encodable target format for {} (content-type {}, requested {}); serving original",
                    source.getFilename(), sourceContentType, spec.format());
            return Optional.empty();
        }

        try (InputStream in = source.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                log.debug("No ImageInputStream for {}; serving original", source.getFilename());
                return Optional.empty();
            }
            BufferedImage src = decodeWithinPixelCeiling(iis, source.getFilename());
            if (src == null) {
                // Either undecodable, or a decode-bomb above the source-pixel ceiling per SH43.
                // In both cases serve the original bytes rather than allocate memory or fail.
                return Optional.empty();
            }

            BufferedImage transformed = resizes ? resize(src, spec) : src;
            transformed = flattenIfOpaqueFormat(transformed, outputFormat);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(transformed, outputFormat, out)) {
                log.debug("No ImageIO writer for format {}; serving original", outputFormat);
                return Optional.empty();
            }
            return Optional.of(new ShImageTransformResult(out.toByteArray(), contentTypeFor(outputFormat)));
        } catch (IOException e) {
            log.warn("Image transform failed for {}; serving original", source.getFilename(), e);
            return Optional.empty();
        }
    }

    /**
     * Decode {@code iis} to a {@link BufferedImage}, but first read the source dimensions
     * from the image header and <em>refuse to decode</em> when {@code width × height}
     * exceeds {@code shio.image-transform.max-source-pixels} (SH43). This is the
     * decode-bomb guard: a few-KB file can declare enormous dimensions and only blow up the
     * heap once decoded, so the ceiling is checked off the header before any raster is
     * allocated. Returns {@code null} for an undecodable source or an over-ceiling one
     * (the caller serves the original bytes in both cases).
     */
    private BufferedImage decodeWithinPixelCeiling(ImageInputStream iis, String fileName) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
        if (!readers.hasNext()) {
            log.debug("No ImageIO reader for {}; serving original", fileName);
            return null;
        }
        ImageReader reader = readers.next();
        try {
            reader.setInput(iis, true, true);
            long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
            long ceiling = properties.getMaxSourcePixels();
            if (ceiling > 0 && pixels > ceiling) {
                log.warn("Source {} is {} px, over the {} px transform ceiling; serving original",
                        fileName, pixels, ceiling);
                return null;
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    /**
     * Flatten an image with an alpha channel onto a white background when the target
     * format ({@code jpeg}/{@code bmp}) cannot carry transparency — otherwise the
     * alpha bytes are reinterpreted as colour and the result is garbled.
     */
    private BufferedImage flattenIfOpaqueFormat(BufferedImage img, String format) {
        boolean opaqueFormat = "jpeg".equals(format) || "bmp".equals(format);
        if (!opaqueFormat || !img.getColorModel().hasAlpha()) {
            return img;
        }
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** The content type to serve a given ImageIO output format as. */
    private String contentTypeFor(String format) {
        return "jpeg".equals(format) ? "image/jpeg" : IMAGE_PREFIX + format;
    }

    private BufferedImage resize(BufferedImage src, ShImageTransformSpec spec) throws IOException {
        Integer w = spec.width();
        Integer h = spec.height();
        ShImageCropMode mode = spec.crop() != null ? spec.crop() : ShImageCropMode.FIT;

        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(src);
        switch (mode) {
            case FIT -> sizePreservingAspect(builder, w, h);
            case SCALE -> {
                if (w != null && h != null) {
                    builder.size(w, h).keepAspectRatio(false);
                } else {
                    sizePreservingAspect(builder, w, h);
                }
            }
            case FILL -> {
                int tw = w != null ? w : h;
                int th = h != null ? h : w;
                builder.size(tw, th).crop(Positions.CENTER);
            }
            case CROP -> {
                int tw = w != null ? w : h;
                int th = h != null ? h : w;
                builder.sourceRegion(Positions.CENTER, tw, th).scale(1.0);
            }
        }
        return builder.asBufferedImage();
    }

    /** Fit within the given bounds preserving aspect ratio; a single dimension scales by that axis. */
    private void sizePreservingAspect(Thumbnails.Builder<BufferedImage> builder, Integer w, Integer h) {
        if (w != null && h != null) {
            builder.size(w, h);
        } else if (w != null) {
            builder.width(w);
        } else {
            builder.height(h);
        }
    }

    /**
     * Map a validated {@code ?format=} value (SH41) to its ImageIO writer-format
     * name. {@code avif} has no Java writer, so it returns {@code null} → graceful
     * passthrough. The value has already passed {@link ShImageTransformSpec}'s
     * allow-list, so anything unexpected here also degrades to {@code null}.
     */
    private String imageFormatOfParam(String format) {
        return switch (format) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            case "webp" -> "webp";
            // avif has no Java writer, so it (and anything unhandled) degrades to null
            default -> null;
        };
    }

    /**
     * The ImageIO format name to re-encode in (= the source format; used when no
     * {@code ?format=} conversion was requested), or {@code null} if the source is
     * not a raster image we handle.
     */
    private String imageFormatOf(String contentType, String fileName) {
        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        if (ct.startsWith(IMAGE_PREFIX)) {
            String subtype = ct.substring(IMAGE_PREFIX.length()).trim();
            switch (subtype) {
                case "jpeg", "jpg" -> { return "jpeg"; }
                case "png" -> { return "png"; }
                case "gif" -> { return "gif"; }
                case "bmp" -> { return "bmp"; }
                default -> { /* fall through to extension */ }
            }
        }
        String name = fileName != null ? fileName.toLowerCase(Locale.ROOT) : "";
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (ext) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            default -> null;
        };
    }
}
