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
import com.viglet.shio.api.staticfile.ShAssetResponseHeaders;
import com.viglet.shio.api.staticfile.ShUploadPolicy;
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
    /** SH951 — the same verdict the upload path takes, asked of bytes that are already stored. */
    private final ShUploadPolicy shUploadPolicy;

    public ShAssetsTool(ShAgentReadService shAgentReadService, ShUploadPolicy shUploadPolicy) {
        this.shAgentReadService = shAgentReadService;
        this.shUploadPolicy = shUploadPolicy;
    }

    @Override
    public String name() {
        return "shio_assets";
    }

    @Override
    public String description() {
        return "List the uploaded files (images, documents) you can reference, with the public "
                + "URL of each. To reference one from a File field, use the asset's address. "
                // SH951 traded the closing rationale for the two flags, and the budget is why: this
                // payload is re-sent every turn, so it may carry what a caller DECIDES on and not
                // why the design is what it is. That base64 through a tool call is the most
                // expensive way to move bytes is in this class's javadoc, where it costs nothing
                // per turn; "POST multipart to this URL" is the whole of what an agent acts on.
                + "Uploading is not a tool — POST multipart to /api/v2/staticfile/upload. "
                + "A row flagged `unsafe` is of a type this instance now refuses; `sanitized` "
                + "means its stored bytes were cleaned.";
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
                // SH951 — SANITIZED alongside FILE, because "these are not the bytes that were
                // uploaded" is a fact about the asset an agent is choosing between others.
                List.of(ShSystemPostTypeAttr.FILE, ShSystemPostTypeAttr.SANITIZED), true);
        for (ShAgentDoc doc : docs) {
            String fileName = fileName(doc);
            boolean sanitized = flagOf(doc, ShSystemPostTypeAttr.SANITIZED);
            out.append(ShTerse.line("asset")
                    .word(doc.address())
                    .attr("file", fileName)
                    .attr("url", fileName == null ? null : "/file_source/" + doc.id() + "/" + fileName)
                    // Both are `flag`s, so they cost nothing on the ordinary asset — which is every
                    // asset on a healthy instance, and the reason this is affordable at all (P3).
                    .flag("sanitized", sanitized)
                    .flag("unsafe", isUnsafe(fileName, sanitized))
                    .build());
        }
        return out.toString();
    }

    /**
     * Bytes this instance would not accept today, and nothing has cleaned (SH951).
     *
     * <p>The policy is asked about what is <em>already stored</em>, which is the question an upload
     * check cannot answer: an asset uploaded before the policy existed, or before a policy was
     * tightened, is still sitting there. {@code DENY} is unsafe outright; {@code SANITIZE} is
     * unsafe only where the post does not say it was sanitized, because that is exactly the SVG
     * that predates the cleaning.</p>
     *
     * <p>An asset with no file name cannot be classified and is not guessed at — reporting it as
     * safe and reporting it as unsafe are both claims nothing here can make.</p>
     */
    private boolean isUnsafe(String fileName, boolean sanitized) {
        if (fileName == null) {
            return false;
        }
        String contentType = ShAssetResponseHeaders.contentTypeByName(fileName);
        return switch (shUploadPolicy.verdictFor(contentType)) {
            case DENY -> true;
            case SANITIZE -> !sanitized;
            case ALLOW -> false;
        };
    }

    private static boolean flagOf(ShAgentDoc doc, String key) {
        JsonNode value = doc.data() == null ? null : doc.data().get(key);
        return value != null && value.asBoolean(false);
    }

    private static String fileName(ShAgentDoc doc) {
        JsonNode value = doc.data() == null ? null : doc.data().get(ShSystemPostTypeAttr.FILE);
        return value == null || value.isNull() ? null
                : (value.isString() ? value.asString() : value.toString());
    }
}
