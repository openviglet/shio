/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Optional;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.api.staticfile.transform.ShImageTransformGuard;
import com.viglet.shio.api.staticfile.transform.ShImageTransformPipeline;
import com.viglet.shio.api.staticfile.transform.ShImageTransformResult;
import com.viglet.shio.api.staticfile.transform.ShImageTransformSigner;
import com.viglet.shio.api.staticfile.transform.ShImageTransformSpec;
import com.viglet.shio.bean.error.ShHttpMessageBean;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

/**
 * Static-file upload/serve API (SH38). The {@code File} content widget posts a
 * binary here; the response is the created {@code File} post (its {@code id} is
 * what the widget stores). Bytes are served either through this controller
 * ({@code GET /{id}}) or directly from the {@code /file_source/**} resource
 * handler.
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@RestController
@RequestMapping("/api/v2/staticfile")
@Tag(name = "Static File", description = "File upload / serve API (SH38)")
@Slf4j
public class ShStaticFileAPI {

    private final ShStaticFileService shStaticFileService;
    private final ShFileStorageService storage;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShImageTransformPipeline imageTransformPipeline;
    private final ShImageTransformGuard imageTransformGuard;
    private final ShImageTransformSigner imageTransformSigner;

    public ShStaticFileAPI(ShStaticFileService shStaticFileService,
                           ShFileStorageService storage,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShImageTransformPipeline imageTransformPipeline,
                           ShImageTransformGuard imageTransformGuard,
                           ShImageTransformSigner imageTransformSigner) {
        this.shStaticFileService = shStaticFileService;
        this.storage = storage;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.imageTransformPipeline = imageTransformPipeline;
        this.imageTransformGuard = imageTransformGuard;
        this.imageTransformSigner = imageTransformSigner;
    }

    /**
     * List {@code File} posts in the active tenant (Media Library, SH61/SH62).
     * Returns a flat view ({@link ShStaticFileItem}) with on-disk size + content
     * type, newest first.
     *
     * <p>All query params are optional filters (SH62): {@code folderId}/{@code siteId}
     * scope to a container, {@code category} keeps one media category
     * ({@code image}/{@code video}/{@code audio}/{@code document}/{@code archive}/{@code other}),
     * and {@code q} is a case-insensitive file-name substring.</p>
     */
    @GetMapping
    public java.util.List<ShStaticFileItem> list(
            @RequestParam(name = "folderId", required = false) String folderId,
            @RequestParam(name = "siteId", required = false) String siteId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String q) {
        return shStaticFileService.listFiles(folderId, siteId, category, q);
    }

    /**
     * Upload a file into {@code folderId}, creating a {@code File} post for it.
     * Returns the created post (200) so the caller can store its id.
     */
    @PostMapping("/upload")
    public ResponseEntity<ShPostUnified> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam("folderId") String folderId,
                                                @RequestParam(name = "createPost", defaultValue = "true") boolean createPost,
                                                Principal principal) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload is empty");
        }
        String owner = principal != null ? principal.getName() : null;
        ShPostUnified post = shStaticFileService.upload(file, folderId, owner);
        return ResponseEntity.ok(post);
    }

    /**
     * Pre-upload informational check: reports whether a {@code File} post with
     * {@code fileName} already exists in {@code folderId}. Kept for legacy
     * client parity — uploads are never blocked, since each upload is a distinct
     * id-keyed post.
     */
    @GetMapping("/pre-upload/{folderId}/{fileName}")
    public ResponseEntity<ShHttpMessageBean> preUpload(@PathVariable String folderId,
                                                       @PathVariable String fileName) {
        boolean exists = shStaticFileService.fileExists(folderId, fileName);
        ShHttpMessageBean bean = new ShHttpMessageBean();
        bean.setCode(exists ? 1001 : 200);
        bean.setTitle(exists ? "File Exists" : "File doesn't exist");
        bean.setMessage(exists
                ? "A file with this name already exists in the folder."
                : "File doesn't exist");
        return ResponseEntity.ok(bean);
    }

    /**
     * Stream the bytes of a {@code File} post by id. 404 if the post or its
     * stored file is missing.
     *
     * <p>SH39: the optional {@code w}/{@code h}/{@code format}/{@code crop} query
     * parameters are parsed into an {@link ShImageTransformSpec}; when present they
     * are routed to the {@link ShImageTransformPipeline} (SH40 resize/crop, SH41
     * format conversion — the response content type follows the transformed result).
     * The pipeline gracefully declines (serves the original bytes) for a non-image
     * or unsupported target, but a malformed parameter still yields 400 here.</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> serve(@PathVariable String id,
                                          @RequestParam(name = "w", required = false) String w,
                                          @RequestParam(name = "h", required = false) String h,
                                          @RequestParam(name = "format", required = false) String format,
                                          @RequestParam(name = "crop", required = false) String crop,
                                          @RequestParam(name = "sig", required = false) String sig,
                                          @RequestHeader(name = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ShPostUnified post = shPostUnifiedRepository.findById(id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File post not found"));

        String fileName = shStaticFileService.fileNameOf(post);
        if (fileName == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post has no file reference");
        }

        Path path = storage.resolve(id, fileName);
        if (!Files.isRegularFile(path)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stored file not found");
        }

        String contentType = storage.probeContentType(path);

        Optional<ShImageTransformSpec> spec = ShImageTransformSpec.parse(w, h, format, crop);
        if (spec.isPresent()) {
            // SH43: enforce the limits & security policy (signed URL, allowed formats, max
            // dimensions) before spending CPU on the transform.
            imageTransformGuard.check(id, spec.get(), sig);
            Optional<ShImageTransformResult> result =
                    imageTransformPipeline.transform(new FileSystemResource(path), contentType, spec.get());
            if (result.isPresent()) {
                ShImageTransformResult transformed = result.get();
                // SH42: a cached result carries a stable ETag — emit it and honour
                // If-None-Match with a 304 so an unchanged transform is not re-sent.
                String etag = transformed.etag() != null ? "\"" + transformed.etag() + "\"" : null;
                if (etag != null && ifNoneMatchMatches(ifNoneMatch, etag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
                }
                ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(transformed.contentType()))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
                if (etag != null) {
                    builder.eTag(etag);
                }
                return builder.body(new ByteArrayResource(transformed.bytes()));
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(path));
    }

    /**
     * Mint a signed transform URL for a {@code File} post (SH43). When signed delivery is
     * enabled ({@code shio.image-transform.signing.enabled=true}), the public
     * {@code /file_source/**} and {@code /api/v2/staticfile/{id}} transform routes reject
     * any request whose {@code sig} this can't reproduce — so the console / SDK call this
     * (console-authenticated) endpoint to obtain the signature for a given param set.
     *
     * <p>Returns the {@code sig} plus a ready-to-append {@code query} string (the same
     * {@code w/h/format/crop} echoed back with {@code &sig=…}). 409 if no signing secret
     * is configured (nothing can be minted).</p>
     */
    @GetMapping("/{id}/signed-url")
    public ResponseEntity<ShSignedTransformUrl> signedUrl(@PathVariable String id,
                                                          @RequestParam(name = "w", required = false) String w,
                                                          @RequestParam(name = "h", required = false) String h,
                                                          @RequestParam(name = "format", required = false) String format,
                                                          @RequestParam(name = "crop", required = false) String crop) {
        ShImageTransformSpec spec = ShImageTransformSpec.parse(w, h, format, crop)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No transform parameters to sign"));
        String sig;
        try {
            sig = imageTransformSigner.sign(id, spec);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        return ResponseEntity.ok(new ShSignedTransformUrl(sig, buildQuery(spec, sig)));
    }

    /** The query string (with {@code sig}) for a signed transform, in canonical param order. */
    private static String buildQuery(ShImageTransformSpec spec, String sig) {
        StringBuilder query = new StringBuilder();
        if (spec.width() != null) {
            query.append("w=").append(spec.width()).append('&');
        }
        if (spec.height() != null) {
            query.append("h=").append(spec.height()).append('&');
        }
        if (spec.format() != null) {
            query.append("format=").append(spec.format()).append('&');
        }
        if (spec.crop() != null) {
            query.append("crop=").append(spec.crop().name().toLowerCase(java.util.Locale.ROOT)).append('&');
        }
        query.append("sig=").append(sig);
        return query.toString();
    }

    /** Whether the {@code If-None-Match} header (a list, or {@code *}) covers {@code etag}. */
    private static boolean ifNoneMatchMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String trimmed = ifNoneMatch.trim();
        return trimmed.equals("*") || trimmed.contains(etag);
    }

    /** Response of {@code GET /{id}/signed-url}: the HMAC signature and the query to append. */
    public record ShSignedTransformUrl(String sig, String query) {
    }
}
