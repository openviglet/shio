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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.shio.api.history.ShChangedFields;
import com.viglet.shio.api.history.ShContentAction;
import com.viglet.shio.api.history.ShContentAuditService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService.CreateRequest;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.persistence.model.reference.ShObjectReference;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;
import com.viglet.shio.tenant.ShTenantQuotaService;
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
    /** SH1034 — the reverse index, maintained beside this class's own writes. */
    private final ShObjectReferenceService shObjectReferenceService;
    /** SH1035 — the bytes a replace supersedes, so the replace can be undone. */
    private final ShAssetRenditionStore shAssetRenditionStore;


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
    private final ShTenantQuotaService shTenantQuotaService;
    /** SH951 — what this instance accepts, and what it cleans before accepting. */
    private final ShUploadPolicy shUploadPolicy;
    private final ShSvgSanitizer shSvgSanitizer;
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
    /**
     * The size the bytes had when {@link #DATA_SHA256} was taken (SH399) — the stamp's validator.
     *
     * <p>Not here to save a stat: SH398 weighed that and the existence check has to happen anyway,
     * so a stamped size would remove one syscall of two and buy a key that can disagree with the
     * disk. It is here because the digest <em>is</em> a cache now, and its only invalidation is a
     * `replace` — so bytes changed under the CMS (a restored backup, a synced volume) would leave
     * every reader confidently holding a hash that is not theirs. The listing already knows the
     * file's real size, so a stamped size that disagrees is proof the stamp is stale.</p>
     */
    static final String DATA_SIZE = "SIZE";
    /** {@code data} keys for editable Media Library metadata (SH62). */
    /**
     * The content type the bytes had when they were written (SH1030).
     *
     * <p>Stamped for the same reason the digest is, and used for a different one: the media
     * library's category filter is a function of this string, and computing it per row meant
     * resolving the type from the file name — or from the disk — for every asset in the tenant
     * before a single one could be excluded. A stamped value is a column a query can read.</p>
     *
     * <p><b>The probe stays as the fallback</b>, and not only for old rows: it is what keeps
     * {@code contentType} honest on an asset whose bytes were replaced underneath the CMS. The
     * stamp answers the <em>category</em>, which is a filter; the probe answers what the file
     * <em>is</em>, which is what a download sends.</p>
     */
    static final String DATA_CONTENT_TYPE = "CONTENT_TYPE";

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
                               ShTenantQuotaService shTenantQuotaService,
                               ShUploadPolicy shUploadPolicy,
                               ShSvgSanitizer shSvgSanitizer,
                               ObjectProvider<ShStaticFileService> self,
                               ShObjectReferenceService shObjectReferenceService,
                               ShAssetRenditionStore shAssetRenditionStore) {
        this.shAssetRenditionStore = shAssetRenditionStore;
        this.shObjectReferenceService = shObjectReferenceService;
        this.shUploadPolicy = shUploadPolicy;
        this.shSvgSanitizer = shSvgSanitizer;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.shFolderRepository = shFolderRepository;
        this.storage = storage;
        this.dimensionReader = dimensionReader;
        this.objectMapper = objectMapper;
        this.shContentAuditService = shContentAuditService;
        this.shTenantQuotaService = shTenantQuotaService;
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

        // SH951 — the policy decides before anything is stored, and before the quota is spent:
        // refusing costs nothing, and a 400 raised after a row exists leaves a File post with no
        // bytes. The verdict is taken on how the file will be SERVED (its name, which is what
        // ShAssetResponseHeaders reads) as well as on what the client declared, and the stricter of
        // the two wins — otherwise `evil.svg` sent as image/png dodges the policy and is then
        // served as an SVG anyway.
        byte[] sanitized = applyUploadPolicy(file, fileName);

        // SH110 — before the post row and before the bytes. A 402 raised after either would leave
        // the tenant holding something they were just told they could not have, and the row is the
        // half that survives a rolled-back write on disk.
        shTenantQuotaService.checkCanStore(sanitized != null ? sanitized.length : file.getSize());

        ShPostType fileType = self.getObject().resolveOrCreateFilePostType();

        ObjectNode data = objectMapper.createObjectNode();
        data.put(ShSystemPostTypeAttr.FILE, fileName);
        if (sanitized != null) {
            data.put(ShSystemPostTypeAttr.SANITIZED, true);
        }

        // The post first, then the bytes, then what the bytes say about themselves. The order is
        // forced: storage is keyed by the post's id, so the id has to exist before anything can be
        // written, and the digest cannot be known before that.
        ShPostUnified post = shPostUnifiedWriteService.create(new CreateRequest(
                folderId, fileType.getId(), data, ShPostState.PUBLISHED, owner, null, null));

        // SH951 — the sanitized bytes when the policy cleaned them, the stream otherwise. Only the
        // sanitize path buffers, and only an SVG takes it: streaming is what keeps a video upload
        // from becoming an allocation the size of the video (SH398's reason, one step earlier).
        try (InputStream bytes = sanitized != null
                ? new ByteArrayInputStream(sanitized)
                : file.getInputStream()) {
            storage.store(post.getId(), fileName, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + fileName, e);
        }
        // SH398 — read back from disk rather than held in heap. Buffering the whole upload to hash
        // it works and quietly turns a streamed video upload into an allocation the size of the
        // video; SH62 buffered *images only* for exactly that reason, and the digest must not be
        // what widens it. One streaming read of a file that was just written costs a page-cache
        // hit, and the dimension reader takes the same route because it only wants a header.
        stampBytes(post, fileName);
        return post;
    }

    /**
     * Run the upload policy over these bytes (SH951).
     *
     * <p>Refuses outright where the policy denies the type. Where it says sanitize, returns the
     * cleaned bytes; where the bytes were already clean, returns them anyway so the caller stores
     * what was inspected rather than re-reading a stream that has been consumed.</p>
     *
     * @return the bytes to store, or {@code null} to stream the upload through untouched
     */
    private byte[] applyUploadPolicy(MultipartFile file, String fileName) {
        // How it will be served, which is by name (ShAssetResponseHeaders), and what the client
        // said it was. A deny on either refuses: the two disagree exactly when somebody is trying
        // to get an SVG past a policy by calling it a PNG.
        String byName = ShAssetResponseHeaders.contentTypeByName(fileName);
        String declared = file.getContentType();

        boolean sanitize = false;
        if (byName != null) {
            sanitize = shUploadPolicy.check(byName, fileName);
        }
        if (declared != null && !ShUploadPolicy.bare(declared).equals(ShUploadPolicy.bare(byName))) {
            sanitize |= shUploadPolicy.check(declared, fileName);
        }
        if (byName == null && declared == null) {
            shUploadPolicy.check(ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE, fileName);
        }
        if (!sanitize) {
            return null;
        }

        byte[] raw;
        try {
            raw = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload " + fileName, e);
        }
        ShSvgSanitizer.Result result = shSvgSanitizer.sanitize(raw);
        if (result.changed()) {
            log.info("SH951: sanitized {} on upload — removed {}", fileName, result.removed());
        }
        return result.bytes();
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
        return rowsFor(folderId, fileType, null, null);
    }

    /**
     * The rows a listing has to look at, narrowed by what the database can decide (SH1030).
     *
     * <h2>A superset, on purpose</h2>
     *
     * <p>Both narrowings are approximate and the exact decision stays in {@link #matches}. That is
     * what makes this safe rather than a second definition of "matches": the predicate can only
     * ever return <b>more</b> rows than the answer, so a mistake in it costs a row read and
     * discarded — never a row missing from what a curator sees.</p>
     *
     * <p>The term is matched against the whole {@code data} document while the filter matches file
     * names, so an asset whose alt text contains the word comes back and is dropped here. The
     * category is matched against the stamped content type <em>or no stamp at all</em>, because
     * SH1030 stamps at upload and leaves the probe as the fallback for everything written before
     * it — a predicate that dropped unstamped rows would make a pre-existing library vanish from
     * every category, silently, with every number on screen agreeing.</p>
     *
     * <p>So the win is real and it is a <b>library that has been uploaded to since this shipped</b>:
     * there, a category listing reads that category. On a legacy one it reads everything and
     * answers correctly, which is the right way round for a filter to degrade.</p>
     */
    private List<ShPostUnified> rowsFor(String folderId, ShPostType fileType, String q,
                                        String category) {
        String contentTypePrefix = contentTypePrefixFor(category);
        ShFolder folder = isBlank(folderId) ? null
                : shFolderRepository.findById(folderId).orElse(null);
        if (!isBlank(folderId) && folder == null) {
            return List.of();
        }
        return shPostUnifiedRepository.findAssets(fileType.getId(), ShPostState.PUBLISHED, folder,
                q, contentTypePrefix);
    }

    /**
     * The content-type prefix a category is a filter on, or {@code null} when it is not one.
     *
     * <p>Only the four whose types share a prefix. {@code document}, {@code archive} and
     * {@code other} are lists and exclusions — {@link #categoryOf} spells them out — and a
     * predicate for those would be the classifier written twice, in two languages, agreeing until
     * somebody edits one. Those categories read every row and filter here, which is what they did
     * before.</p>
     */
    private static String contentTypePrefixFor(String category) {
        if (isBlank(category)) {
            return null;
        }
        return switch (category.toLowerCase(Locale.ROOT)) {
            case "image" -> "image/";
            case "video" -> "video/";
            case "audio" -> "audio/";
            default -> null;
        };
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
        ShPostUnified post = requireFilePost(id);

        String fileName = fileNameOf(post);
        // SH951 — `replace` is an upload path, so the policy applies here too. The name is the
        // existing post's, which is the point: new bytes cannot change what this asset is served
        // as, so a script smuggled in through a replace would be served under the old name.
        byte[] sanitized = applyUploadPolicy(file, fileName);
        // SH110 — the DIFFERENCE, not the new size: replacing a 900 MB video with a 1 MB one frees
        // 899 MB, and charging the plan for the new size would refuse a write that makes the tenant
        // smaller. A negative delta is a no-op in the shared check.
        long incoming = sanitized != null ? sanitized.length : file.getSize();
        shTenantQuotaService.checkCanStore(incoming - storage.storedBytes(post.getId(), fileName));
        // SH1035 — the bytes about to be overwritten, kept aside BEFORE the write. This is what
        // makes replace safe enough to encourage: the console's old route was delete-and-re-upload,
        // which breaks every field pointing at the asset, and a curator who cannot undo a replace
        // goes back to that. A capture that fails is logged and does not fail the replace — the
        // caller asked for new bytes, and refusing them because the net could not be written would
        // make this less reliable than what it replaces.
        shAssetRenditionStore.capture(post.getId(), fileName);
        try (InputStream bytes = sanitized != null
                ? new ByteArrayInputStream(sanitized)
                : file.getInputStream()) {
            storage.store(post.getId(), fileName, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read replacement for " + fileName, e);
        }
        if (sanitized != null) {
            markSanitized(post);
        }
        post.setModifier(modifier);
        post.setModifiedDate(Instant.now());
        // SH398 — re-stamped from the bytes now on disk, never left: a digest that outlived the
        // bytes it describes would make every reader believe an asset it just replaced is
        // unchanged, which is the silent no-op SH210 removed, restored by its own cache.
        stampBytes(post, fileName);
        ShPostUnified saved = shPostUnifiedRepository.save(post);
        shObjectReferenceService.reindex(saved);
        // The change feed and the SH109 queue both read the audit trail, and a replaced asset is
        // a content change like any other — a reviewer told nothing would see the field unchanged
        // (it is: the path did not move) and no reason for the page to look different.
        shContentAuditService.record(ShContentAction.UPDATE, saved.getId(),
                saved.getShSite() != null ? saved.getShSite().getId() : null,
                fileName, modifier);
        return toItem(saved);
    }

    /**
     * The published {@code File} post behind an id, or the absence as an exception.
     *
     * <p>Extracted by SH1035 because three verbs now need it — replace, the rendition listing and
     * the revert — and a fourth spelling of "is this actually an asset" is how one of them comes to
     * accept something the others refuse.</p>
     */
    private ShPostUnified requireFilePost(String id) {
        return shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .filter(p -> p.getShPostType() != null
                        && ShSystemPostType.FILE.equals(p.getShPostType().getName()))
                .orElseThrow(() -> new java.util.NoSuchElementException("File post not found: " + id));
    }

    /**
     * The bytes this asset used to have (SH1035), newest first.
     *
     * <p>A read, so it is safe to call from the detail dialog every time it opens; it lists a
     * directory rather than hashing anything.</p>
     */
    @Transactional(readOnly = true)
    public List<ShAssetRendition> listRenditions(String id) {
        ShPostUnified post = requireFilePost(id);
        return shAssetRenditionStore.list(post.getId());
    }

    /**
     * Puts a superseded set of bytes back, and re-stamps everything derived from them (SH1035).
     *
     * <p>Everything {@code replace} does on the way out, this does on the way back: the digest and
     * the stamped size are re-taken (a stamp that outlived its bytes is SH398's silent no-op), the
     * dimensions are re-read, and the change reaches the audit trail as an {@code UPDATE} — a
     * revert is a content change, and a reviewer told nothing would see a page that looks different
     * for no recorded reason.</p>
     *
     * <p>The name is never taken from the rendition. A replace cannot change what an asset is
     * served as, so the live name is the asset's own and restoring an old one would move the
     * public URL — breaking exactly the references this whole feature exists to keep.</p>
     */
    @Transactional
    public ShStaticFileItem revertToRendition(String id, int number, String modifier) {
        ShPostUnified post = requireFilePost(id);
        String fileName = fileNameOf(post);
        if (shAssetRenditionStore.revert(post.getId(), fileName, number).isEmpty()) {
            throw new IllegalArgumentException(
                    "No rendition " + number + " for asset " + id + ". "
                            + "GET /api/v2/staticfile/" + id + "/renditions lists what is kept; "
                            + "this instance keeps " + shAssetRenditionStore.keep() + " per asset.");
        }
        post.setModifier(modifier);
        post.setModifiedDate(Instant.now());
        stampBytes(post, fileName);
        ShPostUnified saved = shPostUnifiedRepository.save(post);
        shObjectReferenceService.reindex(saved);
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
        String stamped = trustedStamp(dataOnce(post), attributes);
        if (stamped != null) {
            return new ShStaticFileDigest(post.getId(), fileName, attributes.size(), stamped);
        }
        try (InputStream in = Files.newInputStream(path)) {
            return new ShStaticFileDigest(post.getId(), fileName, attributes.size(),
                    hex(sha256Of(in)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + fileName + " for " + id, e);
        }
    }

    /**
     * Record on the post that its stored bytes are not the bytes that arrived (SH951).
     *
     * <p>Set, never cleared: a replace that needed no cleaning does not un-say that an earlier one
     * did, because the flag is about what is on disk and a replace writes over it. The one case
     * that would clear it is bytes replaced by something the policy allows outright, and that is
     * exactly when the caller does not reach here.</p>
     */
    private void markSanitized(ShPostUnified post) {
        ObjectNode data = dataOf(post);
        data.put(ShSystemPostTypeAttr.SANITIZED, true);
        post.setData(objectMapper.writeValueAsString(data));
    }

    /**
     * Reads what a post's stored bytes say about themselves and puts it on the post (SH398).
     *
     * <p>The digest, and the pixel dimensions SH62 wanted. Both come from the file that was just
     * written rather than from a copy in memory, which is what keeps an upload's memory profile the
     * size of a buffer instead of the size of the upload — SH62 buffered <em>images only</em> for
     * exactly that reason, and a digest must not be what widens it to everything. One streaming
     * read of a file written moments ago costs a page-cache hit.</p>
     *
     * <p>Dimensions are cleared when the bytes are not an image: stale ones are worse than none,
     * because the Media Library renders them and an image replaced by a document would keep the
     * old picture's shape for ever.</p>
     */
    private void stampBytes(ShPostUnified post, String fileName) {
        Path path = storage.resolve(post.getId(), fileName);
        ObjectNode data = dataOf(post);
        try (InputStream bytes = Files.newInputStream(path)) {
            data.put(DATA_SHA256, hex(sha256Of(bytes)));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read back " + fileName, e);
        }
        // SH1030 — the content type, so the category filter is a value the listing already has
        // rather than a probe per row. Taken from the same file, at the same moment.
        String probed = storage.probeContentType(path);
        if (probed != null && !probed.isBlank()) {
            data.put(DATA_CONTENT_TYPE, probed);
        } else {
            data.remove(DATA_CONTENT_TYPE);
        }
        // SH399 — the size the hash was taken over, so a later read can tell whether the hash is
        // still describing these bytes. Written after the digest and from the same file.
        Attributes stamped = attributesOf(path);
        if (stamped != null) {
            data.put(DATA_SIZE, stamped.size());
        } else {
            data.remove(DATA_SIZE);
        }
        int[] dims;
        try (InputStream bytes = Files.newInputStream(path)) {
            dims = dimensionReader.read(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read back " + fileName, e);
        }
        if (dims != null) {
            data.put(DATA_WIDTH, dims[0]);
            data.put(DATA_HEIGHT, dims[1]);
        } else {
            data.remove(DATA_WIDTH);
            data.remove(DATA_HEIGHT);
        }
        post.setData(objectMapper.writeValueAsString(data));
    }

    /**
     * The stamped digest, but only while it can still be describing these bytes (SH399).
     *
     * <p>{@code null} when there is no stamp, when the file is gone, or when the size it was taken
     * over is not the size on disk — which is proof somebody changed the bytes outside the CMS,
     * since every path that writes them re-stamps. A caller that gets {@code null} falls back to
     * hashing, and gets the right answer at the old cost; the fast path is for bytes the CMS
     * itself wrote, which is all of them in a healthy instance.</p>
     *
     * <p><b>It detects a length change and nothing else.</b> An external edit that preserves the
     * size exactly is invisible here, and nothing short of hashing on every read would see it —
     * which is the cost SH398 exists to remove. That is the limit of the guarantee, stated rather
     * than left for a reader to work out.</p>
     *
     * <p>Detect, never repair: re-stamping here would make a {@code GET} write, and a read that
     * mutates is the surprise P7 is about. The recompute is correct every time; only its cost
     * persists, and only for a file somebody edited behind the CMS's back.</p>
     */
    private String trustedStamp(Data data, Attributes attributes) {
        if (attributes == null) {
            return null;
        }
        String stamped = data.text(DATA_SHA256);
        if (stamped == null || stamped.isBlank()) {
            return null;
        }
        Long size = data.longValue(DATA_SIZE);
        return size != null && size == attributes.size() ? stamped : null;
    }

    /** A stream's sha-256, read in bounded memory whatever the file's size. */
    private static byte[] sha256Of(InputStream bytes) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Required of every JRE, so this branch is unreachable rather than unhandled.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        byte[] buffer = new byte[8192];
        int read;
        while ((read = bytes.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
        return digest.digest();
    }

    /** What one stat says: the file is there, and how big it is. {@code null} when it is not. */
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
        return findFileId(folderId, fileName).isPresent();
    }

    /**
     * SH1252 — the id of the {@code File} post with this name in this folder, if there is one.
     *
     * <p>What lets a blueprint re-apply its assets idempotently: found, the bytes are compared by
     * digest and replaced only when they differ; absent, the asset is uploaded.</p>
     */
    public java.util.Optional<String> findFileId(String folderId, String fileName) {
        String name = ShFileStorageService.sanitizeFileName(fileName);
        return shFolderRepository.findById(folderId)
                .flatMap(folder -> shPostUnifiedRepository
                        .findByShFolderAndStateIn(folder, Set.of(ShPostState.DRAFT, ShPostState.PUBLISHED))
                        .stream()
                        .filter(p -> p.getShPostType() != null
                                && ShSystemPostType.FILE.equals(p.getShPostType().getName())
                                && name.equals(p.getTitle()))
                        .map(ShPostUnified::getId)
                        .sorted()
                        .findFirst());
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
        return matching(folderId, siteId, category, q);
    }

    /** What a caller that says nothing gets — the page size the object listing has used since SH165. */
    private static final int DEFAULT_PAGE_SIZE = 50;

    /**
     * One page of the media library, with the counts a filter bar needs (SH1030).
     *
     * <h2>What this changes and what it deliberately does not</h2>
     *
     * <p>The <b>response</b> is a page. The <b>read</b> is still the folder's rows, narrowed by
     * the predicate SH375 moved into the query and then filtered here on category and name — the
     * design's remaining half is to push those two down as well, which needs the content type
     * stamped at upload, and it is a change to what is <em>written</em> rather than to what is
     * read. Shipping the envelope first is what lets every surface stop assuming one response
     * while that lands.</p>
     *
     * <p>So this is honest about its own cost: it does not make the server's work smaller, it
     * makes the answer's size bounded. That is the half the console was paying twice for — it
     * downloaded the library, filtered it, and counted its chips over the whole array.</p>
     */
    @Transactional(readOnly = true)
    public ShStaticFileListing page(String folderId, String siteId, String category, String q,
                                    Integer page, Integer size) {
        int index = Math.max(page == null ? 0 : page, 0);
        int length = Math.max(size == null ? DEFAULT_PAGE_SIZE : size, 1);

        // Everything the filters admit EXCEPT the category, which is what the facets are counted
        // over: a chip saying "video 40" while `image` is selected is the answer to "what else is
        // there", and counting inside the current category answers nobody's question.
        List<ShStaticFileItem> beforeCategory = matching(folderId, siteId, null, q);
        Map<String, Long> facets = new java.util.TreeMap<>();
        for (ShStaticFileItem item : beforeCategory) {
            facets.merge(categoryOf(item.contentType()), 1L, Long::sum);
        }
        facets.put("all", (long) beforeCategory.size());

        String wantCategory = isBlank(category) ? null : category.toLowerCase(Locale.ROOT);
        List<ShStaticFileItem> matched = wantCategory == null ? beforeCategory
                : beforeCategory.stream()
                        .filter(item -> wantCategory.equals(categoryOf(item.contentType())))
                        .toList();

        int from = Math.min(index * length, matched.size());
        int to = Math.min(from + length, matched.size());
        return new ShStaticFileListing(withUsage(matched.subList(from, to)), index, length,
                matched.size(), facets);
    }

    /**
     * Stamps each row with how many posts reference it (SH1034).
     *
     * <p><b>On the page's rows, in one query.</b> Before the index this was unanswerable per row at
     * any acceptable price — SH1033 measured the per-asset form at a table scan each — and the
     * mistake available now is cheaper but the same shape: a count per row is N indexed reads where
     * the whole point of the index is that the answer costs what the answer is. So the ids go in
     * together, once, for the ten rows being drawn rather than the library they came from.</p>
     */
    private List<ShStaticFileItem> withUsage(List<ShStaticFileItem> rows) {
        if (rows.isEmpty()) {
            return rows;
        }
        Map<String, Long> counts = shObjectReferenceService
                .referrerCounts(rows.stream().map(ShStaticFileItem::id).toList());
        return rows.stream().map(row -> row.withUsageCount(counts.get(row.id()))).toList();
    }

    private List<ShStaticFileItem> matching(String folderId, String siteId, String category,
                                            String q) {
        ShPostType fileType = shPostTypeRepository.findByName(ShSystemPostType.FILE);
        if (fileType == null) {
            return List.of(); // no File post type yet => nothing uploaded
        }
        String wantCategory = isBlank(category) ? null : category.toLowerCase(Locale.ROOT);
        String wantQuery = isBlank(q) ? null : q.toLowerCase(Locale.ROOT);
        return rowsFor(folderId, fileType, q, category)
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

    /**
     * One {@code File} post's {@code data}, parsed once (SH1029).
     *
     * <h2>What it replaces</h2>
     *
     * <p>{@link #toItem} read eight keys — {@code FILE}, {@code WIDTH}, {@code HEIGHT},
     * {@code ALT}, {@code CAPTION}, {@code TAGS}, and {@code SHA256} and {@code SIZE} through
     * {@link #trustedStamp} — and each of the typed readers began by parsing the whole
     * {@code data} string again. Eight parses per row, every row, every listing: a tenant with a
     * few thousand assets paid tens of thousands of them for one request, and
     * {@code ShMediaListingBenchmarkTest} is the measurement that says how many.</p>
     *
     * <h2>Why a value rather than a cache</h2>
     *
     * <p>A memo keyed on the post would be a second thing that can be stale, and staleness here is
     * invisible: a listing answering from a cache written before a metadata edit shows the old alt
     * text and nothing looks wrong. This is parsed at the top of the one method that needs the
     * whole row and then thrown away, which is the smallest thing that can be correct.</p>
     *
     * <p>The single-key readers stay for their own callers — {@code fileNameOf} is public and is
     * called from the download path, where one key really is all that is wanted.</p>
     */
    private record Data(tools.jackson.databind.JsonNode tree) {

        String text(String key) {
            var value = tree == null ? null : tree.get(key);
            if (value == null || value.isNull()) {
                return null;
            }
            String read = value.asString();
            return read != null && !read.isBlank() ? read : null;
        }

        tools.jackson.databind.JsonNode number(String key) {
            var value = tree == null ? null : tree.get(key);
            return value != null && value.isNumber() ? value : null;
        }

        Integer integer(String key) {
            var value = number(key);
            return value == null ? null : value.asInt();
        }

        Long longValue(String key) {
            var value = number(key);
            return value == null ? null : value.asLong();
        }

        List<String> tags() {
            var value = tree == null ? null : tree.get(DATA_TAGS);
            if (value == null || !value.isArray()) {
                return List.of();
            }
            List<String> tags = new java.util.ArrayList<>();
            value.forEach(each -> {
                if (each != null && !each.isNull()) {
                    tags.add(each.asString());
                }
            });
            return tags;
        }
    }

    /** A post's {@code data} as one tree, or an empty one — the only place a listing parses it. */
    private Data dataOnce(ShPostUnified post) {
        if (post == null || post.getData() == null) {
            return new Data(null);
        }
        try {
            return new Data(objectMapper.readTree(post.getData()));
        } catch (RuntimeException e) {
            // The one place that has to know a `data` column can be unparseable, which is what the
            // eight readers each carried a copy of.
            log.debug("Could not read data from post {}", post.getId(), e);
            return new Data(null);
        }
    }

    private ShStaticFileItem toItem(ShPostUnified post) {
        Data data = dataOnce(post);
        String fileName = data.text(ShSystemPostTypeAttr.FILE);
        long size = -1;
        String contentType = null;
        Attributes attributes = null;
        if (fileName != null) {
            Path path = storage.resolve(post.getId(), fileName);
            // SH398 — one `readAttributes` where this was `isRegularFile` then `size`, which is two
            // syscalls asking the filesystem the same question twice. Stamping the size was the
            // obvious alternative and is worth less: the existence check has to happen anyway to
            // keep `size: -1` honest, so a stamp would remove one stat of two and buy a `data` key
            // that can disagree with the disk. One call removes the same stat and cannot go stale.
            // SH399 stamps the size anyway, for the other reason: not to skip this call but to
            // check the *digest* against it — see `trustedStamp`.
            attributes = attributesOf(path);
            if (attributes != null) {
                // SH1030 — the stamp when there is one, the probe for every row written before it
                // existed. The probe is not expensive by itself (it resolves by name first), but
                // it is a second answer to "what is this file", and two answers to one question is
                // what makes the category filter and the download disagree about an asset.
                String stamped = data.text(DATA_CONTENT_TYPE);
                contentType = stamped != null ? stamped : storage.probeContentType(path);
                size = attributes.size();
            }
        }
        return new ShStaticFileItem(
                post.getId(), fileName, contentType, size, post.getOwner(), post.getDate(),
                post.getShFolder() != null ? post.getShFolder().getId() : null,
                post.getShFolder() != null ? post.getShFolder().getName() : null,
                post.getShSite() != null ? post.getShSite().getId() : null,
                post.getShSite() != null ? post.getShSite().getName() : null,
                data.integer(DATA_WIDTH), data.integer(DATA_HEIGHT),
                data.text(DATA_ALT), data.text(DATA_CAPTION),
                data.tags(), trustedStamp(data, attributes));
    }

    /**
     * Update the editable Media Library metadata (SH62) of a {@code File} post —
     * {@code alt}/{@code caption}/{@code tags} merged into its {@code data},
     * leaving {@code FILE}/{@code WIDTH}/{@code HEIGHT} intact. A blank/null value
     * clears the key. 404 if the id is not a published {@code File} post.
     *
     * <h2>It writes an audit row now (SH1032)</h2>
     *
     * <p>{@link #replace} has recorded one since SH210 and this did not, so describing an image was
     * the one content write that reached neither the change feed nor the SH109 review queue. That
     * asymmetry was invisible while the console was the only caller — a curator who typed the alt
     * text knew they had typed it. {@code asset.meta} makes an agent a caller, and P7 says the
     * curator is never surprised by what one did: an image that acquired a description overnight
     * with nothing in the feed is exactly that surprise. The changed keys are named (SH989's
     * overload), so the row says <em>which</em> of the three moved rather than "metadata".</p>
     */
    @Transactional
    public ShStaticFileItem updateMeta(String id, String alt, String caption, List<String> tags,
                                       String actor) {
        ShPostUnified post = publishedFile(id);

        ObjectNode data = dataOf(post);
        // Read before the merge overwrites it: this row holds both sides, so naming the fields
        // costs no query — the same shape ShPostUnifiedWriteService#update uses.
        String before = post.getData();

        putOrRemove(data, DATA_ALT, alt);
        putOrRemove(data, DATA_CAPTION, caption);
        if (tags == null || tags.isEmpty()) {
            data.remove(DATA_TAGS);
        } else {
            var arr = data.putArray(DATA_TAGS);
            tags.stream().filter(s -> s != null && !s.isBlank()).map(String::trim).forEach(arr::add);
        }
        return saveMeta(post, data, before, actor);
    }

    /**
     * SH1263 — a {@code File} post's {@code ALT} alone, as a blueprint's asset declares it.
     *
     * <p>Not {@link #updateMeta}, which treats an absent caption or tag list as "clear it": a package
     * re-applied over an image a curator captioned would erase the caption. Writes nothing when the
     * stored value already matches, so a converged re-apply leaves no row in the feed.</p>
     */
    @Transactional
    public void describe(String id, String alt, String actor) {
        ShPostUnified post = publishedFile(id);
        ObjectNode data = dataOf(post);
        if (alt == null || alt.equals(data.path(DATA_ALT).asString(null))) {
            return;
        }
        String before = post.getData();
        data.put(DATA_ALT, alt);
        saveMeta(post, data, before, actor);
    }

    private ShPostUnified publishedFile(String id) {
        return shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED)
                .filter(p -> p.getShPostType() != null
                        && ShSystemPostType.FILE.equals(p.getShPostType().getName()))
                .orElseThrow(() -> new java.util.NoSuchElementException("File post not found: " + id));
    }

    private ShStaticFileItem saveMeta(ShPostUnified post, ObjectNode data, String before, String actor) {
        post.setData(objectMapper.writeValueAsString(data));
        post.setModifiedDate(Instant.now());
        if (actor != null) {
            post.setModifier(actor);
        }
        ShPostUnified saved = shPostUnifiedRepository.save(post);
        shObjectReferenceService.reindex(saved);
        // SH1032 — the feed and the review queue both read this, and describing an image is a
        // content change like any other. Nothing is recorded when nothing moved: a PUT that re-sent
        // the values already stored would otherwise put a row in a curator's feed saying an asset
        // changed, which is the inverse of the surprise this closes.
        List<String> changed = ShChangedFields.between(before, saved.getData());
        if (!changed.isEmpty()) {
            shContentAuditService.record(ShContentAction.UPDATE, saved.getId(),
                    saved.getShSite() != null ? saved.getShSite().getId() : null,
                    saved.getTitle(), actor, changed);
        }
        return toItem(saved);
    }

    /**
     * Posts that reference this file (SH62 "used in"). Scans PUBLISHED then DRAFT
     * referrers (a referenced id appears as a plain string in a post's {@code data}),
     * deduped by post id (PUBLISHED preferred), so an editor can see where a file is
     * used before deleting it.
     */
    @Transactional(readOnly = true)
    public List<ShStaticFileUsage> listUsage(String id) {
        // SH1034 — ONE indexed read where SH1033 measured two table scans, and only the rows the
        // answer names are loaded afterwards. The cost is the answer's size: 4 rows examined at
        // fifty thousand posts, against 50,002 twice.
        List<ShObjectReference> edges = shObjectReferenceService.referrersOf(id);
        Map<String, ShPostState> statesById = new LinkedHashMap<>();
        // PUBLISHED first, so the dedupe below prefers the live row — the same preference the
        // two-query form got from the order it ran them in.
        preferState(edges, ShPostState.PUBLISHED, id, statesById);
        preferState(edges, ShPostState.DRAFT, id, statesById);

        Map<String, ShStaticFileUsage> byId = new LinkedHashMap<>();
        statesById.forEach((postId, state) -> shPostUnifiedRepository
                .findByIdAndState(postId, state)
                .ifPresent(ref -> byId.put(ref.getId(), new ShStaticFileUsage(
                        ref.getId(), ref.getTitle(),
                        ref.getShPostType() != null ? ref.getShPostType().getName() : null,
                        ref.getShFolder() != null ? ref.getShFolder().getName() : null,
                        ref.getShSite() != null ? ref.getShSite().getName() : null,
                        state == ShPostState.PUBLISHED))));
        return List.copyOf(byId.values());
    }

    /** Claims each referrer for {@code state} unless an earlier call already claimed it. */
    private static void preferState(List<ShObjectReference> edges, ShPostState state,
            String assetId, Map<String, ShPostState> into) {
        for (ShObjectReference edge : edges) {
            if (edge.getFromState() == state && !edge.getFromPostId().equals(assetId)) {
                into.putIfAbsent(edge.getFromPostId(), state);
            }
        }
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

    /*
     * SH1029 removed five readers from here: numberFromData, intFromData, longFromData,
     * stringFromData and tagsFromData. Each began by parsing the whole `data` string, and `toItem`
     * called them six to eight times per row — 2400 parses for 400 assets, measured. `Data` parses
     * once and every key is read off that tree, so the typed readers had no callers left.
     *
     * Deleted rather than kept for a future caller: each carried its own `catch (RuntimeException)`
     * around the same parse, which ShSilentCatchInventoryTest counts as a declared population, and
     * a silent catch nobody calls is a number the next task has to explain.
     */

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
     * One field the {@code File} post-type is defined by (SH474).
     *
     * @param name     the {@code data} key this service writes, which is what the type must declare
     * @param label    what the console shows
     * @param widget   the catalogue key; {@code Hidden} for a value the machine stamps
     * @param ordinal  form order
     * @param required whether a {@code File} post can exist without it
     * @param title    whether it names the post
     */
    private record FileField(String name, String label, String widget, int ordinal,
                             boolean required, boolean title) {
    }

    /**
     * The whole model of a {@code File} post, read by creation <em>and</em> repair (SH474).
     *
     * <h2>Why four of these were missing for as long as they have been written</h2>
     *
     * <p>{@code FILE} was the only declared field, and every upload stores five: SH398's
     * {@code SHA256} and SH399's {@code SIZE}, then SH471's {@code WIDTH}/{@code HEIGHT}. A
     * declaration that omits what the writer unconditionally writes is not a smaller model, it is a
     * wrong one — SH168's {@code unknown-field} is an <b>error</b>, so a replica whose assets were
     * uploaded by this service went red on its own bytes. Measured on a clean replication: 66
     * findings, and the fix is to declare them rather than to stop writing them, because every one
     * is read back (the digest is how a re-upload is skipped, the size is what invalidates it, and
     * the dimensions are what lets a converted section draw an image without measuring it).</p>
     *
     * <p><b>{@code Hidden}</b> on all four, which is the honest widget: these are stamped from the
     * bytes and a curator editing one would be editing a claim about a file rather than the file.
     * The catalogue has had the row since before this service existed.</p>
     *
     * <h2>And the three SH474 left out, on a premise that turned out to be false (SH478)</h2>
     *
     * <p>{@code ALT}, {@code CAPTION} and {@code TAGS} are SH62's Media Library metadata, written by
     * {@link #updateMeta} rather than by an upload, and they were held back for a modelling question:
     * {@code TAGS} is an array, and the catalogue's {@code Multi Select} was believed to read
     * {@code widgetSettings} naming the options a curator may pick — which a tag vocabulary does not
     * have in advance. <b>It does not.</b> {@code multi-select.widget.tsx} is a free-form chip input:
     * type a value, press Enter. The widgets that need an option list are {@code Combo Box} and
     * {@code Check Box}, and the premise was those two, carried across by assumption. So there was
     * never a decision to make, and the three fields waited on it for a block.</p>
     *
     * <p>The widgets are therefore the ones the curator is <em>already</em> using: the Media Library
     * dialog renders alt with an {@code Input}, caption with a two-row {@code Textarea} and tags with
     * the same chip control the {@code Multi Select} widget is. A declaration that disagreed with
     * that dialog would be the same defect one surface over.</p>
     */
    private static List<FileField> fileFields() {
        return List.of(
                new FileField(ShSystemPostTypeAttr.FILE, "File Path", ShSystemWidget.FILE, 1, true, true),
                new FileField(DATA_SHA256, "SHA-256", ShSystemWidget.HIDDEN, 2, false, false),
                new FileField(DATA_SIZE, "Size (bytes)", ShSystemWidget.HIDDEN, 3, false, false),
                new FileField(DATA_WIDTH, "Width (px)", ShSystemWidget.HIDDEN, 4, false, false),
                new FileField(DATA_HEIGHT, "Height (px)", ShSystemWidget.HIDDEN, 5, false, false),
                new FileField(DATA_ALT, "Alternative text", ShSystemWidget.TEXT, 6, false, false),
                new FileField(DATA_CAPTION, "Caption", ShSystemWidget.TEXT_AREA, 7, false, false),
                new FileField(DATA_TAGS, "Tags", ShSystemWidget.MULTI_SELECT, 8, false, false),
                // SH1098 — written by SH951's two sanitize paths since the day they shipped, and
                // declared by nobody. SH168 makes an undeclared field an ERROR, so `shio_assets`,
                // which reads it beside FILE to answer "were these the uploaded bytes", refused
                // every listing that returned an asset — the whole tool, on every instance. Four
                // passes over exactly this question missed it (SH474's four keys, SH478's three,
                // SH1030's CONTENT_TYPE) because the lint reflected constants named `DATA_*` and
                // this one is `ShSystemPostTypeAttr.SANITIZED`. The lint reads call sites now.
                //
                // HIDDEN for the reason SHA256 and SIZE are: it is a verdict about bytes, and a
                // curator editing it would be editing a claim about a file rather than the file.
                new FileField(ShSystemPostTypeAttr.SANITIZED, "Sanitized", ShSystemWidget.HIDDEN,
                        10, false, false),
                // SH1030 — stamped at upload so the category filter reads a value rather than
                // resolving one per row. Hidden, like the other stamps: it is what the machine
                // recorded about the bytes, not something a curator sets.
                new FileField(DATA_CONTENT_TYPE, "Content type", ShSystemWidget.HIDDEN, 9,
                        false, false));
    }

    /**
     * Find the system {@code File} post type, creating it the first time and adding whatever
     * {@link #fileFields} declares that it lacks. A fresh install does not seed system post types,
     * so an upload must be able to bootstrap it.
     *
     * <h2>Why the existing-type branch repairs instead of returning (SH474)</h2>
     *
     * <p>Because there is no other door. {@code PUT /api/v2/post-type/File} is refused — the type
     * is {@code system}, and so is {@code ShPostTypeAdminService.mergeMissingFields}, which is the
     * additive verb a blueprint uses. {@code render.provision} repairs the five names in
     * {@code ShRenderPostTypes.NAMES}, and {@code File} is deliberately not one of them: that list
     * is also every "is this a template type" test in the renderer. So an instance that uploaded an
     * asset before this release has four keys its model does not mention and nothing an operator
     * can call.</p>
     *
     * <p>It is SH289's rule rather than a violation of SH152: <b>additive only</b>, from a table
     * this code owns, of fields this same class writes unconditionally two methods down. Nothing a
     * curator authored is touched, because a curator cannot author on a system type. What it does
     * not do is repair an instance nobody uploads to — the next upload is the trigger, which for a
     * replication is the run itself.</p>
     */
    @Transactional
    public ShPostType resolveOrCreateFilePostType() {
        ShPostType existing = shPostTypeRepository.findByName(ShSystemPostType.FILE);
        if (existing != null) {
            return addMissingFields(existing);
        }

        ShPostType postType = new ShPostType();
        postType.setName(ShSystemPostType.FILE);
        postType.setNamePlural("Files");
        postType.setTitle("File");
        postType.setDescription("File Post Type");
        postType.setSystem((byte) 1);
        postType.setDate(Instant.now());
        postType.setOwner("admin");

        Set<ShPostTypeAttr> attrs = new HashSet<>();
        for (FileField field : fileFields()) {
            attrs.add(attrOf(field, postType));
        }
        postType.setShPostTypeAttrs(attrs);

        return shPostTypeRepository.save(postType);
    }

    /** Adds what {@link #fileFields} declares and this type lacks; returns it unchanged otherwise. */
    private ShPostType addMissingFields(ShPostType existing) {
        Set<ShPostTypeAttr> attrs = existing.getShPostTypeAttrs();
        if (attrs == null) {
            return existing;
        }
        Set<String> present = new HashSet<>();
        attrs.forEach(attr -> present.add(attr.getName()));
        List<FileField> missing = fileFields().stream()
                .filter(field -> !present.contains(field.name()))
                .toList();
        if (missing.isEmpty()) {
            return existing;
        }
        for (FileField field : missing) {
            attrs.add(attrOf(field, existing));
        }
        log.info("File post type: added {}", missing.stream().map(FileField::name).toList());
        return shPostTypeRepository.save(existing);
    }

    private ShPostTypeAttr attrOf(FileField field, ShPostType postType) {
        // findFirstByName, the only resolver there is now (SH753). This line used to call the
        // derived single-result form, which throws IncorrectResultSizeDataAccessException on a
        // second row — and it runs on EVERY upload, resolving the File post type, so a catalog
        // that had picked up duplicate `Text` rows took uploads down rather than degrading.
        ShWidget widget = shWidgetRepository.findFirstByName(field.widget());
        if (widget == null) {
            throw new IllegalStateException(
                    field.widget() + " widget is not seeded; cannot model the File post type");
        }
        ShPostTypeAttr attr = new ShPostTypeAttr();
        attr.setName(field.name());
        attr.setLabel(field.label());
        attr.setDescription(field.label());
        attr.setOrdinal(field.ordinal());
        attr.setRequired((byte) (field.required() ? 1 : 0));
        attr.setIsTitle((byte) (field.title() ? 1 : 0));
        attr.setIsSummary((byte) 0);
        attr.setShWidget(widget);
        attr.setShPostType(postType);
        return attr;
    }
}
