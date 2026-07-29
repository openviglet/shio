/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.imports;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.exchange.ShExchangeException;
import com.viglet.shio.exchange.ShExchangePackageCodec;

/**
 * SH189 — an import failure that says which of the four things went wrong.
 *
 * <h2>What this replaces</h2>
 *
 * <p>{@code ShExchangeImportAPI} ended every failure the same way:</p>
 *
 * <pre>{@code
 * } catch (IOException | ShExchangeException e) {
 *     return ResponseEntity.badRequest().build();
 * }
 * }</pre>
 *
 * <p>{@code ShExchangeException} knew exactly what was wrong and the caller got a {@code 400} with
 * no body. Bringing the 2018 {@code openviglet/bootstrap-site} package forward therefore meant
 * reading the {@code ShExchange*} records in Java to infer seven field renames, because nothing in
 * the response distinguished a manifest-schema mismatch from a bad zip, a missing site or an IO
 * failure. Every other write surface here answers a bad request with {@code fix} / {@code allowed}
 * / {@code didYouMean} — in the same session a mistyped address came back with
 * {@code didYouMean: "bootstrap_nav_region"} unprompted. Import was the outlier, and it is the one
 * endpoint whose input a human assembled by hand and cannot inspect.</p>
 *
 * <h2>Why classification and not just the message</h2>
 *
 * <p>Passing the exception's text through would already have been most of the value, and it would
 * not have told the caller <em>what kind</em> of thing to go and look at. The four causes need four
 * different next actions: re-export the package, unzip it and read the manifest, check the zip
 * layout, or retry. So the slug is the classification and the {@code fix} is the action, which is
 * SH80's shape — and the underlying message is carried too, because it is the part that names the
 * actual field.</p>
 *
 * <h2>The classification is a {@code switch}, not a string match (SH267)</h2>
 *
 * <p>It used to read the message — {@code lower.contains("manifest")} and two more — because
 * {@code ShExchangeException} was a bare {@code RuntimeException} subclass with no kind and thirteen
 * throw sites. {@link com.viglet.shio.exchange.ShExchangeException.Kind} now carries what the throw
 * site knew, and this class {@code switch}es over it with <b>no {@code default}</b>: a new kind fails
 * the build here until somebody decides what a caller should do about it, which is exactly the case
 * that used to land silently in the catch-all.</p>
 *
 * <p>Three things changed when the guessing stopped, and all three were mis-classifications rather
 * than refactoring noise:</p>
 *
 * <ul>
 *   <li>A missing {@code export.json} ({@code MANIFEST_MISSING}, thrown as "Failed to read package
 *       from …") answered {@code import-failed}, because the message names neither "manifest" nor
 *       "zip". It is the manifest case.</li>
 *   <li>A truncated or non-zip upload ({@code ARCHIVE_UNREADABLE}, "Failed to read import package")
 *       answered {@code import-failed} for the same reason. It is the archive case.</li>
 *   <li>The manifest fix named <b>{@code shio-exchange.json}</b>, which is not a file this codebase
 *       writes or reads — the manifest is {@code export.json}
 *       ({@link com.viglet.shio.exchange.ShExchangePackageCodec#MANIFEST_ENTRY}, interpolated below
 *       so the two cannot drift again). P6 makes that a defect and not a typo: the instruction sent
 *       an author to unzip a package and look for a file that was never in it. The test asserted the
 *       wrong name too, which is how it survived SH189.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShImportFailedException extends ShTeachingException {

    private ShImportFailedException(ShProblem problem) {
        super(problem);
    }

    /** No file, or an empty one — the only failure that is the request's own shape. */
    public static ShImportFailedException noFile() {
        return new ShImportFailedException(ShProblem
                .badRequest("import-no-file", "No package was uploaded.")
                .withDetail("The request carried no `file` part, or the part was empty.")
                .withFix("POST a multipart request with a `file` part holding an SH68 exchange "
                        + "package (.zip). An export from GET /api/v2/site/{id}/export produces one.")
                .withExample("curl -u user:pass -F file=@site.zip http://localhost:2710/api/v2/import"));
    }

    /**
     * Classifies a failure from the exchange layer.
     *
     * @param cause    what was thrown
     * @param fileName the uploaded file's name, echoed because a caller who uploaded the wrong
     *                 file learns more from its name than from any diagnosis of its contents
     */
    public static ShImportFailedException of(Exception cause, String fileName) {
        String detail = detailOf(cause);
        String named = fileName == null || fileName.isBlank() ? "the uploaded package" : fileName;

        // Not an exchange failure at all — the multipart stream itself. There is no kind to read,
        // and the catch-all is the honest answer rather than a guess at what the IO meant.
        if (!(cause instanceof ShExchangeException exchange)) {
            return failed(named, detail);
        }
        return switch (exchange.kind()) {
            // The case that cost seven inferred renames. The cause's own message is what names the
            // offending field, so it is carried verbatim rather than summarized.
            case MANIFEST_UNREADABLE, MANIFEST_UNWRITABLE -> manifestSchemaDrift(named, detail);
            case MANIFEST_MISSING -> manifestMissing(named, detail);
            case ARCHIVE_UNREADABLE -> archiveUnreadable(named, detail);
            case ARCHIVE_UNSAFE -> archiveUnsafe(named, detail);
            // Export-side kinds. Unreachable from this endpoint — an import never writes a package —
            // and named rather than defaulted, so the day one of them becomes reachable is a decision
            // somebody makes here instead of a caller getting a shrug.
            case ARCHIVE_UNWRITABLE, POST_DATA_INVALID -> failed(named, detail);
            case NO_SITE -> noSite(named);
        };
    }

    private static ShImportFailedException manifestSchemaDrift(String named, String detail) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-manifest-unreadable", "The package manifest is unreadable.")
                .withDetail("The manifest in " + named + " could not be read: " + detail)
                .withFix("The manifest is `" + ShExchangePackageCodec.MANIFEST_ENTRY + "` at the root "
                        + "of the zip, and its schema is the SH68 record set. A package exported by "
                        + "an older version can be several field renames behind: unzip it, compare "
                        + "the manifest against a fresh export from GET /api/v2/site/{id}/export, "
                        + "and re-zip. The message above names the field that did not match."));
    }

    /**
     * A different next action from schema drift, which is why it is a separate kind: there is nothing
     * to compare, because there is no manifest to open.
     */
    private static ShImportFailedException manifestMissing(String named, String detail) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-manifest-unreadable", "The package manifest is unreadable.")
                .withDetail("No readable `" + ShExchangePackageCodec.MANIFEST_ENTRY + "` was found in "
                        + named + ": " + detail)
                .withFix("An exchange package carries `" + ShExchangePackageCodec.MANIFEST_ENTRY
                        + "` at its root, beside a `" + ShExchangePackageCodec.FILES_DIR + "/` "
                        + "directory. A zip of a zip, or one whose entries sit two directories deep, "
                        + "looks like this: re-zip from the directory that holds the manifest. An "
                        + "export from GET /api/v2/site/{id}/export has the right shape."));
    }

    private static ShImportFailedException archiveUnreadable(String named, String detail) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-package-unreadable", "The archive is unreadable.")
                .withDetail(named + " could not be read as an exchange package: " + detail)
                .withFix("An exchange package is a zip whose entries are relative paths. Check "
                        + "that the upload is a zip and not a tar or a bare directory, and that "
                        + "it was not truncated in transit."));
    }

    /**
     * A traversal attempt keeps SH189's slug — the archive is unusable either way, and that decision
     * is the caller's contract — but not its fix, which told the reader to check for a tar. Naming
     * the actual condition is free now that the kind says so.
     */
    private static ShImportFailedException archiveUnsafe(String named, String detail) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-package-unreadable", "The archive is unreadable.")
                .withDetail(named + " was refused: " + detail)
                .withFix("An entry in the zip resolves outside the extraction root, so the package "
                        + "was refused rather than unpacked. Entries must be relative paths under "
                        + "the package root — no leading `/` and no `..` segments. Re-zip from "
                        + "inside the package directory, or re-export the source site."));
    }

    private static ShImportFailedException noSite(String named) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-no-site", "The package holds no site.")
                .withDetail("The package " + named + " holds no site to import.")
                .withFix("A site package's manifest declares one site. This is usually a "
                        + "post-type-only or blueprint-shaped archive: apply post-types with "
                        + "PUT /api/v2/post-type/{name}, or content with POST /api/v2/agent/apply."));
    }

    private static ShImportFailedException failed(String named, String detail) {
        return new ShImportFailedException(ShProblem
                .badRequest("import-failed", "The import failed.")
                .withDetail("Importing " + named + " failed: " + detail)
                .withFix("The message above is the exchange layer's own. If it names a field, the "
                        + "manifest is out of date with this version's schema; if it names a file, "
                        + "the zip is incomplete. Re-exporting the source site produces a package "
                        + "this version reads."));
    }

    /**
     * The message a reader needs, which is often the <em>cause's</em> rather than the wrapper's.
     *
     * <p>{@code new ShExchangeException("Failed to parse exchange manifest", e)} carries the useful
     * half in {@code e} — Jackson names the property that did not map. Reporting only the wrapper
     * would reproduce the defect this class exists to fix, one level in.</p>
     */
    private static String detailOf(Exception cause) {
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        Throwable root = cause.getCause();
        if (root == null || root.getMessage() == null || root.getMessage().isBlank()) {
            return message;
        }
        return message + " — " + root.getMessage();
    }
}
