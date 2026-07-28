/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.github.jknack.handlebars.Options;
import com.viglet.shio.api.agent.ShAddress;
import com.viglet.shio.api.agent.ShAddressResolver;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.utils.ShStableMap;

/**
 * SH183 — the closed capability set, as registered helpers instead of bindings on an
 * unfiltered script engine.
 *
 * <h2>This is what {@code shObject.js} was trying to be</h2>
 *
 * <p>FSP's façade exposed seven functions and nothing else — {@code navigation},
 * {@code navigationFolder}, {@code query}, {@code queryByPostType}, {@code searchComponent},
 * {@code formComponent}, {@code getRelation} — each a one-line wrapper over a Spring bean.
 * That is an allow-list <em>expressed as a convention</em>: the script could reach anything
 * the JVM could, and the façade was merely the polite way to do it. Registered helpers are
 * the same allow-list expressed as a fact.</p>
 *
 * <h2>Why this is not a loss of expressiveness</h2>
 *
 * <p>The 2018 regions spent between 767 and 1098 characters each on hand-written
 * JavaScript, and reading them, almost none of it was capability: it was a call to one of
 * the seven functions followed by a loop remapping {@code TITLE}/{@code DESCRIPTION}/
 * {@code TEXT} into a shape the template could iterate. A block helper yields the rows
 * already shaped, so the whole remap becomes the block's body:</p>
 *
 * <pre>{@code
 * {{#query folder="/blog" type="Article" limit=10}}
 *   <div class="card"><h5>{{title}}</h5><a href="{{link}}">Read More</a></div>
 * {{/query}}
 * }</pre>
 *
 * <h2>What is here and what is not</h2>
 *
 * <p>{@code query}, {@code navigation} and {@code file_url} are the three the shipped
 * sample actually used. {@code search}, {@code form} and {@code getRelation} are the
 * remaining three of the seven and are <b>not</b> here: each needs a backing component
 * that the nuclear refactor removed, and inventing them alongside a renderer would be
 * three subsystems judged by one test. They are filed rather than stubbed — a helper that
 * silently renders nothing is worse than an unknown-helper error, which Handlebars raises
 * with the line and column.</p>
 *
 * <h2>Determinism</h2>
 *
 * <p>Every listing is sorted before it is yielded, and by a total order — a date with an
 * id tiebreak, never a date alone, because two posts written in the same millisecond
 * would otherwise swap places between renders and move SH101's digest. Rows are built
 * with {@link ShStableMap} for the same reason (SH136).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShRenderHelpers {

    /** A listing with no ceiling is a page that gets slower as the site grows. */
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 500;

    private final ShTemplateEngine engine;
    private final ShAddressResolver shAddressResolver;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShRenderModel shRenderModel;

    public ShRenderHelpers(ShTemplateEngine engine,
                           ShAddressResolver shAddressResolver,
                           ShFolderRepository shFolderRepository,
                           ShPostUnifiedRepository shPostUnifiedRepository,
                           ShSiteRepository shSiteRepository,
                           ShRenderModel shRenderModel) {
        this.engine = engine;
        this.shAddressResolver = shAddressResolver;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteRepository = shSiteRepository;
        this.shRenderModel = shRenderModel;
    }

    @PostConstruct
    void register() {
        engine.register("query", this::query);
        engine.register("navigation", this::navigation);
        engine.register("file_url", this::fileUrl);
    }

    /**
     * {@code {{#query folder="/blog" type="Article" limit=10}}…{{/query}}} — the posts of
     * a folder, each row a map the block body reads directly.
     *
     * <p>{@code folder} is a name chain within the current site (SH79's folder address
     * without the {@code folder:<site>/} prefix, because a template is already inside one
     * site and repeating it would let a layout read another). Omit it for the folder the
     * page being rendered lives in. {@code {{else}}} renders when nothing matched, which
     * is what stops "no articles yet" from being an empty {@code <div>}.</p>
     */
    Object query(Object context, Options options) throws IOException {
        String siteId = string(options.context.get("site.id"));
        if (siteId == null) {
            return options.inverse();
        }
        String folderChain = hash(options, "folder", null);
        String type = hash(options, "type", null);
        int limit = limit(options);

        Optional<ShFolder> folder = folderChain == null
                ? currentFolder(options)
                : folderByChain(siteId, folderChain);
        if (folder.isEmpty()) {
            return options.inverse();
        }

        List<Map<String, Object>> rows = rowsOf(folder.get(), type, limit, state(options));
        if (rows.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (Map<String, Object> row : rows) {
            out.append(options.fn(row));
        }
        return out;
    }

    /**
     * {@code {{#navigation}}…{{/navigation}}} — the site's top-level folders, in the
     * position order the console shows, each row {@code {name, url, id}}.
     *
     * <p>{@code folder="/docs"} narrows it to that folder's children, which is FSP's
     * {@code navigationFolder} without a second helper name to remember.</p>
     */
    Object navigation(Object context, Options options) throws IOException {
        String siteId = string(options.context.get("site.id"));
        if (siteId == null) {
            return options.inverse();
        }
        String folderChain = hash(options, "folder", null);
        Optional<ShFolder> parent = folderChain == null
                ? rootFolder(siteId)
                : folderByChain(siteId, folderChain);
        if (parent.isEmpty()) {
            return options.inverse();
        }
        List<ShFolder> children =
                new ArrayList<>(shFolderRepository.findByParentFolderOrderByPositionAsc(parent.get()));
        if (children.isEmpty()) {
            return options.inverse();
        }
        StringBuilder out = new StringBuilder();
        for (ShFolder child : children) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", child.getId());
            row.put("name", child.getName());
            row.put("title", child.getName());
            out.append(options.fn(ShStableMap.copyOf(row)));
        }
        return out;
    }

    /**
     * {@code {{file_url FIELD}}} — a {@code File} field's post id as the public byte path
     * SH38 serves it on. The 2018 navigation region registered a {@code file_url} helper
     * of its own in JavaScript for exactly this; it is one line of Java and no engine.
     *
     * <p>An id that resolves to nothing renders as the empty string rather than as a
     * broken path: a {@code <img src="/file_source/undefined/…">} is a 404 the browser
     * reports and the author does not.</p>
     */
    Object fileUrl(Object context, Options options) {
        String id = string(context);
        if (id == null || id.isBlank()) {
            return "";
        }
        return shPostUnifiedRepository.findById(id).stream()
                .findFirst()
                .map(this::bytePath)
                .orElse("");
    }

    private String bytePath(ShPostUnified filePost) {
        Map<String, Object> fields = shRenderModel.fieldsOf(filePost);
        Object name = fields.get("FILE");
        return name == null ? "" : "/file_source/" + filePost.getId() + "/" + name;
    }

    // --- resolution -------------------------------------------------------------

    private Optional<ShFolder> rootFolder(String siteId) {
        ShSite site = shSiteRepository.findById(siteId).orElse(null);
        if (site == null) {
            return Optional.empty();
        }
        // rootFolder is a byte flag on ShFolder; a site has exactly one.
        return shFolderRepository.findByShSiteAndRootFolder(site, (byte) 1).stream().findFirst();
    }

    /**
     * Resolves a name chain through {@link ShAddressResolver} rather than walking it here
     * — P10, and it means a template's idea of where {@code /docs/guides} is cannot drift
     * from the agent surface's.
     */
    private Optional<ShFolder> folderByChain(String siteId, String chain) {
        ShSite site = shSiteRepository.findById(siteId).orElse(null);
        if (site == null) {
            return Optional.empty();
        }
        return shAddressResolver.find(ShAddress.parse("folder:" + site.getName() + "/" + trim(chain)))
                .flatMap(resolved -> shFolderRepository.findById(resolved.id()));
    }

    private Optional<ShFolder> currentFolder(Options options) {
        String postId = string(options.context.get("post.id"));
        if (postId == null) {
            return Optional.empty();
        }
        return shPostUnifiedRepository.findById(postId).stream()
                .findFirst()
                .map(ShPostUnified::getShFolder);
    }

    private List<Map<String, Object>> rowsOf(ShFolder folder, String type, int limit, ShPostState state) {
        // DRAFT-preferred by id, the same rule /agent/read and the console listing use: a
        // preview must show the edit that has not been published yet, or it is not a preview.
        Map<String, ShPostUnified> byId = new LinkedHashMap<>();
        for (ShPostUnified post : shPostUnifiedRepository
                .findByShFolderAndStateIn(folder, List.of(ShPostState.PUBLISHED, ShPostState.DRAFT))) {
            if (state == ShPostState.PUBLISHED && post.getState() != ShPostState.PUBLISHED) {
                continue;
            }
            ShPostUnified held = byId.get(post.getId());
            if (held == null || post.getState() == ShPostState.DRAFT) {
                byId.put(post.getId(), post);
            }
        }
        return byId.values().stream()
                .filter(post -> type == null || type.equals(typeNameOf(post)))
                // A total order: date alone ties for two posts written in the same
                // millisecond, and a listing that reorders itself between renders moves
                // the SH101 digest for a change nobody made.
                .sorted(Comparator
                        .comparing((ShPostUnified p) -> p.getDate(),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ShPostUnified::getId))
                .limit(limit)
                .map(this::row)
                .toList();
    }

    private String typeNameOf(ShPostUnified post) {
        return post.getShPostType() == null ? null : post.getShPostType().getName();
    }

    /** A row is the post's fields plus the four things a listing always writes. */
    private Map<String, Object> row(ShPostUnified post) {
        Map<String, Object> row = new LinkedHashMap<>(shRenderModel.fieldsOf(post));
        row.put("id", post.getId());
        row.put("title", post.getTitle());
        row.put("summary", post.getSummary());
        row.put("url", post.getFurl());
        // `link` is the site-relative address a delivered page would use. It is the post's
        // friendly URL and not a preview path on purpose: the markup a template produces
        // has to be the markup the front end serves, or the preview is proving the wrong
        // page. The preview route rewrites nothing.
        row.put("link", post.getFurl());
        row.put("type", typeNameOf(post));
        return ShStableMap.copyOf(row);
    }

    // --- option reading ---------------------------------------------------------

    private ShPostState state(Options options) {
        return "PUBLISHED".equals(string(options.context.get("render.state")))
                ? ShPostState.PUBLISHED
                : ShPostState.DRAFT;
    }

    private int limit(Options options) {
        Object raw = options.hash("limit");
        if (raw instanceof Number number) {
            return Math.clamp(number.intValue(), 1, MAX_LIMIT);
        }
        if (raw != null) {
            try {
                return Math.clamp(Integer.parseInt(raw.toString().trim()), 1, MAX_LIMIT);
            } catch (NumberFormatException e) {
                return DEFAULT_LIMIT;
            }
        }
        return DEFAULT_LIMIT;
    }

    private String hash(Options options, String key, String fallback) {
        String value = string(options.hash(key));
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }

    private String trim(String chain) {
        String trimmed = chain.trim();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}
