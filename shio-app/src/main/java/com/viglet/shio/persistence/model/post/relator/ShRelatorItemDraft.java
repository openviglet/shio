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
package com.viglet.shio.persistence.model.post.relator;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;

import org.hibernate.annotations.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;

/**
 * The persistent class for the ShRelatorItemDraft database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShRelatorItemDraft.findAll", query = "SELECT rid FROM ShRelatorItemDraft rid")
@JsonIgnoreProperties({ "shPostAttr", "shParentPostAttr" })
public class ShRelatorItemDraft implements Serializable, ShRelatorItemImpl {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String title;

	private String summary;

	private int ordinal;

	// bi-directional many-to-one association to ShPostAttr
	@OneToMany(mappedBy = "shParentRelatorItem", orphanRemoval = true, fetch = FetchType.LAZY)
	// Need this join, because without this show error when try to remove a relator
	// item.
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<ShPostDraftAttr> shChildrenPostAttrs = new HashSet<>();

	// bi-directional many-to-one association to ShPost
	@ManyToOne(fetch = FetchType.LAZY) // (cascade = {CascadeType.ALL})
	@JoinColumn(name = "post_attr_id")
	private ShPostDraftAttr shParentPostAttr;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}
	
	@Override
	public Set<? extends ShPostAttrImpl> getShChildrenPostAttrs() {

		return this.getShChildrenPostAttrsDraft();
	}

	@Override
	public Set<ShPostAttr> getShChildrenPostAttrsNonDraft() {
		return new HashSet<>();
	}

	@Override
	public Set<ShPostDraftAttr> getShChildrenPostAttrsDraft() {		
		return shChildrenPostAttrs;
	}
	
	@Override
	public void setShChildrenPostAttrs(Set<? extends ShPostAttrImpl> shChildrenPostAttrs) {
		this.shChildrenPostAttrs.clear();
		if (shChildrenPostAttrs != null) {
			shChildrenPostAttrs
					.forEach(shChildrenPostAttr -> this.shChildrenPostAttrs.add((ShPostDraftAttr) shChildrenPostAttr));
		}
	}

	@Override
	public ShPostAttrImpl getShParentPostAttr() {
		return shParentPostAttr;
	}

	@Override
	public void setShParentPostAttr(ShPostAttrImpl shParentPostAttr) {
		this.shParentPostAttr = (ShPostDraftAttr) shParentPostAttr;
	}

	@Override
	public String getTitle() {
		return title;
	}

	@Override
	public void setTitle(String title) {
		this.title = title;
	}

	@Override
	public String getSummary() {
		return summary;
	}

	@Override
	public void setSummary(String summary) {
		this.summary = summary;
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}

	@Override
	public void setOrdinal(int ordinal) {
		this.ordinal = ordinal;
	}

}