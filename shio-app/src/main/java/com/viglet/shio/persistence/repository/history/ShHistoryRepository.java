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
	 * <p>The ceiling is the lag rule — an entry younger than it is held back so a
	 * transaction that is still committing cannot land <em>behind</em> a cursor that has
	 * already moved past its timestamp. See {@code ShAgentChangesService}.</p>
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

	@Query("select h from ShHistory h where h.date <= :ceiling order by h.date desc, h.id desc")
	List<ShHistory> findWindow(Instant ceiling, Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds "
			+ "order by h.date desc, h.id desc")
	List<ShHistory> findWindowInSites(Instant ceiling, Collection<String> siteIds, Pageable pageable);

	/**
	 * Everything strictly after the {@code (at, id)} position, oldest first (SH88).
	 *
	 * <p>The keyset predicate is what makes the feed resumable without dropping ties: an
	 * entry qualifies when its timestamp is later, <em>or</em> when the timestamp is equal
	 * and the id sorts after. A plain {@code date >} would skip every entry sharing the
	 * cursor's second, which on a batch write is most of them.</p>
	 */
	@Query("select h from ShHistory h where h.date <= :ceiling "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) "
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSince(Instant ceiling, Instant at, String id, Pageable pageable);

	@Query("select h from ShHistory h where h.date <= :ceiling and h.shSite in :siteIds "
			+ "and (h.date > :at or (h.date = :at and h.id > :id)) "
			+ "order by h.date asc, h.id asc")
	List<ShHistory> findSinceInSites(Instant ceiling, Collection<String> siteIds, Instant at,
			String id, Pageable pageable);
}
