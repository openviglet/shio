/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH560 — the source corpus, driven offline: one suite, every archetype.
 *
 * ## What this file is, against the two that already exist
 *
 * `replication-chain.test.mjs` (SH422) runs the chain once, over one source, and its subject is the
 * **seams** — that each phase reads what the last one wrote. It is the right shape for that and it
 * cannot be the shape for this: its fixture is a literal in its own file, so the only source traits
 * it can hold are the ones its author thought to write.
 *
 * This file's subject is the **source**. `fixtures/source-sites/` holds one directory per archetype,
 * each declaring what a capture of it should contain and — the field that makes the corpus worth
 * keeping — the task ids it exists to catch. The chain below is the same chain; what changes is that
 * it runs once per archetype and every assertion reads a declaration rather than a constant.
 *
 * ## Why the assertions are expected to be red
 *
 * `.claude/skills/shio-replication-test/SKILL.md` is a hand-run checklist, and every rule in it was
 * paid for by a defect that shipped green. Most of those rules are still only in that file, which
 * means they run when a person remembers to run them. Encoding one as an assertion is therefore
 * **expected to fail on arrival** — that is the SH338 order, the instrument before the fixes — and a
 * red case here is a finding to file, never a case to soften. Each one names its id, so a reader can
 * tell a defect from a fixture mistake without running anything.
 *
 * The three the checklist insists on and that nothing automated held before this file:
 *
 * - **count, do not eyeball** — a page that looks annotated can be five props over a grid whose rows
 *   carry none (SH465, then SH475, and the visible page looked reasonable at every stage);
 * - **every reference resolves, resolved against the serving URL** — filtering on a leading `/`
 *   reported 0 broken on a page with fifteen missing images (SH512);
 * - **both spellings of a directory path answer**, one canonical and one redirecting, the way the
 *   source does (SH467).
 *
 * ## No instance, no browser, no socket
 *
 * Everything here is `pnpm test`, so the drivers are `injectedFetch` and `convert --out`. That is a
 * real bound and it is declared per archetype: `spa` carries `needsBrowser`, and the live tier
 * (SH561) is where it is *refused* rather than skipped. What the fast tier can still see is most of
 * the checklist, because the conversion document holds the layout, the theme and every field value
 * before anything is uploaded.
 */
import assert from "node:assert/strict";
import { describe, test } from "node:test";

import * as nodeFs from "node:fs/promises";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { INVENTORY_FILE, INVENTORY_VERSION } from "../src/clone.mjs";
import { PLAN_FILE } from "../src/propose.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as convert from "../src/commands/convert.mjs";
import * as propose from "../src/commands/propose.mjs";
import { injectedFetch, loadAll } from "../../fixtures/source-sites/corpus.mjs";

/** The origin every archetype is served at. Fixed, because a capture records paths against it. */
const ORIGIN = "https://fixture.test";

/**
 * The assertions that are red at HEAD, each owned by an open task.
 *
 * <p>`docs/agents/cli.md` records why this file needs one: **an instrument that cries wolf in the
 * gate is worse than one outside it**, because the next red run is the one nobody reads. And SH338
 * says the instrument lands *before* the fixes, so a corpus written honestly is red on arrival.
 * Both are true at once, and a named list is what satisfies them together — the same device
 * `assertion-debt.test.mjs` uses, and deliberately **not** a ceiling: a ceiling lets one task pay
 * the debt and the next task spend it.
 *
 * <p>Checked in both directions. A held case that has gone **green fails the run**, naming the id
 * to delete — otherwise the list is where findings go to be forgotten. And a held case whose
 * assertion nobody runs fails too, because a stale key is a finding that quietly stopped being
 * checked.
 */
const OPEN = JSON.parse(
  await readFile(new URL("../../fixtures/source-sites/open-findings.json", import.meta.url), "utf8"),
);

const held = new Map(OPEN.findings.map((finding) => [`${finding.archetype}/${finding.assertion}`, finding]));
const exercised = new Set();
const reported = [];

/**
 * Run one finding-bearing assertion under the held list.
 *
 * <p>Three outcomes, and the third is the one that makes this honest: red and held is **reported**,
 * red and unheld is a **failure** (a new finding), and green and held is a **failure** too, because
 * the fix landed and the ledger has not been told.
 */
