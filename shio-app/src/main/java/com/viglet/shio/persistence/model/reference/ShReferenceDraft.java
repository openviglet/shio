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
package com.viglet.shio.persistence.model.reference;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;

import org.hibernate.annotations.GenericGenerator;

import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.object.ShObjectDraft;
import org.hibernate.annotations.UuidGenerator;

/**
 * The persistent class for the ShReferenceDraft database table.
 * 
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShReferenceDraft.findAll", query = "SELECT r FROM ShReferenceDraft r")
public class ShReferenceDraft implements Serializable{
	
	private static final long serialVersionUID = 1L;
	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "object_from" , nullable = false)
	private ShObjectDraft shObjectFrom;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "object_to", nullable = false)
	private ShObject shObjectTo;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ShObjectDraft getShObjectFrom() {
		return shObjectFrom;
	}

	public void setShObjectFrom(ShObjectDraft shObjectFrom) {
		this.shObjectFrom = shObjectFrom;
	}

	public ShObject getShObjectTo() {
		return shObjectTo;
	}

	public void setShObjectTo(ShObject shObjectTo) {
		this.shObjectTo = shObjectTo;
	}

	
}
