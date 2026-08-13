/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH708 — the read half of the tool surface, spoken over the wire against content this suite seeds.
 *
 * ## What SH707 measured, and what this closes
 *
 * `cli/test/live-coverage.test.mjs` counts which of the agent's verbs a suite with an instance
 * behind it has ever called. When it shipped the answer was **2 of 12 tools**, and both were reads
 * of nothing in particular: SH561 set out to prove the *transport*, not the surface it carries.
 * Everything else is proved by MockMvc, where the handler's signature stands in for the schema an
 * agent actually fills in.
 *
 * ## What a live call can be wrong about that a MockMvc test cannot
 *
 * - **The declared input schema.** An agent fills its arguments in from `tools/list`, not from a
 *   Java signature. A property renamed there is invisible to every test in `shio-app`.
 * - **The address forms** (SH79) resolved against rows that exist: `post:<site>/<furl>`, the home
 *   page's bare `post:<site>/` that the grammar does not spell, and `id:<uuid>`.
 * - **A projection on real content** rather than on a two-post fixture.
 * - **A cursor that has to survive a second call** — `shio_changes` hands one back and it is only a
 *   cursor if the next request can use it.
 * - **A refusal an agent has to read**, which over this transport is a result or a JSON-RPC error
 *   and never a thrown exception.
 *
 * ## The fixture
 *
 * One scratch site, seeded through the batch surface, torn down through the same `ConsoleSession`
 * dance `replication.conformance.mjs` uses — so a red run leaves the instance where it found it and
 * `SHIO_KEEP_FIXTURES` still holds when somebody needs to look at the wreckage.
 *
 * Two posts collide on a search term deliberately: a `find` that returns one row proves nothing
 * about ranking or about the `%`-wrapping SH131 is a whole invariant about.
 */
import assert from "node:assert/strict";
import { after, before, describe, test } from "node:test";

import {
  ConsoleSession, batch, connection, errorsIn, keepsFixtures, requireInstance, scratchSite,
} from "./support/instance.mjs";
import { openMcp } from "./support/mcp-stdio.mjs";

/** The term both seeded posts carry, so `find` has to choose between rows rather than find one. */
const TERM = "kestrel";

/** Every read-side tool this suite is accountable for, which is SH707's uncovered read list. */
const READ_TOOLS = [
  "shio_context", "shio_read", "shio_find", "shio_changes",
  "shio_digest", "shio_verify", "shio_assets", "shio_marketplace",
];

