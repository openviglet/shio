/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.staticfile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentAuditService;
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
    private final ShImageDimensionReader dimensionReader;
    private final ObjectMapper objectMapper;
    /** SH210 — a replaced asset is a content change, so it reaches the feed and the queue. */
    private final ShContentAuditService shContentAuditService;
    /** Self-reference so upload() bootstraps the File post type through the
     *  transactional proxy instead of a bare {@code this} call (S6809). */
    private final ObjectProvider<ShStaticFileService> self;

    /** {@code data} keys for image pixel dimensions stamped at upload (SH62). */
    static final String DATA_WIDTH = "WIDTH";
    static final String DATA_HEIGHT = "HEIGHT";
    /**
     * The bytes' sha-256, stamped at write time (SH398).
     *
     * <p>Same treatment SH62 gave {@code WIDTH}/{@code HEIGHT}, for the same reason and a bigger
     * saving: a fact about bytes that cannot change without a write, re-derived on every read. It
     * cost {@code shio pull --content} one HTTP request per asset already on disk (SH373's
     * comparison), which on a settled tree is the whole library.</p>
     */
    static final String DATA_SHA256 = "SHA256";
    /** {@code data} keys for editable Media Library metadata (SH62). */
    static final String DATA_ALT = "ALT";
    static final String DATA_CAPTION = "CAPTION";
    static final String DATA_TAGS = "TAGS";

    public ShStaticFileService(ShPostUnifiedWriteService shPostUnifiedWriteService,
                               ShPostUnifiedRepository shPostUnifiedRepository,
                               ShPostTypeRepository shPostTypeRepository,
                               ShWidgetRepository shWidgetRepository,
                               ShFolderRepository shFolderRepository,
                               ShFileStorageService storage,
                               ShImageDimensionReader dimensionReader,
                               ObjectMapper objectMapper,
                               ShContentAuditService shContentAuditService,
                               ObjectProvider<ShStaticFileService> self) {
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.shFolderRepository = shFolderRepository;
        this.storage = storage;
        this.dimensionReader = dimensionReader;
        this.objectMapper = objectMapper;
        this.shContentAuditService = shContentAuditService;
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

        // SH62: for images, stamp pixel dimensions on the post so the Media
        // Library shows them without re-reading the bytes on every listing. The
        // upload is buffered once and replayed to both the dimension reader (header
        // only) and disk storage, so the multipart stream is consumed exactly once.
        // SH398 buffers unconditionally where SH62 buffered only images: the digest needs every
        // byte, and reading the stream twice is not an option — a multipart stream is consumable
        // once, which is why SH62 buffered and replayed in the first place.
        byte[] buffered;
        try {
            buffered = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + fileName, e);
        }
        data.put(DATA_SHA256, hex(sha256(buffered)));
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            int[] dims = dimensionReader.read(new ByteArrayInputStream(buffered));
            if (dims != null) {
                data.put(DATA_WIDTH, dims[0]);
                data.put(DATA_HEIGHT, dims[1]);
            }
        }

        ShPostUnified post = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, fileType.getId(), data, ShPostState.PUBLISHED, owner, null, null));

        try (InputStream bytes = new ByteArrayInputStream(buffered)) {
            storage.store(post.getId(), fileName, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + fileName, e);
        }
        return post;
    }

    /**
     * The rows a listing has to read — the folder's, when one is named (SH375).
     *
     * <h2>The measurement that produced this</h2>
     *
     * <p>{@code folderId} used to be applied by {@link #matches} <b>after</b> every published
     * {@code File} post in the tenant had been loaded and {@link #toItem} had stat-ed each one's
     * bytes on disk. So it narrowed the *response* and not the work, which made SH213's per-folder
     * push — F listings instead of one — cost F whole-library reads to return a fraction of the
     * rows. SH375 measured it: a one-folder listing on a 60-file library fetched all 60.</p>
     *
     * <p>The predicate belongs in the query, and that is all this is. Two things it deliberately
     * keeps: the folder is passed as an <b>entity</b>, never as an id in JPQL, because
     * {@code ShFolder} is an {@code ShObject} subclass and navigating one forces a join whose
     * {@code @TenantId} predicate references an unjoined alias (the SH22 hazard, and the reason
     * {@code findByShPostType_IdAndState} is keyed by id rather than by name); and the post-type
     * check stays in the stream, because a folder holds few rows and one predicate here is not
     * worth a second finder.</p>
     *
     * <p>An unknown folder reads nothing rather than everything. A filter that silently widened to
     * the whole tenant is how a caller asking about one folder is answered about a site.</p>
     */
    private List<ShPostUnified> rowsFor(String folderId, ShPostType fileType) {
        if (isBlank(folderId)) {
            return shPostUnifiedRepository
                    .findByShPostType_IdAndState(fileType.getId(), ShPostState.PUBLISHED);
        }
        return shFolderRepository.findById(folderId)
                .map(folder -> shPostUnifiedRepository
                        .findByShFolderAndState(folder, ShPostState.PUBLISHED)
                        .stream()
                        .filter(post -> post.getShPostType() != null
                                && fileType.getId().equals(post.getShPostType().getId()))
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * Replace the bytes of an existing {@code File} post, keeping its identity (SH210).
     *
     * <h2>Why this operation had to exist</h2>
     *
     * <p>An upload always <em>creates</em> an id-keyed post, which is what let SH145 treat an
     * asset path — folder chain plus file name — as immutable and resolve it idempotently. The
     * cost showed up on the most ordinary thing anyone does to an asset: {@code push --content}
     * skipped a path the server already had, so <b>editing an asset's bytes uploaded nothing and
     * reported success</b>. The projection's one silent no-op.</p>
     *
     * <p>The alternative — upload the new bytes as a second post and repoint the field — is
     * cheaper and <b>unsound here</b>: it would put two {@code File} posts at one (folder, file
     * name) pair, which is precisely the pair an asset path <em>is</em>, so the path would no
     * longer name one asset. It also strands the old post, and a {@code File} post may be
     * referenced by content this tree does not own (which is why deleting one is not projected).
     * One post per asset, with replaceable bytes, keeps the path stable, the id stable and the
     * SH62 usage list intact.</p>
     *
     * <h2>The file name is the post's, not the upload's</h2>
     *
     * <p>Whatever name the multipart carries is ignored. The name is half the asset's address, so
     * changing it here would silently move the asset — a rename is a different gesture, the way
     * {@code post.move} is for a post, and there is no reason for a caller replacing bytes to be
     * able to perform one by accident.</p>
     *
     * @param file     the new bytes
     * @param id       the {@code File} post whose bytes are replaced
     * @param modifier who did it (may be {@code null})
     * @return the refreshed item
     * @throws java.util.NoSuchElementException the id is not a published {@code File} post
     */
    @Transactional
    public ShStaticFileItem replace(MultipartFile file, String id, String modifier) {
        ShPostUnified post = shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .filter(p -> p.getShPostType() != null
                        && ShSystemPostType.FILE.equals(p.getShPostType().getName()))
                .orElseThrow(() -> new java.util.NoSuchElementException("File post not found: " + id));

        String fileName = fileNameOf(post);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read replacement for " + fileName, e);
        }

        // Buffered once and replayed, the same as `upload`: the dimension reader needs a header
        // and storage needs the whole thing, and a multipart stream is consumable once.
        ObjectNode data = dataOf(post);
        // SH398 — re-stamped, not left: a digest that outlived the bytes it describes would make
        // every reader believe an asset it just replaced is unchanged, which is the silent no-op
        // SH210 removed, restored by its own cache.
        data.put(DATA_SHA256, hex(sha256(bytes)));
        int[] dims = dimensionReader.read(new ByteArrayInputStream(bytes));
        if (dims != null) {
            data.put(DATA_WIDTH, dims[0]);
            data.put(DATA_HEIGHT, dims[1]);
        } else {
            // Stale dimensions are worse than none: the Media Library renders them, and an
            // image replaced by a document would keep the old picture's shape for ever.
            data.remove(DATA_WIDTH);
            data.remove(DATA_HEIGHT);
        }
        post.setData(objectMapper.writeValueAsString(data));
        post.setModifier(modifier);
        post.setModifiedDate(Instant.now());

        storage.store(post.getId(), fileName, new ByteArrayInputStream(bytes));
        ShPostUnified saved = shPostUnifiedRepository.save(post);
        // The change feed and the SH109 queue both read the audit trail, and a replaced asset is
        // a content change like any other — a reviewer told nothing would see the field unchanged
        // (it is: the path did not move) and no reason for the page to look different.
        shContentAuditService.record(ShContentAction.UPDATE, saved.getId(),
                saved.getShSite() != null ? saved.getShSite().getId() : null,
                fileName, modifier);
        return toItem(saved);
    }

    /**
     * What an asset's stored bytes hash to, so a caller can tell "unchanged" from "changed"
     * without transferring them (SH210).
     *
     * <h2>Why the hash is here and not in the listing</h2>
     *
     * <p>{@code listFiles} stats every file in the tenant on every call; hashing there would read
     * every byte of every asset to answer a question about the handful a push declares. One id at
     * a time is proportional to what the caller is actually reconciling, and the response is 64
     * characters instead of a file.</p>
     *
     * <p>Computed on demand rather than stamped at upload time, which is the obvious optimisation
     * and a different task: stamping helps only assets written after it ships, so the on-demand
     * path has to exist anyway for everything already in an instance, and one mechanism that
     * always works beats two that disagree about which rows are covered.</p>
     *
     * @return the digest, or a {@code sha256} of {@code null} when the post exists and its bytes
     *         do not — the caller must be able to tell that from a mismatch, because the remedy is
     *         the same (send the bytes) and the diagnosis is not
     * @throws java.util.NoSuchElementException the id is not a published {@code File} post
     */
    @Transactional(readOnly = true)
    public ShStaticFileDigest digestOf(String id) {
        ShPostUnified post = shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .filter(p -> p.getShPostType() != null
                        && ShSystemPostType.FILE.equals(p.getShPostType().getName()))
                .orElseThrow(() -> new java.util.NoSuchElementException("File post not found: " + id));

        String fileName = fileNameOf(post);
        Path path = storage.resolve(post.getId(), fileName);
        // SH398 — the stamp answers when there is one, and the read below is what covers every row
        // written before it existed. The existence check stays either way: a stamp cannot know that
        // somebody removed the bytes underneath the CMS, and reporting it there would turn SH62's
        // `size: -1` into a confident lie.
        Attributes attributes = attributesOf(path);
        if (attributes == null) {
            return new ShStaticFileDigest(post.getId(), fileName, -1L, null);
        }
        String stamped = stringFromData(post, DATA_SHA256);
        if (stamped != null && !stamped.isBlank()) {
            return new ShStaticFileDigest(post.getId(), fileName, attributes.size(), stamped);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long size = 0;
            try (InputStream in = Files.newInputStream(path)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                    size += read;
                }
            }
            return new ShStaticFileDigest(post.getId(), fileName, size, hex(digest.digest()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + fileName + " for " + id, e);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JRE, so this branch is unreachable rather than
            // unhandled — and rethrowing keeps it from being a `catch` that records nothing.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    /** What one stat says: the file is there, and how big it is. `null` when it is not. */
    private record Attributes(long size) {
    }

    private static Attributes attributesOf(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.isRegularFile() ? new Attributes(attributes.size()) : null;
        } catch (IOException e) {
            // Absent, or unreadable — the caller's answer is the same either way, and it is the
            // one SH62 defined: `size: -1` means the post is there and its bytes are not.
            return null;
        }
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            // Required of every JRE, so this branch is unreachable rather than unhandled.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /**
     * What an asset's stored bytes are (SH210).
     *
     * @param id       the {@code File} post
     * @param fileName its name, so a caller can check it is reconciling what it thinks
     * @param size     bytes on disk, or {@code -1} when they are missing (SH62's convention)
     * @param sha256   lowercase hex, or {@code null} when there are no bytes to hash
     */
    public record ShStaticFileDigest(String id, String fileName, long size, String sha256) {
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

    /**
     * List every published {@code File} post in the active tenant (no filter).
     *
     * @see #listFiles(String, String, String, String)
     */
    // @Transactional-overload-exempt: the delegation goes through the injected self-reference, not
    // through `this`, so it does cross the proxy and the readOnly transaction below is the one this
    // read actually runs in. SH357's lint cannot see that from the text, and the pair it exists to
    // catch is the one where the annotation is silently skipped — see ShAgentIncidentStore.
    public List<ShStaticFileItem> listFiles() {
        return self.getObject().listFiles(null, null, null, null);
    }

    /**
     * List published {@code File} posts in the active tenant as a flat
     * {@link ShStaticFileItem} view (newest first) for the Media Library (SH61/SH62).
     * Reads each file's on-disk size + content type; a file whose bytes are missing
     * is reported with {@code size = -1}.
     *
     * <p>All filters are optional (SH62): {@code folderId}/{@code siteId} scope to a
     * container, {@code category} keeps only that media category (see
     * {@link #categoryOf(String)} — {@code image}/{@code video}/{@code audio}/
     * {@code document}/{@code archive}/{@code other}), and {@code q} is a
     * case-insensitive file-name substring match. A blank/null filter is ignored.</p>
     */
    @Transactional(readOnly = true)
    public List<ShStaticFileItem> listFiles(String folderId, String siteId, String category, String q) {
        ShPostType fileType = shPostTypeRepository.findByName(ShSystemPostType.FILE);
        if (fileType == null) {
            return List.of(); // no File post type yet => nothing uploaded
        }
        String wantCategory = isBlank(category) ? null : category.toLowerCase(Locale.ROOT);
        String wantQuery = isBlank(q) ? null : q.toLowerCase(Locale.ROOT);
        return rowsFor(folderId, fileType)
                .stream()
                .map(this::toItem)
                .filter(item -> matches(item, folderId, siteId, wantCategory, wantQuery))
                .sorted(Comparator.comparing(ShStaticFileItem::date,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static boolean matches(ShStaticFileItem item, String folderId, String siteId,
                                   String category, String query) {
        if (!isBlank(folderId) && !folderId.equals(item.folderId())) {
            return false;
        }
        if (!isBlank(siteId) && !siteId.equals(item.siteId())) {
            return false;
        }
        if (category != null && !category.equals(categoryOf(item.contentType()))) {
            return false;
        }
        if (query != null) {
            String name = item.fileName();
            return name != null && name.toLowerCase(Locale.ROOT).contains(query);
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Classify a content type into a Media Library category (SH62) — the Java
     * mirror of the React {@code media-type.ts} classifier, kept in sync so the
     * {@code ?category=} server filter agrees with the client's filter chips.
     */
    static String categoryOf(String contentType) {
        if (contentType == null) {
            return "other";
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.startsWith("image/")) {
            return "image";
        }
        if (ct.startsWith("video/")) {
            return "video";
        }
        if (ct.startsWith("audio/")) {
            return "audio";
        }
        if (ct.contains("zip") || ct.contains("compressed") || ct.contains("tar")
                || ct.equals("application/gzip") || ct.contains("rar")) {
            return "archive";
        }
        if (ct.startsWith("text/") || ct.equals("application/pdf")
                || ct.contains("officedocument") || ct.contains("opendocument")
                || ct.contains("msword") || ct.contains("ms-excel") || ct.contains("ms-powerpoint")
                || ct.equals("application/rtf") || ct.equals("application/json")
                || ct.equals("application/xml")) {
            return "document";
        }
        return "other";
    }

    private ShStaticFileItem toItem(ShPostUnified post) {
        String fileName = fileNameOf(post);
        long size = -1;
        String contentType = null;
        if (fileName != null) {
            Path path = storage.resolve(post.getId(), fileName);
            // SH398 — one `readAttributes` where this was `isRegularFile` then `size`, which is two
            // syscalls asking the filesystem the same question twice. Stamping the size was the
            // obvious alternative and is worth less: the existence check has to happen anyway to
            // keep `size: -1` honest, so a stamp would remove one stat of two and buy a `data` key
            // that can disagree with the disk. One call removes the same stat and cannot go stale.
            Attributes attributes = attributesOf(path);
            if (attributes != null) {
                contentType = storage.probeContentType(path);
                size = attributes.size();
            }
        }
        Integer width = intFromData(post, DATA_WIDTH);
        Integer height = intFromData(post, DATA_HEIGHT);
        return new ShStaticFileItem(
                post.getId(), fileName, contentType, size, post.getOwner(), post.getDate(),
                post.getShFolder() != null ? post.getShFolder().getId() : null,
                post.getShFolder() != null ? post.getShFolder().getName() : null,
                post.getShSite() != null ? post.getShSite().getId() : null,
                post.getShSite() != null ? post.getShSite().getName() : null,
                width, height,
                stringFromData(post, DATA_ALT), stringFromData(post, DATA_CAPTION),
                tagsFromData(post), stringFromData(post, DATA_SHA256));
    }

    /**
     * Update the editable Media Library metadata (SH62) of a {@code File} post —
     * {@code alt}/{@code caption}/{@code tags} merged into its {@code data},
     * leaving {@code FILE}/{@code WIDTH}/{@code HEIGHT} intact. A blank/null value
     * clears the key. 404 if the id is not a published {@code File} post.
     */
    @Transactional
    public ShStaticFileItem updateMeta(String id, String alt, String caption, List<String> tags) {
        ShPostUnified post = shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .filter(p -> p.getShPostType() != null
                        && ShSystemPostType.FILE.equals(p.getShPostType().getName()))
                .orElseThrow(() -> new java.util.NoSuchElementException("File post not found: " + id));

        ObjectNode data = dataOf(post);

        putOrRemove(data, DATA_ALT, alt);
        putOrRemove(data, DATA_CAPTION, caption);
        if (tags == null || tags.isEmpty()) {
            data.remove(DATA_TAGS);
        } else {
            var arr = data.putArray(DATA_TAGS);
            tags.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(arr::add);
        }

        post.setData(objectMapper.writeValueAsString(data));
        post.setModifiedDate(Instant.now());
        return toItem(shPostUnifiedRepository.save(post));
    }

    /**
     * Posts that reference this file (SH62 "used in"). Scans PUBLISHED then DRAFT
     * referrers (a referenced id appears as a plain string in a post's {@code data}),
     * deduped by post id (PUBLISHED preferred), so an editor can see where a file is
     * used before deleting it.
     */
    @Transactional(readOnly = true)
    public List<ShStaticFileUsage> listUsage(String id) {
        java.util.LinkedHashMap<String, ShStaticFileUsage> byId = new java.util.LinkedHashMap<>();
        for (ShPostState state : List.of(ShPostState.PUBLISHED, ShPostState.DRAFT)) {
            for (ShPostUnified ref : shPostUnifiedRepository.findReferencingFileId(id, state)) {
                byId.putIfAbsent(ref.getId(), new ShStaticFileUsage(
                        ref.getId(), ref.getTitle(),
                        ref.getShPostType() != null ? ref.getShPostType().getName() : null,
                        ref.getShFolder() != null ? ref.getShFolder().getName() : null,
                        ref.getShSite() != null ? ref.getShSite().getName() : null,
                        state == ShPostState.PUBLISHED));
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * A {@code File} post's {@code data} as a mutable object, or an empty one.
     *
     * <p>The guard is the agents.md invariant in Java: the column can hold the literal string
     * {@code "null"}, and a parse of that is a {@code NullNode} rather than an {@code ObjectNode}
     * — so every caller that means "the fields" has to say which, or a replaced asset silently
     * loses the {@code FILE} key that names its own bytes.</p>
     */
    private ObjectNode dataOf(ShPostUnified post) {
        var existing = post.getData() != null ? objectMapper.readTree(post.getData()) : null;
        return existing instanceof ObjectNode node ? node : objectMapper.createObjectNode();
    }

    private static void putOrRemove(ObjectNode data, String key, String value) {
        if (value == null || value.isBlank()) {
            data.remove(key);
        } else {
            data.put(key, value.trim());
        }
    }

    /** Read an integer {@code data} key (e.g. SH62 {@code WIDTH}/{@code HEIGHT}); null if absent/non-numeric. */
    private Integer intFromData(ShPostUnified post, String key) {
        if (post == null || post.getData() == null) {
            return null;
        }
        try {
            var node = objectMapper.readTree(post.getData());
            var value = node != null ? node.get(key) : null;
            return value != null && value.isNumber() ? value.asInt() : null;
        } catch (RuntimeException e) {
            log.debug("Could not read {} from post {} data", key, post.getId(), e);
            return null;
        }
    }

    /** Read a string {@code data} key (e.g. SH62 {@code ALT}/{@code CAPTION}); null if absent/blank. */
    private String stringFromData(ShPostUnified post, String key) {
        if (post == null || post.getData() == null) {
            return null;
        }
        try {
            var node = objectMapper.readTree(post.getData());
            var value = node != null ? node.get(key) : null;
            if (value == null || value.isNull()) {
                return null;
            }
            String text = value.asString();
            return text != null && !text.isBlank() ? text : null;
        } catch (RuntimeException e) {
            log.debug("Could not read {} from post {} data", key, post.getId(), e);
            return null;
        }
    }

    /** Read the SH62 {@code TAGS} string array off a {@code File} post's data; empty list if absent. */
    private List<String> tagsFromData(ShPostUnified post) {
        if (post == null || post.getData() == null) {
            return List.of();
        }
        try {
            var node = objectMapper.readTree(post.getData());
            var value = node != null ? node.get(DATA_TAGS) : null;
            if (value == null || !value.isArray()) {
                return List.of();
            }
            List<String> tags = new java.util.ArrayList<>();
            value.forEach(t -> { if (t != null && !t.isNull()) tags.add(t.asString()); });
            return tags;
        } catch (RuntimeException e) {
            log.debug("Could not read TAGS from post {} data", post.getId(), e);
            return List.of();
        }
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
