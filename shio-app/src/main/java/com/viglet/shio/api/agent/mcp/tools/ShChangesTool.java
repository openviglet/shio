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
import org.springframework.transaction.annotation.Transactional;

import com.viglet.shio.api.agent.ShAgentChangesService;
import com.viglet.shio.api.agent.ShAgentChangesTerse;
import com.viglet.shio.api.agent.mcp.ShMcpTool;

import tools.jackson.databind.JsonNode;

/**
 * {@code shio_changes} (SH83, made resumable by SH88) — what has happened to this
 * instance's content, and where the reader is in it.
 *
 * <h2>What changed with SH88</h2>
 *
 * <p>This shipped as a <em>window</em> and said so: the last N transitions, with no way to
 * express "everything since I last looked". It now takes {@code since} and returns a
 * {@code cursor}, so the question an agent actually asks at the start of a session — did a
 * human change anything while I was away? — costs one call and cannot silently miss an
 * edit that fell outside a guessed window.</p>
 *
 * <p>The tool is still a <em>shape</em> over the REST surface (law P10): every rule that
 * matters — the keyset cursor, the lag that keeps a concurrent write from landing behind
 * it, and the site scoping — lives in {@link ShAgentChangesService}, which
 * {@code GET /api/v2/agent/changes} calls too. That is also why the SH2 allow-list now
 * applies here: the fix went in one place and both surfaces got it.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3
 */
@Component
public class ShChangesTool implements ShMcpTool {

    private final ShAgentChangesService shAgentChangesService;

    public ShChangesTool(ShAgentChangesService shAgentChangesService) {
        this.shAgentChangesService = shAgentChangesService;
    }

    @Override
    public String name() {
        return "shio_changes";
    }

    @Override
    public String description() {
        return "Content changes: what was created, updated, published or deleted, by whom, and "
                + "from which agent session. Call it with no since= to get a recent window plus a "
                + "cursor; pass that cursor back next time to see only what happened after it.";
    }

    @Override
    public Map<String, Object> inputSchema() {
        return ShMcpTool.schema(Map.of(
                "site", ShMcpTool.string("Site name; omit for every site you can read."),
                "since", ShMcpTool.string("A cursor from a previous call — returns only what "
                        + "happened after it."),
                "limit", ShMcpTool.integer("How many entries (default 50, max 500)."),
                // SH989 — REST's include=fields. After an approve the draft and the live row are one
                // document, so these names are the only description of the run that was accepted.
                "fields", ShMcpTool.bool("Name the fields each update or publish changed.")),
                List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public String call(JsonNode arguments) {
        return ShAgentChangesTerse.render(shAgentChangesService.changes(
                ShMcpArgs.string(arguments, "site"),
                ShMcpArgs.string(arguments, "since"),
                ShMcpArgs.integer(arguments, "limit"),
                false, null,
                ShMcpArgs.bool(arguments, "fields")));
    }
}
