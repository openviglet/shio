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
import com.viglet.shio.api.agent.ShAgentSessionContext;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.blueprint.ShBlueprintService;
import com.viglet.shio.blueprint.ShBlueprintWriteService;
import com.viglet.shio.blueprint.ShBlueprintTerse;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_apply} (SH83, extended by SH93) — declare what a section should be and
 * converge to it, or apply a whole ready-made one.
 *
 * <p>Shape over SH82's plan/apply and, since Block J, over SH93's blueprint apply. The
 * one thing this tool adds over {@code shio_write} is <b>re-runnability</b>, and the
 * description says so, because that is the decision the agent actually has to make
 * between the two.</p>
 *
 * <h2>Why the blueprint verb lives here rather than in a tool of its own</h2>
 *
 * <p>Because it is the same decision. An agent about to build a blog either declares the
 * pages itself or asks for the package that already declares them; both answers converge
 * an instance towards a stated end state, both return a plan first, and both are safe to
 * re-run. A separate {@code shio_blueprint} tool would cost every session another schema
 * (P3) to express a branch this one already has — and the branch it would express is
 * "which tool", which is exactly the question the model should not have to answer.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShApplyTool implements ShMcpTool {

    private final ShAgentDesiredStateService shAgentDesiredStateService;
    private final ShBlueprintService shBlueprintService;

    public ShApplyTool(ShAgentDesiredStateService shAgentDesiredStateService,
                       ShBlueprintService shBlueprintService) {
        this.shAgentDesiredStateService = shAgentDesiredStateService;
        this.shBlueprintService = shBlueprintService;
    }

    @Override
    public String name() {
        return "shio_apply";
    }

    @Override
    public String description() {
        // SH342 trimmed this from 457 to fund a `when` on post.publish, which is the order
        // agents.md sets for this tool: "the answer there is a trim, not a raise". Every
        // decision-changing fact is kept — idempotent, the blueprint verb, dryRun and changed=0 —
        // and what went is the sentence explaining *why* idempotence is useful, which a caller
        // that has read "applying twice changes nothing" does not need spelled out.
        return "Declare what a site section should contain and make it so. Unlike shio_write "
                + "this is a statement, not a script: applying the same document twice changes "
                + "nothing, so it is safe to re-send after a compaction. Set `blueprint` to apply "
                + "a ready-made section from shio://blueprints instead of declaring one. "
                + "dryRun=true shows the diff; changed=0 means no drift.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name. Every path below is relative to it."),
                "blueprint", ShMcpTool.string(
                        "Blueprint name; applies that package instead of the document below."),
                "params", ShMcpTool.object("The blueprint's parameter values."),
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
                // SH150's `moves` is not here either, and for once the budget argument and the
                // design argument agree completely: a `moves` array of from/to/folder objects is
                // the largest thing that could be added to the largest tool on the surface, and
                // `shio_write` already carries `post.move` at zero marginal cost — it is one
                // value in an op enum that exists. A rename over MCP is a `shio_write` call.
                "prune", ShMcpTool.bool(
                        "Delete posts inside the declared folders that this document does not "
                                + "declare. Requires `folders`."),
                "dryRun", ShMcpTool.bool("Report the diff and write nothing."),
                "confirm", ShMcpTool.string("dryRun's token; required to delete.")),
                List.of("site"));
    }

    @Override
    public String call(JsonNode arguments) {
        String actor = ShAgentSessionContext.actor();
        boolean dryRun = ShMcpArgs.bool(arguments, "dryRun");
        String site = ShMcpArgs.string(arguments, "site");
        String blueprint = ShMcpArgs.string(arguments, "blueprint");

        if (blueprint != null && !blueprint.isBlank()) {
            JsonNode params = ShMcpArgs.node(arguments, "params");
            // SH151's `createSite` and SH152's `mergePostTypes` are deliberately not arguments
            // here. Either one puts this tool over the per-tool ceiling, and `tools/list` is
            // re-sent on every turn of every session (P3) — so the cost would be paid by every
            // session including the ones that never create a site. Neither capability is out of
            // reach: `shio_write` takes `site.upsert` for free (it is one more value in an enum
            // that already existed), and the missing-site error names it. The reconciliation flag
            // is a decision a person makes once, over REST or the CLI.
            var options = ShBlueprintWriteService.Options.NONE;
            return ShBlueprintTerse.applied(dryRun
                    ? shBlueprintService.plan(blueprint, site, params, actor, options)
                    : shBlueprintService.apply(blueprint, site, params, actor, options));
        }
        ShAgentDesiredState desired = new ShAgentDesiredState(site, folders(arguments),
                posts(arguments), ShMcpArgs.bool(arguments, "prune"),
                ShMcpArgs.string(arguments, "confirm"));
        return ShAgentReadTerse.changes(dryRun
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
