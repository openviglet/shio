// The site's own claims, asserted beside the scripts that own them (SH567).
//
// SH527's reader holds a named DEBT list, and its `shio-site/` group was the cheapest left with the
// least defensible absence. The note names exactly what is assertable ("the prerender, the token
// budgets and the copy, each assertable beside the script that owns it"), and this is that, for the
// three of the ten whose failure is genuinely invisible:
//
//   SH533  the four token figures are GENERATED, never typed
//   SH537  every route emits a `.md` twin beside its HTML
//   SH532  `ROUTES` and the prerender's route table are one list, not two
//
// Second by return-risk in SH527's own ranking, and the reason is worth restating: a marketing
// claim that has gone false is invisible until somebody reads the page against the product. That is
// the failure the site's own rule (every claim is about a real surface) exists to prevent, and it
// is the one rule a marketing site cannot self-check.
//
// The ids above are named because each is ASSERTED below. The debt reader treats an id appearing in
// a suite as paid, so naming one this file does not cover is a false payment, which has now
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

  // A missing key is a build failure rather than a missing bar, asserted because the alternative
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
  // time when `ROUTES` in App.tsx and the `routes` table in entry-server.tsx disagree, in either
  // direction, which is what stops a page existing with no title or a title with no page.
  assert.match(ENTRY_SERVER, /routesAreDeclared/, "the guard exists");
  assert.match(APP, /ROUTES/, "and App.tsx is the other half of the pair it compares");

  // The table is an array of `{ path, title, description }`, not a keyed object; the first
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
      `${path} is prerendered and is not in ROUTES: the two lists are one list, and a route in `
        + "only one of them is a page nobody can reach or a title nobody sees",
    );
  }
});

/**
 * The positioning claim, which is the one this site got wrong for longer than any
 * factual one and could not see: every sentence on the page was true of the agent
 * path, and the composition of them said the product cannot be used without an
 * agent. Nothing here can check tone, but it can check that the structures the
 * correction rests on are still present, which is the difference between a fix and
 * a fix that survives the next edit.
 */
const listOf = (block, key) => [...block.matchAll(new RegExp(`${key}:\\s*"([^"]+)"`, "g"))].map(([, v]) => v);
const blockBetween = (source, from, to) =>
  source.slice(source.indexOf(from), to ? source.indexOf(to) : undefined);

test("the four ways in are declared, and the console is the first of them", () => {
  const paths = blockBetween(SITE_CONTENT, "export const PATHS", "export interface UseCase");
  const ids = listOf(paths, "id");

  assert.deepEqual(
    ids,
    ["console", "agent", "files", "api"],
    "four doors onto one content engine, console first: the ordering is the argument, "
      + "because a reader decides whether the product is for them in the first screenful",
  );

  // Each one states what it costs to start. A path with no prerequisite named is
  // how "you will also need the CLI" becomes something a reader discovers later.
  assert.equal(listOf(paths, "needs").length, 4, "every path names its honest prerequisite");
});

test("a use case that involves no agent at all is always on the page", () => {
  const cases = blockBetween(SITE_CONTENT, "export const USE_CASES", "/* ── The three roles");
  const tagged = [...cases.matchAll(/paths:\s*\[([^\]]+)\]/g)].map(([, list]) =>
    [...list.matchAll(/"([^"]+)"/g)].map(([, id]) => id),
  );

  assert.ok(tagged.length >= 4, `the page shows several deployment shapes; found ${tagged.length}`);
  assert.ok(
    tagged.some((paths) => !paths.includes("agent")),
    "at least one scenario has to be the team that never attaches an agent: it is the case "
      + "the old page argued its own reader out of, and the reason this file exists",
  );

  // Every tag resolves to a path. `use-cases.tsx` falls back to printing the raw id,
  // so a renamed path would ship a chip reading `api` rather than a wrong-looking one.
  const known = new Set(
    listOf(blockBetween(SITE_CONTENT, "export const PATHS", "export interface UseCase"), "id"),
  );
  for (const paths of tagged) {
    for (const id of paths) {
      assert.ok(known.has(id), `use case tagged with unknown path '${id}'`);
    }
  }
});

test("the console page names what the console cannot do", () => {
  // A page that only claimed the good half would be the same mistake pointed the
  // other way. The four command-line capabilities are listed on the page itself,
  // and the FAQ answer that says "four things" is counted against this list.
  const limits = blockBetween(SITE_CONTENT, "export const CONSOLE_LIMITS", "/** What actually changes");
  const titles = listOf(limits, "title");
  assert.equal(
    titles.length,
    4,
    `the console page promises four command-line capabilities; found ${titles.length}. `
      + "If that number moved, the objection copy on /compare says 'four things' and has to move with it",
  );

  const capabilities = blockBetween(
    SITE_CONTENT,
    "export const CONSOLE_CAPABILITIES",
    "export const CONSOLE_LIMITS",
  );
  assert.ok(
    listOf(capabilities, "title").length >= 5,
    "and the capability list is a walk of the console directory, not a slogan",
  );
});

