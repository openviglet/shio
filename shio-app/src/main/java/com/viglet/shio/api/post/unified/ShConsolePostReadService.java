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
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.unified.dto.ShConsolePost;
import com.viglet.shio.migration.unified.ShPostUnifiedWriteService;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.folder.ShFolderRepository;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.post.ShTranslationStaleness;

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
    /** SH1058 — whether a translation still matches the source it was written from. */
    private final ShTranslationStaleness shTranslationStaleness;

    public ShConsolePostReadService(ShPostUnifiedRepository shPostUnifiedRepository,
                                    ShFolderRepository shFolderRepository,
                                    ShPostUnifiedWriteService shPostUnifiedWriteService,
                                    ShConsolePostMapper mapper,
                                    ShTranslationStaleness shTranslationStaleness) {
        this.shTranslationStaleness = shTranslationStaleness;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shFolderRepository = shFolderRepository;
        this.shPostUnifiedWriteService = shPostUnifiedWriteService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<ShConsolePost> get(String id, ShPostState state) {
        return shPostUnifiedRepository.findByIdAndState(id, state)
                .map(row -> mapper.toConsolePost(row)
                        // SH1058 — one row, so at most one extra read, and only for a row that
                        // carries a stamp: a post that is not a translation asks nothing.
                        .withTranslationStale(shTranslationStaleness.isStale(row) ? Boolean.TRUE : null));
    }

    /**
     * Which states this id exists in, ordered, for a 404 that can say why it is one (SH921).
     *
     * <p>A read is {@code findByIdAndState}, so an id that exists only as {@code PUBLISHED}
     * answers nothing for {@code ?state=DRAFT} — and a bare 404 makes that indistinguishable
     * from an id nobody has ever heard of. The caller has to guess between a wrong id, a wrong
     * state and a deleted row, and the console guessed too: its post editor rendered an empty
     * form behind two failed requests.
     *
     * <p>Deliberately <b>not</b> a fallback to the sibling state. {@link #written} does that,
     * because there the caller has just written the row and wants whatever it became; here the
     * caller named a state, and answering with a different one is a lie a client cannot detect.
     * The states are reported so the caller can ask again, on purpose.
     */
    @Transactional(readOnly = true)
    public List<ShPostState> statesOf(String id) {
        return shPostUnifiedRepository.findById(id).stream()
                .map(ShPostUnified::getState)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
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

    /**
     * A page's siblings, each saying whether it has fallen behind the source (SH1058).
     *
     * <p>The group's source is read <b>once</b> for the whole list rather than per row: every
     * member shares one group by construction, so the per-row shape would be the same query as
     * many times as the page has languages.</p>
     */
    @Transactional(readOnly = true)
    public List<ShConsolePost> translations(String id) {
        List<ShPostUnified> rows = shPostUnifiedWriteService.listTranslations(id);
        if (rows.isEmpty()) {
            return List.of();
        }
        Optional<String> source = shTranslationStaleness.sourceDigest(rows.getFirst().getTranslationGroupId());
        return rows.stream()
                .map(row -> mapper.toConsolePost(row).withTranslationStale(staleAgainst(row, source)))
                .toList();
    }

    /** Whether this row's stamp differs from the source digest already in hand. */
    private static Boolean staleAgainst(ShPostUnified row, Optional<String> sourceDigest) {
        return row.getSourceDigest() != null
                && ShTranslationStaleness.isTranslation(row)
                && sourceDigest.filter(current -> !current.equals(row.getSourceDigest())).isPresent()
                ? Boolean.TRUE : null;
    }

    private static ShPostState other(ShPostState state) {
        return state == ShPostState.DRAFT ? ShPostState.PUBLISHED : ShPostState.DRAFT;
    }
}
