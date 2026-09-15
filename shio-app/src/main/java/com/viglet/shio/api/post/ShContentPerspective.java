/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.release.ShReleaseService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;

/**
 * Which version of a page a read is asking for: the {@code state=} grammar, parsed once (SH990).
 *
 * <h2>Why one type</h2>
 *
 * <p>Two read paths took a state and each parsed it itself. {@code /agent/read} refused an unknown
 * value with a teaching 400 and understood {@code rev:<n>}; {@code /preview} compared the text to
 * {@code PUBLISHED} and read anything else as the draft, so {@code state=live} previewed the draft
 * and said nothing. A third path would have made a third reading. The grammar is small enough that
 * the copies agree by accident, and that is the state in which one of them drifts.</p>
 *
 * <h2>The grammar</h2>
 *
 * <ul>
 *   <li>absent or blank — the path's own default, which is not the same everywhere: a read is
 *       draft-preferred, a preview is the draft;</li>
 *   <li>{@code draft} and {@code published}, in any case, since the console and older scripts send
 *       {@code DRAFT} and {@code PUBLISHED};</li>
 *   <li>{@code rev:<n>} — a captured revision (SH986), by the number its listing shows.</li>
 * </ul>
 *
 * <p>{@code release:<name>} is BA4's, added by the task that creates the thing it names and never
 * before: a grammar that accepts a word nothing resolves is a 200 for a read of nothing.</p>
 *
 * @param row      the post row asked for, or {@code null} for the path's default or a revision
 * @param revision the revision number asked for, or {@code null}
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShContentPerspective(ShPostState row, Integer revision, String release) {

    /** The two-field form, before SH993 gave the grammar a release. */
    public ShContentPerspective(ShPostState row, Integer revision) {
        this(row, revision, null);
    }

    /** No {@code state} was sent; the path decides. */
    public static final ShContentPerspective DEFAULT = new ShContentPerspective(null, null, null);

    private static final String REVISION_PREFIX = "rev:";

    /** SH993 — the page as the named release found it: the version its run replaced. */
    private static final String RELEASE_PREFIX = "release:";

    /** A revision number as its listing shows it: positive, and short enough not to overflow. */
    private static final Pattern REVISION_NUMBER = Pattern.compile("[1-9]\\d{0,8}");

    /** The three spellings a caller can send, as the refusal lists them. */
    private static final List<String> ALLOWED =
            List.of("draft", "published", "rev:<n>", "release:<name>");

    /**
     * Parses a {@code state=} value.
     *
     * @throws UnknownStateException for anything outside the grammar — P6, because a typo read as
     *         the default answers a question the caller did not ask, in the shape of a right answer
     */
    public static ShContentPerspective parse(String sent) {
        if (sent == null || sent.isBlank()) {
            return DEFAULT;
        }
        String text = sent.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith(REVISION_PREFIX)) {
            String number = text.substring(REVISION_PREFIX.length()).trim();
            // Checked before it is parsed rather than caught after: nine digits cannot overflow, and
            // a parse failure swallowed into a refusal would be a silent catch (SH270's inventory).
            if (REVISION_NUMBER.matcher(number).matches()) {
                return new ShContentPerspective(null, Integer.parseInt(number));
            }
            throw new UnknownStateException(sent);
        }
        if (text.startsWith(RELEASE_PREFIX)) {
            // The name is checked by the same rule that accepts one on a release address, so a
            // spelling that could never have been created is refused here rather than resolving to
            // nothing one layer down.
            return new ShContentPerspective(null, null,
                    ShReleaseService.requireName(sent.trim().substring(RELEASE_PREFIX.length())));
        }
        return switch (text) {
            case "draft" -> new ShContentPerspective(ShPostState.DRAFT, null);
            case "published" -> new ShContentPerspective(ShPostState.PUBLISHED, null);
            default -> throw new UnknownStateException(sent);
        };
    }

    /** Whether the caller named nothing, so the path's default applies. */
    public boolean isDefault() {
        return row == null && revision == null && release == null;
    }

    /** Whether the caller asked for a page as a release found it (SH993). */
    public boolean isRelease() {
        return release != null;
    }

    /** Whether the caller asked for a captured revision rather than a row. */
    public boolean isRevision() {
        return revision != null;
    }

    /** The row asked for, or {@code fallback} when the caller named none. */
    public ShPostState rowOr(ShPostState fallback) {
        return row != null ? row : fallback;
    }

    /**
     * This perspective, on a path that reads rows and does not yet read a revision.
     *
     * <p>Refused rather than quietly read as the draft, for the same reason an unknown word is: the
     * caller asked for what a page said at a number, and the draft is a different page.</p>
     *
     * @param path    the path being asked, as the refusal names it
     * @param instead the call that does read a revision today
     */
    public ShContentPerspective requireRow(String path, String instead) {
        if (isRevision()) {
            throw new RevisionNotServedException(path, "rev:" + revision, instead);
        }
        // SH993 — a release perspective is a version of a page, so a path that cannot answer a
        // revision cannot answer this either. Refused rather than read as the default, which would
        // answer the live page to a caller asking what the launch replaced.
        return requireVersion(path, instead);
    }

    /**
     * This perspective, on a path that serves a revision but not a release (SH993).
     *
     * <p>Every path that reads {@code state} has to decide about a word the grammar accepts, and the
     * failure of not deciding is silent: {@code release:} leaves {@code row} and {@code revision}
     * both null, which is exactly what "no state was sent" looks like.</p>
     */
    public ShContentPerspective requireVersion(String path, String instead) {
        if (isRelease()) {
            throw new RevisionNotServedException(path, RELEASE_PREFIX + release, instead);
        }
        return this;
    }

    /** A 400: {@code state=} took a value outside the grammar. */
    public static class UnknownStateException extends ShTeachingException {

        public UnknownStateException(String sent) {
            super(ShProblem.badRequest("unknown-state", "'" + sent + "' is not a post state")
                    .withDetail("A post has a draft that is edited, a published row that is served, "
                            + "and the revisions captured each time it went live. `state` picks "
                            + "which one to read.")
                    .withAllowed(ALLOWED)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use `state=published` for what is live, `state=draft` for the editable "
                            + "copy, `state=rev:<n>` for a revision by its number, or omit `state` "
                            + "for this path's default.")
                    .withExample("state=published"));
        }
    }

    /** A 400: a revision was asked of a path that reads rows only. */
    public static class RevisionNotServedException extends ShTeachingException {

        public RevisionNotServedException(String path, String spelling, String instead) {
            super(ShProblem.badRequest("state-revision-not-served",
                            "'" + spelling + "' is not served on " + path)
                    .withDetail(path + " reads the draft or the published row. Answering a "
                            + "revision with one of them would show a different page than the one "
                            + "asked for.")
                    .withAllowed(List.of("draft", "published"))
                    .withFix("Read the revision with " + instead + ", or ask this path for "
                            + "`state=draft` or `state=published`.")
                    .withExample(instead));
        }
    }
}