test("SH1242: the two niche pages say the words a searcher types, in the title", () => {
  // A CMS an agent drives is the niche Shio ranks in unaided, and the titles used to name only the
  // mechanism ("the plugin, the MCP bridge"), which nobody searches for.
  const route = (path) => {
    const at = ENTRY_SERVER.indexOf(`path: "${path}"`);
    return ENTRY_SERVER.slice(at, ENTRY_SERVER.indexOf("},", at));
  };
  assert.match(route("/claude-code"), /title: "[^"]*CMS for Claude Code/,
    "SH1242: /claude-code's title says it is a CMS for Claude Code");
  assert.match(route("/claude-code"), /description:\s*"[^"]*agent-ready CMS[^"]*MCP/,
    "SH1242: and its description names the agent-ready CMS and MCP");

  const features = read("../src/lib/feature-content.ts");
  const gateway = blockBetween(features, "const AGENT_GATEWAY", "const CONTENT_AS_FILES");
  assert.match(gateway, /metaTitle: "[^"]*agent-ready CMS/,
    "SH1242: /features/agent-gateway's title names the agent-ready CMS");
  assert.match(gateway, /metaDescription:\s*"[^"]*MCP server/,
    "SH1242: and its description names the MCP server it sits behind");
});

test("SH1244: the home's title names the category, in both places it is written", () => {
  // The home ranked on the first page with no clicks: a searcher who has never heard of Shio
  // decides from the title, and "a complete CMS your agent can drive too" named no category.
  const title = "Viglet Shio: the open-source, agent-ready CMS";
  const home = ENTRY_SERVER.slice(ENTRY_SERVER.indexOf('path: "/"'));
  assert.ok(home.includes(`title: "${title}"`), "SH1244: the / route's title names the category");
  const template = read("../index.html");
  assert.ok(template.includes(`<title>${title}</title>`), "SH1244: and so does the Vite template");
  assert.ok(template.includes(`og:title" content="${title}"`), "SH1244: and its og:title");

  // The pill row and the share image said "headless", the word for delivery, where the site
  // means agent-ready.
  assert.match(read("../src/components/sections/hero.tsx"), /Agent-ready &middot;/,
    "SH1244: the hero's pill row says Agent-ready");
  assert.match(read("../public/og.svg"), /agent-ready &#183;/,
    "SH1244: and so does the share image");
});

test("SH1243: Google Analytics loads only after consent, as on www.viglet.org", () => {
  // The template is what every prerendered page starts from, so a loader in its head runs
  // before anybody is asked.
  assert.doesNotMatch(read("../index.html"), /googletagmanager|gtag\(/,
    "SH1243: index.html loads no analytics; analytics.ts injects gtag after a granted choice");

  const analytics = read("../src/lib/analytics.ts");
  const loader = analytics.slice(analytics.indexOf("export function loadAnalytics"));
  assert.match(loader, /googletagmanager\.com\/gtag\/js/, "SH1243: the one loader is loadAnalytics");
  assert.match(analytics, /if \(consent === "granted"\) loadAnalytics\(\)/,
    "SH1243: a choice loads GA only when it is granted");
  assert.match(analytics, /if \(getConsent\(\) === "granted"\) loadAnalytics\(\)/,
    "SH1243: and on a later visit only a stored grant loads it");
  assert.doesNotMatch(analytics, /gtag\("consent"/,
    "SH1243: no consent-mode pings: a visitor who declines stays uncounted (W42)");

  const banner = read("../src/components/consent-banner.tsx");
  assert.match(banner, /choose\("denied"\)/, "SH1243: the banner offers Decline as well as Accept");
  assert.match(read("../src/App.tsx"), /<ConsentBanner \/>/, "SH1243: and every page mounts it");
  assert.match(read("../src/components/sections/site-footer.tsx"), /reopenConsent/,
    "SH1243: the footer can reopen it to change the choice");
});

test("SH537: every prerendered route also gets its Markdown twin", () => {
  // SH537's actual claim, which the route test above does not make: each route emits `<path>.md`
  // beside its HTML, so a reader (or an agent) can fetch the page as text. Asserted on the
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

test("SH1258: the showcase's pages and features are read from the SH1250 matrix, never retyped", () => {
  // The matrix is what `cli/test/showcase-matrix.test.mjs` holds to the product, so a page this
  // site lists is one the package carries. A copy typed here would be a second list with nothing
  // failing when the two part.
  const showcase = read("../src/lib/showcase.ts");
  assert.match(showcase, /from "\.\.\/\.\.\/\.\.\/docs\/specs\/SH1250-showcase-matrix\.json"/,
    "SH1258: showcase.ts imports the matrix itself");
  assert.doesNotMatch(showcase, /"\/tour\/[a-z]/,
    "SH1258: and names no tour page by hand: every path comes from the matrix");

  // Both install paths are on the page, and the CLI one spells the package id from the matrix.
  const install = blockBetween(SITE_CONTENT, "/* ── The showcase", "/* ── Replication");
  assert.match(install, /SHOWCASE_CONSOLE_STEPS/, "SH1258: the console path is on the page");
  assert.match(install, /shio marketplace install \$\{SHOWCASE_ID\} --apply/,
    "SH1258: and the CLI path installs and applies the id the matrix names");

  // Every depth page links to the showcase page that demonstrates it, by feature key; the
  // lookup throws at build time on a key the matrix does not map.
  const features = read("../src/lib/feature-content.ts");
  const slugs = [...features.matchAll(/^ {2}slug: "([^"]+)",$/gm)].length;
  const keys = [...features.matchAll(/^ {2}showcase: "([^"]+)",$/gm)].length;
  assert.equal(keys, slugs, "SH1258: every depth page names the matrix feature its showcase link reads");
  assert.ok(APP.includes('"/showcase"'), "SH1258: /showcase is a route");
});
