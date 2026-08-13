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
import { readFile } from "node:fs/promises";
import { test } from "node:test";

const BASELINE = new URL("../../shio-react/eslint-suppressions.json", import.meta.url);

/**
 * The declared baseline: every suppressed violation, by file and rule.
 *
 * <h2>`set-state-in-effect` went 15 → 11, and the split is the finding</h2>
 *
 * <p>It held **15 over 13 files** when SH701 opened, and the four that came off were all one
 * construction: a `useCallback` opening with `setLoading(true)`, invoked straight from a mount
 * effect. That is the shape SH699 landed on `marketplace.list.page.tsx` — start the fetch before
 * touching state, let `loading` begin `true`, and keep the explicit raise on the control the
 * curator actually pressed. Mechanical, and the four are `activity.list`, `review.list`, `trash`
 * and `static-file.list`.</p>
 *
 * <p>The remaining eleven are <b>not</b> that shape, which is why they are still here rather than
 * fixed in the same pass. Each is a conditional `setState` inside an effect, and the correct fix
 * for every one is <b>derived state or a keyed remount</b> — a behaviour change in a dialog or in
 * one of the two largest pages in the console, none of which a suite in this repository opens. They
 * are grouped below by the fix they need, because that is what makes them work rather than a list:
 * SH702 holds the six dialogs, SH703 the four derived-state cases, SH704 the discovery hook.</p>
 *
 * <h2>What stays, and why the rest is not the same kind of debt</h2>
 *
 * <p>`react-refresh/only-export-components` is 14 files and is not a defect at all: every one is a
 * context or a `ui/` primitive that exports a hook or a variants object beside its component. The
 * rule is about hot-reload granularity in dev, the export is the API, and "fixing" it means
 * splitting 14 files to please a dev-server heuristic.</p>
 *
 * <p>The other three are single instances in two files, and each is a real smell that is cheaper to
 * leave visible here than to fix blind: they sit in the two largest pages in the console.</p>
 */
const DECLARED = {
  // SH703 — derived state. A re-query spinner and a selection reset, both driven by a value that
  // changes above them, so the fix is to compute rather than to set.
  // SH702 — dialogs syncing to an `open` prop; the fix is a keyed remount, not an effect.
  // SH704 — a module cache republished through an effect, one render late.
  "react-hooks/set-state-in-effect": {
    "src/app/console/content/object.children.page.tsx": 2,
    "src/app/console/universal-editor/visual-editor.page.tsx": 2,
    "src/components/permissions.dialog.tsx": 1,
    "src/components/post-schedule.dialog.tsx": 1,
    "src/components/post-versions.dialog.tsx": 1,
    "src/components/widgets/content-browser-dialog.tsx": 1,
    "src/components/widgets/content-select.widget.tsx": 1,
    "src/components/widgets/file.widget.tsx": 1,
    "src/hooks/use-discovery.ts": 1,
  },
  "@typescript-eslint/no-unused-expressions": {
    "src/app/console/content/object.children.page.tsx": 1,
  },
  "no-useless-assignment": {
    "src/app/console/content/post-type.edit.page.tsx": 1,
  },
  "react-hooks/static-components": {
    "src/app/console/content/static-file.list.page.tsx": 2,
  },
  "react-refresh/only-export-components": {
    "src/app/login/login-settings.tsx": 1,
    "src/app/routes/graphql.routes.tsx": 1,
    "src/components/theme-provider.tsx": 1,
    "src/components/ui/badge.tsx": 1,
    "src/components/ui/button.tsx": 1,
    "src/components/ui/form.tsx": 1,
    "src/components/ui/gradient-button.tsx": 1,
    "src/components/ui/sidebar.tsx": 1,
    "src/components/ui/sonner.tsx": 1,
    "src/contexts/ShioServiceContext.tsx": 1,
    "src/contexts/breadcrumb.context.tsx": 1,
    "src/contexts/review-pending.context.tsx": 1,
    "src/contexts/tenant.context.tsx": 1,
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
 * The four SH701 paid off, held by name so they cannot be re-suppressed.
 *
 * <p>Separate from the table above on purpose. That one fails when the baseline *drifts*, in either
 * direction, and reads as bookkeeping. This one says the thing worth saying: these four pages were
 * fixed, and a regeneration that puts them back has undone work rather than recorded state.</p>
 */
const PAID = [
  "src/app/console/admin/activity/activity.list.page.tsx",
  "src/app/console/admin/review/review.list.page.tsx",
  "src/app/console/content/trash.page.tsx",
  "src/app/console/content/static-file.list.page.tsx",
];

test("SH701: the four paid-off pages stay paid off", async () => {
  const document = JSON.parse(await readFile(BASELINE, "utf8"));
  const suppressed = byRule(document)["react-hooks/set-state-in-effect"] ?? {};

  assert.deepEqual(
    PAID.filter((file) => file in suppressed), [],
    "SH699/SH701: a page SH701 paid off is suppressed again. The fix these four carry is to start "
    + "the fetch before touching state and let `loading` begin `true`, so the mount effect raises "
    + "nothing, keeping the explicit `setLoading(true)` on the control the curator pressed. "
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
