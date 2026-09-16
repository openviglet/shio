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
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.api.post.type.dto.ShFieldConstraints;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.type.ShPostTypeAttr;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Every rule a field declares, evaluated in one place (SH1010).
 *
 * <h2>What this is on top of</h2>
 *
 * <p>SH1009 gave {@code required} one evaluator — {@link ShRequiredFields} — and put it on every
 * writer. This is the same idea with something to evaluate: a field can now say how long its text
 * may be, what shape it has to match, what range a number is in, which values it accepts and how
 * many entries a list may hold. {@code required} stays where it is and this composes it, so the
 * sentence "is this page publishable" has one answer and two reasons it can be no.</p>
 *
 * <h2>An absent value is not a violation</h2>
 *
 * <p>A constraint describes what a value must look like <em>when there is one</em>. Applying
 * {@code minLength} to an empty optional field would make every constrained field required by the
 * back door, which is the one way a constraint vocabulary quietly changes the meaning of the model
 * it was added to. Presence is {@code required}'s job and it is the only rule that has it.</p>
 *
 * <h2>Advisory is not a weaker error</h2>
 *
 * <p>{@code recommendedLength} never refuses a write and never appears in a refusal. A title over
 * sixty characters is a judgement about search results, not a broken document, and a rule that
 * refused it would teach people to raise the number rather than shorten the title. It is reported
 * by {@code verify} as a warning and shown in the form, which is where advice belongs.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShFieldRules {

    /** Longest pattern this will compile. Past it, the rule belongs in a publish gate. */
    private static final int PATTERN_MAX = 512;

    private ShFieldRules() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * One rule a value breaks.
     *
     * @param rule what was broken, as a stable slug — the vocabulary {@code verify} reports and a
     *             caller can switch on, where the sentence is for a person and meant to improve
     * @param says what is wrong with the value, in the terms the curator typed it in
     * @param fix  what to do, naming the declared bound rather than restating the failure
     */
    public record Violation(String field, String rule, String says, String fix) {
    }

    /**
     * Every constraint this document breaks, in field order — advisories excluded.
     *
     * <p>Advisories are excluded because this list is what refuses a publish and what a plan reports
     * as blocking. {@link #advice} returns the other kind, and keeping them apart here is what stops
     * a warning from being one refactor away from an error.</p>
     */
    public static List<Violation> violations(ShPostType postType, JsonNode data,
                                             ObjectMapper mapper) {
        return evaluate(postType, data, mapper, false);
    }

    /** What the model recommends and this document does not follow. Never refuses anything. */
    public static List<Violation> advice(ShPostType postType, JsonNode data, ObjectMapper mapper) {
        return evaluate(postType, data, mapper, true);
    }

    /** Refuses the publish when a constraint is broken; silent when the document is valid. */
    public static void requireValid(ShPostType postType, JsonNode data, ObjectMapper mapper,
                                    String named) {
        List<Violation> broken = violations(postType, data, mapper);
        if (!broken.isEmpty()) {
            throw new ConstraintViolatedException(broken, named);
        }
    }

    /**
     * What a field declares, or {@link ShFieldConstraints#NONE}.
     *
     * <p>Stored as JSON in one column rather than a column per kind, because the vocabulary grows
     * and a column per kind is a migration per kind on the table every post-type write touches.
     * Unreadable JSON reads as no constraints: a stored block this cannot parse is a defect in the
     * post-type, and refusing every publish of every post of that type is a far worse answer than
     * the rules being off until somebody fixes it.</p>
     */
    public static ShFieldConstraints of(ShPostTypeAttr attr, ObjectMapper mapper) {
        String raw = attr == null ? null : attr.getConstraints();
        if (raw == null || raw.isBlank()) {
            return ShFieldConstraints.NONE;
        }
        try {
            ShFieldConstraints parsed = mapper.readValue(raw, ShFieldConstraints.class);
            return parsed == null ? ShFieldConstraints.NONE : parsed;
        } catch (JacksonException unreadable) {
            // silent-catch: a constraint block the server wrote and cannot read back is a defect in
            // the post-type, not in the content. Reporting it on every post of that type would bury
            // the one place it can be fixed under a finding per page.
            return ShFieldConstraints.NONE;
        }
    }

    private static List<Violation> evaluate(ShPostType postType, JsonNode data, ObjectMapper mapper,
                                            boolean advisory) {
        if (postType == null || postType.getShPostTypeAttrs() == null) {
            return List.of();
        }
        List<Violation> found = new ArrayList<>();
        for (ShPostTypeAttr attr : declared(postType)) {
            String field = attr.getName();
            JsonNode value = data == null || field == null ? null : data.get(field);
            // Absence is `required`'s question, and only its question: a constraint describes what a
            // value must look like when there is one.
            if (field == null || ShRequiredFields.isEmpty(value)) {
                continue;
            }
            ShFieldConstraints rules = of(attr, mapper);
            if (advisory) {
                advise(field, value, rules, found);
            } else {
                check(field, value, rules, found);
            }
        }
        return List.copyOf(found);
    }

    /** Top-level fields only, in the type's own order — the same walk {@code required} makes. */
    private static List<ShPostTypeAttr> declared(ShPostType postType) {
        return postType.getShPostTypeAttrs().stream()
                .filter(attr -> attr.getShParentPostTypeAttr() == null)
                .sorted(Comparator.comparingInt(ShPostTypeAttr::getOrdinal)
                        .thenComparing(attr -> attr.getName() == null ? "" : attr.getName()))
                .toList();
    }

    private static void check(String field, JsonNode value, ShFieldConstraints rules,
                              List<Violation> found) {
        if (value.isArray()) {
            checkItems(field, value, rules, found);
            return;
        }
        String text = value.isString() ? value.asString() : value.toString();
        checkText(field, text, rules, found);
        checkNumber(field, value, text, rules, found);
        checkValues(field, text, rules, found);
    }

    private static void checkText(String field, String text, ShFieldConstraints rules,
                                  List<Violation> found) {
        if (rules.minLength() != null && text.length() < rules.minLength()) {
            found.add(new Violation(field, "too-short",
                    "'" + field + "' is " + text.length() + " characters and the model asks for at "
                            + "least " + rules.minLength() + ".",
                    "Write at least " + rules.minLength() + " characters, or lower minLength on "
                            + "the post-type if the rule is wrong."));
        }
        if (rules.maxLength() != null && text.length() > rules.maxLength()) {
            found.add(new Violation(field, "too-long",
                    "'" + field + "' is " + text.length() + " characters and the model allows "
                            + rules.maxLength() + ".",
                    "Shorten it to " + rules.maxLength() + " characters, or raise maxLength on the "
                            + "post-type if the rule is wrong."));
        }
        if (rules.pattern() != null && !matches(rules.pattern(), text)) {
            // The declared message, when there is one: a regular expression quoted back at somebody
            // is a refusal only its author can act on, which is exactly what P6 is about.
            found.add(new Violation(field, "pattern-mismatch",
                    rules.patternMessage() != null && !rules.patternMessage().isBlank()
                            ? "'" + field + "': " + rules.patternMessage()
                            : "'" + field + "' does not match the shape the model declares.",
                    rules.patternMessage() != null && !rules.patternMessage().isBlank()
                            ? rules.patternMessage()
                            : "The declared pattern is " + rules.pattern()
                                    + " — and a pattern with no patternMessage beside it is a "
                                    + "refusal only whoever wrote it can act on."));
        }
    }

    private static void checkNumber(String field, JsonNode value, String text,
                                    ShFieldConstraints rules, List<Violation> found) {
        if (rules.min() == null && rules.max() == null) {
            return;
        }
        Double number = numberOf(value, text);
        if (number == null) {
            found.add(new Violation(field, "not-a-number",
                    "'" + field + "' has a range declared and does not hold a number.",
                    "Write a number, or drop min/max from the post-type if this field is text."));
            return;
        }
        if (rules.min() != null && number < rules.min()) {
            found.add(new Violation(field, "below-minimum",
                    "'" + field + "' is " + text + " and the model's smallest is " + plain(rules.min()) + ".",
                    "Use " + plain(rules.min()) + " or more."));
        }
        if (rules.max() != null && number > rules.max()) {
            found.add(new Violation(field, "above-maximum",
                    "'" + field + "' is " + text + " and the model's largest is " + plain(rules.max()) + ".",
                    "Use " + plain(rules.max()) + " or less."));
        }
    }

    private static void checkValues(String field, String text, ShFieldConstraints rules,
                                    List<Violation> found) {
        if (rules.values() == null || rules.values().isEmpty() || rules.values().contains(text)) {
            return;
        }
        found.add(new Violation(field, "not-an-accepted-value",
                "'" + field + "' is '" + text + "', which the model does not list.",
                "Use one of: " + String.join(", ", rules.values()) + "."));
    }

    private static void checkItems(String field, JsonNode value, ShFieldConstraints rules,
                                   List<Violation> found) {
        int size = value.size();
        if (rules.minItems() != null && size < rules.minItems()) {
            found.add(new Violation(field, "too-few-items",
                    "'" + field + "' holds " + size + " and the model asks for at least "
                            + rules.minItems() + ".",
                    "Add " + (rules.minItems() - size) + " more."));
        }
        if (rules.maxItems() != null && size > rules.maxItems()) {
            found.add(new Violation(field, "too-many-items",
                    "'" + field + "' holds " + size + " and the model allows " + rules.maxItems()
                            + ".",
                    "Remove " + (size - rules.maxItems()) + "."));
        }
    }

    private static void advise(String field, JsonNode value, ShFieldConstraints rules,
                               List<Violation> found) {
        if (rules.recommendedLength() == null || value.isArray()) {
            return;
        }
        String text = value.isString() ? value.asString() : value.toString();
        if (text.length() > rules.recommendedLength()) {
            found.add(new Violation(field, "longer-than-recommended",
                    "'" + field + "' is " + text.length() + " characters; " + rules.recommendedLength()
                            + " reads better where this is shown.",
                    "Shorten it if you can — this never refuses a write, and a rule that did would "
                            + "teach people to raise the number rather than shorten the text."));
        }
    }

    /**
     * Whether the whole value matches.
     *
     * <p>A pattern that does not compile, or one long enough to be a denial-of-service in itself, is
     * treated as no rule rather than as a failing one: refusing every post of a type because
     * somebody typed a bad regular expression into it puts the fix furthest from the person who can
     * make it.</p>
     */
    private static boolean matches(String pattern, String text) {
        if (pattern.length() > PATTERN_MAX) {
            return true;
        }
        try {
            return Pattern.compile(pattern).matcher(text).matches();
        } catch (PatternSyntaxException malformed) {
            // silent-catch: see above — a broken pattern is a defect in the post-type, and the
            // post-type editor is where it is reported, not on every page of that type.
            return true;
        }
    }

    private static Double numberOf(JsonNode value, String text) {
        if (value.isNumber()) {
            return value.asDouble();
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException notANumber) {
            // silent-catch: the caller reports it as `not-a-number`, which is the finding this
            // question exists to produce — swallowing it here is what lets that happen once.
            return null;
        }
    }

    /** {@code 10} rather than {@code 10.0}: the bound as somebody typed it into the model. */
    private static String plain(double bound) {
        return bound == Math.rint(bound) && !Double.isInfinite(bound)
                ? String.format(Locale.ROOT, "%d", (long) bound)
                : String.valueOf(bound);
    }

    /**
     * The teaching 400 a publish is refused with (P6).
     *
     * <p>{@code at} is the first broken field, so the console lands the curator in the input; the
     * detail names every one of them, because fixing one and being refused again is the worst
     * version of this.</p>
     */
    public static class ConstraintViolatedException extends ShTeachingException {

        public ConstraintViolatedException(List<Violation> broken, String named) {
            super(ShProblem.badRequest("constraint-violated",
                            broken.size() == 1
                                    ? broken.get(0).says()
                                    : broken.size() + " fields do not match what the model declares")
                    .withAt("data." + broken.get(0).field())
                    .withDetail("A draft may be anything — that is what a draft is for — but "
                            + named + " would go live with " + sentences(broken))
                    .withAllowed(broken.stream().map(Violation::field).toList())
                    .withFix(broken.stream().map(Violation::fix).findFirst().orElse("")));
        }

        private static String sentences(List<Violation> broken) {
            StringBuilder out = new StringBuilder();
            for (Violation violation : broken) {
                out.append(out.isEmpty() ? "" : " ").append(violation.says());
            }
            return out.toString();
        }
    }
}
