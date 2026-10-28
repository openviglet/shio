/**
 * The published examples, extracted and counted (SH499, first deliverable).
 *
 * Block R wrote a documentation set full of copyable invocations. Nothing runs one, which
 * is exactly how the 2018 pages became wrong: written once, product moved, never compared.
 *
 * This module does the *measurement* the task asks for before the harness is designed:
 * walk the documentation pages (`shio-site/docs/src/content/docs/**`), find the fenced blocks that are executable claims about this
 * product, classify them, and print the counts. The count is what decides whether the
 * eventual runner is a build gate or a periodic check.
 *
 * Deliberately dependency-free and read-only. It never touches an instance: extracting is
 * a different job from executing, and the extractor has to be trustworthy first.
 *
 * Usage:
 *   node cli/conformance/docs-examples.mjs [docsDir]           # counts
 *   node cli/conformance/docs-examples.mjs [docsDir] --json    # the blocks themselves
 */

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

import { DOCS_DIR } from "../../scripts/docs-location.mjs";

// @scans prose reason: it reads Markdown tables and fences on the published pages; SH663 exists
// because it was NOT reading the table half (SH740).

const DEFAULT_DOCS = DOCS_DIR;

/** A fenced block is executable when it makes a checkable claim about this instance. */
export const CLAIMS = [
  {
    kind: "cli",
    langs: ["bash", "sh", "shell", "console"],
    // `[ \t]*` and not `\s*`: `\s` matches `\n`, which overlaps the `(^|\n)` before it and makes
    // the pattern backtrack superlinearly over a long block. A shell indent is spaces or tabs.
    match: (body) => /(^|\n)[ \t]*(npx @viglet\/shio|shio)[ \t]+[a-z][a-z-]*/.test(body),
    /**
     * The verb is the assertable part: a renamed command is the failure this catches.
     *
     * SH499 — a verb starts with a letter. `[a-z-]+` also matched the `--help` in `shio --help`,
     * so the vocabulary check was handed a flag to look up in the command registry and reported a
     * miss that named the extractor's defect rather than the docs'.
     */
    subject: (body) =>
      [...body.matchAll(/(?:^|\n)[ \t]*(?:npx @viglet\/shio|shio)[ \t]+([a-z][a-z-]*)/g)]
        .map((m) => m[1]),
  },
  {
    kind: "http",
    langs: ["http"],
    match: (body) => /^(GET|POST|PUT|PATCH|DELETE)\s+\/\S/m.test(body),
    subject: (body) =>
      [...body.matchAll(/^(GET|POST|PUT|PATCH|DELETE)\s+(\S+)/gm)].map((m) => `${m[1]} ${m[2]}`),
  },
  {
    kind: "curl",
    langs: ["bash", "sh", "shell", "console"],
    match: (body) => /\bcurl\b/.test(body),
    subject: (body) => [...body.matchAll(/https?:\/\/\S+|\s\/(api|mcp|sites|preview)\S*/g)].map((m) => m[0].trim()),
  },
  {
    kind: "mcp",
    langs: ["json"],
    match: (body) => /"method"\s*:\s*"(tools|resources|prompts)\//.test(body),
    subject: (body) => [...body.matchAll(/"name"\s*:\s*"(shio_[a-z_]+)"/g)].map((m) => m[1]),
  },
  {
    kind: "payload",
    langs: ["json"],
    /** A request body: it asserts field names the server validates. */
    match: (body) => /"(ops|op|address|site|posts|folders|dryRun|confirm|multiselect)"\s*:/.test(body),
    subject: (body) => [...body.matchAll(/"op"\s*:\s*"([a-z.]+)"/g)].map((m) => m[1]),
  },
  {
    /**
     * SH1068 — a resource URI a page tells a client to read.
     *
     * Its own kind rather than another subject of `mcp`, because the two are checked against
     * different populations: a tool name is in `tools/list` and a URI is in `resources/list`, and
     * one test receiving both would have to guess which set to look in from the shape of a string.
     */
    kind: "resource",
    langs: ["json"],
    match: (body) => /"method"\s*:\s*"resources\//.test(body),
    subject: (body) => [...body.matchAll(/"uri"\s*:\s*"(shio:\/\/[^"]+)"/g)].map((m) => m[1]),
  },
  {
    /** SH1068 — the `verify --checks` groups a page names, which is a closed vocabulary. */
    kind: "checks",
    langs: ["bash", "sh", "shell", "console"],
    match: (body) => /--checks[= ][a-z]/.test(body),
    subject: (body) => [...body.matchAll(/--checks[= ]([a-z][a-z0-9,-]*)/g)]
      .flatMap((m) => m[1].split(",")).filter(Boolean),
  },
  {
    kind: "properties",
    langs: ["properties"],
    match: (body) => /^\s*(shio|spring|server|logging|management)\./m.test(body),
    subject: (body) => [...body.matchAll(/^\s*([a-z][a-z0-9.<>-]*)\s*=/gm)].map((m) => m[1]),
  },
];

