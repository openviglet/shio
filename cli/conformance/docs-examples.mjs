/**
 * The published examples, extracted and counted (SH499, first deliverable).
 *
 * Block R wrote a documentation set full of copyable invocations. Nothing runs one, which
 * is exactly how the 2018 pages became wrong: written once, product moved, never compared.
 *
 * This module does the *measurement* the task asks for before the harness is designed:
 * walk `docs-shio/**`, find the fenced blocks that are executable claims about this
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

const DEFAULT_DOCS = "D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-shio";

/** A fenced block is executable when it makes a checkable claim about this instance. */
const CLAIMS = [
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

function fences(text) {
  const out = [];
  const re = /```([a-zA-Z]*)\n([\s\S]*?)```/g;
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

/** A table row's cells, without the leading and trailing pipe. */
function cells(line) {
  return line.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map((c) => c.trim());
}

export function extract(docsDir = DEFAULT_DOCS) {
  const blocks = [];
  let illustrative = 0;
  let tablesSkipped = 0;
  let rowsRelative = 0;
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
  return { blocks, illustrative, tablesSkipped, rowsRelative };
}

function main() {
  const args = process.argv.slice(2);
  const docsDir = args.find((a) => !a.startsWith("--")) ?? DEFAULT_DOCS;
  const { blocks, illustrative, tablesSkipped, rowsRelative } = extract(docsDir);

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
  process.stdout.write(`    ${"rows relative".padEnd(12)} ${String(rowsRelative).padStart(3)}  (a field named under a prefix stated elsewhere)\n\n`);

  process.stdout.write(`  Distinct assertable subjects: ${subjects.size}\n`);
  const files = new Set(blocks.map((b) => b.file));
  process.stdout.write(`  Across ${files.size} pages\n\n`);
}

if (process.argv[1] && process.argv[1].endsWith("docs-examples.mjs")) main();
