/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a published post into the attribute map Turing indexes (SH84).
 *
 * <h2>The one invariant that fails silently: absent is not empty</h2>
 *
 * <p>{@code TurSNJobItem}'s own contract (T381, Dumont's grounding rule D03) is that the map
 * carries <b>only fields the source actually supplies</b> — an absent field is a <em>missing
 * key</em>, never a key mapped to {@code null}, {@code ""} or an empty collection. Materializing
 * an empty value is not a cosmetic difference: it makes facet counts count documents that have no
 * such value and makes an {@code exists}-style filter match them. Nothing in Shio or in Turing
 * refuses such a document, so the symptom is a facet with a wrong number in it — which is why
 * {@link ShTuringDocumentMapperTest} asserts the property directly rather than trusting each
 * {@code put}.</p>
 *
 * <h2>Why the mapping is fixed rather than configurable</h2>
 *
 * <p>Shio's post-types are user-defined, so the temptation is a per-attribute mapping UI. That
 * would be a second schema language for the thing SH16's post-types already describe. The
 * fixed mapping instead spends Turing's <em>standard</em> field names ({@link TurSNFieldName}) on
 * the facts every post-type has — title, summary, URL, dates, type, site — and folds everything
 * else into {@code text}, which is what a full-text field is for. A post-type that needs a facet
 * of its own is a real request, and it is a separate task: it needs a `TurSNJobAttributeSpec`,
 * which is a decision about the *index*, not about the post.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTuringDocumentMapper {

    private final ObjectMapper objectMapper;

    public ShTuringDocumentMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * The attributes of a post as Turing's standard fields.
     *
     * @param post      the post to describe — normally its {@code PUBLISHED} row, since that is
     *                  what a search index is allowed to serve
     * @param sourceApp the {@code source_apps} stamp, see {@link ShTuringProperties#getSourceApp()}
     * @return a map containing exactly the fields this post has a value for
     */
    public Map<String, Object> attributes(ShPostUnified post, String sourceApp) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        put(attributes, TurSNFieldName.ID, post.getId());
        put(attributes, TurSNFieldName.TITLE, post.getTitle());
        put(attributes, TurSNFieldName.ABSTRACT, post.getSummary());
        put(attributes, TurSNFieldName.URL, url(post));
        put(attributes, TurSNFieldName.TYPE, post.getShPostType() == null
                ? null
                : post.getShPostType().getName());
        put(attributes, TurSNFieldName.SITE, post.getShSite() == null
                ? null
                : post.getShSite().getName());
        put(attributes, TurSNFieldName.PUBLICATION_DATE, post.getPublicationDate());
        put(attributes, TurSNFieldName.MODIFICATION_DATE, post.getModifiedDate());
        put(attributes, TurSNFieldName.AUTHOR, post.getOwner());
        put(attributes, TurSNFieldName.SOURCE_APPS, sourceApp);
        put(attributes, TurSNFieldName.TEXT, text(post));
        return attributes;
    }

    /**
     * The absolute URL of the page, when the site knows where it is served.
     *
     * <p>Falls back to the {@code furl} alone rather than to nothing: a relative URL is still a
     * usable link for a search front end serving the same origin, and a document with no
     * {@code url} at all is a result nobody can click. A site with no {@code url} configured and
     * a post with no {@code furl} yields {@code null}, and then the key is simply absent.</p>
     */
    private static String url(ShPostUnified post) {
        String furl = post.getFurl();
        if (furl == null || furl.isBlank()) {
            return null;
        }
        String path = furl.startsWith("/") ? furl : "/" + furl;
        ShSite site = post.getShSite();
        String base = site == null ? null : site.getUrl();
        if (base == null || base.isBlank()) {
            return path;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path;
    }

    /**
     * Every string in the post's {@code data}, HTML stripped, joined by newlines.
     *
     * <p>The fields are user-defined (SH16), so this walks the JSON rather than naming any of
     * them: a post-type's fifth field has to be searchable the day it is added, without a mapping
     * edit. HTML is stripped because a rich-text field's markup would otherwise be indexed as
     * words — a search for "div" would match every page on the site.</p>
     */
    private String text(ShPostUnified post) {
        JsonNode data = readData(post.getData());
        if (data == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        collect(data, parts);
        return parts.isEmpty() ? null : String.join("\n", parts);
    }

    private JsonNode readData(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            // The backend stores the literal string "null" for an empty payload, and
            // readTree("null") is a NullNode that passes every non-null check — the same trap the
            // console's widgetSettings guard exists for.
            return parsed == null || parsed.isNull() ? null : parsed;
        } catch (JacksonException e) {
            // Not an incident: a post whose data is not JSON is already reported as a CONTENT
            // degradation by the render path (SH270), and recording it a second time here would
            // put one broken post in the diagnostics window once per publish.
            return null;
        }
    }

    private static void collect(JsonNode node, List<String> parts) {
        if (node.isTextual()) {
            String text = Jsoup.parse(node.stringValue()).text().trim();
            if (!text.isEmpty()) {
                parts.add(text);
            }
            return;
        }
        if (node.isNumber() || node.isBoolean()) {
            return; // a number is a value, not prose — indexing it as text matches nothing useful
        }
        for (JsonNode child : node) {
            collect(child, parts);
        }
    }

    /**
     * The whole grounding contract, in one place.
     *
     * <p>Every field goes through here, so "omit rather than send empty" is one rule enforced
     * once instead of eleven {@code if} statements that have to each be right.</p>
     */
    private static void put(Map<String, Object> attributes, String field, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof CharSequence text && text.toString().isBlank()) {
            return;
        }
        if (value instanceof java.util.Collection<?> items && items.isEmpty()) {
            return;
        }
        attributes.put(field, value);
    }
}
