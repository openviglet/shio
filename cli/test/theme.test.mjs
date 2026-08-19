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
  ACCENT_CHROMA_FLOOR,
  buildTheme,
  chromaOf,
  contrastRatio,
  customPropertyFor,
  declaredTokens,
  describeTheme,
  dtcgFor,
  flatten,
  isTranslucent,
  pageStatement,
  parseColor,
  pathForCustomProperty,
  recurringTokens,
  rewriteUrls,
  scanCss,
} from "../src/theme.mjs";
import {
  collidingProperties, CONTRAST_PAIRS, declaredProperties, VOCABULARY_THEME_CSS,
  VOCABULARY_THEME_TOKENS, vocabularyTokensFrom,
} from "../src/vocabulary-theme.mjs";

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
  assert.equal(from["shio-color.ink"], "#10243a", "the body's own colour, not the one four rules reach for");
  assert.equal(from["shio-color.surface"], "#eef3f8");
  assert.equal(tokens["shio-color"].ink.$value, "#10243a");
  assert.equal(tokens["shio-color"]["ink-muted"].$value, VOCABULARY_THEME_TOKENS["shio-color"]["ink-muted"].$value,
    "and the accent did not slide into the next text slot either: `--brand` is excluded from the "
      + "declared half outright, not merely outranked, so a slot with nothing to fill it keeps "
      + "this file's default");
  assert.ok(
    unmapped.some((row) => row.name === "shio-color.ink-muted" && row.role === "text#2"),
    "the second text colour is reported by the role it looked for, so a reader can tell a default "
      + "that was chosen from one that was never offered a value (P9)",
  );
});

test("SH831: a recurring literal fills a slot the source declares no variable for", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(PALETTE_SHEET).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // `border-color` and `fill` three times each: the extractor already ranks these by role, which is
  // its published contract, so they are taken at their own rank with no second inference.
  assert.equal(from["shio-color.line"], "#d9e2ec");
  assert.equal(from["shio-color.accent"], "#ff6600",
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
    ["shio-color.accent", "shio-color.ink", "shio-color.ink-muted", "shio-color.line", "shio-color.surface",
      "shio-color.surface-sunk", "shio-size.radius", "shio-text.base", "shio-text.family"],
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
  assert.equal(from["shio-size.radius"], "6px", "used on three elements and on no page-level selector");
  assert.equal(tokens["shio-size"].radius.$value, "6px");
  assert.equal(from["shio-text.family"], '"Fixture Sans",system-ui,sans-serif',
    "and the stack, which had nowhere to land until this theme declared a token for it - "
      + "`.shio-page` hardcoded `system-ui, …`, so a replica in the source's colours and the "
      + "system font read as *nearly* the source");
});

test("SH834: a scale is refused rather than guessed, and the refusal is reported", () => {
  const { mapped, unmapped, tokens } = vocabularyTokensFrom(themeFor(TYPE_SHEET).rows);

  // 28px is declared three times, so `recurringTokens` writes `font.size.1` — and rank is not
  // scale position: this theme's five text steps are ordered by size, so taking rank 1 for
  // `shio-text.base` would set the body copy to whatever size the source happened to repeat most.
  assert.ok(themeFor(TYPE_SHEET).rows.some((row) => row.path === "font.size.1"),
    "the row is there to be taken, which is what makes the refusal a decision rather than a gap");
  assert.ok(!mapped.some((row) => row.name === "shio-text.base"));
  assert.equal(tokens["shio-text"].base.$value, VOCABULARY_THEME_TOKENS["shio-text"].base.$value);
  assert.ok(
    unmapped.some((row) => row.name === "shio-text.base" && row.role === "font.size#1"),
    "reported, because a refusal nobody states is indistinguishable from a group nobody read",
  );
  for (const step of ["lead", "h1", "h2", "h3"]) {
    assert.equal(tokens["shio-text"][step].$value, VOCABULARY_THEME_TOKENS["shio-text"][step].$value,
      `text.${step} is not in the bridge at all - four steps of a scale cannot be derived from a `
        + "ranking, and inventing three of them from one measured value is the guess this refuses");
  }
});

