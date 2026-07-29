/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.http.HttpStatus;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * One name per concept, and an unrecognised argument refused (SH331).
 *
 * <h2>Why this is one component and not a rename per tool</h2>
 *
 * <p>SH331 was filed from four wrong calls in one real build, and read as four inconsistencies to
 * align. SH340 then <b>measured</b> it and found something else: {@code shio_context},
 * {@code shio_find}, {@code shio_verify} and {@code shio_changes} all accept an unrecognised key and
 * answer 200. So "an unknown argument is silently dropped" was the <em>default behaviour of the
 * surface</em> rather than one tool's oversight, and the fix is a refusal applied once, where
 * arguments are read — not four renames. That is what the measurement changed about the design, and
 * it is the reason this class exists.</p>
 *
 * <p>The expensive half is the silent one. A teaching error costs one round trip and the SH80
 * {@code fix} makes the retry land; a dropped argument costs a <b>wrong outcome nobody notices</b> —
 * {@code scope} instead of {@code site} on {@code shio_remember} wrote the note instance-wide,
 * reported success, and finding out meant reading the tool's Java. It is also a direct violation of
 * SH320's rule: {@code site} plainly <em>selects</em> work, since it decides which memory the note
 * joins and therefore which sessions ever see it.</p>
 *
 * <h2>A synonym is accepted and reported; anything else is refused</h2>
 *
 * <p>Accepting an unambiguous synonym removes the round trip rather than shortening it, and the
 * boundary is drawn where SH331 drew it, because it will otherwise be "simplified" into the opposite
 * of SH168. It is safe here and only here because all three hold: the set is <b>closed</b>, it is
 * <b>server-owned</b>, and the intent is <b>unambiguous</b>. A {@code data} key is none of those —
 * SH168's whole point is that an upsert declares whole state and a near-miss silently replaces the
 * field it meant — so this never touches one.</p>
 *
 * <p>It also sits correctly against SH320: a synonym does not select or bound work differently from
 * the canonical name, so accepting one cannot answer a different question than the one asked.</p>
 *
 * <h2>Every alias is a spelling this product itself uses</h2>
 *
 * <p>Not a guess at what a model might type. Each entry below is a name some other Shio surface
 * gives the same concept, which is exactly why an agent reached for it: the concept has four names
 * ({@code postTypeId} in REST, {@code shPostType} in the console, {@code type} in a batch op,
 * {@code postType} on {@code POST /api/v2/post-unified}), so a caller that learned one of them and
 * used it elsewhere was being consistent while the product was not.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
public final class ShMcpVocabulary {

