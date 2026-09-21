/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

import tools.jackson.databind.JsonNode;

/**
 * A post as the console reads it (SH176) — the shape the post form is built from.
 *
 * <p>It exists because {@code GET /api/v2/post-unified/{id}} used to serialize the JPA
 * entity, and the storage shape is not the authoring shape in two ways that each empty the
 * form on their own: {@code shPostType} is a LAZY association nothing fetched, so it arrived
 * as {@code null}, and {@code data} is a {@code String} column, so it arrived as a JSON
 * <em>string</em> while the console's model declares an object. Both are faithful
 * serializations of a valid {@code 200}, which is why no API test could see them — the SH120
 * lesson arriving one endpoint late.</p>
 *
 * <p><b>The post-type travels as its SH16 {@link ShPostTypeDef}</b>, the same bytes
 * {@code GET /api/v2/post-type/{name}} returns, so there is no third serialization of the
 * content model (law P10) and the form projects the {@code fields} it already knows how to
 * project (SH178).</p>
 *
 * <p>{@code undeclaredFields} names the keys {@code data} holds that the post-type does not
 * declare. A projected object must not become a place values disappear into: the console shows
 * them rather than hiding them, and the list is what lets it say so. It is the read side of
 * SH168 — a write should refuse such a key in the first place, and until it does, a post that
 * already carries one must not look empty.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public record ShConsolePost(
        String id,
        String state,
        String title,
        String summary,
        String furl,
        String owner,
        String modifier,
        String publisher,
        boolean published,
        String publishStatus,
        Instant date,
        Instant modifiedDate,
        Instant publicationDate,
        Instant publishScheduledDate,
        Instant unpublishScheduledDate,
        Instant deletedDate,
        String locale,
        String translationGroupId,
        ShPostTypeDef shPostType,
        ShConsolePostFolder shFolder,
        ShConsolePostSite shSite,
        JsonNode data,
        List<String> undeclaredFields,
        /**
         * SH1058 — true when the source this page translates has changed since, absent otherwise.
         *
         * <p>Per-property {@code NON_NULL} rather than on the record: a key carrying {@code null}
         * on every post of a monolingual instance is a byte every console read pays for a fact
         * that does not apply to it, and annotating the record would change how its other absent
         * fields serialize.</p>
         */
        @JsonInclude(Include.NON_NULL) Boolean translationStale) {

    /** Without the staleness flag — what every caller wanted before SH1058. */
    public ShConsolePost(String id, String state, String title, String summary, String furl,
                         String owner, String modifier, String publisher, boolean published,
                         String publishStatus, Instant date, Instant modifiedDate,
                         Instant publicationDate, Instant publishScheduledDate,
                         Instant unpublishScheduledDate, Instant deletedDate, String locale,
                         String translationGroupId, ShPostTypeDef shPostType,
                         ShConsolePostFolder shFolder, ShConsolePostSite shSite, JsonNode data,
                         List<String> undeclaredFields) {
        this(id, state, title, summary, furl, owner, modifier, publisher, published, publishStatus,
                date, modifiedDate, publicationDate, publishScheduledDate, unpublishScheduledDate,
                deletedDate, locale, translationGroupId, shPostType, shFolder, shSite, data,
                undeclaredFields, null);
    }

    /** The same post, saying its source has moved on (SH1058). */
    public ShConsolePost withTranslationStale(Boolean stale) {
        return !Boolean.TRUE.equals(stale) ? this
                : new ShConsolePost(id, state, title, summary, furl, owner, modifier, publisher,
                        published, publishStatus, date, modifiedDate, publicationDate,
                        publishScheduledDate, unpublishScheduledDate, deletedDate, locale,
                        translationGroupId, shPostType, shFolder, shSite, data, undeclaredFields,
                        Boolean.TRUE);
    }

    /** The post's folder, and through it its site — what the console's breadcrumb needs. */
    public record ShConsolePostFolder(String id, String name, ShConsolePostSite shSite) {
    }

    /** The site a post belongs to, resolved through {@code ShFolderSite} (SH128), never read off a nested folder. */
    public record ShConsolePostSite(String id, String name) {
    }
}
