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
package com.viglet.shio.persistence.repository.site;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.persistence.model.site.ShSite;

/**
 * Sites, and <b>only the live ones</b> unless the method says otherwise (SH648).
 *
 * <p>A trashed site is a row with a {@code deleted_date}, and every ordinary read — the console
 * listing, the CDA, the renderer's site resolution, the address resolver — must not see it, or a
 * deleted site keeps answering at its URL. The exclusion is spelled out here rather than as a
 * Hibernate {@code @SQLRestriction} on {@link ShSite} for the reason {@code ShFolderRepository}
 * records one level down: a restriction fires when a {@code @ManyToOne} to the entity is
 * initialized too, and a trashed site's root folder reads {@code getShSite()} on exactly the path
 * a restore walks. Filtering here keeps the association honest and costs one predicate per query.</p>
 *
 * <p>The trash's own reads are the {@code findAny…} and {@code findTrashedSites} methods, which are
 * the only ones that may see a trashed row.</p>
 *
 * @author Alexandre Oliveira
 */
@Repository
public interface ShSiteRepository extends JpaRepository<ShSite, String> {

	@Query("select s from ShSite s where s.deletedDate is null")
	List<ShSite> findAll();

	/**
	 * SH1217 — {@link #findAll()} for the sites listing, in one statement.
	 *
	 * <p>{@code ShObject.shWorkflowTasks} is {@code @Fetch(JOIN)}, which makes it eager, and a
	 * query cannot join it implicitly — so {@code findAll} pays one select per site for it, which
	 * {@code ShSiteListingCostTest} measured at 403 statements for 400 sites. Fetched here instead.</p>
	 */
	@Query("select distinct s from ShSite s left join fetch s.shWorkflowTasks where s.deletedDate is null")
	List<ShSite> findAllForListing();

	@Query("select s from ShSite s where s.id = ?1 and s.deletedDate is null")
	Optional<ShSite> findById(String id);

	@Query("select s from ShSite s where s.id in ?1 and s.deletedDate is null")
	List<ShSite> findByIdIn(Collection<String> ids);

	@Query("select s from ShSite s where (s.owner = ?1 or s.owner is null) and s.deletedDate is null")
	List<ShSite> findByOwnerOrOwnerIsNull(String owner);

	@Query("select s from ShSite s where s.name = ?1 and s.deletedDate is null")
	ShSite findByName(String name);

	@Query("select s from ShSite s where s.furl = ?1 and s.deletedDate is null")
	ShSite findByFurl(String furl);

	/**
	 * The site whether or not it is trashed — what a restore, a purge and the SH88 change feed
	 * need, since each of them has to address the site it just reported deleted (SH648).
	 */
	@Query("select s from ShSite s where s.id = ?1")
	Optional<ShSite> findAnyById(String id);

	/**
	 * The site holding that name, trashed or not (SH648).
	 *
	 * <p>{@code ShSite.name} is {@code unique}, so a trashed site still owns its name and a
	 * re-create collides in the database rather than in the service. This is the read that turns
	 * that collision into {@code ShSiteNameHeldByTrashException} instead of a 500.</p>
	 */
	@Query("select s from ShSite s where s.name = ?1")
	ShSite findAnyByName(String name);

	/** The site holding that friendly URL, trashed or not — the furl half of {@link #findAnyByName}. */
	@Query("select s from ShSite s where s.furl = ?1")
	ShSite findAnyByFurl(String furl);

	/** SH648 — what is in the trash, newest deletion first, which is the order a curator scans. */
	@Query("select s from ShSite s where s.deletedDate is not null order by s.deletedDate desc")
	List<ShSite> findTrashedSites();

	@SuppressWarnings("unchecked")
	ShSite save(ShSite shSite);

	@Modifying
	@Query("delete from ShSite p where p.id = ?1")
	void delete(String shSiteId);
}
