/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.agent;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.agent.ShAgentMemoryEntry;

/**
 * Instance-memory rows (SH114).
 *
 * <p>Ordering is part of the contract, not a convenience: the notes are rendered
 * into the context pack, whose {@code digest} is the SH116 entity tag. An unstable
 * order would move the digest on every call and turn every conditional read back
 * into a full one.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShAgentMemoryRepository extends JpaRepository<ShAgentMemoryEntry, String> {

    List<ShAgentMemoryEntry> findAllByOrderByScopeAscMemoryKeyAsc();

    Optional<ShAgentMemoryEntry> findByScopeAndMemoryKey(String scope, String memoryKey);
}