/** Illustrative, not executable: a template, a tree drawing, a type declaration. */
const ILLUSTRATIVE = ["handlebars", "mermaid", "ts", "tsx", "typescript", "html", "css", ""];

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (/\.mdx?$/.test(name)) out.push(full);
  }
  return out;
}

/**
 * The fenced blocks, on either line ending (SH674).
 *
 * <h3>`\r?\n`, and it was worth a whole task</h3>
 *
 * <p>This required a bare `\n` after the language tag, so on a **CRLF** file the `\r` sat where
 * the pattern wanted the newline and nothing matched — not one block, on the whole page. Seven of
 * the thirty-three published pages are CRLF, including `agent-surface.md` and
 * `content-modeling.md`, and they were invisible here.</p>
 *
 * <p>What made it cost a task rather than a minute is that <b>it fails as an absence</b>. An
 * extractor that reads no block reports no subject, and a checker handed no subjects passes. So the
 * four discovery GETs `MUST_ANSWER_200` names read as *"no longer published — this list has
 * outlived its page"*, which is a sentence about the documentation, and the failure was filed
 * against the other repository twice: once as SH674 and once as an exception in `red-suites.json`
 * saying no change in this tree could turn it green. The pages had never stopped publishing them.</p>
 *
 * <p>The tell was available and nobody had reason to look for it: `blueprints.md` came back on its
 * own the moment an unrelated task edited it, because the editor wrote LF. A defect that repairs
 * itself when a file is touched for another reason is one that will be re-filed rather than found.</p>
 */
function fences(text) {
  const out = [];
  const re = /```([a-zA-Z]*)\r?\n([\s\S]*?)```/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    out.push({ lang: m[1].toLowerCase(), body: m[2], index: m.index });
  }
  return out;
}

function lineOf(text, index) {
  return text.slice(0, index).split("\n").length;
}

/**
 * SH663 — the property rows a Markdown table holds, which no fence contains.
 *
 * <h3>The page the property check exists for is the page it did not read</h3>
 *
 * <p>`CLAIMS`' `properties` entry takes a fenced block tagged `properties`. The configuration
 * reference does not have one: its whole content is a **table**, one row per key, so the page whose
 * entire subject is the property vocabulary contributed nothing to the count that checks it. Three
 * keys binding nothing shipped as published rows and stayed (SH656 removed them), and with the
 * vocabulary now correct a page naming a key that was never bound at all would still pass — the two
 * failures are independent, and only one of them was closed.</p>
 *
 * <h3>The narrow rule, because a cell is prose</h3>
 *
 * <p>Only the **first** cell of a row, and only in a table whose header names a property column. A
 * backticked token in a Description column is a citation — `shio.url` mentioned while explaining
 * another key — and reading it as a documented key would report the page for saying true things
 * about itself. The first cell is the only position where a table of this shape means *"this row is
 * about this key"*.</p>
 *
 * <p>A table that does not have that shape is <b>reported, never guessed at</b>: `tablesSkipped`
 * counts them and the printout names the number. A check that silently covered some tables and not
 * others would be worse than one covering none, because the count is what makes it readable.</p>
 */
