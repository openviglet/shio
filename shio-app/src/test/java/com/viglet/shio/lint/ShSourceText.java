/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.lint;

/**
 * Java source with its comments removed, for the lints that scan it (SH613).
 *
 * <h2>The defect this is the shared half of</h2>
 *
 * <p>{@code ShRenderCacheReachLintTest} scanned raw file text for {@code @Transactional}. SH206
 * removed that annotation from {@code ShAgentVerifyService} and explained in the javadoc <em>why</em>
 * — and the build went red, because a sentence naming the annotation is indistinguishable from
 * declaring it. <b>A lint whose finding can be produced by writing about the rule is one people
 * learn to route around by not writing about it</b>, which costs exactly the explanation the next
 * reader needs.</p>
 *
 * <h2>Why it is shared</h2>
 *
 * <p>SH613's estimate was that this was the second copy the repository would want and the first it
 * should not have. The count was low: there were <b>five</b> — a whole-file state machine in
 * {@code ShRenderCacheReachLintTest}, three per-line {@code stripComment} methods, and an inline
 * skip inside a loop — each with its own idea of what a comment is, none tested against the others.
 * A rule enforced by five implementations is five rules.</p>
 *
 * <h2>String and text-block aware, and a state machine on purpose</h2>
 *
 * <p>This repository is full of URLs and address grammars, and a {@code //} inside a literal is not
 * a comment. Comment stripping is the one job a regex cannot do on a language with nested quoting
 * rules, and getting it wrong here <b>silently blinds the lint rather than failing it</b> — the
 * worst failure shape a guard has, because the build stays green.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShSourceText {

    private ShSourceText() {
    }

    /**
     * A whole file with block and line comments removed, literals intact.
     *
     * <p>Lifted verbatim from the state machine SH206 wrote as a private method, which is the one
     * of the five that was correct.</p>
     */
    public static String code(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? source.length() : end + 3;
                out.append(source, i, stop);
                i = stop;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < source.length() && source.charAt(j) != c) {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                int stop = Math.min(j + 1, source.length());
                out.append(source, i, stop);
                i = stop;
            } else if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? source.length() : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() : end + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * One line's code, for the scans that report a line number and so cannot lose the line.
     *
     * <p>A whole-file strip is strictly better where it is available: it knows a {@code /*} on the
     * previous line is still open, and this cannot. It is offered anyway because several lints
     * report {@code file:line} and a stripped file no longer has the same numbering — and the
     * alternative those lints chose was a private one-liner each.</p>
     *
     * <p>Returns {@code ""} for a line that is entirely comment, including a javadoc continuation
     * — so a caller can treat "no code on this line" and "nothing matched" as one case.</p>
     */
    public static String codeLine(String line) {
        if (line == null) {
            return "";
        }
        String text = line.strip();
        if (text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")) {
            return "";
        }
        // Only outside a literal: an address grammar's `post:<site>/<url>` and every URL in this
        // repository would otherwise be truncated mid-string, which reads as a lint that stopped
        // matching rather than one that broke.
        return code(line);
    }
}
