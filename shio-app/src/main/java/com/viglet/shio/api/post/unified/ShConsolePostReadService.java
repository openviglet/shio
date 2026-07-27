/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.unified;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.unified.dto.ShConsolePost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

/**
 * The console's post reads (SH176), mapped inside a transaction.
 *
 * <p>The transaction is the load-bearing part. {@code shPostType} and {@code shFolder} are
 * LAZY associations, so mapping them has to happen with a session open — mapping a detached
 * entity is exactly how the post-type reached the browser as {@code null} and every post edit
 * page rendered zero fields.</p>
 *
 * <p>Writes re-read through {@link #get(String, ShPostState)} instead of mapping the entity
 * their service returned, for the same reason: the write's own transaction has closed by the
 * time the controller sees the result, so its associations are proxies. One extra query on a
 * console write is cheaper than a response the form cannot bind to.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
public class ShConsolePostReadService {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShFolderRepository shFolderRepository;
    private final ShPostUnifiedWriteService shPostUnifiedWriteService;
    private final ShConsolePostMapper mapper;

    public ShConsolePostReadService(ShPostUnifiedRepository shPostUnifiedRepository,
                                    ShFolderRepository shFolderRepository,
                                    ShPostUnifiedWriteService shPostUnifiedWriteService,
                                    ShConsolePostMapper mapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<ShConsolePost> get(String id, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state).map(mapper::toConsolePost);
    }

    /** The written row, in the console's shape. Falls back to the sibling state when the write moved it. */
    @Transactional(readOnly = true)
    public ShConsolePost written(ShPostUnified written) {
        if (written == null) {
            return null;
        }
        ShPostState state = written.getState() != null ? written.getState() : ShPostState.DRAFT;
        return get(written.getId(), state)
                .or(() -> get(written.getId(), other(state)))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ShConsolePost> versions(String id) {
        return mapper.toConsolePosts(shPostUnifiedRepository.findById(id));
    }

    @Transactional(readOnly = true)
    public List<ShConsolePost> listByFolder(String folderId, ShPostState state) {
        return shFolderRepository.findById(folderId)
                .map(folder -> mapper.toConsolePosts(
                        shPostUnifiedRepository.findByShFolderAndState(folder, state)))
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<ShConsolePost> trash() {
        return mapper.toConsolePosts(shPostUnifiedWriteService.listTrash());
    }

    @Transactional(readOnly = true)
    public List<ShConsolePost> translations(String id) {
        return mapper.toConsolePosts(shPostUnifiedWriteService.listTranslations(id));
    }

    private static ShPostState other(ShPostState state) {
        return state == ShPostState.DRAFT ? ShPostState.PUBLISHED : ShPostState.DRAFT;
    }
}
