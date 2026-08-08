/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH560 — the corpus's **second driver**: the same route table, behind a real socket.
 *
 * ## The half that was declared and had no consumer
 *
 * `fixtures/source-sites/corpus.mjs` was built with two drivers on purpose — `injectedFetch` for the
 * fast tier and `serve`, a real `node:http`, for this one — and the argument for the pair is that
 * **a divergence between the tiers is then a statement about the boundary rather than about the
 * fixture**. Until this file, `serve` was exported and called by nothing, so that argument was
 * unmade: one driver ran, and every claim the corpus makes was a claim about an injected `fetch`.
 * The same failure `source-corpus.test.mjs` group K exists to catch, one level up — a declaration
 * nobody reads.
 *
 * ## What this tier can say that the fast one structurally cannot
 *
 * Three things, and each of them is about a **request** rather than about an artefact:
 *
 * - **what was asked for.** `served.requested` is every path that reached the server. "Absent from
 *   the source" and "absent from the capture" are opposite claims (SH387), and only a socket can
 *   tell them apart — an injected fetch answers whatever it is asked and records nothing about
 *   refusing.
 * - **what the platform's own `fetch` does with the answer.** The fake resolves a 301 as a 301;
 *   `undici` follows it and hands back the destination with `res.url` moved. That difference is not
 *   a fixture detail, it is *the* reason SH563's redirect row is hard — and it was invisible while
 *   one driver ran.
 * - **that the browser-shaped archetype is refused rather than skipped.** `spa` declares
 *   `needsBrowser`, and `instance.mjs`'s rule — absent is a failure, never a skip — has to hold for
 *   a missing browser too, or the corpus quietly stops covering the one archetype it cost the most
 *   to write.
 *
 * ## Why this scenario does not call `requireInstance`
 *
 * Every other file here does, and it is right that they do: their boundary is the running Shio. This
 * one's boundary is the **socket**, and requiring an instance it never speaks to would be a premise
 * the file does not use — a red run blamed on a Shio that had nothing to do with it. It lives here
 * rather than in `cli/test/` for the reason that directory states about itself: `pnpm test` runs
 * with no instance, no browser and **no socket**, and all three of the claims above need one of the
 * three.
 */
import assert from "node:assert/strict";
import { describe, test } from "node:test";

import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { INVENTORY_FILE } from "../src/clone.mjs";
import { INSTALL } from "../src/browser.mjs";
import * as clone from "../src/commands/clone.mjs";
import { injectedFetch, loadAll, serve } from "../../fixtures/source-sites/corpus.mjs";

/** The origin the fast tier serves at, so the two captures are compared against the same paths. */
const INJECTED_ORIGIN = "https://fixture.test";

const corpus = await loadAll();

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/** The flags the archetype declares, so both drivers crawl it the same way. */
function cloneFlags(entry) {
  const declared = entry.declaration.clone ?? {};
  return {
    markup: declared.markup ?? true,
    assets: declared.assets ?? true,
    depth: declared.depth ?? 2,
    site: `Corpus${entry.id.replace(/[^A-Za-z0-9]/g, "")}`,
  };
}

/** The seed, against whichever origin the driver under test is serving at. */
function seedOf(entry, origin) {
  return new URL(entry.declaration.clone?.seed ?? "/", origin).href;
}

/** One capture through the injected fetch — the fast tier's driver, run here for comparison only. */
async function captureInjected(entry) {
  const cwd = await mkdtemp(join(tmpdir(), `shio-sh560-fake-${entry.id}-`));
  const source = injectedFetch(entry.table, INJECTED_ORIGIN);
  const ctx = { env: {}, cwd, stdout: sink(), stderr: sink(), fetch: source.fetch, fs: nodeFs };
  try {
    await clone.run(cloneFlags(entry), [seedOf(entry, INJECTED_ORIGIN)], ctx);
    return { inventory: JSON.parse(await readFile(join(cwd, INVENTORY_FILE), "utf8")), requested: source.requested };
  } finally {
    await rm(cwd, { recursive: true, force: true }).catch(() => {});
  }
}

