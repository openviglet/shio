/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { claudeCommand, mcpEndpoint } from "../src/lib/agent-connect.ts"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1421 — the console tells a newcomer how to connect a coding agent.
 *
 * The agent is the operator the product is designed around, and first use offered only console
 * paths: the endpoint, the key and the command had to be found in the docs and put together by
 * hand. The first-use screen now carries a fourth choice that issues the key, prints the command and
 * says when the agent arrives.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const read = (...parts) => stripComments(readFileSync(join(SRC, ...parts), "utf8"))

test("SH1421: the MCP endpoint is beside /api, on the backend's origin or the page's own", () => {
  assert.equal(mcpEndpoint("/api", "https://cms.example.org"), "https://cms.example.org/mcp")
  assert.equal(mcpEndpoint(undefined, "https://cms.example.org"), "https://cms.example.org/mcp")
  // A built console: VITE_API_URL is empty, so the base is the bare "/api".
  assert.equal(mcpEndpoint("/api", "http://localhost:2710"), "http://localhost:2710/mcp")
  // The Vite dev server: the API lives on the backend's port, and so does /mcp.
  assert.equal(mcpEndpoint("http://localhost:2710/api", "http://localhost:5173"), "http://localhost:2710/mcp")
})

test("SH1421: the command registers this instance over HTTP with the key in the header", () => {
  assert.equal(claudeCommand("http://localhost:2710/mcp", "abc123"),
    "claude mcp add --transport http shio http://localhost:2710/mcp --header \"Key: abc123\"")
})

test("SH1421: the panel issues an AGENT key that cannot publish, and waits for a run since then", () => {
  const dialog = read("app", "content", "connect-agent.dialog.tsx")
  assert.match(dialog, /scope: "AGENT"/, "SH1421: the connect panel no longer issues an AGENT-scope key")
  assert.match(dialog, /mayPublish: false/,
    "SH1421: the connect panel's key may publish, so the agent's work skips the curator (P7)")
  assert.match(dialog, /"\/v2\/agent\/runs", \{ signal, params: \{ since: issuedAt \} \}/,
    "SH1421: the panel no longer asks whether a run started after the key was issued")
  assert.match(dialog, /claude plugin install shio/, "SH1421: the plugin install is no longer named as the alternative")
})

test("SH1421: the first-use screen offers the agent to an administrator, after the three console paths", () => {
  const start = read("app", "content", "first-site-start.tsx")
  assert.match(start, /\{admin && \(\s*<li className="flex">\s*<button[^>]*data-start-choice="agent"/,
    "SH1421: the first-use screen lost its agent choice, or offers it to a curator who cannot issue a key")
  assert.ok(start.indexOf("data-start-choice=\"agent\"") > start.indexOf("id: \"import\""),
    "SH1421: the agent choice moved ahead of the console paths")
})

test("SH1421: every connect-panel string exists in English and Portuguese", () => {
  const keys = ["startAgentTitle", "startAgentDesc", "connectTitle", "connectLead", "connectStepKey",
    "connectStepKeyDesc", "connectIssueKey", "connectKeyIssued", "connectTokenTitle",
    "connectTokenDescription", "connectTokenFailed", "connectStepCommand", "connectCommandWaits",
    "connectCopy", "connectPlugin", "connectStepWait", "connectWaitIdle", "connectWaiting",
    "connectArrived", "connectAnAgent"]
  for (const lang of ["en", "pt"]) {
    const content = JSON.parse(readFileSync(join(SRC, "i18n", "locales", lang, "content.json"), "utf8"))
    for (const key of keys) assert.ok(content[`content.${key}`], `SH1421: content.${key} is missing in ${lang}`)
  }
})
