/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.turing.client.sn.job.TurSNJobAttributeSpec;
import com.viglet.turing.commons.se.field.TurSEFieldType;
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
        declared(post, attributes);
        return attributes;
    }

    /**
     * The post-type's own declared fields, typed, beside the standard ones (SH914).
     *
     * <h2>Why this exists at all</h2>
     *
     * <p>The fold into {@code text} above answers "does this word appear". It cannot answer "tuition
     * under 5000" or "has a scholarship", because {@link #collect} skips numbers and booleans on
     * purpose — a number indexed as prose matches nothing useful. So a school filtering by level,
     * campus or tuition had those values in no field at all. A field declared indexed (SH16, on the
     * attribute) leaves here in a field of its own, carrying the type the declaration named.</p>
     *
     * <h2>It does not un-fold anything</h2>
     *
     * <p>A declared string is emitted <em>and</em> still contributes to {@code text}. Removing it
     * from the fold would silently make a page stop matching a full-text search the day somebody
     * added a facet to it, which is a much worse surprise than the value appearing twice — and
     * "appearing twice" is what a search index is built to handle.</p>
     */
    private void declared(ShPostUnified post, Map<String, Object> attributes) {
        ShPostType postType = post.getShPostType();
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return;
        }
        JsonNode data = readData(post.getData());
        if (data == null) {
            return;
        }
        for (ShPostTypeAttr attr : postType.getShPostTypeAttrs()) {
            if (attr == null || !Boolean.TRUE.equals(attr.getIndexed()) || attr.getName() == null) {
                continue;
            }
            JsonNode value = data.get(attr.getName());
            if (value == null || value.isNull()) {
                continue;
            }
            String field = attr.getTuringField() == null || attr.getTuringField().isBlank()
                    ? attr.getName()
                    : attr.getTuringField();
            // Through the same `put`, so absent-is-not-empty holds for a declared field exactly as
            // it does for a standard one. A declared field whose post has no value is a MISSING
            // KEY, never a zero — a facet counting documents that have no tuition as tuition 0 is
            // the grounding defect this mapper's whole contract exists to prevent.
            put(attributes, field, typed(value, attr.getTuringType()));
        }
    }

    /**
     * The JSON value as the declared type, or {@code null} when it cannot be one.
     *
     * <p>Refusing beats coercing. A tuition field holding "ask us" is a content mistake, and
     * sending {@code 0} for it would make that post answer a "under 5000" filter — a wrong result
     * presented as a right one. Omitting the key leaves the document honest: it simply has no
     * tuition, which is true.</p>
     */
    private static Object typed(JsonNode value, String declaredType) {
        TurSEFieldType type = ShTuringFieldDeclaration.typeOf(declaredType);
        return switch (type) {
            case INT -> value.isNumber() ? (Object) value.intValue() : parsedInt(value);
            case LONG -> value.isNumber() ? (Object) value.longValue() : parsedLong(value);
            case FLOAT, DOUBLE -> value.isNumber() ? (Object) value.doubleValue() : parsedDouble(value);
            case BOOL -> value.isBoolean() ? (Object) value.booleanValue() : parsedBool(value);
            // STRING, TEXT and DATE all leave as text. DATE is Turing's to parse: it accepts the
            // ISO-8601 a Shio date widget already stores, and re-formatting it here would be this
            // mapper inventing a second date contract.
            default -> plain(value);
        };
    }

    private static String plain(JsonNode value) {
        String text = value.isString() ? value.stringValue() : value.toString();
        return Jsoup.parse(text).text().trim();
    }

    /**
     * One parse, one catch — the three numeric types differ only by the function they hand in.
     *
     * <p>Written as three near-identical {@code try}/{@code catch} blocks first. Collapsing them is
     * not tidiness: {@code ShSilentCatchInventoryTest} counts catches that neither record nor
     * rethrow, and three copies of one decision read as three decisions in a population somebody
     * has to review by hand.</p>
     */
    private static <T> T parsed(JsonNode value, java.util.function.Function<String, T> as) {
        try {
            return as.apply(plain(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parsedInt(JsonNode value) {
        return parsed(value, Integer::valueOf);
    }

    private static Long parsedLong(JsonNode value) {
        return parsed(value, Long::valueOf);
    }

    private static Double parsedDouble(JsonNode value) {
        return parsed(value, Double::valueOf);
    }

    /**
     * {@code Boolean.parseBoolean} answers false for everything that is not "true", which would
     * turn a typo into a confident negative. Only the two words this understands produce a value.
     */
    private static Boolean parsedBool(JsonNode value) {
        String text = plain(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        return "false".equalsIgnoreCase(text) ? Boolean.FALSE : null;
    }

    /**
     * The schema for this post-type's declared fields, so Turing knows their types (SH914).
     *
     * <p>Sent with the job rather than configured by hand in Turing's console: a field whose type
     * the index does not know is a field a range filter cannot use, and a schema kept in two places
     * is a schema that disagrees. Empty for a post-type that declares nothing, which is every
     * post-type until somebody declares one.</p>
     */
    public List<TurSNJobAttributeSpec> specs(ShPostUnified post) {
        ShPostType postType = post.getShPostType();
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        List<TurSNJobAttributeSpec> specs = new ArrayList<>();
        for (ShPostTypeAttr attr : ordered(postType.getShPostTypeAttrs())) {
            if (attr == null || !Boolean.TRUE.equals(attr.getIndexed()) || attr.getName() == null) {
                continue;
            }
            TurSNJobAttributeSpec spec = new TurSNJobAttributeSpec();
            spec.setName(attr.getTuringField() == null || attr.getTuringField().isBlank()
                    ? attr.getName()
                    : attr.getTuringField());
            spec.setType(ShTuringFieldDeclaration.typeOf(attr.getTuringType()));
            spec.setDescription(attr.getLabel());
            // Faceted by default, because "I want to filter by this" is the only reason anybody
            // declares a field here. Not mandatory: a post that leaves the value blank must stay
            // publishable, and the absent-is-not-empty rule already keeps it out of the counts.
            spec.setFacet(true);
            spec.setMandatory(false);
            specs.add(spec);
        }
        return specs;
    }

    /** By ordinal, then name — so two runs of one post-type produce byte-identical specs (P5). */
    private static List<ShPostTypeAttr> ordered(Collection<ShPostTypeAttr> attrs) {
        List<ShPostTypeAttr> sorted = new ArrayList<>(attrs);
        sorted.sort(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()));
        return sorted;
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
        if (node.isString()) {
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
