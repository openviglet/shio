/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH701 — the lint baseline is a debt, and this is what counts it.
 *
 * ## What a baseline is, and what it silently becomes
 *
 * SH665 adopted the `react-hooks` compiler rules against a console that already existed. Adopting a
 * rule mid-project means choosing between a config commit that also rewrites 28 unrelated files and
 * a baseline that records what was already broken. The baseline is the right call, and
 * `shio-react/eslint-suppressions.json` is it.
 *
 * What it becomes, left alone, is a ceiling nobody reads. `eslint --suppress-all` regenerates the
 * file from whatever is red *today*, so a violation added tomorrow can be absorbed by the same
 * command that was supposed to record yesterday's. Nothing prints the number. Nothing dates it.
 *
 * ## SH699 is the evidence, and it is the assertion this file owes
 *
 * A page written after the baseline — `marketplace.list.page.tsx` — hit the identical defect the
 * baseline was holding thirteen instances of. CI caught it, which is the system working; but it
 * caught it because that page was *outside* the baseline, not because anybody could see that
 * thirteen were inside. Had the file been regenerated first, the fourteenth would have joined the
 * thirteen and the gate would have stayed green.
 *
 * So the assertion that would have caught SH699's defect coming back is not "is eslint green" —
 * eslint answers that, and answers it in a vocabulary of rule ids rather than task ids. It is
 * **this**: the baseline is exactly the declared list, and it does not grow.
 *
 * ## A named list, never a ceiling
 *
 * The same contract `assertion-debt.test.mjs` states for SH527's debt and `red-suites.test.mjs` for
 * SH579's exceptions: a ceiling lets one task pay the debt and the next task spend it. So the shape
 * below is per file and per rule, and adding to it is a diff somebody reviews.
 *
 * The counts are **printed on every run**, because a gate that checks nothing passes quietly.
 */
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { test } from "node:test";

// @scans prose reason: the ESLint baseline is JSON and the rule ids inside it are the subject,
// read as text rather than as code around them (SH740).

const BASELINE = new URL("../../shio-react/eslint-suppressions.json", import.meta.url);

/**
 * The declared baseline: every suppressed violation, by file and rule.
 *
 * <h2>`set-state-in-effect` went 15 → 0, and the route there is the finding</h2>
 *
 * <p>Four tasks, and no two of them were the same fix — which is the argument for a baseline being
 * a <em>list</em> rather than a number. A count of fifteen suggests one sweep; the list is what
 * showed that the fifteen were four different defects, and that two of them had been grouped by
 * reading the lint report instead of the code.</p>
 *
 * <p>It held **15 over 13 files** when SH701 opened. The four SH701 took off were one construction:
 * a `useCallback` opening with `setLoading(true)`, invoked straight from a mount effect — the shape
 * SH699 landed on `marketplace.list.page.tsx`. Mechanical, and asserted only by eslint.</p>
 *
 * <p>SH702 took four more, and those needed a behaviour change: four dialogs syncing to an `open`
 * prop, replaced by a keyed remount so closing unmounts the body. That one is asserted against a
 * <em>running console</em> by `shio-react/e2e/dialog-remount.check.mjs`, because what it fixes is
 * not visible to a lint — a reopened version history kept the previous read's <b>Restaurar versão
 * publicada</b> button for the whole of the next read.</p>
 *
 * <p>SH703 took six more by deriving instead of setting — a spinner that was a comparison, a
 * selection and a bridge handshake that now carry what they belong to, and two widgets that tag
 * their resolved path, which closed a real race rather than a frame. Two of those six arrived from
 * SH702's list: opening them found no `open` prop at all.</p>
 *
 * <p>SH704 took the last, and is the only one that left a test behind. The module cache became a
 * subscription in `@/lib/discovery-store` — a plain module a Node test can drive — so what it
 * promises is asserted directly instead of by this file's count.</p>
 *
 * <p>What SH703's own change is <b>not</b> asserted by is a browser. Its user-visible effects are
 * one frame wide — the spinner arriving a render late, a selection count clearing a render late —
 * and an assertion that cannot distinguish one frame reliably is a flaky gate, which is worse than
 * none (SH579). It is on the declared debt in `assertion-debt.test.mjs` with that reason.</p>
 *
 * <h2>What stays, and why the rest is not the same kind of debt</h2>
 *
 * <p>`react-refresh/only-export-components` is 9 files and is not a defect at all: every one is a
 * context or a `ui/` primitive that exports a hook or a variants object beside its component. The
 * rule is about hot-reload granularity in dev, the export is the API, and "fixing" it means
 * splitting those files to please a dev-server heuristic. It was 14 until SH1046: four of the
 * five it lost had been deleted from the tree rounds earlier, and the fifth was
 * `ShioServiceContext.tsx`, the dead provider that task replaced — which is the finding the last
 * test in this file now gates.</p>
 *
 * <p>The other three are single instances in two files, and each is a real smell that is cheaper to
 * leave visible here than to fix blind: they sit in the two largest pages in the console.</p>
 */
