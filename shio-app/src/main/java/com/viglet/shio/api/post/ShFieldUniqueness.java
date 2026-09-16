/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The one constraint that is answered by reading other posts (SH1010, second part).
 *
 * <h2>Why it is not in {@link ShFieldRules}</h2>
 *
 * <p>Every other kind is decided by looking at the value in front of you, which is what lets that
 * evaluator be a pure function over a document. This one asks a question about the rest of the site,
 * so it needs a repository and a transaction — and keeping it separate is what stops the cheap
 * evaluator from quietly acquiring a database round trip that every caller then pays.</p>
 *
 * <h2>The scope is the post-type, and the cost is a scan</h2>
 *
 * <p>"Unique" means unique among posts of the same type: a product code and a campaign code are not
 * competing for one namespace just because both are called {@code code}. There is no index on a JSON
 * field and no portable way to ask a database for one, so the check reads the type's authoring rows
 * and compares parsed values. That is <b>one query per publish of a post whose type declares a
 * unique field</b>, and none at all for every other publish in the product — which is the shape that
 * makes it affordable: almost no type declares one.</p>
 *
 * <p>Compared case-insensitively and trimmed. {@code ACME-1} and {@code acme-1} being two posts is
 * the failure a uniqueness rule is added to prevent, and a reader who typed a trailing space did not
 * mean to create a second code.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Service
@Transactional(readOnly = true)
public class ShFieldUniqueness {

    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShFieldUniqueness(ShPostUnifiedRepository shPostUnifiedRepository,
                             ObjectMapper objectMapper) {
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Every unique field of {@code post} that another post of its type already holds.
     *
     * @param post the row being published — excluded from its own comparison by id, so re-publishing
     *             an unchanged page is never a collision with itself
     */
    public List<ShFieldRules.Violation> collisions(ShPostUnified post, JsonNode data) {
        ShPostType postType = post == null ? null : post.getShPostType();
        if (postType == null || postType.getShPostTypeAttrs() == null || data == null) {
            return List.of();
        }
        List<ShPostTypeAttr> unique = postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .filter(attr -> ShFieldRules.of(attr, objectMapper).declaresUnique())
                .toList();
        if (unique.isEmpty()) {
            // The ordinary case, and the reason this is affordable: no query at all unless a type
            // declares one.
            return List.of();
        }

        List<ShPostUnified> siblings = shPostUnifiedRepository.findByShPostType_IdAndState(
                postType.getId(), ShPostState.DRAFT);
        List<ShFieldRules.Violation> found = new ArrayList<>();
        for (ShPostTypeAttr attr : unique) {
            String field = attr.getName();
            JsonNode value = field == null ? null : data.get(field);
            if (field == null || ShRequiredFields.isEmpty(value)) {
                // Absence is `required`'s question here too: two pages with no code are not two
                // pages with the same code.
                continue;
            }
            String mine = normalized(value);
            siblings.stream()
                    .filter(sibling -> !sibling.getId().equals(post.getId()))
                    .filter(sibling -> mine.equals(valueOf(sibling, field)))
                    .findFirst()
                    .ifPresent(clash -> found.add(new ShFieldRules.Violation(field, "not-unique",
                            "'" + field + "' is '" + value.asString() + "', which "
                                    + named(clash) + " already holds.",
                            "Use a value no other " + postType.getName() + " holds, or drop unique "
                                    + "from the post-type if two may share one.")));
        }
        return List.copyOf(found);
    }

    private String valueOf(ShPostUnified sibling, String field) {
        try {
            JsonNode data = objectMapper.readTree(
                    sibling.getData() == null ? "{}" : sibling.getData());
            JsonNode value = data.get(field);
            return ShRequiredFields.isEmpty(value) ? null : normalized(value);
        } catch (JacksonException unreadable) {
            // silent-catch: a sibling whose data cannot be parsed cannot be compared, and calling
            // that a collision would refuse a publish because of a defect in a different post.
            // Verify reports the unparseable row where it belongs — on that row.
            return null;
        }
    }

    /** Trimmed and folded: {@code ACME-1} and {@code acme-1} are the collision, not two codes. */
    private static String normalized(JsonNode value) {
        String text = value.isString() ? value.asString() : value.toString();
        return text.trim().toLowerCase(Locale.ROOT);
    }

    /** What to call the other post in the refusal: what a person would call it, never a uuid. */
    private static String named(ShPostUnified clash) {
        return clash.getTitle() != null && !clash.getTitle().isBlank()
                ? "\"" + clash.getTitle() + "\""
                : "another post";
    }

    /**
     * Fills in what the model says a missing field defaults to (SH1010, second part).
     *
     * <p>On <b>create</b> only, and only for a field the document does not carry. A default that
     * re-applied on update would overwrite a value somebody deliberately cleared, which is the one
     * way a convenience becomes a data loss; and one that filled a field already holding something
     * would be a rule about what a page says rather than about where it starts.</p>
     *
     * @return the data to store — the argument itself when nothing was filled, so a document that
     *         needs no default survives byte for byte and no digest moves
     */
    public JsonNode withDefaults(ShPostType postType, JsonNode data) {
        if (postType == null || postType.getShPostTypeAttrs() == null
                || data == null || !data.isObject()) {
            return data;
        }
        tools.jackson.databind.node.ObjectNode filled = null;
        for (ShPostTypeAttr attr : postType.getShPostTypeAttrs()) {
            String field = attr.getName();
            boolean fillable = attr.getShParentPostTypeAttr() == null && field != null
                    && !data.has(field);
            String declared = fillable ? ShFieldRules.of(attr, objectMapper).defaultValue() : null;
            if (declared != null) {
                if (filled == null) {
                    filled = (tools.jackson.databind.node.ObjectNode) data.deepCopy();
                }
                filled.put(field, declared);
            }
        }
        return filled == null ? data : filled;
    }
}
