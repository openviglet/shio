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

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
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
import org.hibernate.annotations.TenantId;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.workflow.ShWorkflowTask;

/**
 * The persistent class for the ShObject database table.
 *
 * @author Alexandre Oliveira
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NamedQuery(name = "ShObject.findAll", query = "SELECT o FROM ShObject o")
@JsonIgnoreProperties({"shGroups", "shUsers", "summary", "shWorkflowTasks"})
public class ShObject implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@TenantId
	@Column(name = "tenant_id", updatable = false)
	private String tenantId;

	private Instant date;

	private String owner;

	private String furl;

	private String modifier;

	private String publisher;

	private Instant modifiedDate;

	private Instant publicationDate;

	private int position;

	private String objectType;

	private boolean published;

	@ElementCollection
	private Set<String> shGroups = new HashSet<>();

	@ElementCollection
	private Set<String> shUsers = new HashSet<>();

	/**
	 * Lazy, which a collection is by default. SH1236 — it was {@code @Fetch(JOIN)}, which makes a
	 * collection eager, and a query cannot join it implicitly, so every query over a site, folder
	 * or post type paid one more select per row to fill it: 202 statements for a folder's 200
	 * children. No read path touches it and every serializer ignores it; the cascade still reaches
	 * it when an object is removed. {@code ShWorkflowTaskFetchCostTest} holds the count flat.
	 */
	@OneToMany(mappedBy = "shObject", cascade = jakarta.persistence.CascadeType.ALL)
	private Set<ShWorkflowTask> shWorkflowTasks = new HashSet<>();

	private String publishStatus;

	private boolean pageAllowRegisterUser = false;

	private boolean pageAllowGuestUser = true;

	@ElementCollection
	private Set<String> shPageGroups = new HashSet<>();

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTenantId() { return tenantId; }
	public void setTenantId(String tenantId) { this.tenantId = tenantId; }

	public String getOwner() { return owner; }
	public void setOwner(String owner) { this.owner = owner; }

	public String getFurl() { return furl; }
	public void setFurl(String furl) { this.furl = furl; }

	public Instant getDate() { return date; }
	public void setDate(Instant date) { this.date = date; }

	public String getModifier() { return modifier; }
	public void setModifier(String modifier) { this.modifier = modifier; }

	public String getPublisher() { return publisher; }
	public void setPublisher(String publisher) { this.publisher = publisher; }

	public Instant getModifiedDate() { return modifiedDate; }
	public void setModifiedDate(Instant modifiedDate) { this.modifiedDate = modifiedDate; }

	public Instant getPublicationDate() { return publicationDate; }
	public void setPublicationDate(Instant publicationDate) { this.publicationDate = publicationDate; }

	public int getPosition() { return position; }
	public void setPosition(int position) { this.position = position; }

	public String getObjectType() { return objectType; }
	public void setObjectType(String objectType) { this.objectType = objectType; }

	public boolean isPublished() { return published; }
	public void setPublished(boolean published) { this.published = published; }

	public String getPublishStatus() { return publishStatus; }
	public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }

	public Set<ShWorkflowTask> getShWorkflowTasks() { return this.shWorkflowTasks; }
	public void setShWorkflowTasks(Set<ShWorkflowTask> shWorkflowTasks) {
		this.shWorkflowTasks.clear();
		if (shWorkflowTasks != null) this.shWorkflowTasks.addAll(shWorkflowTasks);
	}

	public Set<String> getShUsers() { return shUsers; }
	public void setShUsers(Set<String> shUsers) { this.shUsers = shUsers; }

	public Set<String> getShGroups() { return shGroups; }
	public void setShGroups(Set<String> shGroups) { this.shGroups = shGroups; }

	public boolean isPageAllowRegisterUser() { return pageAllowRegisterUser; }
	public void setPageAllowRegisterUser(boolean b) { this.pageAllowRegisterUser = b; }

	public boolean isPageAllowGuestUser() { return pageAllowGuestUser; }
	public void setPageAllowGuestUser(boolean b) { this.pageAllowGuestUser = b; }

	public Set<String> getShPageGroups() { return shPageGroups; }
	public void setShPageGroups(Set<String> shPageGroups) { this.shPageGroups = shPageGroups; }
}
