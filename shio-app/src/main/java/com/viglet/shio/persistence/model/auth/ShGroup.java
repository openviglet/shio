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
package com.viglet.shio.persistence.model.auth;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.*;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;

/**
 * The persistent class for the ShGroup database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShGroup.findAll", query = "SELECT g FROM ShGroup g")
public class ShGroup implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String name;

	private String description;

	@ManyToMany(mappedBy = "shGroups")
	private Set<ShRole> shRoles = new HashSet<>();

	@ManyToMany(mappedBy = "shGroups")
	private Set<ShUser> shUsers = new HashSet<>();

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Set<ShUser> getShUsers() {
		return this.shUsers;
	}

	public void setShUsers(Set<ShUser> shUsers) {
		this.shUsers.clear();
		if (shUsers != null) {
			this.shUsers.addAll(shUsers);
		}
	}
}