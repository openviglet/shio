/*
 * Copyright (C) 2016-2020 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.shio.persistence.repository.history;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.history.ShHistory;

/**
 * @author Alexandre Oliveira
 */
@Repository
public interface ShHistoryRepository extends JpaRepository<ShHistory, String> {

	/**
	 * The feed's SH999 attribution filter, as one clause the four feed queries share.
	 *
	 * <p>Two nullable parameters rather than four query variants: {@code session} narrows to one
	 * run, {@code kind} to {@code agent} or {@code curator}, and both {@code null} is every row —
	 * which is what every caller from SH88 to SH998 passes, so the unfiltered feed is the same
	 * result set it always was.</p>
	 *
	 * <p>{@code curator} rather than the species word, which is SH705's rule and not a nicety: the
	 * counterpart of a run is the person who answers for the site, and this product already calls
	 * them the curator everywhere else.</p>
	 *
	 * <p>A <b>console</b> session is one of theirs (SH996), so it belongs on that side of the
	 * predicate. That is the reason the test asserts the split rather than the column: a filter that
	 * read "has a session id" would file a curator's own publish under the machines.</p>
	 */
	/**
	 * SH1002 — narrowed to the changes a curator decided one way.
	 *
	 * <p>A subquery rather than a filter applied after the page, for the reason the attribution
	 * filter states one clause up: this feed is resumable, so a post-filter would hand back a short
	 * page while the matching rows sat behind the cursor. A decision is keyed by the same two
	 * columns a change row already carries, which is what makes the predicate cheap.</p>
	 */
	String DECIDED = "and (:decision is null or exists (select 1 from ShReviewDecision d "
			+ "  where d.session = h.agentSessionId and d.postId = h.shObject "
			+ "  and d.decision = :decision)) ";

	String WROTE_IT = "and (:session is null or h.agentSessionId = :session) "
			+ "and (:kind is null "
			+ "  or (:kind = 'agent' and h.agentSessionId is not null "
			+ "      and h.agentSessionId not like 'console:%') "
			+ "  or (:kind = 'curator' and (h.agentSessionId is null "
			+ "      or h.agentSessionId like 'console:%'))) ";

	/**
	 * SH1005 — the three narrowings the console asked for and one reader now answers.
	 *
	 * <p>Here rather than in a second query set, and applied before the page rather than after it,
	 * for the reason {@link com.viglet.shio.api.history.ShHistoryFilter} states: this trail is
	 * resumable from both ends now, so a filter applied to what came back would hand a caller a
	 * short page while the rows it asked for sat behind the cursor.</p>
	 *
	 * <p>{@code subjectKind} is coalesced rather than compared, because a row written before SH196
	 * added that column carries {@code null} and <em>is</em> a post — asking for posts and not
	 * getting the oldest ones would make the filter quietly mean "posts since SH196".</p>
	 */
	String MATCHING = "and (:action is null or h.action = :action) "
			+ "and (:actor is null or h.owner = :actor) "
			+ "and (:subject is null or coalesce(h.subjectKind, 'POST') = :subject) "
			+ "and (:object is null or h.shObject = :object) ";

	List<ShHistory> findAll();

	Optional<ShHistory> findById(String id);

	@SuppressWarnings("unchecked")
	ShHistory save(ShHistory shHistory);

	int countByShObject(String shObject);

	int countByShSite(String shSite);

	@Modifying
	@Query("delete from ShHistory h where h.id = ?1")
	void delete(String id);

	/**
	 * One object's whole trail, newest first.
	 *
	 * <p>The last derived finder over this table, and it is here because a <b>read-back</b> is not
	 * a surface: a test asserting what a write recorded wants the rows and none of the scope,
	 * ceiling and cursor the two readers exist to apply. SH1005 deleted the other four — the
	 * console's whole query set, which duplicated the feed's more weakly — and merged
	 * {@code ShHistoryPageableRepository} into this one, because two repositories over one table
	 * is how a second reader gets written without anybody deciding to write one.</p>
	 *
	 * <p>{@code shObject} is a plain id column here, not an association, so filtering on it cannot
	 * trip the SH22 {@code @TenantId} JOINED-join hazard — and the row's own {@code tenantId}
	 * discriminator already scopes the result.</p>
	 */
	List<ShHistory> findByShObjectOrderByDateDesc(String shObject, Pageable pageable);

