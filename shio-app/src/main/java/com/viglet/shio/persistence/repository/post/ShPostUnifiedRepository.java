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
import java.util.Set;

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

    /**
     * SH207 — the state rows of these posts, in one query: {@code [id, state, modified, published]}.
     *
     * <h2>Why the dates and not just the states</h2>
     *
     * <p>"Is there a {@code DRAFT} row" is <b>not</b> the same question as "is there unpublished
     * work", and the difference is the whole reason this exists. {@link #publish} writes a
     * {@code PUBLISHED} row and <em>keeps</em> the draft — the draft is the editable copy, so every
     * post that has ever existed has one forever. A count built on the draft's presence therefore
     * never goes down, which is what {@code ShReviewSession.pending} does today.</p>
     *
     * <p>The honest test needs both rows: a draft with no published twin has never been published,
     * and a draft modified after its twin's publication has edits nobody has released.</p>
     *
     * <h2>Why one query</h2>
     *
     * <p>{@link #findByIdAndState} answers this one id at a time, which is what the review queue's
     * summary does once per audit row. Affordable on a page somebody opened deliberately; not on
     * what a navigation badge asks for on every render.</p>
     *
     * <p>Columns and not entities: fetching the rows would drag every field and a lazy site across
     * to compare two instants.</p>
     */
    @Query("select p.id, p.state, p.modifiedDate, p.publicationDate, p.date from ShPostUnified p "
            + "where p.id in :ids")
    List<Object[]> findStateRowsOf(@Param("ids") Collection<String> ids);

    List<ShPostUnified> findById(String id);

    List<ShPostUnified> findByShFolderAndState(ShFolder shFolder, ShPostState state);

    List<ShPostUnified> findByState(ShPostState state);

    // --- Scheduled publish/unpublish (SH15) sweep finders ---

    /** Rows of {@code state} whose publish schedule is set and now due. */
    List<ShPostUnified> findByStateAndPublishScheduledDateLessThanEqual(ShPostState state, Instant now);

    /** Rows of {@code state} whose unpublish schedule is set and now due. */
    List<ShPostUnified> findByStateAndUnpublishScheduledDateLessThanEqual(ShPostState state, Instant now);

    // --- CDA (SH1) read finders ---

    /**
     * Every post claiming this friendly URL in this state, by id — SH321.
     *
     * <p>A {@code List}, and ordered, because a friendly URL is <b>not</b> unique by construction:
     * nothing in the schema prevents two posts in one site from claiming it, which is why
     * {@code duplicate-url} has been an SH99 <em>error</em> since SH100. The derived
     * {@code Optional} finder this replaces threw {@code IncorrectResultSizeDataAccessException}
     * when that happened, so a content defect the instance itself reports came out of eight call
     * sites as a <b>500</b> — the server knowing exactly what went wrong and saying the least
     * useful thing available (SH189's bodiless 400, one layer down).</p>
     *
     * <p>Rides {@code idx_post_unified_furl (furl, state)}.</p>
     */
    List<ShPostUnified> findByShSite_IdAndFurlAndStateOrderByIdAsc(String siteId, String furl,
                                                                   ShPostState state);

    /**
     * The post at a friendly URL, deterministically, for readers that must serve something.
     *
     * <p>Takes the first of the ordered list, which is SH278's rule applied to a second identity:
     * a duplicate resolves <b>consistently</b> wrongly rather than alternating between requests.
     * Consistently wrong is a defect somebody recognises and {@code verify} already names; a
     * coin-flip is one that reads as flakiness in delivery.</p>
     *
     * <p>A <em>write</em> path must not use this — resolving to whichever post sorted first is how
     * an edit lands on the wrong one. {@code ShAddressResolver} refuses instead.</p>
     */
    default Optional<ShPostUnified> findByShSite_IdAndFurlAndState(String siteId, String furl,
                                                                   ShPostState state) {
        return findByShSite_IdAndFurlAndStateOrderByIdAsc(siteId, furl, state).stream().findFirst();
    }

    Page<ShPostUnified> findByShFolderAndState(ShFolder shFolder, ShPostState state, Pageable pageable);

    /**
     * One folder's posts of one post-type, windowed (SH419).
     *
     * <p>Keyed by post-type <b>id</b> and never by name, for the {@code @TenantId} reason spelled
     * out on {@link #findByShPostType_IdAndState}: {@code post_type_id} is a column on this table,
     * while navigating to {@code shPostType.name} joins the JOINED {@code ShObject} root and dies at
     * query-prepare time. The renderer's {@code {{#query}}} takes a post-type <em>name</em> and
     * resolves it to an id first, exactly as the CDA does.</p>
     */
    Page<ShPostUnified> findByShFolderAndShPostType_IdAndState(ShFolder shFolder, String postTypeId,
                                                              ShPostState state, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndState(String siteId, ShPostState state, Pageable pageable);

    /**
     * Posts of one post-type in a site. Keyed by post-type <b>id</b>, never by name —
     * see the {@code @TenantId} note on {@link #findByShPostType_IdAndState}. The CDA
     * takes a post-type *name* from the caller and resolves it to an id first.
     */
    Page<ShPostUnified> findByShSite_IdAndShPostType_IdAndState(String siteId, String postTypeId,
                                                               ShPostState state, Pageable pageable);

    // --- SH1057: the three paged delivery listings above, in one locale. A delivery read that
    //     asks for pt-br pages the pt-br rows, so its `totalPosts` counts what it can page through
    //     rather than a total the locale then thins out in memory. ---

    Page<ShPostUnified> findByShFolderAndStateAndLocale(ShFolder shFolder, ShPostState state,
                                                        String locale, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndStateAndLocale(String siteId, ShPostState state,
                                                         String locale, Pageable pageable);

    Page<ShPostUnified> findByShSite_IdAndShPostType_IdAndStateAndLocale(String siteId, String postTypeId,
                                                                         ShPostState state, String locale,
                                                                         Pageable pageable);

    /**
     * How many rows point at this post-type, both states counted (SH163's rename report).
     *
     * <p>Keyed by <b>id</b> for the {@code @TenantId} reason spelled out on
     * {@link #findByShPostType_IdAndState}: {@code post_type_id} is a column on this table, and
     * navigating to {@code shPostType.name} would join the JOINED {@code ShObject} root and die
     * at prepare time.</p>
     */
    long countByShPostType_Id(String postTypeId);

    /**
     * Every row pointing at this post-type, <b>trash included</b> (SH236).
     *
     * <p>Native because {@code ShPostUnified} carries {@code @SQLRestriction("deleted_date is
     * null")}, so the derived counter above cannot see a trashed post — and a trashed post still
     * holds its {@code post_type_id}, so it still blocks the delete. A refusal that answered
     * "used by 0 posts" and then let the delete fail on a constraint would be worse than no
     * refusal at all. Native SQL bypasses {@code @TenantId} too, hence the explicit tenant.</p>
     */
    @Query(value = "select count(*) from sh_post_unified where post_type_id = :postTypeId "
            + "and tenant_id = :tenant", nativeQuery = true)
    long countAllByPostTypeIncludingTrash(@Param("postTypeId") String postTypeId,
                                          @Param("tenant") String tenant);

    /**
     * All posts of a given post type (by id) in a given state (tenant-scoped) — used by
     * the SH61 static-file manager. Keyed by id (the {@code post_type_id} FK column on
     * {@code sh_post_unified}), NOT by name: navigating {@code shPostType.name} forces a
     * join onto the {@code @TenantId} JOINED {@code ShObject}/{@code ShPostType} table,
     * which Hibernate's tenant predicate references with an alias that isn't joined
     * ({@code Column "..._.tenant_id" not found}). The FK column needs no join.
     */
    List<ShPostUnified> findByShPostType_IdAndState(String postTypeId, ShPostState state);

    /**
     * Posts whose {@code data} JSON contains {@code fileId} (SH62 "used in"): the
     * {@code File}/{@code Content Select} widgets store a referenced post's id as a
     * plain string in {@code data}, so a substring match on the (UUID) id finds every
     * referrer with no false positives. Excludes the file post itself; tenant-scoped
     * via {@code @TenantId}. The state filter lets the caller dedupe draft-preferred.
     */
    @Query("select p from ShPostUnified p where p.id <> :fileId and p.state = :state "
            + "and p.data like concat('%', :fileId, '%')")
    List<ShPostUnified> findReferencingFileId(@Param("fileId") String fileId, @Param("state") ShPostState state);

    /**
     * Posts that belong to no site (SH99's {@code orphan-post} lint rule).
     *
     * <p>Exists because this row is invisible to every other finder here: it is not in
     * any site's listing, the CDA cannot deliver it and the SH79 resolver cannot address
     * it, so the only way to see it is to ask for exactly it. That is the SH128 defect's
     * signature, and a lint that cannot see the defect class its own guard was written
     * for is not much of a lint.</p>
     *
     * <p>{@code p.shSite is null} compiles to {@code site_id is null} on this table — a
     * null check on the FK column, which needs no join and therefore does not trip the
     * SH22 tenant-join hazard documented above.</p>
     */
    /**
     * One post's folder id, for the ACL walk that must not need a session (SH225).
     *
     * <p>{@code p.shFolder.id} and no further — the target is an {@code ShObject} subclass, so
     * navigating past {@code .id} would emit the tenant predicate against an alias that has no
     * such column. {@code distinct} because a post is two rows and both carry the same folder.</p>
     */
    @Query("select distinct p.shFolder.id from ShPostUnified p where p.id = :id "
            + "and p.shFolder is not null")
    Optional<String> findFolderIdOf(@Param("id") String id);

    @Query("select p from ShPostUnified p where p.shSite is null and p.state in :states")
    List<ShPostUnified> findWithoutSite(@Param("states") Collection<ShPostState> states);

    // --- CDA preview (SH5) finders: fetch across a state set so the service can
    //     dedup draft-preferred. Used only by the PREVIEW-environment read path. ---

    List<ShPostUnified> findByShFolderAndStateIn(ShFolder shFolder, Collection<ShPostState> states);

    /**
     * One folder's posts as sort keys only — no {@code data}, no entity (SH419).
     *
     * <h3>The measurement this exists to move</h3>
     *
     * <p>{@code ShRenderListingPageBenchmarkTest} recorded <b>213 entity rows loaded to render
     * 10</b>, identical for page 1 and page 19: the listing reads the whole folder and the window is
     * applied afterwards in memory, so the cost grows with the folder rather than with the page and
     * a 5000-post archive pays 5000 rows for the first ten of them. Every row past the tenth is
     * loaded, hydrated with its JSON {@code data}, and thrown away.</p>
     *
     * <h3>Why a projection and not a windowed query</h3>
     *
     * <p>SH384 deferred the window for three reasons and all three are still true. The
     * draft-preference fold reads both states and keeps one row per id, so a window applied before
     * it can return fewer rows than asked for. The pager's totals are exact and come from the whole
     * matching set. And the {@code field=} filter is a predicate over the post's JSON, evaluated in
     * Java.</p>
     *
     * <p>There is a fourth that only appears when you try it, and it is the one that decides the
     * shape: <b>the sort cannot be pushed down portably.</b> {@code date} is nullable and the
     * comparator is {@code nullsLast}, so the ORDER BY needs explicit null placement — and MariaDB
     * has no {@code NULLS LAST}, while PostgreSQL and MariaDB disagree about where a {@code DESC}
     * null goes by default. A listing whose page boundaries differ by engine is SH101's digest
     * moving for a change nobody made, on one deployment and not another.</p>
     *
     * <p>So the fold, the comparators and both filters stay exactly where they are — <b>no semantic
     * changes at all</b> — and what changes is what they operate on. These tuples carry only what
     * the fold and the sort read; the ≤{@code limit} rows that survive are then loaded whole in one
     * {@code IN} query. Two statements instead of one, and the entity count stops scaling with the
     * folder.</p>
     *
     * <p>{@code p.shPostType.id} and not {@code .name}: navigating past {@code .id} of an
     * {@code ShObject} subclass emits the tenant predicate against a subclass alias that has no such
     * column, which is valid JPQL that dies at query-prepare time. The renderer resolves the name.</p>
     */
    @Query("""
            select new com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository$ShListingKey(
                p.id, p.state, p.date, p.title, p.shPostType.id)
            from ShPostUnified p
            where p.shFolder = :folder and p.state in :states""")
    List<ShListingKey> findListingKeys(@Param("folder") ShFolder folder,
                                       @Param("states") Collection<ShPostState> states);

    /**
     * Everything the fold, the sort and the type filter read about a post, and nothing else.
     *
     * @param id         the post id — the fold's key
     * @param state      which row this is, so the winner can be re-read by (id, state)
     * @param date       the default sort key
     * @param title      the {@code sort="title"} key
     * @param postTypeId the {@code type=} filter's key, by id because the name would join
     */
    record ShListingKey(String id, ShPostState state, Instant date, String title, String postTypeId) {
    }

    /** The window's rows, loaded whole, in one statement rather than one per row (SH419). */
    List<ShPostUnified> findByIdInAndState(Collection<String> ids, ShPostState state);

    /** SH1026 — these posts in either state, in one statement: the images a listing page points at. */
    List<ShPostUnified> findByIdIn(Collection<String> ids);

    /**
     * One page of a listing, decided <b>in the query</b> (SH634).
     *
     * <h3>What SH419 left, measured</h3>
     *
     * <p>SH419 took entity loads from 213-per-10 to 2 by folding and sorting over
     * {@link #findListingKeys}' tuples and reading only the survivors whole. The scan stayed:
     * {@code ShRenderListingPageBenchmarkTest} measures <b>228 rows returned to render 10</b>,
     * identical for page 1 and page 19, because the key query still reads the whole folder. Cheap
     * tuples — five scalar columns, no JSON — but O(folder), and invisible to the instrument that
     * proved SH419, since {@code getEntityLoadCount()} does not count a projection.</p>
     *
     * <h3>The fold, in one predicate that is correct for both renders</h3>
     *
     * <p>{@code p.state = :preferred or not exists (a :preferred row for this id in this folder)}.
     * For a <b>draft</b> render ({@code states} = both, {@code preferred} = {@code DRAFT}) that keeps
     * the draft when there is one and the published row otherwise — exactly
     * {@code ShRenderState.preferDraftKeys}. For a <b>published</b> render ({@code states} =
     * {@code PUBLISHED} only, {@code preferred} = {@code PUBLISHED}) the left side is true for every
     * row, so the predicate costs nothing and excludes nothing. One query rather than two that can
     * disagree.</p>
     *
     * <h3>Why the ORDER BY spells its own null handling</h3>
     *
     * <p>{@code date} is nullable and the comparator it mirrors is {@code nullsLast}, so the clause
     * needs explicit null placement — and {@code NULLS LAST} is not portable: MariaDB has no such
     * syntax, and it and PostgreSQL disagree about where a {@code DESC} null falls by default. A
     * listing whose page boundaries differ by engine moves SH101's digest for a change nobody made,
     * on one deployment and not another. {@code coalesce(p.date, :floor)} maps a null to an instant
     * below every real one, so {@code DESC} puts nulls last and {@code ASC} puts them first on every
     * engine — which is what the Java comparators do. {@code ShPostgresSchemaIT} and
     * {@code ShMariaDbSchemaIT} are what make that a proof rather than a claim.</p>
     */
    @Query("""
            select new com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository$ShListingKey(
                p.id, p.state, p.date, p.title, p.shPostType.id)
            from ShPostUnified p
            where p.shFolder = :folder and p.state in :states
              and (:typeId is null or p.shPostType.id = :typeId)
              and (p.state = :preferred or not exists (
                    select 1 from ShPostUnified d
                    where d.id = p.id and d.state = :preferred and d.shFolder = :folder))
            order by coalesce(p.date, :floor) desc, p.id asc""")
    List<ShListingKey> findListingWindowNewest(@Param("folder") ShFolder folder,
                                               @Param("states") Collection<ShPostState> states,
                                               @Param("preferred") ShPostState preferred,
                                               @Param("typeId") String typeId,
                                               @Param("floor") Instant floor,
                                               Pageable pageable);

    /**
     * The same window, oldest first (SH634).
     *
     * <p>{@code oldest} is {@code byDateThenId().reversed()} — which reverses the id tiebreak too,
     * so it is {@code date asc, id desc} and still total. Spelled as its own query rather than a
     * {@code Sort}, because the {@code coalesce} the null handling needs is an expression and a
     * derived {@code Sort} can only name properties.</p>
     */
    @Query("""
            select new com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository$ShListingKey(
                p.id, p.state, p.date, p.title, p.shPostType.id)
            from ShPostUnified p
            where p.shFolder = :folder and p.state in :states
              and (:typeId is null or p.shPostType.id = :typeId)
              and (p.state = :preferred or not exists (
                    select 1 from ShPostUnified d
                    where d.id = p.id and d.state = :preferred and d.shFolder = :folder))
            order by coalesce(p.date, :floor) asc, p.id desc""")
    List<ShListingKey> findListingWindowOldest(@Param("folder") ShFolder folder,
                                               @Param("states") Collection<ShPostState> states,
                                               @Param("preferred") ShPostState preferred,
                                               @Param("typeId") String typeId,
                                               @Param("floor") Instant floor,
                                               Pageable pageable);

    /**
     * How many rows that window is a page of (SH634).
     *
     * <p>The pager's totals are exact and always were, so a windowed read owes a second query with
     * the <b>same</b> predicates. Written beside them and asserted against the unwindowed path in
     * {@code ShRenderListingPageTest}, because a count that drifts from its query is a pager that
     * lies — and lies quietly, since the rows on screen still look right.</p>
     */
    @Query("""
            select count(p) from ShPostUnified p
            where p.shFolder = :folder and p.state in :states
              and (:typeId is null or p.shPostType.id = :typeId)
              and (p.state = :preferred or not exists (
                    select 1 from ShPostUnified d
                    where d.id = p.id and d.state = :preferred and d.shFolder = :folder))""")
    long countListingWindow(@Param("folder") ShFolder folder,
                            @Param("states") Collection<ShPostState> states,
                            @Param("preferred") ShPostState preferred,
                            @Param("typeId") String typeId);

    /**
     * One page of a folder's posts, one row per logical id, DRAFT preferred — the console
     * content browser's listing (SH165).
     *
     * <p><b>Why this exists rather than reusing the CDA's dedup.</b> {@code ShCdaService}
     * pages draft-preferred rows by loading the folder whole and slicing the list in memory
     * ({@code draftPreferred}), which makes the <em>response</em> smaller and the query no
     * cheaper. The console's listing is the read this repository's largest folders are opened
     * with, so the dedup is expressed as a predicate instead: a row qualifies if it is the
     * DRAFT, or it is the PUBLISHED row of a post that has no DRAFT. That is exactly
     * one row per logical id, so {@code LIMIT}, {@code ORDER BY} and the count query are the
     * database's job — sorting and paging over the whole folder without loading it.</p>
     *
     * <p>{@code p.shFolder = :folder} compares the association to an entity, which compiles to
     * the {@code folder_id} FK column already on this table: no join, so the SH22
     * {@code @TenantId} JOINED-join hazard documented above does not apply. The correlated
     * subquery is on {@code ShPostUnified} itself, which carries its own {@code tenant_id}.</p>
     *
     * @param folder the parent folder
     * <p><b>The filter is the CDA's own predicate (SH240)</b> — {@link #FULLTEXT_PREDICATE},
     * matching title, summary and the stored field data. It started as a title substring, which
     * was faithful to what the browser's local filter did and made the console's search box
     * quietly weaker than the delivery API: a curator who remembered a phrase from a page's body
     * was told there was nothing there. One predicate, two callers, so the two surfaces cannot
     * come to disagree about what "matches" means.</p>
     *
     * @param like   a lower-cased, {@code %}-wrapped pattern, or {@code null} for all — prefer
     *               {@link #pageConsoleRows(ShFolder, String, Pageable)}, which normalizes for
     *               you (see the SH131 note on {@code FULLTEXT_PREDICATE})
     */
    @Query("select p " + CONSOLE_ROWS)
    Page<ShPostUnified> pageConsoleRowsLike(@Param("folderIds") Collection<String> folderIds,
                                            @Param("like") String like,
                                            @Param("typeId") String typeId,
                                            @Param("state") ShPostState state,
                                            @Param("locale") String locale,
                                            @Param("since") Instant since,
                                            @Param("before") Instant before,
                                            @Param("author") String author,
                                            @Param("modifier") String modifier,
                                            @Param("missingLocale") String missingLocale,
                                            @Param("submission") Boolean submission,
                                            @Param("agent") Boolean agent,
                                            Pageable pageable);

    /**
     * SH1024 — the facet counts of the listing {@link #pageConsoleRowsLike} pages, by post type,
     * locale and row state: one grouped read over the same rows, so the counts are the filtered
     * totals by construction and never a second opinion. Same parameters, raw like the page's.
     */
    @Query("select p.shPostType.id, p.locale, p.state, count(p) " + CONSOLE_ROWS
            + " group by p.shPostType.id, p.locale, p.state")
    List<Object[]> consoleFacetRowsLike(@Param("folderIds") Collection<String> folderIds,
                                        @Param("like") String like,
                                        @Param("typeId") String typeId,
                                        @Param("state") ShPostState state,
                                        @Param("locale") String locale,
                                        @Param("since") Instant since,
                                        @Param("before") Instant before,
                                        @Param("author") String author,
                                        @Param("modifier") String modifier,
                                        @Param("missingLocale") String missingLocale,
                                        @Param("submission") Boolean submission,
                                        @Param("agent") Boolean agent);

    /**
     * SH1024 — how many of those rows are live, which the grouped read cannot say: the listing
     * shows the draft row of a post that has both, so "published" is a question about a sibling.
     */
    @Query("select count(p) " + CONSOLE_ROWS + " and exists (select 1 from ShPostUnified l "
            + "where l.id = p.id and l.state = com.viglet.shio.persistence.model.post.unified.ShPostState.PUBLISHED)")
    long consoleLiveCountLike(@Param("folderIds") Collection<String> folderIds,
                              @Param("like") String like,
                              @Param("typeId") String typeId,
                              @Param("state") ShPostState state,
                              @Param("locale") String locale,
                              @Param("since") Instant since,
                              @Param("before") Instant before,
                              @Param("author") String author,
                              @Param("modifier") String modifier,
                              @Param("missingLocale") String missingLocale,
                              @Param("submission") Boolean submission,
                              @Param("agent") Boolean agent);

    /** {@link #consoleFacetRowsLike} with the page's own normalization (SH131). */
    default List<Object[]> consoleFacetRows(Collection<String> folderIds, String query,
            String typeId, ShPostState state, String locale, Instant since, Instant before,
            String author, String modifier, String missingLocale, Boolean submission,
            Boolean agent) {
        return consoleFacetRowsLike(folderIds,
                query == null || query.isBlank() ? null : fullTextPattern(query), typeId, state,
                blankToNull(locale), since, before, lowerOrNull(author), lowerOrNull(modifier),
                blankToNull(missingLocale), submission, agent);
    }

    /** {@link #consoleLiveCountLike} with the page's own normalization (SH131). */
    default long consoleLiveCount(Collection<String> folderIds, String query,
            String typeId, ShPostState state, String locale, Instant since, Instant before,
            String author, String modifier, String missingLocale, Boolean submission,
            Boolean agent) {
        return consoleLiveCountLike(folderIds,
                query == null || query.isBlank() ? null : fullTextPattern(query), typeId, state,
                blankToNull(locale), since, before, lowerOrNull(author), lowerOrNull(modifier),
                blankToNull(missingLocale), submission, agent);
    }

    /**
     * {@link #pageConsoleRowsLike} with the caller's own spellings normalized: {@code null}/blank
     * means no filter, the term is lower-cased and {@code %}-wrapped, and so are the two people
     * columns. Call this one — the raw query is suffixed {@code Like} because normalizing twice
     * searches for {@code %%term%%} and matches nothing (SH131).
     */
    default Page<ShPostUnified> pageConsoleRows(Collection<String> folderIds, String query,
            String typeId, ShPostState state, String locale, Instant since, Instant before,
            String author, String modifier, Pageable pageable) {
        return pageConsoleRows(folderIds, query, typeId, state, locale, since, before, author,
                modifier, null, null, null, pageable);
    }

    /**
     * As above, narrowed to the groups missing a locale (SH1060), to form submissions or none
     * (SH1066) and to what an agent wrote or none (SH1024); null is no narrowing.
     */
    default Page<ShPostUnified> pageConsoleRows(Collection<String> folderIds, String query,
            String typeId, ShPostState state, String locale, Instant since, Instant before,
            String author, String modifier, String missingLocale, Boolean submission,
            Boolean agent, Pageable pageable) {
        return pageConsoleRowsLike(folderIds,
                query == null || query.isBlank() ? null : fullTextPattern(query), typeId, state,
                blankToNull(locale), since, before, lowerOrNull(author), lowerOrNull(modifier),
                blankToNull(missingLocale), submission, agent, pageable);
    }

    /**
     * Every row matching one content query, across the sites a credential may read (SH1022).
     *
     * <p><b>The predicate that used to be a stream.</b> {@code /agent/find} loaded both state rows
     * of every post in the site and then filtered the list — so a {@code folder=} listing on a
     * ten-thousand-post site read twenty thousand rows to answer with twenty, and a
     * {@code state=published} one read the draft half it was about to discard.
     * {@code ShFindQueryBenchmarkTest} measured it before this existed, which is the order this
     * project requires: the number is the argument for the query, not the other way round.</p>
     *
     * <p>{@code anyFolder} rather than a nullable collection: a JPQL {@code in} over an unbound or
     * empty list is not portable, and the flag makes "no folder scope" a value the query states
     * instead of an accident of binding. Every association is compared through {@code .id}, which
     * is the FK column on this table — the SH22 {@code @TenantId} hazard needs an implicit join,
     * and there is none.</p>
     *
     * @param like     a lower-cased, {@code %}-wrapped pattern, or {@code null} for all — prefer
     *                 {@link #findByQuery}, which normalizes for you (SH131)
     * @param author   a lower-cased owner, or {@code null}
     * @param modifier a lower-cased modifier, or {@code null}
     */
    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state in :states "
            + "and (:anyFolder = true or p.shFolder.id in :folderIds) "
            + "and (:like is null or " + FULLTEXT_PREDICATE + ")" + QUERY_FILTERS)
    List<ShPostUnified> findByQueryLower(@Param("siteIds") Collection<String> siteIds,
                                         @Param("states") Collection<ShPostState> states,
                                         @Param("anyFolder") boolean anyFolder,
                                         @Param("folderIds") Collection<String> folderIds,
                                         @Param("like") String like,
                                         @Param("typeId") String typeId,
                                         @Param("locale") String locale,
                                         @Param("since") Instant since,
                                         @Param("before") Instant before,
                                         @Param("author") String author,
                                         @Param("modifier") String modifier,
                                         @Param("missingLocale") String missingLocale,
                                         @Param("submission") Boolean submission,
                                         @Param("agent") Boolean agent);

    /** {@link #findByQueryLower} with the term and the two people columns normalized once. */
    default List<ShPostUnified> findByQuery(Collection<String> siteIds,
            Collection<ShPostState> states, Collection<String> folderIds, String term,
            String typeId, String locale, Instant since, Instant before, String author,
            String modifier) {
        return findByQuery(siteIds, states, folderIds, term, typeId, locale, since, before, author,
                modifier, null, null, null);
    }

    /**
     * As above, narrowed to the groups missing a locale (SH1060), to form submissions or none
     * (SH1066) and to what an agent wrote or none (SH1024); null is no narrowing.
     */
    default List<ShPostUnified> findByQuery(Collection<String> siteIds,
            Collection<ShPostState> states, Collection<String> folderIds, String term,
            String typeId, String locale, Instant since, Instant before, String author,
            String modifier, String missingLocale, Boolean submission, Boolean agent) {
        boolean anyFolder = folderIds == null || folderIds.isEmpty();
        return findByQueryLower(siteIds, states, anyFolder,
                anyFolder ? List.of("") : folderIds,
                term == null || term.isBlank() ? null : fullTextPattern(term),
                blankToNull(typeId), blankToNull(locale), since, before,
                lowerOrNull(author), lowerOrNull(modifier), blankToNull(missingLocale), submission,
                agent);
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String lowerOrNull(String value) {
        String text = blankToNull(value);
        return text == null ? null : text.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Assets of one type, narrowed by a <b>conservative superset</b> of the media filters (SH1030).
     *
     * <h2>Why a superset rather than the filter itself</h2>
     *
     * <p>Both narrowings are approximate on purpose, and the exact decision stays in the service —
     * which is what makes this safe to add rather than a second definition of "matches".</p>
     *
     * <p>{@code like} is matched against the whole {@code data} document, so a term that appears
     * in an asset's alt text or tags brings its row back even though the media filter matches file
     * <em>names</em>. That is a row read and discarded, never a row returned.</p>
     *
     * <p>{@code contentTypeLike} keeps a row whose stamped type matches <b>or that carries no
     * stamp at all</b>. SH1030 stamps the content type at upload and keeps the name probe as the
     * fallback for everything written before it, so a predicate that dropped unstamped rows would
     * make a whole pre-existing library vanish from every category — silently, and consistently,
     * with every number on screen agreeing.</p>
     *
     * @param like            a lower-cased, {@code %}-wrapped pattern, or {@code null}
     * @param contentTypeLike a pattern for the stamped content type, or {@code null}
     */
    @Query("select p from ShPostUnified p where p.shPostType.id = :typeId and p.state = :state "
            + "and (:folder is null or p.shFolder = :folder) "
            + "and (:like is null or lower(cast(p.data as string)) like :like) "
            + "and (:contentTypeLike is null "
            + "  or lower(cast(p.data as string)) like :contentTypeLike "
            + "  or lower(cast(p.data as string)) not like '%\"content_type\"%')")
    List<ShPostUnified> findAssetsLike(@Param("typeId") String typeId,
                                       @Param("state") ShPostState state,
                                       @Param("folder") ShFolder folder,
                                       @Param("like") String like,
                                       @Param("contentTypeLike") String contentTypeLike);

    /** {@link #findAssetsLike} with the term normalized once (SH131). */
    default List<ShPostUnified> findAssets(String typeId, ShPostState state, ShFolder folder,
                                           String term, String contentTypePrefix) {
        return findAssetsLike(typeId, state, folder,
                term == null || term.isBlank() ? null : fullTextPattern(term),
                contentTypePrefix == null || contentTypePrefix.isBlank() ? null
                        : "%\"content_type\":\"" + contentTypePrefix.toLowerCase(java.util.Locale.ROOT)
                                + "%");
    }

    List<ShPostUnified> findByShSite_IdAndStateIn(String siteId, Collection<ShPostState> states);

    List<ShPostUnified> findByShSite_IdAndShPostType_IdAndStateIn(String siteId, String postTypeId,
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

    /**
     * Trashed posts sitting in any of these folders (SH200) — what restoring or purging a
     * deleted folder subtree operates on. Callers must not pass an empty collection.
     */
    @Query(value = "select * from sh_post_unified where deleted_date is not null "
            + "and folder_id in :folderIds and tenant_id = :tenant", nativeQuery = true)
    List<ShPostUnified> findTrashedByFolderIds(@Param("folderIds") Collection<String> folderIds,
                                              @Param("tenant") String tenant);

    /**
     * How many <b>trashed</b> rows in a site hold this friendly URL (SH243).
     *
     * <p>Native for the {@code @SQLRestriction} reason above, and it exists because a trashed post
     * still holds its {@code furl} — exactly SH236's shape one column along, where a trashed post
     * still held its {@code post_type_id} and a refusal counting only live rows said "0 posts" and
     * then failed on a constraint. Here the symptom is worse than a bad message: {@code freeFurl}
     * derived a URL a sleeping post already owned, so restoring that post produced the
     * {@code duplicate-url} defect SH202 was filed to remove, out of two individually correct
     * operations.</p>
     *
     * <p>A count rather than the rows: the naming loop only asks <em>whether</em> the candidate is
     * taken, and the refusal path needs a <em>live</em> occupant to name (a trashed one is not
     * something the curator can be pointed at). Native SQL bypasses {@code @TenantId}, hence the
     * explicit tenant.</p>
     */
    @Query(value = "select count(*) from sh_post_unified where site_id = :siteId and furl = :furl "
            + "and deleted_date is not null and tenant_id = :tenant", nativeQuery = true)
    long countTrashedByFurl(@Param("siteId") String siteId, @Param("furl") String furl,
                            @Param("tenant") String tenant);

    @Modifying
    @Query(value = "update sh_post_unified set deleted_date = null where id = :id and tenant_id = :tenant",
            nativeQuery = true)
    int restoreFromTrash(@Param("id") String id, @Param("tenant") String tenant);

    @Modifying
    @Query(value = "delete from sh_post_unified where id = :id and tenant_id = :tenant",
            nativeQuery = true)
    int purge(@Param("id") String id, @Param("tenant") String tenant);

    /**
     * The distinct locales a site's content is written in, in this state (SH459).
     *
     * <p>What the public route's locale segment is checked against. {@code p.shSite.id} and never
     * {@code p.shSite} — navigating past {@code .id} of an association whose target is an
     * {@code ShObject} subclass emits the {@code @TenantId} predicate against a subclass alias that
     * has no such column, which is valid JPQL and a runtime failure.</p>
     */
    @Query("select distinct p.locale from ShPostUnified p "
            + "where p.shSite.id = :siteId and p.state = :state and p.locale is not null "
            + "order by p.locale")
    List<String> findDistinctLocalesBySiteId(@Param("siteId") String siteId,
                                             @Param("state") ShPostState state);

    /**
     * SH1056 — the locales several sites' content is written in, in one query.
     *
     * <p>What an undeclared site reports as its policy, and the reason this is not the method
     * above in a loop: the context pack and the site listing both answer for every site a caller
     * can see, and a per-site query there is a query per row on a read P2 promises replaces a
     * session.</p>
     *
     * <p>Both states, unlike {@link #findDistinctLocalesBySiteId}: a draft written in Portuguese
     * means the site is being authored in Portuguese, and a policy that only counted what is
     * published would report a language as absent on the day before it is launched. Deleted rows
     * are excluded — a locale nothing but the recycle bin holds is not one the site offers.</p>
     *
     * @return one row per (site, locale) pair as {@code [siteId, locale]}, ordered
     */
    @Query("select distinct p.shSite.id, p.locale from ShPostUnified p "
            + "where p.shSite.id in :siteIds and p.locale is not null and p.deletedDate is null "
            + "order by p.shSite.id, p.locale")
    List<Object[]> findDistinctLocalesBySiteIdIn(@Param("siteIds") Collection<String> siteIds);

    /**
     * SH1123 — the same read, over only the states a delivery reader can see.
     *
     * <p>The method above counts drafts on purpose, because it answers the author. The CDA answers
     * a front end, and there a locale nothing but a draft is written in is a switcher entry that
     * links to a 404: a production token reads {@code PUBLISHED} alone, so it must not be told the
     * site offers a language it cannot fetch a single page in.</p>
     *
     * @return one row per (site, locale) pair as {@code [siteId, locale]}, ordered
     */
    @Query("select distinct p.shSite.id, p.locale from ShPostUnified p "
            + "where p.shSite.id in :siteIds and p.state in :states and p.locale is not null "
            + "and p.deletedDate is null order by p.shSite.id, p.locale")
    List<Object[]> findDistinctLocalesBySiteIdInAndStateIn(@Param("siteIds") Collection<String> siteIds,
                                                           @Param("states") Collection<ShPostState> states);

    /**
     * SH1130 — which languages each site has actually published, and in which post types.
     *
     * <p>The type is carried because the answer is about <em>content</em>: every row gets a locale
     * on write, templates and assets included, so a site that declares only {@code pt-br} would
     * otherwise be told its layouts are written in {@code en}. The caller drops the system types by
     * name; the id is selected rather than {@code p.shPostType.name}, because navigating past
     * {@code .id} to an {@code ShObject} subclass emits the tenant predicate against an alias with
     * no such column. {@code locale} may be null on a row written before the column existed, and
     * the caller reads that as the writer's default (SH383), as every other reader does.</p>
     *
     * @return rows of {@code [siteId, locale, postTypeId]}, ordered
     */
    @Query("select distinct p.shSite.id, p.locale, p.shPostType.id from ShPostUnified p "
            + "where p.shSite.id in :siteIds and p.state = :state and p.deletedDate is null "
            + "order by p.shSite.id, p.locale, p.shPostType.id")
    List<Object[]> findDistinctLocaleTypesBySiteIdIn(@Param("siteIds") Collection<String> siteIds,
                                                     @Param("state") ShPostState state);

    /**
     * SH1175 — each post's type id, for the posts a rendered page's fields belong to.
     *
     * <p>A section is a post of its own type, and the composer needs that type's widgets to say
     * what kind of field each annotated element is. The id and not {@code p.shPostType.name}, for
     * the tenant-join reason above; a post's type is the same in every state, so {@code distinct}
     * folds its rows into one.</p>
     *
     * @return rows of {@code [postId, postTypeId]}
     */
    @Query("select distinct p.id, p.shPostType.id from ShPostUnified p where p.id in :ids")
    List<Object[]> findPostTypeIdsByIdIn(@Param("ids") Collection<String> ids);

    // --- Content i18n / locales (SH53) ---

    /** All sibling translation rows (across states) sharing a translation group. */
    List<ShPostUnified> findByTranslationGroupId(String translationGroupId);

    /**
     * SH1060 — everything a coverage board reads, as one projection per site.
     *
     * <p>Columns and not entities: the board needs which group, which language, where it is and
     * whether it carries a stamp, and loading the documents to answer that would read a site's
     * whole content to draw a table of links. Both states, because the board describes the page a
     * curator is editing (draft-preferred), as every other console read does.</p>
     *
     * @return rows of {@code [translationGroupId, id, locale, furl, title, sourceDigest, state]}
     */
    @Query("select p.translationGroupId, p.id, p.locale, p.furl, p.title, p.sourceDigest, p.state "
            + "from ShPostUnified p where p.shSite.id = :siteId")
    List<Object[]> findCoverageRowsBySiteId(@Param("siteId") String siteId);

    /**
     * SH1060 — the documents a coverage read has to digest, in one query.
     *
     * <p>Only the groups' sources, and only where some member carries a stamp: a site whose
     * translations predate SH1058 reads no documents at all.</p>
     *
     * @return rows of {@code [id, state, data]}
     */
    @Query("select p.id, p.state, p.data from ShPostUnified p where p.id in :ids")
    List<Object[]> findDocumentsByIdIn(@Param("ids") Collection<String> ids);

    /**
     * SH1129 — the locales several translation groups are in, for a listing in one query.
     *
     * <p>{@link #findByTranslationGroupId} answers one post's switcher and loads whole rows to do
     * it. A delivery list asking the same of every row would be a query per row, so this is the
     * same fact for a page: two columns rather than whole documents.</p>
     *
     * <p><b>SH1128 — over the states the reader can see</b>, which SH1129 deliberately did not do so
     * the two surfaces agreed while that defect was open. {@code translate} creates the sibling as a
     * DRAFT, so counting both states told every production token that a page it reads exists in a
     * language it cannot fetch a single row of — a switcher entry linking to a 404 for as long as the
     * translation was being written. Same rule and same reason as
     * {@link #findDistinctLocalesBySiteIdInAndStateIn} one level up: {@code PUBLISHED} for a
     * production token, both for a preview token. Deleted rows are excluded, because a locale nothing
     * but the recycle bin holds is not one the group is available in.</p>
     *
     * @return one row per (group, locale) pair as {@code [translationGroupId, locale]}
     */
    @Query("select distinct p.translationGroupId, p.locale from ShPostUnified p "
            + "where p.translationGroupId in :groups and p.state in :states "
            + "and p.locale is not null and p.deletedDate is null")
    List<Object[]> findDistinctLocalesByTranslationGroupIdInAndStateIn(
            @Param("groups") Collection<String> groups,
            @Param("states") Collection<ShPostState> states);

    /**
     * Every post claiming this friendly URL in this state <em>and</em> this locale, by id — SH323.
     *
     * <p>SH321's shape one parameter along, and it needed the same fix for the same reason: the
     * derived {@code Optional} finder threw {@code IncorrectResultSizeDataAccessException}, so
     * two posts sharing a URL <em>within one locale</em> reached the CDA's localized read as a
     * <b>500</b>.</p>
     *
     * <h2>Why the locale does not make a shared URL legitimate</h2>
     *
     * <p>The tempting reading is that {@code duplicate-url} should be locale-aware, since two
     * translations at one URL are told apart here. It is the wrong one, and the reason is the
     * address grammar: an SH79 address is {@code post:<site>/<furl>} and carries <b>no locale</b>,
     * so two posts sharing a URL are unaddressable whatever distinguishes them —
     * {@code ShAddressResolver} refuses (SH321), and SH134's occupied-target check on
     * {@code post.move} is locale-blind too. Making the lint locale-aware would leave the agent
     * path <em>refusing</em> content {@code verify} had just called clean, which is the false green
     * this codebase treats as the worst failure available. So the locale-blind rule is the
     * definition: <b>one published URL, one post, per site.</b> What locale-aware delivery is for
     * is a translation with its <em>own</em> URL, plus {@code availableLocales} linking the two.</p>
     */
    List<ShPostUnified> findByShSite_IdAndFurlAndStateAndLocaleOrderByIdAsc(
            String siteId, String furl, ShPostState state, String locale);

    /**
     * Resolve a post by site + friendly URL + locale (SH53 locale-aware delivery).
     *
     * <p>Takes the first of the ordered list for the reason spelled out on
     * {@link #findByShSite_IdAndFurlAndState}: a reader that must serve something serves the same
     * row every time, so a duplicate is a defect somebody recognises rather than one that reads as
     * flakiness in delivery.</p>
     */
    default Optional<ShPostUnified> findByShSite_IdAndFurlAndStateAndLocale(
            String siteId, String furl, ShPostState state, String locale) {
        return findByShSite_IdAndFurlAndStateAndLocaleOrderByIdAsc(siteId, furl, state, locale)
                .stream().findFirst();
    }

    // --- Full-text search (SH52). The predicate matches title, summary and the raw
    //     attribute data CLOB (portable LIKE — keeps the SH26 cross-DB guarantee,
    //     unlike engine-specific FULLTEXT/tsvector). PROD paths are paged; PREVIEW
    //     paths fetch across the state set so the service can dedup draft-preferred.
    //
    //     SH131 — the bind contract is enforced, not documented. The `:q` parameter
    //     must arrive pre-lowercased and %-wrapped, and for two years that requirement
    //     lived only in a comment beside the constant. SH83 read the comment, wrote the
    //     caller, and still got it wrong; the symptom was an *empty result set*, which
    //     reads as "nothing matched" rather than as a bug and therefore survives review
    //     and manual testing alike. So the raw `@Query` methods are now suffixed
    //     `...Like` (their parameter name says what it wants), and every caller uses
    //     the `search*` default methods below, which take a plain user term and
    //     normalize it exactly once. A contract that can only be honoured by
    //     remembering, and whose breach looks like valid data, belongs inside the
    //     method rather than beside it. ---

    // summary and data are CLOB-mapped; Hibernate rejects lower() on a CLOB
    // argument, so they are cast to string first. title is a plain VARCHAR.
    String FULLTEXT_PREDICATE =
            "(lower(p.title) like :like or lower(cast(p.summary as string)) like :like "
            + "or lower(cast(p.data as string)) like :like)";

    /**
     * The SH1022 content-query filters, as one predicate every listing appends.
     *
     * <p>One string and not two copies, for the reason SH240 gave {@code FULLTEXT_PREDICATE}: two
     * surfaces that each spell "modified since" for themselves come to disagree about whether the
     * bound is inclusive, and nothing fails when they do — both answer 200 with a plausible list.
     * {@code state} is <em>not</em> here, because the two callers ask it of different things: the
     * agent's listing answers rows and the console's answers one row per post.</p>
     *
     * <p>{@code coalesce(p.modifiedDate, p.date)} because a post created and never edited has no
     * modified date, and "changed this week" that excludes what was written this week is the
     * answer nobody means. The lower bound is inclusive and the upper exclusive, so two adjacent
     * windows partition the timeline instead of overlapping on one instant.</p>
     */
    /** SH1066 — a row a site's form wrote, spelled from ShFormSubmitter's own constants. */
    String SUBMITTED = "(p.owner = '" + com.viglet.shio.api.form.ShFormSubmitter.UNTOKENED
            + "' or p.owner like '" + com.viglet.shio.api.form.ShFormSubmitter.TOKEN_PREFIX + "%')";

    /** SH1024 — a post an agent run wrote at least once. */
    String AGENT_WROTE = "exists (select 1 from ShHistory h where h.shObject = p.id and "
            + com.viglet.shio.persistence.repository.history.ShHistoryRepository.AGENT_SESSION + ")";

    String QUERY_FILTERS =
            " and (:typeId is null or p.shPostType.id = :typeId)"
            + " and (:locale is null or p.locale = :locale)"
            + " and (:since is null or coalesce(p.modifiedDate, p.date) >= :since)"
            + " and (:before is null or coalesce(p.modifiedDate, p.date) < :before)"
            + " and (:author is null or lower(p.owner) = :author)"
            + " and (:modifier is null or lower(p.modifier) = :modifier)"
            // SH1066 — what a site's form wrote, recognised by the owner the form endpoint records
            // (ShFormSubmitter), in both directions. A submission is a post, so this is a filter on
            // posts and not a table of its own.
            + " and (:submission is null"
            + "   or (:submission = true and " + SUBMITTED + ")"
            + "   or (:submission = false and (p.owner is null or not " + SUBMITTED + ")))"
            // SH1024 — what an agent run wrote, read off the audit trail with the feed's own
            // spelling of "an agent session" (ShHistoryRepository.AGENT_SESSION), so a console
            // session, which is a curator's, never counts. `h.shObject` is a plain id column, so
            // the subquery has no join and no SH22 hazard.
            + " and (:agent is null"
            + "   or (:agent = true and " + AGENT_WROTE + ")"
            + "   or (:agent = false and not " + AGENT_WROTE + "))"
            // SH1060 — "what is missing in pt-br", as a predicate rather than a scan. A group with
            // no member in that locale, answered on the group's SOURCE alone (`p.id =
            // p.translationGroupId`, SH383's anchor): reporting every member would answer one gap
            // as many times as the page already has languages, and the source is the page somebody
            // translates FROM. The subquery is on this table, so no join and no SH22 hazard.
            + " and (:missingLocale is null or (p.id = p.translationGroupId and not exists ("
            + "   select 1 from ShPostUnified t where t.translationGroupId = p.translationGroupId"
            + "     and t.locale = :missingLocale)))";

    /**
     * The console listing's rows (SH165, SH1022): one row per logical post — the draft where there
     * is one — under the content-query filters. One string for the page and its facet counts
     * (SH1024), so the two cannot come to answer about different rows.
     */
    String CONSOLE_ROWS = "from ShPostUnified p where p.shFolder.id in :folderIds "
            + "and (p.state = com.viglet.shio.persistence.model.post.unified.ShPostState.DRAFT "
            + "  or not exists (select 1 from ShPostUnified d where d.id = p.id "
            + "    and d.state = com.viglet.shio.persistence.model.post.unified.ShPostState.DRAFT)) "
            + "and (:like is null or " + FULLTEXT_PREDICATE + ")" + QUERY_FILTERS
            // `exists` rather than `p.state = :state`, because this listing answers one row per
            // logical id and that row is the draft. "Published" here is a question about the
            // post — is it live — and asking it of the row in hand would empty the listing.
            + " and (:state is null or exists (select 1 from ShPostUnified s "
            + "   where s.id = p.id and s.state = :state))";

    /**
     * Turns a user-typed term into the LIKE pattern {@link #FULLTEXT_PREDICATE} needs:
     * trimmed, lower-cased and wrapped in {@code %}.
     *
     * <p>A blank term becomes {@code %}, which matches every row with a non-null
     * column — the same thing "search for nothing" means everywhere else. Callers that
     * want "no filter" should not call a search method at all.</p>
     */
    static String fullTextPattern(String term) {
        String normalized = term == null ? "" : term.trim().toLowerCase(java.util.Locale.ROOT);
        return "%" + normalized + "%";
    }

    /**
     * SH1057 — the delivery read's locale, as a predicate every search appends.
     *
     * <p>Null means "any locale", which is what every search asked before this existed, so the
     * entry points below that take no locale pass null and answer exactly as they did.</p>
     */
    String LOCALE_FILTER = " and (:locale is null or p.locale = :locale)";

    @Query("select p from ShPostUnified p where p.state = :state and " + FULLTEXT_PREDICATE + LOCALE_FILTER)
    Page<ShPostUnified> searchByStateLike(@Param("state") ShPostState state, @Param("like") String like,
                                          @Param("locale") String locale, Pageable pageable);

    @Query("select p from ShPostUnified p where p.state in :states and " + FULLTEXT_PREDICATE + LOCALE_FILTER)
    List<ShPostUnified> searchByStateInLike(@Param("states") Collection<ShPostState> states,
                                            @Param("like") String like, @Param("locale") String locale);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state = :state and "
            + FULLTEXT_PREDICATE + LOCALE_FILTER)
    Page<ShPostUnified> searchBySitesAndStateLike(@Param("siteIds") Collection<String> siteIds,
                                                  @Param("state") ShPostState state, @Param("like") String like,
                                                  @Param("locale") String locale, Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state in :states and "
            + FULLTEXT_PREDICATE + LOCALE_FILTER)
    List<ShPostUnified> searchBySitesAndStateInLike(@Param("siteIds") Collection<String> siteIds,
                                                    @Param("states") Collection<ShPostState> states,
                                                    @Param("like") String like, @Param("locale") String locale);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.id in :postTypeIds "
            + "and p.state = :state and " + FULLTEXT_PREDICATE + LOCALE_FILTER)
    Page<ShPostUnified> searchBySiteAndTypesAndStateLike(@Param("siteId") String siteId,
                                                         @Param("postTypeIds") Collection<String> postTypeIds,
                                                         @Param("state") ShPostState state,
                                                         @Param("like") String like,
                                                         @Param("locale") String locale, Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.id in :postTypeIds "
            + "and p.state in :states and " + FULLTEXT_PREDICATE + LOCALE_FILTER)
    List<ShPostUnified> searchBySiteAndTypesAndStateInLike(@Param("siteId") String siteId,
                                                           @Param("postTypeIds") Collection<String> postTypeIds,
                                                           @Param("states") Collection<ShPostState> states,
                                                           @Param("like") String like,
                                                           @Param("locale") String locale);

    // --- The search entry points. Take the term as the user typed it, and a locale or null. ---

    /** Full-text search in one state, across the whole instance. */
    default Page<ShPostUnified> searchByState(ShPostState state, String term, Pageable pageable) {
        return searchByState(state, term, null, pageable);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default Page<ShPostUnified> searchByState(ShPostState state, String term, String locale,
                                              Pageable pageable) {
        return searchByStateLike(state, fullTextPattern(term), locale, pageable);
    }

    /** Full-text search across a state set, across the whole instance. */
    default List<ShPostUnified> searchByStateIn(Collection<ShPostState> states, String term) {
        return searchByStateIn(states, term, null);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default List<ShPostUnified> searchByStateIn(Collection<ShPostState> states, String term, String locale) {
        return searchByStateInLike(states, fullTextPattern(term), locale);
    }

    /** Full-text search in one state, scoped to a set of sites. */
    default Page<ShPostUnified> searchBySitesAndState(Collection<String> siteIds, ShPostState state,
                                                      String term, Pageable pageable) {
        return searchBySitesAndState(siteIds, state, term, null, pageable);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default Page<ShPostUnified> searchBySitesAndState(Collection<String> siteIds, ShPostState state,
                                                      String term, String locale, Pageable pageable) {
        return searchBySitesAndStateLike(siteIds, state, fullTextPattern(term), locale, pageable);
    }

    /** Full-text search across a state set, scoped to a set of sites. */
    default List<ShPostUnified> searchBySitesAndStateIn(Collection<String> siteIds,
                                                        Collection<ShPostState> states, String term) {
        return searchBySitesAndStateIn(siteIds, states, term, null);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default List<ShPostUnified> searchBySitesAndStateIn(Collection<String> siteIds,
                                                        Collection<ShPostState> states, String term,
                                                        String locale) {
        return searchBySitesAndStateInLike(siteIds, states, fullTextPattern(term), locale);
    }

    /** Full-text search in one state, scoped to a site and a set of post-types. */
    default Page<ShPostUnified> searchBySiteAndTypesAndState(String siteId,
                                                             Collection<String> postTypeIds,
                                                             ShPostState state, String term,
                                                             Pageable pageable) {
        return searchBySiteAndTypesAndState(siteId, postTypeIds, state, term, null, pageable);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default Page<ShPostUnified> searchBySiteAndTypesAndState(String siteId,
                                                             Collection<String> postTypeIds,
                                                             ShPostState state, String term,
                                                             String locale, Pageable pageable) {
        return searchBySiteAndTypesAndStateLike(siteId, postTypeIds, state, fullTextPattern(term),
                locale, pageable);
    }

    /** Full-text search across a state set, scoped to a site and a set of post-types. */
    default List<ShPostUnified> searchBySiteAndTypesAndStateIn(String siteId,
                                                               Collection<String> postTypeIds,
                                                               Collection<ShPostState> states,
                                                               String term) {
        return searchBySiteAndTypesAndStateIn(siteId, postTypeIds, states, term, null);
    }

    /** As above, in one locale (SH1057); a null locale is every locale. */
    default List<ShPostUnified> searchBySiteAndTypesAndStateIn(String siteId,
                                                               Collection<String> postTypeIds,
                                                               Collection<ShPostState> states,
                                                               String term, String locale) {
        return searchBySiteAndTypesAndStateInLike(siteId, postTypeIds, states, fullTextPattern(term),
                locale);
    }

    // --- Aggregate counts for the agent context pack (SH77). Grouped so the whole
    //     instance costs one query instead of one per post-type / per site, and
    //     counted on `distinct p.id` because the unified model stores up to two rows
    //     per post (DRAFT + PUBLISHED) — counting rows would double most content.
    //
    //     Both group by the association's **id** and never by one of its other
    //     fields. Reaching a further field (`p.shPostType.name`) forces a join to
    //     ShPostType, which is an ShObject subclass in the JOINED hierarchy that
    //     carries SH22's `@TenantId`; Hibernate then emits the tenant predicate
    //     against the *subclass* table alias, which has no `tenant_id` column, and
    //     the query dies at prepare time with `Column "spt1_1.tenant_id" not found`.
    //     Selecting only the id uses the FK column already on sh_post_unified — no
    //     join, no tenant predicate, and cheaper SQL. Callers resolve id → name from
    //     the post-type list they already hold. ---

    /**
     * SH277 — the ids of posts in one site, of one post-type, holding exactly this title.
     *
     * <p>Ids and not rows, because the caller only needs to know whether anybody <em>else</em>
     * holds the title: returning entities would load the fields of a post nobody is going to read.
     * Keyed on {@code p.shPostType.id} rather than the type's name for the SH22 reason spelled out
     * above — reaching {@code p.shPostType.name} joins an {@code ShObject} subclass and the tenant
     * predicate lands on an alias with no {@code tenant_id}.</p>
     *
     * <p><b>Exact match, deliberately.</b> {@code sh-region="NAV"} resolves by exact title, so
     * {@code nav} and {@code NAV} are two different templates — which is exactly what SH193's
     * {@code unused-region} fix is about, and a case-insensitive collision check here would refuse
     * a write that is not a collision. {@code distinct} because a template has a DRAFT row and a
     * PUBLISHED row that share both id and title.</p>
     */
    @Query("select distinct p.id from ShPostUnified p where p.shSite.id = :siteId "
            + "and p.shPostType.id = :postTypeId and p.title = :title")
    List<String> findIdsBySiteAndPostTypeAndTitle(@Param("siteId") String siteId,
                                                  @Param("postTypeId") String postTypeId,
                                                  @Param("title") String title);

    /** @return rows of {@code [postTypeId (String), posts (Long)]}. */
    @Query("select p.shPostType.id, count(distinct p.id) from ShPostUnified p "
            + "where p.shPostType is not null group by p.shPostType.id")
    List<Object[]> countDistinctByPostTypeId();

    /** @return rows of {@code [siteId (String), posts (Long)]}. */
    @Query("select p.shSite.id, count(distinct p.id) from ShPostUnified p "
            + "where p.shSite is not null group by p.shSite.id")
    List<Object[]> countDistinctBySiteId();

    /**
     * SH1063 — the same count in one state, for the routing table's "what a broken route costs".
     *
     * @return rows of {@code [siteId (String), posts (Long)]}
     */
    @Query("select p.shSite.id, count(distinct p.id) from ShPostUnified p "
            + "where p.shSite is not null and p.state = :state group by p.shSite.id")
    List<Object[]> countDistinctBySiteIdAndState(@Param("state") ShPostState state);

    /**
     * SH143 — the URL index a <em>scoped</em> verify run still needs whole.
     *
     * <p>Two lint rules are cross-cutting by nature: "is any other published post claiming
     * this URL" and "does this link resolve anywhere in the site". Neither can be answered
     * from the scoped subject set, and neither needs a post's data, its post-type or its
     * attribute tree — which is the part a full read pass is expensive for. So the index
     * is a four-column projection: one query per site, no entities, no lazy loading.</p>
     *
     * <p>The fourth column is the {@code locale} (SH323), and it is here rather than read off the
     * subject rows for the same reason the index exists at all: a scoped run holds one of two
     * posts sharing a URL, so a message that named the <em>other</em> one's locale from the loaded
     * rows would be guessing. A claim about content outside the scope has to come from the
     * cross-cutting index or not be made.</p>
     *
     * @return rows of {@code [id (String), furl (String), state (ShPostState), locale (String)]}
     */
    @Query("select p.id, p.furl, p.state, p.locale from ShPostUnified p "
            + "where p.shSite.id = :siteId and p.state in :states")
    List<Object[]> findUrlIndexBySiteId(@Param("siteId") String siteId,
                                       @Param("states") Collection<ShPostState> states);

    /**
     * SH143 — the folder ids that hold at least one post.
     *
     * <p>Half of the {@code empty-folder} rule, and the half a scoped run cannot compute
     * from its own subjects: a folder outside the scope may still be the reason a folder
     * inside it is not empty. Ids only, for the reason on {@link #countDistinctByPostTypeId}.</p>
     */
    @Query("select distinct p.shFolder.id from ShPostUnified p where p.shFolder is not null")
    Set<String> findFolderIdsWithPosts();

    /**
     * SH1050 — how many publishes and unpublishes fall due in a window.
     *
     * <p>A count rather than the rows, which is the whole of SH1050: the console's home wants the
     * number, and reading every scheduled post to measure the list is the shape that made home
     * fetch two whole collections to draw two figures.</p>
     *
     * <p>A post scheduled to go up <em>and</em> come down inside the window counts once. That is
     * the honest answer to "how many things happen this week" for a dashboard tile — the agenda
     * page splits them into two moments, and this is not the agenda.</p>
     *
     * <p>Scalar columns only, deliberately: navigating past the id of an association whose target
     * is an {@code ShObject} subclass emits a tenant predicate against an alias that has no such
     * column, and the failure arrives at query-prepare time rather than here.</p>
     */
    @Query("select count(p) from ShPostUnified p where p.deletedDate is null and ("
            + "(p.publishScheduledDate is not null "
            + "and p.publishScheduledDate >= :from and p.publishScheduledDate < :until) "
            + "or (p.unpublishScheduledDate is not null "
            + "and p.unpublishScheduledDate >= :from and p.unpublishScheduledDate < :until))")
    long countScheduledBetween(@Param("from") Instant from, @Param("until") Instant until);
}
