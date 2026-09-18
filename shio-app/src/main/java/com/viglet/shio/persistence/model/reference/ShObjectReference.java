/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.reference;

import java.io.Serializable;

import org.hibernate.annotations.TenantId;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.shio.persistence.model.post.unified.ShPostState;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * One edge: a post's field points at an object (SH1034).
 *
 * <h2>Why this is not {@code ShReference}</h2>
 *
 * <p>{@link ShReference} stores object-to-object edges and cannot hold these — it joins
 * {@code object_from} and {@code object_to} to {@code ShObject}, and a post is <b>not</b> an
 * {@code ShObject}: the unified model is its own table. {@code ShTemplateGraph} reached the same
 * conclusion from the other side and recorded it ("a post-to-post edge would need a new table").
 * Nothing in main writes {@code ShReference} today.</p>
 *
 * <h2>What it is for, with the number that chose it</h2>
 *
 * <p>SH1033 measured the question this answers. "Where is this used" is
 * {@code data like concat('%', :fileId, '%')} — a leading wildcard, which no B-tree orders — run
 * once per state, so one detail dialog examines every post row in the tenant twice: 1,002, 10,002
 * and 50,002 rows at the three sizes measured. A prototype of this table answered by examining
 * <b>four</b> at fifty thousand, and its maintenance measured three statements per write,
 * identical at fifty thousand rows and at zero. Two curves rather than two numbers, which is what
 * chose the index over a cached count — a count makes one dialog cheap and leaves the inverse
 * question ("which assets does nothing reference") at one full scan per asset.</p>
 *
 * <h2>{@code state} is part of the edge, not a detail</h2>
 *
 * <p>A post's key is {@code (id, state)}, so a draft and a published row are two documents that
 * may reference different assets — that is what a draft is for. An index keyed on the id alone
 * would answer with the union and could never say which. It also makes the maintenance exact:
 * publishing rewrites one state's rows and leaves the other's alone.</p>
 *
 * <p>No foreign keys, for the reason {@code sh_note} and {@code sh_review_decision} give: a post's
 * key is composite, so an FK would have to name a state, and the row it named is the one
 * publishing deletes.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Entity
@Table(name = "sh_object_reference",
        indexes = {
                @Index(name = "IDX_sh_object_reference_to", columnList = "to_object_id, from_state"),
                @Index(name = "IDX_sh_object_reference_from",
                        columnList = "from_post_id, from_state") })
public class ShObjectReference implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    /** SH22 — tenant discriminator, lowercase as every content table carries it. */
    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    @Column(name = "from_post_id", length = 36, nullable = false)
    private String fromPostId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 16, nullable = false)
    private ShPostState fromState;

    @Column(name = "to_object_id", length = 36, nullable = false)
    private String toObjectId;

    /**
     * The post-type attribute whose value this edge came from.
     *
     * <p>What makes the index more accurate than the substring it replaces: the walk reads the
     * fields a type <em>declares</em> as {@code File} or {@code Content Select}, so an edge is a
     * modelled reference rather than a UUID that happens to appear in a body — and a finding can
     * name the field to fix.</p>
     */
    @Column(name = "field_name", length = 255, nullable = false)
    private String fieldName;

    public ShObjectReference() {
        // JPA
    }

    public ShObjectReference(String fromPostId, ShPostState fromState, String toObjectId,
            String fieldName) {
        this.fromPostId = fromPostId;
        this.fromState = fromState;
        this.toObjectId = toObjectId;
        this.fieldName = fieldName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getFromPostId() {
        return fromPostId;
    }

    public void setFromPostId(String fromPostId) {
        this.fromPostId = fromPostId;
    }

    public ShPostState getFromState() {
        return fromState;
    }

    public void setFromState(ShPostState fromState) {
        this.fromState = fromState;
    }

    public String getToObjectId() {
        return toObjectId;
    }

    public void setToObjectId(String toObjectId) {
        this.toObjectId = toObjectId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}
