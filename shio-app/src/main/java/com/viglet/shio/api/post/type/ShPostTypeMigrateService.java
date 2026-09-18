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
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;
import com.viglet.shio.persistence.model.post.type.ShPostType;
import com.viglet.shio.persistence.model.post.unified.ShPostState;
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.persistence.model.post.unified.ShPostUnified;
import com.viglet.shio.persistence.repository.post.ShPostUnifiedRepository;
import com.viglet.shio.persistence.repository.post.type.ShPostTypeRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * SH1018 — a model change applied to the content it governs.
 *
 * <h2>Three mappings, and no fourth</h2>
 *
 * <p>{@code rename} moves a value to the key the model now declares, {@code drop} removes one the
 * model no longer has, and {@code default} fills a field that was added as required. Those are the
 * three shapes {@link ShPostTypeCheckService} reports, which is deliberate: the migration answers
 * the measurement rather than being a general-purpose content rewriter. A fourth — "set this field
 * to a computed value" — is a script, and a script that runs inside a batch op is a door with no
 * bound on what goes through it.</p>
 *
 * <h2>A rename never lands on an occupied key</h2>
 *
 * <p>If the target already holds something, this refuses the whole migration rather than choosing
 * a winner. Both values are somebody's content, and the two plausible rules — the old wins, the new
 * wins — are each silent data loss half the time. The refusal names the rows, which is what lets a
 * caller decide.</p>
 *
 * <h2>Nothing is written until everything is checked</h2>
 *
 * <p>A migration that failed on row 400 of 900 would leave a type whose content is half in one
 * shape and half in another, with no way to tell which rows are which. So the whole plan is
 * computed first and applied second, in one transaction.</p>
 */
@Service
public class ShPostTypeMigrateService {
    /** SH1034 — the reverse index, maintained beside this class's own writes. */
    private final ShObjectReferenceService shObjectReferenceService;


    private final ShPostTypeRepository shPostTypeRepository;
    private final ShPostUnifiedRepository shPostUnifiedRepository;
    private final ObjectMapper objectMapper;

