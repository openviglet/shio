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

import com.viglet.shio.api.agent.mcp.ShMcpTool;
import com.viglet.shio.marketplace.ShMarketplaceCatalog;
import com.viglet.shio.marketplace.dto.ShMarketplaceItem;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_marketplace} (SH678) — what this instance could install, without a shell.
 *
 * <h2>Read-only, and that is the design rather than a first slice</h2>
 *
 * <p>The CLI already installs (SH677), so what MCP adds is the half a terminal cannot give a
 * client that has no terminal: <b>discovery</b>. That is also the only half it may add.</p>
 *
 * <p>MCP tools call services <em>in process</em>. An API token reaches {@code /mcp} and is
 * explicitly barred from {@code /api/v2/marketplace} — the refusal names the four namespaces a
 * token authenticates (SH2) — so a tool that installed would hand that token a capability the URL
 * layer denies it. The non-goals say it in one line: <b>the agent path is not a privilege
 * escalation</b>. Installing stays where the credential is the operator's, and the description
 * below says so, because an agent that knows the boundary asks the human instead of retrying.</p>
 *
 * <h2>Why a tool and not an argument on an existing one</h2>
 *
 * <p>An argument on {@code shio_apply} would have been ~20 tokens against this one's ~120, and it
 * was rejected for the reason {@code shio_digest} is separate from {@code shio_verify}: the
 * question is different. "Which packages exist that I have not got" is not "apply this package",
 * and folding it in produces a tool whose output shape depends on which parameter was set.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShMarketplaceTool implements ShMcpTool {

    private final ShMarketplaceCatalog catalog;

    public ShMarketplaceTool(ShMarketplaceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public String name() {
        return "shio_marketplace";
    }

    @Override
    public String description() {
        // Trimmed before the raise, which is this file's order. What went was "Apply an installed
        // package with shio_apply" — shio_apply's own `blueprint` parameter already says it, and a
        // description restating another tool's schema is the defect the ceiling exists to find.
        return "Blueprint packages this instance could install, with their parameters and whether "
                + "each is already here. Read-only — installing needs an operator credential "
                + "(`shio marketplace install`, or the console).";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "id", ShMcpTool.string("One package, in full. Omit for the whole catalogue.")),
                List.of());
    }

    @Override
    public String call(JsonNode arguments) {
        ShMarketplaceCatalog.Catalogue catalogue = catalog.catalogue();
        String id = ShMcpArgs.string(arguments, "id");
        return id == null || id.isBlank() ? list(catalogue) : one(catalogue, id);
    }

    /**
     * One line per package, then where the list came from.
     *
     * <p>Terse for P3's reason — this is a listing an agent skims to choose — and the source line
     * is not decoration: with no catalogue configured the answer is the built-in six and nothing
     * else, which looks identical to a catalogue that returned nothing.</p>
     */
    private static String list(ShMarketplaceCatalog.Catalogue catalogue) {
        StringBuilder out = new StringBuilder();
        for (ShMarketplaceItem item : catalogue.items()) {
            out.append(item.id())
                    .append(item.installed() ? " [installed]" : " [available]")
                    .append(item.builtIn() ? " [built-in]" : "")
                    .append(" ").append(item.version() == null ? "-" : item.version())
                    .append(" — ").append(item.title() == null ? "" : item.title())
                    .append('\n');
        }
        return out.append('\n').append(source(catalogue.source())).toString();
    }

    /** One package, with the parameters a later apply has to satisfy. */
    private static String one(ShMarketplaceCatalog.Catalogue catalogue, String id) {
        ShMarketplaceItem item = catalogue.items().stream()
                .filter(each -> each.id().equals(id)).findFirst().orElse(null);
        if (item == null) {
            return "No package '" + id + "'. Call this tool with no id for what there is.";
        }
        StringBuilder out = new StringBuilder(item.id());
        out.append(' ').append(item.version() == null ? "-" : item.version())
                .append(item.installed() ? " [installed]" : " [available]").append('\n');
        if (item.title() != null) {
            out.append(item.title()).append('\n');
        }
        if (item.description() != null) {
            out.append(item.description()).append('\n');
        }
        if (!item.requires().isEmpty()) {
            out.append("requires: ").append(String.join(", ", item.requires())).append('\n');
        }
        if (item.params() != null && item.params().has("properties")) {
            out.append("params: ")
                    .append(String.join(", ", item.params().get("properties").propertyNames()))
                    .append('\n');
        }
        if (!item.installed()) {
            out.append("install with: shio marketplace install ").append(item.id()).append('\n');
        }
        return out.toString();
    }

    private static String source(ShMarketplaceCatalog.Source source) {
        if (source == null) {
            return "";
        }
        if (ShMarketplaceCatalog.BUILTIN_ONLY.equals(source.state())) {
            return "source: this instance's own packages; no catalogue is configured.";
        }
        if (ShMarketplaceCatalog.UNREACHABLE.equals(source.state())) {
            return "source: " + source.url() + " could not be read — " + source.reason()
                    + " The packages above are this instance's own.";
        }
        return "source: " + source.url()
                + (source.reason() == null ? "" : " (" + source.reason() + ")");
    }
}
