/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH380 — a captured stylesheet becomes tokens plus CSS.
 *
 * The properties worth pinning here are all of one shape: **the ways this can quietly write something
 * wrong**. A token whose name does not compile back to the property the source's CSS references
 * un-styles every rule that uses it; a value lifted out of a dark-mode block picks a theme nobody
 * asked for; a `url()` resolved against the page instead of against the stylesheet names a file that
 * does not exist. None of those fails loudly, and each is one line of code away.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {

// @scans both reason: token names are extracted from Java source, and the javadoc naming them
// is deliberately part of what is read (SH740).
  buildTheme,
  contrastRatio,
  customPropertyFor,
  declaredTokens,
  describeTheme,
  dtcgFor,
  flatten,
  pageStatement,
  parseColor,
  pathForCustomProperty,
  recurringTokens,
  rewriteUrls,
  scanCss,
} from "../src/theme.mjs";
import { VOCABULARY_THEME_TOKENS, vocabularyTokensFrom } from "../src/vocabulary-theme.mjs";

const declarationsOf = (css) => scanCss(css).declarations;

// --- the scanner ---

test("a declaration is split on its first colon, not its last", () => {
  // `background: url(http://x/y.png)` has three colons. Splitting on the last yields a property
  // called `background: url(http`, which every group test below silently ignores — so the site's
  // background quietly stops being part of the theme.
  const [row] = declarationsOf("a{background:url(http://x.test/y.png)}");

  assert.equal(row.property, "background");
  assert.equal(row.value, "url(http://x.test/y.png)");
});

test("a brace inside a quoted value does not end the rule", () => {
  const rows = declarationsOf('a{content:"}";color:#0b5533}');

  assert.deepEqual(rows.map((row) => row.property), ["content", "color"]);
});

test("a comment cannot hide a declaration, and an unterminated one ends at the file", () => {
  assert.deepEqual(declarationsOf("a{/*color:red;*/color:#0b5533}").map((row) => row.value), ["#0b5533"]);
  assert.deepEqual(declarationsOf("a{color:#0b5533}/* trailing").map((row) => row.value), ["#0b5533"]);
});

test("a declaration after a media query belongs to its own rule, not to the query's last one", () => {
  // The defect this exists for: without restoring the enclosing selector on `}`, everything after a
  // media block is attributed to the rule inside it — which would put a dark-mode override's custom
  // properties on `:root` and make them look like the site's tokens.
  const rows = declarationsOf(":root{--a:#111111}@media(prefers-color-scheme:dark){:root{--a:#eeeeee}}h1{color:#222222}");

  assert.equal(rows.at(-1).selector, "h1");
  assert.equal(rows.find((row) => row.value === "#eeeeee").atRule, "@media(prefers-color-scheme:dark)");
});

test("a custom property keeps its case, and an ordinary property does not", () => {
  // The defect a smoke run against a real stylesheet found, and it defeats this module's only hard
  // rule: `--brandPrimary` lowercased is `--brandprimary`, which *does* survive the round trip — so
  // the token gets written under a name the source's CSS never references. CSS folds `COLOR` and
  // treats `--Foo` and `--foo` as two properties, so the scanner has to as well.
  const rows = declarationsOf(":root{--brandPrimary:#0b5533;--brandprimary:#111111}h1{COLOR:red}");

  assert.deepEqual(rows.map((row) => row.property), ["--brandPrimary", "--brandprimary", "color"]);
  const { tokens } = declaredTokens(rows);
  assert.deepEqual(tokens.map((row) => row.name), ["--brandPrimary", "--brandprimary"]);
  assert.deepEqual(tokens.map((row) => row.path), ["brandPrimary", "brandprimary"]);
});

test("a pathological value does not hang the scan", () => {
  // SH377's rule, one module along: this is the second reader of a stranger's bytes, and a quantifier
  // pair over one digit class turns a 20k-digit value into a quadratic walk. The assertion is the
  // timeout — if this regresses the test does not fail, it stops.
  const css = `a{width:${"9".repeat(20000)}x;padding:${"9".repeat(20000)}x}`;

  const { declarations } = scanCss(css);
  assert.equal(declarations.length, 2);
  assert.deepEqual(recurringTokens(declarations, { minRefs: 2 }).tokens, []);
});

// --- the round trip, which is the only hard rule ---

test("the DTCG path is the inverse of the mapping, so the captured CSS keeps working", () => {
  // `var(--brand-primary)` is in the source's own rules. A token that compiled to any other name
  // would leave every one of them unstyled — silently, and only in the replica.
  assert.equal(pathForCustomProperty("--brand-primary"), "brand.primary");
  assert.equal(customPropertyFor("brand.primary"), "--brand-primary");
});

test("a name that does not survive the round trip is refused rather than renamed", () => {
  for (const name of ["--Brand_Primary", "--a--b", "--", "-x", "--x y"]) {
    assert.equal(pathForCustomProperty(name), null, name);
  }
});

test("the mapping mirrors ShThemeTokens, and the Java is what says so", async () => {
  // Mirroring across two languages is a debt, and the alternative is worse: this module would be
  // writing a name whose runtime spelling it cannot predict. So the debt is instrumented — a change
  // to either side fails here rather than in somebody's :root block.
  const java = await readFile(
    fileURLToPath(new URL("../../shio-app/src/main/java/com/viglet/shio/render/ShThemeTokens.java", import.meta.url)),
    "utf8",
  );

  assert.match(
    java,
    /replaceAll\("\[\^A-Za-z0-9-\]\+", "-"\)/,
    "ShThemeTokens.customProperty changed — customPropertyFor in cli/src/theme.mjs must change with it",
  );
});

// --- declared tokens ---

test("a declared custom property is the editable half, with its source name kept", () => {
  const { tokens } = declaredTokens(declarationsOf(":root{--brand-primary:#0b5533;--space-4:16px}"));

  assert.deepEqual(tokens, [
    { path: "brand.primary", name: "--brand-primary", value: "#0b5533", type: "color", source: "declared" },
    { path: "space.4", name: "--space-4", value: "16px", type: "dimension", source: "declared" },
  ]);
});

test("a property with a light and a dark value is reported, never written", () => {
  // Writing either one picks a theme. This is the case that would make a captured site arrive in
  // whichever palette the stylesheet happened to declare second.
  const { tokens, unsure } = declaredTokens(declarationsOf(
    ":root{--bg:#ffffff}@media(prefers-color-scheme:dark){:root{--bg:#000000}}",
  ));

  assert.deepEqual(tokens, []);
  assert.equal(unsure[0].reason, "variant");
  assert.match(unsure[0].detail, /also declared under @media/);
});

test("a palette declared only under a class is reported, so silence never reads as no tokens", () => {
  const { tokens, unsure } = declaredTokens(declarationsOf(".dark{--bg:#000000}"));

  assert.deepEqual(tokens, []);
  assert.equal(unsure[0].reason, "variant-only");
});

