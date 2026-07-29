/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.imports;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.ShExchangeException.Kind;
import com.viglet.shio.exchange.ShExchangePackageCodec;

/**
 * SH189 — an import failure names which of the four things went wrong.
 * SH267 — and it names it from the exception's <em>kind</em>, not from its prose.
 *
 * <p>The defect: {@code ShExchangeImportAPI} answered {@code badRequest().build()} for every
 * failure, so a caller could not tell a manifest-schema mismatch from a bad zip from a package with
 * no site in it — while {@code ShExchangeException} knew exactly which. Bringing the 2018
 * {@code openviglet/bootstrap-site} package forward meant reading the {@code ShExchange*} records in
 * Java to infer seven field renames as a result, which is the cost this removes.</p>
 *
 * <p>SH267's half is the two tests at the bottom, and they are the point of that task rather than
 * its bookkeeping: <b>a reworded message no longer changes an HTTP response</b>, and every kind is
 * classified — including the ones only the export path throws, which is where the old classifier's
 * catch-all silently absorbed anything new.</p>
 *
 * <p>No Spring context: the classification is a pure function of the exception, which is what makes
 * it cheap enough to assert every branch and the message-carrying behaviour.</p>
 *
 * @author Alexandre Oliveira
 */
class ShImportFailedExceptionTest {

    private static String fixOf(ShImportFailedException thrown) {
        Object fix = thrown.toProblemDetail().getProperties() == null
                ? null : thrown.toProblemDetail().getProperties().get("fix");
        return fix == null ? "" : fix.toString();
    }

    private static String exampleOf(ShImportFailedException thrown) {
        Object example = thrown.toProblemDetail().getProperties() == null
                ? null : thrown.toProblemDetail().getProperties().get("example");
        return example == null ? "" : example.toString();
    }

    @Test
    @DisplayName("a manifest failure carries the cause's own message, which names the field")
    void aManifestFailureNamesTheField() {
        ShImportFailedException thrown = ShImportFailedException.of(
                new ShExchangeException(Kind.MANIFEST_UNREADABLE, "Failed to parse exchange manifest",
                        new IllegalArgumentException("Unrecognized property \"siteName\"")),
                "bootstrap-site.zip");

        assertThat(thrown.problem().slug()).isEqualTo("import-manifest-unreadable");
        assertThat(thrown.toProblemDetail().getDetail())
                .as("""
                        The wrapper says "failed to parse"; the *cause* names the property that did \
                        not map. Reporting only the wrapper would reproduce this very defect one \
                        level in — which is why the detail is built from both.""")
                .contains("bootstrap-site.zip")
                .contains("siteName");
        assertThat(fixOf(thrown))
                .as("P6 — the action, not just the diagnosis: this is the schema-drift case, and "
                        + "the fix is to compare against a fresh export")
                .contains("export.json")
                .contains("export");
    }

    @Test
    @DisplayName("the manifest fix names the file the package actually carries")
    void theManifestFixNamesTheRealFile() {
        String fix = fixOf(ShImportFailedException.of(
                new ShExchangeException(Kind.MANIFEST_UNREADABLE, "Failed to parse exchange manifest"),
                "x.zip"));

        assertThat(fix)
                .as("""
                        SH267: this said `shio-exchange.json`, which this codebase neither writes nor \
                        reads — so the instruction sent an author to unzip a package and look for a \
                        file that was never in it. P6 makes a wrong instruction a defect, and the \
                        SH189 test asserted the wrong name too, which is how it survived.""")
                .contains(ShExchangePackageCodec.MANIFEST_ENTRY)
                .doesNotContain("shio-exchange.json");
    }

    @Test
    @DisplayName("a bad archive, a package with no site and an unknown failure are each their own kind")
    void theFourKindsAreDistinguished() {
        assertThat(ShImportFailedException.of(
                        new ShExchangeException(Kind.ARCHIVE_UNREADABLE, "Failed to unzip package",
                                new IOException("EOF")), "x.zip")
                .problem().slug())
                .isEqualTo("import-package-unreadable");
        assertThat(ShImportFailedException.of(
                        new ShExchangeException(Kind.ARCHIVE_UNSAFE,
                                "Zip entry escapes target directory: ../evil"), "x.zip")
                .problem().slug())
                .as("a traversal attempt is an unreadable archive, not a schema problem")
                .isEqualTo("import-package-unreadable");
        assertThat(ShImportFailedException.of(
                        new ShExchangeException(Kind.NO_SITE, "Package contains no site to import"),
                        "types-only.zip")
                .problem().slug())
                .isEqualTo("import-no-site");
        assertThat(ShImportFailedException.of(new IOException("connection reset"), "x.zip")
                .problem().slug())
                .as("""
                        the catch-all still carries the message and a fix, because "something else \
                        went wrong" with no detail is the bodiless 400 this replaces.""")
                .isEqualTo("import-failed");
    }

    @Test
    @DisplayName("a traversal refusal is told what is wrong with the entry, not to check for a tar")
    void aTraversalRefusalNamesTheEntry() {
        String fix = fixOf(ShImportFailedException.of(
                new ShExchangeException(Kind.ARCHIVE_UNSAFE,
                        "Zip entry escapes target directory: ../evil"), "x.zip"));

        assertThat(fix)
                .as("SH189 kept one slug for both archive failures, which is right — the archive is "
                        + "unusable either way — and gave both the same fix, which is not: this one "
                        + "was refused deliberately, and 'check it is a zip and not a tar' is the "
                        + "wrong next action. The kind makes the distinction free.")
                .contains("outside the extraction root")
                .contains("..")
                .doesNotContain("tar");
    }

