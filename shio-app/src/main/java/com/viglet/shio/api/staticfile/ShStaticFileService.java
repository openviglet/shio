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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Upload orchestration for the file_source subsystem (SH38), rebuilt on the
 * unified post model. An upload (a) ensures the system {@code File} post type
 * exists, (b) creates a published {@code File} {@link ShPostUnified} whose
 * {@code data.FILE} holds the original file name, and (c) writes the bytes to
 * disk keyed by that post's id.
 *
 * <p>The {@code File} post is the addressable handle for the binary: the
 * {@code File}/{@code Content Select} widgets store its id, the bytes are served
 * from {@code /file_source/<id>/<name>} (and {@code /api/v2/staticfile/<id>}),
 * and deleting the post removes the bytes (see {@code ShStaticFileCleanupListener}).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
@Slf4j
public class ShStaticFileService {

    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ShWidgetRepository shWidgetRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShFileStorageService storage;
    private final ObjectMapper objectMapper;
    /** Self-reference so upload() bootstraps the File post type through the
     *  transactional proxy instead of a bare {@code this} call (S6809). */
    private final ObjectProvider<ShStaticFileService> self;

    public ShStaticFileService(ShPostUnifiedWriteService shPostUnifiedWriteService,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShWidgetRepository shWidgetRepository,
                               ShFolderRepository shFolderRepository,
                               ShFileStorageService storage,
                               ObjectMapper objectMapper,
                               ObjectProvider<ShStaticFileService> self) {
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.shFolderRepository = shFolderRepository;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.self = self;
    }

    /**
     * Store an uploaded file as a published {@code File} post and write its
     * bytes to disk. Both the post and the bytes commit together: an I/O failure
     * rolls back the post (the method runs in a transaction and rethrows).
     *
     * @param file      the multipart upload
     * @param folderId  the target folder (or site, resolved to its root folder)
     * @param owner     the authenticated user name (may be {@code null})
     * @return the created {@code File} post
     */
    @Transactional
    public ShPostUnified upload(MultipartFile file, String folderId, String owner) {
        String fileName = ShFileStorageService.sanitizeFileName(file.getOriginalFilename());

        ShPostType fileType = self.getObject().resolveOrCreateFilePostType();

        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.FILE, fileName);

        ShPostUnified post = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, fileType.getId(), data, ShPostState.PUBLISHED, owner, null, null));

        try {
            storage.store(post.getId(), fileName, file.getInputStream());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + fileName, e);
        }
        return post;
    }

    /**
     * Whether a {@code File} post named {@code fileName} already exists in the
     * folder (informational, for the pre-upload check). Returns {@code false}
     * for an unknown folder.
     */
    @Transactional(readOnly = true)
    public boolean fileExists(String folderId, String fileName) {
        String name = ShFileStorageService.sanitizeFileName(fileName);
        return shFolderRepository.findById(folderId)
                .map(folder -> shPostUnifiedRepository
                        .findByShFolderAndStateIn(folder, Set.of(ShPostState.DRAFT, ShPostState.PUBLISHED))
                        .stream()
                        .anyMatch(p -> p.getShPostType() != null
                                && ShSystemPostType.FILE.equals(p.getShPostType().getName())
                                && name.equals(p.getTitle())))
                .orElse(false);
    }

    /** The original file name stored on a {@code File} post's {@code data.FILE}. */
    public String fileNameOf(ShPostUnified post) {
        if (post == null || post.getData() == null) {
            return null;
        }
        try {
            var node = objectMapper.readTree(post.getData());
            var value = node != null ? node.get(ShSystemPostTypeAttr.FILE) : null;
            return value != null && !value.isNull() ? value.asString() : null;
        } catch (RuntimeException e) {
            log.debug("Could not read FILE from post {} data", post.getId(), e);
            return null;
        }
    }

    /**
     * Find the system {@code File} post type, creating it (with its single
     * {@code File}-widget {@code FILE} attribute) the first time. A fresh install
     * does not seed system post types, so an upload must be able to bootstrap it.
     */
    @Transactional
    public ShPostType resolveOrCreateFilePostType() {
        ShPostType existing = shPostTypeRepository.findByName(ShSystemPostType.FILE);
        if (existing != null) {
            return existing;
        }

        ShWidget fileWidget = shWidgetRepository.findByName(ShSystemWidget.FILE);
        if (fileWidget == null) {
            throw new IllegalStateException("File widget is not seeded; cannot create File post type");
        }

        ShPostType postType = new ShPostType();
        postType.setName(ShSystemPostType.FILE);
        postType.setNamePlural("Files");
        postType.setTitle("File");
        postType.setDescription("File Post Type");
        postType.setSystem((byte) 1);
        postType.setDate(Instant.now());
        postType.setOwner("admin");

        ShPostTypeAttr fileAttr = new ShPostTypeAttr();
        fileAttr.setName(ShSystemPostTypeAttr.FILE);
        fileAttr.setLabel("File Path");
        fileAttr.setDescription("File Path");
        fileAttr.setOrdinal(1);
        fileAttr.setRequired((byte) 1);
        fileAttr.setIsTitle((byte) 1);
        fileAttr.setIsSummary((byte) 0);
        fileAttr.setShWidget(fileWidget);
        fileAttr.setShPostType(postType);

        Set<ShPostTypeAttr> attrs = new HashSet<>();
        attrs.add(fileAttr);
        postType.setShPostTypeAttrs(attrs);

        return shPostTypeRepository.save(postType);
    }
}