test("SH834: a page-level font-size is the one step of the scale that can be taken", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(`${TYPE_SHEET}\n  body{font-size:17px}`).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  // The declared half has carried `page` since SH831; the recurring half carries it now, because a
  // literal on `body` is the same statement as a variable on `body` and only one of them was read.
  assert.equal(from["shio-text.base"], "17px",
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
  assert.equal(from["shio-color.ink"], "#111111",
    "the page's own text colour, not the one five rules reach for - a replica whose body copy is "
      + "the card's colour is the artefact SH831 refused to ship on the other half of the palette");
  assert.equal(from["shio-color.ink-muted"], "#333333",
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

  assert.equal(from["shio-color.ink"], "#234652", "the source's text colour, which the replica used to "
    + "render with this file's hardcoded #12151a because the token was never written");
  assert.equal(from["shio-color.surface"], "#ffffff");
  assert.equal(from["shio-size.radius"], "8px");
  assert.equal(kept["shio-color.surface-sunk"], VOCABULARY_THEME_TOKENS["shio-color"]["surface-sunk"].$value,
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

  assert.equal(tokens["shio-color"].surface.$value, VOCABULARY_THEME_TOKENS["shio-color"].surface.$value,
    "`.shio-page` sets `background: var(--shio-color-surface)`, so a transparent one shows whatever lies "
      + "behind it - white here only because the document is, which is the accident that survives "
      + "review and breaks on a dark layout");
  assert.deepEqual(mapped.map((row) => row.name), ["shio-color.ink"]);
  assert.equal(rejected[0].name, "shio-color.surface");
  assert.equal(rejected[0].reason, "transparent");
  assert.match(rejected[0].detail, /the source's background#1 is #0000, which paints nothing/);
});

test("SH846: refused and never-offered are two facts, and a rank nothing filled is the second", () => {
  const { rejected, unmapped } = vocabularyTokensFrom([colorRow("vg.bg", "#0000", "background")]);

  assert.deepEqual(rejected.map((row) => row.name), ["shio-color.surface"]);
  assert.deepEqual(unmapped.map((row) => row.name).filter((name) => name.startsWith("shio-color.surface")),
    ["shio-color.surface-sunk"],
    "the source states ONE background and the floor took it, so `background#2` was never offered a "
      + "row at all - calling that refused would report a defect the source does not have");
});

test("SH846: a mapping never writes a pair it assembled that cannot be read", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#ffffff", "background"),
    colorRow("vg.fg", "#f9fafb", "text"),
  ]);

  assert.equal(tokens["shio-color"].ink.$value, VOCABULARY_THEME_TOKENS["shio-color"].ink.$value);
  assert.deepEqual(mapped.map((row) => row.name), ["shio-color.surface"],
    "the surface is the larger area and the more certain reading, so the text is the half that "
      + "gives way where both came from the source");
  assert.equal(rejected[0].name, "shio-color.ink");
  assert.equal(rejected[0].reason, "contrast");
  assert.match(rejected[0].detail, /1\.05:1 against --shio-color-surface #ffffff, under the 1\.5:1 floor/);
});

test("SH846: the failure does not need a bad source — a dark page under a default ink is the likelier half", () => {
  const { tokens, rejected } = vocabularyTokensFrom([colorRow("vg.bg", "#0f172a", "background")]);

  assert.equal(tokens["shio-color"].surface.$value, VOCABULARY_THEME_TOKENS["shio-color"].surface.$value,
    "nothing about #0f172a is wrong; it is wrong under THIS file's near-black default, and no rule "
      + "about the source's own values could have caught it");
  assert.equal(rejected[0].name, "shio-color.surface");
  assert.match(rejected[0].detail, /1\.02:1 against --shio-color-ink/);
});

test("SH846: an ordinary dark theme states both halves and nothing moves", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#0f172a", "background"),
    colorRow("vg.fg", "#f1f5f9", "text"),
  ]);

  assert.deepEqual(rejected, [], "the floor must not fire on the case it exists to allow through");
  assert.deepEqual(mapped.map((row) => row.value).sort(), ["#0f172a", "#f1f5f9"]);
  assert.equal(tokens["shio-color"].surface.$value, "#0f172a");
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
    [["shio-color.ink", "#111111"], ["shio-color.ink-muted", "#999999"], ["shio-color.surface", "#ffffff"]]);
});