test("the same property declared twice under :root with two values is a cascade question, so it is refused", () => {
  const { tokens, unsure } = declaredTokens(declarationsOf(":root{--bg:#ffffff}:root{--bg:#eeeeee}"));

  assert.deepEqual(tokens, []);
  assert.equal(unsure[0].reason, "conflict");
});

test("the same value declared twice is not a conflict", () => {
  const { tokens, unsure } = declaredTokens(declarationsOf(":root{--bg:#ffffff}html{--bg:#ffffff}"));

  assert.equal(tokens.length, 1);
  assert.deepEqual(unsure, []);
});

// --- recurring tokens ---

const PALETTE = `
  h1{color:#0b5533;font-size:32px}
  h2{color:#0b5533;font-size:32px}
  h3{color:#0b5533;font-size:32px}
  p{color:#333333;padding:16px}
  .a{padding:16px}.b{padding:16px}
  .c{background-color:#0b5533}
`;

test("a literal past the threshold is a palette value, named by group, role and rank", () => {
  const { tokens } = recurringTokens(declarationsOf(PALETTE), { minRefs: 3 });
  const paths = tokens.map((row) => row.path);

  assert.ok(paths.includes("color.text-1"), paths.join(" "));
  assert.ok(paths.includes("font.size.1"));
  assert.ok(paths.includes("spacing.1"));
  // #333333 appears once and is not a palette; the background is its own role, not the third text.
  assert.ok(!paths.includes("color.text-2"));
});

test("a rank compiles to the same custom property whichever way it joins its stem", () => {
  // Which is what makes the nesting decision free: the DTCG file gets to be coherent and no runtime
  // name moves. If this ever stops holding, the tidier shape is the one to give up.
  assert.equal(customPropertyFor("spacing.1"), "--spacing-1");
  assert.equal(customPropertyFor("color.text-1"), "--color-text-1");
  assert.equal(customPropertyFor("font.size.1"), "--font-size-1");
});

test("every recurring token carries the count that argued for it", () => {
  const { tokens } = recurringTokens(declarationsOf(PALETTE), { minRefs: 3 });
  const accent = tokens.find((row) => row.path === "color.text-1");

  assert.equal(accent.value, "#0b5533");
  assert.equal(accent.refs, 3);
  assert.deepEqual(accent.properties, ["color"]);
});

test("the threshold is what stops every colour on the site becoming a token", () => {
  const low = recurringTokens(declarationsOf(PALETTE), { minRefs: 2 }).tokens.length;
  const high = recurringTokens(declarationsOf(PALETTE), { minRefs: 4 }).tokens.length;

  assert.ok(low > high, `${low} at 2 refs, ${high} at 4`);
});

test("ranks break ties on the value, so two runs agree about which colour is which", () => {
  const css = ".a{color:#111111}.b{color:#111111}.c{color:#111111}.d{color:#222222}.e{color:#222222}.f{color:#222222}";
  const once = recurringTokens(declarationsOf(css), { minRefs: 3 }).tokens.map((row) => `${row.path}=${row.value}`);
  const again = recurringTokens(declarationsOf(`${css} `), { minRefs: 3 }).tokens.map((row) => `${row.path}=${row.value}`);

  assert.deepEqual(once, again);
  assert.deepEqual(once, ["color.text-1=#111111", "color.text-2=#222222"]);
});

test("a shorthand is not a length, so 8px 16px becomes no token at all", () => {
  // Two values in one declaration are two tokens or none, and this reader will not split them: which
  // of the four sides each belongs to is a question a scanner cannot answer.
  const { tokens } = recurringTokens(declarationsOf(".a{padding:8px 16px}.b{padding:8px 16px}.c{padding:8px 16px}"), { minRefs: 3 });

  assert.deepEqual(tokens, []);
});

// --- the DTCG tree ---

test("the tree is the TOKENS field, sorted, and it flattens back to the paths it came from", () => {
  const { tokens } = dtcgFor([
    { path: "color.text-1", value: "#0b5533", type: "color" },
    { path: "brand.primary", value: "#0b5533", type: "color" },
  ]);

  assert.deepEqual(Object.keys(tokens), ["brand", "color"]);
  assert.deepEqual(tokens.brand.primary, { $value: "#0b5533", $type: "color" });
  assert.deepEqual(Object.keys(flatten(tokens)).sort(), ["brand.primary", "color.text-1"]);
});

test("SH428: a name that is both a token and a group keeps both, the second one flat", () => {
  // Over openviglet.github.io's palette this wrote nine of sixteen declared tokens and dropped seven:
  // bg.alt, border.strong, brand.2, brand.3, brand.ink, brand.wash, brand.font. Detecting the
  // collision was right — DTCG cannot represent both — but creating it was the defect, and the round
  // trip (the only hard rule here) is satisfied by the flat name: --bg-alt is what the source wrote.
  const { tokens, dropped, rows } = dtcgFor([
    { path: "color", value: "#0b5533", type: "color", source: "declared" },
    { path: "color.text", value: "#333333", type: "color", source: "declared" },
  ]);

  assert.deepEqual(tokens.color, { $value: "#0b5533", $type: "color" });
  assert.deepEqual(tokens["color-text"], { $value: "#333333", $type: "color" },
    "the flat name, which is the one the stylesheet's own var() references");
  assert.deepEqual(dropped, [], "nothing is lost: --x plus --x-y is the commonest scheme in CSS");
  assert.equal(customPropertyFor("color-text"), "--color-text",
    "and it compiles back to the property the source declared, which is what keeps the CSS working");
  assert.deepEqual(
    rows.map((row) => row.path),
    ["color", "color-text"],
    "the rows report the path the file actually holds, so a count taken from them cannot contradict it",
  );
  assert.equal(rows[1].nested, "color.text", "with the path it would have had, for the record");
});

test("SH428: the order of the two does not decide which one survives", () => {
  // Rows arrive in declaration order, so the collision can be met from either side. Taking the
  // shortest path first is what keeps a flat name free at the moment it is needed — in the other
  // order `color` would find a group already there and have nowhere to go.
  const { tokens, dropped } = dtcgFor([
    { path: "color.text", value: "#333333", type: "color" },
    { path: "color", value: "#0b5533", type: "color" },
  ]);

  assert.deepEqual(tokens.color, { $value: "#0b5533", $type: "color" });
  assert.deepEqual(tokens["color-text"], { $value: "#333333", $type: "color" });
  assert.deepEqual(dropped, []);
});

test("the collision guard is still there for the case the fallback cannot answer", () => {
  // Two names cannot both take one flat slot. This shape does not arise from one stylesheet's custom
  // properties — the flat name IS the source's own name, so it would mean two identical declarations
  // — which is why it is asserted here rather than left to a capture: a guard nobody can reach is
  // still the thing that stops a token being written over.
  const { tokens, dropped } = dtcgFor([
    { path: "color", value: "#0b5533", type: "color" },
    { path: "color-text", value: "#111111", type: "color" },
    { path: "color.text", value: "#333333", type: "color" },
  ]);

  assert.deepEqual(tokens["color-text"], { $value: "#111111", $type: "color" },
    "first writer keeps the slot; the loser is named rather than overwriting it");
  assert.equal(dropped.length, 1);
  assert.equal(dropped[0].name, "color.text");
  assert.equal(dropped[0].reason, "collision");
});