function holding(archetype, assertion, check) {
  const key = `${archetype}/${assertion}`;
  exercised.add(key);
  const entry = held.get(key);
  let failure = null;
  try {
    check();
  } catch (error) {
    failure = error;
  }
  if (!entry) {
    if (failure) throw failure;
    return;
  }
  if (!failure) {
    throw new assert.AssertionError({
      message: `${entry.id} is GREEN: ${key} passes, and open-findings.json still holds it.\n`
        + `  what it held: ${entry.what}\n`
        + "  delete that entry — a fix that lands without the list being told turns this suite back "
        + "into one that reports a number nobody can act on",
    });
  }
  reported.push({ ...entry, key, message: failure.message });
}

/**
 * A friendly URL's grammar, as the address forms allow it (SH79).
 *
 * <p>`post:<site>/<friendly-url>` has no room for a `?`: the character terminates the address and
 * the remainder is a query on the request that carries it. So a captured path containing one is a
 * page the replica cannot address, which is SH557 — and the point of the rule being *here* is that
 * it is decidable at capture time, before 403 assets have been uploaded.
 */
const ADDRESSABLE = /^\/[A-Za-z0-9\-._~/]*$/;

const corpus = await loadAll();

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

const json = async (cwd, file) => JSON.parse(await readFile(join(cwd, file), "utf8"));

/** One archetype's chain, run in a real temp directory. The artefact between phases is the file. */
async function capture(entry, { accept = false } = {}) {
  const cwd = await mkdtemp(join(tmpdir(), `shio-sh560-${entry.id}-`));
  const source = injectedFetch(entry.table, ORIGIN);
  const ctx = { env: {}, cwd, stdout: sink(), stderr: sink(), fetch: source.fetch, fs: nodeFs };
  const declared = entry.declaration.clone ?? {};
  const seed = declared.seed ? new URL(declared.seed, ORIGIN).href : ORIGIN;

  const result = { cwd, ctx, source, dispose: () => rm(cwd, { recursive: true, force: true }) };
  result.captured = await clone.run(
    {
      markup: declared.markup ?? true,
      assets: declared.assets ?? true,
      depth: declared.depth ?? 2,
      site: siteOf(entry),
    },
    [seed],
    ctx,
  );
  result.inventory = await json(cwd, INVENTORY_FILE);
  if (!accept) return result;

  const { proposal } = await propose.run({}, [], ctx);
  result.proposal = proposal;
  await propose.run({ accept: true }, ["all"], ctx);
  result.plan = await json(cwd, PLAN_FILE);
  await convert.run({ site: siteOf(entry), out: "conversion.json" }, [], ctx);
  result.conversion = await json(cwd, "conversion.json");
  return result;
}

/** A site name per archetype: grep-able, and never one a live run would collide with. */
function siteOf(entry) {
  return `Corpus${entry.id.replace(/[^A-Za-z0-9]/g, "")}`;
}

