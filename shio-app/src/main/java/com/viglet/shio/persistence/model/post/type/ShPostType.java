/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.persistence.model.post.type;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.CascadeType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.post.type.ShSystemPostType;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;

@Getter
@Setter
@Entity
@NamedQuery(name = "ShPostType.findAll", query = "SELECT s FROM ShPostType s")
@JsonIgnoreProperties({"shGroups", "$$_hibernate_interceptor", "hibernateLazyInitializer"})
@PrimaryKeyJoinColumn(name = "object_id")
public class ShPostType extends ShObject {
    @Serial
    private static final long serialVersionUID = 1L;

    private String description;
    private String name;
    private String namePlural;
    private String title;
    private byte system;

    /**
     * SH66 — "managed by code" lock. Set true by the SH16 admin-API {@code upsert}
     * (the {@code shio} CLI {@code push} path), false for GUI-created/console types.
     * When true the React console renders the post-type read-only so the DSL↔GUI
     * boundary is explicit instead of a silent last-writer-wins race. Distinct from
     * {@link #system} (backend-owned platform types).
     */
    private boolean managed;

    @OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonView({ShJsonView.ShJsonViewPostType.class})
    private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

    private String workflowPublishEntity;

    @Lob
    @Column(name = "schema_json")
    private String schemaJson;

    public ShPostType() {
        this.setObjectType(ShObjectType.POST_TYPE);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setName(ShSystemPostType shSystemPostType) {
        this.name = shSystemPostType.toString();
    }

    public void setShPostTypeAttrs(Set<ShPostTypeAttr> shPostTypeAttrs) {
        this.shPostTypeAttrs.clear();
        if (shPostTypeAttrs != null) {
            this.shPostTypeAttrs.addAll(shPostTypeAttrs);
        }
    }

    @Override
    public String getObjectType() {
        return ShObjectType.POST_TYPE;
    }

    @Override
    public void setObjectType(String objectType) {
        super.setObjectType(ShObjectType.POST_TYPE);
    }
}
