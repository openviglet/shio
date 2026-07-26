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
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQuery;

import lombok.Getter;
import org.hibernate.annotations.TenantId;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;

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
	@VigletAssignableUuidGenerator
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@TenantId
	@Column(name = "tenant_id", updatable = false)
	private String tenantId;

	private String owner;

	private Instant date;

	/** Structured transition kind (SH51): CREATE / UPDATE / PUBLISH / UNPUBLISH / DELETE / RESTORE. */
	private String action;

	private String description;

	private String shObject;

	private String shSite;

	/**
	 * Which agent session made this change (SH106), or {@code null} for a human.
	 *
	 * <p>SH75 minted the id and stamped it into the {@code owner} string as
	 * {@code alice (agent:7f3c…)}, explicitly as a placeholder: a column is the right
	 * answer and belongs to the task that owns the credential model, so that the schema
	 * moves once. This is that column. The {@code owner} suffix stays — an agent acts
	 * <em>as</em> someone, and the audit trail should keep saying so — but the review
	 * queue (SH109) selects on this, not on a substring of a display string.</p>
	 */
	@Column(name = "agent_session")
	private String agentSession;

	/**
	 * When a human accepted this change in the review queue (SH109); {@code null} while
	 * it is still pending. Stamped per row rather than per session because a reviewer
	 * who approves nine of ten changes has said something the session-level flag cannot.
	 */
	@Column(name = "reviewed_date")
	private Instant reviewedDate;

	/** Who reviewed it. Only meaningful alongside {@link #reviewedDate}. */
	@Column(name = "reviewed_by")
	private String reviewedBy;

    public void setId(String id) {
		this.id = id;
	}

    public void setOwner(String owner) {
		this.owner = owner;
	}

    public void setAction(String action) {
		this.action = action;
	}

    public void setDate(Instant date) {
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

    public void setAgentSession(String agentSession) {
		this.agentSession = agentSession;
	}

    public void setReviewedDate(Instant reviewedDate) {
		this.reviewedDate = reviewedDate;
	}

    public void setReviewedBy(String reviewedBy) {
		this.reviewedBy = reviewedBy;
	}

	/** @return {@code true} when a human has accepted this change (SH109). */
	public boolean isReviewed() {
		return reviewedDate != null;
	}

}
