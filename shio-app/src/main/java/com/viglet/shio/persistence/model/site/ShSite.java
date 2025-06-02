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
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
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
	
	@OneToMany(mappedBy = "shSite")
	@Cascade({ CascadeType.ALL })
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private Set<ShFolder> shFolders = new HashSet<>();

	private String formSuccess;
	
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
	
}