test("SH846: a check that could not run is reported, not passed", () => {
  // SH856 — written with `oklch(0.98 0.01 250)`, which this reader converts now. The property is
  // about the ABSTENTION, so the fixture moves to a value that is still genuinely unreadable rather
  // than the property being dropped: `lab()` needs a D50 white point and a chromatic adaptation,
  // which is the colour library this module declines to become.
  const { mapped, rejected, unchecked } = vocabularyTokensFrom([
    colorRow("vg.bg", "lab(98% 1 -4)", "background"),
    colorRow("vg.fg", "#334155", "text"),
  ]);

  assert.equal(parseColor("lab(98% 1 -4)"), null,
    "this module scans rather than models, and a chromatic adaptation is modelling");
  assert.deepEqual(mapped.map((row) => row.name), ["shio-color.ink", "shio-color.surface"],
    "refusing what it cannot read would throw away the source's palette, which is the loss SH845 "
      + "spent a task repairing");
  assert.deepEqual(rejected, []);
  assert.deepEqual(unchecked.map((row) => [row.text, row.on]),
    [["shio-color.ink", "shio-color.surface"], ["shio-color.ink-muted", "shio-color.surface"], ["shio-color.surface", "shio-color.accent"]],
    "SH852 - three pairs, because the button's label IS `shio-color.surface`: the value the page pair "
      + "could not be judged on is the same value the button is written in, and a report naming two "
      + "of the three would have the curator fix a page whose button is unread for the same reason");
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
  assert.equal(from["shio-color.accent"], "#ea580c",
    "the one slot a curator sees most: it paints every button on the replica, and it kept this "
      + "file's dark green on a site whose accent is orange");
  assert.equal(from["shio-color.surface"], "#ffffff");
  assert.equal(from["shio-color.ink"], "#234652");
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

  assert.equal(from["shio-color.accent"], "#38bdf8",
    "the button's text is the page's surface here too - a test written in absolute lightness would "
      + "have caught the white-on-orange case and missed this one entirely");
  assert.equal(from["shio-color.surface"], "#0f172a");
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
  // Every archetype: none states a page-level background, so no reversal can be computed on any of
  // them. A signal that needs the page pair declines where the page states none rather than guessing
  // from a rule it cannot situate. (SH848 reads `.mark` below by its chroma instead, which needs no
  // page pair — so `theme-heavy` does now get an accent, and it gets it from the other signal.)
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

// --- SH848: the role read off the colour, because the same word is a different role per vocabulary ---

/**
 * The palette this was filed on: shadcn's words, where `accent` is a near-white hover tint, `primary`
 * is a near-black neutral, and the orange every button is painted with sits under `brand`.
 *
 * <p>Two things stop SH847's reversal from reaching it, and only one of them is about naming. The
 * button's own text is `--vg-primary-foreground` — a near-white of its OWN, not the page's
 * `--vg-background` — so the reversal test's equality never holds; and both are in `oklch()`, which
 * `parseColor` declines outright, so the two could not have been compared even had they matched.</p>
 */
const SHADCN_SHEET = `
  :root{
    --vg-background:#ffffff;--vg-foreground:oklch(14.5% 0 0);
    --vg-accent:oklch(97% 0 0);--vg-primary:oklch(20.5% 0 0);
    --vg-primary-foreground:oklch(98.5% 0 0);
    --vg-brand:#c2410c;--vg-brand-hover:#f97316;--vg-border:#e5e7eb
  }
  body{background:var(--vg-background);color:var(--vg-foreground)}
  .card{background:var(--vg-accent);border:1px solid var(--vg-border)}
  .nav a:hover{background-color:var(--vg-accent)}
  .chip{background-color:var(--vg-accent);color:var(--vg-primary)}
  .btn{background-color:var(--vg-brand);color:var(--vg-primary-foreground)}
  .btn:hover{background-color:var(--vg-brand-hover);color:var(--vg-primary-foreground)}
`;

test("SH848: a shadcn palette hands over its brand colour, not the near-white its `accent` names", () => {
  const rows = themeFor(SHADCN_SHEET).rows;
  const { mapped, tokens } = vocabularyTokensFrom(rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.equal(tokens["shio-color"].accent.$value, "#c2410c",
    "measured on the replica as `--shio-color-accent: #0b5533`, this file's built-in dark green, "
      + "unchanged across four rounds on a site whose accent is orange");
  assert.equal(from["shio-color.accent"], "#c2410c");
  assert.equal(from["shio-color.surface"], "#ffffff", "and the slots the page states outright are "
    + "untouched: the new signal is a fallback, not a re-reading of what already worked");
  assert.equal(from["shio-color.ink"], "oklch(14.5% 0 0)");

  const brand = rows.find((row) => row.name === "--vg-brand");
  assert.equal(brand.role, "accent");
  assert.equal(brand.page, false, "and it is not page-level, which is the bar it would still be "
    + "failing");
  // SH856 corrects this task's stated bound, the way SH848 corrected SH847's. This asserted
  // `saturated: true` and `reversal: undefined`, on the reasoning that the button's text is
  // `--vg-primary-foreground` — a near-white of its OWN — and that both it and the page's
  // `--vg-background` are in `oklch()`, "which `parseColor` declines, so the two could not have been
  // compared even had they matched". They can be compared now, and they DO match: `oklch(98.5% 0 0)`
  // is the page's white to within the 1.5:1 floor `same` uses. So the stronger reading is the one
  // that fires, and it was never wrong — only unreachable.
  assert.equal(brand.reversal, true, "the button IS the page turned inside out; SH848 could not see "
    + "it because the pair was in a colour space this reader had not learned");
  assert.equal(brand.saturated, undefined, "and the fallback stands down where the signal it was a "
    + "fallback FOR can answer - which is what makes the two flags separate facts");
});

test("SH848: the tint the source called `accent` is refused, having no colour in it to be one", () => {
  const rows = themeFor(SHADCN_SHEET).rows;
  const tint = rows.find((row) => row.name === "--vg-accent");

  assert.equal(chromaOf("oklch(97% 0 0)"), 0,
    "zero chroma is what a hover tint is, in any vocabulary and in a space `parseColor` cannot "
      + "convert - which is the whole reason the chroma is read off the value rather than computed");
  assert.equal(tint.role, "background",
    "four rules read it and every one of them paints a surface; a name table would have handed it "
      + "the accent slot on the word, which is the unverifiable label SH689 spent a task removing");
  assert.equal(vocabularyTokensFrom(rows).mapped.find((row) => row.name === "shio-color.accent").value,
    "#c2410c");
});

test("SH848: a hover fill is the colour the button turns, so the resting one wins the slot", () => {
  const rows = themeFor(SHADCN_SHEET).rows;
  const hover = rows.find((row) => row.name === "--vg-brand-hover");

  assert.equal(chromaOf("#f97316") > chromaOf("#c2410c"), true,
    "the hover colour is the MORE saturated of the two, so chroma alone would take it - the "
      + "selector is what says which is the element and which is a state");
  assert.equal(hover.role, "background",
    "`.btn:hover` states what the button becomes, not what it is; taking it would paint every "
      + "button on the replica with the source's hover colour");
});

test("SH848: the page's own two colours are not accents, however saturated the page is", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(`
    :root{--bg:#f4a259;--fg:#3b1f0b;--panel:#f4a259}
    body{background:var(--bg);color:var(--fg)}
    .panel{background-color:var(--panel);color:var(--fg)}
    .band{background-color:#3b1f0b;color:#f4a259}
  `).rows);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.equal(from["shio-color.surface"], "#f4a259");
  assert.equal(from["shio-color.accent"], undefined,
    "a rule repainting the page's surface is not an accent and neither is one painting its ink - "
      + "the same pair the reversal test excludes, excluded here for the same reason, or a "
      + "saturated page would elect itself its own call-to-action");
});

test("SH848: a chroma nothing could read is not a zero, so the slot declines instead of refusing", () => {
  assert.equal(chromaOf("color-mix(in oklab, red 50%, blue)"), null,
    "cannot say - and a caller that read null as zero would refuse a colour nobody showed was "
      + "grey, which is the silent-pass failure one direction along");
  assert.equal(chromaOf("oklch(0.7 0.19 40)") >= ACCENT_CHROMA_FLOOR, true);
  assert.equal(chromaOf("hsl(20 90% 50%)") >= ACCENT_CHROMA_FLOOR, true);

  // The button's text is its own dark grey rather than the page's surface, so SH847's reversal —
  // which is the stronger reading and is tried first — does not fire and chroma is all that is left.
  const { mapped } = vocabularyTokensFrom(themeFor(`
    body{background:#ffffff;color:#111111}
    .btn{background-color:color-mix(in oklab, red 50%, blue);color:#3f3f46}
  `).rows);
  assert.equal(mapped.find((row) => row.name === "shio-color.accent"), undefined);
});

test("SH848: the floor is in the gap, so every neutral a page is built from stays below it", () => {
  // Measured rather than chosen: the values the failing palette actually holds, and the ones this
  // project's own corpus holds. A bar between two clusters an order of magnitude apart is evidence;
  // a bar somebody liked the look of is the taste threshold this file refuses to ship.
  for (const grey of ["oklch(97% 0 0)", "oklch(20.5% 0 0)", "#e5e7eb", "#6b7280", "#eef3f8", "#10243a"]) {
    assert.equal(chromaOf(grey) < ACCENT_CHROMA_FLOOR, true, `${grey} reads ${chromaOf(grey)}`);
  }
  for (const brand of ["#c2410c", "#f97316", "#0b7285", "#ea580c", "#38bdf8"]) {
    assert.equal(chromaOf(brand) >= ACCENT_CHROMA_FLOOR, true, `${brand} reads ${chromaOf(brand)}`);
  }
});

test("SH848: the floor is the role's, so a surface at half alpha is refused and a border is not", () => {
  const row = (path, value, role) => ({
    path, value, type: "color", source: "declared", group: "color", role, refs: 2, page: true,
  });
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    row("vg.surface", "color-mix(in oklab, var(--vg-accent) 50%, transparent)", "background"),
    row("vg.surface.2", "#eef3f8", "background"),
    row("vg.fg", "#111827", "text"),
    row("vg.line", "rgba(17, 24, 39, 0.12)", "border"),
  ]);
  const from = Object.fromEntries(mapped.map((each) => [each.name, each.value]));

  assert.equal(tokens["shio-color"].surface.$value, "#eef3f8",
    "SH846's floor asked whether the value paints NOTHING, which 50% does not - so the page surface "
      + "became a translucent tint of a near-white and the replica looked white only because the "
      + "document behind it was");
  assert.equal(from["shio-color.line"], "rgba(17, 24, 39, 0.12)",
    "and the same value is an ordinary border: refusing it everywhere would throw away a decision "
      + "the source made on purpose, which is why the floor is on the role and not on the value");
  assert.deepEqual(rejected.map((each) => [each.name, each.reason]),
    [["shio-color.surface-sunk", "translucent"]]);
  assert.match(rejected[0].detail, /which lets the page behind it through/,
    "a different fact from `paints nothing`, and a reader who cannot tell them apart cannot act on "
      + "either");
});

