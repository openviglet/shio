/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post.type;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.type.dto.ShModelProposal;
import com.viglet.shio.api.post.type.dto.ShPostTypeDef;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.model.site.ShSite;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;
import com.viglet.shio.persistence.repository.site.ShSiteRepository;
import com.viglet.shio.render.ShPageRenderService;
import com.viglet.shio.render.ShPageRenderService.ShNamedTemplate;
import com.viglet.shio.render.ShTemplateFieldScanner;
import com.viglet.shio.render.ShTemplateFieldScanner.ShHelperShapes;
import com.viglet.shio.render.ShTemplateFieldScanner.ShTemplateReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * What a model change would cost, before it is made (SH523).
 *
 * <h2>The asymmetry this closes</h2>
 *
 * <p>Modelling is the least agent-native thing in the product, and the contrast is inside it. For
 * <b>content</b>, SH379 gives a proposal: what would change, what it costs, accept it an entry at a
 * time. For the <b>model</b> there was no such artefact — you write a post-type, push it, and find
 * what broke afterwards.</p>
 *
 * <h2>Why a proposal rather than a lint</h2>
 *
 * <p>Because a model change is a <b>decision with alternatives</b>. Making a field required is
 * either a data cleanup or a mistake, and only the count of violating posts tells them apart. A
 * refusal cannot say that; a proposal can. It is also what makes modelling incremental: today the
 * safe move is to declare a field optional and never tighten it, because tightening is the change
 * nobody can see the cost of.</p>
 *
 * <h2>The four questions, and which need a proposal at all</h2>
 *
 * <p>Two are about a <em>change</em> and cannot be asked without one: which posts would violate a
 * field about to become required, and which templates read a field about to be removed. Two are
 * about the model <em>as it stands</em> and are true with or without a proposed change: which
 * declared fields nothing reads, and which stored keys no type declares. The last is not
 * hypothetical — a {@code File} post stores {@code SHA256}, {@code SIZE}, {@code WIDTH} and
 * {@code HEIGHT} against a type declaring one field, and it took a lint firing 66 times to surface
 * it (SH474).</p>
 *
 * <p>All four are read-only and this service writes nothing, which is what lets the answer ride a
 * {@code dryRun} on the write an agent was going to make anyway rather than needing a surface of
 * its own (P10).</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShModelProposalService {

    /** How many offending posts a finding names before it stops listing and only counts. */
    private static final int SAMPLE = 10;

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ShSiteRepository shSiteRepository;
    private final ShPageRenderService renderService;
    private final ObjectMapper objectMapper;

    public ShModelProposalService(ShPostTypeRepository shPostTypeRepository,
                                  ShPostUnifiedRepository shPostUnifiedRepository,
                                  ShSiteRepository shSiteRepository,
                                  ShPageRenderService renderService,
                                  ObjectMapper objectMapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.shSiteRepository = shSiteRepository;
        this.renderService = renderService;
        this.objectMapper = objectMapper;
    }

    /**
     * What applying {@code proposed} to the type called {@code name} would cost.
     *
     * @param name     the post-type the proposal is about
     * @param proposed the definition being considered, or {@code null} to ask only the two
     *                 questions that are about the model as it stands
     */
    public ShModelProposal propose(String name, ShPostTypeDef proposed) {
        var existing = shPostTypeRepository.findByName(name);
        List<ShPostUnified> posts = existing == null ? List.of()
                : shPostUnifiedRepository.findByShPostType_IdAndState(existing.getId(),
                        ShPostState.PUBLISHED);
        Set<String> declaredNow = declared(existing);
        Set<String> declaredAfter = proposed == null ? declaredNow : names(proposed);
        Set<String> read = readBy(name);

        List<ShModelProposal.Finding> findings = new ArrayList<>();
        tightening(proposed, declaredNow, posts, findings);
        removals(declaredNow, declaredAfter, read, findings);
        unread(declaredAfter, read, findings);
        undeclared(declaredAfter, posts, findings);

        return new ShModelProposal(name, existing != null, posts.size(), List.copyOf(findings));
    }

    /**
     * Posts that would violate a field this proposal makes required (SH523).
     *
     * <p>The count is the whole point: it is what tells a data cleanup from a mistake, and it is
     * the question that made "declare it optional and never tighten it" the safe move. Counted over
     * {@code PUBLISHED} rows, because a draft that is missing a field is a draft somebody is still
     * writing.</p>
     */
    private void tightening(ShPostTypeDef proposed, Set<String> declaredNow,
                            List<ShPostUnified> posts, List<ShModelProposal.Finding> findings) {
        if (proposed == null || proposed.fields() == null) {
            return;
        }
        for (ShPostTypeAttrDef field : proposed.fields()) {
            if (!isRequired(field) || !declaredNow.contains(field.name())) {
                // A field that is new *and* required is a different finding: nothing violates it
                // yet because nothing has it, and every existing post would. Reported by the
                // undeclared/unread pair rather than here, which is about *tightening*.
                continue;
            }
            List<String> violating = posts.stream()
                    .filter(post -> blank(dataOf(post).get(field.name())))
                    .map(ShPostUnified::getId)
                    .toList();
            if (!violating.isEmpty()) {
                findings.add(new ShModelProposal.Finding("required-field-violated", field.name(),
                        violating.size(),
                        violating.stream().limit(SAMPLE).toList(),
                        violating.size() + " published post(s) have no '" + field.name()
                                + "', so making it required describes content that does not exist. "
                                + "Fill them first, or leave the field optional."));
            }
        }
    }

    /** Fields this proposal drops that a template still interpolates — a page that renders empty. */
    private void removals(Set<String> declaredNow, Set<String> declaredAfter, Set<String> read,
                          List<ShModelProposal.Finding> findings) {
        for (String field : declaredNow) {
            if (!declaredAfter.contains(field) && read.contains(field)) {
                findings.add(new ShModelProposal.Finding("removed-field-still-read", field, 1,
                        List.of(),
                        "A template reads '" + field + "' and this proposal removes it, so every "
                                + "page rendering through that template interpolates nothing. "
                                + "Remove the interpolation first, or keep the field."));
            }
        }
    }

    /** Declared and never interpolated — cheap to keep, but nobody can tell it is unused. */
    private void unread(Set<String> declaredAfter, Set<String> read,
                        List<ShModelProposal.Finding> findings) {
        List<String> unread = declaredAfter.stream().filter(field -> !read.contains(field)).toList();
        if (!unread.isEmpty()) {
            findings.add(new ShModelProposal.Finding("declared-field-unread", null, unread.size(),
                    unread.stream().limit(SAMPLE).toList(),
                    unread.size() + " declared field(s) no template reads. Not a defect — a field "
                            + "can be delivered over the CDA — but it is the list to check before "
                            + "adding another."));
        }
    }

    /**
     * Keys the content stores that no type declares (SH474's defect, asked as a question).
     *
     * <p>A {@code File} post stores {@code SHA256}, {@code SIZE}, {@code WIDTH} and {@code HEIGHT}
     * against a type declaring one field. That took a lint firing 66 times to surface; here it is a
     * line in a proposal, before the next type is written the same way.</p>
     */
    private void undeclared(Set<String> declaredAfter, List<ShPostUnified> posts,
                            List<ShModelProposal.Finding> findings) {
        Set<String> stored = new TreeSet<>();
        for (ShPostUnified post : posts) {
            dataOf(post).propertyNames().forEach(stored::add);
        }
        List<String> undeclared = stored.stream()
                .filter(key -> !declaredAfter.contains(key))
                .toList();
        if (!undeclared.isEmpty()) {
            findings.add(new ShModelProposal.Finding("stored-key-undeclared", null,
                    undeclared.size(), undeclared.stream().limit(SAMPLE).toList(),
                    undeclared.size() + " key(s) the content stores are declared by no field, so "
                            + "nothing validates them and the console cannot edit them."));
        }
    }

    /** Every field name any template bound to this type interpolates, across every site. */
    private Set<String> readBy(String typeName) {
        Set<String> read = new LinkedHashSet<>();
        for (ShSite site : shSiteRepository.findAll()) {
            if (!renderService.layoutBindings(site).containsKey(typeName)) {
                continue;
            }
            renderService.templatesFor(site, typeName).ifPresent(templates -> {
                for (ShNamedTemplate template : templates.all()) {
                    // The same scanner the SH251 lint reads templates with, so "read" means here
                    // exactly what it means there — two answers to "does a template use this
                    // field" is one of them being wrong (SH245).
                    for (ShTemplateReference reference
                            : ShTemplateFieldScanner.referencesIn(template.html(),
                                    ShHelperShapes.none())) {
                        read.add(reference.name());
                    }
                }
            });
        }
        return read;
    }

    private Set<String> declared(com.viglet.shio.persistence.model.post.type.ShPostType type) {
        Set<String> names = new TreeSet<>();
        if (type != null && type.getShPostTypeAttrs() != null) {
            type.getShPostTypeAttrs().forEach(attr -> names.add(attr.getName()));
        }
        return names;
    }

    private static Set<String> names(ShPostTypeDef def) {
        Set<String> names = new TreeSet<>();
        if (def.fields() != null) {
            def.fields().forEach(field -> names.add(field.name()));
        }
        return names;
    }

    private static boolean isRequired(ShPostTypeAttrDef field) {
        return field.required();
    }

    private JsonNode dataOf(ShPostUnified post) {
        String json = post.getData();
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (RuntimeException e) {
            // Unparseable `data` is its own defect and `verify` reports it. Treating it as empty
            // here would count the post as violating every field, which would drown the finding
            // this proposal is actually about.
            return objectMapper.createObjectNode();
        }
    }

    private static boolean blank(JsonNode value) {
        return value == null || value.isNull()
                || (value.isString() && value.asString().isBlank());
    }

    /** The proposal for a type nobody has written yet — every field is new, so nothing is at risk. */
    public Optional<ShModelProposal> forNewType(String name, ShPostTypeDef proposed) {
        return shPostTypeRepository.findByName(name) == null
                ? Optional.of(propose(name, proposed)) : Optional.empty();
    }
}
