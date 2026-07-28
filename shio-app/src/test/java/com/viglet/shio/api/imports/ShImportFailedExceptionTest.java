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

import com.viglet.shio.exchange.ShExchangeException;

/**
 * SH189 — an import failure names which of the four things went wrong.
 *
 * <p>The defect: {@code ShExchangeImportAPI} answered {@code badRequest().build()} for every
 * failure, so a caller could not tell a manifest-schema mismatch from a bad zip from a package with
 * no site in it — while {@code ShExchangeException} knew exactly which. Bringing the 2018
 * {@code openviglet/bootstrap-site} package forward meant reading the {@code ShExchange*} records in
 * Java to infer seven field renames as a result, which is the cost this removes.</p>
 *
 * <p>No Spring context: the classification is a pure function of the exception, which is what makes
 * it cheap enough to assert all four branches and the message-carrying behaviour.</p>
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
                new ShExchangeException("Failed to parse exchange manifest",
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
                .contains("shio-exchange.json")
                .contains("export");
    }

    @Test
    @DisplayName("a bad archive, a package with no site and an unknown failure are each their own kind")
    void theFourKindsAreDistinguished() {
        assertThat(ShImportFailedException.of(
                        new ShExchangeException("Failed to unzip package", new IOException("EOF")), "x.zip")
                .problem().slug())
                .isEqualTo("import-package-unreadable");
        assertThat(ShImportFailedException.of(
                        new ShExchangeException("Zip entry escapes target directory: ../evil"), "x.zip")
                .problem().slug())
                .as("a traversal attempt is an unreadable archive, not a schema problem")
                .isEqualTo("import-package-unreadable");
        assertThat(ShImportFailedException.of(
                        new ShExchangeException("Package contains no site to import"), "types-only.zip")
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
    @DisplayName("a package with no site is pointed at the two surfaces that do accept it")
    void noSiteSuggestsTheAlternatives() {
        ShImportFailedException thrown = ShImportFailedException.of(
                new ShExchangeException("Package contains no site to import"), "types.zip");

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
                        new ShExchangeException("Package contains no site to import"), null)
                .toProblemDetail().getDetail())
                .as("a multipart part without a filename is legal, and `The package null holds` is "
                        + "the kind of detail that makes a reader distrust the rest of the message")
                .contains("the uploaded package")
                .doesNotContain("null");
    }
}
