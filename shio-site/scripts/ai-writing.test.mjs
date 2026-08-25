// @scans prose reason: the copy is the subject — it reads each source whole, comments included,
// because a literal-extractor is a heuristic that can be wrong in the direction of passing (SH740).
//
// SH925 — the AI-writing pass, as a lint over the site's own prose.
//
// The site has one editing rule — every claim is about a real surface — and until this file
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
//     silence afterwards — invisible to a reader, to `tsc` and to every other suite. Five were
//     wrong when this file was written: the matrix said seventeen questions and had eighteen
//     rows, the projection page said ten keys and the CLI carries eleven, the manifest figures
//     were a measurement from three tasks before the ceiling moved, `llms.txt` renamed a design
//     law, and the Markdown size pair was typed twice and disagreed with itself and with the
//     build. Each rule below reads the claim back against the surface it is about.
//
// Source lints, like `site-claims.test.mjs` beside them: properties of the repository rather
// than of a rendered DOM.

import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, "..");
const repo = join(root, "..");

const read = (path) => readFileSync(join(root, path), "utf-8");

/** Every file that carries prose a reader will see. Comments included — see below. */
function proseFiles() {
  const walk = (dir) =>
    readdirSync(join(root, dir), { withFileTypes: true }).flatMap((e) =>
      e.isDirectory()
        ? walk(join(dir, e.name))
        : /\.tsx?$/.test(e.name)
          ? [join(dir, e.name)]
          : [],
    );
  return [
    ...walk("src/pages"),
    ...walk("src/components/sections"),
    "src/components/hero-sessions.tsx",
    "src/lib/site-content.ts",
    "src/lib/feature-content.ts",
    "src/entry-server.tsx",
    "public/llms.txt",
  ].map((path) => ({ path: path.replaceAll("\\", "/"), text: read(path) }));
}

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
  [/\p{Extended_Pictographic}/u, "an emoji — the copy's own marks are box-drawing, never these"],
];

/**
 * Section B, reduced to what a grep may decide alone: the phrases that are a tell on their own
 * rather than by density. The judgement calls — the rule of three, negative parallelism, em
 * dashes — are deliberately absent. Em dashes are this site's house style and a lint against
 * them would rewrite the voice; a lint that fires on good writing gets switched off.
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
  /\b(boasts|nestled|vibrant|unwavering|meticulous|garner|bolster|showcase)\b/i,
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
          + "checklist — delete it, and if it stood in for a citation, say what is missing.",
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
  // Two places spell it — the page heading and the route's meta description — and neither is
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
  // fails the CLI's own suite when it changes — so the count is knowable here without guessing.
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
    const stated = new RegExp(`^- ${id} ([^—]+?)\\s*—`, "m").exec(LLMS);
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
  // disagreed with what the build was producing. The prerender is the only place that knows —
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
