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

	/** Declared width of {@link #description} — the limit the writer truncates to (SH544). */
	public static final int DESCRIPTION_MAX_LENGTH = 255;

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

	/**
	 * What {@link #shObject} is — {@code POST} or {@code FOLDER} (SH196), {@code null} on a row
	 * written before the column existed and read as {@code POST} then.
	 *
	 * <p>It is the second dimension of "what happened", and it is stored rather than derived
	 * because the derivation stops working exactly when it matters: a purged folder cannot be
	 * looked up, so its own deletion entry would be reclassified as a post. See
	 * {@link com.viglet.shio.api.history.ShContentSubject}.</p>
	 */
	@Column(name = "subject_kind", length = 16)
	private String subjectKind;

	/**
	 * The human-readable sentence describing the change, composed by
	 * {@code ShContentAuditService.describe()} from a title the caller supplied.
	 *
	 * <p>The width is declared here — redundantly, since {@code VARCHAR(255)} is both the
	 * changelog's type and JPA's default — because SH544 was the width being known to the
	 * schema and to nobody else: five of one site's 198 page titles are longer than 245
	 * characters, and each of them ended a 201-page conversion with a 500. The writer now
	 * truncates against {@link #DESCRIPTION_MAX_LENGTH}, so the constant and the column have
	 * to agree; {@code ShAuditDescriptionLengthTest} asserts they still do, against the
	 * changelog rather than this annotation.</p>
	 */
	@Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
	private String description;

	private String shObject;

	private String shSite;

	/**
	 * The agent run that made this change (SH106), or {@code null} for a human's own edit.
	 *
	 * <p>It is what makes SH109's review queue a query rather than a guess: "show me
	 * everything that run touched" is a {@code where} clause, where before SH106 it was a
	 * {@code LIKE} over a suffix in {@link #owner}. {@code owner} therefore names only the
	 * human whose credential was used — an agent acts <em>as</em> someone, and both halves
	 * of that are recorded, separately.</p>
	 */
	@Column(name = "agent_session_id", length = 64)
	private String agentSessionId;

	/** Declared width of {@link #changedFields} — the limit the writer bounds to (SH989). */
	public static final int CHANGED_FIELDS_MAX_LENGTH = 1024;

	/**
	 * The names of the fields this write changed, as a JSON array (SH989), and never their values.
	 *
	 * <p>{@code null} means not recorded — a create, a delete, or a row older than the column — and
	 * {@code []} means recorded and nothing changed, which is a different statement. Encoded and
	 * bounded by {@code ShChangedFields}; {@code ShChangedFieldsTest} holds this width to the
	 * changelog's, the way SH544's test holds {@link #description}'s.</p>
	 */
	@Column(name = "changed_fields", length = CHANGED_FIELDS_MAX_LENGTH)
	private String changedFields;

	/** The changed field names, already encoded by {@code ShChangedFields.encode} (SH989). */
	public void setChangedFields(String changedFields) {
		this.changedFields = changedFields;
	}

    /** The agent run that made this change (SH106); {@code null} for a human's own edit. */
    public void setAgentSessionId(String agentSessionId) {
		this.agentSessionId = agentSessionId;
	}

    public void setId(String id) {
		this.id = id;
	}

    public void setOwner(String owner) {
		this.owner = owner;
	}

    public void setAction(String action) {
		this.action = action;
	}

    /** What the audited object is (SH196); {@code null} means a post. */
    public void setSubjectKind(String subjectKind) {
		this.subjectKind = subjectKind;
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
	

}
