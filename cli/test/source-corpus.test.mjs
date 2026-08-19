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
import { PLAN_FILE, SECTION_TYPES } from "../src/propose.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as convert from "../src/commands/convert.mjs";
import * as propose from "../src/commands/propose.mjs";
import { injectedFetch, loadAll } from "../../fixtures/source-sites/corpus.mjs";
import { holdings } from "../../fixtures/source-sites/open-findings.mjs";

// @scans prose reason: it reads the corpus ledger and the archetype descriptions as text; the
// findings it holds are prose by construction (SH740).

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

/**
 * The expectation keys that are declared and deliberately read by nothing, held as a named list.
 *
 * <p>Same device as `OPEN`, one level up: `OPEN` holds assertions that are red, this holds traits
 * that are not asserted at all — which is the worse of the two, because a red case is visible and an
 * unread key reads as coverage.
 *
 * <p><b>Empty, and the second driver is why.</b> It held `scenarios.headless`, which no assertion
 * could consume while `serve` — the corpus's live driver — was exported and called by nothing. The
 * key is a claim about a *browser-driven* capture, and this tier declares it runs with none; so the
 * honest home for it is `cli/conformance/source-corpus.conformance.mjs`, which the walk below now
 * reads for exactly that reason. A key whose only possible reader is in another tier is not inert,
 * it is misfiled, and the list could not tell the two apart until there was a second tier.
 */
const INERT_KEYS = [];

/**
 * Held findings whose owning task has already shipped, which is a debt of its own.
 *
 * <p><b>Empty, and SH594 is why.</b> It held `SH555`: that task shipped as a render-side fix — a
 * captured stylesheet now reaches the browser as CSS — while the CLI's generated layout kept the
 * `{{CSS}}` this corpus reads, so the case was red under a closed id and nobody would ever pick it
 * up. This declaration's own instruction was that emptying it means the generator was fixed too
 * <i>or the entry was repointed at a task somebody can pick up</i>, and the second is what
 * happened: SH606 now owns it, and it is a real question rather than a re-filing — the render's
 * `SafeString` and this brace count disagree about whether two braces are a defect at all.</p>
 *
 * <p>Kept as a list rather than deleted with its last entry. It is the only reader of the
 * *other* direction of staleness — a fix that lands somewhere this corpus does not look — and a
 * check that exists only while it is failing is one nobody re-adds.</p>
 */
const CLOSED_OWNERS = [];

// SH671 — this tier's subset, from the shared reader. The machinery moved to
// `fixtures/source-sites/open-findings.mjs` when the *live* tier turned out to have no way to
// declare a red at all: it was a property of this file rather than of the corpus, so a socket-only
// finding had nowhere to go and the gate sat red and undeclared. `held` is scoped to `fast`, which
// is what keeps the two tiers from failing each other's entries as unexercised.
const { held, exercised, reported, holding } = holdings(OPEN.findings, "fast");

/**
 * Held findings whose own prose names a task that has shipped, and which do not admit it (SH568).
 *
 * <p>Exported as a function rather than inlined so the check can be run against a synthetic entry:
 * `open-findings.json` is empty on a good day, and a rule that only ever runs over an empty list is
 * a rule nobody can tell from a typo. `refiledFrom` is the acknowledgement — an entry carrying it
 * has been looked at since the fix it outlived, which is the whole of what this asks for.</p>
 *
 * @param roadmap the ROADMAP text; an id that appears there is OPEN and is not what this looks for
 */
