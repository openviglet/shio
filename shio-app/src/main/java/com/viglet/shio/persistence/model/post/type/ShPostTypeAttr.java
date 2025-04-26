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

import java.io.Serializable;
import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import org.hibernate.annotations.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.widget.ShWidget;
import org.hibernate.annotations.CascadeType;

import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the ShPostTypeAttr database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShPostTypeAttr.findAll", query = "SELECT s FROM ShPostTypeAttr s")
@JsonIgnoreProperties({ "shPostAttrs", "shPostDraftAttrs", "$$_hibernate_interceptor", "hibernateLazyInitializer" })
// Removed shPostType ignore, because it is used in JSON from new Post
public class ShPostTypeAttr implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String description;

	private byte isSummary;

	private byte isTitle;

	private String label;

	private String name;

	private int ordinal;

	private byte required;

	@OneToMany(mappedBy = "shParentPostTypeAttr", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

	@OneToMany(mappedBy = "shPostTypeAttr", fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	private Set<ShPostAttr> shPostAttrs = new HashSet<>();

	@OneToMany(mappedBy = "shPostTypeAttr", fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	private Set<ShPostDraftAttr> shPostDraftAttrs = new HashSet<>();

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "postType_id")
	@JsonView({ ShJsonView.ShJsonViewPostTypeAttr.class })
	private ShPostType shPostType;

	// Remove LAZY because it have few shWidgets
	@ManyToOne
	@JoinColumn(name = "widget_id")
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	private ShWidget shWidget;

	@Column(name = "widget_settings", length = 5 * 1024 * 1024) // 5Mb
	private String widgetSettings;

	// bi-directional many-to-one association to ShPostTypeAttr
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_relator_id")
	@JsonView({ ShJsonView.ShJsonViewPostTypeAttr.class })
	private ShPostTypeAttr shParentPostTypeAttr;

	@Transient
	private String shPostTypeName;

	public String getShPostTypeName() {
		if (shPostType != null) {
			shPostTypeName = shPostType.getName();
		}
		return shPostTypeName;
	}

	public ShPostTypeAttr() {
		super();
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public byte getIsSummary() {
		return this.isSummary;
	}

	public void setIsSummary(byte isSummary) {
		this.isSummary = isSummary;
	}

	public byte getIsTitle() {
		return this.isTitle;
	}

	public void setIsTitle(byte isTitle) {
		this.isTitle = isTitle;
	}

	public String getLabel() {
		return this.label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getOrdinal() {
		return this.ordinal;
	}

	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}

	public byte getRequired() {
		return this.required;
	}

	public void setRequired(byte required) {
		this.required = required;
	}

	public Set<ShPostAttr> getShPostAttrs() {
		return this.shPostAttrs;
	}

	public void setShPostAttrs(Set<ShPostAttr> shPostAttrs) {
		this.shPostAttrs.clear();
		if (shPostAttrs != null) {
			this.shPostAttrs.addAll(shPostAttrs);
		}
	}

	public Set<ShPostDraftAttr> getShPostDraftAttrs() {
		return this.shPostDraftAttrs;
	}

	public void setShPostDraftAttrs(Set<ShPostDraftAttr> shPostDraftAttrs) {
		this.shPostDraftAttrs.clear();
		if (shPostDraftAttrs != null) {
			this.shPostDraftAttrs.addAll(shPostDraftAttrs);
		}
	}

	public ShPostAttrImpl addShPostAttr(ShPostAttr shPostAttr) {
		getShPostAttrs().add(shPostAttr);
		shPostAttr.setShPostTypeAttr(this);

		return shPostAttr;
	}

	public ShPostAttrImpl removeShPostAttr(ShPostAttrImpl shPostAttr) {
		getShPostAttrs().remove(shPostAttr);
		shPostAttr.setShPostTypeAttr(null);

		return shPostAttr;
	}

	public ShPostType getShPostType() {
		return this.shPostType;
	}

	public void setShPostType(ShPostType shPostType) {
		this.shPostType = shPostType;
	}

	public ShWidget getShWidget() {
		return this.shWidget;
	}

	public void setShWidget(ShWidget shWidget) {
		this.shWidget = shWidget;
	}

	public Set<ShPostTypeAttr> getShPostTypeAttrs() {
		return shPostTypeAttrs;
	}

	public void setShPostTypeAttrs(Set<ShPostTypeAttr> shPostTypeAttrs) {
		this.shPostTypeAttrs.clear();
		if (shPostTypeAttrs != null) {
			this.shPostTypeAttrs.addAll(shPostTypeAttrs);
		}
	}

	public ShPostTypeAttr getShParentPostTypeAttr() {
		return shParentPostTypeAttr;
	}

	public void setShParentPostTypeAttr(ShPostTypeAttr shParentPostTypeAttr) {
		this.shParentPostTypeAttr = shParentPostTypeAttr;
	}

	public String getWidgetSettings() {
		return widgetSettings;
	}

	public void setWidgetSettings(String widgetSettings) {
		this.widgetSettings = widgetSettings;
	}

}