test("SH848: what a value states about its own alpha is read in three spellings", () => {
  assert.equal(isTranslucent("#0000"), true);
  assert.equal(isTranslucent("rgba(0,0,0,.5)"), true);
  assert.equal(isTranslucent("oklch(70% .2 40 / 0.4)"), true,
    "the slash-alpha is stated even in a space this module does not model, so it is read off the "
      + "text rather than through a conversion nobody wrote");
  assert.equal(isTranslucent("color-mix(in oklab, var(--a) 50%, transparent)"), true);
  assert.equal(isTranslucent("#ffffff"), false);
  assert.equal(isTranslucent("oklch(70% .2 40)"), false);
  assert.equal(isTranslucent("color-mix(in oklab, red 50%, blue)"), false,
    "a mix of two opaque colours is opaque, and a mix this cannot read comes back false - "
      + "declining rather than replacing the source's page with this theme's on no evidence");
});

// --- SH852: the other two pairs, and the precedence a shared slot needed before they could land ---

test("SH852: all four painted pairs have a floor, and the two that arrived are the button and the card", () => {
  assert.deepEqual(CONTRAST_PAIRS.map((pair) => [pair.text, pair.on]), [
    ["shio-color.ink", "shio-color.surface"],
    ["shio-color.ink-muted", "shio-color.surface"],
    ["shio-color.ink", "shio-color.surface-sunk"],
    ["shio-color.surface", "shio-color.accent"],
  ], "the stylesheet paints `.shio-button{background:var(--shio-color-accent);color:var(--shio-color-surface)}` "
    + "and `.shio-card{background:var(--shio-color-surface-sunk)}` with `--shio-color-ink` on it - SH846 held "
    + "two of the four and deferred these to the task that made accent reachable");
  const slots = CONTRAST_PAIRS.flatMap((pair) => [pair.text, pair.on]);
  assert.equal(slots.filter((name) => name === "shio-color.surface").length, 3,
    "the order is the decision, not the array: `shio-color.surface` is in three of these four, so the "
      + "pair that may move it has to be stated rather than discovered by which one is listed first");
});

