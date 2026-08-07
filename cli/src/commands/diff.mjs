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

import { addressablePath, countLinks, INVENTORY_FILE, scanPage } from "../clone.mjs";
import { assertMode, compareCapture, describeDiff } from "../diff.mjs";
import { PLAN_FILE, PROPOSAL_FILE } from "../propose.mjs";
import { resolveAdmin } from "../context.mjs";

/** What `shio diff --help` prints. */
export const HELP = `shio diff --site <name> - compare what a replica serves with what declared it

Walks the capture's pages, asks the instance for each one, and reads the served bytes with
the same scanner the capture used. What comes back is not pixels: the counts the source
recorded against the counts the page shows, per page and per fact.

Exits 1 when a page lost something. A fact the replica GAINED is never a failure - a Shio
layout adds links, and a gate that called that damage would fire on every working
conversion.

A page composed from the section vocabulary drops the source's nav, favicon and
stylesheet by design, so the capture's counts are not a claim about it. --mode says what
the counts ARE a claim about, and defaults to reading it off the conversion plan.

  --site <name>        The site the replica lives in (required)
  --against <what>     Which declaration to judge against (default: capture)
  --mode <how>         auto (default), fidelity, mixed or authorable
  --inventory <file>   Read a capture from somewhere else
  --limit <n>          Pages to print
  --json               The comparison as JSON on stdout instead of the report

Examples:
  shio diff --site Acme
  shio diff --site Acme --json
  shio diff --site Acme --mode fidelity   # judge every fact, whatever the plan says
`;

/** The declarations this can judge against. `capture` is the one that is built (SH522). */
const AGAINST = ["capture"];

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
        + "The proposal (what was intended), the document (what was written) and the same address\n"
        + "at an earlier moment are the other three declarations this verb is for, and none of\n"
        + "them is implemented — the capture is the one whose two sides are computed by the same\n"
        + "function, which is what makes this comparison arithmetic rather than a second opinion.",
    );
  }

  // SH577 — before anything is fetched. A flag this cannot honour must not cost a walk of the
  // capture first, and `auto` is the one value `assertMode` does not know because it is a way of
  // *finding* a mode rather than one of them.
  const asked = typeof flags.mode === "string" && flags.mode.trim() ? flags.mode.trim() : "auto";
  if (asked !== "auto") assertMode(asked);

  const path = typeof flags.inventory === "string" ? flags.inventory : INVENTORY_FILE;
  const inventory = await readInventory(join(ctx.cwd, path), ctx, path);
  const pages = (inventory.pages ?? []).filter((page) => page?.path);
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
    produced.set(page.path, { ...doc, links: countLinks(doc.links, url) });
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

function basic(user, password) {
  return `Basic ${Buffer.from(`${user ?? ""}:${password ?? ""}`, "utf8").toString("base64")}`;
}
