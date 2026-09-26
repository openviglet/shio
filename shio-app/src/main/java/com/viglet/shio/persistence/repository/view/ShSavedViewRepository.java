/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.view;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.view.ShSavedView;

/**
 * Saved views (SH1024).
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Repository
public interface ShSavedViewRepository extends JpaRepository<ShSavedView, String> {

    /** One list's views a person can open: their own and the shared ones, by name then id (P5). */
    @Query("select v from ShSavedView v where v.listKey = :listKey "
            + "and (v.owner = :owner or v.shared = true) order by lower(v.name), v.id")
    List<ShSavedView> visible(String listKey, String owner);

    Optional<ShSavedView> findByOwnerAndListKeyAndName(String owner, String listKey, String name);
}
