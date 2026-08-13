/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH713 — two agents and a curator, all writing at once.
 *
 * ## The class of defect no suite here could reach
 *
 * Shio's own story has at least two writers in it: the agent that builds and the curator who
 * corrects. Every other test in this repository has **one**, calling in sequence — the forty-odd
 * MockMvc classes, the sixty CLI suites, and every conformance scenario Block V has shipped. So the
 * whole family that needs two callers has never been asked about: a lost update, a read that sees
 * half a publish, a plan applied around a console edit that landed inside it.
 *
 * ## What it found, before anything it confirmed
 *
 * Three defects, all filed, all with an assertion here that carries the id:
 *
 * - **SH719** — two connections creating the *same new address* both succeed, and the site is left
 *   with two posts on one friendly URL. Reproducible at n=2. The address is then unwritable.
 * - **SH720** — the refusal that state produces names `shio_verify` as the instrument that reports
 *   it, and `verify` scans published rows only, so for these drafts it answers `ok`.
 * - **SH721** — a curator's `PATCH` and an agent's `post.upsert` on one post both return success
 *   and the curator's field is gone; nothing on the surface can say *only if unchanged*.
 *
 * Those three are asserted **as measured**, not as they ought to be, each naming the task that will
 * change them. That is deliberate and it is the shape SH579 requires: a suite asserting an
 * invariant the product does not have is a red gate somebody switches off. Fixing SH719 turns the
 * SH719 test here red, and the message says which lines to flip. A comment would not have done
 * that.
 *
 * ## And four invariants that hold
 *
 * Worth as much as the findings, because each is a thing nobody could previously claim: a publish
 * of a set is never observed half-landed; the change cursor loses nothing to a concurrent writer;
 * concurrent whole-state writes leave one submission whole rather than a splice of two; and twenty
 * simultaneous writes to one post all answer, with no 5xx and no deadlock.
 *
 * ## Cost, and why it is opt-in
 *
 * Same bargain as the tenant ITs, one tier up: real connections, real interleavings, an instance
 * this suite creates and deletes a site on. Everything is confined to that scratch site.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** How long any one interleaving may take before it is a deadlock rather than a slow instance. */
const DEADLINE_MS = 30_000;

