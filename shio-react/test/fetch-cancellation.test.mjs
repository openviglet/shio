/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { fileURLToPath } from "node:url";

import { stripComments } from "./support/source-text.mjs";

/**
 * SH1047 — a read nobody can cancel, and the list of the ones that still cannot.
 *
 * ## The race
 *
 * `AbortController` appeared nowhere in the console. The content browser's page fetch is rebuilt on
 * every change of folder, sort or filter and the request it replaced was left running, so a curator
 * moving quickly can be shown a **slow earlier response over a fast later one**: the rows belong to
 * a folder they already left, under a breadcrumb that says otherwise. Nothing errors. There is no
 * way to tell by looking, which is why it needs an instrument rather than a fix alone.
 *
 * ## Why the guard is here rather than in the query layer
 *
 * SH1046's `useQuery` hands its `queryFn` a signal and aborts it when the key moves, so a page on
 * the query layer is cancelled by construction. That is the good path and it is not the whole
 * console: thirty-odd screens still read inside an effect, and the ones this task migrated did not
 * move onto the query layer because their state is a cursor or an accumulating page, which is a
 * different task. A lint that only understood `useQuery` would call those screens clean the moment
 * somebody rewrote them and blind the day somebody did not.
 *
 * ## One shared reason, said once
 *
 * `UNCANCELLED` is a list and not a ceiling — a ceiling lets this task pay the debt and the next
 * one spend it. Every entry carries the same reason, written here instead of thirty-four times:
 * *its fetching effect passes no signal, and SH1047 migrated the five screens its symptom named.*
 * Inventing a distinct sentence per file would be inventing, and a reason that is not true is worse
 * than a shared one that is. What the list is for is that a **thirty-fifth** cannot arrive quietly.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const ROOTS = ["src/app", "src/components", "src/hooks"];

/** A call that goes to the server: a service method, or axios reached for directly. */
const FETCHES = /\b\w*[Ss]ervice\.\w+\(|\baxios\.(?:get|post|put|delete|patch)\b/u;

/**
 * The screens that read inside an effect and pass no signal.
 *
 * <p>Thirty-four, measured on 2026-09-20. See the class comment for the one reason they share.
 * Deleting an entry is the fix; adding one needs the diff to say why the screen cannot take a
 * signal, because "not yet" is what this list already means.</p>
 *
 * <p>It lost `home.page.tsx` the same day: SH1050 replaced that page's four mount reads with one
 * `useQuery` against `/v2/console/summary`, and a query hands its `queryFn` a signal. That is the
 * list working — a debt paid off has to leave it, or the next reader counts it twice.</p>
 */
const UNCANCELLED = new Set([
  "src/app/admin/graphql/graphql.page.tsx",
  "src/app/admin/review/review.list.page.tsx",
  "src/app/content/post.edit.page.tsx",
  "src/app/content/post.new.page.tsx",
  "src/app/content/scheduled.page.tsx",
  "src/app/editor/visual-editor.page.tsx",
  "src/app/setup/setup.page.tsx",
  // The one the stripper caught on a real file rather than a planted one: this page's prose says
  // "an ambient signal", and a raw scan read that as cancellation and passed it.
  "src/app/system/bento.root.page.tsx",
  "src/components/post-history.panel.tsx",
  "src/components/post-live-preview.tsx",
  "src/components/widgets/content-browser-dialog.tsx",
  "src/components/working-line.tsx",
]);

/**
 * The five SH1047's design named, and the five SH1116's first slice took off the list.
 *
 * <p>Held apart from `UNCANCELLED` so the declared list cannot absorb a fix and go on recording it
 * as a debt. SH1116 is explicitly a task in slices — each screen's failure path is its own — and the
 * assertion the design names is <b>this list growing while that one shrinks</b>.</p>
 */
const MIGRATED = [
  "src/app/admin/activity/activity.list.page.tsx",
  // SH1145 — the browser's reads moved into this hook; the page holds none of its own.
  "src/app/content/content-browser/use-browser-listing.ts",
  "src/app/content/search.page.tsx",
  "src/app/content/static-file.list.page.tsx",
  "src/app/content/trash.page.tsx",
];

/**
 * SH1116 — the screens that no longer fetch in an effect at all, because they moved to the query layer.
 *
 * <h2>Why they are a second list rather than five more entries above</h2>
 *
 * <p>`MIGRATED` asserts a file still fetches inside an effect *and* passes a signal, which is the
 * right claim for the five that thread an `AbortController` through one. These five moved to the
 * <b>query layer</b>: the read is a `useQuery` and the effects that remain only seed form state from
 * its answer. Asserting they "fetch inside an effect" would be a sentence that is no longer true and
 * that passes anyway — the scan sees a `useEffect` and a `service.` call in one file and cannot tell
 * they are unrelated. The gate's own failure message says to say so here rather than delete the line,
 * so this is that.</p>
 *
 * <h2>The second defect the migration is what fixed</h2>
 *
 * <p>Each of the five had `item.name` — `item.title` for the token — in its effect's dependency list,
 * so <b>typing in the name field re-read the row</b> and `setItem` put the saved copy back: the
 * curator's edits to *every* field reverted a moment after they were made. Nothing errored. A query's
 * key is its subject, so the read re-runs when the tenant or the token changes and never when the
 * form does, which is the whole of that fix.</p>
 */
const QUERY_LAYER = [
  "src/app/admin/api-token/api-token.page.tsx",
  "src/app/admin/auth-provider/auth-provider.page.tsx",
  "src/app/admin/exchange-provider/exchange-provider.page.tsx",
  "src/app/admin/site-webhook/site-webhook.page.tsx",
  "src/app/admin/tenant/tenant.page.tsx",
  /*
   * Slice two: the rest of the admin area. Three more of the same shape, and two of them had a
   * dependency that was an *identity* rather than a subject — `form.reset` on the user page and
   * `service` on the shared group/role editor. Both are stable in practice, which is why nothing
   * went wrong, and neither says which user or which group is wanted; a key says exactly that.
   * The email configuration is the one singleton, so its key carries no id at all.
   */
  "src/app/admin/admin.user.page.tsx",
  "src/app/admin/email-config/email-config.page.tsx",
  "src/app/admin/identity-entity.page.tsx",
  /*
   * Slice three (SH1150): the content editors. The site editor's one effect made three reads that
   * are wanted together and are not one subject — the site, its locale policy and the instance's
   * locale catalogue have three lifetimes, and the catalogue is the same answer for every site.
   * `post-type.new` is NOT here: its effect was empty, so see the SH1150 test below.
   */
  "src/app/content/folder.edit.page.tsx",
  "src/app/content/post-type.edit.page.tsx",
  "src/app/content/site.edit.page.tsx",
];

/** Every source file under the walked roots, as repo-relative paths with forward slashes. */
function sources() {
  const found = [];
  for (const root of ROOTS) {
    for (const entry of readdirSync(join(ROOT, root), { recursive: true })) {
      if (typeof entry === "string" && /\.tsx?$/u.test(entry)) {
        found.push(`${root}/${entry.split("\\").join("/")}`);
      }
    }
  }
  return found.sort();
}

/**
 * Which of these files fetch inside an effect, and which of those pass a signal.
 *
 * <p>Pure over `{path: source}` so the gate can be driven with a planted file rather than only
 * against a tree that satisfies it — the difference between a check that has been shown to fail and
 * one that has never been asked.</p>
 *
 * <p>Comments are stripped first, and that is load-bearing here rather than tidy: every one of the
 * five migrated files carries a paragraph explaining what the signal is for, so a raw scan would
 * pass a file for <em>talking</em> about cancellation. SH740's trap, and this file is the shape
 * most likely to walk into it.</p>
 */
/**
 * Every `useEffect(...)` body in a file, matched to its balanced closing parenthesis.
 *
 * <p>SH1151 — and the reason it exists is that the first version of this scan asked two questions of
 * the <b>whole file</b>: does it contain `useEffect(`, and does it contain a service call. Those are
 * not the same as "it reads inside an effect", and the difference produced real false positives:</p>
 *
 * <ul>
 *   <li>`post-type.new.page.tsx` held an <b>empty</b> effect and a `service.create` in its submit
 *   handler, and was counted (SH1150 deleted the effect rather than argue with the scan).</li>
 *   <li>The two tenant components hold a `setDismissed` effect and a `service.switchTo` in a click
 *   handler — a <b>write</b>, which cannot take a signal at all: aborting a POST does not undo it,
 *   so `read-options.ts` deliberately offers cancellation to reads only. Counting a write as an
 *   uncancelled read asks for something the design refuses.</li>
 * </ul>
 *
 * <p>Scoping the search to the effect bodies is what makes the list mean what it says. A brace or
 * paren inside a string would confuse this, so `stripComments` runs first and the counter ignores
 * nothing else — measured against the console, it finds every effect the file has.</p>
 */
export function effectBodies(code) {
  const bodies = [];
  const marker = /useEffect\(/gu;
  for (let found = marker.exec(code); found; found = marker.exec(code)) {
    let depth = 1;
    let i = found.index + found[0].length;
    for (; i < code.length && depth > 0; i++) {
      if (code[i] === "(") depth++;
      else if (code[i] === ")") depth--;
    }
    bodies.push(code.slice(found.index, i));
  }
  return bodies;
}

/**
 * The body of a named function an effect calls, so a read hoisted out of the effect still counts.
 *
 * <p>The half that keeps the scoping above from trading a false positive for a **false negative**,
 * which would be the worse bargain: `activity.list.page.tsx` and `review.list.page.tsx` both call a
 * `useCallback` from their effect, and the read lives in the callback. Scoping to effect bodies alone
 * declared them clean — a gate that stops seeing a real uncancelled read is worse than one that
 * counts a write, because the first failure is silent.</p>
 *
 * <p>One hop, not a call graph. Two would be a different program, and one is what the console does.</p>
 */
export function calledBodies(code, effects) {
  const bodies = [];
  const names = new Set();
  for (const effect of effects) {
    for (const [, name] of effect.matchAll(/\b([a-z]\w*)\s*\(/gu)) names.add(name);
  }
  for (const name of names) {
    const declared = new RegExp(
      `(?:const\\s+${name}\\s*=|(?:async\\s+)?function\\s+${name}\\b)`, "u").exec(code);
    if (!declared) continue;
    // From the declaration to the end of the statement that follows it: coarse on purpose, because
    // the question is only "does a read appear in here", and over-reading widens the scan rather
    // than narrowing it — the direction a gate is allowed to be wrong in.
    bodies.push(code.slice(declared.index, declared.index + 2000));
  }
  return bodies;
}

export function cancellation(files) {
  const fetching = [];
  const uncancelled = [];
  for (const [path, source] of Object.entries(files)) {
    const code = stripComments(source);
    // SH1151 — inside the effects and whatever they call, never anywhere in the file. A `service.`
    // call in a click handler is a write on a button, and a write cannot take a signal at all.
    const effects = effectBodies(code);
    const reading = [...effects, ...calledBodies(code, effects)]
      .filter((body) => FETCHES.test(body));
    if (!reading.length) continue;
    fetching.push(path);
    if (!reading.some((body) => /\bsignal\b/u.test(body))) uncancelled.push(path);
  }
  return { fetching: fetching.sort(), uncancelled: uncancelled.sort() };
}

/** The console as it is on disk. */
function onDisk() {
  return Object.fromEntries(sources()
    .map((path) => [path, readFileSync(join(ROOT, path), "utf8")]));
}

test("SH1047: no screen fetches inside an effect without a signal, outside the declared list", () => {
  const { fetching, uncancelled } = cancellation(onDisk());

  console.log(`SH1047 — ${fetching.length} screen(s) fetch inside an effect; `
    + `${fetching.length - uncancelled.length} pass a signal, ${uncancelled.length} do not`);

  // The vacuity guard: a regex that stopped matching would report a console with no fetching
  // effects at all, which agrees with every list including an empty one.
  // SH1151 recalibrated this floor: it was 30 against a scan that asked whether a file contained a
  // `useEffect` and a service call anywhere, which counted writes in click handlers. Reading the
  // effects and what they call gave 37.
  //
  // It comes down as SH1116's slices land, and that is the honest direction: a screen that moves to
  // the query layer stops having a fetching effect at all, so `fetching` falls with `uncancelled`.
  // The floor is a vacuity guard — it exists so a scan that has stopped matching cannot report a
  // clean console — and it is set below the current measurement rather than at it, so it does not
  // have to be edited by every slice. It may only ever come down alongside a shrinking list.
  assert.ok(fetching.length >= 28,
    `SH1047: only ${fetching.length} fetching effect(s) found — this scan is measuring nothing`);

  const arrived = uncancelled.filter((path) => !UNCANCELLED.has(path));
  assert.deepEqual(arrived, [],
    "SH1047: a new screen reads inside an effect and cancels nothing, so a fast navigation can "
    + "render its earlier answer over its later one — the rows of a thing the curator already "
    + "left, with nothing to notice by. Take the signal from `useQuery`, or thread an "
    + "`AbortController` through the effect and abort it on cleanup; `isCancelled` in "
    + "`lib/read-options.ts` is what keeps the rejection out of the failure path.");

  const paid = [...UNCANCELLED].filter((path) => !uncancelled.includes(path)).sort();
  assert.deepEqual(paid, [],
    "SH1047: these are on the declared list and no longer need to be — delete them from "
    + "UNCANCELLED in the same commit, so a debt cannot be paid off and left on the books where "
    + "the next reader counts it twice.");
});

test("SH1047: the five screens the task named cancel, and cannot rejoin the list", () => {
  const { fetching, uncancelled } = cancellation(onDisk());

  for (const path of MIGRATED) {
    assert.ok(fetching.includes(path), `SH1047: ${path} no longer fetches in an effect at all, so `
      + "this assertion has stopped being about anything — if it moved to the query layer, say so "
      + "here rather than deleting the line");
    assert.ok(!uncancelled.includes(path),
      `SH1047: ${path} stopped passing a signal. It is one of the five this task migrated, and `
      + "the content browser is the screen the symptom was written about.");
    assert.ok(!UNCANCELLED.has(path),
      `SH1047: ${path} is both migrated and declared uncancelled — the list would then absorb the `
      + "fix rather than record the debt");
  }
});

test("SH1116: every query-layer screen takes the signal useQuery hands it, and is off the list", () => {
  const { uncancelled } = cancellation(onDisk());

  for (const path of QUERY_LAYER) {
    const code = stripComments(readFileSync(join(ROOT, path), "utf8"));

    // The claim, read off the source: a `queryFn` that is handed a signal and forwards it. A
    // `queryFn: () => service.get(id)` compiles, caches, and cancels nothing — which is this
    // migration's own way of going wrong, and it would leave the file looking done.
    // The service variable is named per page — `service`, `postTypeService`, `localeService` — so this
    // matches the same shape `FETCHES` above does rather than one spelling of it.
    assert.match(code, /queryFn: \(\{ signal \}\) => \w*[Ss]ervice\.\w+\(/u,
      `SH1116: ${path} has a queryFn that does not take useQuery's signal. The whole reason to move `
        + "a read here is that the signal is aborted when the key changes or the screen unmounts; a "
        + "queryFn that ignores it is the same uncancelled read with a cache in front of it.");

    // The dependency that made typing revert the form. `item.name` and `item.title` are the two
    // spellings the five used; either back in an effect's deps is the defect returning.
    assert.doesNotMatch(code, /\}, \[[^\]]*\bitem\.(?:name|title)\b/u,
      `SH1116: ${path} has item.name or item.title back in an effect's dependencies. That is what `
        + "re-read the row on every keystroke and put the saved copy back over what was being typed.");

    // A cancelled read must not reach the failure path, or the fix is a reliable blank screen in
    // place of a rare race — the hazard §LXXII.21 says to carry into every slice.
    assert.match(code, /isCancelled\(/u,
      `SH1116: ${path} reports a read failure without asking whether it was a cancellation. An `
        + "aborted request rejects, so a navigation would be reported to the curator as an error.");

    assert.ok(!uncancelled.includes(path),
      `SH1116: ${path} is back among the screens that read without a signal`);
    assert.ok(!UNCANCELLED.has(path),
      `SH1116: ${path} is on the declared list and migrated — a debt paid off has to leave the `
        + "books, or the next reader counts it twice");
  }

  // The slice's own arithmetic, so a later slice cannot quietly put one back. Held as a ceiling
  // rather than an equality: the next slice lowers it, and nothing may raise it.
  assert.ok(UNCANCELLED.size <= 12,
    `SH1116: ${UNCANCELLED.size} screens are still declared. The list was 33 when this task opened; `
      + "the slices took it to 28, 25, 21, 19, 16, 14 and 12. It may only ever shrink.");
});

test("SH1149: the admin area's last three key on their subject, and the singleton reports failure",
  () => {
    /*
     * SH1149 — slice two's own boundary, and the two shapes it is about are not cancellation.
     *
     * An effect keyed on an **identity** rather than on a subject: `form.reset` on the user editor
     * and `service` on the shared group/role editor. Both are stable in practice, which is why
     * nothing visibly broke, and neither says *which* user or *which* group is wanted — so the read
     * could not be right for the reason a key is right, it merely happened not to be wrong. That is
     * the thing a later refactor turns into a real bug, and it is invisible in a screenshot.
     */
    const user = stripComments(readFileSync(
      join(ROOT, "src/app/admin/admin.user.page.tsx"), "utf8"));
    assert.doesNotMatch(user, /\}, \[[^\]]*\bform\b/u,
      "SH1149: `form` is back in an effect's dependencies on the user editor. A `useForm` identity is "
        + "not the subject being read, and the username is.");
    assert.match(user, /queryKey: addressKey\(`user:\$\{userId \?\? ""\}`\)/u,
      "SH1149: the user editor's cache key is the username it reads");

    const identity = stripComments(readFileSync(
      join(ROOT, "src/app/admin/identity-entity.page.tsx"), "utf8"));
    assert.doesNotMatch(identity, /\}, \[[^\]]*\bservice\b/u,
      "SH1149: `service` is back in an effect's dependencies on the shared group/role editor — an "
        + "object identity, not the entity being read");
    assert.match(identity, /queryKey: addressKey\(`\$\{scheme\}\$\{id\}`\)/u,
      "SH1149: the shared editor is cached under the scheme its caller passes, so a group and a role "
        + "are two subjects and not one. Deriving a scheme from the route parameter would be one typo "
        + "from a key no invalidation ever names, which is why `addressKey` throws on an unknown one.");
    assert.match(identity, /scheme: ShScheme/u,
      "SH1149: and the scheme is a declared scheme rather than a string");

    /*
     * The singleton's failure path, which was the more urgent half. `catch(() => setConfig(EMPTY))`
     * made "the server did not answer" and "nothing is configured" the same screen — and the second
     * one invites a save that overwrites the first one's real settings.
     */
    const email = stripComments(readFileSync(
      join(ROOT, "src/app/admin/email-config/email-config.page.tsx"), "utf8"));
    assert.doesNotMatch(email, /catch\(\(\) => setConfig\(EMPTY\)\)/u,
      "SH1149: an unanswered server and an unconfigured tenant must not render the same screen — the "
        + "empty form is the one that invites a save over the real settings");
    assert.match(email, /queryKey: addressKey\("emailConfig:"\)/u,
      "SH1149: one configuration per tenant, so the key carries no id");

    // The scheme has to be declared, or `addressKey` throws at the first render of that page.
    const keys = readFileSync(join(ROOT, "src/lib/query-keys.ts"), "utf8");
    assert.match(keys, /"emailConfig:"/u,
      "SH1149: `emailConfig:` is not in SCHEMES, so addressKey throws where the page calls it");
  });

test("SH1150: the content editors key on their subject, and one entry was never a debt", () => {
  /*
   * SH1150 — the fourth file is the finding, and it is about this scan rather than about the console.
   *
   * `post-type.new.page.tsx` held `useEffect(() => {}, [t])`: an empty body. It sat on the declared
   * list because `cancellation()` matches `useEffect(` and a `service.` call **anywhere in the same
   * file**, and its `create` is in the submit handler. So one of the twenty-five was never a debt,
   * and the correct fix was to delete dead code rather than to migrate anything. A list that records
   * a debt has to be able to record that an entry was wrong, or it grows a floor nobody can reach.
   */
  const fresh = stripComments(readFileSync(
    join(ROOT, "src/app/content/post-type.new.page.tsx"), "utf8"));
  assert.doesNotMatch(fresh, /useEffect/u,
    "SH1150: the empty effect is back on the post-type create page. It fetched nothing, and it put "
      + "the file on the uncancelled list by standing next to a `service.create` in the submit "
      + "handler — a declared debt that was never one.");

  // The three that were real, each keyed on what it reads rather than on a translation function or a
  // form identity, and each with the failure path it did not have.
  for (const [file, key] of [
    ["src/app/content/folder.edit.page.tsx", /queryKey: addressKey\(`id:\$\{folderId \?\? ""\}`\)/u],
    ["src/app/content/post-type.edit.page.tsx", /queryKey: addressKey\(`postType:\$\{postTypeName\}`\)/u],
    ["src/app/content/site.edit.page.tsx", /queryKey: addressKey\(`id:\$\{siteId \?\? ""\}`\)/u],
  ]) {
    const code = stripComments(readFileSync(join(ROOT, file), "utf8"));
    assert.match(code, key, `SH1150: ${file}'s cache key is not the thing it reads`);
    assert.match(code, /queryFn: \(\{ signal \}\) => \w*[Ss]ervice\.\w+\(/u,
      `SH1150: ${file} has a queryFn that does not take useQuery's signal`);
    assert.match(code, /isCancelled\(/u,
      `SH1150: ${file} reports a read failure without asking whether it was a cancellation. None of `
        + "these three had a failure path at all before — a folder or a site that would not load left "
        + "the previous one's fields on screen under the new one's breadcrumb.");
  }

  // The site editor's three reads are three queries, because they have three lifetimes and the
  // locale catalogue is the same answer for every site.
  const site = stripComments(readFileSync(
    join(ROOT, "src/app/content/site.edit.page.tsx"), "utf8"));
  /*
   * Three subjects, and SH1152 moved two of them into shared hooks because `post-translations.menu`
   * asks for the same two. So the claim is checked where each now lives rather than by counting
   * `useQuery` in one file — the count would have passed for a page that had merged them back.
   */
  assert.equal((site.match(/useQuery</gu) ?? []).length, 1,
    "SH1150: the site editor's own subject is the site; the other two are shared reads");
  assert.match(site, /const reportedPolicy = useSiteLocalePolicy\(siteId\)/u,
    "SH1150: the site's locale policy is its own subject, read through the shared hook");
  assert.match(site, /const localeCatalogue = useLocales\(\)/u,
    "SH1150: the instance's locale catalogue is the same answer for every site, so it is shared");
  const shared = stripComments(readFileSync(join(ROOT, "src/hooks/use-catalogue.ts"), "utf8"));
  assert.match(shared, /queryFn: \(\{ signal \}\) => localeService\.list\(\{ signal \}\)/u,
    "SH1150: and the shared catalogue read takes the signal useQuery hands it");
  assert.match(shared, /queryFn: \(\{ signal \}\) => siteService\.locales\(siteId \?\? "", \{ signal \}\)/u);
  assert.match(site, /function searchableOf\(/u,
    "SH1150: the free-text searchable list is read by a named function — the column has held a "
      + "non-JSON value, and an unreadable list is \"none selected\" rather than a crash on the one "
      + "page a curator could fix it from");

  // And `t` is not back among the things that decide which post-type is read.
  const postType = stripComments(readFileSync(
    join(ROOT, "src/app/content/post-type.edit.page.tsx"), "utf8"));
  assert.doesNotMatch(postType, /\}, \[postTypeName, t, form\]/u,
    "SH1150: a translation function and a form identity are back in the dependencies that decide "
      + "which post-type is read");
});

test("SH1151: a write in a handler is not an uncancelled read, and a hoisted read still is", () => {
  /*
   * SH1151 — this scan asked two questions of the whole file: does it contain `useEffect(`, and does
   * it contain a service call. Neither is "it reads inside an effect", and both halves of the gap
   * were real:
   *
   * - A **write** in a click handler counted. `read-options.ts` deliberately offers cancellation to
   *   reads only — aborting a POST does not undo it — so the list was asking two tenant components
   *   for something the design refuses. SH1150 had already met the same thing as an empty effect
   *   beside a `service.create`.
   * - Scoping to effect bodies alone then went too far the other way, which is the worse bargain: a
   *   read hoisted into a `useCallback` an effect calls became invisible. `activity.list.page.tsx`
   *   and `review.list.page.tsx` are both that shape, and a gate that stops seeing a real
   *   uncancelled read fails silently where one that counts a write fails loudly.
   */
  const write = `
    const service = new ShTenantService()
    export function Switcher() {
      useEffect(() => setDismissed(false), [claimed])
      const switchTo = async () => { await service.switchTo(name) }
      return <button onClick={switchTo} />
    }`;
  assert.deepEqual(cancellation({ "src/app/w.tsx": write }).fetching, [],
    "SH1151: a write on a button is not a read in an effect, and cancelling it is not a thing the "
      + "design offers — `read-options.ts` says so in as many words");

  const hoisted = `
    export function Trail() {
      const load = useCallback(() => { void historyService.recent({}) }, [])
      useEffect(() => { void load() }, [load])
    }`;
  assert.deepEqual(cancellation({ "src/app/h.tsx": hoisted }).uncancelled, ["src/app/h.tsx"],
    "SH1151: the read is one hop from the effect and it is still the effect's read. Missing this is "
      + "the false negative that would have made this whole list read shorter than it is.");

  const hoistedWithSignal = `
    export function Trail() {
      const load = useCallback((signal) => { void historyService.recent({}, { signal }) }, [])
      useEffect(() => { const a = new AbortController(); void load(a.signal); return () => a.abort() }, [load])
    }`;
  assert.deepEqual(cancellation({ "src/app/hs.tsx": hoistedWithSignal }).uncancelled, [],
    "SH1151: and a hoisted read that takes a signal is not a debt");

  // The effect extractor's own subject: nested parens must not end the body early, or a read after
  // one would be missed — the same silent shape as above.
  const nested = "useEffect(() => { void svc.get(one(two(three))) }, [x])";
  assert.equal(effectBodies(nested).length, 1);
  assert.match(effectBodies(nested)[0], /svc\.get/u);
  assert.equal(effectBodies("useEffect(() => {}, [a])\nuseEffect(() => {}, [b])").length, 2,
    "SH1151: two effects are two bodies");

  /*
   * SH1151's own reason for existing: the two tenant components each read `mine()` and `current()`
   * in their own effect, and `bento.root.page.tsx` mounts BOTH — four requests for two answers on
   * every visit to the bento root. They share the reads now, and the shared hooks are where a third
   * caller will find them.
   */
  for (const file of ["src/app/system/bento-tenant-switcher.tsx",
    "src/app/system/bento-tenant-link-notice.tsx"]) {
    const code = stripComments(readFileSync(join(ROOT, file), "utf8"));
    assert.match(code, /useCurrentTenant\(\)/u, `SH1151: ${file} reads the tenant on its own again`);
    assert.match(code, /useMyTenants\(\)/u, `SH1151: ${file} reads the memberships on its own again`);
    assert.doesNotMatch(code, /service\.(?:mine|current)\(/u,
      `SH1151: ${file} is back to asking for an answer the other component is already asking for`);
  }
  const hooks = stripComments(readFileSync(join(ROOT, "src/hooks/use-catalogue.ts"), "utf8"));
  assert.match(hooks, /queryFn: \(\{ signal \}\) => tenantService\.mine\(\{ signal \}\)/u,
    "SH1151: the shared read takes the signal useQuery hands it");
  assert.match(hooks, /queryFn: \(\{ signal \}\) => tenantService\.current\(\{ signal \}\)/u);
});

test("SH1152: the post editor's companions share what they share, and writes invalidate", () => {
  const shared = stripComments(readFileSync(join(ROOT, "src/hooks/use-catalogue.ts"), "utf8"));

  /*
   * SH1152 — the second dedup this task has turned up, and the same shape as SH1151's tenant pair:
   * `site.edit.page.tsx` and `post-translations.menu.tsx` both read the instance's locale catalogue
   * and one site's locale policy, and the menu is mounted from the post editor. Two components
   * independently correct and collectively wasteful.
   */
  for (const file of ["src/app/content/site.edit.page.tsx",
    "src/components/post-translations.menu.tsx"]) {
    const code = stripComments(readFileSync(join(ROOT, file), "utf8"));
    assert.match(code, /useLocales\(\)/u, `SH1152: ${file} reads the locale catalogue on its own again`);
    assert.match(code, /useSiteLocalePolicy\(/u, `SH1152: ${file} reads the site policy on its own`);
    assert.doesNotMatch(code, /localeService\.list\(/u,
      `SH1152: ${file} is back to asking for a catalogue the other surface already has`);
  }

  /*
   * A cache the reads did not have brings a defect the reads did not have: an answer that is right
   * when it is fetched and wrong after a write. Creating a translation changes the group as seen from
   * every member of it, and the curator is navigated to the new page and may come back — so the
   * write invalidates the whole scheme, not the key it was called from.
   */
  const menu = stripComments(readFileSync(
    join(ROOT, "src/components/post-translations.menu.tsx"), "utf8"));
  assert.match(menu, /await invalidate\("translation:"\)/u,
    "SH1152: creating a translation does not invalidate the group. The source post's menu would go "
      + "on showing the group as it was before the translation it just created — which the "
      + "effect-shaped read could not get wrong, because it had no cache to be stale.");

  const rail = stripComments(readFileSync(join(ROOT, "src/components/note-rail.tsx"), "utf8"));
  assert.match(rail, /void invalidate\(`note:\$\{objectId\}`\)/u,
    "SH1152: adding a note does not refresh the rail it was added to");
  assert.doesNotMatch(rail, /catch\(\(\) => setNotes\(\[\]\)\)/u,
    "SH1152: an unanswered server and a field with no notes must not render the same empty rail");

  /*
   * The insights panel's key carries the revision, which is what makes its two long-standing wants
   * true at once: a save re-lints and a keystroke does not. As an effect dependency that was a
   * re-run; as part of the key it is a different subject, which is what it actually is.
   */
  const insights = stripComments(readFileSync(
    join(ROOT, "src/components/post-insights.panel.tsx"), "utf8"));
  assert.match(insights, /queryKey: addressKey\(`insights:\$\{address \?\? ""\}\|\$\{revision\}`\)/u,
    "SH1152: the revision belongs in the insights key — a lint of the saved draft is a different "
      + "subject per revision, not the same subject read again");
  assert.match(insights, /failed = read\.isError && !isCancelled\(read\.error\)/u,
    "SH1152: a cancelled lint is a navigation, not a failed one, and must not raise the panel's flag");

  // Every scheme these keys use has to be declared, or `addressKey` throws where the page calls it.
  const keys = readFileSync(join(ROOT, "src/lib/query-keys.ts"), "utf8");
  for (const scheme of ["note:", "translation:", "insights:", "locale:"]) {
    assert.ok(keys.includes(`"${scheme}"`),
      `SH1152: ${scheme} is not in SCHEMES, so addressKey throws on the first render that uses it`);
  }
});

test("SH1153: the two widgets cancel the resolve instead of ignoring its answer", () => {
  /*
   * SH1153 — the `let alive = true` flag these two carried is exactly what SH1047's own note says the
   * signal is for: it **ignored** a superseded answer, where the signal stops the server being asked
   * for it. Two costs, and the second is the one a curator could see: the flag could not tell a
   * cancellation from a failure, so both landed on `{ file: null }` / `{ content: null }` — "this
   * reference resolves to nothing", which is what the widget draws its missing state for, and "we
   * stopped asking" rendered identically.
   */
  for (const [file, id, field] of [
    ["src/components/widgets/content-select.widget.tsx", "contentId", "content"],
    ["src/components/widgets/file.widget.tsx", "fileId", "file"],
  ]) {
    const code = stripComments(readFileSync(join(ROOT, file), "utf8"));

    assert.doesNotMatch(code, /let alive = true/u,
      `SH1153: ${file} is back to ignoring a superseded answer rather than cancelling the request`);
    assert.match(code, new RegExp(`queryFn: \\(\\{ signal \\}\\) => resolveFullPath\\(${id}, \\{ signal \\}\\)`, "u"),
      `SH1153: ${file}'s resolve does not take the signal useQuery hands it`);

    // The resolve makes two reads of its own, and a signal that reaches only the first cancels half
    // the work — which is the shape that looks done and is not.
    const resolve = /async function resolveFullPath\([\s\S]*?\n\}/u.exec(code)?.[0] ?? "";
    assert.ok(resolve.length > 0, `SH1153: ${file}'s resolveFullPath moved and this scan lost it`);
    assert.equal((resolve.match(/signal: read\.signal/gu) ?? []).length, 2,
      `SH1153: ${file}'s resolve makes two requests and ${
        (resolve.match(/signal: read\.signal/gu) ?? []).length} of them take the signal`);

    // Seeded during render, once per id — `react-hooks/set-state-in-effect`, as every slice since
    // the first has had to be.
    assert.match(code, new RegExp(`if \\(resolvedPath.isSuccess && seeded !== ${id}\\)`, "u"),
      `SH1153: ${file} seeds from the query on every render rather than once per id`);
    // `isSuccess`, not a truthy check on the data: `null` is a real answer here — the reference
    // resolves to nothing — and a truthy guard would re-seed for ever on exactly that answer.
    assert.doesNotMatch(code, new RegExp(`if \\(resolvedPath.data && seeded`, "u"),
      `SH1153: ${file} guards on the data, and \`null\` is a legitimate answer for these two`);
    assert.ok(code.includes(`${field}: resolvedPath.data`),
      `SH1153: ${file} no longer puts the resolved path where the widget reads it`);
  }
});

test("SH1154: the two dialogs read on open, and one of them paid off a suppression", () => {
  const publish = stripComments(readFileSync(
    join(ROOT, "src/components/publish-preview.dialog.tsx"), "utf8"));
  const permissions = stripComments(readFileSync(
    join(ROOT, "src/components/permissions.dialog.tsx"), "utf8"));

  /*
   * SH1154 — the publish preview's effect did two jobs: it READ the plan and the route impact, and it
   * RESET four pieces of state so a second opening did not show the first one's answers. Doing both
   * synchronously is what earned this file one of SH1136's `set-state-in-effect` suppressions, and
   * splitting them is what removed it — the reads are a query enabled only while open, so closing the
   * dialog *is* the reset for those three.
   */
  assert.match(publish, /enabled: open && Boolean\(postId\)/u,
    "SH1154: the plan is read whether or not the dialog is open, which is a request per mount for an "
      + "answer nobody asked for");
  assert.equal((publish.match(/signal: read\.signal|\{ signal \}/gu) ?? []).length >= 2, true,
    "SH1154: both of the dialog's two reads take the signal");
  assert.match(publish, /if \(open !== wasOpen\)/u,
    "SH1154: the two fields a curator types into reset on the open transition, during render");
  assert.doesNotMatch(publish, /useEffect/u,
    "SH1154: an effect is back in the publish preview, which is where its suppression came from");

  assert.match(permissions, /queryFn: \(\{ signal \}\) => service\.get\(objectId, \{ signal \}\)/u,
    "SH1154: the permissions dialog's read does not take the signal");
  assert.doesNotMatch(permissions, /catch\(\(\) => setEntries\(\[\]\)\)/u,
    "SH1154: on a permissions dialog \"the server did not answer\" and \"this object has no rules of "
      + "its own\" are opposite facts — the second says it inherits, which is what a curator acts on");
  assert.match(permissions, /if \(read\.isSuccess && seeded !== objectId\)/u,
    "SH1154: the rows are edited before they are saved, so they are seeded once per object rather "
      + "than derived");

  /*
   * And the suppressions this actually retired, asserted rather than remembered: `eslint --prune-
   * suppressions` found `post-insights.panel.tsx` stale too, left over from SH1152. SH1136 is the task
   * that owns paying these off, so a slice that pays one has to take it off both books or the next
   * reader counts it twice.
   */
  const suppressions = JSON.parse(readFileSync(join(ROOT, "eslint-suppressions.json"), "utf8"));
  for (const paid of ["src/components/publish-preview.dialog.tsx",
    "src/components/post-insights.panel.tsx"]) {
    assert.ok(!(paid in suppressions),
      `SH1154: ${paid} is back in eslint-suppressions.json — either the effect returned, or a `
        + "suppression was re-added for a finding that no longer occurs");
  }
  const baseline = readFileSync(
    join(ROOT, "..", "cli", "test", "lint-baseline.test.mjs"), "utf8");
  for (const paid of ["src/components/publish-preview.dialog.tsx",
    "src/components/post-insights.panel.tsx"]) {
    assert.ok(!baseline.includes(`"${paid}": 1`),
      `SH1154: ${paid} is still declared in the lint baseline, which is the other book SH1136 keeps`);
  }
});

test("SH1047: a planted read with no signal fails by name, and one with a signal does not", () => {
  // Driven, because the rules above have only ever been run against a tree that satisfies them.
  const withSignal = 'useEffect(() => { const a = new AbortController();'
    + ' void siteService.query({ signal: a.signal }); return () => a.abort() }, [])';
  const without = "useEffect(() => { void siteService.query() }, [])";

  assert.deepEqual(cancellation({ "src/app/planted.tsx": without }).uncancelled,
    ["src/app/planted.tsx"], "a fetching effect with no signal must be named");
  assert.deepEqual(cancellation({ "src/app/planted.tsx": withSignal }).uncancelled, []);

  // And the two ways this scan could pass a file it should not.
  assert.deepEqual(cancellation({ "src/app/talky.tsx": `/* the signal is aborted */ ${without}` })
    .uncancelled, ["src/app/talky.tsx"],
  "SH1047: a comment about cancelling is not cancelling, and every migrated file has one — "
    + "without the stripper this scan would pass the console for describing itself");
  assert.deepEqual(cancellation({ "src/app/quiet.tsx": "useEffect(() => { setOpen(true) }, [])" })
    .fetching, [],
  "an effect that fetches nothing is not this file's business");
});
