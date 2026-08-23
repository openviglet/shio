/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.shio.turing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.viglet.shio.api.post.type.ShInvalidIndexDeclarationException;
import com.viglet.shio.api.post.type.dto.ShPostTypeAttrDef;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.field.TurSNFieldName;

/**
 * What a post-type may declare about the Turing fields its content lands in (SH914).
 *
 * <h2>Judged when it is written, not when a post is published</h2>
 *
 * <p>A broken declaration is free until somebody publishes, and then it costs a document quietly
 * missing the field a facet counts — which is the defect SH914 exists to remove, arriving by
 * another road. The declaration describes the <em>model</em>, so the moment it is saved is the
 * moment it can be judged, and the only moment its author is still holding it.</p>
 *
 * <h2>The mistake that cannot be made</h2>
 *
 * <p>SH914's acceptance asked for "a declaration naming a field the post-type lacks fails at save".
 * Putting the declaration <b>on the attribute</b> instead of in a list that names attributes by
 * string deletes that case rather than validating it: there is nowhere to write the name of a field
 * that does not exist. An unrepresentable mistake beats a well-worded error for it, and it also
 * honours the other half of the acceptance — one surface describing SH16's model, not two.</p>
 *
 * <p>What is left is genuinely refusable and is refused here: a type nobody can index by, two
 * fields claiming one Turing name, and a name that would overwrite one of Turing's own standard
 * fields.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShTuringFieldDeclaration {

    private ShTuringFieldDeclaration() {
        throw new IllegalStateException("Static holder");
    }

    /**
     * The types a declaration may name.
     *
     * <p>{@code ARRAY} and {@code CURRENCY} are deliberately absent from the offer. A Shio field's
     * multiplicity is already the widget's business, so {@code ARRAY} would be a second place to
     * say it and a second place for the two to disagree; {@code CURRENCY} carries a unit this model
     * has nowhere to put, and a currency indexed with the wrong one is worse than a
     * {@code DOUBLE}.</p>
     */
    public static final Set<String> ALLOWED_TYPES = allowedTypes();

    private static Set<String> allowedTypes() {
        Set<String> names = new LinkedHashSet<>();
        for (TurSEFieldType type : TurSEFieldType.values()) {
            if (type != TurSEFieldType.ARRAY && type != TurSEFieldType.CURRENCY) {
                names.add(type.name());
            }
        }
        return java.util.Collections.unmodifiableSet(names);
    }

    /** Turing's own field names, which a declaration must not claim. */
    private static final Set<String> STANDARD = standardFieldNames();

    private static Set<String> standardFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        // Read reflectively rather than listed: TurSNFieldName is the SDK's constant holder, and a
        // hand-copied list here would go stale the first time it gains a field — which is exactly
        // the collision this guard is for.
        for (java.lang.reflect.Field field : TurSNFieldName.class.getFields()) {
            if (field.getType() == String.class && java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                try {
                    Object value = field.get(null);
                    if (value instanceof String name && !name.isBlank()) {
                        names.add(name.toLowerCase(Locale.ROOT));
                    }
                } catch (IllegalAccessException e) {
                    // A public static field that cannot be read is not a thing this JVM produces;
                    // skipping it would only narrow the guard, never widen it.
                }
            }
        }
        return names;
    }

    /**
     * Refuses a post-type whose indexing declarations cannot work. Walks nested fields too, since a
     * Relator's children are attributes with declarations of their own.
     */
    public static void validate(List<ShPostTypeAttrDef> fields) {
        Set<String> claimed = new LinkedHashSet<>();
        walk(fields, claimed);
    }

    private static void walk(List<ShPostTypeAttrDef> fields, Set<String> claimed) {
        if (fields == null) {
            return;
        }
        for (ShPostTypeAttrDef field : fields) {
            if (field == null) {
                continue;
            }
            if (field.declaresIndexed()) {
                check(field, claimed);
            } else if (field.turingType() != null || field.turingField() != null) {
                // A declaration with the switch off is the shape of somebody who thinks they turned
                // it on. Refused rather than ignored, because ignoring it means a facet that never
                // appears and nothing anywhere saying why.
                throw new ShInvalidIndexDeclarationException(
                        "Field '" + field.name() + "' describes a Turing field but is not indexed",
                        "`turingType`/`turingField` are set on '" + field.name() + "', but `indexed`"
                                + " is not true — so the value would still be folded into `text` and"
                                + " the declaration would do nothing.",
                        "Set `indexed: true` on the field, or remove `turingType` and `turingField`.",
                        null);
            }
            walk(field.fields(), claimed);
        }
    }

    private static void check(ShPostTypeAttrDef field, Set<String> claimed) {
        String type = field.turingType();
        if (type == null || type.isBlank()) {
            throw new ShInvalidIndexDeclarationException(
                    "Field '" + field.name() + "' is indexed with no type",
                    "Field '" + field.name() + "' is indexed but names no `turingType`, and Turing "
                            + "has to know what the value is before a range filter or a facet over "
                            + "it can mean anything.",
                    "Add `turingType` to '" + field.name() + "'.",
                    ALLOWED_TYPES);
        }
        if (!ALLOWED_TYPES.contains(type.toUpperCase(Locale.ROOT))) {
            throw new ShInvalidIndexDeclarationException(
                    "Unknown Turing field type '" + type + "'",
                    "'" + type + "' is not a type Turing indexes by, so field '" + field.name()
                            + "' could not be declared to it.",
                    "Name one of the allowed types on '" + field.name() + "'.",
                    ALLOWED_TYPES);
        }
        String turingField = field.resolvedTuringField();
        if (turingField == null || turingField.isBlank()) {
            throw new ShInvalidIndexDeclarationException(
                    "An indexed field has no name",
                    "The field declares no `name` and no `turingField`, so there is nothing to call "
                            + "it in the index.",
                    "Give the field a `name`, or set `turingField` explicitly.",
                    null);
        }
        if (STANDARD.contains(turingField.toLowerCase(Locale.ROOT))) {
            throw new ShInvalidIndexDeclarationException(
                    "'" + turingField + "' is one of Turing's own fields",
                    "Field '" + field.name() + "' would land in '" + turingField + "', which SH84's "
                            + "fixed mapping already fills from the post itself — the declaration "
                            + "would overwrite the title, URL or date every document depends on.",
                    "Set `turingField` on '" + field.name() + "' to a name of your own.",
                    new ArrayList<>(STANDARD));
        }
        if (!claimed.add(turingField.toLowerCase(Locale.ROOT))) {
            throw new ShInvalidIndexDeclarationException(
                    "Two fields both index into '" + turingField + "'",
                    "'" + turingField + "' is claimed twice in this post-type, so whichever field "
                            + "the mapper reached last would silently win and the other's value "
                            + "would never reach the index.",
                    "Give one of them a different `turingField`.",
                    null);
        }
    }

    /** The {@code TurSEFieldType} a declaration names, for the mapper. Never called unvalidated. */
    public static TurSEFieldType typeOf(String declared) {
        return Arrays.stream(TurSEFieldType.values())
                .filter(type -> type.name().equalsIgnoreCase(declared))
                .findFirst()
                .orElse(TurSEFieldType.STRING);
    }
}
