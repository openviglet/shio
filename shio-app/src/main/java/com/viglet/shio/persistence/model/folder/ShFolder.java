/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.folder;

import jakarta.persistence.*;

import lombok.Getter;
import org.hibernate.annotations.Fetch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.site.ShSite;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@NamedQuery(name = "ShFolder.findAll", query = "SELECT c FROM ShFolder c")
@JsonIgnoreProperties({ "shFolders", "shGroups", "shUsers", "shWorkflowTasks", "$$_hibernate_interceptor", "hibernateLazyInitializer"  })
@PrimaryKeyJoinColumn(name = "object_id")
public class ShFolder extends ShObject {
	@Serial
	private static final long serialVersionUID = 1L;

	private String name;

	private byte rootFolder;

	@ManyToOne
	@JoinColumn(name = "parent_folder_id")
	private ShFolder parentFolder;

	@ManyToOne
	@JoinColumn(name = "site_id")
	private ShSite shSite;

	@OneToMany(mappedBy = "parentFolder", orphanRemoval = true, cascade = CascadeType.ALL)
	@Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
	private Set<ShFolder> shFolders = new HashSet<>();

	public ShFolder() {
		this.setObjectType(ShObjectType.FOLDER);
	}

    public void setName(String name) { this.name = name; }
    public void setParentFolder(ShFolder parentFolder) { this.parentFolder = parentFolder; }
    public void setShSite(ShSite shSite) { this.shSite = shSite; }
    public void setShFolders(Set<ShFolder> shFolders) {
		this.shFolders.clear();
		if (shFolders != null) this.shFolders.addAll(shFolders);
	}

    public void setRootFolder(byte rootFolder) { this.rootFolder = rootFolder; }

	@Override
	public String getObjectType() { return ShObjectType.FOLDER; }

	@Override
	public void setObjectType(String objectType) { super.setObjectType(ShObjectType.FOLDER); }
}