    /**
     * Alias → canonical, applied only when the canonical name is declared by the tool being called
     * and the alias is not. That guard is what keeps the table from breaking a tool that
     * legitimately declares one of these names — {@code shio_digest} really does take {@code url},
     * and {@code shio_apply} really does take {@code site}.
     */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            // The concept with four spellings across this product's own surfaces.
            Map.entry("postType", "type"),
            Map.entry("postTypeId", "type"),
            Map.entry("shPostType", "type"),
            // A plural-valued field with a singular name — the mistake is reading the value's shape
            // rather than the field's name, which is a reasonable thing for a caller to do.
            Map.entry("addresses", "address"),
            // PUT /api/v2/agent/memory takes `scope`; `shio remember` takes --site; the tool takes
            // `site`. This is the one that did not fail, and therefore the one that cost hours.
            Map.entry("scope", "site"),
            Map.entry("siteName", "site"),
            // `changes` answers with a `cursor` and takes it back as `since`, so the response itself
            // teaches the wrong name for the next call.
            Map.entry("cursor", "since"),
            // A comma-separated set with a plural name, asked for in the singular.
            Map.entry("check", "checks"),
            Map.entry("folderId", "folder"));

    private ShMcpVocabulary() {
        // utility
    }

    /**
     * The arguments a tool should receive, plus what was renamed on the way.
     *
     * @param arguments the canonical arguments
     * @param notes     one line per accepted synonym, for the caller to learn from — reported rather
     *                  than silent, because a caller that is never told keeps sending the alias
     */
    public record Normalized(ObjectNode arguments, List<String> notes) {
    }

    /**
     * Rewrites known synonyms and refuses anything else.
     *
     * @param tool      the tool's name, for the refusal's message
     * @param arguments what the caller sent
     * @param declared  the tool's own {@code inputSchema} properties — read from the schema rather
     *                  than from a second list, so a tool that gains an argument needs no change
     *                  here (SH260's rule: the code that owns the set answers what the set is)
     * @throws UnknownArgumentException naming the allowed set and the nearest match
     */
    public static Normalized normalize(String tool, ObjectNode arguments,
                                       Map<String, Object> schema) {
        List<String> notes = new ArrayList<>();
        ObjectNode canonical = normalizeObject(tool, arguments, properties(schema), notes);
        return new Normalized(canonical, List.copyOf(notes));
    }

    private static ObjectNode normalizeObject(String where, ObjectNode arguments,
                                              Map<String, Object> declared, List<String> notes) {
        ObjectNode canonical = arguments.objectNode();
        List<String> unknown = new ArrayList<>();

        // Sorted, so the notes and the refusal are byte-identical for identical input (P5).
        for (String key : new TreeSet<>(arguments.propertyNames())) {
            JsonNode value = arguments.get(key);
            if (declared.containsKey(key)) {
                canonical.set(key, descend(where + "." + key, value, declared.get(key), notes));
                continue;
            }
            String target = ALIASES.get(key);
            if (target != null && declared.containsKey(target) && !arguments.has(target)) {
                canonical.set(target, descend(where + "." + target, value, declared.get(target), notes));
                notes.add("read `" + key + "` as `" + target + "` — the canonical name on this "
                        + "surface. Both work; the second is the one to keep.");
                continue;
            }
            unknown.add(key);
        }
        if (!unknown.isEmpty()) {
            throw new UnknownArgumentException(where, unknown, declared.keySet());
        }
        return canonical;
    }

    /**
     * One level down, and only where the schema says what is down there.
     *
     * <p>This is what reaches the mistake a real agent actually made: {@code shio_write} declares
     * only {@code ops} at the top level, and {@code postType}-for-{@code type} was written
     * <em>inside</em> an op. SH331's boundary permits it precisely there — an op's argument names are
     * a closed, server-owned, unambiguous set.</p>
     *
     * <p><b>And it must never reach a {@code data} key.</b> {@code data} is declared as an object
     * with <em>no</em> {@code properties}, so the guard is structural rather than a name check: with
     * nothing declared there is nothing to validate, and the value passes through untouched. That is
     * the SH168 line — an upsert declares whole state, and a near-miss silently replacing the field
     * it meant is the failure this must not import. If a future schema ever declares the properties
     * of {@code data}, this recursion would start refusing content keys, and that is the moment to
     * stop it rather than to extend the alias table.</p>
     */
    private static JsonNode descend(String where, JsonNode value, Object schema, List<String> notes) {
        Map<String, Object> declared = properties(schema);
        if (declared.isEmpty()) {
            return value;
        }
        if (value instanceof ObjectNode object) {
            return normalizeObject(where, object, declared, notes);
        }
        if (value instanceof ArrayNode source) {
            ArrayNode array = source.arrayNode();
            int index = 0;
            for (JsonNode element : value) {
                array.add(element instanceof ObjectNode object
                        ? normalizeObject(where + "[" + index + "]", object, declared, notes)
                        : element);
                index++;
            }
            return array;
        }
        return value;
    }

    /**
     * The {@code properties} an object or array-of-object schema declares, or empty.
     *
     * <p>Read out of the schema the client already receives (SH260's rule), which is also what makes
     * the {@code data} exemption above self-maintaining: nothing is declared, so nothing is checked.</p>
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> properties(Object schema) {
        if (!(schema instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Object items = map.get("items");
        Object properties = map.get("properties") != null ? map.get("properties")
                : (items instanceof Map<?, ?> itemMap ? itemMap.get("properties") : null);
        return properties instanceof Map<?, ?> declared
                ? (Map<String, Object>) declared : Map.of();
    }

    /** The canonical names, for a test or a report that wants to state the table. */
    public static Map<String, String> aliases() {
        return new TreeMap<>(ALIASES);
    }

    /**
     * An argument this tool does not declare and no synonym explains.
     *
     * <p>A refusal rather than a shrug, which is the whole of SH331: before this the argument was
     * dropped and the call answered 200, so the caller learned nothing and the outcome was wrong.
     * It carries the closed set and the nearest name, because an agent that guessed once will guess
     * again from the same reasoning unless it is told which word this surface uses.</p>
     */
    public static class UnknownArgumentException extends ShTeachingException {

        public UnknownArgumentException(String tool, List<String> unknown, Set<String> declared) {
            super(ShProblem.of("unknown-argument", HttpStatus.BAD_REQUEST,
                            unknown.size() == 1
                                    ? tool + " has no argument `" + unknown.get(0) + "`"
                                    : tool + " has no arguments " + unknown)
                    .withDetail("An argument this tool does not declare used to be ignored, so the "
                            + "call reported success while doing something other than what was "
                            + "asked (SH331). It is refused instead.")
                    .withAllowed(new TreeSet<>(declared))
                    .withDidYouMeanFrom(unknown.get(0))
                    .withFix("Send the argument under its declared name. `shio_context` describes "
                            + "this instance's model and conventions if the concept is the one that "
                            + "is unclear rather than the spelling."));
        }
    }
}
