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
package com.viglet.shio.persistence.model.workflow;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedQuery;

import lombok.Getter;
import org.hibernate.annotations.GenericGenerator;

import com.viglet.shio.persistence.model.object.ShObject;
import org.hibernate.annotations.UuidGenerator;

/**
 * The persistent class for the ShWorkflowTask database table.
 * 
 * @author Alexandre Oliveira
 */
@Getter
@Entity
@NamedQuery(name = "ShWorkflowTask.findAll", query = "SELECT wt FROM ShWorkflowTask wt")
public class ShWorkflowTask implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@UuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	private Date date;

	private String title;

	@ManyToOne
	@JoinColumn(name = "object_id")
	private ShObject shObject;

	
	// Who created
	private String requester;
	
	// Who will be responsible for the action
	private String requested;

    public void setId(String id) {
		this.id = id;
	}

    public void setDate(Date date) {
		this.date = date;
	}

    public void setRequester(String requester) {
		this.requester = requester;
	}

    public void setRequested(String requested) {
		this.requested = requested;
	}

    public void setTitle(String title) {
		this.title = title;
	}

    public void setShObject(ShObject shObject) {
		this.shObject = shObject;
	}

}
