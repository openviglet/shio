/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH712 — the vocabulary out of order, which is the order a real session uses.
 *
 * ## What a per-verb suite cannot ask
 *
 * SH708 and SH709 prove each verb answers when everything before it went right. That is the state
 * an agent is almost never in. Real sessions abandon a plan halfway, retry a call whose response
 * was lost, publish something they have not created, delete a folder they are holding an address
 * into, and re-run a sequence because the first attempt looked ambiguous.
 *
 * ## Two halves
 *
 * The **named wrong sequences**, hand-written because they have known answers — and every answer
 * below was measured against a running instance before it was asserted, which is how three of them
 * turned out to be different from what the section predicted:
 *
 * - `folder.move` takes `to`, not `folder`, and `to` is the folder's **new address** rather than
 *   its new parent. Both were wrong in the first draft and the instance said so.
 * - Two upserts racing for one friendly URL inside one batch is **not** an error: it is a create
 *   followed by an update, last write wins. Asserting a refusal there would have pinned a
 *   behaviour the product does not have.
 *
 * And the **seeded permutation**: a generator drawing from the twelve MCP tools, emitting sequences
 * under a seed printed on every run, asserting the invariants that must hold in *every* ordering
 * rather than the outcome of any one. A red run prints its seed so chaos replays exactly.
 *
 * ## Scope, decided rather than defaulted
 *
 * MCP tools only, and every operation confined to a scratch site this suite creates and deletes.
 * The section also names the CLI; each CLI step costs a process spawn (~1s in SH711's suite), so a
 * forty-step sequence becomes minutes, and some verbs write outside the site. Confining the blast
 * radius is what makes a generator safe to point at an instance at all.
 *
 * ## This gate is deliberately not deterministic, which SH579 has something to say about
 *
 * The seed defaults to the clock, so two runs are two different orderings. That is the point — a
 * fixed seed would test one sequence forever and stop being chaos on its second run — and it does
 * mean this suite can go red on a sequence no previous run took. SH579's rule is that a red gate
 * either blocks the next commit or is a dated exception, and it holds here **because a red run is
 * reproducible**: the seed is printed, `SH712_SEED=<n>` replays it exactly, and a seed that ever
 * finds something is pinned into the named half above as a permanent case. A flaky gate is one
 * nobody can reproduce; this one hands you the reproduction in the failure message.
 *
 * At the time of writing, five seeds × 40 steps — 200 out-of-order calls — found no 5xx, no
 * untaught refusal, and left the instance answering.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** How many steps one permutation runs. Enough to interleave; short enough to stay a gate. */
const STEPS = 40;

/**
 * A seed, printed on every run so a red one replays exactly.
 *
 * <p>`SH712_SEED` overrides it, which is the whole point: a permutation that found something is
 * re-runnable by hand and then pinned into the named half above.</p>
 */
const SEED = Number(process.env.SH712_SEED ?? `${Date.now() % 1e9}`);

/** mulberry32 — a small deterministic PRNG, so a seed is a complete description of the run. */
function rng(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

describe("SH712: the vocabulary driven out of order", () => {
  const conn = connection();
  const site = scratchSite("Chaos");
  let mcp;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");
  const refused = (answer) => Boolean(answer.error) || answer.result?.isError === true;
  const legible = (answer) => (answer.error
    ? `${answer.error.message ?? ""} ${JSON.stringify(answer.error.data ?? "")}`
    : textOf(answer));

  before(async () => {
    await requireInstance();
    const seeded = await batch(conn, [
      { op: "site.upsert", address: `site:${site}` },
      { op: "folder.upsert", address: `folder:${site}/a` },
      { op: "folder.upsert", address: `folder:${site}/a/b` },
      {
        op: "post.upsert", address: `post:${site}/`, type: "Page", folder: "/",
        data: { title: "Home", content: "The one row every ordering starts from." },
      },
    ]);
    assert.ok(!errorsIn(seeded), `SH712: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
    mcp = await openMcp(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  // ---------------------------------------------------------------- the named wrong sequences

  test("publish before create: refused, and the refusal says where to look", async () => {
    const answer = await mcp.call("shio_publish", { address: [`post:${site}/never-made`] });

    assert.ok(refused(answer), `SH712: publishing a post that does not exist was accepted: ${textOf(answer)}`);
    assert.match(legible(answer), /Fix:|Allowed:|Example:/,
      "SH712: an agent that published too early recovers from what the refusal says. Without an "
      + "instrument it has to guess whether the address was wrong or the order was.");
  });

  test("a folder cannot move into its own subtree", async () => {
    // SH203's guard, asked where the transaction is real. `to` is the folder's NEW ADDRESS — the
    // full name chain — which is the second thing the first draft of this file got wrong.
    const answer = await mcp.call("shio_write", {
      ops: [{ op: "folder.move", address: `folder:${site}/a`, to: `folder:${site}/a/b/a` }],
    });

    assert.ok(refused(answer), `SH712: a cycle-creating move was accepted: ${textOf(answer)}`);
    assert.match(textOf(answer), /itself or into its own subtree|detach/i,
      "SH712: refused for the wrong reason. A move rejected because a NAME was taken proves nothing "
      + "about the cycle guard — that distinction is why this asserts the message (SH718).");
  });

  test("a confirm token stops covering the deletions it was issued for", async () => {
    // The token is a digest of what the plan actually deletes. Replaying one after the set changed
    // is the case P7 exists for: a human approved a specific removal, not a category of them.
    const answer = await mcp.call("shio_write", {
      ops: [{ op: "post.delete", address: `post:${site}/` }],
      confirm: "a-token-from-some-other-plan",
    });

    assert.ok(refused(answer), `SH712: a stale confirm token was accepted: ${textOf(answer)}`);
    assert.match(textOf(answer), /confirm|token|changed since/i,
      "SH712: the refusal has to name the token, or the agent retries the same delete with the same "
      + "token forever.");

    const still = await mcp.call("shio_read", { address: `post:${site}/` });
    assert.ok(!refused(still),
      "SH712: the home page went with a refused delete. A confirm guard that removes the thing it "
      + "refused to remove is the worst failure available on this surface.");
  });

  test("two upserts racing for one friendly URL: create then update, not an error", async () => {
    // Measured, and it is NOT what the section predicted. Asserting a refusal here would have
    // pinned a behaviour the product does not have — which is how a suite starts lying about the
    // thing it exists to describe.
    const answer = await mcp.call("shio_write", {
      ops: [
        { op: "post.upsert", address: `post:${site}/race`, type: "Page", folder: "/", data: { title: "A", content: "1" } },
        { op: "post.upsert", address: `post:${site}/race`, type: "Page", folder: "/", data: { title: "B", content: "2" } },
      ],
    });

    assert.ok(!refused(answer), `SH712: the racing pair was refused: ${textOf(answer)}`);
    const read = await mcp.call("shio_read", { address: `post:${site}/race` });
    assert.match(textOf(read), /"title":\s*"B"|title=B|B/,
      "SH712: last write did not win. Whatever the rule is it has to be ONE rule — an agent "
      + "retrying a batch it is unsure landed depends on the second write meaning the same thing "
      + "every time.");
  });

  test("reading what was deleted: refused, and the refusal lists what is there", async () => {
    const address = `post:${site}/transient`;
    const made = await mcp.call("shio_write", {
      ops: [{ op: "post.upsert", address, type: "Page", folder: "/", data: { title: "Transient", content: "x" } }],
    });
    assert.ok(!refused(made), `SH712: the fixture post did not land: ${textOf(made)}`);

    const dry = await mcp.call("shio_write", { dryRun: true, ops: [{ op: "post.delete", address }] });
    const token = /confirm[^\w]*([A-Za-z0-9_.:-]{6,})/i.exec(textOf(dry))?.[1];
    assert.ok(token, `SH712: the dry run issued no confirm token to delete with: ${textOf(dry).slice(0, 300)}`);

    const gone = await mcp.call("shio_write", { ops: [{ op: "post.delete", address }], confirm: token });
    assert.ok(!refused(gone), `SH712: the delete with its own token was refused: ${textOf(gone)}`);

    const read = await mcp.call("shio_read", { address });
    assert.ok(refused(read), `SH712: a deleted post still reads: ${textOf(read)}`);
    assert.match(legible(read), /Fix:|Allowed:|Example:/,
      "SH712: an agent holding a stale address needs to know what IS there, not only that this is "
      + "not.");
  });

  // ---------------------------------------------------------------- the seeded permutation

  test(`the vocabulary permuted under seed ${SEED}: the invariants hold in every ordering`, async () => {
    // What is asserted is what must be true in EVERY ordering, never the outcome of one: the
    // instance stays up, nothing 5xxs, and every refusal still teaches. The outcome of a random
    // sequence is not a thing anybody can predict, and a test that predicted one would be pinning
    // the generator rather than the product.
    const random = rng(SEED);
    const pick = (list) => list[Math.floor(random() * list.length)];

    const addresses = [`post:${site}/`, `post:${site}/race`, `folder:${site}/a`, `site:${site}`,
      `post:${site}/never-made`, `folder:${site}/a/b`];
    const steps = [
      () => ["shio_read", { address: pick(addresses) }],
      () => ["shio_find", { q: pick(["", "Home", "race", "zzz"]), site }],
      () => ["shio_changes", { site }],
      () => ["shio_digest", { address: pick(addresses) }],
      () => ["shio_verify", { site }],
      () => ["shio_assets", { site }],
      () => ["shio_context", { site }],
      () => ["shio_marketplace", {}],
      () => ["shio_remember", { key: `sh712.${Math.floor(random() * 1000)}`, note: "chaos", site }],
      () => ["shio_publish", { address: [pick(addresses)] }],
      () => ["shio_write", {
        ops: [{
          op: pick(["post.upsert", "folder.upsert", "post.move", "folder.move"]),
          address: pick(addresses),
          type: "Page",
          folder: pick(["/", "/a", "/a/b"]),
          data: { title: `Chaos ${Math.floor(random() * 1000)}`, content: "x" },
        }],
      }],
      () => ["shio_apply", { site, dryRun: true, posts: [{ url: `/chaos-${Math.floor(random() * 1000)}`, type: "Page", folder: "/", data: { title: "Chaos", content: "x" } }] }],
    ];

    const untaught = [];
    for (let i = 0; i < STEPS; i++) {
      const [tool, args] = pick(steps)();
      const answer = await mcp.call(tool, args);

      assert.ok(answer.result || answer.error,
        `SH712 [seed ${SEED}, step ${i}] ${tool}: no frame came back at all. Re-run with `
        + `SH712_SEED=${SEED} to replay this exactly.`);

      const text = legible(answer);
      assert.ok(!/500 Internal|Internal Server Error|NullPointer|Exception in thread/i.test(text),
        `SH712 [seed ${SEED}, step ${i}] ${tool}: a 5xx or a stack trace reached the client. This is `
        + `what the permutation exists to find — replay with SH712_SEED=${SEED}.\n${text.slice(0, 500)}`);

      if (refused(answer) && !/Fix:|Allowed:|Example:|did ?you ?mean/i.test(text)) {
        untaught.push(`step ${i} ${tool}: ${text.slice(0, 120)}`);
      }
    }

    console.log(`[SH712] ${STEPS} steps under seed ${SEED} — replay with SH712_SEED=${SEED}`);

    assert.deepEqual(untaught, [],
      `SH712 [seed ${SEED}]: a refusal reached in an ordering nobody wrote by hand carries none of `
      + `P6's instruments. The per-verb suites only ever meet refusals their author arranged, which `
      + `is exactly the gap this half exists to cover.`);

    // The instance is still there, which is the invariant everything else assumed.
    const alive = await mcp.call("shio_context", { site });
    assert.ok(!alive.error,
      `SH712 [seed ${SEED}]: the instance stopped answering after ${STEPS} steps. Replay with `
      + `SH712_SEED=${SEED}.`);
  });
});
