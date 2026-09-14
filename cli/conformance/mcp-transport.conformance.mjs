/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH561 — the suite that has an instance finally speaks the transport the product is named for.
 *
 * ## The substitution nobody named
 *
 * `replication.conformance.mjs` removed three: the served source, the running instance, the
 * registry. Of its four scenarios one spawns `src/index.mjs` as a process — SH456, because an exit
 * code is something a module call structurally cannot see — and the other three
 * `import * as convert from "../src/commands/convert.mjs"`.
 *
 * Claude Code uses neither. It spawns `shio mcp` and speaks newline-delimited JSON-RPC, and the
 * layer an import removes is the whole of it: **the tool's declared input schema**, **the name an
 * agent must guess right**, **the framing**, **the `tools/list` payload P3 holds a budget on**, and
 * **the shape of a refusal** an agent has to read to recover. A tool renamed, a required property
 * added, a response grown past its budget — all four pass a run that imports the module the tool
 * wraps.
 *
 * ## What this asserts, and what it deliberately leaves to the module driver
 *
 * Only what the transport adds. The replication chain (`clone → propose → convert`) has **no MCP
 * tool** — it is a CLI path, and the thirteen tools are the content surface — so "the same chain
 * over `tools/call`" is not a thing that exists to assert; saying so is more useful than inventing
 * a scenario that pretends otherwise. What the transport can be wrong about is the surface itself,
 * and that is what is here. The module driver stays: it is faster and it localises a failure, which
 * is what it is for. What it cannot be is evidence about the wire.
 *
 * P1 orders MCP first and the manifest claims thirteen tools. `ShAgentP1ConformanceTest` asks
 * whether the HTTP surface can reach a feature. This asks whether the transport can.
 */
import assert from "node:assert/strict";
import { describe, test } from "node:test";

import { connection, requireInstance } from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/**
 * `mcp.tools.list.total` in `shio-app/src/test/resources/token-budgets.properties`.
 *
 * Stated here rather than read, on purpose: this suite runs against an instance it did not build,
 * so reading the number out of the working tree would compare a live payload with a budget that may
 * not be the one that instance was built from. A constant that disagrees with the properties file
 * is a visible edit; a read that silently compares two versions is not.
 *
 * **A visible edit is only visible if something looks, and nothing did (SH673).** This said 2375,
 * which was right at SH342 and then survived three raises to the properties file alone — SH619 to
 * 2385, SH222 to 2395, SH227 to 2400 — until a payload measuring 2397 sat between the two: inside
 * the budget `ShMcpToolBudgetTest` reads, outside the one stated here. So the gate was red over a
 * cost the project had decided it would pay. `cli/test/budget-restatement.test.mjs` now holds this
 * constant against that key, in `pnpm test`, where both sides are files and no instance is needed.
 */
const TOOLS_LIST_BUDGET_TOKENS = 2820;

/** The rough-and-honest conversion the budget file itself uses: four characters to a token. */
const CHARS_PER_TOKEN = 4;

describe("SH561: the MCP transport, spoken the way Claude Code speaks it", () => {
  let mcp;
  let tools;

  test("initialize: the bridge frames a session over stdio", async () => {
    await requireInstance();
    mcp = await openMcp(connection());

    assert.equal(mcp.initialized?.jsonrpc, "2.0",
      `a JSON-RPC envelope came back off stdout. stderr:\n${mcp.stderr()}`);
    assert.ok(mcp.initialized?.result?.protocolVersion,
      "the server answered the version handshake, which is the one exchange that has to happen "
        + "before any tool can be named");
    assert.ok(!mcp.initialized?.error, `initialize failed: ${JSON.stringify(mcp.initialized?.error)}`);
    assert.match(mcp.stderr(), /bridging stdio to/,
      "and the bridge's own status line went to stderr — one stray line on stdout desynchronises "
        + "a client for the rest of the session, so the channel separation is part of the contract");
  });

  test("tools/list: every tool has the name and the input schema an agent reads", async () => {
    const answer = await mcp.request("tools/list");
    tools = answer?.result?.tools;

    assert.ok(Array.isArray(tools) && tools.length > 0,
      `tools/list returned no tools: ${JSON.stringify(answer)}`);
    console.log(`\n[SH561] ${tools.length} tools over stdio: `
      + `${tools.map((tool) => tool.name).sort().join(", ")}\n`);

    for (const tool of tools) {
      assert.match(tool.name, /^shio_[a-z_.]+$/,
        `a tool name is what an agent has to get right, and it is invisible to a module import: `
          + `${JSON.stringify(tool.name)}`);
      assert.ok(tool.description?.length > 0, `${tool.name} has no description`);
      assert.equal(tool.inputSchema?.type, "object",
        `${tool.name} declares no object input schema — the schema IS the contract over this `
          + "transport, and a module call never reads it");
    }
  });

  test("tools/list: the payload is inside the budget P3 holds it to", async () => {
    const answer = await mcp.request("tools/list");
    // `result.tools`, which is exactly what `ShMcpToolBudgetTest` measures, and the same ≈4-chars
    // heuristic `ShTokenBudget.estimate` uses. Measuring the whole `result` instead read 2376
    // against 2375 on the first run — a one-token "regression" that was this file counting the
    // envelope. Two instruments for one budget have to agree on the subject or the second one is
    // just noise with a stack trace.
    const bytes = JSON.stringify(answer.result.tools).length;
    const estimate = Math.ceil(bytes / CHARS_PER_TOKEN);

    console.log(`[SH561] tools/list ~${estimate} tokens (${bytes} chars) against ${TOOLS_LIST_BUDGET_TOKENS}\n`);
    assert.ok(estimate <= TOOLS_LIST_BUDGET_TOKENS,
      `tools/list is ~${estimate} tokens against a budget of ${TOOLS_LIST_BUDGET_TOKENS}. This is `
        + "the priciest fixed cost on the surface — it is re-sent on every turn — and it is the one "
        + "regression a module import structurally cannot see, because the module never serialises "
        + "the list.");
  });

  test("tools/call: a real tool answers in the shape an agent receives", async () => {
    const answer = await mcp.call("shio_context", {});

    assert.ok(!answer.error, `shio_context failed over the transport: ${JSON.stringify(answer.error)}`);
    assert.ok(Array.isArray(answer.result?.content) && answer.result.content.length > 0,
      `an MCP result is content blocks, not a return value: ${JSON.stringify(answer.result)}`);
    assert.equal(answer.result.content[0].type, "text",
      "and the first block is text, which is what the agent actually reads");
  });

  test("tools/call: a refusal arrives as something an agent can act on", async () => {
    // The whole reason P6 exists, at the one layer that had no coverage: an agent recovers from
    // what the refusal says, and over this transport a refusal is a *result* with `isError` or a
    // JSON-RPC `error` — never a thrown exception, which is all a module call can produce.
    const answer = await mcp.call("shio_read", { address: "post:NoSuchSite/nothing" });

    assert.ok(answer.error || answer.result,
      "a refusal is still a well-formed frame; a bridge that dropped it would hang the client");
    const text = JSON.stringify(answer);
    assert.ok(text.length > 0 && !text.includes("undefined"),
      `the refusal is legible rather than an empty envelope: ${text.slice(0, 300)}`);
  });

  test("tools/call: an unknown tool name is refused, not ignored", async () => {
    const answer = await mcp.call("shio_not_a_tool", {});

    assert.ok(answer.error || answer.result?.isError,
      `a name an agent guessed wrong has to come back as a refusal it can read — silence, or a `
        + `success with no content, is the failure mode: ${JSON.stringify(answer)}`);

    await mcp.close();
  });
});
