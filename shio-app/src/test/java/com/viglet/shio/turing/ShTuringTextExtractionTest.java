/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * SH913 — a published PDF was indexed by title only, so its text answered no search.
 *
 * <h2>Turing extracts, Shio asks — and that is what is asserted</h2>
 *
 * <p>The design that did <em>not</em> ship was a local Tika parse, which would have been a fork:
 * Turing already exposes {@code POST /api/ocr/file} and the SDK Shio indexes through already
 * carries {@code TurOcr}. So the unit under test is the <b>bargain around</b> that call — what is
 * asked for, what is not, and what happens to the document when the answer never comes. The OCR
 * itself is Turing's, and asserting it here would be asserting somebody else's parser.</p>
 *
 * <h2>The property that matters most is the failure one</h2>
 *
 * <p>Every outcome but success must still index the metadata. An attachment findable by name is
 * worth a great deal more than an attachment not indexed at all, so a corrupt file, a dead Turing
 * or a file over the limit degrades to exactly what SH84 already did — never to a lost document,
 * and never to an exception one line before the send.</p>
 *
 * @author Alexandre Oliveira
 */
class ShTuringTextExtractionTest {

    /** A {@link TurOcr} that answers from the test rather than from a Turing. */
    private static final class Ocr extends TurOcr {
        private final AtomicInteger calls = new AtomicInteger();
        private final String content;
        private final RuntimeException throwing;
        private final long delayMillis;

        Ocr(String content, RuntimeException throwing, long delayMillis) {
            this.content = content;
            this.throwing = throwing;
            this.delayMillis = delayMillis;
        }

        @Override
        public TurFileAttributes processFile(TurServer server, File file, boolean showOutput) {
            calls.incrementAndGet();
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (throwing != null) {
                throw throwing;
            }
            TurFileAttributes attributes = new TurFileAttributes();
            attributes.setContent(content);
            return attributes;
        }
    }

    private static ShTuringProperties properties() {
        ShTuringProperties properties = new ShTuringProperties();
        properties.setEnabled(true);
        properties.setUrl("http://localhost:2700");
        properties.setDefaultSite("Sample");
        properties.setExtractTimeout(Duration.ofSeconds(5));
        return properties;
    }

    private static TurSNJobItem item() {
        return new TurSNJobItem(TurSNJobAction.CREATE, List.of("Sample"), Locale.ENGLISH,
                new java.util.HashMap<>(Map.of(TurSNFieldName.ID, "post-1",
                        TurSNFieldName.TITLE, "rules.pdf")));
    }

    private static Path filed(Path dir, String name, byte[] bytes) throws Exception {
        Path path = dir.resolve(name);
        Files.write(path, bytes);
        return path;
    }

    @Test
    void aPublishedPdfArrivesWithItsBodyTextInTheTextField(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        Ocr ocr = new Ocr("Fees for 2026 are due in March.", null, 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        extractor.enrich(item, filed(dir, "rules.pdf", "%PDF-1.4 ...".getBytes()));

        assertThat(item.getAttributes().get(TurSNFieldName.TEXT))
                .as("SH913: the whole point — the document's body answers a search, not just its name")
                .isEqualTo("Fees for 2026 are due in March.");
        assertThat(ocr.calls.get()).isEqualTo(1);
    }

    @Test
    void anUnconfiguredExtensionIsNeverSentAndCostsNoCall(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        Ocr ocr = new Ocr("should not be asked", null, 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item, filed(dir, "logo.png", new byte[] {1, 2, 3}));

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.NOT_EXTRACTED);
        assertThat(extraction.detail())
                .as("SH913: a type nobody asked to extract is configuration, not a degradation — "
                        + "reporting it would fill the window with every image on the site")
                .isNull();
        assertThat(item.getAttributes()).doesNotContainKey(TurSNFieldName.TEXT);
        assertThat(ocr.calls.get()).isZero();
    }

