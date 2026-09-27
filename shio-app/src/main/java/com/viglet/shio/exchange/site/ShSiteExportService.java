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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.memory.ShAgentMemoryService;
import com.viglet.shio.api.agent.memory.dto.ShAgentNote;
import com.viglet.shio.api.post.type.ShPostTypeAdminMapper;
import com.viglet.shio.api.post.type.ShPostTypeAdminService;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.ShExchangePackageCodec;
import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.exchange.model.ShExchangeFile;
import com.viglet.shio.exchange.model.ShExchangeFolder;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.exchange.model.ShExchangePackage;
import com.viglet.shio.exchange.model.ShExchangePost;
import com.viglet.shio.exchange.model.ShExchangeSite;
import com.viglet.shio.object.ShFolderSite;
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
 * <p>Binary assets travel with the site (SH1134): every {@code File} post whose bytes are on disk
 * is listed in {@link ShExchangePackage#files()} and its bytes written to {@code files/<postId>},
 * which is what {@link ShSiteImportService} restores. The tenant package does not carry them: it is
 * provisioned in memory and has no directory to carry them in.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShSiteExportService {

    private static final List<ShPostState> ALL_STATES =
            List.of(ShPostState.DRAFT, ShPostState.PUBLISHED);

    /** The {@code File} post type's own field holding the stored file's name. */
    private static final String FILE_FIELD = "FILE";

    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeAdminMapper postTypeMapper;
    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShExchangePackageCodec codec;
    private final ObjectMapper objectMapper;
    private final ShAgentMemoryService shAgentMemoryService;
    /** SH1134 — where a {@code File} post's bytes are read from. */
    private final ShFileStorageService fileStorage;

    public ShSiteExportService(ShSiteRepository shSiteRepository,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeAdminMapper postTypeMapper,
                               ShPostTypeAdminService shPostTypeAdminService,
                               ShExchangePackageCodec codec,
                               ObjectMapper objectMapper,
                               ShAgentMemoryService shAgentMemoryService,
                               ShFileStorageService fileStorage) {
        this.fileStorage = fileStorage;
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.postTypeMapper = postTypeMapper;
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.codec = codec;
        this.objectMapper = objectMapper;
        this.shAgentMemoryService = shAgentMemoryService;
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
        Map<String, ShExchangeFile> files = new LinkedHashMap<>();
        for (ShFolder folder : folders) {
            for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
                ShExchangePost exPost = toExchangePost(row);
                posts.add(exPost);
                ShPostType postType = row.getShPostType();
                if (postType != null) {
                    postTypes.computeIfAbsent(postType.getName(), n -> postTypeMapper.toDef(postType));
                }
                fileOf(row, exPost.data()).ifPresent(file -> files.putIfAbsent(file.id(), file));
            }
        }

        List<ShExchangeFolder> exFolders = folders.stream().map(this::toExchangeFolder).toList();

        return new ShExchangePackage(ShExchangePackage.CURRENT_VERSION, Instant.now().toString(),
                "shio-export", List.of(toExchangeSite(site, roots)), List.copyOf(postTypes.values()),
                exFolders, posts, List.copyOf(files.values()),
                // SH161: what earlier sessions worked out about this site travels with it.
                // Site-scoped only — see ShAgentMemoryService#scopedNotes.
                shAgentMemoryService.scopedNotes(site.getName()));
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

        List<ShAgentNote> exNotes = new ArrayList<>();

        for (ShSite site : shSiteRepository.findAll()) {
            Set<ShFolder> roots = shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1);
            List<ShFolder> folders = new ArrayList<>();
            roots.forEach(root -> collectFolders(root, folders));

            exSites.add(toExchangeSite(site, roots));
            exNotes.addAll(shAgentMemoryService.scopedNotes(site.getName()));
            folders.forEach(folder -> exFolders.add(toExchangeFolder(folder)));
            for (ShFolder folder : folders) {
                for (ShPostUnified row : shPostUnifiedRepository.findByShFolderAndStateIn(folder, ALL_STATES)) {
                    exPosts.add(toExchangePost(row));
                }
            }
        }

        return new ShExchangePackage(ShExchangePackage.CURRENT_VERSION, Instant.now().toString(),
                "shio-tenant-export", exSites, shPostTypeAdminService.list(),
                exFolders, exPosts, List.of(), exNotes);
    }

    /**
     * SH1134 — the bytes a {@code File} post points at, when this instance has them.
     *
     * <p>Once per post, not per row: a post's DRAFT and PUBLISHED rows share one id, and storage is
     * keyed by that id, so they share one set of bytes. The name is the post type's own
     * {@code FILE} field. A post whose bytes are not on disk is not listed — the import would report
     * it as missing bytes, which is the true state of the source too, and {@code verify}'s
     * {@code asset-missing} already says so here.</p>
     */
    private Optional<ShExchangeFile> fileOf(ShPostUnified row, JsonNode data) {
        if (row.getShPostType() == null
                || !ShSystemPostType.FILE.equals(row.getShPostType().getName())
                || data == null || !data.path(FILE_FIELD).isString()) {
            return Optional.empty();
        }
        String name = data.path(FILE_FIELD).stringValue();
        if (!hasText(name) || !fileStorage.exists(row.getId(), name)) {
            return Optional.empty();
        }
        return Optional.of(new ShExchangeFile(row.getId(), name,
                fileStorage.probeContentType(fileStorage.resolve(row.getId(), name))));
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
                // ShFolderSite-exempt: the export records shSite on the root folder
                // only, mirroring how the schema stores it. Walking to the root here
                // would stamp every child with a site id the importer must then ignore.
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
                parseData(row));
    }

    /**
     * SH349 — the failure names <em>which</em> post, because this is the one export failure a caller
     * can act on and an address is what they act with.
     */
    private JsonNode parseData(ShPostUnified row) {
        String data = row.getData();
        if (data == null || data.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(data);
        } catch (RuntimeException e) {
            throw ShExchangeException.about(ShExchangeException.Kind.POST_DATA_INVALID,
                    addressOf(row), "Failed to parse post data JSON", e);
        }
    }

    /**
     * The post's SH79 address, or {@code id:<uuid>} when it has no friendly URL.
     *
     * <p>The site comes from {@code ShFolderSite.of} and never from {@code folder.getShSite()}: only
     * a root folder carries the site, so the direct read would name {@code post:null/…} for every
     * post that is not at the top of the tree — which is every post this can be thrown for.</p>
     */
    private static String addressOf(ShPostUnified row) {
        if (!hasText(row.getFurl()) || row.getShFolder() == null) {
            return ShAddress.id(row.getId()).toString();
        }
        ShSite site = ShFolderSite.of(row.getShFolder());
        // SH666 — composed, never concatenated. A stored furl has two spellings: `setFurl` and
        // `move` guarantee a leading slash, `create`, `copyPost` and the folder/site writes store
        // it bare, and SH467 keeps that on purpose so a page answers at the form its source
        // declared. `"post:" + name + "/" + furl` assumed the bare one, so every post whose address
        // the agent path declared exported as `post:site//page` — not an SH79 address, and which
        // one you got depended on which surface wrote the row. `ShAddress.post` normalises through
        // `ShFurlSpellings.canonical`, which is the same reader every resolver already uses.
        return site == null || !hasText(site.getName())
                ? ShAddress.id(row.getId()).toString()
                : ShAddress.post(site.getName(), row.getFurl()).toString();
    }

    private byte[] zip(ShExchangePackage pkg) {
        Path dir = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            dir = ShExchangeTempDir.create("shio-export-");
            codec.writePackage(pkg, dir);
            // SH1134 — the bytes each listed file names, under files/<postId>, which is the layout
            // the codec reads back and the import restores from.
            for (ShExchangeFile file : pkg.files()) {
                Path target = codec.resolveFile(dir, file.id());
                Files.createDirectories(target.getParent());
                Files.copy(fileStorage.resolve(file.id(), file.fileName()), target);
            }
            codec.zip(dir, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ShExchangeException(ShExchangeException.Kind.ARCHIVE_UNWRITABLE,
                    "Failed to build export archive", e);
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
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-)|(-$)", "");
        return slug.isBlank() ? "site" : slug;
    }
}
