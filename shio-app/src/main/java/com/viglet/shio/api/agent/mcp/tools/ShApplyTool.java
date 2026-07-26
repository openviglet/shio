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

import com.viglet.shio.api.agent.ShAgentDesiredStateService;
import com.viglet.shio.api.agent.ShAgentReadTerse;
import com.viglet.shio.api.agent.dto.ShAgentDesiredState;
import com.viglet.shio.api.agent.mcp.ShMcpSessionContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_apply} (SH83) — declare what a section should be and converge to it.
 *
 * <p>Shape over SH82's plan/apply. The one thing this tool adds over
 * {@code shio_write} is <b>re-runnability</b>, and the description says so, because
 * that is the decision the agent actually has to make between the two.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShApplyTool implements ShMcpTool {

    private final ShAgentDesiredStateService shAgentDesiredStateService;

    public ShApplyTool(ShAgentDesiredStateService shAgentDesiredStateService) {
        this.shAgentDesiredStateService = shAgentDesiredStateService;
    }

    @Override
    public String name() {
        return "shio_apply";
    }

    @Override
    public String description() {
        return "Declare what a site section should contain and make it so. Unlike shio_write "
                + "this is a statement, not a script: applying the same document twice changes "
                + "nothing the second time, so you can re-send it after a compaction without "
                + "remembering what you already did. Use dryRun=true to see the diff first; a "
                + "plan with changed=0 means there is no drift.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name. Every path below is relative to it."),
                "folders", Map.of(
                        "type", "array",
                        "description", "Folder paths that must exist; intermediates are implicit.",
                        "items", Map.of("type", "string")),
                "posts", Map.of(
                        "type", "array",
                        "description", "The posts that must exist, with the values they must hold.",
                        "items", Map.of(
                                "type", "object",
                                "required", List.of("url"),
                                "properties", Map.of(
                                        "url", ShMcpTool.string(
                                                "Friendly URL — the post's identity in this document."),
                                        "type", ShMcpTool.string("Post-type name; needed to create."),
                                        "folder", ShMcpTool.string("Folder path; needed to create."),
                                        "data", ShMcpTool.object("Field values."),
                                        "publish", ShMcpTool.bool(
                                                "true publishes, false unpublishes; omit to leave "
                                                        + "the live state alone.")))),
                "prune", ShMcpTool.bool(
                        "Delete posts inside the declared folders that this document does not "
                                + "declare. Requires `folders`."),
                "dryRun", ShMcpTool.bool("Report the diff and write nothing.")),
                List.of("site"));
    }

    @Override
    public String call(JsonNode arguments) {
        ShAgentDesiredState desired = new ShAgentDesiredState(
                ShMcpArgs.string(arguments, "site"),
                folders(arguments),
                posts(arguments),
                ShMcpArgs.bool(arguments, "prune"));
        String actor = ShMcpSessionContext.actor();
        return ShAgentReadTerse.changes(ShMcpArgs.bool(arguments, "dryRun")
                ? shAgentDesiredStateService.plan(desired, false, actor)
                : shAgentDesiredStateService.apply(desired, false, actor));
    }

    /**
     * Folders arrive as bare path strings here, not as {@code {"path": …}} objects.
     * The REST document keeps the object form so it can grow fields later; the tool
     * schema does not, because a one-key wrapper is a shape a model gets wrong roughly
     * as often as it gets it right, and there is nothing else to say about a folder yet.
     */
    private static List<ShAgentDesiredState.Folder> folders(JsonNode arguments) {
        return ShMcpArgs.strings(arguments, "folders").stream()
                .map(ShAgentDesiredState.Folder::new)
                .toList();
    }

    private static List<ShAgentDesiredState.Post> posts(JsonNode arguments) {
        JsonNode posts = ShMcpArgs.node(arguments, "posts");
        if (posts == null || !posts.isArray()) {
            return List.of();
        }
        List<ShAgentDesiredState.Post> parsed = new ArrayList<>();
        posts.forEach(post -> parsed.add(new ShAgentDesiredState.Post(
                ShMcpArgs.string(post, "url"),
                ShMcpArgs.string(post, "type"),
                ShMcpArgs.string(post, "folder"),
                ShMcpArgs.node(post, "data"),
                // Read through the raw node, not ShMcpArgs.bool: absent and false mean
                // different things here (leave alone vs unpublish), and collapsing them
                // would unpublish live pages whenever the field was simply omitted.
                publish(post))));
        return List.copyOf(parsed);
    }

    private static Boolean publish(JsonNode post) {
        JsonNode value = post == null ? null : post.get("publish");
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isBoolean() ? value.asBoolean() : Boolean.valueOf(value.asString().trim());
    }
}