describe("SH713: two agents and a curator, all writing at once", () => {
  const conn = connection();
  const site = scratchSite("Race");
  /** Two MCP connections — two *processes*, which is what makes them independent sessions. */
  let agentA;
  let agentB;
  /** The curator: a console session, with the cookie and the CSRF token a browser carries. */
  let curator;
  let root;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");
  const refused = (answer) => Boolean(answer.error) || answer.result?.isError === true;
  const legible = (answer) => (answer.error
    ? `${answer.error.message ?? ""} ${JSON.stringify(answer.error.data ?? "")}`
    : textOf(answer));
  /** A published row prints the bare token after its draft state — `state=DRAFT published`. */
  const publishedRows = (text) => [...text.matchAll(/^row \S+ .*\bpublished\b/gm)].length;

  /** One whole-state write, as either agent, so a scenario reads as who-does-what. */
  const upsert = (mcp, url, data) => mcp.call("shio_write", {
    ops: [{ op: "post.upsert", address: `post:${site}${url}`, type: "Page", folder: "/", data }],
  });

  /**
   * Race two creates for one fresh address until the duplicate appears, or give up after `tries`.
   *
   * <p>The race is <b>reachable</b>, not certain — it needs both callers past the resolver's lookup
   * before either inserts, and on a warm instance that has landed on the first attempt every time
   * it has been run. Asserting one attempt would still have been a coin toss dressed as a test, and
   * a gate that goes red on a scheduling accident is the flake SH712 argued its way out of. So the
   * claim this makes is the honest one — the race is reachable within a handful of tries — and the
   * attempt count is returned so a run that suddenly needs five can be seen rather than inferred.</p>
   */
  const raceForADuplicate = async (tag, tries = 5) => {
    for (let attempt = 1; attempt <= tries; attempt++) {
      const url = `/${tag}-${attempt}-${Date.now().toString(36)}`;
      const pair = await Promise.all([
        upsert(agentA, url, { title: "A", content: "A" }),
        upsert(agentB, url, { title: "B", content: "B" }),
      ]);
      const claiming = (await curator.postsIn(root)).filter((post) => post.furl === url);
      if (claiming.length > 1) return { url, pair, claiming, attempt };
      if (attempt === tries) return { url, pair, claiming, attempt };
    }
    throw new Error("unreachable");
  };

  before(async () => {
    await requireInstance();
    // SH724 — `Page` is SH95 vocabulary, not a seeded type: without this the fixture below is
    // refused on any instance nobody built by hand, and every scenario behind it is cancelled.
    await requireVocabulary(conn);
    const seeded = await batch(conn, [
      { op: "site.upsert", address: `site:${site}` },
      {
        op: "post.upsert", address: `post:${site}/`, type: "Page", folder: "/",
        data: { title: "Home", content: "The row both writers start from." },
      },
      {
        op: "post.upsert", address: `post:${site}/contested`, type: "Page", folder: "/",
        data: { title: "Seed", content: "Seed" },
      },
    ]);
    assert.ok(!errorsIn(seeded), `SH713: the fixture did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
    [agentA, agentB] = await Promise.all([openMcp(conn), openMcp(conn)]);
    curator = new ConsoleSession(conn);
    root = await curator.rootFolderId(site);
    assert.ok(root, "SH713: the site's root folder did not resolve — the curator has no handle to edit through.");
  });

  after(async () => {
    await Promise.all([agentA?.close(), agentB?.close()]);
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  // ------------------------------------------------------------------ what holds

  test("two agents writing one post: the winner is whole, never a splice of both", async () => {
    // The invariant that survives last-write-wins. SH712 measured that two upserts racing for one
    // URL inside ONE batch is a create then an update; this is the same race across two *sessions*,
    // where nothing serialises them for us. What must not happen is a document neither caller sent
    // — an agent that retries a write it is unsure about depends on the result being one of the
    // two things it could have been.
    const [first, second] = await Promise.all([
      upsert(agentA, "/contested", { title: "FROM-A", content: "FROM-A" }),
      upsert(agentB, "/contested", { title: "FROM-B", content: "FROM-B" }),
    ]);
    assert.ok(!refused(first) && !refused(second),
      `SH713: a same-post race was refused, which is a behaviour change: ${legible(first)} / ${legible(second)}`);

    const final = textOf(await agentA.call("shio_read", { address: `post:${site}/contested` }));
    const spliced = /FROM-A/.test(final) && /FROM-B/.test(final);
    assert.ok(!spliced,
      "SH713: the surviving document mixes both submissions. Whole-state writes must land whole "
      + `or not at all, or a retry cannot reason about what it is retrying.\n${final}`);
    assert.match(final, /FROM-A|FROM-B/,
      `SH713: neither submission survived a race both callers were told they won.\n${final}`);
  });

  test("a publish of a set is never observed half-landed", async () => {
    // P5's promise, asked where it can fail. A reader polling through the publish of eight posts
    // must see zero or eight — a visitor served three of a set is the torn read a sequential suite
    // structurally cannot produce.
    const addresses = Array.from({ length: 8 }, (_, i) => `post:${site}/set${i}`);
    const made = await agentA.call("shio_write", {
      ops: addresses.map((address, i) => ({
        op: "post.upsert", address, type: "Page", folder: "/",
        data: { title: `Set${i}`, content: "x" },
      })),
    });
    assert.ok(!refused(made), `SH713: the publish fixture did not land: ${textOf(made)}`);

    const seen = [];
    const publishing = agentB.call("shio_publish", { address: addresses });
    const watching = (async () => {
      for (let i = 0; i < 40; i++) {
        seen.push(publishedRows(textOf(await agentA.call("shio_find", { q: "Set", site, limit: 50 }))));
      }
    })();
    const [published] = await Promise.all([publishing, watching]);

    assert.ok(!refused(published), `SH713: the publish was refused: ${legible(published)}`);
    const torn = seen.filter((count) => count !== 0 && count !== addresses.length);
    assert.deepEqual(torn, [],
      `SH713: a reader saw the set half-published — counts ${seen.join(",")}. Nothing may observe a `
      + `partial publish: the whole value of publishing a set in one call is that a visitor never `
      + `gets three pages of eight.`);
    assert.equal(seen.at(-1), addresses.length,
      `SH713: the publish reported success and the last read still sees ${seen.at(-1)} of ${addresses.length}.`);
  });

  test("the change cursor loses nothing to a writer working underneath it", async () => {
    // SH232's question, asked where it can actually fail. A session polling `since=<cursor>` while
    // another writes must not skip an event: a keyset cursor that advanced past a row committed
    // just behind it is a change an agent never learns about, and the failure is invisible — the
    // feed looks healthy and is short.
    const start = (textOf(await agentA.call("shio_changes", { site, limit: 1 })).match(/cursor=(\S+)/) ?? [])[1];
    assert.ok(start, "SH713: shio_changes returned no cursor to resume from.");

    const urls = Array.from({ length: 10 }, (_, i) => `/feed${i}`);
    let cursor = start;
    const seen = new Set();

    const writing = (async () => {
      for (const [i, url] of urls.entries()) {
        const answer = await upsert(agentB, url, { title: `Feed${i}`, content: "x" });
        assert.ok(!refused(answer), `SH713: a feed write was refused: ${legible(answer)}`);
      }
    })();
    const polling = (async () => {
      for (let i = 0; i < 15; i++) {
        const page = textOf(await agentA.call("shio_changes", { site, since: cursor, limit: 200 }));
        for (const row of page.matchAll(/^event \w+ (\S+)/gm)) seen.add(row[1]);
        const next = (page.match(/cursor=(\S+)/) ?? [])[1];
        if (next) cursor = next;
      }
    })();
    await Promise.all([writing, polling]);

    // One last read after the writer is done: a poller that stopped early has not lost anything,
    // it is merely behind, and the assertion is about what the cursor SKIPPED.
    const drain = textOf(await agentA.call("shio_changes", { site, since: cursor, limit: 200 }));
    for (const row of drain.matchAll(/^event \w+ (\S+)/gm)) seen.add(row[1]);

    const missed = urls.map((url) => `post:${site}${url}`).filter((address) => !seen.has(address));
    assert.deepEqual(missed, [],
      `SH713: ${missed.length} of ${urls.length} writes never appeared to a session resuming from `
      + `its own cursor. That is the one failure mode a change feed cannot have — an agent asking `
      + `"did anything change while I was away" is told no, and is wrong.`);
  });

  test("twenty simultaneous writes to one post all answer, and nothing 5xxs", async () => {
    // The deadlock and the stack trace, which are the two ways contention usually shows up first.
    // Note this deliberately writes to a post that ALREADY EXISTS: the create race is SH719 below,
    // and mixing them would have hidden one behind the other.
    const started = Date.now();
    const storm = await Promise.all(Array.from({ length: 20 }, (_, i) =>
      upsert(i % 2 ? agentA : agentB, "/contested", { title: `S${i}`, content: `${i}` })));
    const elapsed = Date.now() - started;

    assert.ok(elapsed < DEADLINE_MS,
      `SH713: twenty concurrent writes took ${elapsed}ms. Past ${DEADLINE_MS}ms this is a lock `
      + `somebody is waiting on rather than an instance under load.`);
    for (const [i, answer] of storm.entries()) {
      assert.ok(answer.result || answer.error, `SH713: write ${i} never came back at all.`);
      assert.ok(!/500 Internal|Internal Server Error|NullPointer|Exception in thread/i.test(legible(answer)),
        `SH713: write ${i} returned a 5xx or a stack trace under contention:\n${legible(answer).slice(0, 400)}`);
    }
    const final = await agentA.call("shio_read", { address: `post:${site}/contested` });
    assert.ok(!refused(final),
      `SH713: the post is unreadable after twenty concurrent writes to it:\n${legible(final)}`);
  });

  // ------------------------------------------------------------------ what it found

  test("SH719: two agents creating one address leave two posts on it, both told they won", async () => {
    // ASSERTED AS MEASURED, not as it should be. When SH719 lands this test goes red — flip the
    // two assertions below to `refused` on exactly one of the pair and `1` post at the URL, and
    // delete this paragraph. A comment saying "this is wrong" would not have failed on the fix.
    const { url, pair, claiming, attempt } = await raceForADuplicate("race");
    console.log(`[SH713] the create race duplicated on attempt ${attempt}`);

    assert.equal(pair.filter(refused).length, 0,
      "SH713/SH719: one of the two creates was refused, which is the FIX — see SH719 and turn this "
      + `test around.\n${pair.map(legible).join("\n---\n")}`);

    assert.equal(claiming.length, 2,
      `SH713/SH719: ${claiming.length} post(s) at ${url} after ${attempt} attempt(s). Two is the `
      + `defect this pins; one every time means SH719 shipped and these assertions are now the `
      + `wrong way round.`);

    const read = await agentA.call("shio_read", { address: `post:${site}${url}` });
    assert.ok(refused(read),
      `SH713/SH719: the address resolves after the race, which would make the duplicate harmless.`);
    assert.match(legible(read), /claimed by 2 posts/,
      `SH713/SH719: the address an agent was told it created is now unusable, and the refusal must `
      + `at least say why. This is the whole cost of the race: two successes, one dead address.`);
    assert.match(legible(read), /Allowed:.*id:/s,
      `SH713/SH719: the only way out of this state is by id, so the refusal has to hand both over.`);
  });

  test("SH720: the refusal names verify, and verify cannot see the drafts that caused it", async () => {
    // ASSERTED AS MEASURED. `duplicateUrls` iterates the scan's published rows, so the drafts the
    // race above leaves are invisible to the instrument the refusal sends the agent to. When SH720
    // lands, `duplicate-url` appears in the report and this test fails naming itself.
    const { url, claiming } = await raceForADuplicate("unseen");
    assert.equal(claiming.length, 2, "SH713/SH720: the fixture needs the duplicate SH719 produces.");

    const refusal = legible(await agentA.call("shio_read", { address: `post:${site}${url}` }));
    assert.match(refusal, /agent\/verify/,
      "SH713/SH720: the refusal stopped naming verify, which is one of the two ways SH720 can be "
      + "closed — the other is the check widening. Either way this assertion is the one to update.");

    const report = textOf(await agentA.call("shio_verify", { site }));
    assert.ok(!/duplicate-url/.test(report),
      `SH713/SH720: verify now reports duplicate-url for DRAFT posts — SH720 shipped, and this `
      + `test is the wrong way round.\n${report.slice(0, 400)}`);
    assert.match(report, /^report ok/m,
      `SH713/SH720: the instrument an agent was told to run answers "ok" while the site holds an `
      + `address no write can name. Being refused and then told nothing is wrong is worse than `
      + `either alone: the agent's next move is to disbelieve one of two answers.\n${report.slice(0, 300)}`);
  });

  test("SH721: a curator's edit under a concurrent plan is accepted, then discarded in silence", async () => {
    // ASSERTED AS MEASURED. Both callers get success and one edit is gone, because no op on this
    // surface can say "only if unchanged". SH721 proposes an `expect` carrying a digest; when it
    // lands, the interesting case is a refusal here rather than two successes.
    const contested = (await curator.postsIn(root)).find((post) => post.furl === "/contested");
    assert.ok(contested?.id, "SH713/SH721: the curator has no post to edit.");

    const [patched, applied] = await Promise.all([
      curator.patchPost(contested.id, { content: "CURATOR-WAS-HERE" }),
      agentA.call("shio_apply", {
        site,
        posts: [{ url: "/contested", type: "Page", folder: "/", data: { title: "PLAN", content: "PLAN" } }],
      }),
    ]);

    assert.equal(patched.status, 200,
      `SH713/SH721: the curator's PATCH was refused with ${patched.status}. A conflict answer is `
      + `the FIX — check SH721 before treating this as a regression.\n${JSON.stringify(patched.body).slice(0, 300)}`);
    assert.ok(!refused(applied),
      `SH713/SH721: the plan was refused, which is the other shape the fix could take.\n${legible(applied)}`);

    // Which of the two lands last is a scheduling accident and this asserts neither outcome — the
    // defect is that ONE of them is gone. `PLAN` is a whole-state upsert and `CURATOR-WAS-HERE` a
    // field merge, so a surface that reconciled them would show both; a surface that refused one
    // would not have returned two successes above.
    const final = textOf(await agentA.call("shio_read", { address: `post:${site}/contested` }));
    const kept = ["CURATOR-WAS-HERE", "PLAN"].filter((mark) => final.includes(mark));
    assert.equal(kept.length, 1,
      `SH713/SH721: both writes were accepted and ${kept.length} survived (${kept.join(", ") || "neither"}). `
      + `One is the measured behaviour; two means the surface learned to merge them and SH721 is `
      + `answered from a direction this test did not expect.\n${final}`);
    // The part that is not a matter of taste: nobody was told. Two writes, two successes, one
    // survivor, and no signal on either side — which is the P7 surprise SH721 exists to remove.
    assert.ok(!/conflict|stale|changed since/i.test(JSON.stringify(patched.body)),
      "SH713/SH721: the curator's 200 now carries a conflict signal, which is progress this test "
      + "should be rewritten around rather than passing quietly.");
  });
});
