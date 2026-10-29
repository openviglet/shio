// @scans prose reason: the copy is the subject; it reads each source whole, comments included,
// because a literal-extractor is a heuristic that can be wrong in the direction of passing (SH740).
//
// SH925: the AI-writing pass, as a lint over the site's own prose.
//
// The site has one editing rule (every claim is about a real surface), and until this file
// nothing read the copy at all. Two different things go wrong in prose, and only one of them
// is greppable:
//
//   * **Mannerisms.** The puffery vocabulary and the copy-paste artefacts an LLM leaves behind.
//     A phrase list catches those, and the list here is the one the `ai-writing` skill carries.
//     It is not the whole pass: density, inflation and an unsupported claim are judgement, and
//     the skill is what a person still has to run before handing copy over. What a grep can do
//     is make sure the cheap half never has to be remembered.
//
//   * **Counts.** A number typed into a sentence is true the day it is typed and wrong in
//     silence afterwards, invisible to a reader, to `tsc` and to every other suite. Five were
//     wrong when this file was written: the matrix said seventeen questions and had eighteen
//     rows, the projection page said ten keys and the CLI carries eleven, the manifest figures
//     were a measurement from three tasks before the ceiling moved, `llms.txt` renamed a design
//     law, and the Markdown size pair was typed twice and disagreed with itself and with the
//     build. Each rule below reads the claim back against the surface it is about.
//
//   * **The em dash.** SH929, and the one rule here that is a house-style decision rather than a
//     defect: this site does not use the mark at all. SH925 wrote the opposite into this file
//     ("em dashes are house style and a lint against them would rewrite the voice"), and that was
//     the wrong call. It is the loudest tell in machine-written English, and a page whose whole
//     argument is that an agent built the site cannot afford to read as though one wrote the copy.
//     262 of them were in the published prose when the rule landed.
//
// Source lints, like `site-claims.test.mjs` beside them: properties of the repository rather
// than of a rendered DOM.

import assert from "node:assert/strict";
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

// The one stripper this repository has, rather than a second one (SH740 owns the question of
// what counts as code). The em-dash rule is the only one here that needs it: a mark in a note
// between contributors is not something a reader is handed, and holding those to a rule about
// published copy would churn a hundred comments for nobody.
import { code } from "../../cli/test/support/source-text.mjs";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const repo = join(root, "..");

const read = (path) => readFileSync(join(root, path), "utf-8");

/** Every file that carries prose a reader will see. Comments included; see below. */
function proseFiles() {
  const walk = (dir) =>
    readdirSync(join(root, dir), { withFileTypes: true }).flatMap((e) =>
      e.isDirectory()
        ? walk(join(dir, e.name))
        : /\.tsx?$/.test(e.name)
          ? [join(dir, e.name)]
          : [],
    );
  return [...walk("src"), ...PAGES, ...SCRIPTS].map((path) => ({
    path: path.replaceAll("\\", "/"),
    // What the phrase list reads. A page's comments are prose somebody reads, so they are in;
    // a build script's are notes about an instrument, and holding those to a rule about
    // marketing vocabulary reports "robust against a bleeding-edge Vite" as puffery.
    text: SCRIPTS.includes(path) ? code(read(path)) : read(path),
    // What the em-dash rule reads. A flat file is prose end to end; a module is prose only
    // where it is not a comment, plus every code sample it carries (see `emitted`).
    published: /\.(txt|svg|html)$/.test(path) ? read(path) : emitted(read(path)),
  }));
}

/**
 * A module's published half: its code, plus the body of every template literal in it.
 *
 * <p>`code()` is line-oriented and drops any line starting with `//`, which is right for a
 * comment and wrong for a **code sample**: `delivery.tsx` renders a snippet whose third line is
 * `// Zero runtime dependencies ...`, and that line is on the landing page. The source scan came
 * back clean while `dist/index.html` still carried the mark, which is how the gap was found.</p>
 *
 * <p>Adding the template bodies back is stateless, which is SH630's rule, and conservative in the
 * safe direction: a stray backtick can only pair wrongly and hand the scan *more* text, never
 * less. Measured at zero false positives across this package.</p>
 */
