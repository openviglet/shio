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

import com.viglet.shio.api.agent.ShAgentReadService;
import com.viglet.shio.api.agent.ShTerse;
import com.viglet.shio.api.agent.dto.ShAgentDoc;
import com.viglet.shio.api.agent.dto.ShAgentFound;
import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.post.type.ShSystemPostType;
import com.viglet.shio.post.type.ShSystemPostTypeAttr;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_assets} (SH83) — the binaries an agent can reference.
 *
 * <h2>Read-only, and saying so is the point</h2>
 *
 * <p>The question this answers is "what images do I already have, and what URL do I put
 * in this field" — which is the question that actually blocks an agent mid-page. It
 * cannot upload: MCP tool arguments are JSON, so a real upload would mean base64-ing a
 * binary through the model's context, which is the single most expensive way to move
 * bytes ever devised. Uploads stay on {@code POST /api/v2/staticfile/upload}
 * (multipart), and the description says so rather than leaving the agent to discover
 * the absence.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShAssetsTool implements ShMcpTool {

    private final ShAgentReadService shAgentReadService;

    public ShAssetsTool(ShAgentReadService shAgentReadService) {
        this.shAgentReadService = shAgentReadService;
    }

    @Override
    public String name() {
        return "shio_assets";
    }

    @Override
    public String description() {
        return "List the uploaded files (images, documents) you can reference, with the public "
                + "URL of each. To reference one from a File field, use the asset's address. "
                + "Uploading is not a tool — POST multipart to /api/v2/staticfile/upload, "
                + "because base64 through a tool call is the most expensive way to move bytes.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "folder", ShMcpTool.string("Folder path to look under, including subfolders."),
                "q", ShMcpTool.string("Match against the file name."),
                "limit", ShMcpTool.integer("Maximum rows (default 25).")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        // SH727 — an instance that has never held an upload declares no `File` type, and `find`
        // refuses a type it cannot resolve. That refusal is right for a caller who typed a name and
        // wrong here: nobody typed this one. The true answer to "what files does this site have" is
        // none, and the message it used to give — "create a new type with PUT /api/v2/post-type/
        // {name}" — points at a system definition whose attributes it does not even list, so a
        // caller that followed it would create the wrong `File`. It is the first assets call of
        // every new install, which is exactly the caller least able to tell a defect from a rule.
        if (!shAgentReadService.hasType(ShSystemPostType.FILE)) {
            return ShTerse.line("assets").word(0).build();
        }
        ShAgentFound found = shAgentReadService.find(
                ShMcpArgs.string(arguments, "site"),
                ShSystemPostType.FILE,
                ShMcpArgs.string(arguments, "folder"),
                ShMcpArgs.string(arguments, "q"),
                ShMcpArgs.integer(arguments, "limit"),
                // Ids are on here, against the SH121 default, because a file's public
                // URL is /file_source/<postId>/<name> — the id is not decoration, it is
                // half the answer the tool exists to give.
                true);

        StringBuilder out = new StringBuilder(ShTerse.line("assets")
                .word(found.count())
                .flag("truncated", found.truncated())
                .build());
        if (found.results().isEmpty()) {
            return out.toString();
        }
        List<ShAgentDoc> docs = shAgentReadService.read(
                found.results().stream().map(ShAgentFound.Row::address).toList(),
                List.of(ShSystemPostTypeAttr.FILE), true);
        for (ShAgentDoc doc : docs) {
            String fileName = fileName(doc);
            out.append(ShTerse.line("asset")
                    .word(doc.address())
                    .attr("file", fileName)
                    .attr("url", fileName == null ? null : "/file_source/" + doc.id() + "/" + fileName)
                    .build());
        }
        return out.toString();
    }

    private static String fileName(ShAgentDoc doc) {
        JsonNode value = doc.data() == null ? null : doc.data().get(ShSystemPostTypeAttr.FILE);
        return value == null || value.isNull() ? null
                : (value.isString() ? value.asString() : value.toString());
    }
}
