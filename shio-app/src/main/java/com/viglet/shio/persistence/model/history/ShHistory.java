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
package com.viglet.shio.persistence.model.history;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;

import lombok.Getter;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.UuidGenerator;

/**
 * The persistent class for the ShHistory database table.
 * 
 * @author Alexandre Oliveira
 */
@Getter
@Entity
@NamedQuery(name = "ShHistory.findAll", query = "SELECT h FROM ShHistory h")
public class ShHistory implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private String owner;

	private Date date;

	private String description;

	private String shObject;

	private String shSite;

    public void setId(String id) {
		this.id = id;
	}

    public void setOwner(String owner) {
		this.owner = owner;
	}

    public void setDate(Date date) {
		this.date = date;
	}

    public void setDescription(String description) {
		this.description = description;
	}

    public void setShObject(String shObject) {
		this.shObject = shObject;
	}

    public void setShSite(String shSite) {
		this.shSite = shSite;
	}
	

}