/**
 * One capture over a real socket, with the platform's own `fetch`.
 *
 * <p>`ctx.fetch` is `globalThis.fetch` and not a wrapper: the point of this tier is that nothing
 * between the command and the network is ours, which is the same bargain `replication.conformance`
 * makes and the reason SH452's redirect-and-content-type behaviour is observable at all.
 */
async function captureServed(entry, { flags = {}, ctx: extra = {} } = {}) {
  const served = await serve(entry.table);
  const cwd = await mkdtemp(join(tmpdir(), `shio-sh560-live-${entry.id}-`));
  const ctx = {
    env: { ...process.env },
    cwd,
    stdout: sink(),
    stderr: sink(),
    fetch: globalThis.fetch,
    fs: nodeFs,
    ...extra,
  };
  try {
    await clone.run({ ...cloneFlags(entry), ...flags }, [seedOf(entry, served.origin)], ctx);
    return {
      inventory: JSON.parse(await readFile(join(cwd, INVENTORY_FILE), "utf8")),
      requested: served.requested,
      origin: served.origin,
      stdout: ctx.stdout.text,
    };
  } finally {
    await served.close();
    await rm(cwd, { recursive: true, force: true }).catch(() => {});
  }
}

const paths = (rows) => (rows ?? []).map((row) => row.path).sort();

// -------------------------------------------------------------------------------------------------
// The archetype, over a socket, against the declaration the fast tier reads
// -------------------------------------------------------------------------------------------------

describe("SH560 live: the corpus over a real socket", () => {
  for (const entry of corpus.filter((each) => !each.declaration.needsBrowser)) {
    const { declaration } = entry;

    test(`${entry.id}: the same archetype captured through undici, not through a fake`, async () => {
      const run = await captureServed(entry);

      assert.deepEqual(
        paths(run.inventory.pages),
        [...declaration.expect.pages].sort(),
        `${entry.id}: the pages a real crawl of this archetype records. Declared once, in the `
          + "archetype, and read here by the driver the fast tier substitutes — which is the only "
          + "way a difference between the two can be attributed to the boundary rather than to a "
          + `second copy of the expectation (catches ${declaration.catches.join(" ")})`,
      );
      assert.deepEqual(
        paths(run.inventory.assets),
        [...(declaration.expect.assets ?? [])].sort(),
        `${entry.id}: and its assets, fetched as bytes over a socket rather than sliced out of a `
          + "Buffer the fixture already held. SH204's point: a fake answers `arrayBuffer` from a "
          + "string, so binary-safety is the property it structurally cannot assert",
      );
      assert.deepEqual(
        paths(run.inventory.problems),
        [...(declaration.expect.problems ?? [])].sort(),
        `${entry.id}: a page the crawl could not read is a problem here too — a driver that turns `
          + "one into a silent absence would make every count above a lower bound",
      );
    });

    test(`${entry.id}: the walk is bounded by what it ASKED for, not by what it kept (SH387)`, async () => {
      const run = await captureServed(entry);

      // The claim only this tier can make. `unfollowed` says the capture did not keep a page; the
      // server's log says nobody ever asked for it. They are different sentences, and a crawl that
      // fetched a page and then discarded it has still spent somebody else's bandwidth and still
      // ignored their robots.txt.
      for (const row of declaration.expect.unfollowed ?? []) {
        assert.ok(
          !run.requested.includes(row),
          `SH387 / ${entry.id}: ${row} is declared unfollowed and the server was asked for it `
            + `anyway. Asked: ${run.requested.join(" ")}`,
        );
      }

      // And nothing outside the archetype at all. A request for a path the route table does not
      // hold is either a crawler inventing URLs or a resolution bug, and both answer 404 — so the
      // capture looks identical and only the server knows.
      const unknown = run.requested.filter((path) => !entry.table.has(path)
        && !entry.table.has(path.split("?")[0])
        && path !== "/robots.txt");
      assert.deepEqual(
        unknown,
        [],
        `${entry.id}: the crawl asked for ${unknown.length} path(s) this archetype does not serve. `
          + "Every one of them answered 404 and left no trace in the inventory, which is why an "
          + `injected fetch cannot see this at all.\n  asked: ${run.requested.join(" ")}`,
      );
    });
  }

  // -----------------------------------------------------------------------------------------------
  // The two drivers, compared — and the divergence is the deliverable, not a failure to suppress
  // -----------------------------------------------------------------------------------------------

  for (const entry of corpus.filter((each) => !each.declaration.needsBrowser)) {
    test(`${entry.id}: both drivers of one route table agree about what was captured`, async () => {
      const [fake, live] = await Promise.all([captureInjected(entry), captureServed(entry)]);

      assert.deepEqual(
        paths(live.inventory.pages),
        paths(fake.inventory.pages),
        `${entry.id}: the fast tier and this one read the same route table, so a page one of them `
          + "has and the other does not is a statement about the boundary between them — an "
          + "injected `fetch` resolving a redirect the platform's own would follow, a content type "
          + "the fake asserts and undici normalises. That is the whole reason the corpus was built "
          + "with two drivers instead of one, and it is unfalsifiable while only one of them runs.",
      );
      assert.deepEqual(
        paths(live.inventory.assets),
        paths(fake.inventory.assets),
        `${entry.id}: and the same assets. A stylesheet's own url() is resolved against the URL the `
          + "stylesheet was served from (SH452), so a driver that reports a different final URL "
          + "moves every reference inside it",
      );
    });
  }
});