test("SH852: a pale accent is the half that gives way, so the button's label survives the page's", () => {
  const { tokens, mapped, rejected } = vocabularyTokensFrom([
    colorRow("vg.bg", "#ffffff", "background"),
    colorRow("vg.fg", "#1f2937", "text"),
    colorRow("vg.brand", "#ffe066", "accent"),
  ]);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.ok(contrastRatio("#ffffff", "#ffe066") < 1.5,
    "white on a pale yellow is the pair a curator sees most and the one that shipped unchecked");
  assert.equal(from["shio-color.surface"], "#ffffff",
    "the page's surface is settled by pair 1 and is the button's LABEL, so a button judged without "
      + "a precedence would revert a surface the page had already settled");
  assert.equal(tokens["shio-color"].accent.$value, VOCABULARY_THEME_TOKENS["shio-color"].accent.$value);
  assert.deepEqual(rejected.map((row) => [row.name, row.reason]), [["shio-color.accent", "contrast"]]);
  assert.match(rejected[0].detail, /against --shio-color-surface #ffffff, under the 1\.5:1 floor/);
});

test("SH852: reverting one slot re-judges every pair, because a shared slot is in more than one", () => {
  // `shio-color.surface` is in three of the four pairs. Reverting it for the button would leave the page
  // pair holding a value nothing re-read - the state the second rule exists to make impossible.
  const { tokens, mapped } = vocabularyTokensFrom([
    colorRow("vg.bg", "#fffdf0", "background"),
    colorRow("vg.fg", "#fffce8", "text"),
    colorRow("vg.brand", "#fffbe0", "accent"),
  ]);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.equal(from["shio-color.ink"], undefined, "pair 1 fails and the text gives way first (SH846)");
  assert.equal(from["shio-color.accent"], undefined, "and pair 4 fails on the slot it introduces");
  assert.ok(contrastRatio(tokens["shio-color"].ink.$value, tokens["shio-color"].surface.$value) >= 1.5);
  assert.ok(contrastRatio(tokens["shio-color"].surface.$value, tokens["shio-color"].accent.$value) >= 1.5,
    "every pair is legible at the end of the loop, not just the last one judged");
});

test("SH852: a pair the floor may not fix is reported, never counted as a filled slot", () => {
  // The measured case: a dark source states its page and no card, so the card is this file's default
  // near-white under the source's near-white ink. Nothing captured `shio-color.surface-sunk`, so the pair
  // has nothing it is allowed to move - and giving way with `shio-color.ink` instead would revert the
  // page's text, fail pair 1, and take the page's surface with it.
  const { tokens, mapped, rejected, unheld } = vocabularyTokensFrom([
    colorRow("vg.bg", "#0f172a", "background"),
    colorRow("vg.fg", "#f1f5f9", "text"),
  ]);
  const from = Object.fromEntries(mapped.map((row) => [row.name, row.value]));

  assert.equal(from["shio-color.ink"], "#f1f5f9", "the page's two slots are the ones a later pair may not move");
  assert.equal(from["shio-color.surface"], "#0f172a");
  assert.deepEqual(rejected, [], "nothing was reverted, and calling this a rejection would report a "
    + "slot the source filled as one it did not");
  assert.deepEqual(unheld.map((row) => [row.text, row.on]), [["shio-color.ink", "shio-color.surface-sunk"]]);
  assert.match(unheld[0].detail, /--shio-color-surface-sunk is this theme's default and the source states none/,
    "the fix is naming the slot the source never stated - a curator told only that the card is "
      + "unreadable cannot tell which of the two halves to go and change");
  assert.ok(unheld[0].ratio < 1.5);
  assert.equal(tokens["shio-color"]["surface-sunk"].$value, VOCABULARY_THEME_TOKENS["shio-color"]["surface-sunk"].$value);
});

// --- SH853: one :root, two vocabularies ---

/** Tailwind v4's own `@theme` output, which is what these replicas capture. */
const TAILWIND_TOKENS = {
  color: { "red-500": { $value: "#ef4444" }, "slate-900": { $value: "#0f172a" } },
  spacing: { $value: "0.25rem" },
  radius: { lg: { $value: "0.5rem" } },
  text: { sm: { $value: "0.875rem" }, base: { $value: "1rem" }, lg: { $value: "1.125rem" } },
  font: { sans: { $value: "ui-sans-serif, system-ui" } },
};

test("SH853: no name is declared by both blocks on a page that carries both", () => {
  assert.deepEqual(collidingProperties(TAILWIND_TOKENS), [],
    "a mixed page carries this theme's :root and the captured one's at equal specificity, so a "
      + "shared name is won by whichever <style> came second - the accident SH424 and SH510 each "
      + "spent a task removing, and there is no order that is right for both halves of the page");
});

test("SH853: the disjointness is bought by the prefix, not re-checked per source", () => {
  const declared = declaredProperties(VOCABULARY_THEME_TOKENS);
  assert.equal(declared.length, 15, "the whole theme, and the number the `unbridged` count is of");
  assert.deepEqual(declared.filter((name) => !name.startsWith("--shio-")), [],
    "the families used to be `color`, `size`, `space` and `text` - the same words a source's own "
      + "design tokens are named after, which is why the count of collisions was one rather than "
      + "zero and would have grown with the next capture rather than with a commit here");
  assert.ok(declared.includes("--shio-text-base"));
  assert.ok(declared.includes("--shio-color-accent"));
});

test("SH853: the check is on the claim, so a source that reaches into the namespace is reported", () => {
  const collides = collidingProperties({ "shio-text": { base: { $value: "1rem" } } });

  assert.deepEqual(collides, ["--shio-text-base"],
    "the prefix rules out every realistic source and cannot rule out a deliberate one, so the "
      + "residual case is named rather than settled by document order - which is the whole "
      + "difference between a decision and an accident");
});

test("SH853: the names compared are the ones ShThemeTokens will emit, not the ones the CSS reads", () => {
  // `--text-base` is what Tailwind declares; this theme now declares `--shio-text-base`. Reading the
  // stylesheet for `var(--…)` would compare what each theme USES and miss what each one DECLARES,
  // which is the only half a `:root` collision is about.
  assert.equal(customPropertyFor("shio-text.base"), "--shio-text-base");
  assert.deepEqual(declaredProperties(TAILWIND_TOKENS).filter((name) => name.startsWith("--text-")),
    ["--text-base", "--text-lg", "--text-sm"]);
  assert.ok(VOCABULARY_THEME_CSS.includes("var(--shio-text-base)"),
    "and the stylesheet reads the renamed token, or the theme ships every rule unstyled");
  assert.ok(!/var\(--(color|text|size|space)-/.test(VOCABULARY_THEME_CSS));
});

// --- SH855: the election, which had a floor and no ranking ---

/**
 * The shape the accent election was lost on: a Tailwind v4 `:root` carrying the whole colour
 * catalogue, one entry of which is genuinely used — a status dot painted in twelve places — beside the
 * site's own brand orange, which paints the two buttons and is read everywhere else as a link colour.
 *
 * <p>Both signals a reader would reach for point at the orange: it is referenced more, and it is the
 * more chromatic. What decided it was `refs` — the count *scoped to the role each token had already
 * won* — where the brand's twenty references collapse to the four that are fills.</p>
 */
const CATALOGUE_SHEET = `
  :root{
    --color-emerald-500:oklch(69.6% .17 162.48);
    --vg-brand:#c2410c;--vg-background:#ffffff;--vg-foreground:oklch(14.5% 0 0);
    --vg-primary-foreground:oklch(98.5% 0 0)
  }
  body{background:var(--vg-background);color:var(--vg-foreground)}
  .btn{background-color:var(--vg-brand);color:var(--vg-primary-foreground)}
  .btn-lg{background-color:var(--vg-brand);color:var(--vg-primary-foreground)}
  a{color:var(--vg-brand)}
  .nav a{color:var(--vg-brand)}
  .footer a{color:var(--vg-brand)}
  .card h3{color:var(--vg-brand)}
  .lead strong{color:var(--vg-brand)}
  .tag{color:var(--vg-brand)}
  .mark{color:var(--vg-brand)}
  .step b{color:var(--vg-brand)}
  .quote cite{color:var(--vg-brand)}
  .footer h4{color:var(--vg-brand)}
  .field:focus{border-color:var(--vg-brand)}
  .icon{fill:var(--vg-brand)}
  .underline{text-decoration-color:var(--vg-brand)}
  .dot{background-color:var(--color-emerald-500)}
  .dot-sm{background-color:var(--color-emerald-500)}
  .badge-ok{background-color:var(--color-emerald-500)}
  .pill-live{background-color:var(--color-emerald-500)}
  .status-up{background-color:var(--color-emerald-500)}
  .chip-yes{background-color:var(--color-emerald-500)}
  .tick{background-color:var(--color-emerald-500)}
  .meter-fill{background-color:var(--color-emerald-500)}
  .gauge{background-color:var(--color-emerald-500)}
  .spark{background-color:var(--color-emerald-500)}
  .ok-band{background-color:var(--color-emerald-500)}
  .legend-ok{background-color:var(--color-emerald-500)}
`;

test("SH855: the brand wins the accent over a decoration the sheet fills more often", () => {
  const rows = themeFor(CATALOGUE_SHEET).rows;
  const brand = rows.find((row) => row.name === "--vg-brand");
  const emerald = rows.find((row) => row.name === "--color-emerald-500");

  // The measurement, before the ranking: both are admitted, and the count that used to order them
  // reads the wrong way round. Asserted rather than described, because it is the reason for the rule.
  assert.equal(brand.role, "accent");
  assert.equal(emerald.role, "accent");
  assert.ok(emerald.refs > brand.refs,
    `the decoration carries ${emerald.refs} fills and the brand ${brand.refs}, which is what the `
      + "generic ranking asked and is the whole defect: `refs` is scoped to the role the token had "
      + "already won, so it asks how often a colour is used AS what it was classified as");
  assert.ok(brand.refsTotal > emerald.refsTotal,
    `and across every role the brand is read ${brand.refsTotal} times against `
      + `${emerald.refsTotal} - the count SH855 says to rank by`);

  const { tokens, mapped } = vocabularyTokensFrom(rows);
  assert.equal(tokens["shio-color"].accent.$value, "#c2410c",
    "measured on the replica as `--shio-color-accent oklch(69.6% .17 162.48)` on a site whose every "
      + "button is orange - the fifth round the accent slot has come out wrong");
  assert.equal(mapped.find((row) => row.name === "shio-color.accent").from, "vg.brand");
});

test("SH855: the run names the signal that elected the accent, and only for the accent", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(CATALOGUE_SHEET).rows);
  const accent = mapped.find((row) => row.name === "shio-color.accent");

  // SH856 — the sheet is shadcn's own, so the button's label is `oklch(98.5% 0 0)` and that is the
  // page's white once this reader can convert it: the reversal is the signal that fires here, and
  // the pairing is what answers where a label is NOT the page's surface. Both are asserted, in the
  // test below and in this one, because which of the four fired is the thing being printed.
  assert.match(accent.elected, /^a rule that paints the page inside out, over color\.emerald\.500 /,
    "`accent#1` names a rank, and the rank was the whole claim - a reader given "
      + "`color.emerald.500 (accent#1)` has nothing to disagree with (P9)");
  assert.deepEqual(mapped.filter((row) => row.elected).map((row) => row.name),
    ["shio-color.accent"],
    "and the other eight slots carry no such clause: `text#1` and `background#2` ARE their own "
      + "evidence, and a constant clause on nine lines is the one everybody stops reading (P3)");
});