test("a value this reader will not type gets no $type rather than a guessed one", () => {
  const { tokens } = dtcgFor([{ path: "font.family-1", value: "Inter, sans-serif", type: null }]);

  assert.deepEqual(tokens.font["family-1"], { $value: "Inter, sans-serif" });
});

// --- url() ---

test("a url() resolves against the stylesheet, not against the page that linked it", () => {
  // `url(../fonts/x.woff2)` in /assets/css/site.css is /assets/fonts/x.woff2. Resolved against the
  // page it would be /fonts/x.woff2, which names a file the download never wrote.
  const assets = new Map([["/assets/fonts/x.woff2", "Acme/assets/fonts/x.woff2"]]);
  const out = rewriteUrls("@font-face{src:url(../fonts/x.woff2)}", {
    base: "https://x.test/assets/css/site.css",
    assets,
  });

  // SH461: what is written is the SOURCE's path, not the map's value. The tree path
  // (`/Acme/assets/fonts/x.woff2`) is addressable in shio/assets/ and nowhere a browser can ask
  // for; the instance resolves a site-relative path, which is what the source used and what the
  // markup — never rewritten here — has been serving correctly since SH437.
  assert.match(out.css, /url\("\/assets\/fonts\/x\.woff2"\)/);
  assert.ok(!out.css.includes("Acme/"), "the site segment is the tree's, and a URL is not the tree");
  assert.equal(out.rewritten, 1);
  assert.deepEqual(out.unresolved, []);
});

test("SH461: the asset map is still the gate — an uncaptured reference is not pointed at us", () => {
  // The map's value stopped being written; its PRESENCE is what still decides. A url() nothing
  // downloaded must keep pointing where it pointed, or a named licensing gap (SH378) becomes a
  // broken replica that claims to serve bytes it never fetched.
  const out = rewriteUrls("a{background:url(/img/never-fetched.png)}", {
    base: "https://x.test/css/site.css",
    assets: new Map(),
  });

  assert.match(out.css, /url\(\/img\/never-fetched\.png\)/);
  assert.equal(out.rewritten, 0);
  assert.deepEqual(out.unresolved, ["/img/never-fetched.png"]);
});

test("a reference the asset map does not hold is left alone and named", () => {
  const out = rewriteUrls("a{background:url(https://cdn.test/bg.png)}", {
    base: "https://x.test/css/site.css",
    assets: new Map(),
  });

  // Rewriting it to a path with no bytes behind it would turn a named licensing gap into a broken
  // replica — SH378's rule, one file along.
  assert.match(out.css, /url\(https:\/\/cdn\.test\/bg\.png\)/);
  assert.deepEqual(out.unresolved, ["https://cdn.test/bg.png"]);
});

test("SH452: an unresolved reference is reported as broken or as still-working, never as both", () => {
  // The note used to say, of everything unresolved: "left as they are — the theme still fetches
  // them from the source's origin". True of an absolute URL, false of a root-relative one, which
  // resolves against the REPLICA's origin where nothing serves it. Measured: two webfonts, 200 at
  // the source and 404 at the replica, while this note told the reader they still worked — SH443's
  // defect one surface along, a message asserting an outcome nobody measured.
  const theme = buildTheme({
    styles: [{
      path: "/css/site.css",
      file: "Acme/css/site.css",
      url: "https://x.test/css/site.css",
      css: "@font-face{src:url(/fonts/inter.woff2)}"
        + "a{background:url(../img/bg.jpg)}"
        + "b{background:url(https://cdn.other.test/x.png)}",
    }],
    assets: new Map(),
  });

  const broken = theme.notes.find((note) => /404/.test(note));
  assert.ok(broken, `the root-relative pair is reported as broken. Notes: ${theme.notes.join(" | ")}`);
  assert.match(broken, /2 url\(\) reference\(s\)/);
  assert.match(broken, /shio clone <url> --assets/, "P6: the note carries the command that fixes it");
  assert.ok(!/cdn\.other\.test/.test(broken), "the absolute one is not in this list");

  const kept = theme.notes.find((note) => /still fetches/.test(note));
  assert.match(kept, /1 url\(\) reference\(s\)/);
  assert.match(kept, /cdn\.other\.test/,
    "and only the one that really does keep working is described as keeping working");
});

test("a data: URI is already portable, so it is not looked up and not reported", () => {
  const out = rewriteUrls('a{background:url("data:image/gif;base64,R0lGOD")}', {
    base: "https://x.test/css/site.css",
    assets: new Map(),
  });

  assert.deepEqual(out.unresolved, []);
  assert.equal(out.rewritten, 0);
});

// --- the whole theme ---

const SHEET = `
  :root{--brand-primary:#0b5533;--space-4:16px}
  body{font-family:Inter,sans-serif;background:url(/img/bg.png)}
  h1{color:#0b5533;font-size:32px}
  h2{color:#0b5533;font-size:32px}
  h3{color:#0b5533;font-size:32px}
`;

function themeFor(css = SHEET) {
  return buildTheme({
    styles: [{ path: "/css/site.css", file: "Acme/css/site.css", url: "https://x.test/css/site.css", css }],
    assets: new Map([["/img/bg.png", "Acme/img/bg.png"], ["/css/site.css", "Acme/css/site.css"]]),
  });
}

test("SH424: a lifted declaration leaves the kept CSS, or the token is inert", () => {
  // Measured on a real replica: nine declared tokens in Theme.TOKENS, and the rendered page carried
  // TWO :root blocks — the token-compiled one at char 1794 and the source's own at 2735, still inside
  // the CSS field SH380 keeps verbatim. Equal specificity, later wins, so all nine were dead and a
  // curator editing `brand` saw no change. Both halves were individually right, which is why nothing
  // caught it; this is the assertion at the seam.
  const theme = themeFor(`:root {
      color-scheme: light;
      --brand: #35d07f;
      --space-4: 16px;
    }
    .btn { background: var(--brand); padding: var(--space-4) }`);

  assert.ok(!/--brand\s*:/.test(theme.css), "the declaration is gone from the CSS the theme keeps");
  assert.ok(!/--space-4\s*:/.test(theme.css));
  assert.deepEqual(theme.tokens.brand, { $value: "#35d07f", $type: "color" },
    "and the token holds it, byte-identically, so the computed value is unchanged");
  assert.match(theme.css, /color-scheme\s*:\s*light/,
    "only the lifted declarations, never the block: color-scheme is no custom property and must stay");
  assert.match(theme.css, /var\(--brand\)/, "and a reference is a reference, not a declaration");
  assert.ok(
    theme.notes.some((note) => /lifted declaration\(s\) were removed from the kept CSS/.test(note)),
    "said out loud, because the artefact's editability claim is what this task is about",
  );
});