// -------------------------------------------------------------------------------------------------
// The archetype that needs a browser: refused, never skipped
// -------------------------------------------------------------------------------------------------

describe("SH560 live: the browser-shaped archetype is refused, not skipped", () => {
  for (const entry of corpus.filter((each) => each.declaration.needsBrowser)) {
    /**
     * `scenarios.headless` — read here, and the only place it can honestly be read.
     *
     * <p>It is the archetype's claim about whether its **browser-driven** capture runs. `false` says
     * it does not, and the danger of a false like that is the one `instance.mjs` opens with: a suite
     * that has a mode in which it notices nothing and says so quietly. So the case below always
     * runs, and it has exactly two acceptable outcomes — a refusal that names the install line, or a
     * capture that genuinely reached the page only a browser can find. A third outcome, "the run
     * quietly did neither", is what a `skip` would have produced.</p>
     */
    test(`${entry.id}: --render either drives a browser or teaches the install line (SH434)`, async () => {
      const headless = entry.declaration.scenarios?.headless === true;
      let refusal = null;
      let run = null;
      try {
        run = await captureServed(entry, { flags: { render: true } });
      } catch (error) {
        refusal = error;
      }

      if (refusal) {
        // Deliberately says nothing about `headless`. Whether a browser resolves is a property of
        // the machine, and an assertion here would make the declaration mean "this laptop", which
        // is the one thing a corpus entry must never mean.
        assert.match(
          refusal.message,
          new RegExp(INSTALL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
          `SH434 / ${entry.id}: a machine with no browser must get the two commands that fix it, in `
            + "one line, and must get them **before** the crawl has asked the source for anything. "
            + "A skip here would be the corpus reporting that its most expensive archetype is "
            + `covered.\n  message: ${refusal.message}`,
        );
        return;
      }

      // A browser resolved. Then the archetype's own declaration is the thing under test: it says
      // the second route is reachable only from a link a script creates, and a run that reached it
      // has proved both the flag and the fixture.
      for (const path of entry.declaration.unreachableWithoutBrowser ?? []) {
        assert.ok(
          run.inventory.pages.some((page) => page.path === path),
          `${entry.id}: playwright resolved and --render still did not reach ${path}, which this `
            + "archetype exists to make reachable. Either the browser could not launch — that is "
            + "`npx playwright install chromium`, the second half of the install line — or the "
            + `crawl is not using the rendered DOM.\n  pages: ${paths(run.inventory.pages).join(" ")}`,
        );
      }
      assert.ok(
        headless,
        `${entry.id}: --render ran to completion on this machine, so scenarios.headless is `
          + "understating what the corpus covers. Set it to true — the field is the corpus's own "
          + "statement about which scenarios have been run, and a false one makes the browser tier "
          + "look impossible when it is merely optional.",
      );
    });
  }
});