const DECLARED = {
  // SH704 paid the last one off, and it came back to eleven while nothing local ran the lint: CI
  // was red on it for weeks and `pnpm test` was green (SH1075). Recorded here rather than fixed in
  // that task, because each is an effect to rework in a page nobody can drive from a Node test, and
  // SH1075's own job was the instrument — `shio-react/test/lint-gate.test.mjs` now runs ESLint
  // inside `pnpm test`, so the twelfth fails locally. Paying these off is SH1136. None is on the
  // PAID list below; the two paid pages that had regressed were fixed instead, not suppressed.
  // Empty again since SH1136 (see PAID), and left declared like the empty groups below.
  "react-hooks/set-state-in-effect": {},
  // SH1075 — a ref read during render, the same accrual and the same payoff task (SH1136). Empty
  // since SH1136's second slice, and left declared like the group below: the two hooks write their
  // latest value in a layout effect, and the Activity page's back-stack is state, because whether
  // "Newer" is enabled is read from it during render.
  "react-hooks/refs": {},
  // Also empty, and also left declared: SH1046 took the last one off. The content browser's
  // `postTypeService.query().then(...)` was an expression statement, and it went with the effect
  // that read the whole post-type catalogue once per folder.
  "@typescript-eslint/no-unused-expressions": {},
  "no-useless-assignment": {
    "src/app/content/post-type.edit.page.tsx": 1,
  },
  // Empty since SH1226: the media library chose its icon per file during render in three places,
  // and one `MediaCategoryIcon` component replaced them. Left declared like the groups above.
  "react-hooks/static-components": {},
  "react-refresh/only-export-components": {
    // SH1075 added the conflict dialog's two and field-constraints' one; SH1136 moved those helpers
    // to `lib/post-conflict.ts` and `lib/constraint-hint.ts`, which read better as modules of pure
    // functions. What stays is the kind the paragraph above calls the API rather than a defect.
    "src/app/login/login-settings.tsx": 1,
    "src/app/routes/graphql.routes.tsx": 1,
    "src/components/theme-provider.tsx": 1,
    "src/components/ui/badge.tsx": 1,
    "src/components/ui/button.tsx": 1,
    "src/components/ui/form.tsx": 1,
    "src/components/ui/sonner.tsx": 1,
    "src/contexts/user.context.tsx": 1,
  },
};

/**
 * Flatten a suppressions document into `rule -> file -> count`, which is how it is declared above.
 *
 * <p>The file's own shape is `file -> rule -> {count}`, which is the right index for eslint and the
 * wrong one for a reader: the question a person asks is "how much of rule X is still suppressed",
 * and answering it from the on-disk shape means walking every file.</p>
 *
 * @param document the parsed `eslint-suppressions.json`
 * @returns the same facts, keyed by rule
 */
export function byRule(document) {
  const out = {};
  for (const [file, rules] of Object.entries(document)) {
    for (const [rule, entry] of Object.entries(rules)) {
      (out[rule] ??= {})[file] = entry.count;
    }
  }
  return out;
}

/** Every `rule -> file -> count` triple as a flat, sorted list of strings a diff can show. */
function triples(indexed) {
  return Object.entries(indexed)
    .flatMap(([rule, files]) => Object.entries(files).map(([file, count]) => `${rule} ${file} ${count}`))
    .sort();
}

/** What the declared table says the totals are, for the line this prints on every run. */
function totals(indexed) {
  const perRule = Object.entries(indexed)
    .map(([rule, files]) => [rule, Object.values(files).reduce((a, b) => a + b, 0), Object.keys(files).length])
    .filter(([, count]) => count > 0)
    .sort();
  return {
    perRule,
    count: perRule.reduce((a, [, c]) => a + c, 0),
    files: new Set(triples(indexed).map((t) => t.split(" ")[1])).size,
  };
}

