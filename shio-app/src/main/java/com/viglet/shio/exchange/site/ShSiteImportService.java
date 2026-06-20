/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.site;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import com.viglet.shio.api.post.type.ShPostTypeAdminService;
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
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports a content exchange package (SH68) into the install, <strong>cloning</strong>
 * it — every structural id (site / folder / post) is reassigned a fresh UUID so the
 * import never collides with existing content, even when re-importing a package
 * exported from this same install (SH70). This is the write half of the rebuilt
 * exchange subsystem and the inverse of {@link ShSiteExportService}; it backs
 * {@code POST /api/v2/import} (the console "Import" action).
 *
 * <p>Order of operations (all in one transaction, so an import is atomic):</p>
 * <ol>
 *   <li><strong>Provision the content model.</strong> Each {@code ShPostTypeDef}
 *       in the package is applied via {@link ShPostTypeAdminService#provisionFromDef}
 *       — idempotent, a no-op for a post-type that already exists by name, so a
 *       clone into the same install reuses the live model.</li>
 *   <li><strong>Pre-generate the id remap</strong> for every site, folder and post
 *       (a post's {@code DRAFT}/{@code PUBLISHED} rows share one new id).</li>
 *   <li><strong>Persist sites</strong> (name/furl de-duplicated against existing
 *       rows), then <strong>folders</strong> parent-first (so the {@code parent_folder_id}
 *       FK resolves), then <strong>posts</strong> — whose {@code data} document has
 *       its embedded id references rewritten through the remap (covers Content
 *       Select / File targets pointing at other cloned posts).</li>
 * </ol>
 *
 * <p>Binary assets are not handled yet — the package carries none until the
 * file_source subsystem exists (SH38), matching the export side.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShSiteImportService {

    private final ShExchangePackageCodec codec;
    private final ShPostTypeAdminService shPostTypeAdminService;
    private final ShSiteRepository shSiteRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShPostTypeRepository shPostTypeRepository;
    private final ObjectMapper objectMapper;

    public ShSiteImportService(ShExchangePackageCodec codec,
                               ShPostTypeAdminService shPostTypeAdminService,
                               ShSiteRepository shSiteRepository,
                               ShFolderRepository shFolderRepository,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ObjectMapper objectMapper) {
        this.codec = codec;
        this.shPostTypeAdminService = shPostTypeAdminService;
        this.shSiteRepository = shSiteRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Unzip and clone a package zip into the install, returning the (first) created
     * site. Throws {@link ShExchangeException} on a malformed package or empty site set.
     */
    @Transactional
    public ShSite importPackage(InputStream zipStream, String owner) {
        Path dest = null;
        try {
            dest = Files.createTempDirectory("shio-import-");
            Path root = codec.unzip(zipStream, dest);
            ShExchangePackage pkg = codec.readPackage(root);
            if (pkg.sites().isEmpty()) {
                throw new ShExchangeException("Package contains no site to import");
            }
            return cloneInto(pkg, owner).site();
        } catch (IOException e) {
            throw new ShExchangeException("Failed to read import package", e);
        } finally {
            if (dest != null) {
                FileSystemUtils.deleteRecursively(dest.toFile());
            }
        }
    }

    /**
     * Clone an already-parsed package into the install (no unzip step). Used by the
     * bootstrap-template provisioning (SH71), which reads a bundled classpath package.
     */
    @Transactional
    public ShSite importPackage(ShExchangePackage pkg, String owner) {
        return cloneInto(pkg, owner).site();
    }

    /**
     * Provision the <em>current</em> tenant from a package (SH72) — the SH70 clone
     * path reused as the single provisioning engine. Always tops up the post-type
     * model (idempotent); clones the package's sites/content <strong>only into an
     * empty tenant</strong> (a tenant that already has at least one site is treated
     * as already-seeded, so a re-provision never duplicates its content). Returns
     * the number of post-types created.
     */
    @Transactional
    public int provisionInto(ShExchangePackage pkg, String tenantId) {
        // Already seeded → only top up the post-type model (no duplicate content).
        if (shSiteRepository.count() > 0) {
            return cloneInto(withoutContent(pkg), tenantId).postTypesCreated();
        }
        // Empty tenant → clone everything, but make site names globally unique:
        // ShSite.name carries a GLOBAL unique index (tenant_id lives on the JOINED
        // ShObject root, not on ShSite, so a tenant-scoped composite isn't possible
        // without denormalising). A tenant suffix sidesteps the collision with the
        // source tenant's identically-named site.
        return cloneInto(tenantScopedSiteNames(pkg, tenantId), tenantId).postTypesCreated();
    }

    /** Strip sites/folders/posts, keeping only the post-type model. */
    private static ShExchangePackage withoutContent(ShExchangePackage pkg) {
        return new ShExchangePackage(pkg.version(), pkg.exportedAt(), pkg.generator(),
                List.of(), pkg.postTypes(), List.of(), List.of(), pkg.files());
    }

    /** Append the tenant to each site name so the clone clears the global unique-name index. */
    private static ShExchangePackage tenantScopedSiteNames(ShExchangePackage pkg, String tenantId) {
        List<ShExchangeSite> sites = pkg.sites().stream()
                .map(s -> new ShExchangeSite(s.id(), s.name() + " (" + tenantId + ")",
                        s.description(), s.url(), s.furl(), s.postTypeLayout(),
                        s.searchablePostTypes(), s.formSuccess(), s.owner(), s.rootFolderIds()))
                .toList();
        return new ShExchangePackage(pkg.version(), pkg.exportedAt(), pkg.generator(),
                sites, pkg.postTypes(), pkg.folders(), pkg.posts(), pkg.files());
    }

    private CloneResult cloneInto(ShExchangePackage pkg, String owner) {
        // 1. Ensure the content model exists (idempotent — reuses live post-types).
        int postTypesCreated = 0;
        for (var def : pkg.postTypes()) {
            if (shPostTypeAdminService.provisionFromDef(def)) {
                postTypesCreated++;
            }
        }

        // 2. Fresh id for every structural object (DRAFT+PUBLISHED rows share one).
        Map<String, String> idRemap = new HashMap<>();
        pkg.sites().forEach(s -> idRemap.put(s.id(), newId()));
        pkg.folders().forEach(f -> idRemap.put(f.id(), newId()));
        pkg.posts().forEach(p -> idRemap.putIfAbsent(p.id(), newId()));

        Date now = new Date();

        // 3. Sites.
        Map<String, ShSite> sitesByOldId = new HashMap<>();
        ShSite firstSite = null;
        for (ShExchangeSite exSite : pkg.sites()) {
            ShSite site = new ShSite();
            site.setId(idRemap.get(exSite.id()));
            site.setName(uniqueName(exSite.name()));
            site.setDescription(exSite.description());
            site.setUrl(exSite.url());
            site.setPostTypeLayout(exSite.postTypeLayout());
            site.setSearchablePostTypes(exSite.searchablePostTypes());
            site.setFormSuccess(exSite.formSuccess());
            site.setFurl(uniqueFurl(exSite.furl(), exSite.name()));
            site.setOwner(owner);
            site.setDate(now.toInstant());
            ShSite saved = shSiteRepository.save(site);
            sitesByOldId.put(exSite.id(), saved);
            if (firstSite == null) {
                firstSite = saved;
            }
        }

        // 4. Folders, parent-first.
        Map<String, ShExchangeFolder> exFolders = new LinkedHashMap<>();
        pkg.folders().forEach(f -> exFolders.put(f.id(), f));
        Map<String, ShFolder> foldersByOldId = new HashMap<>();
        for (ShExchangeFolder exf : pkg.folders()) {
            saveFolder(exf.id(), exFolders, foldersByOldId, sitesByOldId, idRemap, owner, now);
        }

        // 5. Posts (data references remapped).
        for (ShExchangePost exPost : pkg.posts()) {
            ShFolder folder = foldersByOldId.get(exPost.folderId());
            if (folder == null) {
                continue; // post in a folder the package didn't carry — skip defensively
            }
            ShPostUnified row = new ShPostUnified();
            row.setId(idRemap.get(exPost.id()));
            row.setState(exPost.state() != null ? ShPostState.valueOf(exPost.state()) : ShPostState.DRAFT);
            row.setShFolder(folder);
            row.setShSite(siteOf(folder));
            row.setShPostType(resolvePostType(exPost.postTypeName()));
            row.setTitle(exPost.title());
            row.setSummary(exPost.summary());
            row.setFurl(exPost.furl());
            row.setOwner(owner);
            row.setPublished(exPost.published());
            row.setPublishStatus(exPost.publishStatus());
            row.setDate(now.toInstant());
            row.setModifiedDate(now);
            if (exPost.published()) {
                row.setPublicationDate(now);
            }
            row.setData(remapData(exPost.data(), idRemap));
            shPostUnifiedRepository.save(row);
        }

        return new CloneResult(firstSite, postTypesCreated);
    }

    /** Outcome of a clone: the first created site (or {@code null} for a model-only package) and the number of post-types created. */
    private record CloneResult(ShSite site, int postTypesCreated) {
    }

    private ShFolder saveFolder(String oldId, Map<String, ShExchangeFolder> exFolders,
                                Map<String, ShFolder> savedByOldId, Map<String, ShSite> sitesByOldId,
                                Map<String, String> idRemap, String owner, Date now) {
        ShFolder already = savedByOldId.get(oldId);
        if (already != null) {
            return already;
        }
        ShExchangeFolder exf = exFolders.get(oldId);
        if (exf == null) {
            return null;
        }
        ShFolder folder = new ShFolder();
        folder.setId(idRemap.get(oldId));
        folder.setName(exf.name());
        folder.setFurl(exf.furl());
        folder.setRootFolder((byte) (exf.rootFolder() ? 1 : 0));
        folder.setPosition(exf.position());
        folder.setOwner(owner);
        folder.setDate(now.toInstant());
        if (exf.rootFolder()) {
            folder.setShSite(exf.siteId() != null ? sitesByOldId.get(exf.siteId()) : null);
        } else if (exf.parentFolderId() != null) {
            folder.setParentFolder(saveFolder(exf.parentFolderId(), exFolders, savedByOldId,
                    sitesByOldId, idRemap, owner, now));
        }
        ShFolder saved = shFolderRepository.save(folder);
        savedByOldId.put(oldId, saved);
        return saved;
    }

    /** Walk up to the root folder to find the owning site (child folders carry no site). */
    private ShSite siteOf(ShFolder folder) {
        ShFolder f = folder;
        while (f.getParentFolder() != null) {
            f = f.getParentFolder();
        }
        return f.getShSite();
    }

    private ShPostType resolvePostType(String name) {
        return name != null ? shPostTypeRepository.findByName(name) : null;
    }

    /**
     * Serialize the post data and rewrite any embedded structural id (post / folder /
     * site) to its clone. UUIDs are unique 36-char tokens, so a plain string
     * replacement is safe and widget-agnostic (handles Content Select / File targets).
     */
    private String remapData(JsonNode data, Map<String, String> idRemap) {
        String json;
        try {
            json = objectMapper.writeValueAsString(data != null ? data : objectMapper.createObjectNode());
        } catch (RuntimeException e) {
            throw new ShExchangeException("Failed to serialize imported post data", e);
        }
        for (Map.Entry<String, String> entry : idRemap.entrySet()) {
            json = json.replace(entry.getKey(), entry.getValue());
        }
        return json;
    }

    private String uniqueName(String base) {
        String name = hasText(base) ? base : "Imported Site";
        return shSiteRepository.findByName(name) == null ? name : name + " (" + shortId() + ")";
    }

    private String uniqueFurl(String furl, String name) {
        String base = hasText(furl) ? furl : slug(name);
        return shSiteRepository.findByFurl(base) == null ? base : base + "-" + shortId();
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
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
