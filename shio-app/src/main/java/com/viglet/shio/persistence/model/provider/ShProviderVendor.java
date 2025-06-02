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
import lombok.Getter;
import lombok.Setter;

/**
 * The persistent class for the ShProviderVendor database table.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Setter
@Getter
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

}