test("SH855: a pair is what a call-to-action states and a decoration does not", () => {
  // The structural fact, isolated: one rule states a fill and what goes on top of it, the other
  // states a fill. Neither is a reversal - the button's foreground is a token of its own, which is
  // the case SH848 measured and the reason a reversal could not reach it.
  const { reversals, paired } = pageStatement(declarationsOf(
    "body{background:#ffffff;color:#111111}"
      + ".btn{background-color:#c2410c;color:#3f3f46}"
      + ".dot{background-color:#059669}",
  ));

  assert.deepEqual([...reversals], [],
    "`#3f3f46` is a dark grey of the button's own, nowhere near the page's white, so the equality "
      + "never holds - and a near-white WOULD hold it, since `same` is SH846's 1.5:1 floor rather "
      + "than a string compare");
  assert.deepEqual([...paired], [".btn||"],
    "the pairing is the reversal with the equality dropped, which is what makes it readable on a "
      + "sheet whose buttons carry a foreground token of their own");
});

test("SH855: the pairing is computed where the page states no background, which a reversal cannot be", () => {
  // Every archetype in the corpus states no page-level background, so SH847's signal is structurally
  // silent on all of them. This is the half that still answers.
  const { surface, reversals, paired } = pageStatement(declarationsOf(
    ".btn{background-color:#c2410c;color:#ffffff}",
  ));

  assert.equal(surface, null);
  assert.deepEqual([...reversals], []);
  assert.deepEqual([...paired], [".btn||"]);
});

