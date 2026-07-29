/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.site;

import java.util.Optional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.exchange.ShExchangeException;

/**
 * SH349 — the one export failure a caller can act on says so, and names the post.
 *
 * <h2>The defect</h2>
 *
 * <p>SH189 removed the bodiless 400 from {@code POST /api/v2/import} and nothing was done about the
 * direction the same exception travels the other way. {@code GET /api/v2/site/{id}/export} caught
 * nothing and {@code ShApiExceptionHandler} does not handle {@code ShExchangeException}, so an export
 * failure was a <b>500 with no slug</b> — SH189's defect on the surface whose caller is the console's
 * Download button.</p>
 *
 * <h2>Only one of the three kinds belongs to the caller, and that is the whole design</h2>
 *
 * <p>SH267 classified the exchange layer's throw sites, and three kinds are reachable only from the
 * export path. Two of them — a temp directory that could not be written, an archive that could not be
 * built — are the instance's own problem: a 4xx would be a lie and a {@code fix} would be advice
 * nobody can take. The third is not internal at all. {@code POST_DATA_INVALID} on export means
 * <b>one post's {@code data} column is not JSON</b>, which is a content defect with an address, an
 * owner and a remedy.</p>
 *
 * <p>So this classifier answers {@link Optional#empty()} for everything the caller cannot fix, and
 * the endpoint rethrows — deliberately, because that path is <em>already</em> diagnosable:
 * {@code ShAgentErrorRecordingFilter} is registered on {@code /api/*} and records any 5xx (and any
 * exception passing it) as an SH190 incident with {@code frames}. The question §XVI.65 asked — whether
 * these want an incident — was already answered by SH190 and needed no new code, which is worth more
 * than a decision.</p>
 *
 * <p><b>409, not 400.</b> The request is correct; the stored content is not. That is SH321's and
 * SH325's precedent (two posts on one URL, an unseeded widget) and it keeps "the caller sent something
 * wrong" meaning what it says.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShExportFailedException extends ShTeachingException {

    private ShExportFailedException(ShProblem problem) {
        super(problem);
    }

    /**
     * The teaching form of an exchange failure, when there is one.
     *
     * @param cause what the exchange layer threw
     * @return empty when the failure is the instance's rather than the caller's — the endpoint must
     *         then rethrow, so the response and the incident stay what SH190 already makes them
     */
    public static Optional<ShExportFailedException> forCaller(ShExchangeException cause) {
        return switch (cause.kind()) {
            case POST_DATA_INVALID -> Optional.of(postDataInvalid(cause));
            // The instance's own IO. Named rather than defaulted (SH267): if one of these ever becomes
            // the caller's fault, the decision is made here instead of a caller getting a shrug.
            case MANIFEST_UNWRITABLE, ARCHIVE_UNWRITABLE -> Optional.empty();
            // Import-side kinds. Unreachable here — an export reads no package — and the switch is
            // still total, which is what keeps that claim from rotting silently.
            case MANIFEST_UNREADABLE, MANIFEST_MISSING, ARCHIVE_UNREADABLE, ARCHIVE_UNSAFE, NO_SITE ->
                    Optional.empty();
        };
    }

    private static ShExportFailedException postDataInvalid(ShExchangeException cause) {
        String subject = cause.subject().orElse(null);
        String named = subject != null ? subject : "a post in this site";
        return new ShExportFailedException(ShProblem
                .conflict("export-post-data-invalid", "A post's data is not valid JSON.")
                .withDetail("The export stopped at " + named + ", whose stored `data` could not be "
                        + "read as JSON: " + rootMessage(cause))
                .withFix("A package carries each post's `data` as a JSON document, so a column that "
                        + "does not parse cannot be exported. Read the post with "
                        + "GET /api/v2/agent/read?address=" + (subject != null ? subject : "post:<site>/<url>")
                        + " and rewrite its fields with a post.upsert on POST /api/v2/agent/batch; "
                        + "the export succeeds once that one post round-trips.")
                .withAt(subject));
    }

    /**
     * The parser's own message, which names the offending character and offset.
     *
     * <p>The wrapper says "failed to parse post data JSON" and the cause says where — SH189's rule
     * that reporting only the wrapper reproduces the defect one level in.</p>
     */
    private static String rootMessage(ShExchangeException cause) {
        Throwable root = cause.getCause();
        if (root == null || root.getMessage() == null || root.getMessage().isBlank()) {
            return cause.getMessage();
        }
        return root.getMessage();
    }
}
