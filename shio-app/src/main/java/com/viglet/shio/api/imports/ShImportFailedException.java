/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.imports;

import java.util.Locale;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

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
 * <p>The classification reads the message rather than the exception type, because there is one
 * exception type for all of them ({@code ShExchangeException}, a bare {@code RuntimeException}
 * subclass with no kind). Splitting that type would be the better fix and is a wider change than
 * this endpoint — it is filed as <b>SH267</b>, and the tell that it is worth doing is that this
 * classifier is string-matching messages written for a log.</p>
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
        String message = cause.getMessage() == null ? "" : cause.getMessage();
        String detail = detailOf(cause);
        String named = fileName == null || fileName.isBlank() ? "the uploaded package" : fileName;
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("manifest")) {
            // The case that cost seven inferred renames. The cause's own message is what names the
            // offending field, so it is carried verbatim rather than summarized.
            return new ShImportFailedException(ShProblem
                    .badRequest("import-manifest-unreadable", "The package manifest is unreadable.")
                    .withDetail("The manifest in " + named + " could not be read: " + detail)
                    .withFix("The manifest is `shio-exchange.json` inside the zip, and its schema is "
                            + "the SH68 record set. A package exported by an older version can be "
                            + "several field renames behind: unzip it, compare the manifest against "
                            + "a fresh export from GET /api/v2/site/{id}/export, and re-zip. The "
                            + "message above names the field that did not match."));
        }
        if (lower.contains("zip") || lower.contains("escapes")) {
            return new ShImportFailedException(ShProblem
                    .badRequest("import-package-unreadable", "The archive is unreadable.")
                    .withDetail(named + " could not be read as an exchange package: " + detail)
                    .withFix("An exchange package is a zip whose entries are relative paths. Check "
                            + "that the upload is a zip and not a tar or a bare directory, and that "
                            + "it was not truncated in transit."));
        }
        if (lower.contains("no site")) {
            return new ShImportFailedException(ShProblem
                    .badRequest("import-no-site", "The package holds no site.")
                    .withDetail("The package " + named + " holds no site to import.")
                    .withFix("A site package's manifest declares one site. This is usually a "
                            + "post-type-only or blueprint-shaped archive: apply post-types with "
                            + "PUT /api/v2/post-type/{name}, or content with POST /api/v2/agent/apply."));
        }
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
