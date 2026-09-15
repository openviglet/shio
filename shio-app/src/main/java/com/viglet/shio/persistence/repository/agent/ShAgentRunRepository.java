/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.agent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.agent.ShAgentRun;

/**
 * The runs this instance has been introduced to (SH999).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShAgentRunRepository extends JpaRepository<ShAgentRun, String> {

    Optional<ShAgentRun> findById(String id);

    /**
     * The runs a page of the feed names, in one query.
     *
     * <p>One lookup for the page rather than one per row, the way the feed already memoizes
     * addresses: twenty rows of one run is one run, and a name resolved per row is nineteen queries
     * answering a question already answered.</p>
     */
    List<ShAgentRun> findByIdIn(Collection<String> ids);
}
