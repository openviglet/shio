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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.property.ShUploadSessionProperties;
import com.viglet.shio.tenant.ShTenantQuotaService;
import com.viglet.shio.utils.ShIoFailure;

import lombok.extern.slf4j.Slf4j;

/**
 * SH1355 — an upload that survives the link dropping.
 *
 * <p>{@code POST /api/v2/staticfile/upload} takes the whole file in one request, which is right on a
 * desk and wrong on a phone: a request that dies at ninety percent leaves nothing to continue from.
 * This is the session shape beside it, which every resumable protocol converges on:</p>
 *
 * <ol>
 *   <li><b>begin</b> with the name, the size, the folder and the sha256 the client already
 *   computed. The upload policy and the quota decide here, before a byte is sent, because a refusal
 *   after the last chunk would waste the whole transfer.</li>
 *   <li><b>append</b> chunks by offset. A chunk that starts where the received bytes end is
 *   appended; one that lies wholly inside them is a retry and a no-op; anything else is a 409 that
 *   names the offset to resume from. So a client that does not know whether its last chunk landed
 *   asks, or simply resends it.</li>
 *   <li><b>finish</b> compares the digest of what arrived with the declared one, and only then hands
 *   the bytes to {@link ShStaticFileService#upload}. A half upload never becomes an asset, and a
 *   finished one is the asset a single request would have made.</li>
 * </ol>
 *
 * <p>The bytes live under {@code shio.upload-sessions.path}, per tenant, outside
 * {@code /file_source}, so a partial file is never publicly readable. A session belongs to the
 * principal and tenant that began it, and anyone else is told it does not exist. An abandoned session
 * expires after {@code shio.upload-sessions.ttl} and is swept on the next begin and by the storage
 * meter's reconcile.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Slf4j
public class ShUploadSessionService {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SESSION_ID = Pattern.compile("[0-9a-f-]{36}");
    private static final String META = "session.properties";
    private static final String DATA = "data.part";

    private final ShUploadSessionProperties properties;
    private final ShStaticFileService shStaticFileService;
    private final ShTenantQuotaService shTenantQuotaService;
    private final ShUploadPolicy shUploadPolicy;
    private final Clock clock;
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public ShUploadSessionService(ShUploadSessionProperties properties, ShStaticFileService shStaticFileService,
                                  ShTenantQuotaService shTenantQuotaService, ShUploadPolicy shUploadPolicy) {
        this(properties, shStaticFileService, shTenantQuotaService, shUploadPolicy, Clock.systemUTC());
    }

    ShUploadSessionService(ShUploadSessionProperties properties, ShStaticFileService shStaticFileService,
                           ShTenantQuotaService shTenantQuotaService, ShUploadPolicy shUploadPolicy, Clock clock) {
        this.properties = properties;
        this.shStaticFileService = shStaticFileService;
        this.shTenantQuotaService = shTenantQuotaService;
        this.shUploadPolicy = shUploadPolicy;
        this.clock = clock;
    }

    /** What a begin declares. */
    public record Begin(String name, Long size, String folderId, String sha256, String contentType) {
    }

    /**
     * Where a session stands: what to send next, and until when.
     *
     * @param offset   how many bytes have arrived, which is where the next chunk starts
     * @param complete whether every declared byte has arrived, so {@code finish} may be called
     */
    public record Status(String id, String name, long size, long offset, boolean complete, Instant expiresAt) {
    }

    private record Session(String id, String tenant, String owner, String name, long size, String folderId,
                           String sha256, String contentType, Instant createdAt) {
    }

    /** Opens a session after the policy and the quota have said yes. */
    public Status begin(Begin begin, String owner) {
        if (begin == null || begin.name() == null || begin.name().isBlank()) {
            throw new SessionFieldException("name", "the file name the asset will be stored under");
        }
        if (begin.size() == null || begin.size() < 0) {
            throw new SessionFieldException("size", "the file's length in bytes, which every chunk is counted against");
        }
        if (begin.folderId() == null || begin.folderId().isBlank()) {
            throw new SessionFieldException("folderId", "the folder (or site) the asset is created in");
        }
        String sha256 = begin.sha256() == null ? "" : begin.sha256().trim().toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw new SessionFieldException("sha256",
                    "the lower-case hex SHA-256 of the whole file, which finish checks the arrived bytes against");
        }
        String fileName = ShFileStorageService.sanitizeFileName(begin.name());
        String contentType = begin.contentType() == null || begin.contentType().isBlank() ? null : begin.contentType();
        // The same two questions `upload` asks, asked before any byte travels.
        String byName = ShAssetResponseHeaders.contentTypeByName(fileName);
        shUploadPolicy.check(byName != null ? byName : contentType != null ? contentType
                : ShAssetResponseHeaders.FALLBACK_CONTENT_TYPE, fileName);
        if (contentType != null && byName != null
                && !ShUploadPolicy.bare(contentType).equals(ShUploadPolicy.bare(byName))) {
            shUploadPolicy.check(contentType, fileName);
        }
        shTenantQuotaService.checkCanStore(begin.size());
        sweepExpired();

        Session session = new Session(UUID.randomUUID().toString(), ShFileStorageService.tenantSegment(),
                owner == null ? "" : owner, fileName, begin.size(), begin.folderId(), sha256, contentType,
                clock.instant());
        Path dir = dirOf(session.id());
        try {
            Files.createDirectories(dir);
            Files.createFile(dir.resolve(DATA));
            writeMeta(dir, session);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not open an upload session", e);
        }
        return statusOf(session, 0);
    }

    /** Where a session stands. */
    public Status status(String id, String owner) {
        Session session = load(id, owner);
        return statusOf(session, received(session.id()));
    }

    /**
     * Appends one chunk that starts at {@code offset}.
     *
     * <p>Idempotent by construction: a chunk wholly inside the bytes already received is the retry of
     * one that landed, and changes nothing.</p>
     */
    public Status append(String id, long offset, InputStream chunk, String owner) {
        Session session = load(id, owner);
        synchronized (locks.computeIfAbsent(session.id(), key -> new Object())) {
            Path data = dirOf(session.id()).resolve(DATA);
            long received = received(session.id());
            if (offset < 0 || offset > received) {
                throw new OffsetMismatchException(session.id(), offset, received);
            }
            try {
                if (offset < received) {
                    // A retry: read what it carries, and accept it only if it ends inside what has
                    // already arrived. A chunk straddling the end is a client that lost count.
                    long length = chunk.transferTo(OutputStream.nullOutputStream());
                    if (offset + length > received) {
                        throw new OffsetMismatchException(session.id(), offset, received);
                    }
                    return statusOf(session, received);
                }
                long written;
                try (OutputStream out = Files.newOutputStream(data, StandardOpenOption.APPEND)) {
                    written = copyAtMost(chunk, out, session.size() - received);
                }
                if (written < 0) {
                    truncate(data, received);
                    throw new TooManyBytesException(session.id(), session.size());
                }
                return statusOf(session, received + written);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not append to upload session " + session.id(), e);
            }
        }
    }

    /**
     * Turns a complete session into the asset, after the digest agrees, and removes the session.
     *
     * <p>A digest that disagrees discards the session: the bytes are not the file that was declared,
     * and resuming would only append to the wrong ones.</p>
     */
    public ShPostUnified finish(String id, String owner) {
        Session session = load(id, owner);
        synchronized (locks.computeIfAbsent(session.id(), key -> new Object())) {
            Path dir = dirOf(session.id());
            long received = received(session.id());
            if (received != session.size()) {
                throw new IncompleteSessionException(session.id(), received, session.size());
            }
            String digest = sha256Of(dir.resolve(DATA));
            if (!digest.equals(session.sha256())) {
                delete(dir);
                throw new DigestMismatchException(session.sha256(), digest);
            }
            ShPostUnified post = shStaticFileService.upload(
                    new ShStagedUpload(dir.resolve(DATA), session.name(), session.contentType()),
                    session.folderId(), session.owner().isEmpty() ? null : session.owner());
            delete(dir);
            locks.remove(session.id());
            return post;
        }
    }

    /** Drops a session and its bytes. */
    public void abort(String id, String owner) {
        Session session = load(id, owner);
        delete(dirOf(session.id()));
        locks.remove(session.id());
    }

    /**
     * Removes every expired session of the current tenant, returning how many went.
     *
     * <p>Called on each begin, which is when the next abandoned half would otherwise join the pile,
     * and by the storage meter's reconcile, which is the instance's own "make the disk honest" act.</p>
     */
    public int sweepExpired() {
        return sweepExpired(tenantRoot());
    }

    /** {@link #sweepExpired()} for a named tenant, which is how the meter's reconcile asks. */
    public int sweepExpired(String tenant) {
        return sweepExpired(properties.getRootDir().resolve(ShFileStorageService.sanitizeTenant(tenant)));
    }

    private int sweepExpired(Path root) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant now = clock.instant();
        int swept = 0;
        try (Stream<Path> dirs = Files.list(root)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                Optional<Session> session = readMeta(dir);
                if (session.isEmpty() || !session.get().createdAt().plus(properties.getTtl()).isAfter(now)) {
                    delete(dir);
                    swept++;
                }
            }
        } catch (IOException e) {
            log.warn("SH1355 could not sweep upload sessions under {}: {}", root, ShIoFailure.describe(e));
        }
        return swept;
    }

    private Session load(String id, String owner) {
        if (id == null || !SESSION_ID.matcher(id).matches()) {
            throw new UnknownSessionException(String.valueOf(id));
        }
        Session session = readMeta(dirOf(id)).orElseThrow(() -> new UnknownSessionException(id));
        // Someone else's session reads as no session, so the route says nothing about which exist.
        if (!session.tenant().equals(ShFileStorageService.tenantSegment())
                || !session.owner().equals(owner == null ? "" : owner)) {
            throw new UnknownSessionException(id);
        }
        if (!session.createdAt().plus(properties.getTtl()).isAfter(clock.instant())) {
            delete(dirOf(id));
            throw new UnknownSessionException(id);
        }
        return session;
    }

    private Status statusOf(Session session, long received) {
        return new Status(session.id(), session.name(), session.size(), received, received == session.size(),
                session.createdAt().plus(properties.getTtl()));
    }

    private long received(String id) {
        try {
            return Files.size(dirOf(id).resolve(DATA));
        } catch (IOException e) {
            throw new UnknownSessionException(id);
        }
    }

    private Path tenantRoot() {
        return properties.getRootDir().resolve(ShFileStorageService.tenantSegment());
    }

    private Path dirOf(String id) {
        Path root = tenantRoot();
        Path dir = root.resolve(id).normalize();
        if (!dir.startsWith(root)) {
            throw new UnknownSessionException(id);
        }
        return dir;
    }

    /** Copies at most {@code room} bytes; -1 when the stream held more than that. */
    private static long copyAtMost(InputStream in, OutputStream out, long room) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) >= 0) {
            if (total + read > room) {
                return -1;
            }
            out.write(buffer, 0, read);
            total += read;
        }
        return total;
    }

    private static void truncate(Path data, long length) throws IOException {
        try (var channel = java.nio.channels.FileChannel.open(data, StandardOpenOption.WRITE)) {
            channel.truncate(length);
        }
    }

    private static String sha256Of(Path data) {
        try (InputStream in = new DigestInputStream(Files.newInputStream(data), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload session bytes", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void writeMeta(Path dir, Session session) throws IOException {
        Properties meta = new Properties();
        meta.setProperty("id", session.id());
        meta.setProperty("tenant", session.tenant());
        meta.setProperty("owner", session.owner());
        meta.setProperty("name", session.name());
        meta.setProperty("size", Long.toString(session.size()));
        meta.setProperty("folderId", session.folderId());
        meta.setProperty("sha256", session.sha256());
        meta.setProperty("contentType", Objects.requireNonNullElse(session.contentType(), ""));
        meta.setProperty("createdAt", session.createdAt().toString());
        try (Writer out = Files.newBufferedWriter(dir.resolve(META), StandardCharsets.UTF_8)) {
            meta.store(out, "SH1355 upload session");
        }
    }

    private static Optional<Session> readMeta(Path dir) {
        Path file = dir.resolve(META);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Properties meta = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            meta.load(in);
            String contentType = meta.getProperty("contentType", "");
            return Optional.of(new Session(meta.getProperty("id"), meta.getProperty("tenant"),
                    meta.getProperty("owner", ""), meta.getProperty("name"),
                    Long.parseLong(meta.getProperty("size")), meta.getProperty("folderId"),
                    meta.getProperty("sha256"), contentType.isEmpty() ? null : contentType,
                    Instant.parse(meta.getProperty("createdAt"))));
        } catch (IOException | RuntimeException e) {
            log.warn("SH1355 unreadable upload session {}: {}", dir, e.getMessage());
            return Optional.empty();
        }
    }

    private static void delete(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("SH1355 could not delete {}: {}", path, ShIoFailure.describe(e));
                }
            });
        } catch (IOException e) {
            log.warn("SH1355 could not delete upload session {}: {}", dir, ShIoFailure.describe(e));
        }
    }

    // --- the refusals, each a problem document (P6) ---------------------------------------------

    private static final String BASE = "/api/v2/staticfile/upload-session";

    /** A begin missing a field the session cannot work without. */
    public static class SessionFieldException extends ShTeachingException {

        public SessionFieldException(String field, String what) {
            super(ShProblem.badRequest("upload-session-field", "An upload session needs `" + field + "`")
                    .withDetail("`" + field + "` is " + what + ".")
                    .withFix("Send name, size, folderId and sha256 as JSON; contentType is optional.")
                    .withExample(java.util.Map.of("name", "clip.mp4", "size", 73400320, "folderId", "<id>",
                            "sha256", "<64 lower-case hex digits>", "contentType", "video/mp4")));
        }
    }

    /** No such session for this caller: never begun, finished, aborted, expired, or someone else's. */
    public static class UnknownSessionException extends ShTeachingException {

        public UnknownSessionException(String id) {
            super(ShProblem.of("upload-session-unknown", HttpStatus.NOT_FOUND, "No upload session '" + id + "'")
                    .withDetail("A session ends when it is finished or aborted, and expires a while after "
                            + "it was begun. A session is only visible to the account that began it.")
                    .withFix("Begin a new one with POST " + BASE + " and send the file again.")
                    .withExample("POST " + BASE));
        }
    }

    /** A chunk that does not start where the received bytes end. */
    public static class OffsetMismatchException extends ShTeachingException {

        public OffsetMismatchException(String id, long sent, long received) {
            super(ShProblem.conflict("upload-session-offset",
                            "This chunk starts at " + sent + ", and " + received + " bytes have arrived")
                    .withDetail("A chunk is appended only when it starts where the received bytes end. "
                            + "A chunk that lies wholly inside them is a retry and is accepted as a no-op; "
                            + "this one is neither.")
                    .withFix("Resume from offset " + received + ": PUT " + BASE + "/" + id + "?offset="
                            + received + " with the bytes from there. GET " + BASE + "/" + id
                            + " reports the offset at any time.")
                    .withExample(java.util.Map.of("offset", received)));
        }
    }

    /** More bytes than the session declared. */
    public static class TooManyBytesException extends ShTeachingException {

        public TooManyBytesException(String id, long size) {
            super(ShProblem.badRequest("upload-session-overflow",
                            "This chunk runs past the " + size + " bytes the session declared")
                    .withDetail("Nothing from the chunk was kept, so the session is where it was before it.")
                    .withFix("Send only the bytes up to the declared size, or abort with DELETE " + BASE
                            + "/" + id + " and begin again with the real size.")
                    .withExample("GET " + BASE + "/" + id));
        }
    }

    /** Finish before every byte arrived. */
    public static class IncompleteSessionException extends ShTeachingException {

        public IncompleteSessionException(String id, long received, long size) {
            super(ShProblem.conflict("upload-session-incomplete",
                            received + " of " + size + " bytes have arrived")
                    .withDetail("A session becomes an asset only once every declared byte is here, so a "
                            + "half upload is never published.")
                    .withFix("Send the rest from offset " + received + ", then finish again.")
                    .withExample("PUT " + BASE + "/" + id + "?offset=" + received));
        }
    }

    /** The bytes that arrived are not the file that was declared. */
    public static class DigestMismatchException extends ShTeachingException {

        public DigestMismatchException(String declared, String actual) {
            super(ShProblem.of("upload-session-digest", HttpStatus.UNPROCESSABLE_ENTITY,
                            "The bytes that arrived do not hash to the declared sha256")
                    .withDetail("Declared " + declared + ", received " + actual + ". The session was "
                            + "discarded, because resuming would only append to the wrong bytes.")
                    .withFix("Check the digest is the SHA-256 of the whole file and that chunks were sent "
                            + "in order, then begin again.")
                    .withExample("sha256sum clip.mp4"));
        }
    }
}
