/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio diff` (SH522) — judge a produced artefact against the declaration it came from.
 *
 * ## The verb the chain was missing
 *
 * Every artefact in a replication carries the identity of what declared it: the inventory names the
 * capture, the proposal names the inventory, the document names the proposal. Nobody had ever walked
 * that chain, so "is this what it was supposed to be?" was answered by hand every round — and the
 * hand was wrong twice, once reporting zero broken references on a page with a missing logo and once
 * counting 106 editable fields on a page that had lost most of its content.
 *
 * ## Why `--against capture` is the whole of the first shipment
 *
 * §IX.34 names four declarations to judge against: the capture, the proposal, the document, and the
 * same address at an earlier moment. **The capture is the one both hand-errors were about**, and it
 * is the one where both sides are already computed by the same function — the inventory's counts
 * came out of `scanPage`, so the replica's come out of `scanPage` too, and the comparison is
 * arithmetic rather than a second opinion. The other three are named in the refusal below rather
 * than half-built, because a flag that accepts a value and answers approximately is worse than one
 * that says what it does not do yet.
 *
 * ## It fetches, and that is the point
 *
 * `verify --checks projection,replication` is tree-only by design and cannot see the artefact at
 * all; `verify --checks page` (SH516) renders inside the instance. This asks the third question —
 * what does the *route* actually serve — which is the one a reader gets, and the one whose answer
 * has been assembled by hand.
 */
import { join } from "node:path";

import {
  addressablePath, countAssets, countLinks, INVENTORY_FILE, MARKUP_DIR, markupFileFor, scanPage,
} from "../clone.mjs";
import { describeMigration, migrateInventory } from "../inventory-migrate.mjs";
import {
  assertMode, compareCapture, compareProposal, describeDiff, describeProposalDiff,
} from "../diff.mjs";
import { PLAN_FILE, PROPOSAL_FILE } from "../propose.mjs";
import { SECTIONS_FOLDER } from "../convert.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

/** What `shio diff --help` prints. */
export const HELP = helpText("diff");

/**
 * The declarations this can judge against (SH522).
 *
 * <p>`capture` compares the served page with what the source declared; `proposal` compares the
 * content the instance holds with what the plan said the conversion would make. Two different
 * questions on purpose - a page that composes the right sections and serves them badly is the
 * first one's finding, and keeping them apart is what lets a reader tell "the conversion did not
 * do what it promised" from "it did, and the layout is wrong".</p>
 *
 * <p>The document and an earlier moment remain unbuilt, and the refusal names them.</p>
 */
const AGAINST = ["capture", "proposal"];

export async function run(flags, positionals, ctx) {
  const site = typeof flags.site === "string" && flags.site.trim()
    ? flags.site.trim()
    : String(positionals[0] ?? "").trim();
  if (!site) {
    throw new Error(
      "shio diff: pass the site whose replica this should read.\n"
        + "The capture says what the source declared; the site says where to ask for what was\n"
        + "made from it, and there is no way to guess the second from the first.\n"
        + "  shio diff --site Acme",
    );
  }
  const against = typeof flags.against === "string" && flags.against.trim()
    ? flags.against.trim()
    : "capture";
  if (!AGAINST.includes(against)) {
    // P6, and it names what does not exist yet rather than pretending the flag is closed: the
    // proposal, the document and an earlier moment are all declarations this verb should judge
    // against, and saying so is more useful than "allowed: capture".
    throw new Error(
      `shio diff: --against ${against} is not built.\n`
        + `  allowed: ${AGAINST.join(", ")}\n`
        + "The other two declarations SH522 named are settled rather than pending:\n"
        + "  document  - `shio apply --check <file>` already plans a desired-state document\n"
        + "              against the instance and exits non-zero if anything would change.\n"
        + "  published - blocked on a read rather than on this verb: /api/v2/agent/read is\n"
        + "              DRAFT-preferred and takes no state, so the live row's fields cannot\n"
        + "              be fetched at all (SH619).",
    );
  }

  // SH577 — before anything is fetched. A flag this cannot honour must not cost a walk of the
  // capture first, and `auto` is the one value `assertMode` does not know because it is a way of
  // *finding* a mode rather than one of them.
  const asked = typeof flags.mode === "string" && flags.mode.trim() ? flags.mode.trim() : "auto";
  if (asked !== "auto") assertMode(asked);

  if (against === "proposal") {
    // Before the inventory read: this comparison never touches the capture, and paying for a
    // walk of it to answer a question about the plan is the shape SH577 refused one flag along.
    return againstProposal(site, flags, ctx);
  }

  const path = typeof flags.inventory === "string" ? flags.inventory : INVENTORY_FILE;
  const inventory = await readInventory(join(ctx.cwd, path), ctx, path);
  // SH592 — an inventory one version behind is MIGRATED where the missing field is derivable, and
  // refused only where it is not. The refusal this replaces was correct and cost ten minutes of
  // somebody else's crawl for a change that was not the crawl's.
  const upgrade = migrateInventory(inventory, await storedMarkup(inventory, ctx));
  if (upgrade.migrated.length || upgrade.blocked.length) {
    ctx.stdout.write(describeMigration(upgrade));
  }
  const pages = (upgrade.inventory.pages ?? []).filter((page) => page?.path);
  if (!pages.length) {
    throw new Error(
      `shio diff: ${path} records no page, so there is nothing to compare a replica with.\n`
        + "  shio clone <url> --markup",
    );
  }

  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const produced = new Map();
  const unreachable = [];
  for (const page of pages) {
    // SH557 — not asked for at all. A path carrying a `?` has no friendly URL, so there is nothing
    // to request; asking anyway spends 91 round trips on a real source to be told 404 ninety-one
    // times, and `compareCapture` reports it from the path alone.
    if (!addressablePath(page.path)) continue;
    const url = `${baseUrl.replace(/\/+$/, "")}/preview/${encodeURIComponent(site)}${page.path}`;
    let response;
    try {
      response = await ctx.fetch(url, {
        headers: { Authorization: basic(user, password), Accept: "text/html" },
      });
    } catch (error) {
      unreachable.push(`${page.path}: ${error?.message ?? error}`);
      continue;
    }
    // A non-200 is "the replica serves nothing here", which `compareCapture` reports as a missing
    // page rather than as four lost facts — one absence is one fact.
    if (!response?.ok) continue;
    const doc = scanPage(await response.text(), url);
    // SH554 — the second half of the reading, and the half that was missing. `scanPage` hands back
    // raw hrefs; an inventory row carries them already split, so the replica's are split too, by
    // the same `classifyLinks` the capture ran. Against the *served* URL: internal on a replica
    // means the instance that serves it, which is where the source's own-origin links now point.
    // SH578 — and the third half. `scanPage` finds a page's markup references, its inline
    // stylesheet's `url()` (SH455) and its scripts' `fetch()` strings (SH515) and puts all three in
    // one array; on a replica the middle group is the **theme**, inlined into every page. Split by
    // the same function the capture ran, so `assets` means the page's own references at both ends.
    produced.set(page.path, {
      ...doc,
      links: countLinks(doc.links, url),
      assets: countAssets(doc.assets),
    });
  }

  const comparison = compareCapture(pages, produced, await resolveModes(asked, ctx));
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(comparison, null, 2)}\n`);
  } else {
    ctx.stdout.write(describeDiff(comparison, flags));
    for (const row of unreachable) {
      ctx.stdout.write(`  ${row}\n`);
    }
  }
  // A page the instance could not be asked about is not a page that lost anything: the run is
  // incomplete, and reporting it as damage would make a stopped server read as a broken replica.
  return { ...comparison, unreachable, exitCode: comparison.lost > 0 ? 1 : 0 };
}

