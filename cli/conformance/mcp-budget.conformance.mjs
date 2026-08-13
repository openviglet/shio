/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH714 — what every tool costs on a real tree, and whether it answers twice the same.
 *
 * ## Two claims, both previously unchecked
 *
 * P3 makes a token regression a build failure and `ShAgentResponseBudgetTest` enforces it — against
 * a fixture of one site, one post-type, three posts. That is the right shape for catching a field
 * somebody added to a DTO and it is **silent about the number an agent pays**: a listing that fits
 * on three rows and blows the budget on three hundred looks identical to one that does not.
 *
 * P5 promises byte-identical serialization, and nothing anywhere repeated a call to check. Ordering
 * instability is the defect that hides there, and it is invisible to a fixture with too few rows to
 * have an ambiguous order.
 *
 * So this seeds a site large enough to be honest — 121 posts across 6 folders, then five rounds of
 * updates so the change feed has more than its own maximum to cap — calls every tool over the MCP
 * wire, prints the table, and holds each number against `token-budgets.properties`.
 *
 * ## The measurement is the deliverable, and it found two things
 *
 * - **SH722** — ten identical `shio_context` calls return **two** distinct packs. One line moves:
 *   a post-type whose two attributes share an `ordinal` sorts them by whatever order the `Set`
 *   iterated. Every other read tool was stable over the same ten. Asserted below, as measured.
 * - **SH723** — `shio_changes` at its documented maximum returns 500 rows and ~19,000 estimated
 *   tokens in one response, with no ceiling and no truncation marker, while the context pack — the
 *   one that exists to be big — self-limits at 4,000. The surface's only token budget is not on
 *   the response that produces the largest payload.
 *
 * ## Per row, not per response
 *
 * The row figures are the load-bearing ones. A total says how big this fixture is; a row says what
 * the *shape* costs, and that is the number that survives the fixture growing. Both listings were
 * measured flat across 5, 25 and 100 rows, so a row really is a constant and a slope that stops
 * being flat is itself a finding.
 *
 * ## Read, never restated
 *
 * The ceilings are read out of `token-budgets.properties`. `mcp-transport.conformance.mjs` restates
 * its one budget and argues for it — the suite may run against an instance it did not build — and
 * SH673 is the defect that argument cost: the constant drifted three raises behind its declaration
 * and the gate was red for a fortnight. Reading has the opposite failure mode and the cheaper one:
 * against a deliberately stale instance (`SHIO_CONFORMANCE_ALLOW_STALE=1`) these numbers describe
 * the tree rather than the binary, which is a thing the operator asked for by name.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import { ShioAdminClient } from "../src/admin-client.mjs";
import { budgets, need, tokens } from "../test/support/token-budgets.mjs";
import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** Six folders and 121 posts — enough that an ordering is ambiguous and a listing has a slope. */
const FOLDERS = ["/news", "/news/2026", "/blog", "/blog/tech", "/docs", "/docs/guides"];
const POSTS = 120;
/** How many times every post is rewritten, so the feed has more entries than it will ever return. */
const ROUNDS = 5;
/** How many identical calls each read gets, looking for the one that differs. */
const REPEATS = 10;
/** A post-type with two fields at one ordinal — the condition SH722 needs, seeded rather than found. */
const COLLIDING_TYPE = "SH722Collision";

