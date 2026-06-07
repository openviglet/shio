/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.exchange.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;

/**
 * Root model of a content exchange package (SH68) — the {@code export.json} a
 * site export ({@code SH69}) writes and a site import / clone ({@code SH70})
 * reads. It is the unified-model successor to the legacy {@code ShExchange}
 * (deleted with the rest of {@code com.viglet.shio.exchange} in the nuclear
 * refactor, commit {@code 2d3eb353}), and it is deliberately decoupled from the
 * JPA entities so the on-disk format can evolve independently:
 *
 * <ul>
 *   <li>all cross-references are by <strong>id</strong> (folder → parent folder,
 *       post → folder) or by <strong>name</strong> (post → post-type), never by
 *       object graph — the tree is rebuilt at import time;</li>
 *   <li>post content is carried <strong>verbatim</strong> as the unified
 *       {@code data} JSON document (see {@link ShExchangePost}), <em>not</em> the
 *       old split-model flat attribute map;</li>
 *   <li>post-types reuse the canonical lossless {@link ShPostTypeDef} (SH16) so
 *       the content model has a single interchange shape.</li>
 * </ul>
 *
 * <p>A {@code version} stamps the format so future readers can migrate older
 * packages; it defaults to {@link #CURRENT_VERSION} when absent. Static binary
 * assets are listed in {@link #files()} and live alongside the manifest under
 * the package's {@code files/} directory.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShExchangePackage(
        int version,
        String exportedAt,
        String generator,
        List<ShExchangeSite> sites,
        List<ShPostTypeDef> postTypes,
        List<ShExchangeFolder> folders,
        List<ShExchangePost> posts,
        List<ShExchangeFile> files) {

    /** Current package format version. */
    public static final int CURRENT_VERSION = 1;

    public ShExchangePackage {
        version = version == 0 ? CURRENT_VERSION : version;
        sites = sites == null ? List.of() : List.copyOf(sites);
        postTypes = postTypes == null ? List.of() : List.copyOf(postTypes);
        folders = folders == null ? List.of() : List.copyOf(folders);
        posts = posts == null ? List.of() : List.copyOf(posts);
        files = files == null ? List.of() : List.copyOf(files);
    }
}
