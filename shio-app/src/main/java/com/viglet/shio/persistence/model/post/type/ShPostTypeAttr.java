/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.type;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.shio.persistence.model.widget.ShWidget;

import java.util.HashSet;
import java.util.Set;

@Entity
@NamedQuery(name = "ShPostTypeAttr.findAll", query = "SELECT s FROM ShPostTypeAttr s")
@JsonIgnoreProperties({"$$_hibernate_interceptor", "hibernateLazyInitializer"})
@Setter
@Getter
public class ShPostTypeAttr implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @TenantId
    @Column(name = "tenant_id", updatable = false)
    private String tenantId;

    private String description;
    private byte isSummary;
    private byte isTitle;
    private String label;
    private String name;
    private int ordinal;
    private byte required;

    /**
     * Whether this field is sent to Turing as a field of its own rather than folded into
     * {@code text} (SH914).
     *
     * <p>A {@link Boolean} and nullable forever: absent is what every attribute written before this
     * carries, and it reads as {@code false}, so no existing document changes shape. The mapper
     * folds an undeclared field exactly as SH84 did.</p>
     */
    @Column(name = "indexed")
    private Boolean indexed;

    /**
     * The Turing field this value lands in, or {@code null} for the attribute's own {@link #name}.
     *
     * <p>It exists for the instance whose Turing already has a schema and cannot rename its fields
     * to match Shio's — not as something an author should normally have to spell.</p>
     */
    @Column(name = "turing_field")
    private String turingField;

    /**
     * A {@code TurSEFieldType} name — {@code INT}, {@code LONG}, {@code STRING}, {@code TEXT},
     * {@code DATE}, {@code BOOL}, {@code FLOAT}, {@code DOUBLE}.
     *
     * <p>Stored as the enum's <b>name</b> and not its {@code id()}: that id is Turing's wire number,
     * and persisting it would silently re-point every declaration on this instance if the enum ever
     * gained a member in the middle.</p>
     */
    @Column(name = "turing_type", length = 32)
    private String turingType;

    @OneToMany(mappedBy = "shParentPostTypeAttr", orphanRemoval = true, fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    @JoinColumn(name = "postType_id")
    @JsonIgnore
    private ShPostType shPostType;

    @ManyToOne(cascade = jakarta.persistence.CascadeType.ALL)
    @JoinColumn(name = "widget_id")
    @Fetch(org.hibernate.annotations.FetchMode.JOIN)
    private ShWidget shWidget;
    @Column(name = "widget_settings", length = 5 * 1024 * 1024)
    private String widgetSettings;

    @ManyToOne(fetch = FetchType.LAZY, cascade = jakarta.persistence.CascadeType.ALL)
    @JoinColumn(name = "parent_relator_id")
    @JsonIgnore
    private ShPostTypeAttr shParentPostTypeAttr;

    @Transient
    private String shPostTypeName;

    public ShPostTypeAttr() {
        super();
    }

    public void setShPostTypeAttrs(Set<ShPostTypeAttr> shPostTypeAttrs) {
        this.shPostTypeAttrs.clear();
        if (shPostTypeAttrs != null) {
            this.shPostTypeAttrs.addAll(shPostTypeAttrs);
        }
    }
}