test("SH424: a recurring token's literal is left exactly where it is", () => {
  // The line SH380 drew and this does not cross: nothing references a recurring token yet, so
  // search-and-replacing #0b5533 across a stranger's stylesheet would change what the cascade
  // computes — the failure no digest catches. A lifted declaration is the opposite: the compiled
  // :root re-emits it byte for byte.
  const theme = themeFor();

  assert.ok(theme.rows.some((row) => row.source === "recurring" && row.value === "#0b5533"));
  assert.match(theme.css, /color:#0b5533/, "the literal stays in the CSS, untouched");
  assert.ok(!/--brand-primary\s*:/.test(theme.css), "while the declared one is lifted out");
});

test("SH424: a token whose value carries a url() keeps its declaration, and is named", () => {
  // The kept CSS has been rewritten to the tree path and the token has not, so dropping this one
  // would send the replica back to the source's origin for those bytes. It stays — and it stays
  // named, because a token that cannot be edited is exactly the claim this task exists to stop
  // making silently.
  const theme = themeFor(`:root { --hero: url(/img/bg.png); --brand: #35d07f }
    .hero { background-image: var(--hero) }`);

  assert.match(theme.css, /--hero\s*:/, "still declared, so the rewritten path is what renders");
  assert.ok(!/--brand\s*:/.test(theme.css), "and the one beside it is still lifted");
  assert.ok(
    theme.notes.some((note) => /still set by the kept CSS and editing them changes nothing/.test(note)
      && /--hero/.test(note)),
    "the exception is reported per property, not left for a curator to discover by editing",
  );
});

test("SH424: nothing the theme keeps re-declares a token the theme writes", () => {
  // The property that makes the tokens live, stated as one claim over the whole artefact rather than
  // per fixture. `ShThemeTokens` PREPENDS the compiled :root — correct when one author wrote both
  // halves — so "the kept CSS declares none of them" is exactly the condition that makes an edit
  // reach the page.
  const theme = themeFor(`:root { --a: #111; --b: 8px; --c: #222; color-scheme: dark }
    .x { color: var(--a); gap: var(--b); border-color: var(--c) }`);

  for (const row of theme.rows.filter((each) => each.source === "declared")) {
    const property = customPropertyFor(row.path);
    assert.ok(
      // `\s` and not `\\s`: inside a template literal the single backslash is the STRING's, so
      // this read `${property}s*:` — matching `--a:` by luck, because `s*` accepts nothing, and
      // missing `--a : #111` entirely. A negative assertion passing for the wrong reason (SH782).
      !new RegExp(`${property}\\s*:`).test(theme.css),
      `${property} is a token AND is declared by the kept CSS, which is the SH424 defect`,
    );
  }
});

test("SH428: the accent ramp survives, and the note counts what the file holds", () => {
  // The two halves of SH428 in the shape they were measured in. `--brand` beside `--brand-2` and
  // `--brand-3` is the accent ramp — the hero gradient and the primary button — and losing them is
  // SH210's hazard: a curator replacing "the brand colour" replaces a quarter of it. The note said
  // "16 declared token(s) are the editable half" while its own list showed nine written and seven not,
  // because the count was taken before the drop.
  const theme = themeFor(`:root {
      --brand: #35d07f;
      --brand-2: #2bb96f;
      --brand-3: #1f9c5b;
      --brand-ink: #06281a;
      --bg: #0b0f14;
      --bg-alt: #111820;
    }
    .hero { background: linear-gradient(var(--brand), var(--brand-2)) }
    .btn { background: var(--brand-3); color: var(--brand-ink) }
    body { background: var(--bg) }
    .panel { background: var(--bg-alt) }`);

  const declared = theme.rows.filter((row) => row.source === "declared");
  assert.equal(declared.length, 6, "all six, where the nested reading kept two and dropped four");
  assert.deepEqual(
    declared.map((row) => row.path).sort(),
    ["bg", "bg-alt", "brand", "brand-2", "brand-3", "brand-ink"],
    "the four that collided took their flat names, which are the ones the CSS var()s",
  );
  assert.ok(!theme.unsure.some((row) => row.reason === "collision"));

  const note = theme.notes.find((each) => /declared token\(s\) are the editable half/.test(each));
  assert.match(note, /^6 declared token\(s\)/,
    "the number a reader can check against the rows beneath it comes from those rows");
});

test("the theme carries both kinds of token, the CSS it kept, and which file each rule came from", () => {
  const theme = themeFor();

  assert.equal(theme.rows.filter((row) => row.source === "declared").length, 2);
  assert.ok(theme.rows.some((row) => row.source === "recurring" && row.value === "#0b5533"));
  assert.match(theme.css, /\/\* \/css\/site\.css \*\//);
  assert.deepEqual(theme.sheets.map((sheet) => sheet.file), ["Acme/css/site.css"]);
  assert.equal(theme.urls.rewritten, 1);
});

test("the CSS is kept verbatim apart from url(), because substitution is the hazard not the feature", () => {
  const theme = themeFor();

  // The literal is still there. Replacing it with var(--color-text-1) across a stranger's stylesheet
  // is a search-and-replace that breaks a replica in a way no digest catches — twenty-three hits, one
  // of them inside a content: string.
  assert.match(theme.css, /color:#0b5533/);
  assert.ok(!theme.css.includes("var(--color-text-1)"));
});

test("the report says the two kinds are not equally powerful, which is the only way it could mislead", () => {
  const text = describeTheme(themeFor());

  assert.match(text, /declared token\(s\) are the editable half/);
  assert.match(text, /change nothing on their own/);
});

test("a stylesheet with nothing to lift is a theme with no tokens, not a failure", () => {
  const theme = themeFor("a{display:block}");

  assert.deepEqual(theme.rows, []);
  assert.deepEqual(theme.tokens, {});
  assert.match(theme.css, /display:block/);
});

test("two runs over one stylesheet produce identical bytes", () => {
  assert.equal(JSON.stringify(themeFor()), JSON.stringify(themeFor()));
});

// --- SH831: the bridge from the captured palette to the theme the replica renders with ---

/**
 * A source shaped like the two the pipeline actually meets: its own custom properties, and enough
 * repeated literals for the recurring reader to name a role. `--brand` is read by four `color:`
 * declarations and `--ink` by three — one of which is `body`, which is the whole point.
 */
const PALETTE_SHEET = `
  :root{--ink:#10243a;--wash:#eef3f8;--brand:#0b7285}
  body{color:var(--ink);background:var(--wash)}
  .navlink{color:var(--brand)}
  .card h3{color:var(--brand)}
  .card::before{color:var(--brand)}
  .hero .cta::after{color:var(--brand)}
  .a{border-color:#d9e2ec}
  .b{border-color:#d9e2ec}
  .c{border-color:#d9e2ec}
  .d{fill:#ff6600}
  .e{fill:#ff6600}
  .f{fill:#ff6600}
`;

test("SH831: a declared colour carries the role the source uses it in, not the name it was given", () => {
  const rows = themeFor(PALETTE_SHEET).rows;
  const row = (path) => rows.find((each) => each.path === path);

  assert.equal(row("ink").role, "text", "`body { color: var(--ink) }` is a stylesheet saying what "
    + "this site's text looks like; the name `ink` is the source's word and could have been anything");
  assert.equal(row("wash").role, "background");
  assert.equal(row("brand").role, "text",
    "read by four `color:` rules, so `text` is the honest role - which is exactly why the count "
      + "alone must not decide the rank");
  assert.equal(row("ink").page, true);
  assert.equal(row("brand").page, false,
    "a nav link and two ::before marks are not the page, whatever the count");
});

test("SH831: the page's own colour wins the slot, and the accent read more often does not take it", () => {
  const { mapped, unmapped, tokens } = vocabularyTokensFrom(themeFor(PALETTE_SHEET).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // The measurement this rule exists for: ranking `text` by reference count picks #0b7285 - the
  // accent - and paints the replica's body copy teal, which is a worse artefact than the generic
  // default it replaced. A colour the source states at page level is the only one taken.
  assert.equal(from["color.ink"], "#10243a", "the body's own colour, not the one four rules reach for");
  assert.equal(from["color.surface"], "#eef3f8");
  assert.equal(tokens.color.ink.$value, "#10243a");
  assert.equal(tokens.color["ink-muted"].$value, VOCABULARY_THEME_TOKENS.color["ink-muted"].$value,
    "and the accent did not slide into the next text slot either: `--brand` is excluded from the "
      + "declared half outright, not merely outranked, so a slot with nothing to fill it keeps "
      + "this file's default");
  assert.ok(
    unmapped.some((row) => row.name === "color.ink-muted" && row.role === "text#2"),
    "the second text colour is reported by the role it looked for, so a reader can tell a default "
      + "that was chosen from one that was never offered a value (P9)",
  );
});

test("SH831: a recurring literal fills a slot the source declares no variable for", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(PALETTE_SHEET).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // `border-color` and `fill` three times each: the extractor already ranks these by role, which is
  // its published contract, so they are taken at their own rank with no second inference.
  assert.equal(from["color.line"], "#d9e2ec");
  assert.equal(from["color.accent"], "#ff6600",
    "the source's accent, which is the single line the whole task is about: the replica stopped "
      + "carrying this theme's dark green beside a page painted orange");
});

test("SH831: a source with no palette at all changes nothing, and says so for every colour", () => {
  const { tokens, mapped, unmapped } = vocabularyTokensFrom(themeFor("a{display:block}").rows);

  assert.deepEqual(mapped, []);
  assert.equal(JSON.stringify(tokens), JSON.stringify(VOCABULARY_THEME_TOKENS),
    "byte-identical to the defaults, so a capture with nothing to say leaves the theme it had (P5)");
  assert.deepEqual(
    unmapped.map((row) => row.name),
    ["color.accent", "color.ink", "color.ink-muted", "color.line", "color.surface",
      "color.surface-sunk", "size.radius", "text.base", "text.family"],
    "and every slot the bridge names is named back rather than silently kept - the report is the "
      + "difference between a theme that matched the source and one that only looks like it "
      + "might have. SH834: nine, because `size.measure` and `space.step` are in no group and are "
      + "therefore in neither list — this theme has nowhere to put them, which is a different "
      + "fact from the source not stating one",
  );
});

test("SH831: two runs over one palette map identically", () => {
  const once = vocabularyTokensFrom(themeFor(PALETTE_SHEET).rows);
  const twice = vocabularyTokensFrom(themeFor(PALETTE_SHEET).rows);

  assert.equal(JSON.stringify(once), JSON.stringify(twice));
});

// --- SH834: the half of the palette nothing read ---

/**
 * A source that states one thing plainly per group: a page-level font stack, a corner radius used
 * only on an element, and a font-size scale with no page-level step. The three admission rules
 * disagree about all three, which is the whole of SH834.
 */
const TYPE_SHEET = `
  :root{--radius:6px;--font-body:"Fixture Sans",system-ui,sans-serif}
  body{font-family:var(--font-body)}
  .badge{border-radius:var(--radius)}
  .card{border-radius:var(--radius)}
  .pill{border-radius:var(--radius)}
  .h2{font-size:28px}
  .h3{font-size:28px}
  .h4{font-size:28px}
`;

test("SH834: a declared token is roled by every group, not only by colour", () => {
  const rows = TYPE_SHEET && themeFor(TYPE_SHEET).rows;
  const row = (path) => rows.find((each) => each.path === path);

  assert.equal(row("radius").group, "radius",
    "`--radius` is read by three `border-radius` rules; before this it left the extractor as a "
      + "bare name with nothing saying what it was for");
  assert.equal(row("radius").role, null, "a length group has one role, so there is none to name");
  assert.equal(row("font.body").group, "font.family");
  assert.equal(row("font.body").page, true, "`body { font-family: … }` is a page-level statement");
});

test("SH834: a group with one role is taken at its rank, with no page-level statement", () => {
  const { mapped, tokens } = vocabularyTokensFrom(themeFor(TYPE_SHEET).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // The argument SH831 made for colour is about COMPETING roles - the same colour is text here and
  // a background there - so it does not carry to a group that has one. `body { border-radius }`
  // would mean nothing, so there is nothing for a page-level selector to disambiguate.
  assert.equal(from["size.radius"], "6px", "used on three elements and on no page-level selector");
  assert.equal(tokens.size.radius.$value, "6px");
  assert.equal(from["text.family"], '"Fixture Sans",system-ui,sans-serif',
    "and the stack, which had nowhere to land until this theme declared a token for it - "
      + "`.shio-page` hardcoded `system-ui, …`, so a replica in the source's colours and the "
      + "system font read as *nearly* the source");
});

test("SH834: a scale is refused rather than guessed, and the refusal is reported", () => {
  const { mapped, unmapped, tokens } = vocabularyTokensFrom(themeFor(TYPE_SHEET).rows);

  // 28px is declared three times, so `recurringTokens` writes `font.size.1` — and rank is not
  // scale position: this theme's five text steps are ordered by size, so taking rank 1 for
  // `text.base` would set the body copy to whatever size the source happened to repeat most.
  assert.ok(themeFor(TYPE_SHEET).rows.some((row) => row.path === "font.size.1"),
    "the row is there to be taken, which is what makes the refusal a decision rather than a gap");
  assert.ok(!mapped.some((row) => row.name === "text.base"));
  assert.equal(tokens.text.base.$value, VOCABULARY_THEME_TOKENS.text.base.$value);
  assert.ok(
    unmapped.some((row) => row.name === "text.base" && row.role === "font.size#1"),
    "reported, because a refusal nobody states is indistinguishable from a group nobody read",
  );
  for (const step of ["lead", "h1", "h2", "h3"]) {
    assert.equal(tokens.text[step].$value, VOCABULARY_THEME_TOKENS.text[step].$value,
      `text.${step} is not in the bridge at all - four steps of a scale cannot be derived from a `
        + "ranking, and inventing three of them from one measured value is the guess this refuses");
  }
});

test("SH834: a page-level font-size is the one step of the scale that can be taken", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(`${TYPE_SHEET}\n  body{font-size:17px}`).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // The declared half has carried `page` since SH831; the recurring half carries it now, because a
  // literal on `body` is the same statement as a variable on `body` and only one of them was read.
  assert.equal(from["text.base"], "17px",
    "one occurrence, on the page's own selector, outranks three of the same value on classes");
});

test("SH834: a token used where no group looks is roled by nothing", () => {
  const rows = themeFor(':root{--icon:url("/img/sprite.svg")}.badge{background-image:var(--icon)}').rows;

  assert.equal(rows.find((row) => row.path === "icon").group, undefined,
    "`background-image` is in no group, and a url() counted as a length would have reached a slot "
      + "expecting one");
});

// --- SH839: a recurring token is a literal, never a pointer ---

test("SH839: a page-level value that is only a var() is not a token this file may extract", () => {
  // Measured on `theme-heavy`, where SH834's page-level exemption admitted exactly this and the
  // comment on that exemption claimed a blast radius of zero. It said so because the measurement
  // behind it filtered for values holding no `var(` — the right filter for a length group, whose
  // `isLength` already refuses one, and the wrong one for `font.family`, which has no value test.
  const rows = themeFor(':root{--font-body:Inter,sans-serif}body{font-family:var(--font-body)}').rows;

  assert.deepEqual(
    rows.filter((row) => row.source === "recurring").map((row) => row.path), [],
    "a recurring token's premise is a literal the sheet repeats, and `var(--font-body)` is already "
      + "somebody's token: writing it produces `--font-family-1: var(--font-body)`, a field whose "
      + "value is a pointer and which edits into nothing",
  );
  assert.deepEqual(rows.map((row) => row.path), ["font.body"],
    "while the source's OWN token is untouched - it is the one that moves the page");
});

test("SH839: a value that merely interpolates a var() is still the source's own decision", () => {
  const shadow = "0 1px 2px var(--shadow-color)";
  const rows = themeFor(`.a{box-shadow:${shadow}}.b{box-shadow:${shadow}}.c{box-shadow:${shadow}}`).rows;

  assert.deepEqual(rows.map((row) => [row.path, row.value]), [["shadow.1", shadow]],
    "nothing BUT a var() is refused, not anything containing one - a composed value carries several "
      + "decisions and happens to interpolate one, and refusing it would lose a real shadow to a "
      + "rule about a pointer");
});

test("SH839: the page-level exemption still admits the literal it was written for", () => {
  const rows = themeFor("body{font-size:17px}").rows;

  assert.deepEqual(rows.map((row) => [row.path, row.value, row.refs]), [["font.size.1", "17px", 1]],
    "one occurrence, below minRefs, kept on the strength of its selector - narrowing the rule to "
      + "literals must not narrow it out of existence");
});

// --- SH840: the page's own value wins its slot ---

/** A site with no custom properties: a body colour stated once, and a card colour repeated five. */
const COMPETING_SHEET = `
  body{color:#111111}
  .a{color:#333333} .b{color:#333333} .c{color:#333333} .d{color:#333333} .e{color:#333333}
`;

test("SH840: a page-level row outranks a more-referenced one, which is SH831's finding twice", () => {
  const rows = themeFor(COMPETING_SHEET).rows;
  const { mapped } = vocabularyTokensFrom(rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // What the extractor sees, and why the ranking alone gets it wrong: the card colour is written
  // five times and the body's once, so `color.text-1` is the CARD's. SH831 measured this exact
  // shape on declared tokens - `--brand` beating `--ink` four to three - and fixed it there only.
  assert.deepEqual(
    rows.map((row) => [row.path, row.value, row.refs, row.page === true]),
    [["color.text-1", "#333333", 5, false], ["color.text-2", "#111111", 1, true]],
  );
  assert.equal(from["color.ink"], "#111111",
    "the page's own text colour, not the one five rules reach for - a replica whose body copy is "
      + "the card's colour is the artefact SH831 refused to ship on the other half of the palette");
  assert.equal(from["color.ink-muted"], "#333333",
    "and the repeated one is not discarded, it falls to the next slot: the sort decides which of "
      + "several eligible rows fills a slot first, which is why this is not a bar in `admits`");
});

// --- SH845: the palette the source already published ---

/**
 * The shape the modern web declares its palette in, and the shape this reader refused.
 *
 * <p>A Tailwind v4 sheet emits `@layer theme { :root, :host { … } }`, and both halves of that were
 * disqualifying: `@layer` counted as a variant-making at-rule, and `:root,:host` was not one of the
 * five selector spellings the root set happened to hold. Measured on `www.viglet.org` — 319 custom
 * properties, every one reported as `variant-only`, while the three colour tokens that *were*
 * written came from ranking literals in the utility layer, the commonest of which is `#0000`.</p>
 */
const LAYERED_SHEET = `
@layer theme, base, utilities;
@layer theme {
  :root, :host {
    --vg-foreground: #234652;
    --vg-background: #ffffff;
    --vg-radius: 8px;
  }
}
@layer base {
  body { color: var(--vg-foreground); background: var(--vg-background) }
  .card { border-radius: var(--vg-radius) }
}
@layer utilities {
  .bg-none-1 { background-color: #0000 }
  .bg-none-2 { background-color: #0000 }
  .bg-none-3 { background-color: #0000 }
}
`;

test("SH845: a palette declared under @layer is the source's own, not a variant of somebody's theme", () => {
  const { tokens, unsure } = declaredTokens(declarationsOf(LAYERED_SHEET));

  assert.deepEqual(unsure, [],
    "319 of these were reported as `variant-only: declared only under @layer theme` on the run that "
      + "found this - a layer orders the cascade, it does not make what it wraps conditional");
  assert.deepEqual(
    tokens.map((row) => [row.name, row.value]),
    [["--vg-background", "#ffffff"], ["--vg-foreground", "#234652"], ["--vg-radius", "8px"]],
  );
});

test("SH845: a media query inside a layer is still a variant, which one at-rule field could not say", () => {
  const { tokens, unsure } = declaredTokens(declarationsOf(
    "@layer theme{:root{--bg:#ffffff}@media(prefers-color-scheme:dark){:root{--bg:#000000}}}",
  ));

  assert.deepEqual(tokens, [], "the outermost at-rule is the layer, and the layer is now the half "
    + "that is not the reason - so reading `stack.find` would have written the light value");
  assert.equal(unsure[0].reason, "variant");
  assert.match(unsure[0].detail, /also declared under @media/);
});

test("SH845: a root is every compound in the list, so :root,:host is one and :root,.dark is not", () => {
  const both = declaredTokens(declarationsOf(":root,:host{--bg:#ffffff}"));
  const half = declaredTokens(declarationsOf(":root,.dark{--bg:#ffffff}"));

  assert.equal(both.tokens.length, 1, "the spelling a Tailwind v4 sheet emits, which the enumerated "
    + "set held no entry for - and no enumeration of orderings could have held them all");
  assert.deepEqual(half.tokens, [], "a list mixing a root with a theme class declares a variant in "
    + "the half that matters");
});

test("SH845: a page-level declaration inside a layer still states the page's own colour", () => {
  const rows = themeFor(LAYERED_SHEET).rows;
  const row = (name) => rows.find((each) => each.name === name);

  assert.equal(row("--vg-foreground").page, true,
    "`@layer base { body { color: var(--vg-foreground) } }` is the page saying what its text looks "
      + "like; a sheet whose every rule sits in a layer would otherwise have no page-level use at all");
  assert.equal(row("--vg-foreground").role, "text");
});

test("SH845: a fully transparent literal is a reset, so it is not the site's palette", () => {
  const rows = themeFor(LAYERED_SHEET).rows;

  assert.deepEqual(rows.filter((row) => row.source === "recurring"), [],
    "`#0000` written three times in a utility layer outranks every real colour and paints nothing - "
      + "it was ranked `color.background-1`, the slot the bridge assigns to the replica's page");
  assert.deepEqual(
    recurringTokens(declarationsOf(
      ".a{background-color:rgba(0,0,0,0)}.b{background-color:rgba(0,0,0,0)}.c{background-color:rgba(0,0,0,0)}",
    )).tokens,
    [],
    "the function spellings too, or the rule is one hex form deep",
  );
});

test("SH845: the declared palette reaches the theme, and the transparent literal does not", () => {
  const { mapped, unmapped } = vocabularyTokensFrom(themeFor(LAYERED_SHEET).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));
  const kept = Object.fromEntries(unmapped.map((row) => [row.name, row.value]));

  assert.equal(from["color.ink"], "#234652", "the source's text colour, which the replica used to "
    + "render with this file's hardcoded #12151a because the token was never written");
  assert.equal(from["color.surface"], "#ffffff");
  assert.equal(from["size.radius"], "8px");
  assert.equal(kept["color.surface-sunk"], VOCABULARY_THEME_TOKENS.color["surface-sunk"].$value,
    "a slot the source did not state keeps the default and is reported - which is a better artefact "
      + "than a card painted with a utility class's transparent (P9)");
});

// --- SH846: the mapping's floor, and the third thing a run can say about a slot ---

const colorRow = (path, value, role, extra = {}) => ({
  path, value, type: "color", source: "declared", group: "color", role, refs: 3, page: true, ...extra,
});

test("SH846: a fully transparent value is not a page, so the slot is refused rather than filled", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#0000", "background"),
    colorRow("vg.fg", "#234652", "text"),
  ]);

  assert.equal(tokens.color.surface.$value, VOCABULARY_THEME_TOKENS.color.surface.$value,
    "`.shio-page` sets `background: var(--color-surface)`, so a transparent one shows whatever lies "
      + "behind it - white here only because the document is, which is the accident that survives "
      + "review and breaks on a dark layout");
  assert.deepEqual(mapped.map((row) => row.name), ["color.ink"]);
  assert.equal(rejected[0].name, "color.surface");
  assert.equal(rejected[0].reason, "transparent");
  assert.match(rejected[0].detail, /the source's background#1 is #0000, which paints nothing/);
});

test("SH846: refused and never-offered are two facts, and a rank nothing filled is the second", () => {
  const { rejected, unmapped } = vocabularyTokensFrom([colorRow("vg.bg", "#0000", "background")]);

  assert.deepEqual(rejected.map((row) => row.name), ["color.surface"]);
  assert.deepEqual(unmapped.map((row) => row.name).filter((name) => name.startsWith("color.surface")),
    ["color.surface-sunk"],
    "the source states ONE background and the floor took it, so `background#2` was never offered a "
      + "row at all - calling that refused would report a defect the source does not have");
});

test("SH846: a mapping never writes a pair it assembled that cannot be read", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#ffffff", "background"),
    colorRow("vg.fg", "#f9fafb", "text"),
  ]);

  assert.equal(tokens.color.ink.$value, VOCABULARY_THEME_TOKENS.color.ink.$value);
  assert.deepEqual(mapped.map((row) => row.name), ["color.surface"],
    "the surface is the larger area and the more certain reading, so the text is the half that "
      + "gives way where both came from the source");
  assert.equal(rejected[0].name, "color.ink");
  assert.equal(rejected[0].reason, "contrast");
  assert.match(rejected[0].detail, /1\.05:1 against --color-surface #ffffff, under the 1\.5:1 floor/);
});