/**
 * What standard to hold each page to, and where that comes from (SH577).
 *
 * <p><b>Nothing is inferred from the page's own bytes.</b> That would be the second opinion SH522's
 * header refuses — a reader deciding "this looks composed" would disagree with the converter
 * somewhere, and the disagreement would read as a replica that lost content. The chain already
 * wrote the answer down twice: `shio/conversion-plan.json` records which blocks a reviewer accepted,
 * and the proposal records which blocks each page is made of. The join of the two is the mode, per
 * page, exactly as `composePages` computes it in `convert`.</p>
 *
 * <p><b>Per page, because a page is not one mode (SH520).</b> Accepting one block on a page keeps
 * the rest of it verbatim, so a run is routinely a mix — and a whole-run mode would hold a
 * fidelity page to the authorable standard, which is the same defect in the other direction.</p>
 *
 * <p><b>An explicit `--mode` wins and is never overridden</b>, because the case it exists for is
 * judging a replica whose plan is not in this directory at all — a checkout of the capture, or a
 * site somebody else converted.</p>
 *
 * <p>With no plan on disk nothing was accepted, so every page is `fidelity`: that is what this
 * command did before it could tell the difference, and it is the honest reading of an empty plan
 * rather than a fallback.</p>
 */
async function resolveModes(asked, ctx) {
  if (asked !== "auto") return { mode: asked };

  const plan = await readJson(join(ctx.cwd, PLAN_FILE), ctx);
  const accepted = new Set((plan?.accepted ?? []).map((entry) => entry?.block).filter(Boolean));
  if (!accepted.size) return { mode: "fidelity" };

  const proposal = await readJson(join(ctx.cwd, PROPOSAL_FILE), ctx);
  const composition = proposal?.pages ?? [];
  if (!composition.length) {
    // Something was accepted and there is no composition to say *where*. Holding every page to the
    // authorable standard is the safe direction: the alternative reports chrome the reviewer chose
    // to drop as damage, which is the failure this task is about.
    return { mode: "authorable" };
  }

  const modes = new Map();
  for (const page of composition) {
    if (!page?.path) continue;
    const blocks = page.blocks ?? [];
    const taken = blocks.filter((id) => accepted.has(id)).length;
    if (!taken) modes.set(page.path, "fidelity");
    else modes.set(page.path, taken === blocks.length ? "authorable" : "mixed");
  }
  // A captured page the proposal never composed is one `convert` writes verbatim or not at all.
  return { mode: "fidelity", modes };
}