describe("SH714: what every tool costs live, and whether it answers twice the same", () => {
  const conn = connection();
  const site = scratchSite("Budget");
  const declared = budgets();
  let mcp;
  /** The one thing the agent surface cannot do: write a post-type (SH117's name-keyed surface). */
  let admin;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");
  const refused = (answer) => Boolean(answer.error) || answer.result?.isError === true;
  const ask = async (tool, args) => {
    const answer = await mcp.call(tool, args);
    assert.ok(!refused(answer), `SH714: ${tool} was refused, so its size measures a failure: ${textOf(answer)}`);
    return textOf(answer);
  };
  const rowsIn = (text, kind) => [...text.matchAll(new RegExp(`^${kind} `, "gm"))].length;

  before(async () => {
    await requireInstance();
    // SH724 — `Page` is SH95 vocabulary, not a seeded type: without this the fixture below is
    // refused on any instance nobody built by hand, and every scenario behind it is cancelled.
    await requireVocabulary(conn);
    const ops = [
      { op: "site.upsert", address: `site:${site}` },
      ...FOLDERS.map((path) => ({ op: "folder.upsert", address: `folder:${site}${path}` })),
      {
        op: "post.upsert", address: `post:${site}/`, type: "Page", folder: "/",
        data: { title: "Home", content: "The front door." },
      },
    ];
    for (let i = 0; i < POSTS; i++) {
      const folder = FOLDERS[i % FOLDERS.length];
      ops.push({
        op: "post.upsert", address: `post:${site}${folder}/page-${i}`, type: "Page", folder,
        data: {
          title: `Page ${i}`,
          content: `Body of page ${i}. ${"Lorem ipsum dolor sit amet. ".repeat(4)}`,
        },
      });
    }
    const seeded = await batch(conn, ops);
    assert.ok(!errorsIn(seeded), `SH714: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);

    // The feed needs more entries than its own maximum, or "capped at 500" is untested.
    for (let round = 0; round < ROUNDS; round++) {
      await batch(conn, Array.from({ length: POSTS }, (_, i) => ({
        op: "post.upsert", address: `post:${site}${FOLDERS[i % FOLDERS.length]}/page-${i}`,
        type: "Page", folder: FOLDERS[i % FOLDERS.length],
        data: { title: `Page ${i}`, content: `Round ${round}. ${"Lorem ipsum dolor sit amet. ".repeat(4)}` },
      })));
    }
    mcp = await openMcp(conn);
    admin = new ShioAdminClient(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    // The post-type is instance-wide, so it outlives the site and has to be removed by name — a
    // suite that leaves a type behind changes what the NEXT run's context pack costs.
    await admin?.deletePostTypeDef(COLLIDING_TYPE).catch(() => {});
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  // ------------------------------------------------------------------ what it costs

  test("a listing's cost per row is flat, and within its declared ceiling", async () => {
    // The slope is the assertion. A per-row cost that grows with the row count means the payload
    // carries something quadratic; one that is flat means the ceiling below describes the SHAPE
    // rather than this fixture, which is the whole difference from a fixture-measured budget.
    const table = [];
    for (const [kind, tool, args] of [
      ["row", "shio_find", (limit) => ({ q: "Page", site, limit })],
      ["event", "shio_changes", (limit) => ({ site, limit })],
    ]) {
      const perRow = [];
      for (const limit of [5, 25, 100]) {
        const text = await ask(tool, args(limit));
        const rows = rowsIn(text, kind);
        assert.ok(rows > 0, `SH714: ${tool} at limit=${limit} returned no ${kind} lines to measure.`);
        perRow.push({ limit, rows, est: tokens(text), each: tokens(text) / rows });
      }
      table.push({ tool, perRow });
    }

    for (const { tool, perRow } of table) {
      console.log(`[SH714] ${tool}: ${perRow.map((p) => `${p.rows} rows=${p.est} (${p.each.toFixed(1)}/row)`).join("  ")}`);
    }

    for (const { tool, perRow } of table) {
      const [small, , large] = perRow;
      assert.ok(large.each <= small.each * 1.2,
        `SH714: ${tool} costs ${large.each.toFixed(1)} tokens/row at ${large.rows} rows and `
        + `${small.each.toFixed(1)} at ${small.rows}. A row that gets more expensive as the site `
        + `grows means the ceiling below describes this fixture and nothing else.`);
    }

    const find = table[0].perRow.at(-1).each;
    const changes = table[1].perRow.at(-1).each;
    assert.ok(find <= need(declared, "live.find.row"),
      `SH714: a find row costs ${find.toFixed(2)} tokens against a declared ${need(declared, "live.find.row")}. `
      + "Every listing multiplies this by however much content somebody has, which is why the row "
      + "is the number that is held rather than the total.");
    assert.ok(changes <= need(declared, "live.changes.row"),
      `SH714: a change row costs ${changes.toFixed(2)} tokens against a declared ${need(declared, "live.changes.row")}.`);
  });

  test("every fixture-scoped response is within its declared ceiling", async () => {
    const measured = [];
    for (const [key, tool, args] of [
      ["live.read.post", "shio_read", { address: `post:${site}/blog/page-2` }],
      ["live.digest.post", "shio_digest", { address: `post:${site}/` }],
      ["live.verify.site", "shio_verify", { site }],
      ["live.assets.site", "shio_assets", { site }],
      ["live.marketplace", "shio_marketplace", {}],
    ]) {
      measured.push({ key, tool, est: tokens(await ask(tool, args)), ceiling: need(declared, key) });
    }
    console.log(`[SH714] ${measured.map((m) => `${m.tool}=${m.est}/${m.ceiling}`).join("  ")}`);

    for (const { key, tool, est, ceiling } of measured) {
      assert.ok(est <= ceiling,
        `SH714: ${tool} answered ${est} estimated tokens against ${key}=${ceiling}. Raising a `
        + "budget is allowed and is a reviewable act — do it in token-budgets.properties, with "
        + "the reason, the way every other raise in that file is argued.");
    }
  });

  test("the context pack stays inside the limit it declares", async () => {
    // Deliberately NOT held to a number here. The pack describes the instance, so its size depends
    // on what else is on the box — and it does not need one, because it states and enforces its
    // own. That is the property this asserts, and it is the one SH723 says every other unbounded
    // response is missing.
    const pack = await ask("shio_context", { site });
    const stated = /^budget est=(\d+) limit=(\d+)/m.exec(pack);
    assert.ok(stated, `SH714: the pack states no budget line at all:\n${pack.slice(0, 300)}`);

    const [, est, limit] = stated.map(Number);
    console.log(`[SH714] shio_context: est=${est} limit=${limit} measured=${tokens(pack)}`);
    assert.ok(est <= limit,
      `SH714: the pack reports ${est} tokens against its own limit of ${limit} — a budget that `
      + "reports its own breach is not one.");
    assert.ok(tokens(pack) <= limit * 1.1,
      `SH714: the pack measures ${tokens(pack)} on the wire while reporting ${est} against a limit `
      + `of ${limit}. The number an operator logs and the number the budget enforces have to be `
      + "the same number, or only one of them is checked (SH124).");
  });

  test("SH723: the feed's maximum is a row cap, and it is the biggest response on the surface", async () => {
    // ASSERTED AS MEASURED. When SH723 lands, this response carries a truncation marker and a
    // ceiling, the assertion below stops holding, and the message says so rather than a comment.
    const feed = await ask("shio_changes", { site, limit: 500 });
    const rows = rowsIn(feed, "event");
    const est = tokens(feed);
    console.log(`[SH714] shio_changes at its maximum: rows=${rows} est=${est}`);

    assert.equal(rows, 500,
      `SH714/SH723: the feed returned ${rows} rows for limit=500. If it now caps on tokens `
      + "rather than rows, SH723 shipped and this test is the wrong way round.");
    assert.ok(!/^budget est=/m.test(feed),
      `SH714/SH723: the feed now states a budget — which is the fix. Turn this test around.`);
    assert.ok(est > need(declared, "live.find.row") * 200,
      `SH714/SH723: the feed's maximum response (${est} tokens) is no longer larger than the `
      + "listing's, which was the argument for filing it.");
  });

  // ------------------------------------------------------------------ whether it answers twice the same

  test("every read repeated returns byte-identical output", async () => {
    // P5, asked the only way it can be: the same call, ten times, nothing changed in between.
    // `shio_context` is excluded here and pinned by name in the next test — leaving it in would
    // make this assertion red for a defect that is already filed, and SH579 has one rule about a
    // gate that is allowed to be red.
    const unstable = [];
    for (const [tool, args] of [
      ["shio_read", { address: `post:${site}/blog/page-2` }],
      ["shio_find", { q: "Page", site, limit: 50 }],
      ["shio_changes", { site, limit: 50 }],
      ["shio_digest", { address: `post:${site}/` }],
      ["shio_verify", { site }],
      ["shio_assets", { site }],
      ["shio_marketplace", {}],
    ]) {
      const seen = new Set();
      for (let i = 0; i < REPEATS; i++) seen.add(await ask(tool, args));
      if (seen.size !== 1) unstable.push(`${tool}: ${seen.size} distinct over ${REPEATS} calls`);
    }

    assert.deepEqual(unstable, [],
      "SH714: a read answered two different things to the same question with nothing changed in "
      + "between. P5 is what makes a digest, an ETag and a cached pack mean anything — an unstable "
      + "serialization breaks all three at once, and it breaks them invisibly.");
  });

  test("SH722: the context pack is the one read that does not", async () => {
    // ASSERTED AS MEASURED. Ten identical calls, two distinct packs, one line apart: a post-type
    // whose attributes share an `ordinal` is sorted by a key that cannot separate them, out of a
    // Set. When SH722 lands, `seen.size` is 1, this fails, and the tool moves into the test above.
    //
    // **The collision is seeded here rather than found.** The first draft of this reproduced against
    // `PT-FOLDER-INDEX`, a type this developer's instance happened to carry with two attributes at
    // `ordinal: 2` — which is SH724 exactly, one file later: green because of what the instance had
    // accumulated, and stable on a clean one for a reason that has nothing to do with the defect.
    // A suite that measures a condition has to create it.
    await admin.putPostTypeDef(COLLIDING_TYPE, {
      name: COLLIDING_TYPE,
      namePlural: `${COLLIDING_TYPE}s`,
      title: "SH722 ordinal collision",
      description: "Two fields at one ordinal. Seeded by SH714's suite; deleted when it ends.",
      fields: [
        { name: "TITLE", label: "Title", ordinal: 1, widget: "Text", isTitle: true, required: true },
        // The collision, and nothing forbids it: the console writes an ordinal per field and never
        // asserts the set is a permutation, so this is a shape a curator can produce by hand.
        { name: "ALPHA", label: "Alpha", ordinal: 2, widget: "Text", required: false },
        { name: "OMEGA", label: "Omega", ordinal: 2, widget: "Text Area", required: false },
      ],
    });

    const seen = new Set();
    for (let i = 0; i < REPEATS; i++) seen.add(await ask("shio_context", { site }));
    console.log(`[SH714] shio_context: ${seen.size} distinct over ${REPEATS} identical calls`);

    assert.ok(seen.size > 1,
      `SH714/SH722: the pack was stable over ${REPEATS} calls with ${COLLIDING_TYPE} on the `
      + "instance. That is the fix — move shio_context into the P5 test above and delete this one. "
      + "It is also what a tiebreaker on the comparator looks like from out here, so check "
      + "ShCdaMapper#schemaFields before concluding the collision stopped mattering.");

    const [first, second] = [...seen];
    const a = first.split("\n");
    const b = second.split("\n");
    const at = a.findIndex((line, i) => line !== b[i]);
    assert.ok(a[at]?.startsWith("type ") && b[at]?.startsWith("type "),
      `SH714/SH722: the packs differ somewhere other than the model, at line ${at}:\n`
      + `  A: ${a[at]}\n  B: ${b[at]}\n`
      + "SH722 is about a field list sorted by a non-unique ordinal. A difference anywhere else is "
      + "a second instability and needs its own task rather than this one's assertion.");
  });

  test("an idempotent write repeated changes nothing, and says so the same way twice", async () => {
    // P5's other half. The first write creates; every one after it must report a noop and leave
    // one row — which is what makes `shio_apply` safe to re-send after a compaction, the property
    // its own tool description promises.
    const body = {
      ops: [{
        op: "post.upsert", address: `post:${site}/idempotent`, type: "Page", folder: "/",
        data: { title: "Idempotent", content: "The same document, sent three times." },
      }],
    };
    const created = await ask("shio_write", body);
    assert.match(created, /changed=1/, `SH714: the first write did not create: ${created}`);

    const second = await ask("shio_write", body);
    const third = await ask("shio_write", body);
    assert.equal(second, third,
      `SH714: two identical re-sends of a settled write answered differently:\n${second}\n---\n${third}`);
    assert.match(second, /noop/,
      `SH714: re-sending an unchanged document reported work: ${second}. An agent that cannot tell `
      + "\"already right\" from \"changed\" has to read the post back after every write.");

    const applyBody = {
      site,
      posts: [{ url: "/idempotent", type: "Page", folder: "/", data: { title: "Idempotent", content: "The same document, sent three times." } }],
    };
    assert.equal(await ask("shio_apply", applyBody), await ask("shio_apply", applyBody),
      "SH714: two applies of one document answered differently. `changed=0 means no drift` is the "
      + "sentence the tool's own schema tells an agent to rely on.");

    const found = await ask("shio_find", { q: "Idempotent", site });
    assert.equal(rowsIn(found, "row"), 1,
      `SH714: three identical upserts left ${rowsIn(found, "row")} rows.\n${found}`);
  });
});
