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
package com.viglet.shio.persistence.model.object;

import java.io.Serializable;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.object.impl.ShObjectImpl;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.post.impl.ShPostAttrImpl;

/**
 * The persistent class for the ShObjectDraft database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NamedQuery(name = "ShObjectDraft.findAll", query = "SELECT od FROM ShObjectDraft od")
@JsonIgnoreProperties({ "shPostAttrRefs", "shGroups", "shUsers", "summary" })
public class ShObjectDraft implements Serializable, ShObjectImpl {

	private static final long serialVersionUID = 1L;
	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	// bi-directional many-to-one association to ShObject
//	@OneToMany(mappedBy = "referenceObject") // , orphanRemoval = true)
//	@Fetch(org.hibernate.annotations.FetchMode.JOIN)
//	@Cascade({ CascadeType.ALL })
//	@OnDelete(action = OnDeleteAction.CASCADE)
//	private Set<ShPostDraftAttr> shPostAttrRefs = new HashSet<>();

	@Temporal(TemporalType.TIMESTAMP)
	private Date date;

	private String owner;

	private String furl;

	private String modifier;

	private String publisher;

	private Date modifiedDate;

	private Date publicationDate;

	private int position;

	private String objectType;

	private boolean published;

	@Column(name = "draft", length = 5 * 1024 * 1024) // 5Mb
	private String draft;

	@ElementCollection
	private Set<String> shGroups = new HashSet<>();

	@ElementCollection
	private Set<String> shUsers = new HashSet<>();

	private String publishStatus;

	private boolean pageAllowRegisterUser = false;

	private boolean pageAllowGuestUser = true;

	@ElementCollection
	private Set<String> shPageGroups = new HashSet<>();
	@Override
	public String getId() {
		return this.id;
	}
	@Override
	public void setId(String id) {
		this.id = id;
	}
	@Override
	public String getOwner() {
		return owner;
	}
	@Override
	public void setOwner(String owner) {
		this.owner = owner;
	}
	@Override
	public String getFurl() {
		return furl;
	}
	@Override
	public void setFurl(String furl) {
		this.furl = furl;
	}
	@Override
	public Date getDate() {
		return this.date;
	}
	@Override
	public void setDate(Date date) {
		this.date = date;
	}
	@Override
	public String getModifier() {
		return modifier;
	}
	@Override
	public void setModifier(String modifier) {
		this.modifier = modifier;
	}
	@Override
	public String getPublisher() {
		return publisher;
	}
	@Override
	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}
	@Override
	public Date getModifiedDate() {
		return modifiedDate;
	}
	@Override
	public void setModifiedDate(Date modifiedDate) {
		this.modifiedDate = modifiedDate;
	}
	@Override
	public Date getPublicationDate() {
		return publicationDate;
	}
	@Override
	public void setPublicationDate(Date publicationDate) {
		this.publicationDate = publicationDate;
	}
	@Override
	public int getPosition() {
		return position;
	}
	@Override
	public void setPosition(int position) {
		this.position = position;
	}
	@Override
	public String getObjectType() {
		return objectType;
	}
	@Override
	public void setObjectType(String objectType) {
		this.objectType = objectType;
	}

	@Override
	public Set<? extends ShPostAttrImpl> getShPostAttrRefs() {
		return Set.of();
	}

	@Override
	public void setShPostAttrRefs(Set<? extends ShPostAttrImpl> shPostAttrRefs) {

	}

	//@Override
	//public Set<? extends ShPostAttrImpl> getShPostAttrRefs() {
	//	return shPostAttrRefs;
	//}
	//@Override
	//public void setShPostAttrRefs(Set<? extends ShPostAttrImpl> shPostAttrRefs) {
	//	this.shPostAttrRefs.clear();
	//	if (shPostAttrRefs != null)
	//		shPostAttrRefs.forEach(shPostAttrRef -> this.shPostAttrRefs.add((ShPostDraftAttr) shPostAttrRef));
	//}
	@Override
	public Set<String> getShGroups() {
		return shGroups;
	}
	@Override
	public void setShGroups(Set<String> shGroups) {
		this.shGroups = shGroups;
	}

	public String getDraft() {
		return draft;
	}

	public void setDraft(String draft) {
		this.draft = draft;
	}
	@Override
	public boolean isPublished() {
		return published;
	}
	@Override
	public void setPublished(boolean published) {
		this.published = published;
	}
	@Override
	public String getPublishStatus() {
		return publishStatus;
	}
	@Override
	public void setPublishStatus(String publishStatus) {
		this.publishStatus = publishStatus;
	}
	@Override
	public Set<String> getShUsers() {
		return shUsers;
	}
	@Override
	public void setShUsers(Set<String> shUsers) {
		this.shUsers = shUsers;
	}
	@Override
	public boolean isPageAllowRegisterUser() {
		return pageAllowRegisterUser;
	}
	@Override
	public void setPageAllowRegisterUser(boolean pageAllowRegisterUser) {
		this.pageAllowRegisterUser = pageAllowRegisterUser;
	}
	@Override
	public boolean isPageAllowGuestUser() {
		return pageAllowGuestUser;
	}
	@Override
	public void setPageAllowGuestUser(boolean pageAllowGuestUser) {
		this.pageAllowGuestUser = pageAllowGuestUser;
	}
	@Override
	public Set<String> getShPageGroups() {
		return shPageGroups;
	}
	@Override
	public void setShPageGroups(Set<String> shPageGroups) {
		this.shPageGroups = shPageGroups;
	}

}
