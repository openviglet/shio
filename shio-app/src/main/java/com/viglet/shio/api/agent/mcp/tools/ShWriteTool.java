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
import com.viglet.shio.api.agent.mcp.ShMcpSessionContext;
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

    public ShWriteTool(ShAgentBatchService shAgentBatchService) {
        this.shAgentBatchService = shAgentBatchService;
    }

    @Override
    public String name() {
        return "shio_write";
    }

    @Override
    public String description() {
        return "Create, update or delete content: N operations applied as one transaction, all "
                + "or nothing. Ops are addressed, so you never need an id — folder.upsert, "
                + "post.upsert, post.publish, post.unpublish, folder.delete, post.delete. "
                + "Everything lands in DRAFT; nothing is visible until you publish. Set "
                + "dryRun=true first to see what would change.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "ops", Map.of(
                        "type", "array",
                        "description", "The operations, in order.",
                        "items", Map.of(
                                "type", "object",
                                "required", List.of("op", "address"),
                                "properties", Map.of(
                                        "op", Map.of("type", "string",
                                                "enum", ShAgentBatchService.OPS,
                                                "description", "What to do."),
                                        "address", ShMcpTool.string(
                                                "post:<site>/<friendly-url> or folder:<site>/<path>."),
                                        "type", ShMcpTool.string(
                                                "Post-type name; required only when creating a post."),
                                        "folder", ShMcpTool.string(
                                                "Folder path for a new post; required only when creating."),
                                        "data", ShMcpTool.object(
                                                "Field values keyed by post-type field name.")))),
                "dryRun", ShMcpTool.bool("Report what would change and write nothing.")),
                List.of("ops"));
    }

    @Override
    public String call(JsonNode arguments) {
        JsonNode ops = ShMcpArgs.node(arguments, "ops");
        if (ops == null || !ops.isArray() || ops.isEmpty()) {
            throw new MissingOpsException();
        }
        List<ShAgentBatch.Op> parsed = new ArrayList<>();
        ops.forEach(op -> parsed.add(new ShAgentBatch.Op(
                ShMcpArgs.string(op, "op"),
                ShMcpArgs.string(op, "address"),
                ShMcpArgs.string(op, "type"),
                ShMcpArgs.string(op, "folder"),
                ShMcpArgs.node(op, "data"))));

        return ShAgentReadTerse.changes(shAgentBatchService.run(
                new ShAgentBatch(ShMcpArgs.bool(arguments, "dryRun"), List.copyOf(parsed)),
                false,
                // The agent session id travels into the SH51 audit here, which is what
                // makes "show me what this run changed" answerable later (SH109).
                ShMcpSessionContext.actor()));
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
