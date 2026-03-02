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
package com.viglet.shio.persistence.model.folder;

import jakarta.persistence.*;

import lombok.Getter;
import org.hibernate.annotations.Fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.object.ShObjectDraft;
import com.viglet.shio.persistence.model.site.ShSite;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the ShFolderDraft database table.
 * 
 * @author Alexandre Oliveira
 */
@Getter
@Entity
@NamedQuery(name = "ShFolderDraft.findAll", query = "SELECT c FROM ShFolderDraft c")
@JsonIgnoreProperties({ "shFolders", "shPosts", "shPostAttrRefs", "shGroups", "shUsers", "shPostDrafts", "shPostDraftAttrRefs", "$$_hibernate_interceptor", "hibernateLazyInitializer" })
@PrimaryKeyJoinColumn(name = "object_id")
public class ShFolderDraft extends ShObjectDraft {
	@Serial
	private static final long serialVersionUID = 1L;

	private String name;

    private byte rootFolder;

	// bi-directional many-to-one association to ShFolder
	@ManyToOne
	@JoinColumn(name = "parent_folder_id")
	private ShFolderDraft parentFolder;

	// bi-directional many-to-one association to ShSite
	@ManyToOne
	@JoinColumn(name = "site_id")
	private ShSite shSite;

	// bi-directional many-to-one association to ShFolder
	@OneToMany(mappedBy = "parentFolder", orphanRemoval = true, cascade = jakarta.persistence.CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private Set<ShFolderDraft> shFolders = new HashSet<>();

	
	public ShFolderDraft() {
		this.setObjectType(ShObjectType.FOLDER);
	}

	
    public void setParentFolder(ShFolderDraft parentFolder) {
		this.parentFolder = parentFolder;
	}

    public void setShSite(ShSite shSite) {
		this.shSite = shSite;
	}

    public void setShFolders(Set<ShFolderDraft> shFolders) {
		this.shFolders.clear();
		if (shFolders != null) {
			this.shFolders.addAll(shFolders);
		}
	}

    public void setRootFolder(byte rootFolder) {
		this.rootFolder = rootFolder;
	}

	@Override
	public String getObjectType() {
		return ShObjectType.FOLDER;
	}

	@Override
	public void setObjectType(String objectType) {		
		super.setObjectType(ShObjectType.FOLDER);
	}
}
