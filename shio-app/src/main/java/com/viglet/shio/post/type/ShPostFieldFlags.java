/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post.type;

/**
 * What a {@code Check Box} field's stored value means, in one place (SH421).
 *
 * <h2>Why this is shared rather than repeated</h2>
 *
 * <p>A checkbox does not arrive as a {@code Boolean}. The console posts {@code "true"}, an HTML form
 * posts {@code "on"}, an import can carry {@code 1}, and {@code post.upsert} may send a real JSON
 * boolean — SH385 wrote that down for {@code Redirect.PERMANENT}, where a 301 silently becoming a
 * 302 because the value arrived as a string is a defect nobody finds by looking at the content.</p>
 *
 * <p>SH421 gave {@code PageLayout} its own flag and therefore a <b>second</b> reader: the renderer
 * decides what a redirect does, and {@code ShAgentVerifyService} decides whether a lint fires. Two
 * copies of this coercion would be two answers to "is this box ticked", and the one that matters is
 * the disagreement — a layout the author has opted out of would still be warned about, which is the
 * exact false positive SH421 exists to remove. SH245's rule: one writer per verb.</p>
 *
 * @author Alexandre Oliveira
 */
public final class ShPostFieldFlags {

    private ShPostFieldFlags() {
    }

    /**
     * Whether a stored field value means <em>ticked</em>.
     *
     * <p>Deliberately a small closed list rather than a permissive parse: an unrecognised value is
     * {@code false}, because the flags this reads are opt-<b>outs</b>, and the safe reading of a
     * value nobody understands is the one that keeps the check running.</p>
     */
    public static boolean isTrue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = value == null ? "" : value.toString().trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "on".equalsIgnoreCase(text);
    }
}