    public ShPostTypeMigrateService(ShPostTypeRepository shPostTypeRepository,
                                    ShPostUnifiedRepository shPostUnifiedRepository,
                                    ObjectMapper objectMapper,
            ShObjectReferenceService shObjectReferenceService) {
        this.shObjectReferenceService = shObjectReferenceService;
        this.shPostTypeRepository = shPostTypeRepository;
        this.shPostUnifiedRepository = shPostUnifiedRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param renamed how many rows had a value moved, per old name
     * @param dropped how many rows lost a key, per name
     * @param defaulted how many rows gained a value, per name
     * @param rows how many rows this run touched at all
     */
    public record Plan(String postType, int rows, List<String> postIds,
                       Map<String, Integer> renamed,
                       Map<String, Integer> dropped,
                       Map<String, Integer> defaulted) {

        public boolean isEmpty() {
            return renamed.isEmpty() && dropped.isEmpty() && defaulted.isEmpty();
        }

        /** One line, for the change a batch reports. */
        public String describe() {
            List<String> parts = new ArrayList<>();
            renamed.forEach((from, count) -> parts.add(count + " renamed from " + from));
            dropped.forEach((name, count) -> parts.add(count + " dropped " + name));
            defaulted.forEach((name, count) -> parts.add(count + " defaulted " + name));
            return parts.isEmpty() ? "nothing to migrate" : String.join(", ", parts);
        }
    }

    /**
     * Computes and, unless {@code dryRun}, applies the migration.
     *
     * @param mappings {@code rename} (old → new), {@code drop} (a list), {@code default} (name →
     *                 value); each optional, and an empty request is a no-op rather than an error
     */
    @Transactional
    public Plan migrate(String typeName, JsonNode mappings, boolean dryRun) {
        ShPostType postType = shPostTypeRepository.findByName(typeName);
        if (postType == null) {
            throw new ShPostTypeAPI.UnknownPostTypeException(typeName, declaredNames());
        }
        Map<String, String> renames = stringMap(mappings, "rename");
        List<String> drops = strings(mappings, "drop");
        JsonNode defaults = mappings == null ? null : mappings.get("default");

        Map<String, Integer> renamed = new LinkedHashMap<>();
        Map<String, Integer> dropped = new LinkedHashMap<>();
        Map<String, Integer> defaulted = new LinkedHashMap<>();
        List<ShPostUnified> rows = rowsOf(postType);
        List<PlannedWrite> writes = new ArrayList<>();
        List<String> collisions = new ArrayList<>();

        for (ShPostUnified post : rows) {
            JsonNode read = dataOf(post);
            if (read == null || !read.isObject()) {
                continue;
            }
            ObjectNode data = (ObjectNode) read;
            ObjectNode next = data.deepCopy();
            boolean changed = false;

            for (Map.Entry<String, String> rename : renames.entrySet()) {
                if (!next.has(rename.getKey())) {
                    continue;
                }
                if (next.has(rename.getValue()) && !next.get(rename.getValue()).isNull()) {
                    collisions.add(post.getId() + ": " + rename.getKey() + " → " + rename.getValue());
                    continue;
                }
                next.set(rename.getValue(), next.get(rename.getKey()));
                next.remove(rename.getKey());
                renamed.merge(rename.getKey(), 1, Integer::sum);
                changed = true;
            }
            for (String drop : drops) {
                if (next.has(drop)) {
                    next.remove(drop);
                    dropped.merge(drop, 1, Integer::sum);
                    changed = true;
                }
            }
            if (defaults != null && defaults.isObject()) {
                for (String name : defaults.propertyNames()) {
                    // Only where the field is absent or empty. A default that overwrote a value
                    // somebody typed would be the migration deleting content to satisfy a rule the
                    // content already satisfied.
                    JsonNode current = next.get(name);
                    if (current == null || current.isNull()
                            || (current.isString() && current.asString().isEmpty())) {
                        next.set(name, defaults.get(name));
                        defaulted.merge(name, 1, Integer::sum);
                        changed = true;
                    }
                }
            }
            if (changed) {
                writes.add(new PlannedWrite(post, next.toString()));
            }
        }

        if (!collisions.isEmpty()) {
            throw new RenameCollisionException(typeName, collisions);
        }

        // The rows a real run would rewrite, named — so the caller can ask the ACL about exactly
        // those, before anything is written, rather than about the whole type.
        List<String> postIds = writes.stream().map(write -> write.post().getId()).toList();
        Plan plan = new Plan(postType.getName(), writes.size(), postIds,
                renamed, dropped, defaulted);
        if (dryRun) {
            return plan;
        }
        // Applied only after every row was read and no collision was found, so a migration never
        // leaves a type half in one shape and half in another.
        for (PlannedWrite write : writes) {
            write.post().setData(write.data());
            shObjectReferenceService.reindex(shPostUnifiedRepository.save(write.post()));
        }
        return plan;
    }

    private record PlannedWrite(ShPostUnified post, String data) {
    }

    /** Two values, one key, and no rule that is not silent data loss half the time. */
    public static class RenameCollisionException extends ShTeachingException {
        public RenameCollisionException(String typeName, List<String> collisions) {
            super(ShProblem.badRequest("rename-collision",
                    "This rename would land on a key that already holds a value.")
                    .withDetail(collisions.size() + " row(s) of `" + typeName + "` hold both the "
                            + "old field and the new one. Both values are somebody's content, and "
                            + "the two rules that would resolve it — the old wins, the new wins — "
                            + "are each silent data loss half the time.")
                    .withAllowed(collisions.stream().limit(20).toList())
                    .withFix("Drop the target field first if it is the one nobody meant to keep "
                            + "(`drop`), or rename to a name nothing uses.")
                    .withExample("{\"op\":\"post-type.migrate\",\"type\":\"Article\","
                            + "\"data\":{\"drop\":[\"summary\"],"
                            + "\"rename\":{\"subtitle\":\"summary\"}}}"));
        }
    }

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
            // Left exactly as it is, and reported by `check` as unreadable. Rewriting a document
            // this cannot parse is the one thing a migration must never attempt.
            return null;
        }
    }

    private static Map<String, String> stringMap(JsonNode node, String name) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode block = node == null ? null : node.get(name);
        if (block != null && block.isObject()) {
            for (String key : block.propertyNames()) {
                JsonNode value = block.get(key);
                if (value != null && value.isString()) {
                    out.put(key, value.asString());
                }
            }
        }
        return out;
    }

    private static List<String> strings(JsonNode node, String name) {
        List<String> out = new ArrayList<>();
        JsonNode block = node == null ? null : node.get(name);
        if (block != null && block.isArray()) {
            block.forEach(item -> {
                if (item.isString()) {
                    out.add(item.asString());
                }
            });
        }
        return out;
    }

    private List<String> declaredNames() {
        return shPostTypeRepository.findAll().stream()
                .map(ShPostType::getName)
                .filter(name -> name != null && !name.isBlank())
                .sorted()
                .toList();
    }
}
