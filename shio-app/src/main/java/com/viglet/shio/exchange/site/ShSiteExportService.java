/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.ShExchangePackageCodec;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.exchange.model.ShExchangeSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Walks a site into a portable {@link ShExchangePackage} (SH68) and packs it as
 * a zip (SH69) — the read-only half of the rebuilt exchange subsystem. Backs
 * {@code GET /api/v2/site/{id}/export} (the console "Download" action).
 *
 * <p>The walk is: site → its root folders → child folders (recursive) → the
 * unified posts in every folder (<strong>both</strong> {@code DRAFT} and
 * {@code PUBLISHED} rows, so a draft-preferred restore survives the round-trip)
 * → the post-types those posts reference (emitted once, as the canonical lossless
 * {@link ShPostTypeDef}). Post content is carried verbatim — the {@code data}
 * CLOB is parsed back to a {@code JsonNode} and embedded as-is.</p>
 *
 * <p>Static binary assets are not exported yet: the file_source upload/serve
 * subsystem isn't implemented in the current backend (SH38), so there is nothing
 * to walk — {@link ShExchangePackage#files()} is empty and the package has no
 * {@code files/} directory. When SH38 lands, this is the one place that grows a
 * file-collection pass.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShSiteExportService {

    private static final List<ShPostState> ALL_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeAdminMapper postTypeMapper;
    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShExchangePackageCodec codec;
    private final ObjectMapper objectMapper;

    public ShSiteExportService(ShSiteRepository shSiteRepository,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeAdminMapper postTypeMapper,
                               ShPostTypeAdminService shPostTypeAdminService,
                               ShExchangePackageCodec codec,
                               ObjectMapper objectMapper) {
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.postTypeMapper = postTypeMapper;
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.codec = codec;
        this.objectMapper = objectMapper;
    }

    /** Build the export archive for a site, or empty if the site does not exist. */
    @Transactional(readOnly = true)
    public Optional<ShExchangeArchive> exportSite(String siteId) {
        return shSiteRepository.findById(siteId).map(site -> {
            ShExchangePackage pkg = buildPackage(site);
            String base = hasText(site.getFurl()) ? site.getFurl() : slug(site.getName());
            return new ShExchangeArchive(base + ".zip", zip(pkg));
        });
    }

    /** Build the (unzipped) package model for a site — exposed for tests / reuse. */
    @Transactional(readOnly = true)
    public ShExchangePackage buildPackage(String siteId) {
        return shSiteRepository.findById(siteId)
                .map(this::buildPackage)
                .orElseThrow(() -> new IllegalArgumentException("Site not found: " + siteId));
    }

    private ShExchangePackage buildPackage(ShSite site) {
        Set<ShFolder> roots = shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1);

        List<ShFolder> folders = new ArrayList<>();
        roots.forEach(root -> collectFolders(root, folders));

        List<ShExchangePost> posts = new ArrayList<>();
        Map<String, ShPostTypeDef> postTypes = new LinkedHashMap<>();
        for (ShFolder folder : folders) {
            for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
                posts.add(toExchangePost(row));
                ShPostType postType = row.getShPostType();
                if (postType != null) {
                    postTypes.computeIfAbsent(postType.getName(), n -> postTypeMapper.toDef(postType));
                }
            }
        }

        List<ShExchangeFolder> exFolders = folders.stream().map(this::toExchangeFolder).toList();

        return new ShExchangePackage(ShExchangePackage.CURRENT_VERSION, Instant.now().toString(),
                "shio-export", List.of(toExchangeSite(site, roots)), List.copyOf(postTypes.values()),
                exFolders, posts, List.of());
    }

    /**
     * Build a package for the <strong>entire current tenant</strong> — every site
     * (with its full folder/post tree) plus <strong>all</strong> post-types (not
     * just the referenced subset, so orphan types are carried too). This is the
     * source side of SH72 tenant provisioning: the default tenant is exported here
     * and {@link ShSiteImportService#provisionInto} clones it into a new tenant.
     */
    @Transactional(readOnly = true)
    public ShExchangePackage buildTenantPackage() {
        List<ShExchangeSite> exSites = new ArrayList<>();
        List<ShExchangeFolder> exFolders = new ArrayList<>();
        List<ShExchangePost> exPosts = new ArrayList<>();

        for (ShSite site : shSiteRepository.findAll()) {
            Set<ShFolder> roots = shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1);
            List<ShFolder> folders = new ArrayList<>();
            roots.forEach(root -> collectFolders(root, folders));

            exSites.add(toExchangeSite(site, roots));
            folders.forEach(folder -> exFolders.add(toExchangeFolder(folder)));
            for (ShFolder folder : folders) {
                for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
                    exPosts.add(toExchangePost(row));
                }
            }
        }

        return new ShExchangePackage(ShExchangePackage.CURRENT_VERSION, Instant.now().toString(),
                "shio-tenant-export", exSites, shPostTypeAdminService.list(),
                exFolders, exPosts, List.of());
    }

    private ShExchangeSite toExchangeSite(ShSite site, Set<ShFolder> roots) {
        List<String> rootFolderIds = roots.stream().map(ShFolder::getId).toList();
        return new ShExchangeSite(site.getId(), site.getName(), site.getDescription(),
                site.getUrl(), site.getFurl(), site.getPostTypeLayout(),
                site.getSearchablePostTypes(), site.getFormSuccess(), site.getOwner(), rootFolderIds);
    }

    private void collectFolders(ShFolder folder, List<ShFolder> acc) {
        acc.add(folder);
        shFolderRepository.findByParentFolder(folder).forEach(child -> collectFolders(child, acc));
    }

    private ShExchangeFolder toExchangeFolder(ShFolder folder) {
        boolean root = folder.getRootFolder() == 1;
        return new ShExchangeFolder(
                folder.getId(),
                folder.getName(),
                folder.getFurl(),
                root,
                root && folder.getShSite() != null ? folder.getShSite().getId() : null,
                folder.getParentFolder() != null ? folder.getParentFolder().getId() : null,
                folder.getPosition(),
                folder.getOwner());
    }

    private ShExchangePost toExchangePost(ShPostUnified row) {
        return new ShExchangePost(
                row.getId(),
                row.getShFolder() != null ? row.getShFolder().getId() : null,
                row.getShPostType() != null ? row.getShPostType().getName() : null,
                row.getState() != null ? row.getState().name() : null,
                row.getTitle(),
                row.getSummary(),
                row.getFurl(),
                row.getOwner(),
                row.isPublished(),
                row.getPublishStatus(),
                0,
                parseData(row.getData()));
    }

    private JsonNode parseData(String data) {
        if (data == null || data.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(data);
        } catch (RuntimeException e) {
            throw new ShExchangeException("Failed to parse post data JSON", e);
        }
    }

    private byte[] zip(ShExchangePackage pkg) {
        Path dir = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            dir = ShExchangeTempDir.create("shio-export-");
            codec.writePackage(pkg, dir);
            codec.zip(dir, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ShExchangeException("Failed to build export archive", e);
        } finally {
            if (dir != null) {
                FileSystemUtils.deleteRecursively(dir.toFile());
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String slug(String value) {
        if (value == null) {
            return "site";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        return slug.isBlank() ? "site" : slug;
    }
}
