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
