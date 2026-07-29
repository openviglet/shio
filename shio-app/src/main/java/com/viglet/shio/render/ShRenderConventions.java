/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.List;
import java.util.TreeSet;

/**
 * SH292 — what a template may call, <em>and the model it runs against</em>, as the context pack's
 * render conventions.
 *
 * <h2>The asymmetry this closes</h2>
 *
 * <p>SH272 taught an author that {@code {{#query folder= type= limit=}}} exists. Nothing told them
 * that a bare {@code {{TEXT}}} is the <b>page's</b> field at the top level and the <b>listed post's</b>
 * inside the block, which is the single most likely thing to get wrong — and the cost is not
 * hypothetical: SH251's {@code template-unknown-field} is an <em>error</em> at the top level and only a
 * <em>warning</em> inside a listing block, because the rows are whatever the helper found and the
 * engine cannot prove what they hold. So the one place the lint is weakest was the one place the
 * author had no documentation at all.</p>
 *
 * <h2>Where it goes, which was the actual question (§XVI.44)</h2>
 *
 * <p>The pack, in the render line that already exists — not a {@code shio://} resource (which would
 * answer MCP clients only, while this is exactly the *instance-specific* fact the pack section exists
 * for) and not a documentation link (P8: a link is not an appliable unit). Two things make that
 * defensible rather than "add it to conventions because it is easy":</p>
 *
 * <ul>
 *   <li><b>It is derived.</b> The row keys are {@link ShRenderHelpers#ROW_KEYS} and the frames are
 *       {@link ShRenderModel#FRAMES}, read rather than restated (SH260). A hand-written list here
 *       would be a wrong instruction the moment either changed, and the pack is <em>served</em>.</li>
 *   <li><b>It is measured</b>, and the measurement corrected the draft twice. §XVI.44 feared this would
 *       "roughly double" the line; it adds <b>~49%</b> (helpers 120 tokens, this 59, total 179) — and
 *       15 of those tokens were removed <em>by</em> the measurement, which is what showed the first
 *       draft repeating the four block-helper names one line after listing them. The total is asserted
 *       against {@code response.context.conventions.render} in {@code token-budgets.properties}: the
 *       first ceiling the conventions section has ever had, because the response budget test measures
 *       the pack with {@code include=instance,sites,sitemap} — so the only part of the pack that
 *       carries sentences was the one part nothing measured.</li>
 * </ul>
 *
 * <p>Still <b>conditional on the instance holding a render post-type</b> (SH272's rule): a session
 * that will never write a template pays nothing.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShRenderConventions {

    private ShRenderConventions() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The render conventions for an instance whose model holds a render post-type.
     *
     * @param signatures {@code ShTemplateEngine.registeredSignatures()} — the closed capability set
     * @return the lines, in a fixed order (P5): what may be called, then what is in scope
     */
    public static List<String> lines(List<String> signatures) {
        return List.of(
                "A PageLayout's and a Region's HTML is Handlebars, and the helpers it may call are "
                        + "exactly these: " + String.join(", ", signatures)
                        + ". Anything else is refused when you write the post, with a line and a "
                        + "column. A layout slots a region by title with sh-region=\"TITLE\", and "
                        + "binds to a post-type through the site's postTypeLayout map.",
                // Terse on purpose. This is the fact the write-time lint cannot enforce inside a
                // block, so it has to arrive before the template is written rather than after.
                // The block helpers are named on the line above; repeating them here cost 20 tokens
                // of the 94 this line started at, which is the kind of thing only a measurement finds.
                "In a template, a bare {{FIELD}} is the page's own field; inside a listing block it "
                        + "is the listed post's, and a row carries only: "
                        + String.join(", ", new TreeSet<>(ShRenderHelpers.ROW_KEYS))
                        + ". Reserved frames, never your fields: "
                        + String.join(", ", new TreeSet<>(ShRenderModel.FRAMES)) + ".");
    }
}