async function refiledDebt(findings, roadmap) {
  const changelog = await readFile(new URL("../../docs/CHANGELOG.md", import.meta.url), "utf8");
  const flagged = [];
  for (const finding of findings) {
    if (finding.refiledFrom) continue;
    for (const id of new Set(String(finding.what ?? "").match(/SH\d+/g) ?? [])) {
      if (id === finding.id) continue;
      const open = new RegExp(`\\*\\*${id}\\*\\*`).test(roadmap);
      const shipped = new RegExp(`\\*\\*${id}\\b`).test(changelog);
      if (!open && shipped) flagged.push(`${finding.id}/${finding.assertion} names ${id}`);
    }
  }
  return flagged.sort();
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

/**
 * One archetype's chain, run in a real temp directory. The artefact between phases is the file.
 *
 * <p>`accept` chooses the **mode**, and it is the whole difference between the two scenarios the
 * checklist insists are proved apart: accepting nothing is fidelity — `convert` writes the source's
 * verbatim markup and Shio's renderer serves it — and accepting everything is authorable, where the
 * blocks become sections and the markup is gone. `SKILL.md` says a round is not complete until both
 * have run, and until SH567 only the second one did.
 */
async function capture(entry, { accept = false, fidelity = false } = {}) {
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
  if (!accept && !fidelity) return result;

  const { proposal } = await propose.run({}, [], ctx);
  result.proposal = proposal;
  // Fidelity is `propose` and accept **nothing**. The proposal still has to run — it is what writes
  // the plan file `convert` reads — and the acceptance is what it withholds.
  // …and the plan file is written *by* the acceptance, so fidelity has none. Reading it anyway is
  // how this driver first reported four red fidelity cases that were its own ENOENT.
  if (accept) {
    await propose.run({ accept: true }, ["all"], ctx);
    result.plan = await json(cwd, PLAN_FILE);
  }
  await convert.run({ site: siteOf(entry), out: "conversion.json" }, [], ctx);
  result.conversion = await json(cwd, "conversion.json");
  return result;
}

/** The posts of one type in a conversion document, in the order the document holds them. */
const postsOfType = (conversion, type) =>
  (conversion.posts ?? []).filter((post) => post.type === type);

/** Every class name a template draws, which is what a theme has to define for a page to be styled. */
const drawnClasses = (html) =>
  new Set([...String(html).matchAll(/class="([^"]*)"/g)]
    .flatMap((match) => match[1].split(/\s+/))
    .filter((name) => name.startsWith("shio-")));

/** Every selector a stylesheet defines, read the way SH521's own lint reads one. */
const definedClasses = (css) =>
  new Set([...String(css).matchAll(/\.(shio-[A-Za-z0-9_-]+)/g)].map((match) => match[1]));

/** A site name per archetype: grep-able, and never one a live run would collide with. */
function siteOf(entry) {
  return `Corpus${entry.id.replace(/[^A-Za-z0-9]/g, "")}`;
}

/** A path with its trailing slash off, so `/en-us/` and `/en-us` are one address (SH576). */
function trimSlash(path) {
  const trimmed = String(path ?? "").replace(/\/+$/, "");
  return trimmed || "/";
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

        // SH570 — judged on the BYTES, never on what a terminal prints, because the checklist
        // records getting this wrong from the other direction. The fixture serves UTF-8 bytes under
        // a `charset=iso-8859-1` header on purpose: an archetype whose own encoding is ambiguous
        // cannot be the one that settles an encoding question. So the stored field is compared with
        // the source file this run served, read as the bytes it is.
        for (const [path, file] of Object.entries(declaration.expect.bytesTitle ?? {})) {
          holding(entry.id, `bytes-title-${slug(path)}`, async () => {
            const served = await readFile(join(entry.dir, file), "utf8");
            const expected = /<title>([\s\S]*?)<\/title>/i.exec(served)?.[1]?.trim();
            const row = (inventory.pages ?? []).find((page) => page.path === path);
            assert.ok(row, `${entry.id}: ${path} was not captured, so its encoding proves nothing`);
            assert.equal(
              row.title,
              expected,
              `${entry.id} / SH570: the stored title must be the source's bytes decoded as they `
                + "are, not as a mis-declared charset header claims. Mojibake that enters here is "
                + "CONTENT — it survives into the replica and a curator cannot tell it from a "
                + "character the author typed.",
            );
          });
        }

        // SH569 — the assertion this archetype exists to make first. A non-HTML 200 and a page the
        // crawl could not read are DIFFERENT claims: `data` is what was never a page, `problems` is
        // what failed. SH431 collapsed them, and twenty-four of `openviglet.github.io`'s data files
        // landed in `problems` — where the coverage gate reads them as unreadable pages and
        // downgrades EVERY count in the report to a lower bound. A bounded sample then reads as a
        // complete one, which is the worst direction for a report to be wrong in.
        if (declaration.expect.data) {
          holding(entry.id, "data-is-not-a-problem", () => {
            assert.deepEqual(
              (inventory.data ?? []).map((row) => row.path).sort(),
              [...declaration.expect.data].sort(),
              `${entry.id} / SH431: a 200 of application/json or text/plain is the site's data `
                + "surface, linked on purpose and served correctly — not a page, and not a failure",
            );
            const problems = new Set((inventory.problems ?? []).map((row) => row.path));
            for (const path of declaration.expect.data) {
              assert.ok(!problems.has(path),
                `${entry.id} / SH431: ${path} is in BOTH lists, so the distinction has collapsed`);
            }
          });
        }

        for (const note of declaration.expect.notes ?? []) {
          holding(entry.id, `capture-notes-${slug(note.match)}`, () => {
            assert.ok(
              (inventory.notes ?? []).some((line) => new RegExp(note.match, "i").test(line)),
              `${entry.id} / ${note.catches}: no note matching /${note.match}/i. ${note.means}.\n`
                + `  notes: ${JSON.stringify(inventory.notes ?? [], null, 2)}`,
            );
          });
        }

        // SH658 — a menu entry's label, held from both ends: the words the source put there, and
        // the characters a rule was supposed to take out.
        //
        // The note above is the *count* — how often the self-labelling rule fired — and a count is
        // compatible with the rule having produced the wrong string. That is the same mistake the
        // checklist calls "count, do not eyeball", one level up, so a declaration that stopped at
        // the note would be an archetype claiming a trait it half-checks. `keeps` and `drops` are
        // deliberately substrings rather than one expected label: the entity a label carries is a
        // separate question with its own archetype (`awkward`'s `doubleEscaped`), and folding it in
        // here would make this case fail for a reason that is not its subject.
        for (const item of declaration.expect.navigation ?? []) {
          holding(entry.id, `nav-label-${slug(item.href)}`, () => {
            const entries = (inventory.navigation ?? []).filter((each) => each.href === item.href);
            assert.ok(
              entries.length,
              `${entry.id} / ${item.catches}: no navigation entry for ${item.href}. ${item.means}.\n`
                + `  navigation: ${JSON.stringify(inventory.navigation ?? [], null, 2)}`,
            );
            // Asymmetric on purpose, because one href legitimately carries several labels: this
            // fixture's `/` is both the brand link and the about page's "Home" call to action.
            // `keeps` is therefore existential — *some* entry still says what the source said — and
            // `drops` is universal, because a graphic's letters leaking into any one of them is the
            // defect. Read the other way round, "Home" would fail for not being the brand.
            const labels = entries.map((each) => each.label);

            // SH670 — the exact string, where SH658 deliberately held only substrings. It could not
            // assert one while the label was decoded zero times and the same page's <title> once,
            // so the archetype recorded the defect by declining to describe it. Now that both give
            // one answer, the whole label is the assertion: an entity is the case where "contains
            // the right words" and "says the right thing" come apart.
            if (item.label !== undefined) {
              assert.ok(
                labels.includes(item.label),
                `SH670 / ${entry.id} / ${item.catches}: no label for ${item.href} is exactly `
                  + `${JSON.stringify(item.label)}. A link's label is content — it is what \`shio `
                  + "report` prints and what a curator is asked to keep — so it is decoded once, "
                  + `like the <title> of the same page. ${item.means}.\n`
                  + `  labels: ${JSON.stringify(labels)}`,
              );
            }

            const kept = labels.find((label) => (item.keeps ?? []).every((w) => label.includes(w)));
            assert.ok(
              kept !== undefined,
              `SH658 / ${entry.id} / ${item.catches}: no label for ${item.href} carries all of `
                + `${JSON.stringify(item.keeps ?? [])}, so a rule that removes a decorative mark is `
                + `eating the source's own words. ${item.means}.\n`
                + `  labels: ${JSON.stringify(labels)}`,
            );
            for (const word of item.drops ?? []) {
              const leaked = labels.filter((label) => label.includes(word));
              assert.deepEqual(
                leaked,
                [],
                `SH658 / ${entry.id} / ${item.catches}: a label for ${item.href} still carries `
                  + `"${word}", `
                  + "which is a graphic's letters and not a word of this page — so the menu a "
                  + `conversion writes reads as a logo. ${item.means}.\n`
                  + `  leaked: ${JSON.stringify(leaked)}`,
              );
            }
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

        // SH568/SH606 — the raw-field check that used to be here is gone, and the deletion is the
        // deliverable rather than a tidy-up.
        //
        // It read: *the generated layout does not emit `{{CSS}}` with two braces inside the
        // relation block*. That is a statement about the TEMPLATE, and it presumed the cure was a
        // third brace. SH461 had already decided otherwise and written down why — the fix belongs
        // where the value is built, "or every hand-written layout in the product keeps the defect
        // and only the generated one is cured" — so SH555 marked the row's raw-text fields
        // `Handlebars.SafeString` instead. Under that fix two braces are not a defect, and this
        // assertion could never go green however correctly the defect was closed.
        //
        // That is the general finding (SH568): an assertion phrased as *the code does not do X*
        // pins one implementation of a fix, and a list checked in both directions then converts a
        // CORRECT repair into a permanent red. The behaviour is what the entry should have named,
        // and the behaviour has an assertion — `ShConvertedLayoutRenderTest`'s
        // `aCapturedStylesheetIsNotEntityEscapedOnTheWayToThePage`, which renders a page from a
        // layout this generator produced and requires the captured `url()` quotes to arrive intact.
        // It is one tier deeper because that is where the renderer is, and no brace count can stand
        // in for it: the same two braces are right or wrong depending on `RAW_TEXT_FIELDS`.
        //
        // So SH606's question — which of the two readers is wrong — is answered by removing one
        // (SH583's shape, third sighting). What the fast tier keeps is the check it can actually
        // make: `no-entities-in-<url>` below, at the served document.

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
// Group H — fidelity, which is the other half of "a round is not complete until both have run"
// -------------------------------------------------------------------------------------------------

describe("SH560 group H: the mode that accepts nothing, judged in the same document", () => {
  for (const entry of corpus.filter((each) => each.declaration.scenarios?.fidelity
    && !each.declaration.needsBrowser)) {
    test(`${entry.id}: accept nothing, and the source's own markup is what would be written`, async () => {
      const run = await capture(entry, { fidelity: true });
      try {
        const pages = postsOfType(run.conversion, "Page");
        assert.ok(pages.length, `${entry.id}: a fidelity conversion writes one Page per captured page`);

        // The defining property, and the one SH520 proved is worth asserting: fidelity keeps the
        // page. A Page carrying `sections` and no `content` is the authorable shape, and the run
        // that shipped it measured 106 annotated fields on a page that was mostly gone.
        //
        // SH576 — over the pages the SOURCE declared, which is what this rule was always about. A
        // source with no document at `/` gets a front door written for it, and that page has no
        // verbatim markup because there is nothing verbatim to give it: the source served a 404
        // there. Requiring `content` on it would demand a pixel comparison against a page that does
        // not exist, and the honest scope of "fidelity keeps the page" is the pages there were.
        const declared = new Set((entry.declaration.addressable ?? []).map(trimSlash));
        const fromSource = pages.filter((row) => declared.has(trimSlash(row.url)));
        assert.ok(
          fromSource.length,
          `${entry.id}: no converted page matches an addressable path this archetype declares, so `
            + "the rule below would pass over nothing — which is the shape a scoping change breaks "
            + `silently.\n  declared: ${[...declared].join(" ")}\n  wrote: ${pages.map((p) => p.url).join(" ")}`,
        );
        for (const page of fromSource) {
          assert.ok(
            page.data?.content,
            `SH520 / ${entry.id}: ${page.url} was converted with nothing accepted and carries no `
              + "`content`. Fidelity's whole claim is a pixel comparison against the source, and a "
              + "page with no markup cannot make it.\n"
              + `  fields: ${Object.keys(page.data ?? {}).join(" ")}`,
          );
        }

        // And the documented limit, stated as an assertion so it stops being folklore: verbatim
        // markup has no `sh-field`, so the editor loads over a page with nothing to edit. A round
        // that reported annotated fields on a fidelity replica would be measuring the wrong mode.
        const annotated = pages.filter((page) => /sh-field|data-shio-prop/.test(page.data?.content ?? ""));
        assert.deepEqual(
          annotated.map((page) => page.url),
          [],
          `${entry.id}: fidelity is defined by having no editable field — the source's markup has `
            + "no `sh-field` and `convert` adds none. A page here that is annotated means the mode "
            + "boundary moved and SKILL.md's 'zero data-shio-prop' is no longer the property.",
        );

        // Both modes still need a layout: the renderer resolves one per post-type, and SH464 is the
        // task that exists because the authorable path once wrote none and /preview answered 400.
        assert.ok(
          postsOfType(run.conversion, "PageLayout").length,
          `SH464 / ${entry.id}: a fidelity conversion writes no PageLayout, so every page it makes `
            + "answers 400 — the same defect the authorable path shipped with",
        );
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group I — the checklist rules that were declared per archetype and read by nothing
// -------------------------------------------------------------------------------------------------

describe("SH560 group I: the checks the corpus declared and no assertion consumed", () => {
  // Check 3. Both spellings of a directory path answer, one canonical and one redirecting, the way
  // the source does (SH467). Decidable from the capture: the inventory records which spelling the
  // source served and which it redirected, and the conversion says which one becomes the furl.
  for (const entry of corpus.filter((each) => (each.declaration.bothSpellings ?? []).length)) {
    test(`${entry.id}: a directory path keeps the spelling the source made canonical (SH467)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        for (const path of entry.declaration.bothSpellings) {
          holding(entry.id, `both-spellings-${slug(path)}`, () => {
            const withSlash = `${path.replace(/\/$/, "")}/`;
            const urls = new Set(postsOfType(run.conversion, "Page").map((page) => page.url));
            assert.ok(
              urls.has(withSlash),
              `SH467 / ${entry.id}: the source serves ${path} and ${withSlash} as one canonical `
                + "and one redirect, and the replica writes only one of the two — so every captured "
                + "link to the other costs a 301 or a 404. The grammar collapses the declared "
                + `slash, which is the half SH467 left open.\n  urls: ${[...urls].join(" ")}`,
            );
          });
        }
      } finally {
        await run.dispose();
      }
    });
  }

  // Check 1, at the phase where it is cheapest. A document-relative reference is the one that breaks
  // — the replica is served several segments deeper than its source — and collecting only
  // `/`-prefixed refs once reported 0 broken on a page with fifteen missing images (SH512).
  for (const entry of corpus.filter((each) => each.declaration.relativeReferences)) {
    test(`${entry.id}: a document-relative reference is resolved against its own page (SH512)`, async () => {
      const run = await capture(entry);
      try {
        for (const [page, refs] of Object.entries(entry.declaration.relativeReferences)) {
          for (const ref of refs) {
            const resolved = new URL(ref, new URL(page, ORIGIN)).pathname;
            holding(entry.id, `relative-reference-${slug(resolved)}`, () => {
              assert.ok(
                run.inventory.assets.some((asset) => asset.path === resolved),
                `SH512 / ${entry.id}: ${page} references ${ref}, which resolves to ${resolved} `
                  + "against the page's own URL. The capture does not hold it, so the replica will "
                  + "serve a page whose image is simply absent and whose reference list looks "
                  + `clean.\n  captured: ${run.inventory.assets.map((a) => a.path).join(" ")}`,
              );
            });
          }
        }
      } finally {
        await run.dispose();
      }
    });
  }

  // SH433 — a site's home page is `post:<site>/`, the one address form the grammar does not spell.
  // A source with no document at `/` is where that stops being a formality: something has to be the
  // home page, or the replica has a site whose root answers nothing.
  for (const entry of corpus.filter((each) => each.declaration.noSourceHome)) {
    test(`${entry.id}: a source with no home at / still converts to a site with one (SH433)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        holding(entry.id, "home-page-written", () => {
          const urls = postsOfType(run.conversion, "Page").map((page) => page.url);
          assert.ok(
            urls.includes("/"),
            `SH433 / ${entry.id}: every page of this source lives under a locale prefix and the `
              + "conversion writes no page at `/`, so `post:<site>/` — the home address — resolves "
              + `to nothing and the site's root is a 404.\n  urls: ${urls.join(" ")}`,
          );
        });
      } finally {
        await run.dispose();
      }
    });
  }

  // SH556, read from the document rather than from a rendered page. The theme styles SH508's roles;
  // the section templates draw class names. A name drawn and not defined is a page that renders and
  // is unstyled, which is what `verify` reports as `page-unstyled` on every page of a site.
  for (const entry of corpus.filter((each) => each.declaration.conversion?.themeCoversDrawnClasses)) {
    test(`${entry.id}: every class the templates draw is one the theme defines (SH556)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        const layout = postsOfType(run.conversion, "PageLayout")[0];
        const theme = postsOfType(run.conversion, "Theme")
          .find((post) => post.data?.TITLE === layout?.data?.THEME);
        assert.ok(layout && theme, `${entry.id}: the layout names a theme this document holds`);
        holding(entry.id, "theme-covers-drawn-classes", () => {
          const defined = definedClasses(theme.data.CSS ?? "");
          const missing = [...drawnClasses(layout.data.HTML ?? "")].filter((name) => !defined.has(name));
          assert.deepEqual(
            missing,
            [],
            `SH556 / ${entry.id}: the layout draws ${missing.length} class name(s) the theme it `
              + "names does not define, and it loads no other stylesheet — so the page renders and "
              + `is unstyled.\n  missing: ${missing.join(" ")}`,
          );
        });
      } finally {
        await run.dispose();
      }
    });
  }

  // SH550/SH555 at the document. The stored Theme is what the render reads, so an entity here is one
  // no renderer can undo — the distinction from SH555 is that this asks about the *stored bytes*.
  for (const entry of corpus.filter((each) => (each.declaration.conversion?.noEntitiesIn ?? []).length)) {
    test(`${entry.id}: the stored theme holds CSS, not escaped markup (SH550)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        for (const url of entry.declaration.conversion.noEntitiesIn) {
          const post = (run.conversion.posts ?? []).find((each) => each.url === url);
          assert.ok(post, `${entry.id}: the document holds no post at ${url}`);
          holding(entry.id, `no-entities-in-${slug(url)}`, () => {
            const found = JSON.stringify(post.data ?? {}).match(/&(quot|amp|lt|gt|#\d+);/g) ?? [];
            assert.deepEqual(
              [...new Set(found)],
              [],
              `SH550 / ${entry.id}: ${url} carries ${found.length} HTML entity/entities in what is `
                + "stored as CSS. A stylesheet is not markup, so nothing downstream decodes it and "
                + `every url() it holds is a path the site does not serve.\n  found: ${[...new Set(found)].join(" ")}`,
            );
          });
        }
      } finally {
        await run.dispose();
      }
    });
  }

  // "Count, do not eyeball", and the half that is easy to miss: four of the seven section types are
  // collection-shaped, so a page that looks annotated can be five props over a grid whose rows carry
  // none. SH465 drew no rows at all; SH475 drew them with no `sh-post` and no `sh-field`; the
  // visible page looked reasonable at both stages.
  for (const entry of corpus.filter((each) => (each.declaration.collections ?? []).length)) {
    test(`${entry.id}: every collection field annotates its rows, not just its heading (SH475)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        const layout = postsOfType(run.conversion, "PageLayout")[0];
        assert.ok(layout, `${entry.id}: no layout to read the annotations from`);
        const html = layout.data.HTML ?? "";
        for (const field of entry.declaration.collections) {
          holding(entry.id, `collection-rows-annotated-${slug(field)}`, () => {
            // SH830 — the layout carries the branches for the types the conversion created, so a
            // declared collection it does not draw is a declaration the corpus cannot keep. This
            // check ran on four fields here and two of them were **vacuous**: `questions` and
            // `logos` passed against a branch every layout carried whether or not any post on the
            // site could fill it. A coverage claim that cannot fail is worth less than none.
            assert.ok(
              html.includes(`{{#each ${field}}}`),
              `SH830 / ${entry.id}: the declaration names \`${field}\` and this conversion creates `
                + "no type that has one, so the layout draws no such branch. Either the archetype's "
                + "markup should produce that type - `propose` wants two or more <details> for an "
                + "FAQ and three image-only units for a logo wall - or the key is a claim about the "
                + "template rather than about this replica, which convert.test.mjs already holds.",
            );
            // A row-level field is `<field>.{{@index}}.<name>`, in either declaration form: a value
            // that lands in an attribute is declared by `sh-field-attr`, so counting only `sh-field`
            // under-reports every attribute-valued field and once read a complete page as 9 of 10.
            const rowFields = new RegExp(`sh-field(-attr)?="${field}\\.\\{\\{@index\\}\\}\\.`);
            assert.ok(
              rowFields.test(html),
              `SH475 / ${entry.id}: the layout renders \`${field}\` and annotates no field inside `
                + "its rows, so a curator sees a grid drawn from content they cannot edit. Rendering "
                + "is not annotation — that is the distinction both SH465 and SH475 were about.",
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
// Group J — what an authorable replica is missing that no rule in the checklist thought to name
// -------------------------------------------------------------------------------------------------

describe("SH560 group J: the replica as a site somebody can move around in", () => {
  for (const entry of corpus.filter((each) => each.declaration.scenarios?.authorable
    && each.declaration.propose?.accept === "all"
    && (each.declaration.addressable ?? []).length > 1)) {
    // SH575's id, not SH568's — corrected while shipping SH568, which is about this exact hazard.
    // The label read SH568 and the assertion is about navigation; SH568 is the held-finding-phrasing
    // task and SH575 is what put a `<nav>` in the generated layout. It is not cosmetic: SH527's
    // reader counts an id named in an assertion message as ASSERTED, so a wrong id pays off the
    // wrong task's debt — the false-payment shape `assertion-debt.test.mjs` records twice already.
    test(`${entry.id}: an authorable replica can be navigated (SH575)`, async () => {
      const run = await capture(entry, { accept: true });
      try {
        const layout = postsOfType(run.conversion, "PageLayout")[0];
        assert.ok(layout, `${entry.id}: no layout`);
        holding(entry.id, "authorable-has-navigation", () => {
          const html = layout.data.HTML ?? "";
          // The renderer ships a `navigation` helper and the generated layout uses none, so every
          // page of an authorable conversion is a leaf. Measured on a real replica: `shio diff`
          // reported `internal links: 16 → 0` on all twelve pages, `audit` reported `no-landmark`,
          // and `verify` exited 0 — a site of unreachable pages that every gate passes.
          assert.ok(
            /\{\{#?navigation\b/.test(html) || /<nav\b/.test(html),
            `SH575 / ${entry.id}: the generated layout emits neither the \`navigation\` helper nor `
              + "a <nav>, so a replica of a source with "
              + `${entry.declaration.addressable.length} addressable pages serves them with no link `
              + "between them. The source's own nav lived in chrome the vocabulary drops, and "
              + "nothing puts one back.",
          );
        });
      } finally {
        await run.dispose();
      }
    });
  }
});

// -------------------------------------------------------------------------------------------------
// Group L — which of the vocabulary's types this corpus has ever produced (SH833)
// -------------------------------------------------------------------------------------------------

/**
 * Types no archetype converts to, held as a **named list** rather than a count.
 *
 * <p>Same device as `OPEN` and `INERT_KEYS`, and for the same reason: a ceiling lets one task pay
 * the debt and the next task spend it. An entry here says *no source in this corpus has markup
 * `classify` reads as this type*, which is a coverage hole and not a defect — but one that reads as
 * coverage until somebody counts, which is exactly how SH833 was found.</p>
 *
 * <p><b>Empty, and the three it lost are the measurement.</b> When this check was written the corpus
 * produced four of seven: `SectionFaq` wanted two or more `<details>` and `theme-heavy` had a `<dl>`;
 * `SectionLogoWall` wanted three image-only *units* and it had two bare `<img>`, which are not units
 * at all — `unitsAmong` skips a child with no element children, so a logo wall has to be
 * `<li><img></li>` before it is one; and `SectionRichText` — prose, no repeated structure, no call to
 * action — had no block anywhere that was only that. All three bars are deliberate and all three were
 * unmet by fixtures that plainly meant to exercise them. `SectionHero` was on this list until the run
 * printed otherwise, which is the reason the list is measured rather than reasoned.</p>
 *
 * <p>Kept as a list rather than deleted now that it is empty, for `CLOSED_OWNERS`' reason: it is the
 * only reader of the other direction — a fixture edit that costs the corpus its last producer of a
 * type — and a check that exists only while it is failing is one nobody re-adds.</p>
 */
const UNPRODUCED_TYPES = [];

describe("SH833: every section type the vocabulary declares has a source that produces it", () => {
  test("the corpus names the types it has never converted to, and the count is printed", async () => {
    // The hole this closes was invisible in the direction that matters. `theme-heavy` declared four
    // collection fields, its conversion created two, and the SH475 row-annotation assertion passed
    // on all four — because the generated layout carried a branch per field of the WHOLE vocabulary
    // whether or not any post could fill it. SH830 made the layout branch, the two vacuous cases
    // turned red, and the archetype grew the markup `classify` actually asks for: two or more
    // <details> for an FAQ, three image-only units for a logo wall, and a block that is prose and
    // nothing else. Three bars, all deliberate, all unmet by a fixture that plainly meant to
    // exercise them.
    const produced = new Set();
    for (const entry of corpus.filter((each) => each.declaration.propose?.accept === "all")) {
      const run = await capture(entry, { accept: true });
      try {
        for (const type of run.conversion.postTypes ?? []) produced.add(type);
      } finally {
        await run.dispose();
      }
    }
    const missing = Object.keys(SECTION_TYPES).filter((type) => !produced.has(type)).sort();
    console.log(`SH833: ${Object.keys(SECTION_TYPES).length - missing.length} of `
      + `${Object.keys(SECTION_TYPES).length} section type(s) produced by this corpus`
      + `${missing.length ? `, missing: ${missing.join(" ")}` : ""}`);

    assert.deepEqual(missing, UNPRODUCED_TYPES,
      "a type that has gained a producer must leave UNPRODUCED_TYPES and one that has lost its "
        + "last one must join it - held as a list because a count lets the next fixture spend what "
        + "this one paid, and because a classifier branch no source reaches has never run end to end");
  });
});

// -------------------------------------------------------------------------------------------------
// Group K — the map itself: a declaration nobody reads is a scenario nobody runs
// -------------------------------------------------------------------------------------------------

describe("SH560 group K: every trait an archetype declares is read by an assertion", () => {
  test("no expectation key is inert, and the ones that are get named", async () => {
    // The corpus is a catalogue of what a real site does, and its entries are only worth the
    // assertions that consume them. A key declared and never read is the failure mode this whole
    // file exists to prevent, one level up: it reads as coverage in a review and runs nothing.
    const suite = await readFile(new URL(import.meta.url), "utf8");
    const loader = await readFile(
      new URL("../../fixtures/source-sites/corpus.mjs", import.meta.url),
      "utf8",
    );
    // The live tier counts as a reader, and it has to: a trait about a *browser* capture cannot be
    // consumed by a file whose first paragraph says it runs with no browser and no socket. Reading
    // it here is what lets `INERT_KEYS` be empty without either tier pretending to cover the other's
    // ground — and it is why the key that sat on that list for the whole of this corpus's life came
    // off it the moment `serve` had a caller.
    const live = await readFile(
      new URL("../conformance/source-corpus.conformance.mjs", import.meta.url),
      "utf8",
    );
    // Comments are stripped first, and that is not tidiness: the prose *about* a key names it, so
    // a file that documents an unread trait would report itself as reading one. This check found
    // that on its own first run — `scenarios.headless` came back covered by the paragraph saying it
    // is not.
    // …and `INERT_KEYS` is cut from its own walk for the same reason `assertion-debt.test.mjs` is
    // cut from its: a declaration that a key is unread is not a read of it. Left in, the list makes
    // every entry on it report as covered, which is exactly backwards.
    const readers = `${suite}\n${loader}\n${live}`
      .replace(/\/\*[\s\S]*?\*\//g, " ")
      .replace(/(^|[^:])\/\/[^\n]*/g, "$1 ")
      .replace(/const INERT_KEYS = \[[^\]]*\]/, " ");

    const declared = new Set();
    for (const entry of corpus) {
      for (const [key, value] of Object.entries(entry.declaration)) {
        declared.add(key);
        if (value && typeof value === "object" && !Array.isArray(value)) {
          for (const nested of Object.keys(value)) declared.add(`${key}.${nested}`);
        }
      }
    }

    // A leaf name is the unit, because that is how the suite reads one: `declaration.conversion
    // ?.rawFields`. Keys whose leaves are data rather than API — a route table's paths, a page's
    // own reference list — are addressed through their parent and are not looked for by name.
    const addressedThroughParent = (key) => /^(routes|relativeReferences|redirects)\./.test(key);
    const inert = [...declared]
      .filter((key) => !addressedThroughParent(key))
      .filter((key) => {
        const leaf = key.split(".").pop();
        return !new RegExp(`\\b${leaf.replace(/[^\w]/g, "\\$&")}\\b`).test(readers);
      })
      .sort();

    assert.deepEqual(
      inert,
      INERT_KEYS,
      "an expectation key is declared by an archetype and consumed by no assertion, so the corpus "
        + "claims a trait it does not check. Either write the assertion or delete the key — and if "
        + "it is a scenario this tier genuinely cannot run, say so in `scenarios` where group F can "
        + `read it.\n  now inert: ${inert.join(" ")}\n  declared inert: ${INERT_KEYS.join(" ")}`,
    );

    console.log(
      `\nSH560 coverage: ${declared.size} declared key(s), ${inert.length} inert `
        + `(${inert.join(" ") || "none"})`,
    );
  });
});

// -------------------------------------------------------------------------------------------------
// Group G — the held list, read back. This runs last on purpose: it judges the other groups.
// -------------------------------------------------------------------------------------------------

describe("SH560 group G: the debt this corpus is currently carrying", () => {
  test("every held finding was exercised, and the run prints both counts", async () => {
    const stale = [...held.keys()].filter((key) => !exercised.has(key));
    assert.deepEqual(
      stale,
      [],
      "open-findings.json holds a key no assertion runs, so a finding stopped being checked and "
        + "the list still claims it is. Either the assertion was renamed — use the key it emits — "
        + `or the archetype it names is gone.\n  stale: ${stale.join(" ")}`,
    );

    // And the other direction of stale: an entry whose *task* is closed. `holding` catches a fix
    // that lands and turns the case green, and it cannot catch a fix that lands somewhere else —
    // SH555 shipped as a change to the renderer while the CLI's generated layout kept the `{{CSS}}`
    // this corpus reads, so the case stayed red under a closed id and nobody would ever act on it.
    // The roadmap is the authority on which ids are open, and reading it costs one file.
    const roadmap = await readFile(new URL("../../docs/ROADMAP.md", import.meta.url), "utf8");
    const closed = [...new Set(OPEN.findings.map((finding) => finding.id))]
      .filter((id) => !new RegExp(`\\*\\*${id}\\*\\*`).test(roadmap))
      .sort();
    assert.deepEqual(
      closed,
      CLOSED_OWNERS,
      "a held finding names a task that is no longer on the roadmap, so the assertion is red under "
        + "an id nobody will pick up. Either the fix landed somewhere this corpus does not read — "
        + "file the remainder and repoint the entry — or the entry is spent and should be deleted.\n"
        + `  closed owners: ${closed.join(" ")}`,
    );

    // SH568 — and the third direction, which is the one the two above let through. `holding` catches
    // a fix that turns the case green; the check above catches an entry whose owner shipped. Neither
    // sees a **repointed** entry: SH555 shipped, the case stayed red because the fix landed on the
    // value and this assertion measured the template, and the entry was handed to a new open id. To
    // both checks that is indistinguishable from a fresh finding — a live entry under a live owner —
    // and it is how a mechanism-phrased assertion held a CLOSED defect open for a block.
    //
    // The tell is decidable and it is in the entry's own prose: an entry that names a SHIPPED id is
    // one that outlived a fix. It may still be a real defect, and then it says so in `refiledFrom`,
    // which is a field somebody has to write — the point is that "this outlived a fix and here is
    // why that is not the end of it" stops being a sentence nobody was asked for.
    const shippedIds = await refiledDebt(OPEN.findings, roadmap);
    assert.deepEqual(
      shippedIds,
      [],
      "SH568: a held finding's prose names a task that has already shipped, and the entry does not "
        + "declare itself re-filed. Either the fix closed this case — delete the entry — or it did "
        + "not, and `refiledFrom` is where you say what the shipped fix did NOT cover. An assertion "
        + "phrased as *the code does not do X* can only go green the one way its author imagined, "
        + "which is what made this worth a check rather than a habit.\n"
        + `  entries: ${shippedIds.join(" ")}`,
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

  /**
   * The re-filing check, run against entries invented here (SH568).
   *
   * <p>`open-findings.json` is empty on a good day, so the rule above passes over nothing and a
   * reader cannot tell it from a rule that is broken. The ids below are the real ones from the case
   * that produced it — SH555 shipped, SH606 was live — because a synthetic id would also test
   * nothing: what the check reads is the ROADMAP and the CHANGELOG, and an invented id is in
   * neither, which is the one answer it must not give.</p>
   */
  test("SH568: the re-filing rule fires on a re-filed entry and not on a fresh one", async () => {
    const roadmap = await readFile(new URL("../../docs/ROADMAP.md", import.meta.url), "utf8");
    const refiled = {
      id: "SH606", archetype: "theme-heavy", assertion: "layout-raw-field-CSS",
      what: "Re-filed from SH555: that task shipped on the RENDER side, so this assertion and that "
        + "fix now disagree about whether two braces are a defect.",
    };

    assert.deepEqual(
      await refiledDebt([refiled], roadmap),
      ["SH606/layout-raw-field-CSS names SH555"],
      "the entry that cost a block is exactly the one this has to catch: live owner, live key, and "
        + "prose admitting it outlived a shipped fix",
    );
    assert.deepEqual(
      await refiledDebt([{ ...refiled, refiledFrom: "SH555" }], roadmap),
      [],
      "and declaring it is the way through — the check asks for an acknowledgement, not for the "
        + "entry to be deleted, because sometimes the shipped fix really did leave a remainder",
    );
    assert.deepEqual(
      await refiledDebt([{ ...refiled, what: "A stylesheet reaches the page entity-escaped." }], roadmap),
      [],
      "a finding whose prose names no shipped task is an ordinary one and is left alone",
    );
  });
});
