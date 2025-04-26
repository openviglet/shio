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
import jakarta.persistence.*;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the ShRole database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@Table(name = "shRole")
@NamedQuery(name = "ShRole.findAll", query = "SELECT r FROM ShRole r")
public class ShRole implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;


	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(nullable = true, length = 255)
	private String description;

	@ManyToMany
	private Set<ShGroup> shGroups = new HashSet<>();

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public Set<ShGroup> getShGroups() {
		return this.shGroups;
	}

	public void setShGroups(Set<ShGroup> shGroups) {
		this.shGroups.clear();
		if (shGroups != null) {
			this.shGroups.addAll(shGroups);
		}
	}

}