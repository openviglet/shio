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

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.viglet.shio.api.ShJsonView;
import com.viglet.shio.object.ShObjectType;
import com.viglet.shio.persistence.model.object.ShObject;
import com.viglet.shio.persistence.model.post.ShPost;
import com.viglet.shio.persistence.model.post.ShPostDraft;
import com.viglet.shio.persistence.model.post.impl.ShPostImpl;
import com.viglet.shio.post.type.ShSystemPostType;

import java.io.Serial;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrimaryKeyJoinColumn;

/**
 * The persistent class for the ShPostType database table.
 *
 * @author Alexandre Oliveira
 */
@Getter
@Setter
@Entity
@NamedQuery(name = "ShPostType.findAll", query = "SELECT s FROM ShPostType s")
@JsonIgnoreProperties({"shPosts", "shPostDrafts", "shPostAttrs", "shPostDraftAttrs", "shPostAttrRefs",
        "shPostDraftAttrRefs", "shGroups", "$$_hibernate_interceptor", "hibernateLazyInitializer"})
@PrimaryKeyJoinColumn(name = "object_id")
public class ShPostType extends ShObject {
    @Serial
    private static final long serialVersionUID = 1L;

    private String description;

    private String name;

    private String namePlural;

    private String title;

    private byte system;

    // bi-directional many-to-one association to ShPost
    @OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({ CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPost> shPosts = new HashSet<>();

    @OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({ CascadeType.ALL })
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ShPostDraft> shPostDrafts = new HashSet<>();

    // bi-directional many-to-one association to ShPostTypeAttr
    @OneToMany(mappedBy = "shPostType", orphanRemoval = true, fetch = FetchType.LAZY)
    @Cascade({CascadeType.ALL})
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

    public void setName(ShSystemPostType shSystemPostType) {
        this.name = shSystemPostType.toString();
    }

    public void setShPosts(Set<ShPost> shPosts) {
        this.shPosts.clear();
        if (shPosts != null) {
            this.shPosts.addAll(shPosts);
        }
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
