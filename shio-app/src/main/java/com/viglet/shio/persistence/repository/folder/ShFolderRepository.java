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
package com.viglet.shio.persistence.repository.folder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.viglet.shio.bean.ShFolderTinyBean;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.site.ShSite;

/**
 * Folder reads and writes.
 *
 * <h2>Every read here hides a trashed folder (SH200)</h2>
 *
 * <p>A folder delete is a soft-delete: it stamps {@code deleted_date} down the subtree and
 * removes nothing, so the posts beneath it can sit in SH55's trash and still have a folder
 * to be restored to. That only works if a trashed folder disappears from navigation, the
 * CDA, the sitemap, the content lint and the exchange export — every one of which reaches a
 * folder through this interface.</p>
 *
 * <p>So the derived finders below are spelled out as JPQL with {@code deletedDate is null}
 * rather than left to method-name derivation. The alternative was a Hibernate
 * {@code @SQLRestriction} on {@link ShFolder} — rejected because it would also apply when a
 * {@code @ManyToOne} to a folder is initialized, and a trashed post's {@code getShFolder()}
 * is precisely what the trash listing reads. Filtering here keeps the association honest
 * and costs one predicate per query; the trash's own reads are the {@code …Trashed…}
 * methods, which are the only ones that may see them.</p>
 *
 * <p>Naming note: the filtered methods keep their original names on purpose. Renaming them
 * to {@code …AndDeletedDateIsNull} would have touched sixty call sites and left the plain
 * name available for the next reader to use by accident — the failure mode being a trashed
 * section quietly reappearing in one listing.</p>
 *
 * @author Alexandre Oliveira
 */
@Repository
public interface ShFolderRepository extends JpaRepository<ShFolder, String> {

	@Query("select f from ShFolder f where f.deletedDate is null")
	List<ShFolder> findAll();

	@Query("select new com.viglet.shio.bean.ShFolderTinyBean(f.id, f.name, f.position, f.date) from ShFolder f where f.shSite = ?1 and f.rootFolder = ?2 and f.deletedDate is null")
	Set<ShFolderTinyBean> findByShSiteAndRootFolderTiny(ShSite shSite, byte rootFolder);


	@Query("select f from ShFolder f where f.shSite = ?1 and f.rootFolder = ?2 and f.deletedDate is null")
	Set<ShFolder> findByShSiteAndRootFolder(ShSite shSite, byte rootFolder);

	/**
	 * SH279 — a site's root folder, resolved the same way by every caller.
	 *
	 * <h2>Why this exists rather than eight {@code findFirst()}s</h2>
	 *
	 * <p>{@link #findByShSiteAndRootFolder} returns a {@code Set}, and eight call sites took the
	 * first element of it — the address resolver, the CDA, the console's navigation and write
	 * services, the exchange export twice, the post writer and the agent's site access. A
	 * {@code Set} has no order, so "the first" was the database's choice, and SH278's lesson is
	 * that such a choice is invisible on H2 and surfaces on somebody else's engine.</p>
	 *
	 * <p><b>A site has one root by construction</b> — {@code ShSiteWriteService} creates exactly one
	 * and {@code ShFolderWriteService} nulls {@code shSite} on every child (SH128). That premise is
	 * the reason this was harmless in practice, and SH263 is the reason it is not a defence: a title
	 * was also unique in practice until it wasn't. So the order is stated rather than assumed, and
	 * it costs one comparator.</p>
	 *
	 * <p>The bar SH279 sets is two questions — <em>does anything guarantee uniqueness, and is that
	 * guarantee enforced?</em> Here the answer is "yes, by construction, and nothing checks it", so
	 * the finder is ordered <em>and</em> {@code ShRootFolderReaderLintTest} keeps the raw
	 * {@code Set} finder from being read directly again.</p>
	 */
	default java.util.Optional<ShFolder> findRootFolder(ShSite shSite) {
		return findByShSiteAndRootFolder(shSite, (byte) 1).stream()
				.min(java.util.Comparator.comparing(ShFolder::getId));
	}

