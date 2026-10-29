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
import com.viglet.shio.api.reference.ShObjectReferenceService;
import com.viglet.shio.api.staticfile.ShAssetLoadService;
import com.viglet.shio.api.staticfile.ShAssetResponseHeaders;
import com.viglet.shio.api.staticfile.ShStaticFileService;
import com.viglet.shio.api.staticfile.ShStaticFileUsage;
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
    /** SH1032 — the console's own "used in" service, composed rather than re-queried (P10). */
    private final ShStaticFileService shStaticFileService;
    /** SH1034 — the reverse index, read once for a whole page rather than once per row. */
    private final ShObjectReferenceService shObjectReferenceService;
    /** SH1280 — what rendered pages load, the half of "used" the reference index cannot see. */
    private final ShAssetLoadService shAssetLoadService;

    public ShAssetsTool(ShAgentReadService shAgentReadService, ShUploadPolicy shUploadPolicy,
                        ShStaticFileService shStaticFileService,
                        ShObjectReferenceService shObjectReferenceService,
                        ShAssetLoadService shAssetLoadService) {
        this.shAssetLoadService = shAssetLoadService;
        this.shAgentReadService = shAgentReadService;
        this.shUploadPolicy = shUploadPolicy;
        this.shStaticFileService = shStaticFileService;
        this.shObjectReferenceService = shObjectReferenceService;
    }

    @Override
    public String name() {
        return "shio_assets";
    }

    @Override
    public String description() {
        return "List the uploaded files you can reference, with the public URL of each. Use an "
                + "asset's address from a File field. "
                // SH951 traded the closing rationale for the two flags, and the budget is why: this
                // payload is re-sent every turn, so it may carry what a caller DECIDES on and not
                // why the design is what it is. That base64 through a tool call is the most
                // expensive way to move bytes is in this class's javadoc, where it costs nothing
                // per turn; "POST multipart to this URL" is the whole of what an agent acts on.
                // SH1030 trimmed it again to pay for `after`, on the same rule.
                + "Uploading is not a tool — POST multipart to /api/v2/staticfile/upload. "
                + "`unsafe` is a type this instance now refuses; `sanitized` means its bytes "
                + "were cleaned.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for all you can read."),
                "folder", ShMcpTool.string("Folder path; its subtree too."),
                "q", ShMcpTool.string("Match against the file name."),
                "limit", ShMcpTool.integer("Maximum rows (default 25)."),
                // SH1030 — worth its schema for the reason `after` on shio_find was (SH146):
                // without it a library past the cap is a dead end. An agent choosing an asset
                // from a large media library is exactly the caller that meets one, and a
                // truncated list it cannot continue reads as "there is no such image".
                "after", ShMcpTool.string("Cursor from a truncated result: the next page."),
                // SH1032 — a flag on a call the agent already makes, not a tool of its own. "Where
                // is this used" had one surface, GET /api/v2/staticfile/{id}/usage, and it is asked
                // in exactly one situation: before replacing or deleting an asset, about assets the
                // caller is already listing. A second tool would be paid by every session on every
                // turn (P3) for a question this one is already halfway through answering.
                //
                // OFF BY DEFAULT because it costs tokens on every row, and the default listing is
                // the browse. SH1034 made it cheap on the SERVER — the counts for a whole page are
                // one indexed query, and an unused asset needs nothing further — but a `used` line
                // per row is still payload, which is the reason this stays opt-in rather than
                // becoming free because the query got faster.
                "usage", ShMcpTool.bool("Also say where each asset is used.")),
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
                true,
                ShMcpArgs.string(arguments, "after"));

        StringBuilder out = new StringBuilder(ShTerse.line("assets")
                .word(found.count())
                .flag("truncated", found.truncated())
                // SH1030 — the cursor beside the flag, the way `find` carries it: `truncated`
                // alone tells a caller it is missing rows and not how to get them, which is the
                // dead end SH146 removed one surface over.
                .attr("cursor", found.cursor())
                .build());
        if (found.results().isEmpty()) {
            return out.toString();
        }
        List<ShAgentDoc> docs = shAgentReadService.read(
                found.results().stream().map(ShAgentFound.Row::address).toList(),
                // SH951 — SANITIZED alongside FILE, because "these are not the bytes that were
                // uploaded" is a fact about the asset an agent is choosing between others.
                List.of(ShSystemPostTypeAttr.FILE, ShSystemPostTypeAttr.SANITIZED), true);
        boolean withUsage = ShMcpArgs.bool(arguments, "usage");
        // SH1034 — the counts for the whole page in ONE indexed query, so the common question
        // ("which of these is unused") costs one read rather than one per row. Before the index
        // each row was a table scan; making them indexed reads would have been N-times-cheaper and
        // still N, which is the shape SH1033 exists to stop.
        Map<String, Long> usageCounts = withUsage
                ? shObjectReferenceService.referrerCounts(
                        docs.stream().map(ShAgentDoc::id).toList())
                : Map.of();
        // SH1280 — and what the sites' rendered pages load, so a theme's font is not `used 0`.
        // One render per page of the sites involved, paid only on `usage`, the opt-in it rides.
        ShAssetLoadService.Loads loads = withUsage
                ? shAssetLoadService.loadedByAssets(docs.stream().map(ShAgentDoc::id).toList())
                : null;
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
            if (withUsage) {
                out.append(usageOf(doc.id(), usageCounts.getOrDefault(doc.id(), 0L), loads));
            }
        }
        return out.toString();
    }

    /**
     * Where one asset is referenced, as rows under it (SH1032).
     *
     * <h2>The count first, and why an unused asset says so out loud</h2>
     *
     * <p>{@code used 0} is written rather than omitted, which is the opposite of how the flags
     * above are treated and deliberate: zero is the answer the caller asked for. "Can I delete
     * this" is answered by the absence of referrers, and an absent line is indistinguishable from
     * a line this tool forgot to write — so the one reading that would cause a deletion is the one
     * that must not be inferred from silence (P7).</p>
     *
     * <h2>Draft referrers count</h2>
     *
     * <p>{@link ShStaticFileService#listUsage} reads PUBLISHED then DRAFT and prefers the
     * published row, and the {@code draft} flag carries that distinction through rather than
     * flattening it. An asset used only by a draft is not unused: deleting it breaks a page nobody
     * has published yet, which is the page most likely to be mid-edit.</p>
     */
    private String usageOf(String assetId, long count, ShAssetLoadService.Loads loads) {
        // SH1280 — `loaded` is how many rendered pages request it, with `of` when the server's cap
        // cut the walk: an asset loaded only past the cap would otherwise read as unused.
        StringBuilder out = new StringBuilder(ShTerse.line("used").word(count)
                .attr("loaded", loads.byAsset().getOrDefault(assetId, 0))
                .attr("rendered", loads.complete() ? null : loads.rendered() + "/" + loads.pages())
                .build());
        if (count == 0) {
            // SH1034 — the count came from the page's one batched query, so an unused asset costs
            // nothing further. This is the answer the caller most often wants and it is now free.
            return out.toString();
        }
        for (ShStaticFileUsage row : shStaticFileService.listUsage(assetId)) {
            out.append(ShTerse.line("by")
                    .word(row.postId())
                    .attr("title", row.title())
                    .attr("type", row.postTypeName())
                    .attr("site", row.siteName())
                    .flag("draft", !row.published())
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
