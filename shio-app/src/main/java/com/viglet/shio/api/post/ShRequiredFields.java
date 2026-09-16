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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;
import com.viglet.shio.widget.ShSystemWidget;

import tools.jackson.databind.JsonNode;

/**
 * What a post-type says must be filled in, and one answer to whether it is (SH1009).
 *
 * <h2>The defect</h2>
 *
 * <p>The console's form painted the required marker and posted the data unconditionally; the REST
 * create and update validated only the post-type name; the agent's batch refused an <em>undeclared</em>
 * key and never an empty required one. The only enforcement anywhere was {@code required-field-empty}
 * in {@code shio verify} — <b>after the fact</b>, and only if somebody ran it. So a page missing its
 * title published, served, and reached a reviewer looking complete.</p>
 *
 * <h2>A draft may be incomplete; a publish may not</h2>
 *
 * <p>Drafts are where work happens, and a rule that refused an incomplete one would make the product
 * worse than the defect: a curator cannot save a page they have half written, and an agent cannot
 * build a page in two ops. So saving is never refused and publishing always is — the moment a page
 * stops being somebody's work in progress and becomes what a visitor is served.</p>
 *
 * <h2>One evaluator</h2>
 *
 * <p>Three callers: the write service, which every publish passes through (the console's, REST's,
 * the batch's, the scheduler's); the batch's plan, so a dry run reports what the apply would refuse
 * without writing; and {@code ShAgentVerifyService}, whose rule this used to be. That third one is
 * the point of extracting it rather than writing the check where it was needed — two definitions of
 * "empty" would mean a page verify calls complete and a publish refuses, which is the state a reader
 * cannot act on from either end.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShRequiredFields {

    /**
     * Widgets whose emptiness says nothing about the page.
     *
     * <p>A {@code Tab} holds no value of its own, and a {@code Relator} is a container whose rows
     * live elsewhere; requiring either would refuse every post of a type that declares one. The set
     * is the verify rule's, moved rather than re-derived.</p>
     */
    private static final Set<String> CONTAINERS =
            Set.of(ShSystemWidget.TAB, ShSystemWidget.RELATOR);

    private ShRequiredFields() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * The required fields this data leaves empty, in declaration order.
     *
     * @return an empty list when the page is complete, which is the ordinary answer and the one
     *         every caller checks first
     */
    public static List<String> missing(ShPostType postType, JsonNode data) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        List<String> empty = new ArrayList<>();
        List<ShPostTypeAttr> attrs = postType.getShPostTypeAttrs().stream()
                // A post-type's attribute set is FLAT: a Relator's child fields carry the same
                // post-type, so an unfiltered walk asks the document for `title` when `title` is a
                // column inside each row of `features`. That is the first thing this evaluator got
                // wrong, and `ShBlueprintApplyTest` caught it — every first-party section post
                // refused to publish, naming a field the document was never supposed to carry.
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                // Ordinal then name, so two runs over one post-type name the fields in the same
                // order: the attributes arrive in a Set, ordinals are not unique, and a refusal
                // that reorders itself between two identical requests is one nobody can diff.
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .toList();
        for (ShPostTypeAttr attr : attrs) {
            String field = attr.getName();
            if (field == null || field.isBlank() || attr.getRequired() != 1
                    || CONTAINERS.contains(widgetOf(attr))) {
                continue;
            }
            if (isEmpty(data == null ? null : data.get(field))) {
                empty.add(field);
            }
        }
        return List.copyOf(empty);
    }

    /** Refuses the publish when anything is missing; does nothing when the page is complete. */
    public static void requireComplete(ShPostType postType, JsonNode data, String address) {
        List<String> empty = missing(postType, data);
        if (!empty.isEmpty()) {
            throw new RequiredFieldEmptyException(empty, address);
        }
    }

    /**
     * Blank is empty, and so is an empty array or object.
     *
     * <p>A whitespace-only title is the case this exists for: it satisfies "a value is present" and
     * is not a title, and it is what a copy-paste from a converted page leaves behind.</p>
     */
    public static boolean isEmpty(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isString()) {
            return value.asString().isBlank();
        }
        return (value.isArray() || value.isObject()) && value.isEmpty();
    }

    private static String widgetOf(ShPostTypeAttr attr) {
        return attr.getShWidget() == null || attr.getShWidget().getName() == null
                ? "" : attr.getShWidget().getName();
    }

    /**
     * The teaching 400 a publish is refused with (P6).
     *
     * <p>{@code at} is {@code data.<field>} — the path the console's problem reader maps onto the
     * input, so the curator lands in the box to type in rather than reading a sentence about a page
     * with sixteen of them. The first missing field names the location; the message names them all,
     * because fixing one and being refused again is the worst version of this.</p>
     */
    public static class RequiredFieldEmptyException extends ShTeachingException {

        public RequiredFieldEmptyException(List<String> empty, String address) {
            super(ShProblem.badRequest("required-field-empty",
                            empty.size() == 1
                                    ? "'" + empty.get(0) + "' is required and empty"
                                    : String.join(", ", empty) + " are required and empty")
                    .withAt("data." + empty.get(0))
                    // The fields are named here as well as in the title, because this is the
                    // sentence a wrapped exception carries: a batch reports its op's failure by
                    // detail, and "missing a field it declares it must have" sends whoever reads
                    // it to go and find out which.
                    .withDetail("A draft may be incomplete — that is what a draft is for — but a "
                            + "publish is what a visitor is served, and " + address
                            + " would go live with " + String.join(", ", empty) + " empty.")
                    .withAllowed(empty)
                    .withFix("Fill in " + String.join(", ", empty) + " and publish again, or drop "
                            + "the required flag on the post-type if it is genuinely optional."));
        }
    }
}
