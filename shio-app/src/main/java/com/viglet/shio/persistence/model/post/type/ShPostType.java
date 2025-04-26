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
package com.viglet.shio.persistence.model.post.type;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.search.annotations.Field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.post.type.ShSystemPostType;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * The persistent class for the ShPostType database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShPostType.findAll", query = "SELECT s FROM ShPostType s")
@JsonIgnoreProperties({ "shPosts", "shPostDrafts", "shPostAttrs", "shPostDraftAttrs", "shPostAttrRefs", "shPostDraftAttrRefs", "shGroups", "$$_hibernate_interceptor", "hibernateLazyInitializer" })
@PrimaryKeyJoinColumn(name = "object_id")
public class ShPostType extends ShObject {
	private static final long serialVersionUID = 1L;

	private String description;

	@Field
	private String name;
	
	private String namePlural;

	private String title;

	private byte system;

	// bi-directional many-to-one association to ShPost
	@OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<ShPost> shPosts = new HashSet<>();

	@OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
	private Set<ShPostDraft> shPostDrafts = new HashSet<>();

	// bi-directional many-to-one association to ShPostTypeAttr
	@OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JsonView({ ShJsonView.ShJsonViewPostType.class })
	private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

	private String workflowPublishEntity;

	public ShPostType() {
		this.setObjectType(ShObjectType.POST_TYPE);
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setName(ShSystemPostType shSystemPostType) {
		this.name = shSystemPostType.toString();
	}
	
	public String getNamePlural() {
		return namePlural;
	}

	public void setNamePlural(String namePlural) {
		this.namePlural = namePlural;
	}

	public String getTitle() {
		return this.title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Set<ShPost> getShPosts() {
		return this.shPosts;
	}

	public void setShPosts(Set<ShPost> shPosts) {
		this.shPosts.clear();
		if (shPosts != null) {
			this.shPosts.addAll(shPosts);
		}
	}

	public Set<ShPostDraft> getShPostDrafts() {
		return this.shPostDrafts;
	}

	public void setShPostDrafts(Set<ShPostDraft> shPostDrafts) {
		this.shPostDrafts.clear();
		if (shPostDrafts != null) {
			this.shPostDrafts.addAll(shPostDrafts);
		}
	}

	public ShPostImpl addShPost(ShPost shPost) {
		getShPosts().add(shPost);
		shPost.setShPostType(this);

		return shPost;
	}

	public ShPostImpl removeShPost(ShPostImpl shPost) {
		getShPosts().remove(shPost);
		shPost.setShPostType(null);

		return shPost;
	}

	public Set<ShPostTypeAttr> getShPostTypeAttrs() {
		return this.shPostTypeAttrs;
	}

	public void setShPostTypeAttrs(Set<ShPostTypeAttr> shPostTypeAttrs) {
		this.shPostTypeAttrs.clear();
		if (shPostTypeAttrs != null) {
			this.shPostTypeAttrs.addAll(shPostTypeAttrs);
		}
	}

	public ShPostTypeAttr addShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		getShPostTypeAttrs().add(shPostTypeAttr);
		shPostTypeAttr.setShPostType(this);

		return shPostTypeAttr;
	}

	public ShPostTypeAttr removeShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		getShPostTypeAttrs().remove(shPostTypeAttr);
		shPostTypeAttr.setShPostType(null);

		return shPostTypeAttr;
	}

	public byte getSystem() {
		return system;
	}

	public void setSystem(byte system) {
		this.system = system;
	}

	@Override
	public String getObjectType() {
		return ShObjectType.POST_TYPE;
	}

	@Override
	public void setObjectType(String objectType) {
		super.setObjectType(ShObjectType.POST_TYPE);
	}

	public String getWorkflowPublishEntity() {
		return workflowPublishEntity;
	}

	public void setWorkflowPublishEntity(String workflowPublishEntity) {
		this.workflowPublishEntity = workflowPublishEntity;
	}

}