test("SH846: the failure does not need a bad source — a dark page under a default ink is the likelier half", () => {
  const { tokens, rejected } = vocabularyTokensFrom([colorRow("vg.bg", "#0f172a", "background")]);

  assert.equal(tokens.color.surface.$value, VOCABULARY_THEME_TOKENS.color.surface.$value,
    "nothing about #0f172a is wrong; it is wrong under THIS file's near-black default, and no rule "
      + "about the source's own values could have caught it");
  assert.equal(rejected[0].name, "color.surface");
  assert.match(rejected[0].detail, /1\.02:1 against --color-ink/);
});

test("SH846: an ordinary dark theme states both halves and nothing moves", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#0f172a", "background"),
    colorRow("vg.fg", "#f1f5f9", "text"),
  ]);

  assert.deepEqual(rejected, [], "the floor must not fire on the case it exists to allow through");
  assert.deepEqual(mapped.map((row) => row.value).sort(), ["#0f172a", "#f1f5f9"]);
  assert.equal(tokens.color.surface.$value, "#0f172a");
});

test("SH846: the floor is below every published grade, so a real caption grey survives it", () => {
  // 2.85:1 - under WCAG's 3 and 4.5, and a colour a great many sites ship on purpose. A mapping
  // that graded the source's design would be substituting taste for the source.
  assert.equal(contrastRatio("#999999", "#ffffff").toFixed(2), "2.85");
  const { mapped } = vocabularyTokensFrom([
    colorRow("vg.bg", "#ffffff", "background"),
    colorRow("vg.fg", "#111111", "text"),
    colorRow("vg.muted", "#999999", "text", { refs: 1 }),
  ]);

  assert.deepEqual(mapped.map((row) => [row.name, row.value]).sort(),
    [["color.ink", "#111111"], ["color.ink-muted", "#999999"], ["color.surface", "#ffffff"]]);
});

