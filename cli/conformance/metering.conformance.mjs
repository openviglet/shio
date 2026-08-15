/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH761 — the meter, proved where it actually runs.
 *
 * ## Why a live suite and not a wider unit test
 *
 * SH45's meter has unit tests that call `record` and `flush` directly, and they pass. What none of
 * them drives is the path that makes it a meter: a **real request** arriving, being classified by
 * surface, and counted against the tenant the security chain resolved.
 *
 * That path has a precondition MockMvc cannot reproduce. `ShTenantApiCallFilter` is mounted at
 * `LOWEST_PRECEDENCE - 30` so it runs *inside* the Spring Security chain, where `ShTenantFilter`
 * has resolved the tenant and has not yet run the clear it always does on the way out. The tenant,
 * the chain order and the flush are all things only a running instance arranges.
 *
 * ## It found the defect on its first run, which is the argument for the tier
 *
 * Five agent calls against a clean instance, and every count came back **zero** — while the
 * document named `tenant=shio`, the very tenant it had counted nothing for. `shio.multi-tenant` is
 * `false` by DEFAULT, so `ShTenantFilter` is a no-op, nothing is ever bound to the context, and the
 * filter's raw `getTenantId()` answered null on every request. The reader defaulted to the reserved
 * tenant and the writer did not, so the meter recorded nothing on every ordinary install.
 *
 * That is SH751's shape one subsystem over: complete everywhere except the line that connects it,
 * with every surface reporting success.
 *
 * ## Pending, not written — so nothing waits a minute
 *
 * The meter accumulates in heap and flushes on an interval (60s by default), so a suite asserting
 * the *durable* figure would have to sleep. It does not have to: SH754 put `pending` in the document
 * beside `written` precisely because a reader needs to see work that has not been flushed. The
 * assertions below are on the total of the two, which is what a caller actually spent.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, basic, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** How many extra agent calls the assertion sends. Small: the claim is that the number MOVES. */
const CALLS = 5;

describe("SH761: the API-call meter counts a real request", () => {
  const conn = connection();
  const site = scratchSite("Meter");
  let mcp;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");
  const refused = (answer) => Boolean(answer.error) || answer.result?.isError === true;

  /**
   * The agent-surface total the document reports — written plus pending.
   *
   * <p>Both, because which side of the flush a call has landed on is an implementation detail of
   * when the timer last fired, and a test that asserted only `written` would be asserting the
   * scheduler rather than the meter.</p>
   */
  function agentCalls(document) {
    const row = /^calls agent written=(\d+) pending=(\d+)$/m.exec(document);
    assert.ok(row, `SH761: no agent row in the usage document:\n${document}`);
    return Number(row[1]) + Number(row[2]);
  }

  before(async () => {
    await requireInstance();
    await requireVocabulary(conn);
    const seeded = await batch(conn, [{ op: "site.upsert", address: `site:${site}` }]);
    assert.ok(!errorsIn(seeded), `SH761: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
    mcp = await openMcp(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  test("shio://usage answers over the wire, and names the tenant it is counting for", async () => {
    // Also SH760's population: this is the first live read of any MCP resource, and shio://usage
    // is the one whose whole argument was that an MCP-only client has no other route to it.
    const answer = await mcp.readResource("shio://usage");
    const document = (answer?.result?.contents ?? []).map((part) => part.text ?? "").join("\n");

    assert.ok(!answer.error, `SH761: shio://usage was refused: ${JSON.stringify(answer.error)}`);
    assert.match(document, /^shio-usage tenant=\S+ period=\d{4}-\d{2}-\d{2}$/m,
      `SH761: the document has to name the tenant and the period it is about, or a reader cannot `
      + `tell whose spend it is looking at:\n${document.slice(0, 300)}`);
    assert.match(document, /^calls agent written=\d+ pending=\d+$/m,
      "SH761: written and pending are stated separately — a document reporting only what is "
      + "durable answers zero for the first minute of every window, and an agent that reads zero "
      + "after doing work stops believing the number");
  });

  test("calls made against a live instance move the meter", async () => {
    // The assertion the unit tests cannot make. On its first run this failed with 0 → 0 on a
    // default install, which is the defect SH761 was filed to look for.
    const before = agentCalls(await ask());

    for (let i = 0; i < CALLS; i++) {
      const answer = await mcp.call("shio_context", { site });
      assert.ok(!refused(answer), `SH761: the call being counted was refused: ${textOf(answer)}`);
    }

    const after = agentCalls(await ask());
    assert.ok(after >= before + CALLS,
      `SH761: the agent surface went from ${before} to ${after} across ${CALLS} calls. A meter `
      + `that does not move is indistinguishable from an instance nobody used — and that is not a `
      + `hypothetical: shio.multi-tenant is false by default, ShTenantFilter is then a no-op, and `
      + `the filter's raw getTenantId() answered null on every request, so every call was dropped `
      + `while the document went on naming the tenant it had counted nothing for.`);
  });

  test("the HTTP twin serves the same document, so an agent with no MCP client sees it too", async () => {
    // P10 and P1 together: shio://usage and ?include=usage are one service, so identical bytes are
    // a property of the construction. Asserted because "the same" is the kind of claim that stops
    // being true quietly.
    const overHttp = await ask();
    const overMcp = (await mcp.readResource("shio://usage")).result?.contents
      ?.map((part) => part.text ?? "").join("\n") ?? "";

    const rowsOf = (text) => text.split("\n").filter((line) => line.startsWith("calls ")).length;
    assert.equal(rowsOf(overHttp), 4,
      `SH761: the context pack's usage section has to carry every surface plus the total — a row `
      + `missing there is a surface a reader cannot see:\n${overHttp.slice(0, 400)}`);
    assert.ok(overMcp.startsWith("shio-usage "),
      "SH761: the resource and the section are one document; if they diverge, one of them is "
      + "describing a meter nobody is reading");
  });

  /** The usage document over HTTP — the twin of the resource, and one console call to fetch. */
  async function ask() {
    const res = await fetch(`${conn.baseUrl}/api/v2/agent/context?include=usage&format=terse`, {
      headers: { Authorization: basic(conn.user, conn.password), Accept: "text/plain" },
    });
    assert.ok(res.ok, `SH761: the usage section answered ${res.status}`);
    return res.text();
  }
});
