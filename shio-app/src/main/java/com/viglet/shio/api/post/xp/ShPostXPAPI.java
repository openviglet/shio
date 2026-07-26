/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.xp;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Post "XP" resolution endpoint ({@code /api/v2/post/xp/{id}}) — rebuilt on the
 * unified model. The React content widgets (Content Select, File) call it to
 * resolve a referenced post's title and containing folder for display.
 *
 * <p>Returns {@code { shPost, allowPublish }} for the editable (DRAFT-preferred,
 * else PUBLISHED) snapshot, matching the shape the legacy endpoint exposed.</p>
 *
 * @author Alexandre Oliveira
 */
@RestController
@RequestMapping("/api/v2/post/xp")
@Tag(name = "Post XP", description = "Post resolution for content references")
public class ShPostXPAPI {

    private final ShPostUnifiedRepository shPostUnifiedRepository;

    public ShPostXPAPI(ShPostUnifiedRepository shPostUnifiedRepository) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ShPostXP> get(@PathVariable String id) {
        ShPostUnified post = shPostUnifiedRepository.findByIdAndState(id, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(id, ShPostState.PUBLISHED))
                .orElse(null);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }
        // Initialize the (LAZY) folder + its site inside the tx so they serialize
        // for the widget's display/path resolution (open-in-view is off).
        ShFolder folder = post.getShFolder();
        if (folder != null) {
            folder.getName();
            // ShFolderSite-exempt: this forces the LAZY proxy on the association so it
            // survives serialization; it is not resolving ownership. A nested folder has
            // nothing to initialize here, which is the correct outcome — the console
            // reads the owning site from the breadcrumb path, not from this field.
            ShSite site = folder.getShSite();
            if (site != null) {
                site.getName();
            }
        }
        return ResponseEntity.ok(new ShPostXP(post, post.getState() == ShPostState.DRAFT));
    }

    /** Editing envelope expected by the console: the post plus a publish-permission flag. */
    public record ShPostXP(ShPostUnified shPost, boolean allowPublish) {
    }
}