test("SH846: a check that could not run is reported, not passed", () => {
  const { mapped, rejected, unchecked } = vocabularyTokensFrom([
    colorRow("vg.bg", "oklch(0.98 0.01 250)", "background"),
    colorRow("vg.fg", "#334155", "text"),
  ]);

  assert.equal(parseColor("oklch(0.98 0.01 250)"), null,
    "this module scans rather than models, and a colour-space conversion is modelling");
  assert.deepEqual(mapped.map((row) => row.name), ["color.ink", "color.surface"],
    "refusing what it cannot read would throw away the source's palette, which is the loss SH845 "
      + "spent a task repairing");
  assert.deepEqual(rejected, []);
  assert.deepEqual(unchecked.map((row) => row.text), ["color.ink", "color.ink-muted"]);
});

test("SH846: a default-on-default pair needs no note, because nothing captured is in it", () => {
  const { unchecked } = vocabularyTokensFrom([]);

  assert.deepEqual(unchecked, []);
});

test("SH846: a translucent foreground is judged as it will be seen, not as if it were opaque", () => {
  assert.equal(contrastRatio("rgba(0,0,0,.05)", "#ffffff").toFixed(2), "1.12",
    "compositing is what makes this 1.12 and not 21 - an uncomposited reading is how a floor lets "
      + "an invisible value through");
  assert.equal(contrastRatio("hsl(0 0% 60%)", "hsl(0 0% 100%)").toFixed(2), "2.85",
    "and hsl reads the same as the hex it is, or the floor depends on which spelling the source used");
  assert.equal(contrastRatio("#0b5533", "color-mix(in oklab, red 50%, blue)"), null);
});

