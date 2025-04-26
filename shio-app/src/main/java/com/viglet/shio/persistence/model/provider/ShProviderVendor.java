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
package com.viglet.shio.persistence.model.provider;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The persistent class for the ShProviderVendor database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@NamedQuery(name = "ShProviderVendor.findAll", query = "SELECT pv FROM ShProviderVendor pv")
@JsonIgnoreProperties({ "instances" })
public class ShProviderVendor {

	@Id
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String name;

	@Column(name = "class_name")
	private String className;

	private String description;

	@Column(name = "configurationPage")
	private String configurationPage;

	@OneToMany(mappedBy = "vendor")
	private List<ShProviderInstance> instances;

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

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getConfigurationPage() {
		return configurationPage;
	}

	public void setConfigurationPage(String configurationPage) {
		this.configurationPage = configurationPage;
	}

	public List<ShProviderInstance> getInstances() {
		return instances;
	}

	public void setInstances(List<ShProviderInstance> instances) {
		this.instances = instances;
	}

}