test("SH855: chroma ranks only where nothing else separates two candidates", () => {
  // Two fills, neither paired, read the same number of times. SH848 made chroma a floor, which is
  // the right shape for an admission bar and the wrong one for a ranking - before this it broke on
  // the value string, so which colour became the accent depended on its hex digits.
  const { mapped } = vocabularyTokensFrom(themeFor(`
    body{background:#ffffff;color:#111111}
    .a{background-color:#b45309}
    .b{background-color:#b45309}
    .c{background-color:#c2410c}
    .d{background-color:#c2410c}
  `).rows);

  assert.ok(chromaOf("#c2410c") > chromaOf("#b45309"));
  assert.equal(mapped.find((row) => row.name === "shio-color.accent").value, "#c2410c",
    `#c2410c reads ${chromaOf("#c2410c")} against ${chromaOf("#b45309")}, and sorting on the value `
      + "would have taken `#b45309` for no reason a reader could name - which is what the ranking "
      + "did before: two candidates the counts read alike were separated by their hex digits");
});

test("SH855: a reversal still outranks a pair, so SH847's reading is the one that stands", () => {
  const { mapped } = vocabularyTokensFrom(themeFor(`
    body{background:#ffffff;color:#111111}
    .cta{background-color:#0b7285;color:#ffffff}
    .badge{background-color:#c2410c;color:#3f3f46}
    .badge-2{background-color:#c2410c;color:#3f3f46}
    .badge-3{background-color:#c2410c;color:#3f3f46}
  `).rows);
  const accent = mapped.find((row) => row.name === "shio-color.accent");

  assert.equal(accent.value, "#0b7285",
    "the page turned inside out is the strongest reading there is, and it is stated once against a "
      + "paired fill stated three times - the order of the signals is the claim, not the counts");
  assert.match(accent.elected, /^a rule that paints the page inside out, /);
});