// --- SH847: the accent, read off the reversal a stylesheet states without naming it ---

/**
 * A source shaped like the one this was filed on: the whole palette in custom properties, the page
 * stating its own pair, and the call-to-action a `background-color` on a class.
 */
const BUTTON_SHEET = `
  :root{--vg-accent:#ea580c;--vg-bg:#ffffff;--vg-fg:#234652;--vg-card:#f6f8f9}
  body{background:var(--vg-bg);color:var(--vg-fg)}
  .btn{background-color:var(--vg-accent);color:#ffffff}
  .card{background-color:var(--vg-card);color:var(--vg-fg)}
  .footer{background-color:#234652;color:#ffffff}
`;

test("SH847: a rule painting the page's own surface as its text is stating the accent", () => {
  const { surface, ink, reversals } = pageStatement(declarationsOf(BUTTON_SHEET));

  assert.equal(surface, "#ffffff", "read through the custom property, since a modern sheet states "
    + "its page as `background: var(--vg-bg)` and nothing else");
  assert.equal(ink, "#234652");
  assert.deepEqual([...reversals], [".btn||"],
    "the card is not one - its text is the PAGE's text, so it is a surface and not a reversal; and "
      + "the footer is the page inverted rather than an accent, which the measurement found and the "
      + "design did not");
});

