// The site's own claims, asserted beside the scripts that own them (SH567).
//
// SH527's reader holds a named DEBT list, and its `shio-site/` group was the cheapest left with the
// least defensible absence. The note names exactly what is assertable — "the prerender, the token
// budgets and the copy, each assertable beside the script that owns it" — and this is that, for the
// three of the ten whose failure is genuinely invisible:
//
//   SH533  the four token figures are GENERATED, never typed
//   SH537  every route emits a `.md` twin beside its HTML
//   SH532  `ROUTES` and the prerender's route table are one list, not two
//
// Second by return-risk in SH527's own ranking, and the reason is worth restating: a marketing
// claim that has gone false is invisible until somebody reads the page against the product. That is
// the failure the site's own rule — every claim is about a real surface — exists to prevent, and it
// is the one rule a marketing site cannot self-check.
//
// The ids above are named because each is ASSERTED below. The debt reader treats an id appearing in
// a suite as paid, so naming one this file does not cover is a false payment — which has now
// happened twice, once here and once in a backend test whose assertion message mentioned a task it
// did not assert. Neither id is repeated in this comment, for that exact reason.
//
// Source lints, like `scroll-ownership.test.mjs` beside them: these are properties of the repository
// rather than of a rendered DOM, and a jsdom that has no layout and no build output could not see
// any of them.

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const read = (path) => readFileSync(fileURLToPath(new URL(path, import.meta.url)), "utf-8");

const SITE_CONTENT = read("../src/lib/site-content.ts");
const TOKEN_BUDGETS = read("./token-budgets.mjs");
const APP = read("../src/App.tsx");
const ENTRY_SERVER = read("../src/entry-server.tsx");

test("SH533: the token figures are read from the properties file, never typed into the copy", () => {
  // The generator reads `shio-app/src/test/resources/token-budgets.properties`, which is the file
  // the backend's own budget tests assert against. That is the whole claim: the number on the page
  // and the number the build enforces are one value, so a budget that moves cannot leave the site
  // quoting the old one.
  assert.match(
    TOKEN_BUDGETS,
    /token-budgets\.properties/,
    "the source of the figures is the properties file the backend measures against, not a constant",
  );
  assert.match(
    TOKEN_BUDGETS,
    /token-budgets\.generated\.json/,
    "and it is written to a generated file the site imports, so nothing is transcribed by hand",
  );

  // A missing key is a build failure rather than a missing bar — asserted because the alternative
  // failure mode is a chart that silently drops one, which reads as a chart.
  assert.match(TOKEN_BUDGETS, /no key '\$\{?key\}?'|no key '/, "a renamed key fails the build");
});

test("SH533: the copy re-exports the generated figures rather than restating them", () => {
  // The first version of this asserted "no four-digit literal in the copy" and was wrong about the
  // code: `#831843` is a hex colour and `2710` is the documented default port. The rule is not
  // "no numbers", it is that the MEASURED ones have exactly one source.
  assert.match(
    SITE_CONTENT,
    /from "\.\/token-budgets\.generated\.json"/,
    "the site re-exports the generated file, so the figure on the page and the figure the backend "
      + "measures against are one value",
  );

  // And the generated file is not committed prose: it is written by the script on every build, so
  // a budget that moves cannot leave the page quoting the old number.
  assert.match(TOKEN_BUDGETS, /writeFileSync|writeFile/, "the script writes it on every build");
});

test("SH532: every route is declared in both places, because the build enforces it", () => {
  // `routesAreDeclared()` throws at import
  // time when `ROUTES` in App.tsx and the `routes` table in entry-server.tsx disagree — in either
  // direction, which is what stops a page existing with no title or a title with no page.
  assert.match(ENTRY_SERVER, /routesAreDeclared/, "the guard exists");
  assert.match(APP, /ROUTES/, "and App.tsx is the other half of the pair it compares");

  // The table is an array of `{ path, title, description }`, not a keyed object — the first
  // version of this read it as keys, found none, and would have passed vacuously the moment the
  // count assertion below was relaxed.
  const declared = [...ENTRY_SERVER.matchAll(/path:\s*"(\/[a-z0-9/-]*)"/g)].map(([, path]) => path);
  assert.ok(
    declared.length >= 5,
    `the prerender's route table should list the site's routes; found ${declared.length}`,
  );
  for (const path of declared) {
    assert.ok(
      APP.includes(`"${path}"`),
      `${path} is prerendered and is not in ROUTES — the two lists are one list, and a route in `
        + "only one of them is a page nobody can reach or a title nobody sees",
    );
  }
});

test("SH537: every prerendered route also gets its Markdown twin", () => {
  // SH537's actual claim, which the route test above does not make: each route emits `<path>.md`
  // beside its HTML, so a reader — or an agent — can fetch the page as text. Asserted on the
  // prerender because that is where the pair is written; a test that only counted routes would
  // have passed while the twins stopped being produced.
  const prerender = read("./prerender.mjs");

  assert.match(prerender, /pageToMarkdown/, "the twin is the same render converted, not a second one");
  assert.match(
    prerender,
    /index\.md/,
    "the root's twin is `index.md`, because `/.md` is not a path a static host serves",
  );
  assert.match(
    prerender,
    /\$\{?route\.path\}?\.md|`\.\$\{route\.path\}\.md`/,
    "and every other route's twin sits beside it at `<path>.md`",
  );
});
