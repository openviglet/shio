/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

/**
 * SH872 — the fields a post's own stored markup declares.
 *
 * <h2>The rule, and where it came from</h2>
 *
 * <p>SH868 proposed that <b>the annotation is the field and the markup is the storage</b>. What
 * shipped stored the values in fields on {@code Page} instead, named from the captured block's id —
 * and {@code Page} is the type <em>every</em> page in the instance uses. Measured on an eleven-page
 * replica: {@code Page} went from <b>4 fields to 44</b>, and {@code shio annotations} on the home
 * page answered {@code 14 of 44 declared field(s) annotated, 30 NOT editable}, naming
 * {@code b425bd805Heading} and {@code b6d312963Intro} — blocks that live on <em>other</em>
 * pages. Thirty findings on a page with nothing wrong with it, growing with every page captured.</p>
 *
 * <p>So a conversion no longer copies the annotated text into the post's data: it is already in the
 * markup, which is why {@link ShRenderModel} substitutes only where a stored value <em>differs</em>
 * from what the markup says (SH870). An unedited annotation stores nothing and declares nothing.</p>
 *
 * <h2>What is left to answer, and why it needs a shared rule</h2>
 *
 * <p>A curator's edit through the Universal Editor <em>does</em> store one — {@code
 * ShPostDraftMergeService} writes {@code data.<field>} and asks no post-type anything. Two readers
 * would then call that key a defect: {@code ShAgentVerifyService}'s {@code unknown-field}, which is
 * an <b>error</b>, and {@code ShAgentBatchService}'s SH168 check, which would refuse a
 * {@code shio push} of the content file the edit produced. Both are right in general and wrong
 * here, so both ask this: <b>a key annotated by the post's own markup is declared by that
 * markup.</b></p>
 *
 * <p>Scoped to the post being checked and to a value that <em>is</em> markup, so it cannot be used
 * to smuggle an arbitrary key past the model: the key has to be addressed by an
 * {@code sh-field} attribute in a string this same post stores.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShAnnotatedFields {

    /**
     * {@code sh-field="name"} in a stored string, either quoting.
     *
     * <p>A regex and not a parse, for {@code ShRenderModel}'s reason inverted: parsing a megabyte of
     * captured markup to answer "does this key appear" would cost a jsoup document per post per
     * lint. The names this matches are the ones {@code convert} writes, and a false positive costs
     * one grandfathered key rather than a wrong page.</p>
     */
    private static final Pattern ANNOTATION = Pattern.compile(
            ShRegionComposer.FIELD_ATTR + "\\s*=\\s*[\"']([^\"']+)[\"']");

    private ShAnnotatedFields() {
        // utility
    }

    /**
     * Every field name the string values of {@code data} annotate.
     *
     * <p>Empty for a post storing no markup, which is almost all of them — the walk stops at the
     * {@code contains} before any matching happens.</p>
     */
    public static Set<String> of(JsonNode data) {
        Set<String> names = new LinkedHashSet<>();
        if (data == null || !data.isObject()) {
            return names;
        }
        for (String key : data.propertyNames()) {
            JsonNode value = data.get(key);
            if (value == null || !value.isString()) {
                continue;
            }
            String markup = value.stringValue();
            if (!markup.contains(ShRegionComposer.FIELD_ATTR + "=")) {
                continue;
            }
            Matcher matcher = ANNOTATION.matcher(markup);
            while (matcher.find()) {
                String name = matcher.group(1).trim();
                // A path into a collection is the section's own data and its template renders it —
                // the same boundary `ShRenderModel.substituteInto` draws one step along.
                if (!name.isEmpty() && name.indexOf('.') < 0) {
                    names.add(name);
                }
            }
        }
        return names;
    }
}
