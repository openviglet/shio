/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;

/**
 * Read/write surface for post-types as code (SH16) — the engine behind the
 * {@code shio} CLI's {@code pull}/{@code push}. Unlike the read-only, lossy CDA
 * schema (SH11), this exposes (and re-applies) the full authoring model:
 * container widgets ({@code Tab}), verbatim {@code widgetSettings} and the
 * Relator field tree.
 *
 * <p><b>Cascade safety.</b> {@code ShPostTypeAttr} maps {@code shWidget},
 * {@code shPostType} and {@code shParentPostTypeAttr} as {@code @ManyToOne(cascade
 * = ALL)} — so a naive orphan-removal of an attribute on update would cascade a
 * {@code REMOVE} into the <em>shared</em> widget catalog (and even the post-type
 * itself). Before replacing the attribute set we therefore detach those
 * associations ({@link #detach}) so only the attributes are deleted.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.2
 */
@Service
public class ShPostTypeAdminService {

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShWidgetRepository shWidgetRepository;
    private final ShPostTypeAdminMapper mapper;

    public ShPostTypeAdminService(ShPostTypeRepository shPostTypeRepository,
                                  ShWidgetRepository shWidgetRepository,
                                  ShPostTypeAdminMapper mapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ShPostTypeDef> list() {
        return shPostTypeRepository.findAll().stream()
                .sorted(Comparator.comparing(ShPostType::getName, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(mapper::toDef)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ShPostTypeDef> get(String name) {
        return Optional.ofNullable(shPostTypeRepository.findByName(name)).map(mapper::toDef);
    }

    /**
     * Create or fully replace the post-type named {@code name} from {@code def}
     * (idempotent by name). Rejects writes to a system post-type.
     */
    @Transactional
    public ShPostTypeDef upsert(String name, ShPostTypeDef def) {
        ShPostType existing = shPostTypeRepository.findByName(name);
        if (existing != null && existing.getSystem() == 1) {
            throw new ShSystemPostTypeException(name);
        }

        ShPostType postType = existing != null ? existing : newPostType(name);
        postType.setTitle(def.title());
        postType.setNamePlural(def.namePlural());
        postType.setDescription(def.description());
        postType.setWorkflowPublishEntity(def.workflowPublishEntity());

        if (existing != null) {
            // Detach cascade-bearing associations so the orphan removal below
            // deletes only the old attributes, never the shared widget catalog.
            flatten(postType.getShPostTypeAttrs()).forEach(this::detach);
            postType.getShPostTypeAttrs().clear();
            shPostTypeRepository.saveAndFlush(postType);
        }

        postType.setShPostTypeAttrs(buildAttrs(def.fields(), postType, null));
        return mapper.toDef(shPostTypeRepository.save(postType));
    }

    /** Delete a non-system post-type. Returns {@code false} if it does not exist. */
    @Transactional
    public boolean delete(String name) {
        ShPostType existing = shPostTypeRepository.findByName(name);
        if (existing == null) {
            return false;
        }
        if (existing.getSystem() == 1) {
            throw new ShSystemPostTypeException(name);
        }
        flatten(existing.getShPostTypeAttrs()).forEach(this::detach);
        existing.getShPostTypeAttrs().clear();
        shPostTypeRepository.delete(existing);
        return true;
    }

    private ShPostType newPostType(String name) {
        ShPostType postType = new ShPostType();
        postType.setName(name);
        postType.setSystem((byte) 0);
        postType.setDate(Instant.now());
        postType.setOwner(currentUser());
        return postType;
    }

    private Set<ShPostTypeAttr> buildAttrs(List<ShPostTypeAttrDef> defs, ShPostType postType,
                                           ShPostTypeAttr parent) {
        Set<ShPostTypeAttr> attrs = new LinkedHashSet<>();
        if (defs == null) {
            return attrs;
        }
        for (ShPostTypeAttrDef def : defs) {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(def.name());
            attr.setLabel(def.label());
            attr.setDescription(def.description());
            attr.setOrdinal(def.ordinal());
            attr.setRequired((byte) (def.required() ? 1 : 0));
            attr.setIsTitle((byte) (def.isTitle() ? 1 : 0));
            attr.setIsSummary((byte) (def.isSummary() ? 1 : 0));
            attr.setWidgetSettings(def.widgetSettings());
            if (def.widget() != null) {
                ShWidget widget = shWidgetRepository.findFirstByName(def.widget());
                if (widget == null) {
                    throw new ShUnknownWidgetException(def.widget());
                }
                attr.setShWidget(widget);
            }
            attr.setShPostType(postType);
            attr.setShParentPostTypeAttr(parent);
            attr.setShPostTypeAttrs(buildAttrs(def.fields(), postType, attr));
            attrs.add(attr);
        }
        return attrs;
    }

    /** Depth-first flatten of an attribute tree (top-level + every nested child). */
    private List<ShPostTypeAttr> flatten(Set<ShPostTypeAttr> attrs) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .flatMap(attr -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(attr),
                        flatten(attr.getShPostTypeAttrs()).stream()))
                .toList();
    }

    private void detach(ShPostTypeAttr attr) {
        attr.setShWidget(null);
        attr.setShParentPostTypeAttr(null);
        attr.setShPostType(null);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "admin";
    }
}