test("SH847: the declared accent reaches the theme, having been refused twice over before", () => {
  const rows = themeFor(BUTTON_SHEET).rows;
  const accent = rows.find((row) => row.name === "--vg-accent");

  assert.equal(accent.role, "accent",
    "roled `background` before this, because `accent` was fed only by fill/stroke/caret-color/"
      + "text-decoration-color and a real call-to-action is none of those");
  assert.equal(accent.reversal, true);
  assert.equal(accent.page, false, "and it is NOT page-level, which was the second refusal");

  const { mapped } = vocabularyTokensFrom(rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));
  assert.equal(from["color.accent"], "#ea580c",
    "the one slot a curator sees most: it paints every button on the replica, and it kept this "
      + "file's dark green on a site whose accent is orange");
  assert.equal(from["color.surface"], "#ffffff");
  assert.equal(from["color.ink"], "#234652");
});

test("SH847: the signal is stated relative to the page, so a dark source reads the same way", () => {
  const rows = themeFor(`
    :root{--bg:#0f172a;--fg:#f1f5f9;--accent:#38bdf8}
    body{background:var(--bg);color:var(--fg)}
    .btn{background-color:var(--accent);color:#0f172a}
    .card{background-color:#1e293b;color:var(--fg)}
  `).rows;
  const { mapped } = vocabularyTokensFrom(rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.equal(from["color.accent"], "#38bdf8",
    "the button's text is the page's surface here too - a test written in absolute lightness would "
      + "have caught the white-on-orange case and missed this one entirely");
  assert.equal(from["color.surface"], "#0f172a");
});

test("SH847: a reversal is a labelled statement, so one button is enough and counting is the wrong question", () => {
  const { tokens } = recurringTokens(declarationsOf(`
    body{background:#ffffff;color:#111111}
    .btn{background-color:#ea580c;color:#ffffff}
  `));

  assert.deepEqual(tokens.filter((row) => row.role === "accent").map((row) => [row.path, row.value, row.refs]),
    [["color.accent-1", "#ea580c", 1]],
    "`minRefs` asks *is this value a palette*; a page-level selector answers *this is the page's "
      + "own*; a reversal answers *this is the element the page turns inside out*. Three questions, "
      + "and only the first is about repetition");
});

test("SH847: a source that states no page background gets no reversals, and the corpus is that source", () => {
  // Every archetype: none states a page-level background, so `theme-heavy` keeps taking its accent
  // from `fill` exactly as SH831 wrote it. A signal that needs the page pair declines where the
  // page states none rather than guessing from a rule it cannot situate.
  const { surface, reversals } = pageStatement(declarationsOf(`
    .mark{background:#0b7285;color:#fff}
    .quote{background:#10243a;color:#fff}
  `));

  assert.equal(surface, null);
  assert.deepEqual([...reversals], []);
});

test("SH847: an alias chain is read through, and a cycle returns instead of recursing", () => {
  const direct = pageStatement(declarationsOf(
    ":root{--a:var(--b);--b:#ffffff;--fg:#234652}body{background:var(--a);color:var(--fg)}",
  ));
  assert.equal(direct.surface, "#ffffff", "one chain of var() read through, which is what a token "
    + "layer built on another token layer looks like");

  const cycle = pageStatement(declarationsOf(
    ":root{--a:var(--b);--b:var(--a)}body{background:var(--a);color:#111111}",
  ));
  assert.equal(cycle.surface, null, "legal to write, computes to nothing, and must not recurse on "
    + "a stranger's bytes");
});

test("SH847: a reversal under a media query is its own rule, so a variant cannot claim the accent", () => {
  const { reversals } = pageStatement(declarationsOf(
    "body{background:#ffffff;color:#111111}"
      + "@media(min-width:40em){.btn{background-color:#ea580c;color:#ffffff}}",
  ));

  assert.deepEqual([...reversals], [".btn||@media(min-width:40em)"],
    "the key carries the condition, so `.btn` inside and outside a query are two rules - the same "
      + "separation `declaredTokens` makes, since merging them would attribute one's background to "
      + "the other's text colour");
});