/** A note pattern as a stable assertion key. Regex punctuation is not a key. */
function slug(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

// -------------------------------------------------------------------------------------------------
// Group A — the corpus is a catalogue, and a catalogue entry that claims nothing is a directory
// -------------------------------------------------------------------------------------------------

describe("SH560 group A: the corpus declares itself", () => {
  test("every archetype names what it is, why it exists, and the ids it catches", () => {
    assert.ok(corpus.length >= 5, `the corpus holds ${corpus.length} archetype(s); the argument for `
      + "it over a bigger fixture was that per-source traits do not compose into one server, and "
      + "fewer than five means that argument is being made by one example");

    for (const entry of corpus) {
      const declaration = entry.declaration;
      assert.equal(declaration.id, entry.id, `${entry.id}: the declaration's id is its directory`);
      assert.ok(declaration.title, `${entry.id}: a title, which is what a red run prints`);
      assert.ok(
        (declaration.why ?? "").length >= 120,
        `${entry.id}: the reason this archetype exists, at length. A fixture trait with no recorded `
          + "cause is the thing somebody deletes to make a run green",
      );
      assert.ok(
        Array.isArray(declaration.catches) && declaration.catches.length,
        `${entry.id}: at least one id. SH528's rule is that a trait exists because a defect needed `
          + "it, never because a real site happened to have it — this field is what says which",
      );
      for (const id of declaration.catches) {
        assert.match(id, /^SH\d+$/, `${entry.id}: "${id}" is not a task id`);
      }
      assert.ok(
        ["site", "page"].includes(declaration.trait),
        `${entry.id}: trait is "site" or "page" — a page trait belongs in SH528's single fixture, `
          + "and only a site trait needs its own server, which is what this corpus is for",
      );
    }
  });

  test("no two archetypes claim the same trait, and the site traits are the ones with a server", () => {
    const siteTraits = corpus.filter((entry) => entry.declaration.trait === "site");
    assert.ok(
      siteTraits.length >= 4,
      `${siteTraits.length} site-level archetype(s). These are the ones SH528 structurally cannot `
        + "absorb; a corpus that is mostly page traits is a corpus that should have been a fixture",
    );
    const ids = corpus.flatMap((entry) => entry.declaration.catches);
    assert.equal(
      new Set(ids).size,
      ids.length,
      `two archetypes catch the same id: ${ids.filter((id, i) => ids.indexOf(id) !== i).join(" ")}. `
        + "One defect, one archetype — otherwise a fix makes two cases green and neither is the proof",
    );
  });
});

// -------------------------------------------------------------------------------------------------
// Group B — the capture, per archetype, against what the archetype declares
// -------------------------------------------------------------------------------------------------

describe("SH560 group B: the capture of each archetype is what it declares", () => {
  for (const entry of corpus) {
    const { declaration } = entry;

    test(`${entry.id}: ${declaration.title}`, async () => {
      const run = await capture(entry);
      try {
        const inventory = run.inventory;
        assert.equal(inventory.version, INVENTORY_VERSION);

        assert.deepEqual(
          inventory.pages.map((row) => row.path).sort(),
          [...declaration.expect.pages].sort(),
          `${entry.id}: the pages the walk recorded. Declared in the archetype rather than in this `
            + `file, so the fixture and the expectation move together (catches ${declaration.catches.join(" ")})`,
        );

        assert.deepEqual(
          (inventory.unfollowed ?? []).map((row) => row.path).sort(),
          [...declaration.expect.unfollowed].sort(),
          `${entry.id}: "absent from the source" and "absent from the capture" are opposite claims, `
            + "and `unfollowed` is the only field that tells them apart (SH387)",
        );

        assert.deepEqual(
          (inventory.problems ?? []).map((row) => row.path).sort(),
          [...(declaration.expect.problems ?? [])].sort(),
          `${entry.id}: a page that could not be read is a problem, and a problem the archetype did `
            + "not declare is either a defect or a fixture that does not serve what it says",
        );

        assert.deepEqual(
          (inventory.assets ?? []).map((row) => row.path).sort(),
          [...(declaration.expect.assets ?? [])].sort(),
          `${entry.id}: every asset the markup and the stylesheets name. A webfont referenced only `
            + "from inside a stylesheet is the one that goes missing while every test passes (SH452)",
        );

        for (const note of declaration.expect.notes ?? []) {
          holding(entry.id, `capture-notes-${slug(note.match)}`, () => {
            assert.ok(
              (inventory.notes ?? []).some((line) => new RegExp(note.match, "i").test(line)),
              `${entry.id} / ${note.catches}: no note matching /${note.match}/i. ${note.means}.\n`
                + `  notes: ${JSON.stringify(inventory.notes ?? [], null, 2)}`,
            );
          });
        }
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group C — SH557: a captured page a replica cannot address, said at capture rather than at diff
// -------------------------------------------------------------------------------------------------

describe("SH560 group C: a page the replica cannot address is named where it can still be acted on", () => {
  for (const entry of corpus.filter((each) => (each.declaration.unaddressable ?? []).length)) {
    test(`${entry.id}: the capture names its own unaddressable pages (SH557)`, async () => {
      const run = await capture(entry);
      try {
        const paths = run.inventory.pages.map((row) => row.path);
        const unaddressable = paths.filter((path) => !ADDRESSABLE.test(path));

        assert.deepEqual(
          unaddressable.sort(),
          [...entry.declaration.unaddressable].sort(),
          `${entry.id}: the archetype and the grammar agree on which captured paths no friendly URL `
            + "can hold. If this fails the fixture is wrong, and every assertion below it is noise",
        );

        // The finding. `diff` reports these at the end of the chain, as pages the replica serves
        // nothing at — after the capture, the proposal, the upload and the publish have all run.
        // The capture already knows: it holds the path, and the grammar is a regex.
        holding(entry.id, "unaddressable-named-at-capture", () => {
          const said = (run.inventory.notes ?? []).some((note) =>
            /query string|cannot be addressed|no friendly URL/i.test(note));
          assert.ok(
            said,
            `SH557: ${unaddressable.length} of ${paths.length} captured page(s) cannot be addressed `
              + "by any friendly URL, and the capture's notes do not say so. On a real source this "
              + "was 91 of 107, reported by `diff` after 403 assets had been uploaded — a limit "
              + "discovered at the last phase is a limit discovered too late to act on.\n"
              + `  unaddressable: ${unaddressable.join(" ")}\n`
              + `  notes: ${JSON.stringify(run.inventory.notes ?? [], null, 2)}`,
          );
        });
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group D — the conversion document, which holds the layout before anything is uploaded
// -------------------------------------------------------------------------------------------------

describe("SH560 group D: the conversion document, judged before an instance sees it", () => {
  for (const entry of corpus.filter((each) => each.declaration.propose?.accept === "all"
    && each.declaration.scenarios?.authorable)) {
    const { declaration } = entry;

    test(`${entry.id}: accept everything, and read what would be sent`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        const conversion = run.conversion;
        assert.ok(conversion.posts?.length, `${entry.id}: the document carries posts`);

        assert.ok(
          conversion.posts.every((post) => !("publish" in post)),
          `${entry.id}: a conversion of somebody else's site lands in DRAFT — every post, no `
            + "exceptions (P7)",
        );

        // Check 2 of the checklist: no href carries the source's base. A verbatim href is a
        // hardcoded base and always points off-site (SH466). The document is where it is cheapest
        // to see, because the alternative is fetching the served page and reading its links.
        if (declaration.conversion?.noSourceBaseInHrefs) {
          holding(entry.id, "no-source-base-in-fields", () => {
            const base = declaration.sourceBase ?? ORIGIN;
            const offenders = conversion.posts
              .flatMap((post) => Object.entries(post.data ?? {})
                .filter(([, value]) => JSON.stringify(value ?? null).includes(base))
                .map(([field]) => `${post.url}.${field}`));
            assert.deepEqual(
              offenders,
              [],
              `SH564 / ${entry.id}: ${offenders.length} field(s) carry the source's own base `
                + `${base}, so every link they render leaves the replica for the original. SH466 `
                + "rebases root-relative hrefs at render and rightly leaves an absolute one alone, "
                + "so the phase that can tell is the one that knows what the origin was.\n"
                + `  fields: ${offenders.join(" ")}`,
            );
          });
        }

        // SH555, read one phase earlier than the run that found it. The generated layout pulls the
        // captured theme through a relation and emits its CSS; a plain `{{CSS}}` inside that block
        // is escaped, so a megabyte of stylesheet arrives with every `url()` entity-escaped and the
        // browser cannot parse it. The layout is in this document, so the brace count is checkable
        // with no instance, no publish and no page fetch.
        for (const field of declaration.conversion?.rawFields ?? []) {
          const layout = conversion.posts.find((post) => post.type === "PageLayout");
          assert.ok(layout, `${entry.id}: a Page with no layout is one the renderer answers 400 for`);
          holding(entry.id, `layout-raw-field-${field}`, () => {
            const escaped = new RegExp(`(^|[^{])\\{\\{${field}\\}\\}`);
            const raw = new RegExp(`\\{\\{\\{${field}\\}\\}\\}`);
            assert.ok(
              !escaped.test(layout.data.HTML) || raw.test(layout.data.HTML),
              `SH555 / ${entry.id}: the layout emits {{${field}}} with two braces, so Handlebars `
                + "escapes it. A captured stylesheet then reaches the browser with every url() as "
                + "&quot;…&quot; and verify reports page-reference-broken for each one. The stored "
                + "Theme post is clean, which is why nothing upstream of the render sees this.\n"
                + `  layout HTML: ${layout.data.HTML}`,
            );
          });
        }

        // The entity round trip (SH476/SH550). A title escaped twice at the source decodes to
        // `&amp;` and must not decode further; a title escaped once must arrive as the character.
        for (const [path, expected] of Object.entries(declaration.doubleEscaped ?? {})) {
          const page = conversion.posts.find((post) => post.url === path);
          assert.ok(page, `${entry.id}: no post at ${path}`);
          holding(entry.id, "title-entity-decoded", () => {
            assert.equal(
              page.data.title,
              expected,
              `SH565 / ${entry.id}: an entity survives extraction as characters and is escaped `
                + "again on render, so a title decoded the wrong number of times is wrong in the "
                + "page and in the editor field, and invisible unless it is compared. `<title>` is "
                + "RCDATA — its entities are decoded, unlike <script> and <style> — and it reaches "
                + "`Page.data.title` without passing through `propose`, so both paths are wrong.",
            );
          });
        }
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group E — the traits that are about a request, which only a route table can express
// -------------------------------------------------------------------------------------------------

describe("SH560 group E: requests that answer oddly", () => {
  for (const entry of corpus.filter((each) => each.declaration.redirects
    || (each.declaration.emptyAssets ?? []).length)) {
    const { declaration } = entry;

    test(`${entry.id}: a redirect is recorded, and a zero-byte asset is not silently dropped`, async () => {
      const run = await capture(entry);
      try {
        for (const [from, to] of Object.entries(declaration.redirects ?? {})) {
          const row = run.inventory.pages.find((page) => page.path === from);
          assert.ok(
            row,
            `SH467 / ${entry.id}: ${from} 301s to ${to} and the capture has no row for it. Both `
              + "spellings of a directory path answer on the source — one canonical, one "
              + "redirecting — and a replica that keeps only one of them breaks every link to the "
              + `other.\n  captured: ${run.inventory.pages.map((page) => page.path).join(" ")}`,
          );
          holding(entry.id, "redirect-recorded", () => {
            assert.equal(
              row.redirectedTo,
              to,
              `SH563 / ${entry.id}: the redirect must be *visible* in the inventory rather than `
                + "erased, which is the only way a later phase can reproduce it. `visit` returns "
                + "`redirectedTo` and `buildInventory` never copies it onto the row, so both "
                + `spellings are stored as two pages with the same body.\n  row: ${JSON.stringify(row)}`,
            );
          });
        }

        for (const path of declaration.emptyAssets ?? []) {
          const asset = run.inventory.assets.find((each) => each.path === path);
          assert.ok(
            asset,
            `SH514 / ${entry.id}: ${path} is zero bytes and absent from the inventory. An empty `
              + "file the upload refuses is a reference that 404s on the replica, and a capture "
              + "that drops it makes the cause unfindable",
          );
          assert.ok(
            asset.file || asset.error,
            `${entry.id}: ${path} was either stored or the reason it was not is recorded — one of `
              + "the two, because a row with neither reads as a success",
          );
        }
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group F — the archetype the fast tier cannot finish, declared rather than quietly skipped
// -------------------------------------------------------------------------------------------------

describe("SH560 group F: the bound of this tier is declared, not discovered", () => {
  for (const entry of corpus.filter((each) => each.declaration.needsBrowser)) {
    test(`${entry.id}: a fetch-only capture is thin, and the tool says which flag fixes it`, async () => {
      const run = await capture(entry);
      try {
        // Not an assertion that the capture is *good* — it is an assertion that it is *honest*. A
        // client-rendered source yields one thin page to a fetch, and the whole value of the
        // archetype is that the note teaches --render rather than reporting a captured site.
        for (const path of entry.declaration.unreachableWithoutBrowser ?? []) {
          assert.ok(
            !run.inventory.pages.some((page) => page.path === path),
            `${entry.id}: ${path} is reachable only from a link a script creates, so a fetch-only `
              + "crawl must not have it. If it does, the fixture is serving markup it should not",
          );
        }
        assert.ok(
          (run.inventory.notes ?? []).some((note) => /--render/.test(note)),
          `${entry.id}: the crawl produced ${run.inventory.pages.length} page(s) of a source whose `
            + "every word is written by a script, and its notes do not name --render. `clone` "
            + "teaching the next flag is what turned a blank capture of a React SPA into six pages "
            + `with navigation.\n  notes: ${JSON.stringify(run.inventory.notes ?? [], null, 2)}`,
        );
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group G — the held list, read back. This runs last on purpose: it judges the other groups.
// -------------------------------------------------------------------------------------------------

describe("SH560 group G: the debt this corpus is currently carrying", () => {
  test("every held finding was exercised, and the run prints both counts", () => {
    const stale = [...held.keys()].filter((key) => !exercised.has(key));
    assert.deepEqual(
      stale,
      [],
      "open-findings.json holds a key no assertion runs, so a finding stopped being checked and "
        + "the list still claims it is. Either the assertion was renamed — use the key it emits — "
        + `or the archetype it names is gone.\n  stale: ${stale.join(" ")}`,
    );

    // Not a ceiling. The point of printing both is that a reader can see the debt move without
    // this file having an opinion about what the number should be (SH527's reasoning, applied to
    // a second ledger). A task closes by deleting its entry, and `holding` fails if it does not.
    const owners = [...new Set(reported.map((finding) => finding.id))].sort();
    console.log(
      `\nSH560: ${exercised.size} finding-bearing assertion(s), ${reported.length} red and held by `
        + `${owners.length} open task(s): ${owners.join(" ")}`,
    );
    for (const finding of reported) {
      console.log(`  ${finding.id}  ${finding.key}\n    ${finding.what}`);
    }

    assert.equal(
      reported.length,
      held.size,
      `${held.size} finding(s) are held and ${reported.length} came back red. A held finding that `
        + "is neither red nor reported means `holding` never saw it — the case threw before "
        + "reaching its assertion, which makes the whole group's result a lower bound",
    );
  });
});
