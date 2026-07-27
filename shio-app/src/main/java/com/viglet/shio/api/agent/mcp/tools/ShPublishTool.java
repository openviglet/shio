/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.viglet.shio.api.agent.mcp.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.shio.api.agent.ShAgentBatchService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.dto.ShAgentBatch;
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.api.error.ShProblem;
import com.viglet.shio.api.error.ShTeachingException;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_publish} (SH83) — the only call that changes what visitors see.
 *
 * <h2>Why it is its own tool when {@code shio_write} can already do it</h2>
 *
 * <p>Because "never surprise the human" (P7) is enforced by making the consequential
 * act <b>separately nameable</b>. Publishing hidden inside a general write op is a
 * thing an agent does incidentally; publishing as its own tool is a thing it decides
 * to do — and, when SH107 lands, a thing a token can be denied without denying writes.
 * The redundancy is the feature.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShPublishTool implements ShMcpTool {

    private final ShAgentBatchService shAgentBatchService;

    public ShPublishTool(ShAgentBatchService shAgentBatchService) {
        this.shAgentBatchService = shAgentBatchService;
    }

    @Override
    public String name() {
        return "shio_publish";
    }

    @Override
    public String description() {
        return "Make drafts live, or take live posts down (unpublish=true). This is the only "
                + "call that changes what visitors see — everything you write before it is "
                + "invisible. Atomic across all the addresses you pass; re-publishing unchanged "
                + "content reports noop.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "address", ShMcpTool.strings("One or more post addresses to publish."),
                "unpublish", ShMcpTool.bool("Take them down instead."),
                "dryRun", ShMcpTool.bool("Report what would change and do nothing.")),
                List.of("address"));
    }

    @Override
    public String call(JsonNode arguments) {
        List<String> addresses = ShMcpArgs.strings(arguments, "address");
        if (addresses.isEmpty()) {
            throw new NothingToPublishException();
        }
        String op = ShMcpArgs.bool(arguments, "unpublish")
                ? ShAgentBatchService.POST_UNPUBLISH
                : ShAgentBatchService.POST_PUBLISH;
        List<ShAgentBatch.Op> ops = addresses.stream()
                .map(address -> new ShAgentBatch.Op(op, address, null, null, null))
                .toList();
        return ShAgentReadTerse.changes(shAgentBatchService.run(
                new ShAgentBatch(ShMcpArgs.bool(arguments, "dryRun"), ops),
                false,
                ShAgentSessionContext.actor()));
    }

    /** A 400 for a publish with no target. */
    public static class NothingToPublishException extends ShTeachingException {

        public NothingToPublishException() {
            super(ShProblem.badRequest("nothing-to-publish", "shio_publish needs an address")
                    .withDetail("Pass the post addresses to publish; there is deliberately no "
                            + "publish-everything form.")
                    .withFix("List the addresses, or find them first with shio_find.")
                    .withExample("{\"address\":[\"post:Blog/blog/hello-world\"]}"));
        }
    }
}
