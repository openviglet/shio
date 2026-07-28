/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.Serial;

import com.github.jknack.handlebars.HandlebarsError;
import com.github.jknack.handlebars.HandlebarsException;

/**
 * A template that would not compile or would not run, reported the way FSP's
 * {@code regionError} reported one in 2019: <b>where</b>, not just <b>what</b>.
 *
 * <p>The old engine's error output was better than most template engines ship — file,
 * line, column, a five-line window and a caret under the offending character — and it is
 * exactly the shape law P6 asks for. Handlebars.java carries the same coordinates on
 * {@link HandlebarsError}, so keeping them costs nothing and is the difference between
 * "the page is blank" and "line 14, column 22, `{{#each` was never closed".</p>
 *
 * <p>Carrying the position is also what SH192 needs to refuse a bad template <em>at write
 * time</em> rather than at view time. This type is the shared vocabulary for that: the
 * renderer throws it, and a lint rule can report it without re-deriving anything.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public class ShTemplateException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The region or layout the failure is in, when the caller named one. */
    private final transient String source;
    private final int line;
    private final int column;

    public ShTemplateException(String message, String source, int line, int column, Throwable cause) {
        super(message, cause);
        this.source = source;
        this.line = line;
        this.column = column;
    }

    /**
     * Lifts Handlebars.java's own coordinates onto this type, losing nothing that helps.
     *
     * <p>The synthetic {@code inline@<hash>} filename is dropped. A template is compiled
     * from a post's field rather than from a file, so that name identifies nothing an
     * author can open — and printing it in front of a real line and column reads as though
     * the position belongs to some file they should go and find. The caller names the
     * source; the engine only knows where in it.</p>
     */
    static ShTemplateException of(HandlebarsException e) {
        HandlebarsError error = e.getError();
        if (error == null) {
            return new ShTemplateException(e.getMessage(), null, 0, 0, e);
        }
        String filename = error.filename == null || error.filename.startsWith("inline@")
                ? null
                : error.filename;
        return new ShTemplateException(error.reason == null ? error.message : error.reason,
                filename, error.line, error.column, e);
    }

    public String getSource() {
        return source;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    /**
     * @return the failure as one line an author can act on — the position first, because
     *         "unclosed block" without it means reading the whole template again.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (source != null && !source.isBlank()) {
            sb.append(source);
            if (line > 0) {
                sb.append(':').append(line).append(':').append(column);
            }
            sb.append(" — ");
        } else if (line > 0) {
            sb.append("line ").append(line).append(", column ").append(column).append(" — ");
        }
        return sb.append(getMessage()).toString();
    }
}
