/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { stripComments } from "./support/source-text.mjs";

/**
 * SH1049 — a page that fetches and cannot say it is loading, or that it failed.
 *
 * ## The three states, and the two most pages have
 *
 * A read has three answers — pending, failed, arrived — and a page that models two of them turns
 * the third into one of the others. The post editor was the worst case and the one this task fixed:
 * it settled every promise on purpose (SH654 needs to know which rows exist), so a 500 left `post`
 * at `null` and the render gate spun **for ever**, with no error, no retry, and nothing to press.
 * A slow server, a deleted post and a broken instance were one screen.
 *
 * ## What this holds, and what it cannot
 *
 * It cannot tell a good skeleton from a bad one — that is the browser probe's job, and
 * `console-check.mjs` injects a 500 per route and reads what a curator actually sees. What a source
 * scan can do is refuse the *absence*: a page that reads from the server and mentions no loading,
 * error or empty state anywhere is one that has decided the read always works.
 *
 * ## One hop, because most lists delegate
 *
 * Eight admin lists are three lines that hand a fetcher to `AdminListPage`, which gates for them.
 * Crediting them for their own text would be wrong and failing them would be wrong, so this follows
 * a page that imports another *page* one level — the same move `widget-editability.test.mjs` makes
 * for a lazy widget. One level, and only page to page: a hop that reached any component would reach
 * something gated from almost anywhere, which is a scan that says yes to everything.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** Anything that distinguishes "still loading" or "this failed" from "here is your content". */
const GATES = /LoadProvider|BentoListPage|isPending|isLoading|\bloading\b|Skeleton|EmptyState|RouteBoundary/u;

/** A call that goes to the server, directly or through SH1046's cached hooks. */
const FETCHES = /\b\w*[Ss]ervice\.\w+\(|\baxios\.(?:get|post|put|delete|patch)\b|use(?:Query|PostTypes|Sites|Widgets)\b/u;

/**
 * Pages that read from the server and model no loading or failure state.
 *
 * <p>A declared list that shrinks by deletion, never a ceiling: a ceiling would let this task pay
 * the debt and the next spend it. Each entry is a page a curator can reach, and the sentence they
 * share is that a failed read there renders as an empty form or an empty table — less wrong than
 * the endless spinner SH1049 removed, and still a lie.</p>
 *
 * <p>Four, measured on 2026-09-20. The shell left this list in the same commit: it reads the
 * session's tenant and user to draw the rail and still models neither state for them, but it now
 * mounts `RouteBoundary` around the outlet, and a page that can catch a throw is past the absence
 * this check refuses.</p>
 *
 * <p>Three since SH1225: the post types list moved onto `BentoListPage`, whose gate draws the
 * loading and the failed read.</p>
 */
const NO_LOADING_STATE = new Set([
  "src/app/admin/identity-entity.page.tsx",
  "src/app/content/folder.edit.page.tsx",
  "src/app/content/site.edit.page.tsx",
]);

/** Every route page under `src/app`. */
function pages() {
  const found = [];
  for (const entry of readdirSync(join(ROOT, "src", "app"), { recursive: true })) {
    if (typeof entry === "string" && entry.endsWith(".page.tsx")) {
      found.push(`src/app/${entry.split("\\").join("/")}`);
    }
  }
  return found.sort();
}

/**
 * The page a page delegates to — the one hop, and only that one.
 *
 * <p>Narrowed to imports that are themselves pages under `src/app`, which is the delegation this
 * exists for: eight admin lists are three lines handing a fetcher to `AdminListPage`. The first
 * draft followed every local import and credited `post-type.list.page.tsx` because `page-hero.tsx`
 * has a `loading` prop — a page passed for importing a header. A hop that reaches any component
 * reaches something gated from almost anywhere, which is a scan that says yes to everything.</p>
 */
function delegatesTo(path, code) {
  const here = dirname(path);
  const parts = [];
  for (const [, spec] of code.matchAll(/from "([./@][^"]*)"/gu)) {
    const resolved = spec.startsWith("@/")
      ? `src/${spec.slice(2)}`
      : join(here, spec).split("\\").join("/");
    if (!resolved.startsWith("src/app/") || !resolved.endsWith(".page")) continue;
    if (existsSync(join(ROOT, `${resolved}.tsx`))) parts.push(`${resolved}.tsx`);
  }
  return parts;
}

/** Which pages fetch, and which of those model no loading or failure state. */
export function loadingStates(read = (path) => readFileSync(join(ROOT, path), "utf8")) {
  const fetching = [];
  const ungated = [];
  for (const path of pages()) {
    const code = stripComments(read(path));
    if (!FETCHES.test(code)) continue;
    fetching.push(path);
    if (GATES.test(code)) continue;
    const delegated = delegatesTo(path, code)
      .some((part) => GATES.test(stripComments(read(part))));
    if (!delegated) ungated.push(path);
  }
  return { fetching, ungated };
}

