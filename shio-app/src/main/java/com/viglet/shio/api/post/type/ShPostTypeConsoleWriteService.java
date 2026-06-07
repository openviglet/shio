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
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.widget.ShWidget;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.widget.ShWidgetRepository;

/**
 * Write-side for the <em>console</em> post-type API ({@code /api/v2/post/type},
 * id-keyed, entity-shaped) — the editor surface the React console drives. Distinct
 * from the by-name, lossless {@code ShPostTypeAdminService} (SH16, the {@code shio}
 * CLI), but it reuses the same cascade-safety discipline.
 *
 * <p><b>Cascade safety.</b> {@code ShPostTypeAttr} maps {@code shWidget},
 * {@code shPostType} and {@code shParentPostTypeAttr} as {@code @ManyToOne(cascade =
 * ALL)}. A naive orphan-removal of the old attribute set on update would cascade a
 * {@code REMOVE} into the <em>shared</em> widget catalog. We therefore {@link #detach}
 * those associations before clearing, then rebuild the tree fresh from the payload —
 * resolving each widget against the managed catalog by id (falling back to name).</p>
 *
 * @author Alexandre Oliveira
 */
@Service
public class ShPostTypeConsoleWriteService {

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShWidgetRepository shWidgetRepository;

    public ShPostTypeConsoleWriteService(ShPostTypeRepository shPostTypeRepository,
                                         ShWidgetRepository shWidgetRepository) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shWidgetRepository = shWidgetRepository;
    }

    /**
     * List all post-types with their attribute tree initialized so the (LAZY)
     * {@code shPostTypeAttrs} serialize even with open-in-view off.
     */
    @Transactional(readOnly = true)
    public List<ShPostType> list() {
        List<ShPostType> all = shPostTypeRepository.findAll();
        all.forEach(pt -> initialize(pt.getShPostTypeAttrs()));
        return all;
    }

    @Transactional(readOnly = true)
    public Optional<ShPostType> get(String id) {
        return shPostTypeRepository.findById(id).map(pt -> {
            initialize(pt.getShPostTypeAttrs());
            return pt;
        });
    }

    /** Touch the lazy attribute graph (widget + nested fields) inside the transaction. */
    private void initialize(Set<ShPostTypeAttr> attrs) {
        if (attrs == null) {
            return;
        }
        for (ShPostTypeAttr attr : attrs) {
            if (attr.getShWidget() != null) {
                attr.getShWidget().getName();
            }
            initialize(attr.getShPostTypeAttrs());
        }
    }

    @Transactional
    public ShPostType create(ShPostType payload, String owner) {
        ShPostType postType = new ShPostType();
        postType.setName(payload.getName());
        postType.setNamePlural(payload.getNamePlural());
        postType.setTitle(payload.getTitle());
        postType.setDescription(payload.getDescription());
        postType.setWorkflowPublishEntity(payload.getWorkflowPublishEntity());
        postType.setSystem((byte) 0);
        postType.setOwner(owner);
        postType.setDate(Instant.now());
        postType.setShPostTypeAttrs(buildAttrs(payload.getShPostTypeAttrs(), postType, null));
        return shPostTypeRepository.save(postType);
    }

    @Transactional
    public Optional<ShPostType> update(String id, ShPostType payload, String modifier) {
        return shPostTypeRepository.findById(id).map(postType -> {
            if (postType.getSystem() == 1) {
                throw new ShSystemPostTypeException(postType.getName());
            }
            if (payload.getName() != null && !payload.getName().isBlank()) {
                postType.setName(payload.getName());
            }
            postType.setNamePlural(payload.getNamePlural());
            postType.setTitle(payload.getTitle());
            postType.setDescription(payload.getDescription());
            postType.setWorkflowPublishEntity(payload.getWorkflowPublishEntity());
            postType.setModifier(modifier);
            postType.setModifiedDate(new Date());

            // Detach cascade-bearing associations so the orphan removal deletes only
            // the old attributes, never the shared widget catalog.
            flatten(postType.getShPostTypeAttrs()).forEach(ShPostTypeConsoleWriteService::detach);
            postType.getShPostTypeAttrs().clear();
            shPostTypeRepository.saveAndFlush(postType);

            postType.setShPostTypeAttrs(buildAttrs(payload.getShPostTypeAttrs(), postType, null));
            return shPostTypeRepository.save(postType);
        });
    }

    @Transactional
    public boolean delete(String id) {
        return shPostTypeRepository.findById(id).map(postType -> {
            if (postType.getSystem() == 1) {
                throw new ShSystemPostTypeException(postType.getName());
            }
            flatten(postType.getShPostTypeAttrs()).forEach(ShPostTypeConsoleWriteService::detach);
            postType.getShPostTypeAttrs().clear();
            shPostTypeRepository.delete(postType);
            return true;
        }).orElse(false);
    }

    private Set<ShPostTypeAttr> buildAttrs(Set<ShPostTypeAttr> payload, ShPostType postType, ShPostTypeAttr parent) {
        Set<ShPostTypeAttr> attrs = new LinkedHashSet<>();
        if (payload == null) {
            return attrs;
        }
        for (ShPostTypeAttr src : payload) {
            ShPostTypeAttr attr = new ShPostTypeAttr();
            attr.setName(src.getName());
            attr.setLabel(src.getLabel());
            attr.setDescription(src.getDescription());
            attr.setOrdinal(src.getOrdinal());
            attr.setRequired(src.getRequired());
            attr.setIsTitle(src.getIsTitle());
            attr.setIsSummary(src.getIsSummary());
            attr.setWidgetSettings(src.getWidgetSettings());
            attr.setShWidget(resolveWidget(src.getShWidget()));
            attr.setShPostType(postType);
            attr.setShParentPostTypeAttr(parent);
            attr.setShPostTypeAttrs(buildAttrs(src.getShPostTypeAttrs(), postType, attr));
            attrs.add(attr);
        }
        return attrs;
    }

    /** Resolve the posted widget reference to a managed catalog row (by id, then name). */
    private ShWidget resolveWidget(ShWidget posted) {
        if (posted == null) {
            return null;
        }
        ShWidget widget = null;
        if (posted.getId() != null) {
            widget = shWidgetRepository.findById(posted.getId()).orElse(null);
        }
        if (widget == null && posted.getName() != null) {
            widget = shWidgetRepository.findFirstByName(posted.getName());
        }
        if (widget == null) {
            throw new ShUnknownWidgetException(posted.getName() != null ? posted.getName() : posted.getId());
        }
        return widget;
    }

    private static List<ShPostTypeAttr> flatten(Set<ShPostTypeAttr> attrs) {
        if (attrs == null) {
            return List.of();
        }
        return attrs.stream()
                .flatMap(attr -> Stream.concat(Stream.of(attr), flatten(attr.getShPostTypeAttrs()).stream()))
                .toList();
    }

    private static void detach(ShPostTypeAttr attr) {
        attr.setShWidget(null);
        attr.setShParentPostTypeAttr(null);
        attr.setShPostType(null);
    }
}
