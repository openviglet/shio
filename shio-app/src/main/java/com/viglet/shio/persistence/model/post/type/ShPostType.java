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

    /**
     * Whether this type was declared disposable by whoever created it (SH768).
     *
     * <p>A conformance run seeds types named by its own run id and leaves them behind:
     * {@code ShioConfRendermsgmgaup2qjcArticle} and {@code ShioConfRendermsgmgrcoqdq2Article} sat
     * in the model beside {@code Page} and the seven {@code Section*} types with nothing marking
     * them as scratch. The cost is paid on every cold start by every reader — {@code shio context}
     * is the one call P2 promises replaces a session and the model is most of its payload, so an
     * invented type with a field called {@code TEXT} reads as <em>this instance has a convention I
     * should follow</em>.</p>
     *
     * <p><b>Set at creation and never afterwards</b>, which is SH591's guarantee for {@link
     * com.viglet.shio.persistence.model.site.ShSite#isScratch()} and holds for the same reason: a
     * flag anybody could raise later would be a way to make a real post-type sweepable in bulk,
     * which is exactly what the sweep must not become. A convention on the name would be a claim
     * anybody can make about anybody's type after the fact; this one is a claim its creator made
     * before there was anything to lose.</p>
     *
     * <p>{@code Boolean} rather than {@code boolean}: every type created before this column is a
     * type nobody called disposable, and null must read as that rather than as a declaration.</p>
     */
    private Boolean scratch;

    @OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @JsonView({ShJsonView.ShJsonViewPostType.class})
    private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

    private String workflowPublishEntity;

    public ShPostType() {
        this.setObjectType(ShObjectType.POST_TYPE);
    }

    public void setName(String name) {
        this.name = name;
    }

    /** SH768 — null is a type that predates the column, which is a type nobody called disposable. */
    public boolean isScratch() {
        return Boolean.TRUE.equals(this.scratch);
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
