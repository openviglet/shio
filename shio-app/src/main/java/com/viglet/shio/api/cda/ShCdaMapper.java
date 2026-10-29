/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.cda;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.cda.dto.ShCdaField;
import com.viglet.shio.api.cda.dto.ShCdaFolder;
import com.viglet.shio.api.cda.dto.ShCdaPost;
import com.viglet.shio.api.cda.dto.ShCdaPostSummary;
import com.viglet.shio.api.cda.dto.ShCdaPostTypeSchema;
import com.viglet.shio.api.cda.dto.ShCdaSchemaField;
import com.viglet.shio.api.cda.dto.ShCdaRedirect;
import com.viglet.shio.api.cda.dto.ShCdaSite;
import com.viglet.shio.api.cda.dto.ShCdaSiteLocales;
import com.viglet.shio.url.ShFurlSpellings;
import com.viglet.shio.post.type.ShPostFieldFlags;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;
import com.viglet.shio.api.site.ShSiteLocales;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;

import java.util.LinkedHashMap;
import java.util.Map;
import com.viglet.shio.post.block.ShBlocks;
import com.viglet.shio.widget.ShSystemWidget;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Maps persistence entities into the frozen CDA read DTOs (SH1).
 *
 * <p>Centralizing the mapping is what lets the JPA entities evolve without
 * breaking the public contract, and is the single place where the
 * authoring-only fields ({@code owner}, {@code modifier}, {@code publisher},
 * {@code publishStatus}) are dropped from the delivery surface.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Component
public class ShCdaMapper {

    private static final byte ROOT_FOLDER = 1;

    /** Widgets whose delivered value is an array of scalars (SH11). */
    private static final Set<String> ARRAY_WIDGETS = Set.of("Check Box", "Multi Select");
    /** Layout-only widgets that carry no delivered value — omitted from the schema (SH11). */
    private static final Set<String> CONTAINER_WIDGETS = Set.of("Tab");

    private final ObjectMapper objectMapper;

    public ShCdaMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShCdaSite toSite(ShSite site, String rootFolderId) {
        return toSite(site, rootFolderId, null);
    }

    /**
     * SH1133 — one {@code Redirect} post as a row of the site's redirect table, or null when it
     * names no destination.
     *
     * <p>Null on an empty {@code TO} for the renderer's reason: {@code redirectFor} treats that post
     * as no redirect at all, and a table that listed it would tell a front end to send readers to
     * nowhere. {@code PERMANENT} arrives as {@code "1"}/{@code "0"} from the console and as a boolean
     * from an op, so it goes through the one coercion both writers already share.</p>
     */
    public ShCdaRedirect toRedirect(ShPostUnified post) {
        if (post == null) {
            return null;
        }
        JsonNode data = parseAttrs(post.getData());
        JsonNode to = data.get(ShSystemPostTypeAttr.TO);
        if (to == null || !to.isString() || to.stringValue().isBlank()) {
            return null;
        }
        JsonNode permanent = data.get(ShSystemPostTypeAttr.PERMANENT);
        return new ShCdaRedirect(ShFurlSpellings.canonical(post.getFurl()), to.stringValue().trim(),
                permanent != null && ShPostFieldFlags.isTrue(
                        permanent.isBoolean() ? permanent.asBoolean() : permanent.asString()));
    }

    /** SH1123 — the site read, carrying the locale policy a switcher is built from. */
    public ShCdaSite toSite(ShSite site, String rootFolderId, ShSiteLocales locales) {
        if (site == null) {
            return null;
        }
        return new ShCdaSite(site.getId(), site.getName(), site.getDescription(), site.getUrl(),
                rootFolderId, toLocales(locales));
    }

    public ShCdaSiteLocales toLocales(ShSiteLocales locales) {
        if (locales == null) {
            return null;
        }
        return new ShCdaSiteLocales(locales.defaultLocale(), locales.enabled(), locales.fallback(),
                locales.declared(), locales.labels().isEmpty() ? null : locales.labels());
    }

    public ShCdaFolder toFolder(ShFolder folder) {
        if (folder == null) {
            return null;
        }
        return new ShCdaFolder(folder.getId(), folder.getName(), folder.getRootFolder() == ROOT_FOLDER);
    }

    public ShCdaPostSummary toSummary(ShPostUnified post) {
        if (post == null) {
            return null;
        }
        return new ShCdaPostSummary(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getFurl(),
                postTypeName(post),
                post.getLocale(),
                post.getDate(),
                post.getModifiedDate());
    }

    public ShCdaPost toPost(ShPostUnified post) {
        return toPost(post, java.util.List.of());
    }

    /**
     * Full post including the SH53 locale axis. {@code availableLocales} lists
     * the locales this content is translated into (computed by the service from
     * the translation group) so a headless front can render a language switcher.
     */
    public ShCdaPost toPost(ShPostUnified post, java.util.List<String> availableLocales) {
        if (post == null) {
            return null;
        }
        JsonNode attrs = parseAttrs(post.getData());
        return new ShCdaPost(
                post.getId(),
                post.getTitle(),
                post.getSummary(),
                post.getFurl(),
                postTypeName(post),
                post.getShSite() != null ? post.getShSite().getId() : null,
                post.getShFolder() != null ? post.getShFolder().getId() : null,
                post.isPublished(),
                post.getDate(),
                post.getModifiedDate(),
                post.getPublicationDate(),
                post.getLocale(),
                availableLocales != null ? availableLocales : java.util.List.of(),
                attrs,
                renderedBlocks(post, attrs));
    }

