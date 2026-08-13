/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH709 — the write half, where a guard that fails leaves a real row behind.
 *
 * ## Why a mock cannot do this one
 *
 * `shio_write`, `shio_apply`, `shio_publish` and `shio_remember` are the four tools that change
 * something, and they carry the guards P7 exists for: draft by default, a `dryRun` that must write
 * nothing, confirm tokens on the destructive paths. A mock proves the *branch was taken*. Only an
 * instance proves the row is **absent afterwards** — and every one of these guards fails in the same
 * direction, which is the one a branch assertion cannot see: the response says refused and the
 * write happened anyway.
 *
 * So every scenario here is **call, then look**, and the looking is a separate read of the store
 * rather than a re-reading of the same answer.
 *
 * ## How "did it land" is asked
 *
 * Through the **published projection**, which is the discriminator the product already has:
 * `shio_read` with `state: "PUBLISHED"` refuses with *"has no published row … has never been
 * published"* until something publishes it. That is a store read, not a rendered page — the
 * renderer needs a theme and a layout and is `render.conformance.mjs`'s subject. Saying which of
 * the two this asserts matters: a suite that claimed "a visitor cannot see it" while reading the
 * draft table would be asserting the opposite of what it printed.
 *
 * The CDA would be the other half and is deliberately not used: `/api/v2/cda/**` takes an API token
 * and refuses a console credential (SH2), so reaching it would mean provisioning a token to prove
 * something this projection already answers.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance,
  requireVocabulary, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** Every write-side tool this suite is accountable for — SH707's uncovered write list. */
const WRITE_TOOLS = ["shio_write", "shio_apply", "shio_publish", "shio_remember"];

