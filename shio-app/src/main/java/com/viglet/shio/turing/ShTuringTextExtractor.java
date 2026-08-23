/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.viglet.shio.api.staticfile.ShFileStorageService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.utils.ShIoFailure;
import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import lombok.extern.slf4j.Slf4j;

/**
 * The text of a published attachment, read by Turing rather than by Shio (SH913).
 *
 * <h2>Turing extracts; Shio asks (P10)</h2>
 *
 * <p>A published PDF was indexed by title, URL and whatever its {@code data} JSON held — its bytes
 * were never read, so a school publishing its rules and its fee table as attachments had documents
 * findable by name that answered nothing, and a chat grounded on the site could not cite them.</p>
 *
 * <p>The obvious fix was to parse the file here: {@code tika-core} and {@code tika-parser-pdf-module}
 * are already declared in {@code shio-app/pom.xml} and, at the time of writing, used by nothing.
 * That would have been a fork. Turing <b>already has an OCR API</b> — {@code POST /api/ocr/file},
 * reached by {@code TurOcr} in the same {@code turing-java-sdk} this package already indexes
 * through — and it is strictly more capable than a local parse: the engine behind it is Turing's
 * configuration, so a scanned document that Tika reads as an empty page comes back with text. Shio
 * sends the bytes and takes the answer. A change to how Turing extracts reaches Shio by bumping a
 * version, which is the same bargain {@link ShTuringIndexService} already made for the wire format.</p>
 *
 * <h2>The bound, and why it is Shio's and not the SDK's</h2>
 *
 * <p>{@code TurOcr} builds its connection manager with a <b>five-minute</b> socket timeout and
 * offers no way to change it. Five minutes is far too long to hold {@link ShTuringConfig}'s single
 * index worker, which is single-threaded to keep publish-then-unpublish ordering and would stall
 * every queued change behind one large PDF. So the call is made on a worker of its own and waited
 * for with a timeout Shio owns ({@code shio.turing.extract-timeout}, default 60s). On expiry the
 * document is indexed with its metadata and no text — late text is worth less than a blocked queue,
 * and the incident says which file it was.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Slf4j
@Service
public class ShTuringTextExtractor {

    /** Why a file has no indexed text, so the caller can report the cases worth reporting. */
    public enum Outcome {
        /** Text came back and is in {@link Extraction#text()}. */
        EXTRACTED,
        /** Extraction is off, or this extension is not configured for it. Not a failure. */
        NOT_EXTRACTED,
        /** Bigger than {@code extract-max-size}. Reported: the operator chose a limit and hit it. */
        TOO_LARGE,
        /** The bytes are not on disk. Reported: metadata indexed for a file that is not there. */
        MISSING,
        /** Turing did not answer inside {@code extract-timeout}, or refused, or errored. */
        FAILED
    }

    /**
     * @param text the extracted text, or {@code null} for every outcome but {@link Outcome#EXTRACTED}
     * @param detail what to say in an incident, or {@code null} when there is nothing to report
     */
    public record Extraction(Outcome outcome, String text, String detail) {
        static Extraction of(String text) {
            return new Extraction(Outcome.EXTRACTED, text, null);
        }

        static Extraction no(Outcome outcome, String detail) {
            return new Extraction(outcome, null, detail);
        }

        public boolean hasText() {
            return outcome == Outcome.EXTRACTED && text != null && !text.isBlank();
        }
    }

    private static final Extraction OFF = Extraction.no(Outcome.NOT_EXTRACTED, null);

    private final ShTuringProperties properties;
    private final TurOcr ocr;

    /**
     * One daemon thread, so the wait below can be bounded without leaving the JVM anything to hold
     * on to at shutdown. Single rather than pooled for the same reason the index worker is: an
     * outage should not turn a queue of publishes into a queue of concurrent uploads at Turing.
     */
    private final ExecutorService calls = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sh-turing-extract");
        thread.setDaemon(true);
        return thread;
    });

    private final ShFileStorageService storage;

    /**
     * {@code @Autowired} because there are two constructors and Spring will not guess between them:
     * without it the container reports "No default constructor found" and every context in the
     * suite fails to start, several beans away from this file.
     */
    @Autowired
    public ShTuringTextExtractor(ShTuringProperties properties, ShFileStorageService storage) {
        this(properties, storage, new TurOcr());
    }

    /** The seam the tests use: a {@code TurOcr} that answers without a Turing. */
    ShTuringTextExtractor(ShTuringProperties properties, ShFileStorageService storage, TurOcr ocr) {
        this.properties = properties;
        this.storage = storage;
        this.ocr = ocr;
    }

    /**
     * The bytes behind a published attachment, or {@code null} when this post is not one — or is one
     * whose type nobody asked to extract, which is answered before the disk is touched.
     *
     * <p><b>Call this on a thread that has the tenant.</b> {@code ShFileStorageService.resolve}
     * reads it to pick the tenant's root, so resolving on the index worker — which has none until
     * {@code ShTenantHandoff} restores it — would look in the wrong place or the default one.</p>
     */
    public Path attachmentOf(ShPostUnified post) {
        if (post == null || post.getShPostType() == null
                || !ShSystemPostType.FILE.equals(post.getShPostType().getName())) {
            return null;
        }
        String fileName = post.getTitle();
        if (!handles(fileName)) {
            return null;
        }
        return storage.resolve(post.getId(), fileName);
    }

    /**
     * Puts Turing's reading of {@code file} into the item's {@code text}, and says what happened.
     *
     * <p>The one call both index paths share — the {@code AFTER_COMMIT} listener and SH413's
     * reindex, which reaches {@code indexItem}/{@code send} directly and would otherwise index an
     * already-published library by name only, which is the very defect this task is about. Each
     * caller decides what to do with a non-null {@link Extraction#detail()}; neither has to know
     * how extraction works.</p>
     */
    public Extraction enrich(TurSNJobItem item, Path file) {
        if (item == null || file == null) {
            return OFF;
        }
        Extraction extraction = extract(file, file.getFileName().toString());
        if (extraction.hasText()) {
            item.getAttributes().put(TurSNFieldName.TEXT, extraction.text());
        }
        return extraction;
    }

    /**
     * Whether this file's text would be asked for at all — cheap, and answered on the committing
     * thread so a file that is never extracted costs the index worker nothing.
     */
    public boolean handles(String fileName) {
        if (!properties.isExtractText() || fileName == null) {
            return false;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return properties.getExtractTypes().stream().anyMatch(extension::equalsIgnoreCase);
    }

    /**
     * Turing's reading of this file. <b>Never throws</b>, because the caller's next act is to index
     * the metadata and an attachment findable by name is strictly better than one not indexed at
     * all — which is what an exception escaping here would cost.
     */
    public Extraction extract(Path path, String fileName) {
        if (!handles(fileName) || path == null) {
            return OFF;
        }
        long size;
        try {
            if (!Files.isRegularFile(path)) {
                return Extraction.no(Outcome.MISSING, "the file's bytes are not on disk");
            }
            size = Files.size(path);
        } catch (java.io.IOException e) {
            // SH756 — describe(), not getMessage(): a FileSystemException's message is its two
            // paths and nothing else unless the platform supplied a reason, which is exactly the
            // failure nobody can reproduce from the incident.
            return Extraction.no(Outcome.MISSING, "the file could not be read: " + ShIoFailure.describe(e));
        }
        long max = properties.getExtractMaxSize();
        if (max > 0 && size > max) {
            return Extraction.no(Outcome.TOO_LARGE,
                    "the file is " + size + " bytes, over the " + max + " extraction limit");
        }
        return ask(path, size);
    }

    private Extraction ask(Path path, long size) {
        Future<TurFileAttributes> pending = calls.submit(() -> ocr.processFile(server(), path.toFile(), false));
        try {
            TurFileAttributes attributes =
                    pending.get(properties.getExtractTimeout().toMillis(), TimeUnit.MILLISECONDS);
            // TurOcr answers a blank TurFileAttributes on an IOException rather than throwing, so an
            // unreachable Turing arrives here as a null content and not as an error. Reported as a
            // failure, because "no text" and "we never asked" must not read the same in diagnostics.
            if (attributes == null || attributes.getContent() == null || attributes.getContent().isBlank()) {
                return Extraction.no(Outcome.FAILED, "Turing returned no text for this file");
            }
            return Extraction.of(attributes.getContent());
        } catch (TimeoutException e) {
            // The call is left running: TurOcr owns the socket and will give up at its own
            // five-minute mark. Cancelling the Future would not close the connection, and waiting
            // for it to is the stall this bound exists to prevent.
            pending.cancel(true);
            return Extraction.no(Outcome.FAILED, "Turing did not extract " + size
                    + " bytes within " + properties.getExtractTimeout());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Extraction.no(Outcome.FAILED, "interrupted while extracting");
        } catch (java.util.concurrent.ExecutionException e) {
            log.warn("Turing OCR errored for {}: {}", path.getFileName(), e.getCause().getMessage());
            return Extraction.no(Outcome.FAILED, "errored: " + e.getCause().getMessage());
        }
    }

    private TurServer server() {
        return new TurServer(URI.create(properties.getUrl()),
                new TurApiKeyCredentials(properties.getApiKey()));
    }
}
