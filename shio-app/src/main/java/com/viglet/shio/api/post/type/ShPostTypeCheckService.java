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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.post.ShFieldRules;
import com.viglet.shio.api.post.ShRequiredFields;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * SH1018 — what a post-type's own content does not satisfy, counted.
 *
 * <h2>The measurement first, and why it is a deliverable rather than a step</h2>
 *
 * <p>SH1010 made constraints refuse a bad write and said nothing about what was already stored, so
 * a type given a required field has an instance full of content that violates it — discovered one
 * publish at a time. The hole is older than constraints: a post's data is a JSON object keyed by
 * field name, and renaming or removing a field leaves values under a key nothing declares, which
 * {@code shio verify} never reported.</p>
 *
 * <p>SH322's rule is that the grep is the deliverable. Here that means the <b>count</b> is: a
 * migration argued for by "some posts probably violate this" is a migration nobody can size, review
 * or verify afterwards. This answers how many rows break each rule and how many carry keys the
 * model does not declare, and that number is what a migration is then written against.</p>
 *
 * <h2>It reads both states</h2>
 *
 * <p>A draft that violates a constraint is a publish that will be refused, and a published row that
 * violates one is a page that is already wrong. Counting only drafts would report the first and
 * hide the second, which is the half that is actually live.</p>
 */
@Service
public class ShPostTypeCheckService {

    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShPostTypeCheckService(ShPostTypeRepository shPostTypeRepository,
                                  ShPostUnifiedRepository shPostUnifiedRepository,
                                  ObjectMapper objectMapper) {
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param postType  the type checked
     * @param posts     how many rows were read, across both states
     * @param violating how many of those break at least one rule
     * @param unreadable how many hold a document this could not parse, and so could not judge
     * @param rules     one row per rule that anything breaks, worst first
     * @param undeclared one row per key stored under this type that it does not declare
     */
    public record Report(String postType, int posts, int violating, int unreadable,
                         List<RuleCount> rules, List<FieldCount> undeclared) {
    }

    /** @param field the field, {@code rule} the constraint, {@code posts} how many break it */
    public record RuleCount(String field, String rule, String says, int posts) {
    }

    /** A key stored under this type that the type does not declare, and how many rows carry it. */
    public record FieldCount(String field, int posts) {
    }

    @Transactional(readOnly = true)
    public Report check(String typeName) {
        ShPostType postType = shPostTypeRepository.findByName(typeName);
        if (postType == null) {
            throw new ShPostTypeAPI.UnknownPostTypeException(typeName, declaredNames());
        }
        Set<String> declared = declaredNames(postType);

        // Keyed by field + rule, so "title is too short" is one row with a count rather than one
        // row per post — the report is a shape to act on, not a log to read.
        Map<String, RuleCount> byRule = new LinkedHashMap<>();
        Map<String, Integer> undeclared = new LinkedHashMap<>();
        int posts = 0;
        int violating = 0;
        int unreadable = 0;

        for (ShPostUnified post : rowsOf(postType)) {
            posts++;
            JsonNode data = dataOf(post);
            if (data == null || !data.isObject()) {
                // Counted rather than skipped in silence. A row this cannot read is a row it cannot
                // judge, and a report that quietly left it out would understate the very number the
                // task exists to produce — which is the failure this whole check is about, arriving
                // by the check.
                unreadable++;
                continue;
            }
            // Two rule sets, because the model has two. `ShFieldRules` covers what a field's
            // constraints declare and `ShRequiredFields` covers `required`, which is a flag on the
            // attribute rather than a constraint — and the required field is the design's own
            // motivating example, so a report that ran only the first would miss the case it was
            // written for. (It did, until a test said so.)
            List<ShFieldRules.Violation> found = new ArrayList<>(
                    ShFieldRules.violations(postType, data, objectMapper));
            for (String empty : ShRequiredFields.missing(postType, data)) {
                found.add(new ShFieldRules.Violation(empty, "required",
                        "This field is required and this post leaves it empty.",
                        "Fill it in, or drop `required` from the field."));
            }
            if (!found.isEmpty()) {
                violating++;
            }
            for (ShFieldRules.Violation violation : found) {
                String key = violation.field() + "\u0000" + violation.rule();
                RuleCount current = byRule.get(key);
                byRule.put(key, current == null
                        ? new RuleCount(violation.field(), violation.rule(), violation.says(), 1)
                        : new RuleCount(current.field(), current.rule(), current.says(),
                                current.posts() + 1));
            }
            for (String name : data.propertyNames()) {
                if (!declared.contains(name)) {
                    undeclared.merge(name, 1, Integer::sum);
                }
            }
        }

        List<RuleCount> rules = new ArrayList<>(byRule.values());
        // Worst first, then by field, so two runs over the same content print the same bytes (P5)
        // and the row that matters most is the one a reader sees without scrolling.
        rules.sort((a, b) -> b.posts() != a.posts() ? Integer.compare(b.posts(), a.posts())
                : (a.field() + a.rule()).compareTo(b.field() + b.rule()));

        List<FieldCount> orphans = undeclared.entrySet().stream()
                .map(entry -> new FieldCount(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> b.posts() != a.posts() ? Integer.compare(b.posts(), a.posts())
                        : a.field().compareTo(b.field()))
                .toList();

        return new Report(postType.getName(), posts, violating, unreadable, rules, orphans);
    }

    /**
     * Both states, in two reads.
     *
     * <p>By post-type <b>id</b> and never by name: {@code post_type_id} is a column on this table,
     * while navigating to {@code shPostType.name} joins the JOINED {@code ShObject} root and dies at
     * query-prepare time — the invariant agents.md names first, and the repository says so on every
     * finder here.</p>
     */
    private List<ShPostUnified> rowsOf(ShPostType postType) {
        List<ShPostUnified> rows = new ArrayList<>(
                shPostUnifiedRepository.findByShPostType_IdAndState(postType.getId(),
                        ShPostState.DRAFT));
        rows.addAll(shPostUnifiedRepository.findByShPostType_IdAndState(postType.getId(),
                ShPostState.PUBLISHED));
        return rows;
    }

    private JsonNode dataOf(ShPostUnified post) {
        try {
            return post.getData() == null ? null : objectMapper.readTree(post.getData());
        } catch (RuntimeException e) {
            // A document that will not parse is a finding of a different kind, and one this report
            // does not claim to make: it counts rules, and a row with no readable document breaks
            // none of them in a way that could be fixed by a migration.
            return null;
        }
    }

    private static Set<String> declaredNames(ShPostType postType) {
        Set<String> names = new LinkedHashSet<>();
        collect(postType.getShPostTypeAttrs(), names);
        return names;
    }

    private static void collect(Set<ShPostTypeAttr> attrs, Set<String> into) {
        if (attrs == null) {
            return;
        }
        for (ShPostTypeAttr attr : attrs) {
            if (attr.getName() != null && !attr.getName().isBlank()) {
                into.add(attr.getName());
            }
            collect(attr.getShPostTypeAttrs(), into);
        }
    }

    private List<String> declaredNames() {
        return shPostTypeRepository.findAll().stream()
                .map(ShPostType::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }
}