test("SH701: the lint baseline is exactly the declared list", async () => {
  const document = JSON.parse(await readFile(BASELINE, "utf8"));
  const found = totals(byRule(document));

  console.log(`SH701: ${found.count} suppressed violation(s) over ${found.files} file(s)`
    + found.perRule.map(([rule, count, files]) => `\n  ${count} ${rule} (${files} file(s))`).join(""));

  assert.deepEqual(
    triples(byRule(document)),
    triples(DECLARED),
    "SH699/SH701: eslint-suppressions.json is not the list declared in this file. A baseline is a "
    + "debt, and a debt that grows without a diff is a ceiling — `eslint --suppress-all` "
    + "regenerates from whatever is red today, so the command that recorded yesterday's breakage "
    + "will absorb tomorrow's in the same breath. That is exactly how SH699 nearly went unseen: it "
    + "was caught only because marketplace.list.page.tsx was OUTSIDE the baseline, while thirteen "
    + "instances of the identical defect sat inside it and green. If you paid one off, delete it "
    + "here; if you are adding one, say in the diff why it cannot be fixed instead.",
  );
});

/**
 * What SH701 and SH702 paid off, held by name so they cannot be re-suppressed.
 *
 * <p>Separate from the table above on purpose. That one fails when the baseline *drifts*, in either
 * direction, and reads as bookkeeping. This one says the thing worth saying: these four pages were
 * fixed, and a regeneration that puts them back has undone work rather than recorded state.</p>
 */
const PAID = [
  // SH701 — the mechanical four: a `useCallback` opening with `setLoading(true)`, called from a
  // mount effect.
  "src/app/admin/activity/activity.list.page.tsx",
  "src/app/admin/review/review.list.page.tsx",
  "src/app/content/trash.page.tsx",
  "src/app/content/static-file.list.page.tsx",
  // SH702 — the four dialogs, by a keyed remount rather than a better effect. Driven and asserted
  // against a running console by `shio-react/e2e/dialog-remount.check.mjs`: before it, a reopened
  // version history kept the previous read's *Restaurar versão publicada* button for the whole of
  // the next read.
  "src/components/permissions.dialog.tsx",
  "src/components/post-schedule.dialog.tsx",
  "src/components/post-versions.dialog.tsx",
  "src/components/widgets/content-browser-dialog.tsx",
  // SH703 — derived instead of set: the re-query spinner is a comparison, the selection and the
  // editor handshake carry the folder and the destination they belong to, and the two widgets tag
  // their resolved path with the id it is for. That last one closed a real race — a slow answer for
  // a file the curator had already replaced used to land on the new one.
  "src/app/content/object.children.page.tsx",
  "src/app/editor/visual-editor.page.tsx",
  "src/components/widgets/content-select.widget.tsx",
  "src/components/widgets/file.widget.tsx",
  // SH704 — the last one, and the only one with a test of its own: the module cache became a
  // subscription (`@/lib/discovery-store`), which is drivable under plain Node, so what it promises
  // is asserted in `shio-react/test/use-discovery.test.mjs` rather than by this file's count alone.
  "src/hooks/use-discovery.ts",
  // SH1136, slice one — the two SH1116 had just reworked. The history panel's spinner is derived
  // (the rows on screen belong to another object, or an older page is in flight), and the live
  // preview's save render is scheduled like its typing render, so neither effect raises a flag.
  "src/components/post-history.panel.tsx",
  "src/components/post-live-preview.tsx",
  // SH1136, slice three — the last five. The Languages board's spinner is derived from which
  // site|page the board answers; search holds its answer as one value and shows nothing when nothing
  // is asked; the tenant notice records which link was dismissed; the list state re-seeds its draft
  // during render; and the conflict dialog is SH702's keyed remount.
  "src/app/content/languages.page.tsx",
  "src/app/content/post-conflict.dialog.tsx",
  "src/app/content/search.page.tsx",
  "src/app/system/bento-tenant-link-notice.tsx",
  "src/hooks/use-list-state.ts",
];

