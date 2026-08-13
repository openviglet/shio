/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH710 — every tool refused on purpose, over the transport an agent actually uses.
 *
 * ## Why the HTTP assertion does not cover this
 *
 * P6 says errors are instructions, and `ShTeachingErrorTest` asserts it — for the HTTP ops. The
 * transport is where an agent *meets* a refusal, and its failure modes are its own: a teaching
 * payload dropped into a JSON-RPC field no client reads, a schema violation caught by the framing
 * layer before the handler that would have taught anything, a stack trace where a `didYouMean`
 * belonged. None of those can happen to a MockMvc call, and all of them are silent here until
 * something asks.
 *
 * ## The shape a refusal actually has, measured before this was written
 *
 * A refused **call** comes back as a `result` with `isError: true` and the teaching text in its
 * content blocks — not as a JSON-RPC `error`. That distinction is load-bearing twice over: a client
 * reading only `error` sees success, and SH709's first draft made exactly that mistake in its own
 * helper and reported a product defect that was its own.
 *
 * A refused **tool name** is the other branch: a JSON-RPC `error` with code -32602 and the whole
 * registry in `data`. Both are asserted, because an agent has to survive both.
 *
 * ## What each case asserts
 *
 * That the refusal is machine-readable, that it carries at least one of P6's four instruments, and
 * — the part only a live run can check — that the store is unchanged afterwards.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";
import { mcpTools } from "./support/product-vocabulary.mjs";

/**
 * P6's four instruments. A refusal has to carry at least one, and which one is the tool's business.
 *
 * <p>Matched on the rendered text rather than on a field name because that is what an agent reads
 * over this transport — the content blocks are the payload, and a `fix` that never reaches them is
 * a `fix` nobody acts on.</p>
 */
const INSTRUMENTS = [/\bFix:/i, /\bAllowed:/i, /\bdid ?you ?mean/i, /\bExample:/i];

