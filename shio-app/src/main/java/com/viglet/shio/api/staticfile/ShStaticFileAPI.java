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

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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

    public ShStaticFileAPI(ShStaticFileService shStaticFileService,
                           ShFileStorageService storage,
                           ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shStaticFileService = shStaticFileService;
        this.storage = storage;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
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
     */
    @GetMapping("/{id}")
    public ResponseEntity<Resource> serve(@PathVariable String id) {
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
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                .body(new PathResource(path));
    }
}
