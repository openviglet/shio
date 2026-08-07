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

	/** SH591 — written by the create that declared it, and by nothing else. */
	public void setScratch(Boolean scratch) {
		this.scratch = scratch;
	}

	/** SH591 — null is a site that predates the column, which is a site nobody called disposable. */
	public boolean isScratch() {
		return Boolean.TRUE.equals(this.scratch);
	}
	
}
