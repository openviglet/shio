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

import java.io.Serial;
import java.io.Serializable;
import jakarta.persistence.*;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import java.util.List;

/**
 * The persistent class for the ShWidget database table.
 *
 * <h2>What a widget is, and the three columns that left (SH285)</h2>
 *
 * <p>A widget is a <b>catalogue key</b>. {@code name} is the whole contract: a post-type field
 * references it, {@code ShRenderPostTypes} resolves it, and the React console maps it onto a
 * component ({@code WIDGET_MAP}). Nothing about the widget's behaviour lives here — the behaviour is
 * in the console's code, keyed by that name.</p>
 *
 * <p>It used to carry three more: {@code class_name}
 * ({@code com.viglet.shio.widget.ShTextWidget} and friends — a package that today holds exactly one
 * file, the {@code ShSystemWidget} constants class), {@code implementation_code} and
 * {@code setting_path} (both {@code template/widget/**}, Thymeleaf paths from the AngularJS UI
 * Block E retired, into a directory this repository does not contain). Nothing read them, and two
 * were <em>writable</em> through {@code PUT /api/v2/widget/{id}} — a field a caller can set that
 * cannot affect anything, which invites the belief that a widget is extensible by pointing it at a
 * template. It is not.</p>
 *
 * <p>{@code description} and {@code type} stayed, and the distinction is SH293's: those two state
 * something <b>true</b> (a Date widget does back {@code DATE,TEXT}) and are merely unread, where the
 * three that went named something <b>absent</b>. Dormant data is not a false capability.</p>
 *
 * @author Alexandre Oliveira
 */
@Getter
@Entity
@NamedQuery(name = "ShWidget.findAll", query = "SELECT s FROM ShWidget s")
@JsonIgnoreProperties({ "shPostTypeAttrs" })
public class ShWidget implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String description;

	private String name;

	private String type;

	// bi-directional many-to-one association to ShPostTypeAttr
	@OneToMany(mappedBy = "shWidget")
	private List<ShPostTypeAttr> shPostTypeAttrs;

    public void setId(String id) {
		this.id = id;
	}

    public void setDescription(String description) {
		this.description = description;
	}

    public void setName(String name) {
		this.name = name;
	}

	public void setName(ShSystemWidget shSystemWidget) {
		this.name = shSystemWidget.toString();
	}

    public void setType(String type) {
		this.type = type;
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

}
