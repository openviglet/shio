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

	/**
	 * When this folder was put in the trash (SH200); {@code null} = live.
	 *
	 * <p>The folder half of SH55's soft-delete, and the reason a folder delete can be
	 * undone at all: the posts beneath it are trashed rather than removed, and their
	 * {@code folder_id → ShObject} FK means the folder row has to outlive the gesture that
	 * deleted it. So a folder delete removes nothing; it stamps this column down the
	 * subtree, and the rows go at <b>purge</b> time instead.</p>
	 *
	 * <p>Unlike {@link com.viglet.shio.persistence.model.post.unified.ShPostUnified} there
	 * is deliberately <b>no {@code @SQLRestriction}</b> here. A restriction on this entity
	 * would also apply when Hibernate initializes a {@code @ManyToOne} pointing at it — so
	 * a trashed post's {@code getShFolder()} would throw, and the trash listing needs
	 * exactly that association to say where the post came from. Trashed folders are
	 * excluded in {@code ShFolderRepository} instead, where every read already goes.</p>
	 */
	@Column(name = "deleted_date")
	private java.time.Instant deletedDate;

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

	public void setDeletedDate(java.time.Instant deletedDate) { this.deletedDate = deletedDate; }

	/**
	 * SH1227 — when it went into the trash, which the trash lists. Left out of every payload of a
	 * live folder rather than sent as null, so no other read grows.
	 */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
	public java.time.Instant getDeletedDate() { return deletedDate; }

	/** Whether this folder is in the trash (SH200) — the readable form of a null check. */
	public boolean isTrashed() { return deletedDate != null; }

	@Override
	public String getObjectType() { return ShObjectType.FOLDER; }

	@Override
	public void setObjectType(String objectType) { super.setObjectType(ShObjectType.FOLDER); }
}