    @Test
    void extractionOffLeavesTheDocumentExactlyAsSh84BuiltIt(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        properties.setExtractText(false);
        Ocr ocr = new Ocr("should not be asked", null, 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        extractor.enrich(item, filed(dir, "rules.pdf", "%PDF".getBytes()));

        assertThat(item.getAttributes()).doesNotContainKey(TurSNFieldName.TEXT);
        assertThat(ocr.calls.get()).isZero();
    }

    @Test
    void aFileOverTheLimitIsReportedAndStillIndexedByItsMetadata(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        properties.setExtractMaxSize(8);
        Ocr ocr = new Ocr("never read", null, 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item, filed(dir, "big.pdf", new byte[64]));

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.TOO_LARGE);
        assertThat(extraction.detail())
                .as("SH913: the operator set the limit, so the incident names both numbers")
                .contains("64").contains("8");
        assertThat(item.getAttributes())
                .as("SH913: skipped with an incident, never an exception, and the metadata still goes")
                .containsKey(TurSNFieldName.ID)
                .doesNotContainKey(TurSNFieldName.TEXT);
        assertThat(ocr.calls.get()).as("and the bytes are never uploaded").isZero();
    }

    @Test
    void bytesThatAreNotOnDiskAreReportedRatherThanThrown(@TempDir Path dir) {
        ShTuringProperties properties = properties();
        ShTuringTextExtractor extractor =
                new ShTuringTextExtractor(properties, null, new Ocr("x", null, 0));

        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item(), dir.resolve("gone.pdf"));

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.MISSING);
        assertThat(extraction.detail()).isNotBlank();
    }

    @Test
    void aCorruptFileDegradesQuietlyAndTheDocumentIsStillIndexed(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        // What Turing answers for bytes it cannot read: TurOcr swallows its own IOException and
        // returns a blank TurFileAttributes, so "no text" arrives as a null content, not an error.
        Ocr ocr = new Ocr(null, null, 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item, filed(dir, "corrupt.pdf", "not really a pdf".getBytes()));

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.FAILED);
        assertThat(item.getAttributes())
                .as("SH913: a corrupt attachment stays findable by name — an extraction failure "
                        + "must never cost the publish its document")
                .containsKey(TurSNFieldName.ID)
                .doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void aThrowingTuringIsCaughtSoTheSendStillHappens(@TempDir Path dir) throws Exception {
        ShTuringProperties properties = properties();
        Ocr ocr = new Ocr(null, new IllegalStateException("connection refused"), 0);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        TurSNJobItem item = item();
        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item, filed(dir, "rules.pdf", "%PDF".getBytes()));

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.FAILED);
        assertThat(extraction.detail()).contains("connection refused");
        assertThat(item.getAttributes()).doesNotContainKey(TurSNFieldName.TEXT);
    }

    @Test
    void aSlowTuringIsAbandonedOnShiosOwnBoundRatherThanTheSdksFiveMinutes(@TempDir Path dir)
            throws Exception {
        ShTuringProperties properties = properties();
        properties.setExtractTimeout(Duration.ofMillis(120));
        Ocr ocr = new Ocr("too late", null, 3000);
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, ocr);

        long before = System.nanoTime();
        TurSNJobItem item = item();
        ShTuringTextExtractor.Extraction extraction =
                extractor.enrich(item, filed(dir, "slow.pdf", "%PDF".getBytes()));
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;

        assertThat(extraction.outcome()).isEqualTo(ShTuringTextExtractor.Outcome.FAILED);
        assertThat(item.getAttributes()).doesNotContainKey(TurSNFieldName.TEXT);
        // The bound is the whole reason this runs on a worker of its own: TurOcr hardcodes a
        // five-minute socket timeout, and the single index worker cannot be held for five minutes
        // without stalling every queued publish behind one large PDF.
        assertThat(elapsedMillis)
                .as("SH913: gave up after %d ms against a 120 ms bound — the wait is not bounded, "
                        + "and one slow file can hold the index queue", elapsedMillis)
                .isLessThan(2500);
    }

    @Test
    void handlesReadsTheConfiguredTypesAndNothingElse() {
        ShTuringProperties properties = properties();
        ShTuringTextExtractor extractor = new ShTuringTextExtractor(properties, null, new Ocr("x", null, 0));

        assertThat(extractor.handles("rules.pdf")).isTrue();
        assertThat(extractor.handles("RULES.PDF")).as("an extension is not case").isTrue();
        assertThat(extractor.handles("fees.docx")).isTrue();
        assertThat(extractor.handles("logo.png")).isFalse();
        assertThat(extractor.handles("noextension")).isFalse();
        assertThat(extractor.handles("trailing.")).isFalse();
        assertThat(extractor.handles(null)).isFalse();

        properties.setExtractTypes(new java.util.ArrayList<>(List.of("txt")));
        assertThat(extractor.handles("rules.pdf"))
                .as("SH913: which types are extracted is configuration, so narrowing it takes effect")
                .isFalse();
        assertThat(extractor.handles("notes.txt")).isTrue();
    }
}