test("SH855: the pairing beats the count where the button's label is not the page's surface", () => {
  // The case the pairing exists for, and it is the commoner one: a button labelled in a dark grey of
  // its own is no reversal, however legible the pair is. Twelve fills against two, and the two win.
  const { mapped } = vocabularyTokensFrom(themeFor(`
    :root{--brand:#c2410c;--ok:#059669;--bg:#ffffff;--fg:#18181b;--label:#3f3f46}
    body{background:var(--bg);color:var(--fg)}
    .btn{background-color:var(--brand);color:var(--label)}
    .btn-lg{background-color:var(--brand);color:var(--label)}
    .d1,.d2,.d3{background-color:var(--ok)}
    .d4,.d5,.d6{background-color:var(--ok)}
    .d7,.d8,.d9{background-color:var(--ok)}
    .d10,.d11,.d12{background-color:var(--ok)}
  `).rows);
  const accent = mapped.find((row) => row.name === "shio-color.accent");

  assert.equal(accent.from, "brand");
  assert.match(accent.elected, /^a fill whose own rule states what goes on top of it, over ok #059669$/);
});

// --- SH856: the floor that could not read the floor ---

test("SH856: the pair the floor abstained on is the pair it now grades", () => {
  // The line the replica shipped, verbatim: `--shio-color-surface #ffffff on --shio-color-accent
  // oklch(69.6% .17 162.48) — contrast NOT checked`. Both halves of the failing pair, and the value
  // is Tailwind v4's own emerald-500, which is what `convert` had just elected and written.
  const ratio = contrastRatio("#ffffff", "oklch(69.6% .17 162.48)");

  assert.ok(ratio !== null, "a check written to spare a human from looking ended by asking one to "
    + "look, on every source built after about 2024 - oklch is what Tailwind v4 emits and what "
    + "shadcn ships");
  assert.equal(ratio.toFixed(2), "2.46");
  assert.ok(ratio >= 1.5, "and the answer is that the pair holds, which is the fact the abstention "
    + "was standing in for");
});

test("SH856: oklch and oklab convert to the sRGB a luminance can be taken of", () => {
  const rgb = (value) => {
    const each = parseColor(value);
    return each && [each.r, each.g, each.b].map((channel) => Math.round(channel));
  };

  assert.deepEqual(rgb("oklch(69.6% .17 162.48)"), [0, 188, 125],
    "Tailwind v4's emerald-500, clipped into sRGB - the wide-gamut value is outside it, and a hard "
      + "clip is what this takes rather than CSS's own gamut mapping, which is enough for a ratio");
  assert.deepEqual(rgb("oklch(97% 0 0)"), [245, 245, 245], "no chroma is a grey in any lightness");
  assert.deepEqual(rgb("oklch(20.5% 0 0)"), [23, 23, 23]);
  assert.deepEqual(rgb("oklab(0.696 -0.1622 0.0505)"), rgb("oklch(69.6% 0.17 162.5)"),
    "the polar and rectangular spellings of one colour, which is the same conversion one step apart");
  assert.deepEqual(rgb("oklch(70% 50% 40)"), rgb("oklch(70% 0.2 40)"),
    "a chroma stated as a percentage is relative to the space's own top (0.4), not to anything here");
  assert.deepEqual(rgb("oklch(70% 0.2 0.111turn)"), rgb("oklch(70% 0.2 40deg)"),
    "and a hue in any of the four units CSS spells one in");
});

test("SH856: a color-mix is resolved by mixing its sides, premultiplied and in the space it names", () => {
  const mixed = parseColor("color-mix(in oklab, #c2410c 50%, transparent)");

  assert.deepEqual([mixed.r, mixed.g, mixed.b].map(Math.round), [194, 65, 12],
    "the value SH848 wrote its role floor for. Un-premultiplied this is a half-step toward black - "
      + "`transparent` is a BLACK with no alpha - and the tint's own colour would be gone");
  assert.equal(mixed.a, 0.5);
  assert.equal(parseColor("color-mix(in srgb, #ff0000 50%, #0000ff)").r, 127.5,
    "and a mix in a space that is not one of the ok pair is interpolated in sRGB, which is what `in "
      + "srgb` asks for");
  assert.equal(parseColor("color-mix(in oklab, lab(50% 40 30) 50%, #ffffff)"), null,
    "either side unreadable is the whole value unreadable: a mix of a colour and something this "
      + "reader declines is not half-known");
});

test("SH856: hwb is the same wheel hsl walks, so it costs one line rather than a conversion", () => {
  const rgb = (value) => {
    const each = parseColor(value);
    return each && [each.r, each.g, each.b].map((channel) => Math.round(channel));
  };

  assert.deepEqual(rgb("hwb(20 10% 20%)"), [204, 85, 26]);
  assert.deepEqual(rgb("hwb(20 60% 60%)"), [128, 128, 128],
    "whiteness and blackness that sum past 1 are a grey, which is what CSS says of them");
});

test("SH856: the three that still abstain say so by the value, not by the space", () => {
  for (const value of ["lab(50% 40 30)", "lch(50% 40 30)", "color(display-p3 1 0 0)"]) {
    assert.equal(parseColor(value), null, `${value} is the boundary the rationale drew: CIE's pair `
      + "needs a D50 white point and an adaptation, and `color()` a table of profiles");
  }

  const { unchecked } = vocabularyTokensFrom([
    colorRow("vg.bg", "lab(98% 1 -4)", "background"),
    colorRow("vg.fg", "lch(20% 5 250)", "text"),
  ]);
  const page = unchecked.find((row) => row.text === "shio-color.ink");

  assert.deepEqual(page.unreadable, ["--shio-color-ink lch(20% 5 250)", "--shio-color-surface lab(98% 1 -4)"],
    "both, because a pair of unreadable values is two of them and a report naming one sends a "
      + "curator to fix half a pair - which is what `one of them` did");
});
