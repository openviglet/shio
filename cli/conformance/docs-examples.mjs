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
    match: (body) => /(^|\n)\s*(npx @viglet\/shio|shio)\s+[a-z-]+/.test(body),
    /** The verb is the assertable part: a renamed command is the failure this catches. */
    subject: (body) =>
      [...body.matchAll(/(?:^|\n)\s*(?:npx @viglet\/shio|shio)\s+([a-z-]+)/g)].map((m) => m[1]),
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

export function extract(docsDir = DEFAULT_DOCS) {
  const blocks = [];
  let illustrative = 0;
  for (const file of walk(docsDir)) {
    const text = readFileSync(file, "utf8");
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
  return { blocks, illustrative };
}

function main() {
  const args = process.argv.slice(2);
  const docsDir = args.find((a) => !a.startsWith("--")) ?? DEFAULT_DOCS;
  const { blocks, illustrative } = extract(docsDir);

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
  process.stdout.write(`    ${"illustrative".padEnd(12)} ${String(illustrative).padStart(3)}  (not executable by design)\n\n`);

  process.stdout.write(`  Distinct assertable subjects: ${subjects.size}\n`);
  const files = new Set(blocks.map((b) => b.file));
  process.stdout.write(`  Across ${files.size} pages\n\n`);
}

if (process.argv[1] && process.argv[1].endsWith("docs-examples.mjs")) main();
