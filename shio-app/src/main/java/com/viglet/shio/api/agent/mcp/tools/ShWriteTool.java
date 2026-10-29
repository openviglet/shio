/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.ShAgentProofService;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_write} (SH83) — the atomic, addressed write.
 *
 * <p>A pure shape over {@code POST /api/v2/agent/batch} (SH81): it unpacks the
 * arguments and renders the change set. Every rule that matters — atomicity, the
 * pending set behind {@code dryRun}, the ACL and quota checks, refusing a lenient
 * address — lives in the service, where the REST caller gets it too. If this class ever
 * grows a decision, that decision is in the wrong place.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShWriteTool implements ShMcpTool {

    /**
     * The op fields this tool declares, and so the only ones it passes on (SH992).
     *
     * <p>One declaration serves the schema and the refusal in {@link #call}, so the two cannot name
     * different sets. Map.ofEntries: eleven properties is one past what Map.of takes. The server
     * canonicalises the order, so nothing reads this one.</p>
     */
    private static final Map<String, Object> OP_FIELDS = Map.ofEntries(
            // No description: the enum is the whole of what it says.
            Map.entry("op", Map.of("type", "string", "enum", ShAgentBatchService.OPS)),
            Map.entry("address", ShMcpTool.string("post:<site>/<url> or folder:<site>/<path>.")),
            Map.entry("type", ShMcpTool.string("Post-type name; required to create.")),
            // SH175: an argument whose requirement depends on another's value has to say so on
            // itself. `type` did and `folder` did not, so the most common write on the surface — the
            // first create — paid a round trip to learn it, and the schema is the one document a
            // model reads before calling.
            Map.entry("folder", ShMcpTool.string("Required to create; post.move's destination.")),
            // SH987 trimmed the parenthetical: a move's destination is an address, and the grammar
            // is two lines above.
            Map.entry("to", ShMcpTool.string("A move's new address.")),
            Map.entry("data", ShMcpTool.object("Field values by name.")),
            // SH342 — the agent-native form of scheduled publishing, which the manifest advertised
            // while only an id-keyed console route could reach it. Funded by trimming shio_apply's
            // description rather than by raising the ceiling, which is the order agents.md sets.
            // SH992 — post.unpublish reads it too, in the same characters.
            Map.entry("when", ShMcpTool.string("(un)publish: ISO-8601 instant; omit for now.")),
            /*
             * SH1073 — read since SH952 and declared nowhere, so no MCP client could learn they
             * exist. Each says "on create" because that is the whole contract: an upsert that finds
             * the post ignores all three, and an agent sending a locale to relocalize a live page
             * would otherwise read success.
             */
            Map.entry("locale", ShMcpTool.string("On create: its locale.")),
            Map.entry("translationOf", ShMcpTool.string("On create: what it translates.")),
            Map.entry("url", ShMcpTool.string("On create: the URL as spelled.")),
            // Useless without its source, which is why shio_read gained `digest` in the same change
            // rather than a later one.
            Map.entry("expect", ShMcpTool.string("shio_read's digest; refused if it changed.")),
            // SH987 — which version post.rollback puts back. Beside `expect` because it is the same
            // category: both name a version rather than declaring content.
            Map.entry("revision", ShMcpTool.integer("post.rollback: which revision to put back.")));

    private final ShAgentBatchService shAgentBatchService;
    private final ShAgentProofService shAgentProofService;

    public ShWriteTool(ShAgentBatchService shAgentBatchService,
                       ShAgentProofService shAgentProofService) {
        this.shAgentBatchService = shAgentBatchService;
        this.shAgentProofService = shAgentProofService;
    }

    @Override
    public String name() {
        return "shio_write";
    }

    @Override
    public String description() {
        // SH137 added folder.move to the enum and a second verb to explain, which took the
        // list 3 tokens over its ceiling. Trimmed rather than raised (P3: lean the payload
        // first) — every sentence here now carries a decision the model has to make, and the
        // two move verbs are stated together because they are one rule.
        // SH1073 trimmed it again, to fund the four op fields below: the list of verbs went, because
        // the `op` enum states them one line later, and nothing that informs a decision did.
        // SH1277 trimmed it once more, to fund `folder.acl` in the enum: same three decisions, fewer
        // words.
        return "N addressed ops, all-or-nothing; no id needed. Rename or relocate with "
                + "post.move / folder.move, never upsert-then-delete, which orphans the page or "
                + "empties the folder. Writes stay DRAFT until published. dryRun=true first.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "ops", Map.of(
                        "type", "array",
                        "description", "Applied in order.",
                        "items", Map.of(
                                "type", "object",
                                "required", List.of("op", "address"),
                                "properties", OP_FIELDS)),
                "dryRun", ShMcpTool.bool("Report what would change; write nothing."),
                "confirm", ShMcpTool.string("dryRun's token; required to delete."),
                // SH157: the closing turn of the write-then-check loop, as an argument.
                // ~15 tokens of schema against a whole round trip, and the report it returns
                // is scoped to what changed rather than to the site.
                "verify", ShMcpTool.bool("Lint what this run touched.")),
                List.of("ops"));
    }

    @Override
    public String call(JsonNode arguments) {
        JsonNode ops = ShMcpArgs.node(arguments, "ops");
        if (ops == null || !ops.isArray() || ops.isEmpty()) {
            throw new MissingOpsException();
        }
        // SH992 — a field this tool does not declare is refused rather than dropped. `cancel` made
        // that urgent: shio://ops lists it for post.publish, the schema here has no room for it, and
        // a publish that lost its `cancel` on the way would go live instead of standing down.
        ops.forEach(op -> op.propertyNames().stream()
                .filter(key -> !OP_FIELDS.containsKey(key))
                .findFirst()
                .ifPresent(key -> {
                    throw new UndeclaredOpFieldException(key, OP_FIELDS.keySet());
                }));
        List<ShAgentBatch.Op> parsed = new ArrayList<>();
        // SH952 — the ELEVEN-field form. This used to call the six-field constructor, whose own
        // javadoc says it is for the ops that never schedule, so `when` arrived as null on the one
        // surface that advertised it: a publish dated for next Monday went live now and reported
        // success. `expect`, `locale`, `translationOf` and `url` were dropped the same way — the
        // REST batch deserialises all eleven, which is why nothing looked wrong from the console.
        ops.forEach(op -> parsed.add(new ShAgentBatch.Op(
                ShMcpArgs.string(op, "op"),
                ShMcpArgs.string(op, "address"),
                ShMcpArgs.string(op, "type"),
                ShMcpArgs.string(op, "folder"),
                ShMcpArgs.string(op, "to"),
                ShMcpArgs.node(op, "data"),
                ShMcpArgs.string(op, "when"),
                ShMcpArgs.string(op, "locale"),
                ShMcpArgs.string(op, "translationOf"),
                ShMcpArgs.string(op, "url"),
                ShMcpArgs.string(op, "expect"),
                ShMcpArgs.integer(op, "revision"))));

        return ShAgentReadTerse.changes(shAgentProofService.attach(
                shAgentBatchService.run(
                        new ShAgentBatch(ShMcpArgs.bool(arguments, "dryRun"), List.copyOf(parsed),
                                ShMcpArgs.string(arguments, "confirm")),
                        false,
                        // The agent session id travels into the SH51 audit here, which is what
                        // makes "show me what this run changed" answerable later (SH109).
                        ShAgentSessionContext.actor()),
                ShMcpArgs.bool(arguments, "verify")));
    }

    /**
     * SH992 — an op field shio_write does not declare.
     *
     * <p>Dropping it ran the op without it, which reads as success and is the SH952 defect with the
     * direction reversed. `cancel` is named because it is the likeliest: the op vocabulary lists it,
     * and it lives on shio_publish, since this schema is at its ceiling.</p>
     */
    public static class UndeclaredOpFieldException extends ShTeachingException {

        public UndeclaredOpFieldException(String key, java.util.Collection<String> declared) {
            super(ShProblem.badRequest("undeclared-op-field",
                            "`" + key + "` is not an op field shio_write takes")
                    .withDetail("A field this tool does not declare would be dropped, and the op "
                            + "would run without it.")
                    .withAllowed(new java.util.TreeSet<>(declared))
                    .withDidYouMeanFrom(key)
                    .withFix("cancel".equals(key)
                            ? "Clear a schedule with shio_publish {\"cancel\":true}, or POST "
                                    + "/api/v2/agent/batch, which takes `cancel` on the op."
                            : "Drop the field, or use one of the allowed names.")
                    .withExample("cancel".equals(key)
                            ? "{\"address\":[\"post:Blog/blog/hello\"],\"cancel\":true}"
                            : "{\"ops\":[{\"op\":\"post.publish\",\"address\":\"post:Blog/blog/hello\"}]}"));
        }
    }

    /** A 400 that shows the smallest working call rather than restating the schema. */
    public static class MissingOpsException extends ShTeachingException {

        public MissingOpsException() {
            super(ShProblem.badRequest("missing-ops", "shio_write needs at least one op")
                    .withDetail("`ops` must be a non-empty array.")
                    .withAllowed(ShAgentBatchService.OPS)
                    .withFix("Pass the operations you want applied; they run in order and commit "
                            + "together.")
                    .withExample("{\"ops\":[{\"op\":\"post.upsert\","
                            + "\"address\":\"post:Blog/blog/hello\",\"type\":\"Article\","
                            + "\"folder\":\"/blog\",\"data\":{\"headline\":\"Hello\"}}]}"));
        }
    }
}
