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
package com.viglet.shio.persistence.model.site;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import lombok.Getter;
import org.hibernate.annotations.Fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;

/**
 * The persistent class for the ShSite database table.
 * 
 * @author Alexandre Oliveira
 */
@Getter
@Entity
@NamedQuery(name = "ShSite.findAll", query = "SELECT s FROM ShSite s")
@JsonIgnoreProperties({ "shFolders", "shPosts", "shPostAttrRefs", "shGroups", "shWorkflowTasks" })
@PrimaryKeyJoinColumn(name = "object_id")
public class ShSite extends ShObject {
	@Serial
	private static final long serialVersionUID = 1L;

	@Column(unique=true)
	private String name;

	private String description;

	private String url;

	@Column(name = "post_type_layout", length = 5 * 1024 * 1024) // 5Mb
	private String postTypeLayout;
	
	@Column(name = "searchable_post_types", length = 5 * 1024 * 1024) // 5Mb
	private String searchablePostTypes;

	/**
	 * The site's declared locale policy as JSON, or null while it has none (SH1056).
	 *
	 * <p>Null is not a missing value here, it is an answer: an undeclared site reports the locales
	 * its content is written in, which is what every site did before there was a setting. See
	 * {@code ShSiteLocales} for the document's shape and {@code ShSiteLocaleService} for which of
	 * the two a reader gets.</p>
	 */
	@Column(name = "locales", length = 4096)
	private String locales;

	/**
	 * The post type a new page in this site starts as, or null while the site names none (SH1283).
	 *
	 * <p>A site binds several types to layouts and none of them is "the page" by itself: Quilhara
	 * binds eight, so an empty folder's New button guessed the catalogue's first. Always one of the
	 * types {@code postTypeLayout} binds, which the write side checks.</p>
	 */
	private String pageType;

	@OneToMany(mappedBy = "shSite", cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private Set<ShFolder> shFolders = new HashSet<>();

	private String formSuccess;

	/**
	 * Whether this site was declared disposable by whoever created it (SH591).
	 *
	 * <p>Twelve replication rounds left thirteen sites on one instance, each needing its own
	 * {@code site.delete}, its own 428 and its own confirm token. The distinction the model was
	 * missing is <b>who declared the site disposable, and when</b>: P7 says a human is never
	 * surprised, and a site an agent marked as scratch at the moment it created it — in a document
	 * the human could read — is not a surprise, because the declaration happened before there was
	 * anything to lose.</p>
	 *
	 * <p><b>Set at creation and never afterwards.</b> That is the whole guarantee. A flag anybody
	 * could raise later would be a way to make a real site deletable in bulk, which is precisely
	 * what {@code shio site rm --scratch} must not become.</p>
	 *
	 * <p>{@code Boolean} rather than {@code boolean}: every site created before this column has
	 * {@code null}, and null means <i>kept</i> — the reading that leaves an existing instance
	 * exactly as it was.</p>
	 */
	private Boolean scratch;

	/**
	 * SH1299 — whether an anonymous visitor turned away from a members-only page is sent to the
	 * console login, with the page as its return address, rather than given the missing-page 404.
	 *
	 * <p>{@code Boolean} for {@link #scratch}'s reason: null is every site created before the
	 * column, and null is off, the behaviour SH1285 shipped.</p>
	 */
	private Boolean membersLogin;

	public ShSite() {
		this.setObjectType(ShObjectType.SITE);
	}

    public void setDescription(String description) {
		this.description = description;
	}

    public void setUrl(String url) {
		this.url = url;
	}

    public void setName(String name) {
		this.name = name;
	}

    public void setShFolders(Set<ShFolder> shFolders) {
		this.shFolders.clear();
		if (shFolders != null) {
			this.shFolders.addAll(shFolders);
		}
	}

    public void setPostTypeLayout(String postTypeLayout) {
		this.postTypeLayout = postTypeLayout;
	}
	
	@Override
	public String getObjectType() {
		return ShObjectType.SITE;
	}

	@Override
	public void setObjectType(String objectType) {		
		super.setObjectType(ShObjectType.SITE);
	}

    public void setSearchablePostTypes(String searchablePostTypes) {
		this.searchablePostTypes = searchablePostTypes;
	}

    public void setFormSuccess(String formSuccess) {
		this.formSuccess = formSuccess;
	}

	/** SH1056 — the declared policy, or null to go back to reading it off the content. */
	public void setLocales(String locales) {
		this.locales = locales;
	}

	/** SH1283 — the type a new page starts as; validated against the bindings by the write side. */
	public void setPageType(String pageType) {
		this.pageType = pageType;
	}

	/** SH1299 — the switch; null and false are both off. */
	public void setMembersLogin(Boolean membersLogin) {
		this.membersLogin = membersLogin;
	}

	/** SH1299 — whether the switch is on. */
	public boolean sendsMembersToLogin() {
		return Boolean.TRUE.equals(membersLogin);
	}

	/** SH591 — written by the create that declared it, and by nothing else. */
	public void setScratch(Boolean scratch) {
		this.scratch = scratch;
	}

	/** SH591 — null is a site that predates the column, which is a site nobody called disposable. */
	public boolean isScratch() {
		return Boolean.TRUE.equals(this.scratch);
	}

	/**
	 * When this site was put in the trash (SH648); {@code null} = live.
	 *
	 * <p>The site half of SH55's soft-delete, and the level the trash stopped one short of.
	 * A site delete already went down through the folder writer and the post writer (SH454),
	 * so every page of a deleted site could be restored — and the site itself could not,
	 * because its row was removed. That inversion put the least recoverable step at the top
	 * of the most destructive gesture in the product.</p>
	 *
	 * <p>Deliberately <b>no {@code @SQLRestriction}</b>, the same choice
	 * {@link com.viglet.shio.persistence.model.folder.ShFolder} records: a restriction here
	 * would also fire when a {@code @ManyToOne} to a site is initialized, and a trashed site's
	 * root folder reads {@code getShSite()} on exactly that path — which is what a restore and
	 * the trash listing need. Live-only filtering lives in
	 * {@code ShSiteRepository} instead, where every read already goes.</p>
	 *
	 * <p>{@link #name} is {@code unique}, so a trashed site keeps holding its name: that is a
	 * refusal somebody has to write rather than a constraint violation somebody has to read
	 * as a 500 — see {@code ShSiteNameHeldByTrashException}.</p>
	 */
	@Column(name = "deleted_date")
	private java.time.Instant deletedDate;

	/**
	 * SH1227 — when it went into the trash, which the trash lists. Left out of every payload of a
	 * live site rather than sent as null, so the sites listing does not grow.
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public java.time.Instant getDeletedDate() {
		return deletedDate;
	}

	public void setDeletedDate(java.time.Instant deletedDate) {
		this.deletedDate = deletedDate;
	}

	/** Whether this site is in the trash (SH648) — the readable form of a null check. */
	public boolean isTrashed() {
		return deletedDate != null;
	}

}
