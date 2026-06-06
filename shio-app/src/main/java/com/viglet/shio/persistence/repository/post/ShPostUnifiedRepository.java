/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.post;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.post.unified.ShPostUnifiedId;

@Repository
public interface ShPostUnifiedRepository extends JpaRepository<ShPostUnified, ShPostUnifiedId> {

    Optional<ShPostUnified> findByIdAndState(String id, ShPostState state);

    List<ShPostUnified> findById(String id);

    List<ShPostUnified> findByShFolderAndState(ShFolder shFolder, ShPostState state);

    List<ShPostUnified> findByState(ShPostState state);

    // --- CDA (SH1) read finders ---

    /** Resolve a post by site + friendly URL; rides {@code idx_post_unified_furl (furl, state)}. */
    Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId, String furl, ShPostState state);

    Page<ShPostUnified> findByShFolderAndState(ShFolder shFolder, ShPostState state, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndState(String siteId, ShPostState state, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndShPostType_NameAndState(String siteId, String postType,
                                                                 ShPostState state, Pageable pageable);

    // --- CDA preview (SH5) finders: fetch across a state set so the service can
    //     dedup draft-preferred. Used only by the PREVIEW-environment read path. ---

    List<ShPostUnified> findByShFolderAndStateIn(ShFolder shFolder, Collection<ShPostState> states);

    List<ShPostUnified> findByShSite_IdAndStateIn(String siteId, Collection<ShPostState> states);

    List<ShPostUnified> findByShSite_IdAndShPostType_NameAndStateIn(String siteId, String postType,
                                                                    Collection<ShPostState> states);
}
