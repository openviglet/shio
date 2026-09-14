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
        return "N addressed ops, all-or-nothing; no id needed. Rename or relocate with "
                + "post.move / folder.move, never upsert-then-delete: that orphans the page, or takes "
                + "the folder's contents. Writes land in DRAFT until published. Send dryRun=true "
                + "first.";
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
                                // Map.ofEntries: eleven properties is one past what Map.of takes.
                                // The server canonicalises the order, so nothing reads this one.
                                "properties", Map.ofEntries(
                                        // No description: the enum is the whole of what it says.
                                        Map.entry("op", Map.of("type", "string",
                                                "enum", ShAgentBatchService.OPS)),
                                        Map.entry("address", ShMcpTool.string(
                                                "post:<site>/<friendly-url> or folder:<site>/<path>.")),
                                        Map.entry("type", ShMcpTool.string(
                                                "Post-type name; required to create a post.")),
                                        // SH175: an argument whose requirement depends on
                                        // another's value has to say so on itself. `type` did
                                        // and `folder` did not, so the most common write on the
                                        // surface — the first create — paid a round trip to
                                        // learn it, and the schema is the one document a model
                                        // reads before calling.
                                        Map.entry("folder", ShMcpTool.string(
                                                "Folder path: required to create a post; "
                                                        + "post.move's destination.")),
                                        Map.entry("to", ShMcpTool.string(
                                                "A move's new address (a folder's whole name "
                                                        + "chain).")),
                                        Map.entry("data", ShMcpTool.object("Field values by name.")),
                                        // SH342 — the agent-native form of scheduled publishing,
                                        // which the manifest advertised while only an id-keyed
                                        // console route could reach it. Funded by trimming
                                        // shio_apply's description rather than by raising the
                                        // ceiling, which is the order agents.md sets.
                                        Map.entry("when", ShMcpTool.string(
                                                "post.publish: ISO-8601 instant to schedule; omit "
                                                        + "for now.")),
                                        /*
                                         * SH1073 — read since SH952 and declared nowhere, so no MCP
                                         * client could learn they exist. Each says "on create"
                                         * because that is the whole contract: an upsert that finds
                                         * the post ignores all three, and an agent sending a locale
                                         * to relocalize a live page would otherwise read success.
                                         */
                                        Map.entry("locale", ShMcpTool.string("On create: its locale.")),
                                        Map.entry("translationOf", ShMcpTool.string(
                                                "On create: the post this translates.")),
                                        Map.entry("url", ShMcpTool.string(
                                                "On create: URL as the source spells it.")),
                                        // Useless without its source, which is why shio_read gained
                                        // `digest` in the same change rather than a later one.
                                        Map.entry("expect", ShMcpTool.string(
                                                "shio_read's digest; refused if the post changed."))))),
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
                ShMcpArgs.string(op, "expect"))));

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