function emitted(body) {
  const templates = body.match(/`[^`]*`/g) ?? [];
  return `${code(body)}\n${templates.join("\n")}`;
}

/**
 * The published surfaces that are not under `src/`, named one by one rather than walked.
 *
 * <p>SH929 found four of them the hard way, by grepping `dist/` after the source scan came back
 * clean. The template's `og:description` reaches every page (the prerender patches the title and
 * the description meta, and not that one), the social card is an SVG with text in it, and three
 * build scripts write strings straight onto the page: the manifest's `startHere`, the four notes
 * under the token chart, and the line each marketplace README is built from.</p>
 *
 * <p>Named rather than walked because the rest of `scripts/` is instruments, and a test file that
 * has to quote the mark it forbids is not a page anybody reads.</p>
 *
 * <p><b>`public/marketplace/**` is deliberately outside all of this.</b> It is generated from
 * `shio-app/src/main/resources/blueprints/**` and republishes those bytes rather than restating
 * them, which is the whole design of that channel, so the copy is not this page's to rewrite.</p>
 *
 * <p><b>SH930 decided how far the em-dash rule reaches, and the answer split the sixteen.</b> Six
 * were sample content, which becomes a page the user publishes: those are prose handed to a reader
 * and they were rewritten, with `SH930: sample content …` below holding the tree so a seventh
 * cannot arrive with the next blueprint. Ten are field help and blueprint summaries, which are
 * product chrome a curator reads in the console; they stay, alongside the 38 of the same kind in
 * the console's own bundles. The reasoning is at that test.</p>
 */
const PAGES = ["public/llms.txt", "public/og.svg", "index.html"];
const SCRIPTS = [
  "scripts/prerender.mjs",
  "scripts/token-budgets.mjs",
  "scripts/marketplace.mjs",
];

/*
 * The whole file is scanned, comments and all, rather than the string literals picked out of
 * it. Two reasons, and the second is the one that decided it: a literal-extractor is a
 * heuristic that can be wrong in the direction of passing, and the engineering comments in
 * these files are prose somebody reads too. Nothing on the list below belongs in either.
 */

/** Section A of the checklist: copy-paste artefacts. A hit is proof a tool touched the text. */
const ARTEFACTS = [
  [/:?contentReference|oaicite:/, "an OpenAI citation artefact"],
  [/turn\d+(search|image|file|news)\d+/, "a broken OpenAI source link"],
  [/\[cite:\s*\d/, "a Gemini citation artefact"],
  [/<grok-card|grok_render_citation_card_json/, "a Grok citation artefact"],
  [/utm_source=(chatgpt\.com|openai|copilot\.com)|referrer=grok\.com/, "an AI-tool tracking parameter"],
  [/:::(writing|écriture)\b/, "a chat-authoring wrapper"],
  [/\b(As an AI language model|up to my last knowledge update)\b/i, "refusal boilerplate"],
  [/\b(I hope this helps|Let me know if|Here'?s a draft|Would you like me to)\b/i, "chat framing"],
  [/\[(Your Name|INSERT_[A-Z_]+|PASTE_[A-Z_]+)\]/, "an unfilled placeholder"],
  [/\p{Extended_Pictographic}/u, "an emoji: the copy's own marks are box-drawing, never these"],
];

/**
 * Section B, reduced to what a grep may decide alone: the phrases that are a tell on their own
 * rather than by density. The judgement calls (the rule of three, negative parallelism) are
 * deliberately absent, because a lint that fires on good writing gets switched off.
 *
 * The em dash was in that sentence until SH929 and is now its own rule below, which is a
 * different kind of thing: not a tell to weigh, a mark this site does not use.
 */
const MANNERISMS = [
  /\b(serves|stands|functions) as an? /i,
  /\bplays? an? (pivotal|crucial|vital|key) role/i,
  /\ba testament to\b/i,
  /\brich tapestry\b/i,
  /\bdelve into\b/i,
  /\bunderscor(es|ing) the\b/i,
  /\b(it'?s|it is) (important to note|worth noting)\b/i,
  /\bkeep in mind that\b/i,
  /\bin the heart of\b/i,
  /\b(boasts|nestled|vibrant|unwavering|meticulous|garner|bolster)\b/i,
  // SH1258: "the showcase" is a name here (the Quilhara package's catalogue title, the
  // /showcase route, the SH1250 matrix), so the noun is the right word. The verb is the
  // tell, and it stays caught.
  /\b(to showcase|showcases|showcased)\b/i,
  /\b(pivotal|crucial|intricate|interplay|realm of|tapestry)\b/i,
  /\b(seamless|seamlessly|foster|leverage|robust|holistic)\b/i,
  /\b(cutting-edge|state-of-the-art|game-chang|revolutioniz|empower|unleash|supercharge)/i,
  /\bnot only\b[^.]{0,60}\bbut also\b/i,
  /^\s*(In summary|In conclusion|Overall|Ultimately)[,:]/im,
  /,\s+(ensuring|highlighting|reflecting|cementing|showcasing|underscoring)\b/i,
  /\b(experts|observers|critics|industry reports) (argue|have noted|say)\b/i,
  /\bwhile specific details are (limited|scarce)\b/i,
  /\bcommitment to (excellence|quality|innovation)\b/i,
  /\ba (wide range|diverse array) of\b/i,
];

test("SH925: no copy-paste artefact reaches the published copy", () => {
  for (const { path, text } of proseFiles()) {
    for (const [pattern, what] of ARTEFACTS) {
      const hit = pattern.exec(text);
      assert.equal(
        hit,
        null,
        `${path} carries ${what}: ${JSON.stringify(hit?.[0])}. Section A of the AI-writing `
          + "checklist: delete it, and if it stood in for a citation, say what is missing.",
      );
    }
  }
});

test("SH925: the copy is free of the phrases that are a tell on their own", () => {
  const found = [];
  for (const { path, text } of proseFiles()) {
    for (const pattern of MANNERISMS) {
      const hit = pattern.exec(text);
      if (hit) found.push(`${path}: ${JSON.stringify(hit[0])}`);
    }
  }
  assert.deepEqual(
    found,
    [],
    "the AI-writing checklist's Section B, on the phrases a grep may judge alone. The fix is "
      + "never a synonym: say the specific thing, or cut the sentence. If a hit is genuinely "
      + `the right word here, say so in this file rather than working around it.\n  ${found.join("\n  ")}`,
  );
});

/**
 * SH929: the em dash, in both spellings a page can carry.
 *
 * No en dash on the list, and that is a measurement rather than an oversight: there were none in
 * the copy, so the rule is one character in four spellings instead of a judgement about numeric
 * ranges. Reported with the line, all of them at once, because the fix is a reading pass and a
 * lint that surfaces one occurrence per run turns that into 262 runs.
 *
 * Written as an escape rather than as the character, so this file can be scanned by itself and
 * so the numeric entities are visible: `&#8212;` was the fourth spelling, and it was the one in
 * the social card, where nothing else was looking.
 */
const EM_DASH = new RegExp(["\\u2014", "&mdash;", "&#8212;", "&#x2014;"].join("|"), "i");

test("SH929: the published copy carries no em dash", () => {
  const found = [];
  for (const { path, published } of proseFiles()) {
    for (const [index, line] of published.split("\n").entries()) {
      if (EM_DASH.test(line)) found.push(`  ${path}: ${line.trim().slice(0, 96)}`);
    }
  }
  assert.deepEqual(
    found.length,
    0,
    "SH929: this site does not use the em dash. It is the loudest tell in machine-written "
      + "English, and the page argues that an agent built the site.\n\nThe replacement is not a "
      + "comma. The mark is standing in for a parenthesis, a colon, a semicolon or a full stop, "
      + "and it hides which, so read the sentence, write what it was asking for, and cut the "
      + `aside where it was carrying nothing.\n\n${found.length} occurrence(s):\n${found.join("\n")}`,
  );
});

/**
 * SH930: the rule follows the prose to its reader, and stops where the product begins.
 *
 * <p>`public/marketplace/**` republishes `shio-app/src/main/resources/blueprints/**` byte for byte,
 * so the em dashes the catalogue serves are not the site's to rewrite. SH930 was filed to decide
 * whether the rule reaches them, with two answers offered: published prose everywhere this project
 * writes, or the site alone. Reading the sixteen split them, and the split is the decision.</p>
 *
 * <ul>
 *   <li><b>Sample content</b> (`content/*.json`) becomes a page the user publishes. "Replace it,
 *       or delete it, once you have something to say" (set with em dashes at the time) is prose Shio hands a reader on somebody
 *       else's marketing site, which is exactly what the rule is about. Six of them; all rewritten,
 *       and this test is what keeps a seventh from arriving with the next blueprint.</li>
 *   <li><b>Field help and blueprint summaries</b> (`post-types/*.json`, `blueprint.json`) are
 *       product chrome a curator reads inside the console. Ten of them, and they stay. The console's
 *       own bundles carry <b>38</b> more of the same kind: rewriting ten while the curator meets
 *       thirty-eight on the surrounding screen makes the product less consistent, not more, and the
 *       task's own phrase is "published prose", which UI labels are not.</li>
 * </ul>
 *
 * <p>So the exception above narrows rather than disappearing. If the console's strings are ever
 * taken, these ten go in that pass and this note is where to start.</p>
 */
const BLUEPRINT_CONTENT = join(repo, "shio-app", "src", "main", "resources", "blueprints");
/** SH1254 — packages published from `marketplace/` publish sample content too. */
const MARKETPLACE_CONTENT = join(repo, "marketplace");

function sampleContentFiles() {
  const out = [];
  for (const root of [BLUEPRINT_CONTENT, MARKETPLACE_CONTENT]) {
    for (const name of readdirSync(root)) {
      const dir = join(root, name, "content");
      if (!existsSync(dir)) continue;
      for (const file of readdirSync(dir)) {
        if (file.endsWith(".json")) {
          out.push({ path: `${relative(repo, dir)}/${file}`, body: readFileSync(join(dir, file), "utf8") });
        }
      }
    }
  }
  return out;
}

test("SH930: sample content a blueprint publishes carries no em dash", () => {
  const files = sampleContentFiles();

  // Never green on nothing: this reaches across modules, and a moved blueprints tree would report
  // a clean sweep of zero files, which is the shape every held list here is written against.
  assert.ok(files.length >= 3,
    `SH930: only ${files.length} blueprint content file(s) found under ${BLUEPRINT_CONTENT}. The `
      + "tree moved and this reader is scanning nothing, which reads exactly like a clean result.");

  const found = [];
  for (const { path, body } of files) {
    for (const line of body.split("\n")) {
      if (EM_DASH.test(line)) found.push(`  ${path}: ${line.trim().slice(0, 96)}`);
    }
  }

  assert.deepEqual(found.length, 0,
    "SH930: a blueprint's sample content carries an em dash. This is not console text: it becomes "
      + "a published page on the site of whoever applied the blueprint, so it is prose Shio hands "
      + "a reader.\n\nField help and blueprint summaries are deliberately NOT covered by this rule "
      + "(see the note above, and the 38 of the same kind in the console's own bundles).\n\n"
      + `${found.length} occurrence(s):\n${found.join("\n")}`);
});

/* ── The counts, each read back from the surface it describes ──────────────────────────── */

const SITE_CONTENT = read("src/lib/site-content.ts");
const FEATURE_CONTENT = read("src/lib/feature-content.ts");
const ENTRY_SERVER = read("src/entry-server.tsx");
const LLMS = read("public/llms.txt");

const NUMBER_WORDS = [
  "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
  "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
  "nineteen", "twenty",
];
const spell = (n) => NUMBER_WORDS[n] ?? String(n);

test("SH925: the matrix's question count is the number of rows in it", () => {
  // Two places spell it (the page heading and the route's meta description), and neither is
  // near the table. It said seventeen for as long as the matrix had eighteen rows.
  const groups = SITE_CONTENT.slice(
    SITE_CONTENT.indexOf("export const COMPARE_GROUPS"),
    SITE_CONTENT.indexOf("export interface Rival"),
  );
  const rows = (groups.match(/\bfeature:\s*"/g) ?? []).length;
  assert.ok(rows > 10, `expected the comparison matrix to have rows; found ${rows}`);

  // Matched loosely and compared as a word, so a wrong count is reported as the two numbers
  // rather than as a regex that did not match a whole file.
  const heading = /([a-z]+) questions</i.exec(read("src/pages/compare.tsx"));
  assert.equal(
    heading?.[1]?.toLowerCase(),
    spell(rows),
    `/compare's heading counts the matrix's questions and the matrix has ${rows} rows`,
  );

  const meta = /([a-z]+) checkable questions/i.exec(ENTRY_SERVER);
  assert.equal(
    meta?.[1]?.toLowerCase(),
    spell(rows),
    `the /compare route's meta description counts the same rows, and the matrix has ${rows}`,
  );
});

test("SH925: the projection page's key count is the CLI's projected key set", () => {
  // `content-projection-coverage.test.mjs` holds the list the projection actually carries, and
  // fails the CLI's own suite when it changes, so the count is knowable here without guessing.
  const coverage = readFileSync(
    join(repo, "cli", "test", "content-projection-coverage.test.mjs"),
    "utf-8",
  );
  const projected = coverage.slice(
    coverage.indexOf("const PROJECTED = ["),
    coverage.indexOf("];", coverage.indexOf("const PROJECTED = [")),
  );
  const keys = (projected.match(/"[a-zA-Z]+"/g) ?? []).length;
  assert.ok(keys > 5, `expected the CLI to declare its projected keys; found ${keys}`);

  const stated = /([a-z]+) keys travel/i.exec(FEATURE_CONTENT);
  assert.equal(
    stated?.[1]?.toLowerCase(),
    spell(keys),
    `/features/content-as-files says "N keys travel"; the projection carries ${keys}. `
      + "The count moved when SH348 added one, and nothing on this side noticed.",
  );
});

test("SH925: a token figure in the copy comes from the generated budgets", () => {
  // SH533's generator covers the four bars on the chart. The two figures inside prose were
  // literals, and both had drifted: 298 and 2343 were measured at SH174, and the ceilings the
  // build now enforces are larger. `ceiling()` is the only way a number gets into a sentence.
  for (const [path, text] of [
    ["src/lib/site-content.ts", SITE_CONTENT],
    ["src/lib/feature-content.ts", FEATURE_CONTENT],
  ]) {
    const typed = text.match(/\b\d{3,5}\s*tokens\b/g) ?? [];
    assert.deepEqual(
      typed,
      [],
      `${path} types a token figure (${typed.join(", ")}). Read it with ceiling() from `
        + "./token-budgets instead, so the page and the build cannot hold two numbers.",
    );
    assert.ok(
      /ceiling\("response\.manifest/.test(text),
      `${path} should quote the enforced ceiling rather than a measurement`,
    );
  }
});

test("SH925: llms.txt states the ten design laws by the names the site gives them", () => {
  // The agent-readable file is the copy nobody re-reads, which is how P7 came to be "Never
  // surprise the human" there and "Never surprise the curator" everywhere else. A renamed law
  // is the drift with the widest blast radius: agents.md, the site and the docs all cite them.
  const laws = [...SITE_CONTENT.matchAll(/id: "(P\d+)",\s*\n\s*title: "([^"]+)"/g)];
  assert.equal(laws.length, 10, `LAWS should carry the ten design laws; found ${laws.length}`);

  for (const [, id, title] of laws) {
    const stated = new RegExp(`^- ${id} ([^:]+?)\\s*:`, "m").exec(LLMS);
    assert.ok(stated, `llms.txt does not state ${id}`);
    assert.equal(
      stated[1],
      title,
      `llms.txt calls ${id} "${stated[1]}" and site-content.ts calls it "${title}"`,
    );
  }
});

test("SH925: the Markdown size pair is measured by the prerender, not typed", () => {
  // It was typed in llms.txt and again in the README, the two already disagreed, and both
  // disagreed with what the build was producing. The prerender is the only place that knows:
  // the figure exists only after every route has been written.
  const prerender = read("scripts/prerender.mjs");
  assert.ok(
    /replaceOrThrow\(\s*readFileSync\(llmsPath/.test(prerender),
    "the prerender patches the pair into dist/llms.txt with replaceOrThrow, so a sentence "
      + "that drifts fails the build instead of publishing a stale figure",
  );
  assert.ok(
    /KB of Markdown against \$\{Math\.round\(htmlBytes/.test(prerender),
    "and the figures it writes are the bytes it just wrote, measured after every route",
  );
  assert.ok(
    /\d+ KB of Markdown against \d+ KB of HTML/.test(LLMS),
    "llms.txt has to keep the sentence in the shape the prerender looks for",
  );

  // The README carried a second copy of the same measurement. One typed pair is a number that
  // goes stale; two are a pair that can disagree with each other, which is what happened.
  assert.ok(
    !/\d+ KB of Markdown/.test(read("README.md")),
    `${relative(repo, join(root, "README.md")).replaceAll("\\", "/")} should point at the `
      + "build's own line rather than restating the figure",
  );
});
