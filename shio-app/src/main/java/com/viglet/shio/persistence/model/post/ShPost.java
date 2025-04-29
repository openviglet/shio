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
package com.viglet.shio.persistence.model.post;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.folder.ShFolder;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.site.ShSite;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * The persistent class for the ShPost database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShPost.findAll", query = "SELECT s FROM ShPost s")
@PrimaryKeyJoinColumn(name = "object_id")
@JsonIgnoreProperties({ "shPostAttrRefs", "shGroups", "shUsers", "shPostDraftAttrRefs", "shWorkflowTasks",
		"shPostAttrsDraft", "shPostAttrsNonDraft" })
public class ShPost extends ShObject implements ShPostImpl {
	private static final long serialVersionUID = 1L;

	private String summary;

	private String title;

	// bi-directional many-to-one association to ShPostType
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_type_id")
	private ShPostType shPostType;

	// bi-directional many-to-one association to ShFolder
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "folder_id")
	private ShFolder shFolder;

	// bi-directional many-to-one association to ShSite
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "site_id")
	private ShSite shSite;

	// bi-directional many-to-one association to ShPostAttr
	@OneToMany(mappedBy = "shPost", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<ShPostAttr> shPostAttrs = new HashSet<>();

	public ShPost() {
		this.setObjectType(ShObjectType.POST);
	}

	@Override
	public String getSummary() {
		return this.summary;
	}

	@Override
	public void setSummary(String summary) {
		this.summary = summary;
	}

	@Override
	public String getTitle() {
		return this.title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public ShPostType getShPostType() {
		return this.shPostType;
	}

	@Override
	public void setShPostType(ShPostType shPostType) {
		this.shPostType = shPostType;
	}

	@Override
	public Set<? extends ShPostAttrImpl> getShPostAttrs() {
		return this.getShPostAttrsNonDraft();
	}

	@Override
	public Set<ShPostAttr> getShPostAttrsNonDraft() {
		return this.shPostAttrs;
	}

	@Override
	public Set<ShPostDraftAttr> getShPostAttrsDraft() {
		return new HashSet<>();
	}

	@Override
	public void setShPostAttrs(Set<? extends ShPostAttrImpl> shPostAttrs) {
		this.shPostAttrs.clear();
		if (shPostAttrs != null)
			shPostAttrs.forEach(shPostAttr -> this.shPostAttrs.add((ShPostAttr) shPostAttr));
	}

	@Override
	public ShPostAttrImpl addShPostAttr(ShPostAttrImpl shPostAttr) {
		getShPostAttrsNonDraft().add((ShPostAttr) shPostAttr);
		shPostAttr.setShPost(this);

		return shPostAttr;
	}

	@Override
	public ShPostAttrImpl removeShPostAttr(ShPostAttrImpl shPostAttr) {
		getShPostAttrs().remove(shPostAttr);
		shPostAttr.setShPost(null);

		return shPostAttr;
	}

	@Override
	public ShFolder getShFolder() {
		return shFolder;
	}

	@Override
	public void setShFolder(ShFolder shFolder) {
		this.shFolder = shFolder;
	}

	@Override
	public ShSite getShSite() {
		return shSite;
	}

	@Override
	public void setShSite(ShSite shSite) {
		this.shSite = shSite;
	}

	@Override
	public String getObjectType() {
		return ShObjectType.POST;
	}

	@Override
	public void setObjectType(String objectType) {
		super.setObjectType(ShObjectType.POST);
	}

}