/**
 * `--against proposal` - the plan's intent against the content the instance holds (SH522).
 *
 * <p>Reads three things and joins them: the reviewed plan, the proposal it reviewed, and the posts
 * the write produced. Nothing is guessed and nothing is re-derived - the chain is already written
 * down, which is what SH522 said made this cheap, and it is why this reads documents rather than
 * running a second scan.</p>
 */
async function againstProposal(site, flags, ctx) {
  const planPath = typeof flags.plan === "string" ? flags.plan : PLAN_FILE;
  const proposalPath = typeof flags.proposal === "string" ? flags.proposal : PROPOSAL_FILE;
  const plan = await readJson(join(ctx.cwd, planPath), ctx);
  const proposal = await readJson(join(ctx.cwd, proposalPath), ctx);
  if (!plan || !proposal) {
    // P6: which file, and the command that writes it. `readJson` answers null for both absent and
    // unreadable, which is right for `--mode auto` (judge everything) and wrong here - the whole
    // comparison IS these two files.
    throw new Error(
      `shio diff: --against proposal needs both declarations, and ${!plan ? planPath : proposalPath}`
        + " could not be read.\n"
        + `  ${proposalPath} - shio propose\n`
        + `  ${planPath} - shio propose --accept <id> --reason "..."`,
    );
  }

  const accepted = (plan.accepted ?? []).map((entry) => entry?.block).filter(Boolean);
  if (!accepted.length) {
    throw new Error(
      `shio diff: ${planPath} accepts no block, so the proposal declared nothing to compare.\n`
        + '  shio propose --accept <id> --reason "..."',
    );
  }

  const addresses = [
    ...new Set([
      ...accepted.map((id) => `post:${site}${SECTIONS_FOLDER}/${id}`),
      ...(proposal.pages ?? []).map((page) => `post:${site}${page.path}`),
    ]),
  ];
  const { ShioAgentClient } = await import("../agent-client.mjs");
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const docs = await client.read(addresses);

  // Keyed by the url the proposal speaks rather than by the SH79 address the read speaks: both
  // sides of this comparison are declarations, and translating once here keeps the pure half free
  // of addressing.
  const held = new Map();
  for (const doc of Array.isArray(docs) ? docs : []) {
    const url = String(doc?.address ?? "").slice(`post:${site}`.length);
    held.set(url, doc);
  }

  const comparison = compareProposal({
    plan, proposal, held, sectionsFolder: SECTIONS_FOLDER, pagesFolder: "",
  });
  ctx.stdout.write(flags.json
    ? `${JSON.stringify(comparison, null, 2)}\n`
    : describeProposalDiff(comparison, { limit: flags.limit }));
  return { ...comparison, exitCode: comparison.broken ? 1 : 0 };
}

async function readJson(at, ctx) {
  let raw;
  try {
    raw = await ctx.fs.readFile(at, "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
  try {
    return JSON.parse(raw);
  } catch {
    // A plan this cannot read is not a reason to refuse the comparison — it is a reason to judge
    // every fact, which is the stricter answer and the one that cannot hide a loss.
    return null;
  }
}

async function readInventory(at, ctx, named) {
  let raw;
  try {
    raw = await ctx.fs.readFile(at, "utf8");
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
    throw new Error(
      `shio diff: no capture at ${named}, and a comparison needs the declaration to compare with.\n`
        + "  shio clone <url> --markup",
    );
  }
  try {
    return JSON.parse(raw);
  } catch (error) {
    throw new Error(`shio diff: ${named} is not readable JSON (${error.message}).`);
  }
}

/**
 * The markup this capture stored, by path (SH592).
 *
 * <p>Read here and handed over as a map, so `migrateInventory` stays pure: the derivation is not
 * IO, and a pure function is the one that can be asserted without a disk. It goes through
 * `ctx.fs` like every other read in this tree — the injected filesystem is what lets a command
 * run against a fixture, and a `node:fs` call inside one is a seam nobody can reach (SH198).</p>
 *
 * <p>Only for the rows that need it. A capture whose pages already carry the split costs no reads
 * at all, which is the common case and the one that must stay free.</p>
 */
async function storedMarkup(inventory, ctx) {
  const markup = new Map();
  for (const page of inventory?.pages ?? []) {
    if (!page?.path) continue;
    if (typeof page.assets?.own === "number" && typeof page.assets?.head === "number") continue;
    try {
      markup.set(page.path,
        await ctx.fs.readFile(join(ctx.cwd, MARKUP_DIR, markupFileFor(page.path)), "utf8"));
    } catch {
      // A capture taken without --markup, or a page whose file was removed. Either way there are
      // no bytes to re-read, and the migration reports it as a page it could not derive rather
      // than scoring it on a shape it does not have.
    }
  }
  return markup;
}

function basic(user, password) {
  return `Basic ${Buffer.from(`${user ?? ""}:${password ?? ""}`, "utf8").toString("base64")}`;
}
