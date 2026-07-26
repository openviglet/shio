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
}