describe("SH708: every read tool, over the wire, against content that exists", () => {
  const conn = connection();
  const site = scratchSite("Read");
  let mcp;
  let called;

  /** The text of a tool result, which is what the agent actually reads. */
  const textOf = (answer) => (answer?.result?.content ?? []).map((b) => b.text ?? "").join("\n");

  /** Call a tool and record that it was called, so the closing assertion is about this run. */
  async function call(name, args = {}) {
    const answer = await mcp.call(name, args);
    called.add(name);
    return answer;
  }

  before(async () => {
    await requireInstance();
    called = new Set();

    const seeded = await batch(conn, [
      { op: "site.upsert", address: `site:${site}` },
      { op: "folder.upsert", address: `folder:${site}/notes` },
      // The home page: `post:<site>/` is the one address form the grammar does not spell (SH433),
      // and it is the one an agent gets wrong first.
      {
        op: "post.upsert", address: `post:${site}/`, type: "Page", folder: "/",
        data: { title: "Home", content: `A ${TERM} on the roof.` },
      },
      {
        op: "post.upsert", address: `post:${site}/notes/second`, type: "Page", folder: "/notes",
        data: { title: `Second ${TERM}`, content: `Another ${TERM}, one folder down.` },
      },
    ]);

    assert.ok(!errorsIn(seeded),
      `SH708: the fixture did not seed, so nothing below is evidence about the read tools — it is `
      + `evidence about the write ones. ${JSON.stringify(seeded).slice(0, 600)}`);

    mcp = await openMcp(conn);
  });

  after(async () => {
    await mcp?.close();
    if (keepsFixtures()) return;
    await new ConsoleSession(conn).deleteSite(site).catch(() => {});
  });

  test("shio_context: the one call P2 promises, and it names the site it was asked about", async () => {
    const answer = await call("shio_context", { site });

    assert.ok(!answer.error, `shio_context failed: ${JSON.stringify(answer.error)}`);
    assert.ok(textOf(answer).includes(site),
      "SH708: the pack came back without the site it was scoped to. A context that answers about "
      + "the instance when it was asked about one site is the difference between one call and a "
      + "session, which is the whole of P2.");
  });

  test("shio_read: the home page answers on the address the grammar does not spell", async () => {
    const answer = await call("shio_read", { address: `post:${site}/` });

    assert.ok(!answer.error, `shio_read failed on the bare home address: ${JSON.stringify(answer.error)}`);
    assert.match(textOf(answer), /Home/,
      "SH708: `post:<site>/` is the home page and SH433 is the task about it being unspellable. "
      + "Nothing in shio-app can prove an agent reaches it, because a MockMvc test is handed the "
      + "resolved address rather than having to write one.");
  });

  test("shio_read: a refusal is legible, and it is a frame rather than a crash", async () => {
    const answer = await call("shio_read", { address: `post:${site}/definitely-not-here` });

    assert.ok(answer.error || answer.result,
      "SH708: a refusal that arrives as neither a result nor an error hangs the client");
    const text = JSON.stringify(answer);
    assert.ok(!text.includes("undefined") && text.length > 40,
      `SH708: an agent recovers from what the refusal says (P6), so an empty envelope is the `
      + `failure: ${text.slice(0, 300)}`);
  });

  test("shio_find: two rows collide on the term, and both come back", async () => {
    const answer = await call("shio_find", { q: TERM, site });

    assert.ok(!answer.error, `shio_find failed: ${JSON.stringify(answer.error)}`);
    const text = textOf(answer);
    assert.match(text, /Home/, "SH708: the home page carries the term and is missing from the result");
    assert.match(text, /Second/,
      "SH708: the second post carries it too. A find asserted against a single match proves nothing "
      + "about the `%`-wrapped predicate SH131 is an invariant about — a double-normalised term "
      + "searches `%%term%%` and matches nothing, which reads as an empty index rather than a bug.");
  });

  test("shio_find: a term nothing carries is an empty answer, not an error", async () => {
    const answer = await call("shio_find", { q: "zzzznothingcarriesthis", site });

    assert.ok(!answer.error,
      `SH708: an empty result is a legitimate answer and must not be framed as a failure: `
      + `${JSON.stringify(answer.error)}`);
    assert.ok(!textOf(answer).includes("Second"),
      "SH708: a search for nothing returned a row, which means the term was ignored");
  });

  test("shio_changes: the cursor it hands back is one the next call can use", async () => {
    const first = await call("shio_changes", { site });
    assert.ok(!first.error, `shio_changes failed: ${JSON.stringify(first.error)}`);

    const text = textOf(first);
    const cursor = /(?:cursor|since)[=:]\s*"?([0-9TZ:.\-]{8,})"?/i.exec(text)?.[1];
    assert.ok(cursor,
      `SH708: no cursor in the response. A feed whose cursor an agent cannot find is a feed it must `
      + `re-read in full every time, which is SH232's whole complaint. Body: ${text.slice(0, 400)}`);

    const second = await call("shio_changes", { site, since: cursor });
    assert.ok(!second.error,
      `SH708: the cursor this surface handed out was refused on the next call — the one thing a `
      + `cursor has to survive, and a round trip is the only place it can be shown: `
      + `${JSON.stringify(second.error)}`);
  });

  test("shio_digest: called twice with nothing changed, it does not change", async () => {
    const first = await call("shio_digest", { address: `post:${site}/` });
    const second = await call("shio_digest", { address: `post:${site}/` });

    assert.ok(!first.error && !second.error,
      `shio_digest failed: ${JSON.stringify(first.error ?? second.error)}`);
    assert.equal(textOf(first), textOf(second),
      "SH708: a digest that moves while the content does not is worse than none — P9 offers it as "
      + "cheap textual proof, and proof that changes on its own is what an agent would chase.");
  });

  test("shio_verify: a site with real rows is verified, and the answer says which", async () => {
    const answer = await call("shio_verify", { site });

    assert.ok(!answer.error, `shio_verify failed: ${JSON.stringify(answer.error)}`);
    assert.ok(textOf(answer).length > 0,
      "SH708: verify answered with no text at all, so an agent closing the perception loop (P9) "
      + "gets nothing to read");
  });

  test("shio_assets and shio_marketplace answer on a site that has neither", async () => {
    // The empty case, which is the one a MockMvc fixture is least like: a seeded site has no
    // uploads and this instance has no installed packages, and both must be an answer.
    const assets = await call("shio_assets", { site });
    const market = await call("shio_marketplace", {});

    assert.ok(!assets.error, `shio_assets failed on an empty site: ${JSON.stringify(assets.error)}`);
    assert.ok(!market.error, `shio_marketplace failed: ${JSON.stringify(market.error)}`);
    assert.ok(textOf(market).length > 0,
      "SH708: the catalogue answered with nothing. An empty catalogue and an unreachable one read "
      + "the same to an agent unless the response says which (SH679's own finding).");
  });

  test("SH708: this run called every read tool it is accountable for", async () => {
    // The guard on the suite rather than on the product. Skipping a tool here would quietly lower
    // the number SH707 prints, and a coverage suite that can lose a tool without failing is the
    // shape that instrument exists to refuse.
    assert.deepEqual([...called].sort(), [...READ_TOOLS].sort(),
      "SH708: the tools this run actually spoke are not the ones it is accountable for. If a tool "
      + "was dropped, `live-coverage.test.mjs` will keep reporting it covered because the call site "
      + "is still in the file — which is exactly the mention-versus-call confusion SH707 was "
      + "written to avoid.");
  });
});
