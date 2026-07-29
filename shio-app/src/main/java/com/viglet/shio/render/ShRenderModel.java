/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.viglet.shio.object.ShFolderSite;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.utils.ShStableMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH183 — what a template can see, and nothing else.
 *
 * <h2>The model is maps all the way down</h2>
 *
 * <p>{@link ShTemplateEngine} resolves values with {@code MapValueResolver} only, so this
 * class is the entire surface a layout or region has access to. That is deliberate and is
 * the second half of the safety argument: handing a template the {@code ShPostUnified}
 * entity and a bean resolver would make {@code {{shFolder.shSite.shFolders}}} a walk of
 * the persistence model, and a layout is content. Projecting to maps means an expression
 * that names something outside this shape resolves to empty rather than to whatever the
 * object graph happens to expose.</p>
 *
 * <h2>Shape</h2>
 *
 * <pre>
 * {  &lt;the post's own data fields, verbatim under their declared names&gt;,
 *    post:  { id, title, summary, url, type, folder },
 *    site:  { id, name, url },
 *    theme: { css, javascript }   }
 * </pre>
 *
 * <p>Fields go in <b>first</b> and the three reserved keys overwrite them, so a post-type
 * that happens to declare a field called {@code site} loses to the site — the reverse
 * would let a content author shadow the frame the layout is built on, and a template
 * silently rendering a data value where it asked for the site's URL is worse than a
 * field the author can rename. Field names are kept <b>verbatim</b> rather than
 * lower-cased: the 2018 templates write {@code {{TITLE}}} and the SH95 vocabulary writes
 * {@code {{headline}}}, and normalising either would break the other.</p>
 *
 * <h2>Ordering</h2>
 *
 * <p>Built through {@link ShStableMap} (SH136). The model is iterated by {@code {{#each}}}
 * and it feeds markup that SH101 hashes, so its order is observable — and
 * {@code Map.copyOf} derives iteration order from a per-JVM salt, which would make the
 * same content render differently after a restart.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRenderModel {

    /**
     * The reserved top-level frames of the render model — SH260's rule applied to a fourth fact.
     *
     * <p>{@code post}, {@code site} and {@code theme} are put by {@link #of}; {@code render} is put
     * by {@code ShPageRenderService}. Three writers and, before SH292, a fourth <em>private</em> copy
     * in {@code ShTemplateFieldLint} used to filter them — a set nobody owned, which is the shape
     * whose drift SH260 found showing up as a warning on a correct template. The context pack now
     * <b>tells an author</b> what these are, so a stale list would be a wrong instruction rather than
     * an internal inconsistency, and `ShRenderConventionsTest` asserts the writers against it in both
     * directions.</p>
     */
    public static final Set<String> FRAMES = Set.of("post", "site", "theme", "render");

    private final ObjectMapper objectMapper;

    public ShRenderModel(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param post  the page being rendered
     * @param theme the resolved theme's assets, or {@code null}
     * @return the view model
     */
    public Map<String, Object> of(ShPostUnified post, ShThemeAssets theme) {
        Map<String, Object> model = new LinkedHashMap<>(fieldsOf(post));
        model.put("post", postFrame(post));
        model.put("site", siteFrame(post));
        model.put("theme", themeFrame(theme));
        return ShStableMap.copyOf(model);
    }

    /**
     * The post's {@code data} column, parsed. Sorted by field name so the model's own
     * iteration order does not depend on how the JSON happened to be written — two posts
     * with the same fields must render identically.
     */
    public Map<String, Object> fieldsOf(ShPostUnified post) {
        Map<String, Object> fields = new TreeMap<>();
        if (post == null || post.getData() == null || post.getData().isBlank()) {
            return fields;
        }
        try {
            JsonNode node = objectMapper.readTree(post.getData());
            if (node != null && node.isObject()) {
                for (String key : node.propertyNames()) {
                    fields.put(key, scalar(node.get(key)));
                }
            }
        } catch (JacksonException e) {
            // A post whose data is not an object renders with no fields rather than
            // failing the page: the defect belongs to the content, and SH99's lint is
            // where it is reported. A blank page would hide it.
            return fields;
        }
        return fields;
    }

    /**
     * JSON → the maps, lists and scalars the resolver understands. One conversion for
     * every node type: a nested object becomes a {@code Map} and an array a {@code List},
     * which is exactly what {@code MapValueResolver} and {@code {{#each}}} expect, and
     * nothing arrives as a {@code JsonNode} the bean resolver would have to walk.
     */
    private Object scalar(JsonNode value) {
        return value == null || value.isNull()
                ? null
                : objectMapper.convertValue(value, Object.class);
    }

    private Map<String, Object> postFrame(ShPostUnified post) {
        Map<String, Object> frame = new LinkedHashMap<>();
        if (post == null) {
            return ShStableMap.copyOf(frame);
        }
        frame.put("id", post.getId());
        frame.put("title", post.getTitle());
        frame.put("summary", post.getSummary());
        frame.put("url", post.getFurl());
        frame.put("type", post.getShPostType() == null ? null : post.getShPostType().getName());
        ShFolder folder = post.getShFolder();
        frame.put("folder", folder == null ? null : folder.getName());
        return ShStableMap.copyOf(frame);
    }

    private Map<String, Object> siteFrame(ShPostUnified post) {
        Map<String, Object> frame = new LinkedHashMap<>();
        // ShFolderSite-exempt: a post carries its own site column (SH128's invariant is
        // about folders), and the folder walk is the fallback for a row written before it.
        ShSite site = post == null ? null : post.getShSite();
        if (site == null && post != null) {
            site = ShFolderSite.of(post.getShFolder());
        }
        if (site == null) {
            return ShStableMap.copyOf(frame);
        }
        frame.put("id", site.getId());
        frame.put("name", site.getName());
        frame.put("url", site.getUrl());
        return ShStableMap.copyOf(frame);
    }

    private Map<String, Object> themeFrame(ShThemeAssets theme) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("css", theme == null ? null : theme.css());
        frame.put("javascript", theme == null ? null : theme.javascript());
        return ShStableMap.copyOf(frame);
    }

    /**
     * A {@code Theme} post's two fields. {@code PT-THEME} carried {@code CSS} and
     * {@code JAVASCRIPT} in 2018 and both were null in the one shipped sample, so the
     * intent is clearer than the usage: one place for the stylesheet and script a whole
     * site shares, referenced by each layout's {@code THEME} field.
     */
    public record ShThemeAssets(String css, String javascript) {
    }
}
