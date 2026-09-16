/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.query;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

/**
 * Turns the names in a {@link ShContentQuery} into the ids the predicate binds (SH1022).
 *
 * <p>Small on purpose. The point is not the lookup — it is that one place decides what
 * {@code type=Artcle} does, so the console cannot answer it with an empty listing while the agent
 * refuses it with the list of names. A filter that fails open returns every row and one that
 * fails closed returns none, and the two surfaces answering differently is how a curator and an
 * agent come to disagree about what is in the folder.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShContentQueryResolver {

    private final ShPostTypeRepository shPostTypeRepository;

    public ShContentQueryResolver(ShPostTypeRepository shPostTypeRepository) {
        this.shPostTypeRepository = shPostTypeRepository;
    }

    /**
     * The post-type a name selects.
     *
     * @throws ShAgentBatchService.UnknownPostTypeException listing the names that do exist, with
     *         the nearest one — the refusal {@code /agent/find} has always given, reused rather
     *         than re-spelled so a typo reads the same wherever it is typed
     */
    public ShPostType requireType(String name) {
        ShPostType postType = shPostTypeRepository.findByName(name.trim());
        if (postType == null) {
            throw new ShAgentBatchService.UnknownPostTypeException(name,
                    shPostTypeRepository.findAll().stream()
                            .map(ShPostType::getName)
                            .filter(candidate -> candidate != null && !candidate.isBlank())
                            .sorted().toList());
        }
        return postType;
    }

    /** The post-type id a query's {@code type} names, or {@code null} when it names none. */
    public String typeId(ShContentQuery query) {
        return query.type() == null || query.type().isBlank() ? null
                : requireType(query.type()).getId();
    }
}