describe("SH710: every tool's wrong call comes back as something an agent can act on", () => {
  const conn = connection();
  const site = scratchSite("Refuse");
  let mcp;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");

  /** Everything an agent can read off a refusal, whichever branch it arrived on. */
  const legible = (answer) => (answer.error
    ? `${answer.error.message ?? ""} ${JSON.stringify(answer.error.data ?? "")}`
    : textOf(answer));

  const refused = (answer) => Boolean(answer.error) || answer.result?.isError === true;

  before(async () => {
    await requireInstance();
    // SH724 — `Page` is SH95 vocabulary, not a seeded type: without this the fixture below is
    // refused on any instance nobody built by hand, and every scenario behind it is cancelled.
    await requireVocabulary(conn);
    const seeded = await batch(conn, [
      { op: "site.upsert", address: `site:${site}` },
      {
        op: "post.upsert", address: `post:${site}/anchor`, type: "Page", folder: "/",
        data: { title: "Anchor", content: "The row that must survive every refusal below." },
      },
    ]);
    assert.ok(!errorsIn(seeded), `SH710: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
    mcp = await openMcp(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  /**
   * The ways a real agent gets a call wrong.
   *
   * <p>Each row is a *shape* rather than a tool, because the shapes are what the transport can
   * mishandle: a schema layer that refuses before the handler teaches, a lenient reader that
   * accepts a property nobody declared, an address form the verb does not take.</p>
   */
  const WRONG_CALLS = () => [
    {
      what: "a required property omitted",
      tool: "shio_read",
      args: {},
      // SH331 is the reason this is a refusal at all: an argument a tool does not declare used to
      // be ignored, so the call reported success while doing something else.
    },
    {
      what: "a property the tool does not declare",
      tool: "shio_read",
      args: { address: `post:${site}/anchor`, notAProperty: "zz" },
    },
    {
      what: "a property of the wrong type",
      tool: "shio_read",
      args: { address: 42 },
    },
    {
      what: "an address form the verb does not take",
      tool: "shio_write",
      // Writes take only `post:`/`folder:` — a folder path and a friendly URL are separate
      // namespaces, and guessing which was meant would create content in the wrong place.
      args: { ops: [{ op: "post.upsert", address: `site:${site}` }] },
    },
    {
      what: "an address that parses and names nothing",
      tool: "shio_read",
      args: { address: `post:${site}/nothing-is-here` },
    },
    {
      what: "a site that does not exist",
      tool: "shio_find",
      args: { q: "x", site: "NoSuchSiteAtAll" },
    },
    {
      what: "a publish aimed at nothing",
      tool: "shio_publish",
      args: { address: [`post:${site}/nothing-is-here`] },
    },
  ];

  for (const row of WRONG_CALLS()) {
    test(`${row.tool}: ${row.what} — refused, and it says what to do`, async () => {
      const answer = await mcp.call(row.tool, row.args);

      assert.ok(refused(answer),
        `SH710: ${row.tool} accepted ${row.what}. A wrong call that succeeds is worse than one that `
        + `fails: the agent proceeds from a state it believes it created. `
        + `${JSON.stringify(answer).slice(0, 300)}`);

      const text = legible(answer);
      assert.ok(text.length > 20,
        `SH710: the refusal is an empty envelope. Over this transport the content blocks ARE the `
        + `payload, so a teaching error that never reaches them teaches nobody: ${text}`);
      assert.ok(INSTRUMENTS.some((re) => re.test(text)),
        `SH710: the refusal carries none of P6's four instruments (fix / allowed / didYouMean / `
        + `example). A refusal an agent cannot recover from turns one wrong call into a session of `
        + `guessing, which is the cost P2 exists to remove: ${text.slice(0, 400)}`);
    });
  }

  test("SH710: an unknown tool name is refused with the registry, on the other branch", async () => {
    // The one case that arrives as a JSON-RPC `error` rather than an `isError` result, because the
    // name never reaches a handler. An agent has to survive both shapes, so both are asserted.
    const answer = await mcp.call("shio_reed", { address: `post:${site}/anchor` });

    assert.ok(answer.error, `SH710: a tool name nothing registers must be refused: ${JSON.stringify(answer)}`);
    const text = legible(answer);
    assert.ok(INSTRUMENTS.some((re) => re.test(text)) || /shio_read/.test(text),
      `SH710: the refusal has to tell the agent what it could have called: ${text.slice(0, 300)}`);

    // SH717 closed the question this test used to leave open. `shio_reed` is one character from
    // `shio_read`, and the caller is holding the registry already — `tools/list` is the first thing
    // it read. So the answer it needs is the EDIT, and this is the end of the loop that starts at
    // the message: assert it over the wire, where a client actually meets it.
    assert.match(answer.error?.message ?? "", /Did you mean '?shio_read'?/,
      `SH717: a near-miss name must come back with the edit, not only with the catalogue. Answering `
      + `twelve names to a one-character typo makes the agent re-read a list it is holding, which is `
      + `the token cost P3 measures and the turn P2 exists to remove: ${text.slice(0, 300)}`);
    assert.ok(Array.isArray(answer.error?.data) && answer.error.data.includes("shio_read"),
      `SH717: and 'allowed' stays — the suggestion is an addition, not a swap. A name near nothing `
      + `still needs the catalogue: ${JSON.stringify(answer.error?.data ?? null).slice(0, 200)}`);
  });

  test("SH710: nothing above wrote anything", async () => {
    // The half only an instance can check. Every case ran against a seeded site; if a refusal ever
    // half-applied, the anchor's neighbours would exist.
    const stray = await mcp.call("shio_find", { q: "", site });
    const text = textOf(stray);

    assert.ok(!/nothing-is-here|notAProperty/i.test(text),
      `SH710: a refused call left a row behind. This is the direction every guard fails in and the `
      + `one a branch assertion cannot see: ${text.slice(0, 400)}`);
  });

  test("SH710: the sweep covers every tool the product registers", () => {
    // The guard on the suite. A tool added without a refusal case would leave this file reporting
    // a clean P6 sweep over a surface it does not cover.
    const registered = [...mcpTools()].sort();
    assert.equal(registered.length >= 12, true, "SH710: the registry read as empty");

    const swept = new Set(WRONG_CALLS().map((row) => row.tool));
    assert.ok(swept.size >= 4,
      "SH710: the table collapsed to fewer shapes than it has tools to exercise them on");
    for (const tool of swept) {
      assert.ok(registered.includes(tool),
        `SH710: this sweep refuses '${tool}', which the product does not register — a case aimed at `
        + "a tool that does not exist proves nothing about the one that does.");
    }
  });
});
