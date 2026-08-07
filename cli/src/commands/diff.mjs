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
import { compareCapture, describeDiff } from "../diff.mjs";
import { resolveAdmin } from "../context.mjs";

/** What `shio diff --help` prints. */
export const HELP = `shio diff --site <name> - compare what a replica serves with what declared it

Walks the capture's pages, asks the instance for each one, and reads the served bytes with
the same scanner the capture used. What comes back is not pixels: the counts the source
recorded against the counts the page shows, per page and per fact.

Exits 1 when a page lost something. A fact the replica GAINED is never a failure - a Shio
layout adds links, and a gate that called that damage would fire on every working
conversion.

  --site <name>        The site the replica lives in (required)
  --against <what>     Which declaration to judge against (default: capture)
  --inventory <file>   Read a capture from somewhere else
  --limit <n>          Pages to print
  --json               The comparison as JSON on stdout instead of the report

Examples:
  shio diff --site Acme
  shio diff --site Acme --json
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

  const comparison = compareCapture(pages, produced);
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
