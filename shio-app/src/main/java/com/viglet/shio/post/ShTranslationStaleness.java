/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.post;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * SH1058 — whether a translation still matches the source it was written from.
 *
 * <h2>One reading of the rule, not four</h2>
 *
 * <p>Four surfaces ask this question — the write that stamps it, {@code verify}, the agent's read
 * and the console — and the rule has two halves that have to agree: <b>which post is the source</b>
 * (the group's anchor, the member whose id is the group id, SH383) and <b>which version of it</b>
 * (draft-preferred: the text a curator editing the source is looking at). Four copies of that would
 * be four opinions, and the symptom of a disagreement is a badge that contradicts a report.</p>
 *
 * <p>Absent is the answer for everything that is not a translation, for a translation written before
 * the column existed, and for a group whose source is gone — the last being SH348's broken link
 * rather than staleness.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShTranslationStaleness {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShTranslationStaleness(ShPostUnifiedRepository shPostUnifiedRepository,
                                  ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    /** Whether this row is a translation of another post rather than the source of its own group. */
    public static boolean isTranslation(ShPostUnified post) {
        String group = post.getTranslationGroupId();
        return group != null && !group.isBlank() && !group.equals(post.getId());
    }

    /** The digest of a group's source as it reads now, or empty when there is no source to read. */
    @Transactional(readOnly = true)
    public Optional<String> sourceDigest(String translationGroupId) {
        if (translationGroupId == null || translationGroupId.isBlank()) {
            return Optional.empty();
        }
        return source(translationGroupId).map(this::digestOf);
    }

    /** The digest of a source already in hand, so a caller holding the row pays no query. */
    public String digestOf(ShPostUnified source) {
        return ShDocumentDigest.of(source.getData(), objectMapper);
    }

    /**
     * Whether this translation was written against a version of its source that has since changed.
     *
     * @return true only when both digests are known and differ; false for everything else, which
     *         includes "not a translation" and "never stamped"
     */
    @Transactional(readOnly = true)
    public boolean isStale(ShPostUnified post) {
        if (post == null || post.getSourceDigest() == null || !isTranslation(post)) {
            return false;
        }
        return sourceDigest(post.getTranslationGroupId())
                .filter(current -> !current.equals(post.getSourceDigest()))
                .isPresent();
    }

    /** The source of a group, draft-preferred, or empty. */
    private Optional<ShPostUnified> source(String translationGroupId) {
        return shPostUnifiedRepository.findByIdAndState(translationGroupId, ShPostState.DRAFT)
                .or(() -> shPostUnifiedRepository.findByIdAndState(translationGroupId,
                        ShPostState.PUBLISHED));
    }
}
