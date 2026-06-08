/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile.transform;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import lombok.extern.slf4j.Slf4j;

/**
 * The SH40 resize/crop transform pipeline, built on Thumbnailator (over ImageIO).
 * It replaces the SH39 no-op as the {@code @Primary} {@link ShImageTransformPipeline},
 * so the {@code /api/v2/staticfile/{id}} controller and the {@code /file_source/**}
 * resource transformer now actually transform images.
 *
 * <p><strong>Scope (deliberately resize/crop only):</strong> the output is always
 * re-encoded in the <em>source</em> format. Honouring the {@code format} parameter
 * (webp/avif/jpeg negotiation) is SH41, so a {@code ?format=} with no dimension is
 * a no-op here (returns empty → original bytes). The {@link ShImageCropMode}
 * decides how {@code w}/{@code h} constrain the resize:</p>
 * <ul>
 *   <li>{@code FIT} (default) — scale to fit inside {@code w}×{@code h}, preserve aspect.</li>
 *   <li>{@code FILL} — scale to cover {@code w}×{@code h}, then centre-crop the overflow.</li>
 *   <li>{@code CROP} — centre-crop a {@code w}×{@code h} region with no scaling.</li>
 *   <li>{@code SCALE} — stretch to exactly {@code w}×{@code h}, ignore aspect.</li>
 * </ul>
 *
 * <p>Anything that cannot be safely transformed — a non-raster/unreadable source,
 * a format ImageIO can't re-encode, or an I/O failure — falls back to
 * {@link Optional#empty()} so the delivery path serves the original bytes rather
 * than 500ing. Resource ceilings (max dimensions, decode bombs) are SH43.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Primary
@Slf4j
public class ShThumbnailatorImageTransformPipeline implements ShImageTransformPipeline {

    @Override
    public Optional<ShImageTransformResult> transform(Resource source, String sourceContentType,
                                                      ShImageTransformSpec spec) {
        // Resize/crop needs at least one target dimension; a format-only request is SH41's job.
        if (spec.width() == null && spec.height() == null) {
            return Optional.empty();
        }

        String imageFormat = imageFormatOf(sourceContentType, source.getFilename());
        if (imageFormat == null) {
            log.debug("Source {} is not a re-encodable image (content-type {}); serving original",
                    source.getFilename(), sourceContentType);
            return Optional.empty();
        }

        try (InputStream in = source.getInputStream()) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                log.debug("ImageIO could not decode {}; serving original", source.getFilename());
                return Optional.empty();
            }

            BufferedImage transformed = resize(src, spec);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(transformed, imageFormat, out)) {
                log.debug("No ImageIO writer for format {}; serving original", imageFormat);
                return Optional.empty();
            }
            String contentType = sourceContentType != null ? sourceContentType : "image/" + imageFormat;
            return Optional.of(new ShImageTransformResult(out.toByteArray(), contentType));
        } catch (IOException e) {
            log.warn("Image transform failed for {}; serving original", source.getFilename(), e);
            return Optional.empty();
        }
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
     * The ImageIO format name to re-encode in (= the source format; SH40 does not
     * convert), or {@code null} if the source is not a raster image we handle.
     */
    private String imageFormatOf(String contentType, String fileName) {
        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        if (ct.startsWith("image/")) {
            String subtype = ct.substring("image/".length()).trim();
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
