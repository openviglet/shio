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

import java.io.Serializable;

import org.hibernate.annotations.*;
import org.hibernate.search.annotations.Field;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.persistence.model.post.relator.ShRelatorItem;
import com.viglet.shio.persistence.model.post.relator.impl.ShRelatorItemImpl;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

/**
 * The persistent class for the ShPostAttr database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShPostAttr.findAll", query = "SELECT s FROM ShPostAttr s")
@JsonIgnoreProperties({ "shPostType", "shPost", "shParentRelatorItem", "tab" })
public class ShPostAttr implements Serializable, ShPostAttrImpl {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "date_value")
	private Date dateValue;

	@Column(name = "int_value")
	private int intValue;

	@Field
	@Column(name = "str_value", length = 5 * 1024 * 1024) // 5Mb
	private String strValue;

	@ElementCollection
	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
	@CollectionTable(name = "sh_post_attr_array_value")
	@JoinColumn(name = "post_attr_id")
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<String> arrayValue = new HashSet<>();

	// bi-directional many-to-one association to shObject
	@ManyToOne
	@JoinColumn(name = "object_id")
	// Void Cyclic Reference
	@JsonIgnoreProperties({ "shPostAttrs" })
	private ShObject referenceObject;

	private int type;

	// bi-directional many-to-one association to ShRelatorItem
	@OneToMany(mappedBy = "shParentPostAttr", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<ShRelatorItem> shChildrenRelatorItems = new HashSet<>();

	// bi-directional many-to-one association to ShPost
	@ManyToOne(fetch = FetchType.LAZY) // (cascade = {CascadeType.ALL})
	@JoinColumn(name = "post_id")
	private ShPost shPost;

	// bi-directional many-to-one association to ShPostTypeAttr
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "post_type_attr_id")
	private ShPostTypeAttr shPostTypeAttr;

	// bi-directional many-to-one association to ShPost
	@ManyToOne(fetch = FetchType.LAZY) // (cascade = {CascadeType.ALL})
	@JoinColumn(name = "post_attr_id")
	private ShRelatorItem shParentRelatorItem;

	@Override
	public ShObject getReferenceObject() {
		return referenceObject;
	}

	@Override
	public void setReferenceObject(ShObject referenceObject) {
		this.referenceObject = referenceObject;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public Date getDateValue() {
		return this.dateValue;
	}

	@Override
	public void setDateValue(Date dateValue) {
		this.dateValue = dateValue;
	}

	@Override
	public int getIntValue() {
		return this.intValue;
	}

	@Override
	public void setIntValue(int intValue) {
		this.intValue = intValue;
	}

	@Override
	public String getStrValue() {
		return this.strValue;
	}

	@Override
	public void setStrValue(String strValue) {
		this.strValue = strValue;
	}

	@Override
	public Set<String> getArrayValue() {
		return arrayValue;
	}

	@Override
	public void setArrayValue(Set<String> arrayValue) {
		this.arrayValue.clear();
		if (arrayValue != null) {
			this.arrayValue.addAll(arrayValue);
		}
	}

	@Override
	public int getType() {
		return this.type;
	}

	@Override
	public void setType(int type) {
		this.type = type;
	}

	@Override
	public ShPost getShPost() {
		return this.shPost;
	}

	@Override
	public void setShPost(ShPostImpl shPost) {
		this.shPost = (ShPost) shPost;
	}

	@Override
	public ShPostTypeAttr getShPostTypeAttr() {
		return this.shPostTypeAttr;
	}

	@Override
	public void setShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		this.shPostTypeAttr = shPostTypeAttr;
	}

	@Override
	public Set<? extends ShRelatorItemImpl> getShChildrenRelatorItems() {
		return shChildrenRelatorItems;
	}

	@Override
	public void setShChildrenRelatorItems(Set<? extends ShRelatorItemImpl> shChildrenRelatorItems) {
		this.shChildrenRelatorItems.clear();
		if (shChildrenRelatorItems != null) {
			shChildrenRelatorItems.forEach(
					shChildrenRelatorItem -> this.shChildrenRelatorItems.add((ShRelatorItem) shChildrenRelatorItem));
		}
	}

	@Override
	public ShRelatorItemImpl getShParentRelatorItem() {
		return shParentRelatorItem;
	}

	@Override
	public void setShParentRelatorItem(ShRelatorItemImpl shParentRelatorItem) {
		this.shParentRelatorItem = (ShRelatorItem) shParentRelatorItem;
	}

}
