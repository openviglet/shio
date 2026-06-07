/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.relator;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;

import org.hibernate.annotations.TenantId;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.shio.persistence.utils.ShAssignableUuidGenerator;

/**
 * Minimal relator row left over from the legacy EAV model.
 *
 * <p>In the unified model, relator children live inside {@code ShPostUnified.data}
 * as nested JSON — this row exists only as a placeholder so the old
 * {@code sh_relator_item} table keeps the schema valid if it was created
 * under the previous model.</p>
 */
@Entity
@NamedQuery(name = "ShRelatorItem.findAll", query = "SELECT ri FROM ShRelatorItem ri")
@JsonIgnoreProperties({"$$_hibernate_interceptor", "hibernateLazyInitializer"})
public class ShRelatorItem implements Serializable {
	@Serial
	private static final long serialVersionUID = 1L;

	@Id
	@ShAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@TenantId
	@Column(name = "tenant_id", updatable = false)
	private String tenantId;

	private String title;
	private String summary;
	private int ordinal;

	public String getId() { return id; }
	public void setId(String id) { this.id = id; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getSummary() { return summary; }
	public void setSummary(String summary) { this.summary = summary; }

	public int getOrdinal() { return ordinal; }
	public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
}
