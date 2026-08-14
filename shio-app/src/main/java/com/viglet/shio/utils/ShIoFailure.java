/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.utils;

import java.io.IOException;
import java.nio.file.FileSystemException;

/**
 * What an {@link IOException} actually said, for a message a person has to act on (SH756).
 *
 * <h2>The defect this exists for</h2>
 *
 * <p>{@code ShMarketplaceInstallerTest} went red on one {@code clean test} and green on the next,
 * minutes apart on the same tree, and the whole of what the failure said was:</p>
 *
 * <pre>
 * InstallFailedException: …\shio-marketplace-1005…\shio-blueprint-newsroom
 *   -&gt; …\junit-7770…\shio-blueprint-newsroom
 * </pre>
 *
 * <p>Two paths and nothing else — because the code passed {@code failed.getMessage()}, and a
 * {@link FileSystemException}'s message <b>is</b> the two paths. Worse than it looks: that message
 * appends {@code ": " + reason} when there is one, so a message showing only paths is a message
 * whose reason was <b>null</b>. At that point the single remaining fact about the failure is the
 * exception's <em>class</em> — {@code FileAlreadyExistsException} and
 * {@code AtomicMoveNotSupportedException} are different diagnoses with the same text — and the
 * class was the thing being discarded.</p>
 *
 * <h2>P6, turned inward</h2>
 *
 * <p>Errors are instructions, and the reader of this one is whoever is holding a red gate at the
 * moment they can least afford a guess. SH354 records the same shape costing a run nobody could
 * ever diagnose. So the class name is always carried, the {@code reason} is carried where the
 * platform supplied one, and the paths follow — in that order, because the first two are what
 * decide and the third is only where.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShIoFailure {

    private ShIoFailure() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * A one-line description that names the failure before locating it.
     *
     * <p>{@code FileAlreadyExistsException: a\b -> c\d} rather than {@code a\b -> c\d}, and
     * {@code AccessDeniedException: The process cannot access the file… (a\b)} where the platform
     * gave a reason. Never {@code null} and never empty: a caller substituting this into a
     * {@code detail} must not have to guard it, since the guard is the branch that gets forgotten
     * on the path nobody tests.</p>
     */
    public static String describe(IOException failed) {
        if (failed == null) {
            return "an I/O failure with no exception";
        }
        String kind = failed.getClass().getSimpleName();
        String reason = failed instanceof FileSystemException fs ? fs.getReason() : null;
        String message = failed.getMessage();
        StringBuilder out = new StringBuilder(kind);
        if (reason != null && !reason.isBlank()) {
            out.append(": ").append(reason.trim());
            if (message != null && !message.isBlank()) {
                // The paths, in brackets, because the reason is the diagnosis and the paths are
                // where it happened — and a message that already ends in the reason must not
                // repeat it.
                out.append(" (").append(withoutReason(message, reason)).append(')');
            }
            return out.toString();
        }
        if (message != null && !message.isBlank()) {
            out.append(": ").append(message.trim());
        }
        return out.toString();
    }

    /**
     * {@code FileSystemException.getMessage()} is {@code file -> other: reason}, so printing the
     * message beside the reason would say it twice. Trimmed by suffix rather than by parsing,
     * because the separator is the platform's and this only needs to be right when it matches.
     */
    private static String withoutReason(String message, String reason) {
        String suffix = ": " + reason;
        String trimmed = message.trim();
        return trimmed.endsWith(suffix)
                ? trimmed.substring(0, trimmed.length() - suffix.length())
                : trimmed;
    }
}