export function propertyRows(text) {
  const rows = [];
  let skipped = 0;
  let relativeRows = 0;
  const lines = text.split("\n");
  for (let at = 0; at < lines.length; at += 1) {
    if (!lines[at].trimStart().startsWith("|")) continue;
    const header = cells(lines[at]);
    const divider = lines[at + 1] ?? "";
    // A table is a header, a `|---|` divider, then rows. Without the divider this is a line that
    // merely begins with a pipe, which is a thing prose does.
    if (!divider.trimStart().startsWith("|") || !/^[\s|:-]+$/.test(divider)) continue;

    const start = at;
    let body = at + 2;
    const gathered = [];
    while (body < lines.length && lines[body].trimStart().startsWith("|")) {
      gathered.push(lines[body]);
      body += 1;
    }
    at = body - 1;

    if (!/^(property|key|setting)$/i.test((header[0] ?? "").replace(/[*`]/g, "").trim())) {
      // Only tables that carry a key somewhere are worth reporting as skipped: a comparison matrix
      // is not a property table that got away, it is a different thing entirely.
      if (gathered.some((line) => /`shio\.[a-z]/i.test(line))) skipped += 1;
      continue;
    }
    for (const line of gathered) {
      const first = cells(line)[0] ?? "";
      for (const match of first.matchAll(/`([a-z][a-z0-9.<>_-]*)`/gi)) {
        // A key is dotted. A bare word in a property column is a field named RELATIVE to a prefix
        // the page states nearby — `search-caching.md` lists `max-width` under a fenced block that
        // spells `shio.image-transform.max-width` three lines above — and this reader cannot join
        // the two. Guessing the prefix would either miss or fabricate a key, so the row is declined
        // and counted instead.
        if (!match[1].includes(".")) {
          relativeRows += 1;
          continue;
        }
        rows.push({ key: match[1], line: start + 1 });
      }
    }
  }
  return { rows, skipped, relativeRows };
}

/**
 * SH697 — the MCP tool names a Markdown table holds, which no fence contains.
 *
 * <h3>The same defect as SH663, one vocabulary over</h3>
 *
 * <p>`CLAIMS`' `mcp` entry takes a JSON fence carrying a `tools/call`. `mcp.md` has exactly one,
 * so of the twelve tools the page publishes, **one** was ever held against the product — and the
 * *table*, which is the thing a reader consults, was unread. Measured: `shio_nonexistent` dropped
 * into that table left the check green, while a bad property key on the same page turned SH663's
 * red.</p>
 *
 * <p>What it cost: the page published `shio_blueprint` and `shio_forget` as tools. Neither exists,
 * and neither is an oversight — `ShApplyTool` and `ShRememberTool` both record the decision to make
 * that capability a *parameter* rather than pay for a schema on every turn. Two rejected designs
 * were published as shipped surface, and `shio_marketplace` shipped without ever reaching the page.
 * The root README never drifted this way because `readme-surface.test.mjs` holds its list against
 * `mcpTools()`; the published documentation was the surface with no instrument.</p>
 *
 * <h3>The narrow rule, for SH663's reason</h3>
 *
 * <p>Only the **first** cell, and only in a table whose header names a tool column. A `shio_apply`
 * cited in a *What it does* column is prose about another row — and the page now carries a
 * paragraph naming `shio_blueprint` and `shio_forget` precisely to say they are **not** tools,
 * which a looser reader would report as the defect it is explaining.</p>
 */
export function toolRows(text) {
  const rows = [];
  let skipped = 0;
  const lines = text.split("\n");
  for (let at = 0; at < lines.length; at += 1) {
    if (!lines[at].trimStart().startsWith("|")) continue;
    const header = cells(lines[at]);
    const divider = lines[at + 1] ?? "";
    if (!divider.trimStart().startsWith("|") || !/^[\s|:-]+$/.test(divider)) continue;

    const start = at;
    let body = at + 2;
    const gathered = [];
    while (body < lines.length && lines[body].trimStart().startsWith("|")) {
      gathered.push(lines[body]);
      body += 1;
    }
    at = body - 1;

    if (!/^tools?$/i.test((header[0] ?? "").replace(/[*`]/g, "").trim())) {
      // Reported, never guessed at: a table naming tools under some other header is a thing this
      // reader declined, and the printed count is what stops that being invisible.
      if (gathered.some((line) => /`shio_[a-z]/i.test(line))) skipped += 1;
      continue;
    }
    for (const line of gathered) {
      const first = cells(line)[0] ?? "";
      for (const match of first.matchAll(/`(shio_[a-z_]+)`/g)) {
        rows.push({ name: match[1], line: start + 1 });
      }
    }
  }
  return { rows, skipped };
}

/** A table row's cells, without the leading and trailing pipe. */
function cells(line) {
  return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
}

export function extract(docsDir = DEFAULT_DOCS) {
  const blocks = [];
  let illustrative = 0;
  let tablesSkipped = 0;
  let rowsRelative = 0;
  let toolTablesSkipped = 0;
  for (const file of walk(docsDir)) {
    const text = readFileSync(file, "utf8");
    // SH663 — the table rows first, so a page whose properties are documented as a table is read at
    // all. Emitted as ordinary `properties` blocks: every consumer already holds `subjects` against
    // the product's vocabulary, and a second kind would be a second thing to remember to check.
    const { rows, skipped, relativeRows } = propertyRows(text);
    tablesSkipped += skipped;
    rowsRelative += relativeRows;
    const byLine = new Map();
    for (const row of rows) {
      if (!byLine.has(row.line)) byLine.set(row.line, []);
      byLine.get(row.line).push(row.key);
    }
    for (const [line, keys] of byLine) {
      blocks.push({
        file: relative(docsDir, file).replace(/\\/g, "/"),
        line,
        kind: "properties",
        from: "table",
        subjects: [...new Set(keys)],
        body: keys.join("\n"),
      });
    }
    // SH697 — the tool table, emitted as an ordinary `mcp` block for SH663's stated reason: every
    // consumer already holds `subjects` against the product, and a second kind would be a second
    // thing somebody has to remember to check.
    const tools = toolRows(text);
    toolTablesSkipped += tools.skipped;
    const toolsByLine = new Map();
    for (const row of tools.rows) {
      if (!toolsByLine.has(row.line)) toolsByLine.set(row.line, []);
      toolsByLine.get(row.line).push(row.name);
    }
    for (const [line, names] of toolsByLine) {
      blocks.push({
        file: relative(docsDir, file).replace(/\\/g, "/"),
        line,
        kind: "mcp",
        from: "table",
        subjects: [...new Set(names)],
        body: names.join("\n"),
      });
    }
    for (const fence of fences(text)) {
      const claim = CLAIMS.find(
        (c) => c.langs.includes(fence.lang) && c.match(fence.body),
      );
      if (!claim) {
        if (ILLUSTRATIVE.includes(fence.lang)) illustrative += 1;
        continue;
      }
      blocks.push({
        file: relative(docsDir, file).replace(/\\/g, "/"),
        line: lineOf(text, fence.index),
        kind: claim.kind,
        subjects: [...new Set(claim.subject(fence.body))],
        body: fence.body.trim(),
      });
    }
  }
  return { blocks, illustrative, tablesSkipped, rowsRelative, toolTablesSkipped };
}

function main() {
  const args = process.argv.slice(2);
  const docsDir = args.find((a) => !a.startsWith("--")) ?? DEFAULT_DOCS;
  const { blocks, illustrative, tablesSkipped, rowsRelative, toolTablesSkipped } = extract(docsDir);

  if (args.includes("--json")) {
    process.stdout.write(`${JSON.stringify(blocks, null, 2)}\n`);
    return;
  }

  const byKind = new Map();
  const subjects = new Map();
  for (const b of blocks) {
    byKind.set(b.kind, (byKind.get(b.kind) ?? 0) + 1);
    for (const s of b.subjects) subjects.set(s, (subjects.get(s) ?? 0) + 1);
  }

  process.stdout.write(`\n  Executable blocks in ${docsDir}\n\n`);
  for (const [kind, n] of [...byKind].sort((a, b) => b[1] - a[1])) {
    process.stdout.write(`    ${kind.padEnd(12)} ${String(n).padStart(3)}\n`);
  }
  process.stdout.write(`    ${"-".repeat(16)}\n`);
  process.stdout.write(`    ${"total".padEnd(12)} ${String(blocks.length).padStart(3)}\n`);
  process.stdout.write(`    ${"illustrative".padEnd(12)} ${String(illustrative).padStart(3)}  (not executable by design)\n`);
  // SH663 — named rather than silent. A table holding `shio.*` keys under no property column is
  // coverage this reader does not have, and a count that omitted it would read as completeness.
  process.stdout.write(`    ${"tables past".padEnd(12)} ${String(tablesSkipped).padStart(3)}  (hold a shio.* key under no property column)\n`);
  process.stdout.write(`    ${"rows relative".padEnd(12)} ${String(rowsRelative).padStart(3)}  (a field named under a prefix stated elsewhere)\n`);
  // SH697 — the same courtesy for the tool tables, and for the same reason.
  process.stdout.write(`    ${"tool tables".padEnd(12)} ${String(toolTablesSkipped).padStart(3)}  (name a shio_ tool under no tool column)\n\n`);

  process.stdout.write(`  Distinct assertable subjects: ${subjects.size}\n`);
  const files = new Set(blocks.map((b) => b.file));
  process.stdout.write(`  Across ${files.size} pages\n\n`);
}

if (process.argv[1] && process.argv[1].endsWith("docs-examples.mjs")) main();
