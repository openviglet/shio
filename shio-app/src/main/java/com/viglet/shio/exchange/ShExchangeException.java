/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange;

/**
 * Unchecked failure while reading or writing a content exchange package (SH68):
 * a malformed manifest, an I/O error, or a corrupt / unsafe zip.
 *
 * <h2>Why it carries a kind (SH267)</h2>
 *
 * <p>SH189 gave the import endpoint teaching errors, and classified them by testing this
 * exception's <em>message</em> for {@code manifest} / {@code zip} / {@code no site} — because there
 * was one exception type for thirteen throw sites and no kind. That works and it is the shape this
 * codebase refuses everywhere else: which failure this is, is known at the {@code throw} and was
 * discarded there. Two consequences followed. A message reworded for clarity silently changed an
 * HTTP response, and a new failure mode added to the codec landed in the caller's catch-all with
 * nothing to say anybody should look at it.</p>
 *
 * <p>So the kind is <b>mandatory</b> — there is no message-only constructor. That is the guard, and
 * it is the compiler's rather than a lint's: a new throw site cannot omit the classification, and
 * {@code ShImportFailedException}'s {@code switch} over {@link Kind} has no {@code default}, so a
 * new kind fails the build until the endpoint says what a caller should do about it. The messages
 * are presentational again.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
public class ShExchangeException extends RuntimeException {

    /**
     * Which of the exchange layer's failures this is.
     *
     * <p>Deliberately about the <em>package</em> and not about the HTTP response: the mapping from a
     * kind to a slug and a next action belongs to the endpoint that answers, which is
     * {@code ShImportFailedException}. Several kinds are only reachable on the <b>export</b> path
     * and can therefore never produce an import response — they exist because the throw site knows
     * them, which is the whole point, and the endpoint's {@code switch} still has to be total.</p>
     */
    public enum Kind {

        /** {@code export.json} was found and did not parse — schema drift, the cause names the field. */
        MANIFEST_UNREADABLE,

        /** No {@code export.json} at the package root — the archive is not shaped like a package. */
        MANIFEST_MISSING,

        /** The manifest could not be serialized or written to disk. Export-side. */
        MANIFEST_UNWRITABLE,

        /** The zip could not be opened, unzipped or streamed — truncated, or not a zip. */
        ARCHIVE_UNREADABLE,

        /** A zip entry resolved outside the extraction root (zip-slip). A refusal, not a defect. */
        ARCHIVE_UNSAFE,

        /** The export archive could not be built. Export-side. */
        ARCHIVE_UNWRITABLE,

        /** The package parsed and declares no site to import. */
        NO_SITE,

        /** A post's {@code data} JSON could not be read or written while mapping. */
        POST_DATA_INVALID
    }

    private final Kind kind;

    public ShExchangeException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = requireKind(kind);
    }

    public ShExchangeException(Kind kind, String message) {
        super(message);
        this.kind = requireKind(kind);
    }

    /** What kind of failure this is — never {@code null}. */
    public Kind kind() {
        return kind;
    }

    private static Kind requireKind(Kind kind) {
        if (kind == null) {
            throw new IllegalArgumentException("An exchange failure must name its kind (SH267)");
        }
        return kind;
    }
}
