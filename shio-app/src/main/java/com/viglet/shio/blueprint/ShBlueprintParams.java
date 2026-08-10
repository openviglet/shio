/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.blueprint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.blueprint.dto.ShBlueprintManifest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * A blueprint's parameters (SH92): validate what the caller sent, then substitute it
 * into the package.
 *
 * <h2>Why substitution and not a template language</h2>
 *
 * <p>{@code {{brand}}} in a string value, and nothing else. No conditionals, no loops,
 * no expressions — because every one of those turns a blueprint from data into a program
 * whose output cannot be predicted from reading it. Design law P5 wants a package that
 * produces the same site from the same parameters; the cheapest way to guarantee that is
 * a substitution that cannot branch.</p>
 *
 * <h2>Every rejection is a teaching error, and they matter here more than most</h2>
 *
 * <p>Parameters are the one part of a blueprint the caller types, so they are the one
 * part the caller gets wrong. A parameter name that is not declared, a value outside a
 * declared {@code enum}, a required parameter with no default and no value: each is a
 * 400 that names the declared parameters, the accepted values and the nearest match
 * (P6). An unknown name is refused rather than ignored, because a silently-ignored
 * {@code brandName} produces a site that says {@code Acme} everywhere and gives the
 * caller no reason to suspect the typo.</p>
 *
 * <h2>The one reserved name</h2>
 *
 * <p>{@code site} is always available and never declarable: it is the target site's
 * name, which comes from the request rather than the package (see
 * {@link ShBlueprintManifest}). A package that declared it would be trying to name its
 * own destination, so the registry refuses to load one that does.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShBlueprintParams {

    /** The built-in parameter: the site the blueprint is being applied to. */
    public static final String SITE = "site";

    /** {@code {{name}}}, tolerating inner whitespace. */
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    private ShBlueprintParams() {
        // utility
    }

    /**
     * The effective parameter values: declared defaults, overridden by what the caller
     * sent, plus the built-in {@code site}.
     *
     * <p>Returned as an insertion-ordered map of <em>strings</em>, because substitution
     * is textual and a number that reached a template as {@code 3.0} instead of {@code 3}
     * would be a difference nobody asked for. The map is echoed back in the result, so a
     * default the caller never mentioned is still visible in the record of what ran.</p>
     *
     * @param manifest the package's manifest, whose {@code params} schema is the contract
     * @param sent     the caller's values, or {@code null}
     * @param site     the target site's name — the value of the reserved {@code site}
     */
    public static Map<String, String> resolve(ShBlueprintManifest manifest, JsonNode sent,
                                              String site) {
        Map<String, JsonNode> declared = properties(manifest.params());
        List<String> names = new ArrayList<>(declared.keySet());
        Map<String, String> values = new LinkedHashMap<>();
        values.put(SITE, site == null ? "" : site);

        if (sent != null && sent.isObject()) {
            for (String name : sent.propertyNames()) {
                if (SITE.equals(name)) {
                    // Not merely unknown: it is the one name the request already carries
                    // in its own field, and accepting it in two places would let them
                    // disagree about where the content goes.
                    throw new ReservedParamException(manifest.name());
                }
                if (!declared.containsKey(name)) {
                    throw new UnknownParamException(manifest.name(), name, names);
                }
                values.put(name, string(manifest.name(), name, declared.get(name), sent.get(name)));
            }
        }

        for (Map.Entry<String, JsonNode> entry : declared.entrySet()) {
            String name = entry.getKey();
            if (values.containsKey(name)) {
                continue;
            }
            JsonNode spec = entry.getValue();
            JsonNode fallback = spec == null ? null : spec.get("default");
            if (fallback != null && !fallback.isNull()) {
                values.put(name, string(manifest.name(), name, spec, fallback));
            } else if (required(manifest.params()).contains(name)) {
                throw new MissingParamException(manifest.name(), name, spec, names);
            }
        }
        // Sorted by declaration order, with `site` first — so an identical apply produces
        // an identical echo, and a diff of two runs shows the parameter that moved (P5).
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put(SITE, values.get(SITE));
        for (String name : names) {
            if (values.containsKey(name)) {
                ordered.put(name, values.get(name));
            }
        }
        return java.util.Collections.unmodifiableMap(ordered);
    }

    /**
     * Substitutes into every string in a JSON tree, leaving structure and non-string
     * values untouched.
     *
     * <p>Object <em>keys</em> are not substituted. A field name is part of the content
     * model, not of the copy, and a parameter that could rename a field would let a
     * blueprint's parameters change the schema its own post-types declare.</p>
     */
    public static JsonNode substitute(JsonNode node, Map<String, String> values,
                                      String blueprint) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isString()) {
            return JsonNodeFactory.instance.stringNode(text(node.asString(), values, blueprint));
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode(node.size());
            node.forEach(element -> array.add(substitute(element, values, blueprint)));
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = JsonNodeFactory.instance.objectNode();
            for (String name : node.propertyNames()) {
                JsonNode child = node.get(name);
                // SH395 — a render field holds a Handlebars template, and the two languages share
                // `{{ }}`. Substituting there still has to happen (a layout may say
                // `{{blogTitle}}` in its masthead), but an UNKNOWN name is the renderer's
                // expression rather than a package's typo — `{{post.title}}`, `{{theme.css}}`,
                // `{{#query}}`. So it passes through instead of refusing the apply.
                object.set(name, TEMPLATE_FIELDS.contains(name) && child != null && child.isString()
                        ? JsonNodeFactory.instance.stringNode(
                                template(child.asString(), values))
                        : substitute(child, values, blueprint));
            }
            return object;
        }
        return node;
    }

    /**
     * The render fields, whose contents are a template in another language (SH395).
     *
     * <p>Kept in step with {@code ShBlueprintRegistry.TEMPLATE_FIELDS} by
     * {@code ShBlueprintTemplateFieldTest}: the load-time check and the substitution have to agree
     * about which fields are templates, and two lists that drift would refuse a package at load
     * that substitution would have accepted, or the reverse — which is worse, because it lands as
     * a `{{post.title}}` written into somebody's page.</p>
     */
    public static final java.util.Set<String> TEMPLATE_FIELDS =
            java.util.Set.of("HTML", "CSS", "JAVASCRIPT");

    /**
     * Substitutes the declared parameters into a template and leaves everything else alone.
     *
     * <p>The trade this makes, stated rather than hidden: a <b>typo'd</b> parameter here —
     * `{{blogTitel}}` — is not caught, and cannot be. The check's whole basis is that an
     * undeclared name is a mistake, and inside a Handlebars template an undeclared name is the
     * normal case. Bounded by being field-scoped, and the renderer has its own answer for an
     * expression that resolves to nothing.</p>
     */
    public static String template(String raw, Map<String, String> values) {
        if (raw == null || raw.indexOf('{') < 0) {
            return raw;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                continue;
            }
            out.append(raw, last, matcher.start()).append(value);
            last = matcher.end();
        }
        return out.append(raw, last, raw.length()).toString();
    }

    /**
     * Substitutes into one string.
     *
     * @throws UnknownPlaceholderException when the text names a parameter the package
     *         does not declare — the package's own bug, surfaced as a 400 that names the
     *         parameters it does declare rather than as an empty gap in the copy
     */
    public static String text(String raw, Map<String, String> values, String blueprint) {
        if (raw == null || raw.indexOf('{') < 0) {
            return raw;
        }
        Matcher matcher = PLACEHOLDER.matcher(raw);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new UnknownPlaceholderException(blueprint, name,
                        values.keySet().stream().sorted().toList());
            }
            out.append(raw, last, matcher.start()).append(value);
            last = matcher.end();
        }
        return out.append(raw, last, raw.length()).toString();
    }

    /** Every placeholder a string mentions — used by the registry's load-time check. */
    public static List<String> placeholders(String raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = PLACEHOLDER.matcher(raw);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** The declared properties of a {@code params} JSON Schema, in declaration order. */
    public static Map<String, JsonNode> properties(JsonNode params) {
        Map<String, JsonNode> declared = new LinkedHashMap<>();
        JsonNode properties = params == null ? null : params.get("properties");
        if (properties != null && properties.isObject()) {
            for (String name : properties.propertyNames()) {
                declared.put(name, properties.get(name));
            }
        }
        return declared;
    }

    /** The {@code required} array of a {@code params} JSON Schema. */
    public static List<String> required(JsonNode params) {
        JsonNode required = params == null ? null : params.get("required");
        if (required == null || !required.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>(required.size());
        required.forEach(name -> names.add(name.asString()));
        return names;
    }

    /**
     * Validates one value against its declared spec and renders it as a string.
     *
     * <p>The supported vocabulary is the JSON Schema subset a blueprint parameter
     * actually needs — {@code type}, {@code enum}, {@code pattern} — and an unsupported
     * keyword is ignored rather than half-honoured. Pretending to enforce
     * {@code minLength} would be worse than not offering it.</p>
     */
    private static String string(String blueprint, String name, JsonNode spec, JsonNode value) {
        String type = spec == null || spec.get("type") == null ? "string" : spec.get("type").asString();
        boolean fits = switch (type) {
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> value.isString();
        };
        if (!fits) {
            throw new BadParamTypeException(blueprint, name, type, value);
        }
        String rendered = value.asString();
        JsonNode choices = spec == null ? null : spec.get("enum");
        if (choices != null && choices.isArray()) {
            List<String> allowed = new ArrayList<>(choices.size());
            choices.forEach(choice -> allowed.add(choice.asString()));
            if (!allowed.contains(rendered)) {
                throw new BadParamValueException(blueprint, name, rendered, allowed);
            }
        }
        JsonNode pattern = spec == null ? null : spec.get("pattern");
        if (pattern != null && pattern.isString() && !matches(pattern.asString(), rendered)) {
            throw new BadParamPatternException(blueprint, name, rendered, pattern.asString());
        }
        return rendered;
    }

    /**
     * A malformed {@code pattern} is the package's fault, and it must not become a 500 on
     * the caller's apply — an un-compilable regex simply does not constrain anything.
     */
    private static boolean matches(String pattern, String value) {
        try {
            return Pattern.compile(pattern).matcher(value).matches();
        } catch (PatternSyntaxException invalid) {
            return true;
        }
    }

    // --- teaching errors (SH80) ---

    /** A 400: a parameter this package does not declare. */
    public static class UnknownParamException extends ShTeachingException {

        public UnknownParamException(String blueprint, String sent, List<String> declared) {
            super(ShProblem.badRequest("unknown-blueprint-param",
                            "'" + sent + "' is not a parameter of blueprint '" + blueprint + "'")
                    .withDetail("An unknown parameter is refused rather than ignored: a typo "
                            + "that was ignored would produce a site full of the package's "
                            + "defaults and nothing to suspect.")
                    .withAllowed(declared)
                    .withDidYouMeanFrom(sent)
                    .withFix(declared.isEmpty()
                            ? "This blueprint takes no parameters — send `params: {}`."
                            : "Use one of the declared parameters, or read GET "
                                    + "/api/v2/agent/blueprints/" + blueprint + " for the schema.")
                    .withExample(declared.isEmpty() ? "{}" : declared.get(0)));
        }
    }

    /** A 400: {@code site} arrived as a parameter. */
    public static class ReservedParamException extends ShTeachingException {

        public ReservedParamException(String blueprint) {
            super(ShProblem.badRequest("reserved-blueprint-param",
                            "`site` is not a blueprint parameter")
                    .withDetail("The target site travels in the request's own `site` field. "
                            + "Accepting it as a parameter too would let the two disagree "
                            + "about where the content goes.")
                    .withFix("Remove `site` from `params` and set the request's `site` instead.")
                    .withExample("{\"site\":\"Acme\",\"params\":{}}"));
        }
    }

    /** A 400: a required parameter with no default and no value. */
    public static class MissingParamException extends ShTeachingException {

        public MissingParamException(String blueprint, String name, JsonNode spec,
                                     List<String> declared) {
            super(ShProblem.badRequest("missing-blueprint-param",
                            "Blueprint '" + blueprint + "' requires the parameter '" + name + "'")
                    .withDetail(describe(spec))
                    .withAllowed(declared)
                    .withFix("Add `" + name + "` to `params`.")
                    .withExample("{\"params\":{\"" + name + "\":\"…\"}}"));
        }

        private static String describe(JsonNode spec) {
            JsonNode description = spec == null ? null : spec.get("description");
            return description != null && description.isString()
                    ? description.asString()
                    : "It has no default, so the package cannot fill it in.";
        }
    }

    /** A 400: a value of the wrong JSON type. */
    public static class BadParamTypeException extends ShTeachingException {

        public BadParamTypeException(String blueprint, String name, String type, JsonNode sent) {
            super(ShProblem.badRequest("bad-blueprint-param",
                            "Parameter '" + name + "' of blueprint '" + blueprint
                                    + "' must be a " + type)
                    .withDetail("Received " + sent.getNodeType().name().toLowerCase(java.util.Locale.ROOT)
                            + ". Values are substituted textually, so the declared type is "
                            + "checked before anything is written.")
                    .withFix("Send `" + name + "` as a JSON " + type + ".")
                    .withExample(example(type)));
        }

        private static Object example(String type) {
            return switch (type) {
                case "integer" -> 3;
                case "number" -> 1.5;
                case "boolean" -> true;
                default -> "Acme";
            };
        }
    }

    /** A 400: a value outside a declared {@code enum}. */
    public static class BadParamValueException extends ShTeachingException {

        public BadParamValueException(String blueprint, String name, String sent,
                                      List<String> allowed) {
            super(ShProblem.badRequest("bad-blueprint-param",
                            "'" + sent + "' is not an accepted value for '" + name + "'")
                    .withDetail("Blueprint '" + blueprint + "' declares a closed set for this "
                            + "parameter.")
                    .withAllowed(allowed)
                    .withDidYouMeanFrom(sent)
                    .withFix("Use one of the accepted values.")
                    .withExample(allowed.isEmpty() ? "" : allowed.get(0)));
        }
    }

    /** A 400: a value that fails a declared {@code pattern}. */
    public static class BadParamPatternException extends ShTeachingException {

        public BadParamPatternException(String blueprint, String name, String sent,
                                       String pattern) {
            super(ShProblem.badRequest("bad-blueprint-param",
                            "'" + sent + "' does not match the shape '" + name + "' requires")
                    .withDetail("Blueprint '" + blueprint + "' constrains this parameter to "
                            + pattern + ".")
                    .withFix("Send a value matching that pattern.")
                    .withExample(pattern));
        }
    }

    /** A 400: the package's own text names a parameter it never declared. */
    public static class UnknownPlaceholderException extends ShTeachingException {

        public UnknownPlaceholderException(String blueprint, String name, List<String> available) {
            super(ShProblem.badRequest("unknown-blueprint-placeholder",
                            "Blueprint '" + blueprint + "' uses {{" + name
                                    + "}}, which it does not declare")
                    .withDetail("This is a defect in the package, not in the request: a "
                            + "placeholder with no parameter behind it would silently leave a "
                            + "gap in the content.")
                    .withAllowed(available)
                    .withDidYouMeanFrom(name)
                    .withFix("Declare '" + name + "' in the blueprint's `params`, or remove the "
                            + "placeholder.")
                    .withExample("{\"params\":{\"properties\":{\"" + name + "\":{\"type\":\"string\"}}}}"));
        }
    }
}
