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
package com.viglet.shio.persistence.model.system;

import java.io.Serial;
import java.io.Serializable;
import jakarta.persistence.*;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;

/**
 * The persistent class for the ShConfigVar database table.
 * 
 * @author Alexandre Oliveira
 */
@Setter
@Getter
@Entity
@Table(name = "shConfigVar")
@NamedQuery(name = "ShConfigVar.findAll", query = "SELECT cv FROM ShConfigVar cv")
public class ShConfigVar implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@Column(nullable = true, length = 255)
	private String path;

	@Column(nullable = true, length = 255)
	private String name;
	
	@Column(nullable = true, length = 255)
	private String value;


}