test("SH1049: no new page reads from the server without a loading or failure state", () => {
  const { fetching, ungated } = loadingStates();

  console.log(`SH1049 — ${fetching.length} fetching page(s); `
    + `${fetching.length - ungated.length} model a loading or failure state, ${ungated.length} do not`);

  // The vacuity guard: a regex that stopped matching would find no fetching pages at all and agree
  // with every list, including an empty one.
  assert.ok(fetching.length >= 30,
    `SH1049: only ${fetching.length} fetching page(s) found — this scan is measuring nothing`);

  const arrived = ungated.filter((path) => !NO_LOADING_STATE.has(path));
  assert.deepEqual(arrived, [],
    "SH1049: a new page reads from the server and models neither a pending read nor a failed one, "
    + "so a 500 and a slow server and an empty result are one screen. Gate it on the read's own "
    + "state — `LoadProvider` takes the value, the error and a try-again URL — or say here why it "
    + "cannot.");

  const paid = [...NO_LOADING_STATE].filter((path) => !ungated.includes(path)).sort();
  assert.deepEqual(paid, [],
    "SH1049: these are declared as having no loading state and now have one — delete them from "
    + "NO_LOADING_STATE in the same commit, so a debt cannot be paid and left on the books.");
});

test("SH1049: the post editor is gated on the failure, not only on the value", () => {
  // The defect this task closed, held by name. `!post` was the whole gate, and `post` stays null on
  // a failed read — so the spinner had no end. A page that goes back to that would pass the scan
  // above, because it would still mention `loading`.
  const editor = stripComments(readFileSync(
    join(ROOT, "src", "app", "content", "post.edit.page.tsx"), "utf8"));

  // SH1158 moved the read onto the query layer, so the failure is DERIVED from the query rather than
  // recorded by a setter — and the derivation has to exclude a cancellation, or leaving the form mid-load
  // becomes a reliable "post not found" in place of a rare spinner. Same claim, at its new spelling.
  assert.match(editor, /const loadFailure = loaded\.isError && !isCancelled\(loaded\.error\)/u,
    "SH1049: the post editor no longer records why the post is missing, so a failed read is "
    + "indistinguishable from a slow one and the page spins for ever");
  assert.match(editor, /<LoadProvider/u,
    "SH1049: and it must show the third state rather than a bare spinner — the package's provider "
    + "draws the blank slate with a way out once `error` is set");
  assert.doesNotMatch(editor, /if \(!unified\) return\n/u,
    "SH1049: this early return is the bug: both reads failing left `post` null with nothing "
    + "recorded, and the render gate below turned that into a spinner nobody could clear.");
});

test("SH1049: a failure stays in the screen it happened in", () => {
  // `main.tsx` still mounts the design system's boundary around everything, and it is now the last
  // resort rather than the only one. Without the inner boundary a render throw anywhere blanks the
  // console — which this codebase's own prose records happening, twice.
  const shell = stripComments(readFileSync(
    join(ROOT, "src", "app", "system", "bento.root.page.tsx"), "utf8"));
  const boundary = stripComments(readFileSync(
    join(ROOT, "src", "components", "route-boundary.tsx"), "utf8"));

  assert.match(shell, /<RouteBoundary>\s*<Outlet \/>\s*<\/RouteBoundary>/u,
    "SH1049: the outlet is no longer wrapped, so one screen's throw takes the rail, the header "
    + "and the palette with it and the only way out is a reload");
  assert.match(boundary, /key=\{pathname\}/u,
    "SH1049: a class boundary holds `hasError` until something resets it, so without a key on the "
    + "path a curator who navigates away from a broken screen carries its error to every screen "
    + "after — the fix looking exactly like the defect");
  assert.match(boundary, /client\.resetQueries\(\)/u,
    "SH1049: `try again` that only resets the boundary re-renders against the same failed cache "
    + "entries and throws again, so the button reads as broken. Dropping the reads is what makes "
    + "it mean anything (SH1046).");
  assert.match(boundary, /export function PanelBoundary/u,
    "SH1049: one dead widget must not take the form it is inside, which needs a smaller boundary "
    + "than the route's");
});

test("SH1049: both locales carry the strings the failure needs", () => {
  for (const locale of ["en", "pt"]) {
    const common = JSON.parse(readFileSync(
      join(ROOT, "src", "i18n", "locales", locale, "common.json"), "utf8"));
    const content = JSON.parse(readFileSync(
      join(ROOT, "src", "i18n", "locales", locale, "content.json"), "utf8"));
    assert.ok(common["common.tryAgain"], `SH1049: ${locale} is missing common.tryAgain`);
    assert.ok(content["content.postLoadFailed"],
      `SH1049: ${locale} is missing content.postLoadFailed`);
  }
});