    /**
     * SH1016 — the HTML view of every block field this post declares.
     *
     * <h2>Rendered here rather than stored at write</h2>
     *
     * <p>The design proposed storing it, so delivery would pay nothing. Storing derived HTML needs
     * a home, and the two available ones are both wrong: a sibling key in {@code data} is an
     * undeclared field the next write refuses (SH168), and a column is a schema change for a value
     * that is a pure function of one already there. What it would buy is a projection over a
     * document this method has already parsed — cheaper than the {@code readTree} above it, which
     * nobody has proposed caching. So it is computed, and {@code ShAgentResponseBudgetTest} is
     * where the claim that this is cheap stops being an argument.</p>
     *
     * <p>Empty means <em>no block field</em> and is dropped by {@code NON_NULL}, so a post that
     * predates the widget serializes exactly as it did.</p>
     */
    private Map<String, String> renderedBlocks(ShPostUnified post, JsonNode attrs) {
        ShPostType postType = post.getShPostType();
        if (postType == null || postType.getShPostTypeAttrs() == null || !attrs.isObject()) {
            return null;
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        for (ShPostTypeAttr attr : postType.getShPostTypeAttrs()) {
            String name = attr.getName();
            if (name == null || attr.getShWidget() == null
                    || !ShSystemWidget.BLOCKS.equals(attr.getShWidget().getName())) {
                continue;
            }
            JsonNode value = attrs.get(name);
            if (value != null && value.isArray()) {
                rendered.put(name, ShBlocks.html(value));
            }
        }
        return rendered.isEmpty() ? null : rendered;
    }

    private String postTypeName(ShPostUnified post) {
        return post.getShPostType() != null ? post.getShPostType().getName() : null;
    }

    /**
     * The post-type's editable fields (SH10) — top-level attributes only
     * ({@code shParentPostTypeAttr == null}; Relator children are edited within
     * their parent), ordered by {@code ordinal}. Backs the editor properties panel.
     */
    public List<ShCdaField> toFields(ShPostType postType) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                // SH722 — the tiebreaker. `ordinal` is a curator-editable number nothing forbids
                // from colliding, and the input is a Set the persistence layer hands over in hash
                // order, so without this the DELIVERED field order of a post flips between calls.
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .map(this::toField)
                .toList();
    }

    public ShCdaField toField(ShPostTypeAttr attr) {
        if (attr == null) {
            return null;
        }
        return new ShCdaField(
                attr.getName(),
                attr.getLabel(),
                attr.getDescription(),
                attr.getShWidget() != null ? attr.getShWidget().getName() : null,
                attr.getRequired() == 1,
                attr.getIsTitle() == 1,
                attr.getIsSummary() == 1,
                attr.getOrdinal());
    }

    /**
     * The full content model of a post-type (SH11) — its API name plus every
     * top-level, value-bearing attribute as a {@link ShCdaSchemaField}, ordered
     * by {@code ordinal}. Backs {@code shio-types} code generation.
     */
    public ShCdaPostTypeSchema toPostTypeSchema(ShPostType postType) {
        if (postType == null) {
            return null;
        }
        return new ShCdaPostTypeSchema(
                postType.getName(),
                postType.getTitle(),
                postType.getNamePlural(),
                postType.getDescription(),
                schemaFields(postType.getShPostTypeAttrs(), true));
    }

    /**
     * One schema field, recursing into a Relator's sub-attributes. {@code multiple}
     * is true for a Relator (its delivered value is an item list) or an
     * array-valued widget ({@code Check Box} / {@code Multi Select}).
     */
    public ShCdaSchemaField toSchemaField(ShPostTypeAttr attr) {
        if (attr == null) {
            return null;
        }
        String widget = attr.getShWidget() != null ? attr.getShWidget().getName() : null;
        List<ShCdaSchemaField> children = schemaFields(attr.getShPostTypeAttrs(), false);
        boolean multiple = !children.isEmpty() || (widget != null && ARRAY_WIDGETS.contains(widget));
        return new ShCdaSchemaField(
                attr.getName(),
                attr.getLabel(),
                attr.getDescription(),
                widget,
                attr.getRequired() == 1,
                multiple,
                attr.getIsTitle() == 1,
                attr.getIsSummary() == 1,
                attr.getOrdinal(),
                children);
    }

    /**
     * Map a set of attributes to ordered schema fields, dropping container-only
     * widgets ({@code Tab}). When {@code topLevel}, also drops Relator children
     * (those are emitted nested within their parent).
     */
    private List<ShCdaSchemaField> schemaFields(Set<ShPostTypeAttr> attrs, boolean topLevel) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .filter(attr -> !topLevel || attr.getShParentPostTypeAttr() == null)
                .filter(attr -> !isContainer(attr))
                // SH722 — the line SH714 measured on the wire: two attributes at ordinal 2, and
                // ten identical shio_context calls returning two distinct packs.
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .map(this::toSchemaField)
                .toList();
    }

    private static boolean isContainer(ShPostTypeAttr attr) {
        return attr != null && attr.getShWidget() != null
                && CONTAINER_WIDGETS.contains(attr.getShWidget().getName());
    }

    /**
     * Parse the {@code data} CLOB into a JSON node. A {@code null}, blank, or
     * malformed payload yields an empty object — never a 500 (acceptance
     * criterion 5).
     */
    private JsonNode parseAttrs(String data) {
        if (data == null || data.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(data);
            return (node != null && node.isObject()) ? node : objectMapper.createObjectNode();
        } catch (RuntimeException e) {
            return objectMapper.createObjectNode();
        }
    }
}
