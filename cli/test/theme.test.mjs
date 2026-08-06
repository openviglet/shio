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
  buildTheme,
  customPropertyFor,
  declaredTokens,
  describeTheme,
  dtcgFor,
  flatten,
  pathForCustomProperty,
  recurringTokens,
  rewriteUrls,
  scanCss,
} from "../src/theme.mjs";

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
      !new RegExp(`${property}\s*:`).test(theme.css),
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
