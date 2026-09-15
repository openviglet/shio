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
	 * run, {@code kind} to {@code agent} or {@code human}, and both {@code null} is every row —
	 * which is what every caller from SH88 to SH998 passes, so the unfiltered feed is the same
	 * result set it always was.</p>
	 *
	 * <p>A <b>console</b> session is a person (SH996), so it belongs on the human side of this
	 * predicate. That is the reason the test asserts the split rather than the column: a filter
	 * that read "has a session id" would file a curator's own publish under the machines.</p>
	 */
	String WROTE_IT = "and (:session is null or h.agentSessionId = :session) "
			+ "and (:kind is null "
			+ "  or (:kind = 'agent' and h.agentSessionId is not null "
			+ "      and h.agentSessionId not like 'console:%') "
			+ "  or (:kind = 'human' and (h.agentSessionId is null "
			+ "      or h.agentSessionId like 'console:%'))) ";

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
	 * Most recent audit entries first (SH83's {@code changes}).
	 *
	 * <p>{@code shSite} is a plain id column here, not an association, so filtering on
	 * it cannot trip the SH22 {@code @TenantId} JOINED-join hazard — and the row's own
	 * {@code tenantId} discriminator already scopes the result. Paged rather than
	 * sorted in memory because an audit table is the one table that only grows.</p>
	 */
	@Query("select h from ShHistory h order by h.date desc, h.id desc")
	List<ShHistory> findRecent(Pageable pageable);

	@Query("select h from ShHistory h where h.shSite = :siteId order by h.date desc, h.id desc")
	List<ShHistory> findRecentBySite(String siteId, Pageable pageable);

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
	 */
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

	@Query("select h from ShHistory h where h.date <= :ceiling " + WROTE_IT
			+ "order by h.date desc, h.id desc")
	List<ShHistory> findWindow(Instant ceiling, String session, String kind, Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds " + WROTE_IT
			+ "order by h.date desc, h.id desc")
	List<ShHistory> findWindowInSites(Instant ceiling, Collection<String> siteIds, String session,
			String kind, Pageable pageable);

	/**
	 * Everything strictly after the {@code (at, id)} position, oldest first (SH88).
	 *
	 * <p>The keyset predicate is what makes the feed resumable without dropping ties: an
	 * entry qualifies when its timestamp is later, <em>or</em> when the timestamp is equal
	 * and the id sorts after. A plain {@code date >} would skip every entry sharing the
	 * cursor's second, which on a batch write is most of them.</p>
	 */
	@Query("select h from ShHistory h where h.date <= :ceiling "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) " + WROTE_IT
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSince(Instant ceiling, Instant at, String id, String session, String kind,
			Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) " + WROTE_IT
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSinceInSites(Instant ceiling, Collection<String> siteIds, Instant at,
			String id, String session, String kind, Pageable pageable);
}