	@Query("select f from ShFolder f where f.shSite = ?1 and f.name = ?2 and f.deletedDate is null")
	ShFolder findByShSiteAndName(ShSite shSite, String name);

	@Query("select f from ShFolder f where f.shSite = ?1 and f.furl = ?2 and f.deletedDate is null")
	ShFolder findByShSiteAndFurl(ShSite shSite, String furl);

	@Query("select f from ShFolder f where f.parentFolder = ?1 and f.name = ?2 and f.deletedDate is null")
	ShFolder findByParentFolderAndName(ShFolder parentFolder, String name);

	@Query("select f from ShFolder f where f.parentFolder = ?1 and f.furl = ?2 and f.deletedDate is null")
	ShFolder findByParentFolderAndFurl(ShFolder parentFolder, String furl);

	@Query("select new com.viglet.shio.bean.ShFolderTinyBean(f.id, f.name, f.position, f.date) from ShFolder f where f.parentFolder = ?1 and f.deletedDate is null")
	Set<ShFolderTinyBean> findByParentFolderTiny(ShFolder parentFolder);

	@Query("select f from ShFolder f where f.parentFolder = ?1 and f.deletedDate is null")
	Set<ShFolder> findByParentFolder(ShFolder parentFolder);

	@Query("select f from ShFolder f where f.parentFolder = ?1 and f.deletedDate is null order by f.position asc")
	List<ShFolder> findByParentFolderOrderByPositionAsc(ShFolder parentFolder);

	@Query("select count(f) from ShFolder f where f.shSite = ?1 and f.deletedDate is null")
	int countByShSite(ShSite shSite);

	@Query("select f from ShFolder f where f.id = ?1 and f.deletedDate is null")
	Optional<ShFolder> findById(String id);

	// --- the trash (SH200): the only reads allowed to see a trashed folder ---

	/**
	 * A folder by id whether or not it is trashed — restore, purge, and the SH88 change
	 * feed, which has to be able to address the folder it just reported deleted.
	 */
	@Query("select f from ShFolder f where f.id = ?1")
	Optional<ShFolder> findAnyById(String id);

	/** Children whether or not they are trashed — the subtree walk restore and purge need. */
	@Query("select f from ShFolder f where f.parentFolder = ?1")
	List<ShFolder> findByParentFolderIncludingTrashed(ShFolder parentFolder);

	/**
	 * Every trashed folder, newest first.
	 *
	 * <p>Flat on purpose: the trash listing wants one row per deleted <em>section</em>, not
	 * one per folder in it, and "is this folder's parent also trashed" is answered in the
	 * service off the association rather than in JPQL. Reaching through
	 * {@code f.parentFolder.deletedDate} here would be the SH22 join hazard — an implicit
	 * join onto the {@code ShObject} subclass table, where the {@code @TenantId} predicate
	 * has no column to land on.</p>
	 */
	@Query("select f from ShFolder f where f.deletedDate is not null order by f.deletedDate desc")
	List<ShFolder> findTrashedFolders();

	/**
	 * SH143 — every folder id that is some folder's parent.
	 *
	 * <p>The other half of the {@code empty-folder} rule: a folder with a subfolder is not
	 * empty even when the subfolder is outside a scoped verify run. {@code parentFolder.id}
	 * uses the FK column already on ShFolder, so this forces no join onto the JOINED
	 * ShObject root and no SH22 tenant predicate against a subclass alias.</p>
	 */
	@Query("select distinct f.parentFolder.id from ShFolder f where f.parentFolder is not null")
	Set<String> findParentFolderIds();

	@SuppressWarnings("unchecked")
	ShFolder save(ShFolder shFolder);

	@Modifying
	@Query("delete from ShFolder p where p.id = ?1")
	void delete(String shFolderId);
}