test("SH701: every paid-off page stays paid off", async () => {
  const document = JSON.parse(await readFile(BASELINE, "utf8"));
  const suppressed = byRule(document)["react-hooks/set-state-in-effect"] ?? {};

  assert.deepEqual(
    PAID.filter((file) => file in suppressed), [],
    "SH701/SH702: a page already paid off is suppressed again. Two fixes live in this list — start "
    + "the fetch before touching state so the mount effect raises nothing (SH701), and mount a "
    + "dialog's body inside DialogContent keyed on its subject so closing unmounts it (SH702). "
    + "Suppressing one instead re-opens the debt, and the next page written against these copies "
    + "whichever shape it finds — which is how the baseline reached fifteen.",
  );
});

test("SH701: the reader is not vacuous — it reads the real file and a drifted one fails", async () => {
  const document = JSON.parse(await readFile(BASELINE, "utf8"));

  assert.ok(Object.keys(document).length > 0,
    "SH701: the baseline parsed to nothing. A reader that finds no suppressions agrees with every "
    + "declared list, including a wrong one.");

  // The rules above are only meaningful if they can fail. Feed them a document that drifted in each
  // of the three ways one can: a rule that came back, a file added, a count that grew.
  const grown = structuredClone(document);
  grown[PAID[0]] = { "react-hooks/set-state-in-effect": { count: 1 } };
  assert.notDeepEqual(triples(byRule(grown)), triples(DECLARED),
    "SH701: a file re-entering the baseline must not compare equal to the declared list");
  assert.notDeepEqual(
    PAID.filter((file) => file in (byRule(grown)["react-hooks/set-state-in-effect"] ?? {})), [],
    "SH701: a paid-off page re-suppressed must not read as still paid");

  const bumped = structuredClone(document);
  const [victim] = Object.keys(bumped);
  const [rule] = Object.keys(bumped[victim]);
  bumped[victim][rule].count += 1;
  assert.notDeepEqual(triples(byRule(bumped)), triples(DECLARED),
    "SH701: a count growing by one must not compare equal to the declared list");
});

/**
 * SH1046 — an excuse for a file that is not there any more.
 *
 * <p>SH701's table above compares the baseline against a declared list, which catches drift in
 * either direction and is the right gate for a debt. It cannot catch this, because the four
 * entries in question were <b>in the declared list too</b>: `components/ui/gradient-button.tsx`,
 * `components/ui/sidebar.tsx`, `contexts/breadcrumb.context.tsx` and `contexts/tenant.context.tsx`
 * were deleted in earlier rounds and their suppressions were not, so the list and the file agreed
 * about four files that did not exist and the gate stayed green for both.</p>
 *
 * <p>Harmless in itself; the reason it is worth a gate is what it means. This file is the ledger of
 * accepted findings, and nothing was reading it as a statement about the tree. `eslint
 * --prune-suppressions` would have removed them — but pruning is something somebody chooses to
 * run, and it also removes suppressions for rules that merely stopped firing, which is a different
 * fact and sometimes a regression's disguise. This asks the one question with no legitimate
 * answer.</p>
 */
test("SH1046: every suppressed path is a file that still exists", async () => {
  const document = JSON.parse(await readFile(BASELINE, "utf8"));
  const paths = Object.keys(document);
  assert.ok(paths.length >= 5,
    `SH1046: the baseline names ${paths.length} path(s), so this reader is looking at a shape it `
    + "no longer parses and would report every tree as clean");

  const gone = paths.filter((path) => !existsSync(new URL(`../../shio-react/${path}`, import.meta.url)));

  assert.deepEqual(gone, [],
    "SH1046: the baseline excuses findings in files that no longer exist. Run "
    + "`pnpm --filter shio-react exec eslint . --prune-suppressions`, commit the result, and take "
    + "the same lines out of DECLARED above — an accepted finding whose file is gone is a ledger "
    + "entry nobody is reading, which is how four of these survived two rounds of deletions.");

  // And the declared list is held to the same standard, since it is the half a person reads.
  const declaredGone = triples(DECLARED)
    .map((one) => one.split(" ")[1])
    .filter((path) => !existsSync(new URL(`../../shio-react/${path}`, import.meta.url)));
  assert.deepEqual(declaredGone, [],
    "SH1046: DECLARED names files that are not in the tree. The table above compares it to the "
    + "baseline, so two lists agreeing about a file neither of them checks is exactly the state "
    + "this found.");
});
