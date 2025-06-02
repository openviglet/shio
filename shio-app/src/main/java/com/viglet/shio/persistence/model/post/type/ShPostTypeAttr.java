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
package com.viglet.shio.persistence.model.post.type;

import java.io.Serial;
import java.io.Serializable;

import jakarta.persistence.*;

import jakarta.persistence.Entity;
import jakarta.persistence.NamedQuery;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.persistence.model.post.ShPostAttr;
import com.viglet.shio.persistence.model.post.ShPostDraftAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import org.hibernate.annotations.CascadeType;

import java.util.HashSet;
import java.util.Set;

/**
 * The persistent class for the ShPostTypeAttr database table.
 *
 * @author Alexandre Oliveira
 */
@Entity
@NamedQuery(name = "ShPostTypeAttr.findAll", query = "SELECT s FROM ShPostTypeAttr s")
@JsonIgnoreProperties({"shPostAttrs", "shPostDraftAttrs", "$$_hibernate_interceptor", "hibernateLazyInitializer"})
// Removed shPostType ignore, because it is used in JSON from new Post
@Setter
@Getter
public class ShPostTypeAttr implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;


    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    private String description;
    private byte isSummary;
    private byte isTitle;
    private String label;
    private String name;
    private int ordinal;
    private byte required;

    @OneToMany(mappedBy = "shParentPostTypeAttr", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPostTypeAttr> shPostTypeAttrs = new HashSet<>();

    @OneToMany(mappedBy = "shPostTypeAttr", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPostAttr> shPostAttrs = new HashSet<>();

    @OneToMany(mappedBy = "shPostTypeAttr", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPostDraftAttr> shPostDraftAttrs = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
    @JoinColumn(name = "postType_id")
    @JsonView({ShJsonView.ShJsonViewPostTypeAttr.class})
    private ShPostType shPostType;

    // Remove LAZY because it have few shWidgets
    @ManyToOne
    @Cascade({CascadeType.ALL})
    @JoinColumn(name = "widget_id")
    @Fetch(org.hibernate.annotations.FetchMode.JOIN)
    private ShWidget shWidget;
    @Column(name = "widget_settings", length = 5 * 1024 * 1024) // 5Mb
    private String widgetSettings;

    @ManyToOne(fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
    @JoinColumn(name = "parent_relator_id")
    @JsonView({ShJsonView.ShJsonViewPostTypeAttr.class})
    private ShPostTypeAttr shParentPostTypeAttr;

    @Transient
    private String shPostTypeName;

    public ShPostTypeAttr() {
        super();
    }


    public void setShPostAttrs(Set<ShPostAttr> shPostAttrs) {
        this.shPostAttrs.clear();
        if (shPostAttrs != null) {
            this.shPostAttrs.addAll(shPostAttrs);
        }
    }

    public void setShPostTypeAttrs(Set<ShPostTypeAttr> shPostTypeAttrs) {
        this.shPostTypeAttrs.clear();
        if (shPostTypeAttrs != null) {
            this.shPostTypeAttrs.addAll(shPostTypeAttrs);
        }
    }
}