    @Test
    @DisplayName("a package with no manifest is told where the manifest belongs")
    void aMissingManifestSaysWhereItBelongs() {
        ShImportFailedException thrown = ShImportFailedException.of(
                new ShExchangeException(Kind.MANIFEST_MISSING, "Failed to read package from /tmp/x",
                        new IOException("/tmp/x/export.json")),
                "nested.zip");

        assertThat(thrown.problem().slug())
                .as("""
                        SH267 corrected this: the message names neither "manifest" nor "zip", so the \
                        string classifier answered import-failed for the commonest hand-zipped \
                        mistake there is.""")
                .isEqualTo("import-manifest-unreadable");
        assertThat(fixOf(thrown))
                .as("there is nothing to compare against a fresh export, because there is no "
                        + "manifest to open — the action is to re-zip from the right directory")
                .contains(ShExchangePackageCodec.FILES_DIR)
                .contains("re-zip");
    }

    @Test
    @DisplayName("a package with no site is pointed at the two surfaces that do accept it")
    void noSiteSuggestsTheAlternatives() {
        ShImportFailedException thrown = ShImportFailedException.of(
                new ShExchangeException(Kind.NO_SITE, "Package contains no site to import"),
                "types.zip");

        assertThat(fixOf(thrown))
                .as("the likely truth is that this is a post-type-only or blueprint-shaped archive, "
                        + "and both have a home — naming them is cheaper than a second attempt")
                .contains("/api/v2/post-type/")
                .contains("/api/v2/agent/apply");
    }

    @Test
    @DisplayName("no file at all is the request's own shape, and gets a runnable example")
    void noFileIsItsOwnKind() {
        ShImportFailedException thrown = ShImportFailedException.noFile();

        assertThat(thrown.problem().slug()).isEqualTo("import-no-file");
        assertThat(exampleOf(thrown))
                .as("SH230's rule — an example is a call the instance would actually accept")
                .contains("-F file=@");
    }

    @Test
    @DisplayName("a cause with no message does not produce a detail of the word null")
    void aMessagelessCauseStillReads() {
        assertThat(ShImportFailedException.of(new IOException(), "x.zip")
                .toProblemDetail().getDetail())
                .doesNotContain("null")
                .contains("IOException");
    }

    @Test
    @DisplayName("an unnamed upload still reads as a sentence")
    void anUnnamedUploadStillReads() {
        assertThat(ShImportFailedException.of(
                        new ShExchangeException(Kind.NO_SITE, "Package contains no site to import"),
                        null)
                .toProblemDetail().getDetail())
                .as("a multipart part without a filename is legal, and `The package null holds` is "
                        + "the kind of detail that makes a reader distrust the rest of the message")
                .contains("the uploaded package")
                .doesNotContain("null");
    }

    // ------------------------------------------------------------------
    // SH267 — the two properties the kind exists for
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("SH267: rewording a message cannot change the response")
    void rewordingAMessageCannotChangeTheResponse(Kind kind) {
        // Every one of these messages is a lie about its kind, and each would have flipped the old
        // classifier: "manifest" and "zip" and "no site" all appear, in a NO_SITE / MANIFEST_* /
        // ARCHIVE_* exception indiscriminately. That is the defect, expressed as a test.
        String misleading = "manifest zip no site — a message somebody reworded for the log";

        ShImportFailedException fromRealMessage = ShImportFailedException.of(
                new ShExchangeException(kind, "the original wording"), "x.zip");
        ShImportFailedException fromReworded = ShImportFailedException.of(
                new ShExchangeException(kind, misleading), "x.zip");

        assertThat(fromReworded.problem().slug())
                .as("the slug is the kind's, and the message is presentational — %s", kind)
                .isEqualTo(fromRealMessage.problem().slug());
        assertThat(fixOf(fromReworded))
                .as("so is the next action")
                .isEqualTo(fixOf(fromRealMessage));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("SH267: every kind is classified and carries an action")
    void everyKindIsClassifiedAndCarriesAnAction(Kind kind) {
        ShImportFailedException thrown = ShImportFailedException.of(
                new ShExchangeException(kind, "a failure of kind " + kind), "x.zip");

        assertThat(thrown.problem().slug())
                .as("""
                        The switch in ShImportFailedException has no default, so a new kind cannot \
                        reach a caller unclassified — this asserts the other half, that the mapping \
                        it is forced to write actually produces a teaching answer. Includes the \
                        export-side kinds, which is where a new failure mode used to land silently.""")
                .startsWith("import-");
        assertThat(fixOf(thrown)).as("P6 — every 4xx carries a fix: %s", kind).isNotBlank();
        assertThat(thrown.toProblemDetail().getDetail())
                .as("and names the upload, because a caller who sent the wrong file learns most "
                        + "from its name: %s", kind)
                .contains("x.zip");
    }

    @Test
    @DisplayName("SH267: an exchange failure cannot be thrown without a kind")
    void anExchangeFailureCannotBeThrownWithoutAKind() {
        // The guard is the compiler's — there is no message-only constructor — and this covers the
        // one hole a compiler cannot: an explicit null at a call site.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                        () -> new ShExchangeException(null, "no kind")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }
}
