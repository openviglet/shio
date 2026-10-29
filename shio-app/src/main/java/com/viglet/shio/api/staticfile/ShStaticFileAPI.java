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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
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
    private final ShAssetLoadService shAssetLoadService;

    public ShStaticFileAPI(ShStaticFileService shStaticFileService,
                           ShFileStorageService storage,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShImageTransformPipeline imageTransformPipeline,
                           ShImageTransformGuard imageTransformGuard,
                           ShImageTransformSigner imageTransformSigner,
                           ShAssetLoadService shAssetLoadService) {
        this.shAssetLoadService = shAssetLoadService;
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
    public ShStaticFileListing list(
            @RequestParam(name = "folderId", required = false) String folderId,
            @RequestParam(name = "siteId", required = false) String siteId,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "q", required = false) String q,
            // SH1030 — the one content read nobody had paged. `page`/`size` are the object
            // listing's own words (SH165), so a reader who knows one listing knows this one.
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size,
            // SH1280 — opt-in, because it renders every page of the rows' sites: the count of
            // pages that load each asset, which the reference index cannot see (a theme's font).
            @RequestParam(name = "loads", defaultValue = "false") boolean loads) {
        ShStaticFileListing listing = shStaticFileService.page(folderId, siteId, category, q, page, size);
        if (!loads || listing.items().isEmpty()) {
            return listing;
        }
        return listing.withLoads(shAssetLoadService.loadedBy(listing.items().stream()
                .map(ShStaticFileItem::siteId).filter(java.util.Objects::nonNull).distinct().toList()));
    }

    /**
     * Update the editable Media Library metadata of a {@code File} post (SH62):
     * {@code alt}/{@code caption}/{@code tags}. Returns the refreshed
     * {@link ShStaticFileItem}; 404 if the id is not a published {@code File} post.
     */
    @PutMapping("/{id}/meta")
    public ShStaticFileItem updateMeta(@PathVariable String id,
                                       @RequestBody ShStaticFileMetaRequest req,
                                       Principal principal) {
        try {
            return shStaticFileService.updateMeta(id,
                    req != null ? req.alt() : null,
                    req != null ? req.caption() : null,
                    req != null ? req.tags() : null,
                    // SH1032 — named on the audit row the merge now writes, exactly as `replace`
                    // has named it since SH210. A feed entry with no actor is the half-answer that
                    // makes a curator go looking.
                    principal != null ? principal.getName() : null);
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /** Body of {@code PUT /{id}/meta}: editable Media Library metadata (SH62). */
    public record ShStaticFileMetaRequest(String alt, String caption, java.util.List<String> tags) {
    }

    /**
     * List the posts that reference this file (SH62 "used in") so an editor can see
     * where it is used before deleting it. Empty list if the file is unused/unknown.
     */
    @GetMapping("/{id}/usage")
    public java.util.List<ShStaticFileUsage> usage(@PathVariable String id) {
        return shStaticFileService.listUsage(id);
    }

    /**
     * The bytes this asset used to have (SH1035), newest first.
     *
     * <p>What turns the console's Replace control from a thing a curator is afraid of into one they
     * will use: §LXX.7's argument is that a copy of the superseded bytes is what makes replacing
     * <em>safe enough to encourage</em>. An instance with the feature off answers an empty list,
     * which is the same answer as "nothing has been replaced yet" — the dialog shows no history
     * either way, and no caller has to special-case a configuration it cannot see.</p>
     *
     * <p>These bytes are <b>not</b> under {@code /file_source/**}. That path is mapped at the root
     * of the asset tree, so a rendition kept beside the live file would be publicly downloadable —
     * a superseded document says whatever it was replaced for saying. They are reached only here.</p>
     */
    @GetMapping("/{id}/renditions")
    public java.util.List<ShAssetRendition> renditions(@PathVariable String id) {
        return shStaticFileService.listRenditions(id);
    }

    /**
     * Put a superseded set of bytes back (SH1035).
     *
     * <p>{@code POST} and not {@code PUT}: this is not idempotent in the way a reader would expect,
     * because reverting captures the <em>current</em> bytes as a new rendition on the way past — so
     * a second call reverts the revert rather than doing nothing. That is deliberate (a curator who
     * reverts to the wrong version must be able to come back), and it is exactly why the verb is
     * the non-idempotent one.</p>
     *
     * <p>An unknown number is a teaching 400 rather than a 404: the asset exists, the request is
     * answerable, and what the caller needs is the list and the instance's own ceiling — both of
     * which the message names (P6).</p>
     */
    @PostMapping("/{id}/renditions/{number}/revert")
    public ShStaticFileItem revert(@PathVariable String id, @PathVariable int number,
                                   Principal principal) {
        return shStaticFileService.revertToRendition(id, number,
                principal != null ? principal.getName() : null);
    }

    /**
     * Upload a file into {@code folderId}, creating a {@code File} post for it.
     * Returns the created post (200) so the caller can store its id.
     *
     * <h2>A zero-byte file is a file (SH514)</h2>
     *
     * <p>This refused an empty upload with {@code ResponseStatusException(BAD_REQUEST, "Upload is
     * empty")}, which Spring renders as {@code {"timestamp":…,"status":400,"error":"Bad
     * Request","path":…}} — <b>no title, no detail, no fix</b>, and the only endpoint in a whole
     * session's traffic to answer that way. The sibling refusal for a missing {@code folderId}
     * carries all four and an example, so the convention was implemented and this path fell outside
     * it.</p>
     *
     * <p>The file was real. {@code vilt-group.com} serves {@code /css/en.css?v=cf8427e} as <b>200
     * with 0 bytes</b> — a legitimately empty stylesheet — the capture wrote it faithfully, and the
     * whole conversion of a 64-asset site died on it. So a correct capture of a correct source
     * produced bytes this endpoint rejected without saying why.</p>
     *
     * <p><b>{@code isEmpty()} was answering two questions at once</b>, and separating them settles
     * it: {@code MultipartFile.isEmpty()} is {@code getSize() == 0}, which is true both when the
     * caller sent no part and when the part holds an empty file. The first is a mistake and is
     * refused — with a problem document, like everything else here. The second is <em>content</em>:
     * a replica that 404s where its source answered an empty 200 is less faithful, not safer, and
     * zero is a count of bytes. {@code /replace} keeps its refusal, because there the same bytes
     * mean truncation of something that exists.</p>
     */
    @PostMapping("/upload")
    public ResponseEntity<ShPostUnified> upload(@RequestParam("file") MultipartFile file,
                                                @RequestParam("folderId") String folderId,
                                                @RequestParam(name = "createPost", defaultValue = "true") boolean createPost,
                                                Principal principal) {
        if (file == null) {
            throw new NoFilePartException();
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new UnnamedFilePartException();
        }
        String owner = principal != null ? principal.getName() : null;
        ShPostUnified post = shStaticFileService.upload(file, folderId, owner);
        return ResponseEntity.ok(post);
    }

    /**
     * What an asset's stored bytes hash to (SH210), so {@code shio push --content} can tell an
     * edited asset from an untouched one without transferring either copy. 404 if the id is not a
     * published {@code File} post.
     */
    @GetMapping("/{id}/digest")
    public ShStaticFileService.ShStaticFileDigest digest(@PathVariable String id) {
        try {
            return shStaticFileService.digestOf(id);
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Replace the bytes of an existing {@code File} post, keeping its id, folder and name
     * (SH210). Returns the refreshed {@link ShStaticFileItem}; 404 if the id is not a published
     * {@code File} post.
     *
     * <p><b>{@code POST}, not {@code PUT}, and that is not a REST slip.</b> Servlet multipart
     * resolution is specified for {@code POST} and is a container-dependent extra for anything
     * else, so a {@code PUT} here would parse the body on one server and hand the controller an
     * empty file on another — a failure that looks like the caller's. The sibling that takes no
     * bytes ({@code PUT /{id}/meta}) is a {@code PUT} for the same reason: it can be.</p>
     */
    @PostMapping("/{id}/replace")
    public ShStaticFileItem replace(@PathVariable String id,
                                    @RequestParam("file") MultipartFile file,
                                    Principal principal) {
        if (file == null || file.isEmpty()) {
            // An empty replacement would truncate the asset and answer 200. A caller that meant
            // to delete it has `/v2/post-unified/{id}`, which also cleans up the bytes. SH514 left
            // this refusal standing and gave it the problem document it always deserved: the
            // sentence was right and Spring was dropping it on the floor.
            throw new EmptyReplacementException();
        }
        try {
            return shStaticFileService.replace(file, id,
                    principal != null ? principal.getName() : null);
        } catch (java.util.NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
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
                // SH950: the transform's own output type decides the disposition, not the
                // source's — `?format=` is how a caller changes it.
                ResponseEntity.BodyBuilder builder = ShAssetResponseHeaders.harden(
                        ResponseEntity.ok().contentType(MediaType.parseMediaType(transformed.contentType())),
                        transformed.contentType(), fileName);
                if (etag != null) {
                    builder.eTag(etag);
                }
                return builder.body(new ByteArrayResource(transformed.bytes()));
            }
        }

        // SH950: an uploaded SVG or HTML file is not a document this origin renders.
        return ShAssetResponseHeaders
                .harden(ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)),
                        contentType, fileName)
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

    // --- SH514: the three refusals this endpoint owes a problem document ------
    //
    // Named subclasses rather than inline construction, which is the convention `ShTeachingException`
    // enforces with a protected constructor: an error a caller can be expected to hit is part of the
    // surface, and giving it a type is what stops one failure being worded three ways.

    /** No {@code file} part in the request at all — a caller mistake, and nothing to store. */
    public static class NoFilePartException extends ShTeachingException {

        public NoFilePartException() {
            super(ShProblem.badRequest("upload-no-file",
                            "This request carried no file to upload")
                    .withDetail("The `file` part is missing from the multipart body, so there are "
                            + "no bytes to store. A file that is *empty* is a different thing and "
                            + "is accepted: zero is a count of bytes, and a source that serves an "
                            + "empty stylesheet with a 200 is replicated by storing one.")
                    .withFix("Send the bytes as a multipart part named `file`, alongside "
                            + "`folderId`.")
                    .withExample("curl -F file=@site.css -F folderId=<id> "
                            + "/api/v2/staticfile/upload"));
        }
    }

    /** A part with no filename: there is nothing to address the stored asset by. */
    public static class UnnamedFilePartException extends ShTeachingException {

        public UnnamedFilePartException() {
            super(ShProblem.badRequest("upload-unnamed-file",
                            "This upload has no file name")
                    .withDetail("An asset is addressed by its folder chain and its file name "
                            + "(SH145), so a part with no name is one nothing could ever ask for.")
                    .withFix("Set the part's filename — with curl, `-F file=@<path>` sends it.")
                    .withExample("curl -F file=@hero.png -F folderId=<id> "
                            + "/api/v2/staticfile/upload"));
        }
    }

    /** Replacing an asset with nothing would truncate it and answer 200. */
    public static class EmptyReplacementException extends ShTeachingException {

        public EmptyReplacementException() {
            super(ShProblem.badRequest("replace-empty",
                            "This replacement carries no bytes")
                    .withDetail("Replacing an asset with an empty body would truncate it and "
                            + "answer 200, so every page that references it would serve nothing "
                            + "and nothing would say why.")
                    .withFix("Send the new bytes, or delete the asset with "
                            + "DELETE /api/v2/post-unified/{id}, which also removes the file.")
                    .withExample("curl -F file=@hero.png /api/v2/staticfile/<id>/replace"));
        }
    }
}
