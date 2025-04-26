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
package com.viglet.shio.persistence.model.widget;

import java.io.Serializable;
import jakarta.persistence.*;

import org.hibernate.annotations.GenericGenerator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;
import org.hibernate.annotations.UuidGenerator;

import java.util.List;

/**
 * The persistent class for the ShWidget database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShWidget.findAll", query = "SELECT s FROM ShWidget s")
@JsonIgnoreProperties({ "shPostTypeAttrs" })
public class ShWidget implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(name = "class_name")
	private String className;

	private String description;

	@Column(name = "implementation_code")
	private String implementationCode;

	@Column(name = "setting_path")
	private String settingPath;

	private String name;

	private String type;

	// bi-directional many-to-one association to ShPostTypeAttr
	@OneToMany(mappedBy = "shWidget")
	private List<ShPostTypeAttr> shPostTypeAttrs;

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getClassName() {
		return this.className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getDescription() {
		return this.description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getImplementationCode() {
		return this.implementationCode;
	}

	public void setImplementationCode(String implementationCode) {
		this.implementationCode = implementationCode;
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setName(ShSystemWidget shSystemWidget) {
		this.name = shSystemWidget.toString();
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public List<ShPostTypeAttr> getShPostTypeAttrs() {
		return this.shPostTypeAttrs;
	}

	public void setShPostTypeAttrs(List<ShPostTypeAttr> shPostTypeAttrs) {
		this.shPostTypeAttrs = shPostTypeAttrs;
	}

	public ShPostTypeAttr addShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		getShPostTypeAttrs().add(shPostTypeAttr);
		shPostTypeAttr.setShWidget(this);

		return shPostTypeAttr;
	}

	public ShPostTypeAttr removeShPostTypeAttr(ShPostTypeAttr shPostTypeAttr) {
		getShPostTypeAttrs().remove(shPostTypeAttr);
		shPostTypeAttr.setShWidget(null);

		return shPostTypeAttr;
	}

	public String getSettingPath() {
		return settingPath;
	}

	public void setSettingPath(String settingPath) {
		this.settingPath = settingPath;
	}

}