describe("SH709: the four tools that change something, against a store that keeps what they do", () => {
  const conn = connection();
  const site = scratchSite("Write");
  let mcp;
  let called;

  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");

  async function call(name, args = {}) {
    const answer = await mcp.call(name, args);
    called.add(name);
    return answer;
  }

  /**
   * Whether the store holds a row for this address in that state.
   *
   * <p>Deliberately asked through `shio_read` rather than through the tool under test: a write tool
   * that reported success and wrote nothing would agree with itself all day.</p>
   */
  async function exists(address, state) {
    const answer = await mcp.call("shio_read", state ? { address, state } : { address });
    // `isError` is the whole answer and the first draft of this helper missed it, which made two
    // tests report a product defect that was this function's. Over MCP a refusal is a *result* —
    // `{ content: [...], isError: true }` — not a JSON-RPC `error`, and a reader that only checks
    // the latter counts every refusal as a row that exists.
    if (answer.error || answer.result?.isError) return false;
    return textOf(answer).length > 0;
  }

  before(async () => {
    await requireInstance();
    // SH724 — `Page` is SH95 vocabulary, not a seeded type: without this the fixture below is
    // refused on any instance nobody built by hand, and every scenario behind it is cancelled.
    await requireVocabulary(conn);
    called = new Set();
    const seeded = await batch(conn, [{ op: "site.upsert", address: `site:${site}` }]);
    assert.ok(!errorsIn(seeded), `SH709: the site did not seed: ${JSON.stringify(seeded).slice(0, 400)}`);
    mcp = await openMcp(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  test("shio_write: a dry run reports a change and leaves nothing behind", async () => {
    const address = `post:${site}/ghost`;
    const answer = await call("shio_write", {
      dryRun: true,
      ops: [{
        op: "post.upsert", address, type: "Page", folder: "/",
        data: { title: "Ghost", content: "Should never exist." },
      }],
    });

    assert.ok(!answer.error, `shio_write dry run failed: ${JSON.stringify(answer.error)}`);
    assert.ok(textOf(answer).length > 0,
      "SH709: a dry run that reports nothing is useless — the whole point is to read the diff first");

    assert.equal(await exists(address), false,
      "SH709: `dryRun` wrote a row. This is the guard failing in the direction a mock cannot see — "
      + "the response says nothing was written and the post is there. P7 is the law that a human is "
      + "never surprised, and a dry run that writes is the most direct violation of it available.");
  });

  test("shio_write: what lands is a draft, with no published row", async () => {
    const address = `post:${site}/first`;
    const answer = await call("shio_write", {
      ops: [{
        op: "post.upsert", address, type: "Page", folder: "/",
        data: { title: "First", content: "Written by the write tool." },
      }],
    });

    assert.ok(!answer.error, `shio_write failed: ${JSON.stringify(answer.error)}`);
    assert.equal(await exists(address, "DRAFT"), true,
      "SH709: the write reported success and the draft is not in the store");
    assert.equal(await exists(address, "PUBLISHED"), false,
      "SH709: a plain write produced a PUBLISHED row. Draft-default is the guard that keeps an "
      + "agent's work off the live site until a person says so, and nothing in a mock can prove the "
      + "published projection is still empty afterwards.");
  });

  test("shio_write: the same upsert twice is one row", async () => {
    const address = `post:${site}/first`;
    const again = await call("shio_write", {
      ops: [{
        op: "post.upsert", address, type: "Page", folder: "/",
        data: { title: "First", content: "Written by the write tool." },
      }],
    });

    assert.ok(!again.error, `the second upsert failed: ${JSON.stringify(again.error)}`);

    const found = await mcp.call("shio_find", { q: "First", site });
    const hits = (textOf(found).match(/first/gi) ?? []).length;
    assert.ok(hits > 0 && hits < 6,
      `SH709: upserting the same address twice produced ${hits} mentions of it. Idempotence is what `
      + "lets an agent retry a batch it is not sure landed, and a second row is the failure that "
      + "makes retrying dangerous.");
  });

  test("shio_publish: the published row appears only after it is asked for", async () => {
    const address = `post:${site}/first`;
    assert.equal(await exists(address, "PUBLISHED"), false,
      "SH709: the precondition — nothing has published this yet");

    const answer = await call("shio_publish", { address: [address] });

    assert.ok(!answer.error, `shio_publish failed: ${JSON.stringify(answer.error)}`);
    assert.equal(await exists(address, "PUBLISHED"), true,
      "SH709: publish reported success and the published projection is still empty. This is the "
      + "assertion the whole draft/publish split rests on, and it has never been made against a "
      + "store — a mock returns whatever the handler decided to say.");
  });

  test("shio_publish: a dry run does not take anything down", async () => {
    const address = `post:${site}/first`;
    const answer = await call("shio_publish", { address: [address], unpublish: true, dryRun: true });

    assert.ok(!answer.error, `shio_publish dry run failed: ${JSON.stringify(answer.error)}`);
    assert.equal(await exists(address, "PUBLISHED"), true,
      "SH709: a dry-run unpublish took the page down. The destructive direction is where a dry run "
      + "that lies costs the most, because the thing it removed was live.");
  });

  test("shio_apply: a plan whose second post is invalid leaves the first one out too", async () => {
    // The commit-order question `ShAgentCommitOrderTest` asks of a mock, asked where the
    // transaction is real: a plan is one unit, so a document that cannot be applied whole must not
    // be applied in part.
    // SH718 — `shio_apply.posts[]` takes `url`, the friendly URL, NOT `address`. Written with
    // `address` this case passed while asking nothing: SH331's strict-argument guard refused the
    // call for the unknown key, before any post-type was looked at, and the absence asserted below
    // was the absence of an attempt. A refusal is not evidence unless it is the RIGHT refusal, so
    // the message is checked rather than the flag.
    const good = `post:${site}/plan-one`;
    const answer = await call("shio_apply", {
      site,
      posts: [
        { url: "/plan-one", type: "Page", folder: "/", data: { title: "Plan one", content: "ok" } },
        // No such post-type. The refusal has to arrive before anything is committed.
        { url: "/plan-two", type: "NoSuchTypeAtAll", folder: "/", data: { title: "Plan two" } },
      ],
    });

    assert.ok(Boolean(answer.error) || answer.result?.isError,
      `SH709: a plan naming a post-type that does not exist was accepted: ${textOf(answer).slice(0, 300)}`);
    assert.match(textOf(answer) || JSON.stringify(answer.error), /NoSuchTypeAtAll|post-type/i,
      "SH718: the plan was refused for something other than the missing post-type, so this case is "
      + "back to proving nothing. Assert the reason, not the flag — that is the whole finding.");

    assert.equal(await exists(good), false,
      "SH709: the first post of a refused plan survived it. A partially applied plan is the worst "
      + "outcome available here — the agent is told the write failed and the site has half of it, "
      + "so the next retry is reasoning from a state nobody described.");
  });

  test("shio_remember: the note outlives the connection that wrote it", async () => {
    const key = "sh709.live-write";
    const note = "The write half is spoken over the wire now.";
    const answer = await call("shio_remember", { key, note, site });

    assert.ok(!answer.error, `shio_remember failed: ${JSON.stringify(answer.error)}`);

    // A second connection, because the whole point of a note is that it is not in this session.
    const second = await openMcp(conn);
    try {
      const pack = await second.call("shio_context", { site });
      assert.match(textOf(pack), new RegExp(note.slice(0, 24).replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
        "SH709: the note is not in the pack a fresh session reads. `shio_remember` exists so the "
        + "next agent does not re-derive a convention, and a note only this connection can see is "
        + "the one thing it must not be.");
    } finally {
      await second.close();
    }
  });

  test("SH709: this run called every write tool it is accountable for", async () => {
    assert.deepEqual([...called].sort(), [...WRITE_TOOLS].sort(),
      "SH709: the tools this run actually spoke are not the four it is accountable for. Dropping "
      + "one would quietly lower the number `live-coverage.test.mjs` prints while its call site "
      + "stays in the file — the mention-versus-call confusion SH707 exists to refuse.");
  });
});