	/**
	 * The agent-made entries, newest first (SH109). {@code agent_session_id} is exactly the
	 * column SH106 added for this: before it, "which changes did that run make" was a
	 * {@code LIKE} over an actor string.
	 */
	@Query("select h from ShHistory h where h.agentSessionId is not null "
			+ "order by h.date desc, h.id desc")
	List<ShHistory> findAgentEntries(Pageable pageable);

	/** One run's entries, oldest first — the order it made them, which is the order to review. */
	@Query("select h from ShHistory h where h.agentSessionId = :session "
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findByAgentSession(String session);

	/**
	 * The change feed's window (SH88): the newest entries up to {@code ceiling}.
	 *
	 * <p>The ceiling is the SH147 watermark — the oldest write that has stamped and not
	 * finished, so an entry cannot be reported while a transaction that stamped before it is
	 * still committing and could land <em>behind</em> a cursor that has already moved past it.
	 * See {@code ShAuditFeedWatermark}.</p>
	 *
	 * <p>Two shapes of each query rather than one with an {@code or} on a flag: an
	 * unrestricted credential reads the whole trail (including entries whose site could
	 * not be recorded), a site-scoped one reads only the sites it can see, and expressing
	 * that as a parameter would put an access rule inside a query string.</p>
	 *
	 * <p><b>SH1005 — and everything strictly <em>before</em> {@code (at, id)}, when one is
	 * given.</b></p>
	 *
	 * <p>The window and the page behind it are one query because they are one question asked from
	 * one end: "the newest rows in scope, starting here". A null position is the edge of the feed,
	 * which is what every caller before SH1005 asked for, so the unfiltered window is the result
	 * set it always was — and the console's back-paging is the same keyset predicate as SH88's,
	 * reversed, rather than a second reader with its own idea of what a cursor is.</p>
	 */
	@Query("select h from ShHistory h where h.date <= :ceiling "
			+ "and (:at is null or h.date < :at or (h.date = :at and h.id < :id)) "
			+ WROTE_IT + DECIDED + MATCHING + "order by h.date desc, h.id desc")
	List<ShHistory> findWindow(Instant ceiling, Instant at, String id, String session, String kind,
			String decision, String action, String actor, String subject, String object,
			Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds "
			+ "and (:at is null or h.date < :at or (h.date = :at and h.id < :id)) "
			+ WROTE_IT + DECIDED + MATCHING + "order by h.date desc, h.id desc")
	List<ShHistory> findWindowInSites(Instant ceiling, Collection<String> siteIds, Instant at,
			String id, String session, String kind, String decision, String action, String actor,
			String subject, String object, Pageable pageable);

	/**
	 * Everything strictly after the {@code (at, id)} position, oldest first (SH88).
	 *
	 * <p>The keyset predicate is what makes the feed resumable without dropping ties: an
	 * entry qualifies when its timestamp is later, <em>or</em> when the timestamp is equal
	 * and the id sorts after. A plain {@code date >} would skip every entry sharing the
	 * cursor's second, which on a batch write is most of them.</p>
	 */
	@Query("select h from ShHistory h where h.date <= :ceiling "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) " + WROTE_IT + DECIDED + MATCHING
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSince(Instant ceiling, Instant at, String id, String session, String kind,
			String decision, String action, String actor, String subject, String object,
			Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) " + WROTE_IT + DECIDED + MATCHING
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSinceInSites(Instant ceiling, Collection<String> siteIds, Instant at,
			String id, String session, String kind, String decision, String action, String actor,
			String subject, String object, Pageable pageable);
}
