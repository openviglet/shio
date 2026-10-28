/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.api.agent.dto.ShAgentResolved;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.blueprint.dto.ShBlueprint;
import com.viglet.shio.blueprint.dto.ShBlueprintApplied;

/**
 * SH1252 — writes a package's assets into the site it is applied to, through the same upload and
 * replace the asset library uses.
 *
 * <p>Each asset is one of three actions, decided the same way on a plan and on an apply:
 * {@code create} when the site has no file of that name in that folder, {@code noop} when it has
 * one with the same SHA-256, {@code replace} when the bytes differ. So a re-apply of an unchanged
 * package writes nothing (P5), and a replace keeps the post's id, so every field already pointing
 * at the asset keeps working (SH1035 keeps the old bytes as a rendition).</p>
 *
 * <p>The folders are not created here: {@link ShBlueprintService} adds them to the desired-state
 * document, so they are planned and created by the engine that creates every other folder, and
 * this only ever writes into a folder that exists.</p>
 */
@Service
public class ShBlueprintAssetWriter {

    static final String CREATE = "create";
    static final String REPLACE = "replace";
    static final String NOOP = "noop";

    private final ShAddressResolver shAddressResolver;
    private final ShStaticFileService shStaticFileService;

    public ShBlueprintAssetWriter(ShAddressResolver shAddressResolver,
                                  ShStaticFileService shStaticFileService) {
        this.shAddressResolver = shAddressResolver;
        this.shStaticFileService = shStaticFileService;
    }

    /**
     * What applying these assets to {@code site} would do. Reads only, inside one read-only
     * transaction: deciding an action reads each candidate's post-type, which is lazy.
     */
    @Transactional(readOnly = true)
    public List<ShBlueprintApplied.AssetRow> plan(List<ShBlueprint.Asset> assets, String site) {
        List<ShBlueprintApplied.AssetRow> rows = new ArrayList<>();
        for (ShBlueprint.Asset asset : assets) {
            rows.add(new ShBlueprintApplied.AssetRow(asset.path(), action(asset, site, folderId(asset, site)),
                    asset.bytes().length));
        }
        return rows;
    }

    /**
     * Writes them, after the content apply has created their folders. One transaction, which the
     * upload and replace calls join, so an asset that is refused rolls back the rows of the ones
     * before it rather than leaving half a package's images.
     */
    @Transactional
    public List<ShBlueprintApplied.AssetRow> apply(List<ShBlueprint.Asset> assets, String site,
                                                   String actor) {
        List<ShBlueprintApplied.AssetRow> rows = new ArrayList<>();
        for (ShBlueprint.Asset asset : assets) {
            Optional<String> folder = folderId(asset, site);
            String folderId = folder.orElseThrow(() -> new IllegalStateException("SH1252: the folder "
                    + asset.folder() + " of site " + site + " should exist after the content apply"));
            Optional<String> existing = shStaticFileService.findFileId(folderId, asset.name());
            String action = action(asset, existing);
            MultipartFile file = new Bytes(asset.name(), asset.bytes());
            String id = existing.orElse(null);
            if (CREATE.equals(action)) {
                id = shStaticFileService.upload(file, folderId, actor).getId();
            } else if (REPLACE.equals(action)) {
                shStaticFileService.replace(file, id, actor);
            }
            // SH1263 — the package's description, on every action including a noop: a package
            // that gained an `alt` since the bytes were uploaded converges on re-apply.
            shStaticFileService.describe(id, asset.alt(), actor);
            rows.add(new ShBlueprintApplied.AssetRow(asset.path(), action, asset.bytes().length));
        }
        return rows;
    }

    /**
     * SH1263 — each asset's {@code File} post id in {@code site}, for the ones that are there.
     *
     * <p>What a package's {@code asset:/images/hero.webp} field values become before its content is
     * applied. An asset the site does not have yet is absent from the map, which on a dry run leaves
     * the token in the plan: the id it will get does not exist until the upload.</p>
     */
    @Transactional(readOnly = true)
    public Map<String, String> ids(List<ShBlueprint.Asset> assets, String site) {
        Map<String, String> ids = new LinkedHashMap<>();
        for (ShBlueprint.Asset asset : assets) {
            folderId(asset, site).flatMap(folder -> shStaticFileService.findFileId(folder, asset.name()))
                    .ifPresent(id -> ids.put(asset.path(), id));
        }
        return ids;
    }

    private String action(ShBlueprint.Asset asset, String site, Optional<String> folderId) {
        return folderId.isEmpty() ? CREATE
                : action(asset, shStaticFileService.findFileId(folderId.get(), asset.name()));
    }

    private String action(ShBlueprint.Asset asset, Optional<String> existing) {
        if (existing.isEmpty()) {
            return CREATE;
        }
        String present = shStaticFileService.digestOf(existing.get()).sha256();
        return asset.sha256().equalsIgnoreCase(present == null ? "" : present) ? NOOP : REPLACE;
    }

    private Optional<String> folderId(ShBlueprint.Asset asset, String site) {
        return shAddressResolver.find(ShAddress.folder(site, asset.folder())).map(ShAgentResolved::id);
    }

    /**
     * Bytes as the upload path takes them. The content type is guessed from the name, which is
     * also what the upload policy reads when the declared type is absent (SH951).
     */
    private record Bytes(String name, byte[] bytes) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return name;
        }

        @Override
        public String getContentType() {
            return URLConnection.guessContentTypeFromName(name);
        }

        @Override
        public boolean isEmpty() {
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File destination) throws java.io.IOException {
            Files.write(destination.toPath(), bytes);
        }

        @Override
        public void transferTo(Path destination) throws java.io.IOException {
            Files.write(destination, bytes);
        }
    }
}
