/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.repository.post;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // --- Scheduled publish/unpublish (SH15) sweep finders ---

    /** Rows of {@code state} whose publish schedule is set and now due. */
    List<ShPostUnified> findByStateAndPublishScheduledDateLessThanEqual(ShPostState state, Instant now);

    /** Rows of {@code state} whose unpublish schedule is set and now due. */
    List<ShPostUnified> findByStateAndUnpublishScheduledDateLessThanEqual(ShPostState state, Instant now);

    // --- CDA (SH1) read finders ---

    /** Resolve a post by site + friendly URL; rides {@code idx_post_unified_furl (furl, state)}. */
    Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId, String furl, ShPostState state);

    Page<ShPostUnified> findByShFolderAndState(ShFolder shFolder, ShPostState state, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndState(String siteId, ShPostState state, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndShPostType_NameAndState(String siteId, String postType,
                                                                 ShPostState state, Pageable pageable);

    /**
     * All posts of a given post type (by id) in a given state (tenant-scoped) — used by
     * the SH61 static-file manager. Keyed by id (the {@code post_type_id} FK column on
     * {@code sh_post_unified}), NOT by name: navigating {@code shPostType.name} forces a
     * join onto the {@code @TenantId} JOINED {@code ShObject}/{@code ShPostType} table,
     * which Hibernate's tenant predicate references with an alias that isn't joined
     * ({@code Column "..._.tenant_id" not found}). The FK column needs no join.
     */
    List<ShPostUnified> findByShPostType_IdAndState(String postTypeId, ShPostState state);

    // --- CDA preview (SH5) finders: fetch across a state set so the service can
    //     dedup draft-preferred. Used only by the PREVIEW-environment read path. ---

    List<ShPostUnified> findByShFolderAndStateIn(ShFolder shFolder, Collection<ShPostState> states);

    List<ShPostUnified> findByShSite_IdAndStateIn(String siteId, Collection<ShPostState> states);

    List<ShPostUnified> findByShSite_IdAndShPostType_NameAndStateIn(String siteId, String postType,
                                                                    Collection<ShPostState> states);

    // --- Trash / soft-delete (SH55). Native so they bypass the entity's
    //     @SQLRestriction("deleted_date is null") and can see/clear trashed rows.
    //     Tenant-scoped explicitly since native SQL also bypasses @TenantId. ---

    @Query(value = "select * from sh_post_unified where deleted_date is not null and tenant_id = :tenant",
            nativeQuery = true)
    List<ShPostUnified> findTrash(@Param("tenant") String tenant);

    @Query(value = "select * from sh_post_unified where id = :id and deleted_date is not null and tenant_id = :tenant",
            nativeQuery = true)
    List<ShPostUnified> findTrashedById(@Param("id") String id, @Param("tenant") String tenant);

    @Modifying
    @Query(value = "update sh_post_unified set deleted_date = null where id = :id and tenant_id = :tenant",
            nativeQuery = true)
    int restoreFromTrash(@Param("id") String id, @Param("tenant") String tenant);

    @Modifying
    @Query(value = "delete from sh_post_unified where id = :id and tenant_id = :tenant",
            nativeQuery = true)
    int purge(@Param("id") String id, @Param("tenant") String tenant);

    // --- Content i18n / locales (SH53) ---

    /** All sibling translation rows (across states) sharing a translation group. */
    List<ShPostUnified> findByTranslationGroupId(String translationGroupId);

    /** Resolve a post by site + friendly URL + locale (SH53 locale-aware delivery). */
    Optional<ShPostUnified> findByShSite_IdAndFurlAndStateAndLocale(String siteId, String furl,
                                                                    ShPostState state, String locale);

    // --- Full-text search (SH52). The :q term is pre-lowercased and %-wrapped by
    //     ShCdaService; the predicate matches title, summary and the raw attribute
    //     data CLOB (portable LIKE — keeps the SH26 cross-DB guarantee, unlike
    //     engine-specific FULLTEXT/tsvector). PROD paths are paged; PREVIEW paths
    //     fetch across the state set so the service can dedup draft-preferred. ---

    // summary and data are CLOB-mapped; Hibernate rejects lower() on a CLOB
    // argument, so they are cast to string first. title is a plain VARCHAR.
    String FULLTEXT_PREDICATE =
            "(lower(p.title) like :q or lower(cast(p.summary as string)) like :q "
            + "or lower(cast(p.data as string)) like :q)";

    @Query("select p from ShPostUnified p where p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchByState(@Param("state") ShPostState state, @Param("q") String q, Pageable pageable);

    @Query("select p from ShPostUnified p where p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchByStateIn(@Param("states") Collection<ShPostState> states, @Param("q") String q);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchBySitesAndState(@Param("siteIds") Collection<String> siteIds,
                                              @Param("state") ShPostState state, @Param("q") String q, Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchBySitesAndStateIn(@Param("siteIds") Collection<String> siteIds,
                                                @Param("states") Collection<ShPostState> states, @Param("q") String q);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.name in :postTypes "
            + "and p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchBySiteAndTypesAndState(@Param("siteId") String siteId,
                                                     @Param("postTypes") Collection<String> postTypes,
                                                     @Param("state") ShPostState state, @Param("q") String q,
                                                     Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.name in :postTypes "
            + "and p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchBySiteAndTypesAndStateIn(@Param("siteId") String siteId,
                                                       @Param("postTypes") Collection<String> postTypes,
                                                       @Param("states") Collection<ShPostState> states,
                                                       @Param("q") String q);
}
