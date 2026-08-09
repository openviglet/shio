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
    @Query("select p from ShPostUnified p where p.shFolder = :folder "
            + "and (p.state = com.viglet.shio.persistence.model.post.unified.ShPostState.DRAFT "
            + "  or not exists (select 1 from ShPostUnified d where d.id = p.id "
            + "    and d.state = com.viglet.shio.persistence.model.post.unified.ShPostState.DRAFT)) "
            + "and (:like is null or " + FULLTEXT_PREDICATE + ")")
    Page<ShPostUnified> pageConsoleRowsLike(@Param("folder") ShFolder folder,
                                            @Param("like") String like, Pageable pageable);

    /**
     * {@link #pageConsoleRowsLike} with the title filter normalized: {@code null}/blank means
     * no filter, anything else is lower-cased and {@code %}-wrapped. Call this one — the raw
     * query is suffixed {@code Like} because normalizing twice searches for {@code %%term%%}
     * and matches nothing (SH131).
     */
    default Page<ShPostUnified> pageConsoleRows(ShFolder folder, String query, Pageable pageable) {
        return pageConsoleRowsLike(folder,
                query == null || query.isBlank() ? null : fullTextPattern(query), pageable);
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

    // --- Content i18n / locales (SH53) ---

    /** All sibling translation rows (across states) sharing a translation group. */
    List<ShPostUnified> findByTranslationGroupId(String translationGroupId);

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

    @Query("select p from ShPostUnified p where p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchByStateLike(@Param("state") ShPostState state, @Param("like") String like,
                                          Pageable pageable);

    @Query("select p from ShPostUnified p where p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchByStateInLike(@Param("states") Collection<ShPostState> states,
                                            @Param("like") String like);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchBySitesAndStateLike(@Param("siteIds") Collection<String> siteIds,
                                                  @Param("state") ShPostState state, @Param("like") String like,
                                                  Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id in :siteIds and p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchBySitesAndStateInLike(@Param("siteIds") Collection<String> siteIds,
                                                    @Param("states") Collection<ShPostState> states,
                                                    @Param("like") String like);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.id in :postTypeIds "
            + "and p.state = :state and " + FULLTEXT_PREDICATE)
    Page<ShPostUnified> searchBySiteAndTypesAndStateLike(@Param("siteId") String siteId,
                                                         @Param("postTypeIds") Collection<String> postTypeIds,
                                                         @Param("state") ShPostState state,
                                                         @Param("like") String like, Pageable pageable);

    @Query("select p from ShPostUnified p where p.shSite.id = :siteId and p.shPostType.id in :postTypeIds "
            + "and p.state in :states and " + FULLTEXT_PREDICATE)
    List<ShPostUnified> searchBySiteAndTypesAndStateInLike(@Param("siteId") String siteId,
                                                           @Param("postTypeIds") Collection<String> postTypeIds,
                                                           @Param("states") Collection<ShPostState> states,
                                                           @Param("like") String like);

    // --- The search entry points. Take the term as the user typed it. ---

    /** Full-text search in one state, across the whole instance. */
    default Page<ShPostUnified> searchByState(ShPostState state, String term, Pageable pageable) {
        return searchByStateLike(state, fullTextPattern(term), pageable);
    }

    /** Full-text search across a state set, across the whole instance. */
    default List<ShPostUnified> searchByStateIn(Collection<ShPostState> states, String term) {
        return searchByStateInLike(states, fullTextPattern(term));
    }

    /** Full-text search in one state, scoped to a set of sites. */
    default Page<ShPostUnified> searchBySitesAndState(Collection<String> siteIds, ShPostState state,
                                                      String term, Pageable pageable) {
        return searchBySitesAndStateLike(siteIds, state, fullTextPattern(term), pageable);
    }

    /** Full-text search across a state set, scoped to a set of sites. */
    default List<ShPostUnified> searchBySitesAndStateIn(Collection<String> siteIds,
                                                        Collection<ShPostState> states, String term) {
        return searchBySitesAndStateInLike(siteIds, states, fullTextPattern(term));
    }

    /** Full-text search in one state, scoped to a site and a set of post-types. */
    default Page<ShPostUnified> searchBySiteAndTypesAndState(String siteId,
                                                             Collection<String> postTypeIds,
                                                             ShPostState state, String term,
                                                             Pageable pageable) {
        return searchBySiteAndTypesAndStateLike(siteId, postTypeIds, state, fullTextPattern(term), pageable);
    }

    /** Full-text search across a state set, scoped to a site and a set of post-types. */
    default List<ShPostUnified> searchBySiteAndTypesAndStateIn(String siteId,
                                                               Collection<String> postTypeIds,
                                                               Collection<ShPostState> states,
                                                               String term) {
        return searchBySiteAndTypesAndStateInLike(siteId, postTypeIds, states, fullTextPattern(term));
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
}
