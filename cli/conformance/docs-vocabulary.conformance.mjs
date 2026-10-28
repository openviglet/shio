/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH499, instrument one of two — **every name a published example uses is a name this product
 * still has.**
 *
 * ## The two instruments, and why one was not enough
 *
 * The task's first deliverable was a count (`docs-examples.mjs`): 58 executable blocks over 18
 * pages carrying 65 assertable subjects. That count is what decided the shape, because it splits
 * cleanly in two and the halves answer different questions at wildly different prices.
 *
 * - **This file — the vocabulary.** Does `shio propose` still exist; is `/api/v2/agent/render`
 *   still mapped; is `shio_write` still a tool; does anything still bind `shio.turing.api-key`?
 *   Read from the product's own declarations, in milliseconds, with **no instance at all**. This
 *   is what catches *the renamed verb* — the failure in the task's own title.
 * - **`docs-examples.conformance.mjs` — the execution.** The documented requests are actually
 *   issued against a seeded instance. Slower, needs a live build, and catches what a name check
 *   structurally cannot: an endpoint that exists in source and 500s, or one whose response no
 *   longer carries the key the page shows.
 *
 * Collapsing them into one would have meant paying for an instance to answer "is this verb
 * spelled right", and every renamed-verb regression would have hidden behind whichever
 * environment problem stopped the instance booting that day.
 *
 * ## Why it lives here and not in `cli/test/`
 *
 * Its input was a **different repository** — `vigletdocs.github.io/docs-shio` — which a clone of
 * this one did not have (the pages moved into `shio-site/docs` with SH1262, and the suite stayed
 * opt-in). `cli/test/` must run with nothing external, so a gate there would have
 * to skip when the checkout is absent, and a skip is the failure mode this whole directory exists
 * to refuse (`support/instance.mjs`: *absent is a failure, never a skip*). Here the bargain is
 * already opt-in, so an absent checkout is a **failed run naming the fix**, exactly as an
 * unreachable instance is. `cli/test/assertion-debt.test.mjs` records the same boundary from the
 * other side: 21 Block R ids sit in `ELSEWHERE` because no suite here could see their deliverable.
 * These two files are what changes that.
 */
import assert from "node:assert/strict";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { describe, test } from "node:test";

import { DOCS_DIR } from "../../scripts/docs-location.mjs";
import { AUDIT_RULES } from "../src/audit.mjs";

import { CLAIMS, extract, toolRows } from "./docs-examples.mjs";
import { productVocabulary, routeExists } from "./support/product-vocabulary.mjs";

// @scans prose reason: it reads the published Markdown pages for the verbs, routes, tool names
// and property keys they spell, so the prose IS the corpus being checked (SH740).

/**
 * Where the published pages are.
 *
 * <p>An override rather than a hard-coded path, because the one thing certain about a sibling
 * checkout is that it is not in the same place on two machines.</p>
 */
const DOCS = DOCS_DIR;

/** This repository, for the shipped model SH775 compares the documentation against. */
const ROOT = new URL("../../", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/**
 * Names a page may use that this product does not own, and the reason each is not a miss.
 *
 * <p>A *classification*, not a suppression list — every entry says which vocabulary the name
 * belongs to instead. The distinction matters because the failure this file exists to catch is a
 * name that quietly stopped existing, and a list of "things not to check" is where such a name
 * would go to be forgotten. `properties` is where it bites: half the keys on the installation
 * page are Spring Boot's own, and asserting Shio declares `spring.datasource.url` would fail on a
 * page that is correct.</p>
 */
const NOT_OURS = [
  { prefix: "spring.", why: "Spring Boot binds it; Shio neither declares nor defaults it" },
  { prefix: "server.", why: "Spring Boot's servlet container" },
  { prefix: "logging.", why: "Spring Boot's logging" },
  { prefix: "management.", why: "Spring Boot Actuator" },
];

const foreign = (key) => NOT_OURS.find((each) => key.startsWith(each.prefix));

/**
 * A documented key with a variable segment, which is a notation rather than a name (SH663).
 *
 * <p>`shio.turing.sites.<ShioSite>` is how the pages spell *"one entry per site"*, and the binding
 * behind it is a map. It surfaced the moment SH663 taught the extractor to read property tables —
 * the fenced blocks happened to contain no placeholder, so the convention had never met this check.
 *
 * <p>Skipped rather than resolved, and the distinction matters: this reader cannot say whether
 * `<ShioSite>` is a *legitimate* variable segment or a page inventing one, and pretending otherwise
 * would be a wildcard that matches anything after a dot. What it can say honestly is that a key
 * carrying the documentation's own placeholder syntax is not a literal key, so demanding a literal
 * binding for it would make the page less readable to satisfy a check.</p>
 */
const isTemplateKey = (key) => /<[^>]+>/.test(key);

/** The host in a `curl` example is illustrative by design; only its path is a claim about us. */
const pathOfUrl = (subject) => {
  if (subject.startsWith("/")) return subject.split("?")[0];
  try {
    return new URL(subject.replace(/["']+$/, "")).pathname;
  } catch {
    return null;
  }
};

function docs() {
  if (!existsSync(DOCS)) {
    throw new Error(
      `SH499: the published documentation is not at ${DOCS}.\n` +
        "This suite compares what those pages tell a reader to type against what this product\n" +
        "declares, so without them it proves nothing — and reporting nothing as a pass is what\n" +
        "let the 2018 pages rot in the first place. They are shio-site/docs/src/content/docs\n" +
        "in this tree (SH1262), or SHIO_DOCS_DIR.",
    );
  }
  return extract(DOCS);
}

/**
 * Every published page as `[name, text]` (SH697).
 *
 * <p>Not derived from `blocks`: a page whose only defect is a wrong sentence carries no executable
 * block, and reading only the pages that already produced one would skip exactly the page this is
 * looking for.</p>
 */
function pages(dir = DOCS, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) pages(full, out);
    else if (/\.mdx?$/.test(name)) out.push([name, readFileSync(full, "utf8")]);
  }
  return out;
}

const { blocks } = docs();
const vocabulary = productVocabulary();

/** Every distinct subject of one kind, with the pages that use it — a miss has to name a page. */
function subjects(kind) {
  const out = new Map();
  for (const block of blocks.filter((each) => each.kind === kind)) {
    for (const subject of block.subjects) {
      if (!out.has(subject)) out.set(subject, []);
      out.get(subject).push(`${block.file}:${block.line}`);
    }
  }
  return out;
}

const report = (misses) =>
  misses.map(({ subject, where }) => `  ${subject}\n    written at ${where.join(", ")}`).join("\n");

describe("SH499 conformance: the published vocabulary is this product's vocabulary", () => {
  test("every documented `shio` verb is in the command registry", () => {
    const misses = [];
    for (const [verb, where] of subjects("cli")) {
      if (!vocabulary.cliVerbs.has(verb)) misses.push({ subject: verb, where });
    }
    assert.deepEqual(
      misses,
      [],
      "SH499: a page tells a reader to run a command this CLI does not have. Either the verb was "
        + "renamed and the page was not, or the page invented it. `COMMANDS` in cli/src/index.mjs "
        + `is the registry it was checked against.\n${report(misses)}`,
    );
  });

  test("every documented endpoint is mapped by a controller", () => {
    const misses = [];
    for (const [subject, where] of subjects("http")) {
      const [method, path] = subject.split(" ");
      if (!routeExists(method, path, vocabulary.routes)) misses.push({ subject, where });
    }
    // A `curl` example is the same claim wearing a different syntax, so it is checked here rather
    // than given a test of its own — the host is illustrative, the path is not.
    for (const [subject, where] of subjects("curl")) {
      const path = pathOfUrl(subject);
      if (!path || path === "/") continue;
      const mapped = ["GET", "POST", "PUT", "PATCH", "DELETE"]
        .some((method) => routeExists(method, path, vocabulary.routes));
      if (!mapped) misses.push({ subject: `${subject} (path ${path})`, where });
    }
    assert.deepEqual(
      misses,
      [],
      "SH499: a page publishes a request to an endpoint no @RequestMapping answers. A moved "
        + "controller is the usual cause and it is invisible to a reader, who gets a 404 and no "
        + `hint that the page is the thing that is wrong.\n${report(misses)}`,
    );
  });

  test("every documented MCP tool name is a tool the server registers", () => {
    const misses = [];
    for (const [name, where] of subjects("mcp")) {
      if (!vocabulary.mcpTools.has(name)) misses.push({ subject: name, where });
    }
    assert.deepEqual(
      misses,
      [],
      "SH499: a page shows a tools/call for a tool `tools/list` does not return. An agent that "
        + `reads the page instead of the manifest loses a turn to it.\n${report(misses)}`,
    );
  });

  /**
   * SH1068 — three populations the gate could not see, and the first one was already extracted.
   *
   * <p>`docs-examples` has pulled `"op": "…"` out of documented payloads since SH499 and nothing
   * consumed it: the subject existed, no test looked at it, and a renamed op was the one name in
   * the protocol a page could go on spelling for ever. The other two had no reader at all until
   * `productVocabulary` grew `mcpResources`, `batchOps` and `verifyChecks`.</p>
   */
  test("SH1068: every documented op name is an op this build dispatches", () => {
    const misses = [];
    for (const [op, where] of subjects("payload")) {
      if (!vocabulary.batchOps.has(op)) misses.push({ subject: op, where });
    }
    assert.deepEqual(misses, [],
      "SH1068: a page publishes a batch op this build does not dispatch. The whole document is "
        + "refused by the server with `allowed`, so a reader following the page loses the write "
        + `and the run.
${report(misses)}`);
  });

  test("SH1068: every documented MCP resource URI is one the server registers", () => {
    const misses = [];
    for (const [uri, where] of subjects("resource")) {
      // A template is registered by its prefix, which is what `ShMcpResource#matches` keys on —
      // the same rule `live-coverage` applies when it counts one as read.
      const known = [...vocabulary.mcpResources]
        .some((each) => uri === each || (each.endsWith("/") && uri.startsWith(each)));
      if (!known) misses.push({ subject: uri, where });
    }
    assert.deepEqual(misses, [],
      "SH1068: a page tells a client to read a resource `resources/list` does not return. It is "
        + `the tool-name failure one method along, and it fails the same way.
${report(misses)}`);
  });

  test("SH1068: every documented `--checks` group is one verify accepts", () => {
    const misses = [];
    for (const [check, where] of subjects("checks")) {
      if (!vocabulary.verifyChecks.has(check)) misses.push({ subject: check, where });
    }
    assert.deepEqual(misses, [],
      "SH1068: a page passes a check group `verify` refuses. The refusal lists what it takes, so "
        + `the reader is told — after their gate has already failed.
${report(misses)}`);
  });

  test("every documented `shio.*` property is bound by something", () => {
    const misses = [];
    for (const [key, where] of subjects("properties")) {
      if (foreign(key) || vocabulary.properties.has(key) || isTemplateKey(key)) continue;
      misses.push({ subject: key, where });
    }
    assert.deepEqual(
      misses,
      [],
      "SH499: a page documents a configuration key no @ConfigurationProperties class binds, so "
        + "setting it does nothing and the operator has no way to find that out — the silent "
        + `failure this is the only check for.\n${report(misses)}`,
    );
  });

  /**
   * The instrument is asked to fail, on names chosen to be absent.
   *
   * <p>Four green tests above are consistent with a reader that resolves everything, and that
   * reader would be indistinguishable from a working one for exactly as long as the docs stay
   * correct — which is to say, until the moment it is needed. So each vocabulary is also asked
   * about something it must not have. `deleteed` and `shio.turing.api_key` are the shapes of the
   * real failures: a renamed verb, and a key written with the wrong separator.</p>
   */
  test("SH499: the check itself fails on a name this product does not have", () => {
    assert.ok(!vocabulary.cliVerbs.has("deleteed"), "a verb registry that has everything has nothing");
    assert.ok(!vocabulary.mcpTools.has("shio_delete"), "shio_delete is not a tool");
    assert.ok(!vocabulary.properties.has("shio.turing.api_key"), "the binding is kebab, not snake");
    // SH628 — the two halves of one defect, and each is why the other went unseen. The reader
    // flattened every `private` field onto the outer prefix, so a nested group produced a key
    // nothing binds *and* hid the key that is real. Both directions are asserted: fixing only the
    // miss would leave the invented spelling passing, which is how a page gets a key that does
    // nothing and no way to find out.
    assert.ok(
      vocabulary.properties.has("shio.cda.preview.ttl-seconds"),
      "SH628: a nested @ConfigurationProperties group is a path segment, not a flattened field",
    );
    assert.ok(
      !vocabulary.properties.has("shio.cda.ttl-seconds"),
      "SH628: the flattened spelling binds nothing and must not read as declared",
    );
    assert.equal(
      routeExists("GET", "/api/v2/agent/manifesto", vocabulary.routes),
      null,
      "one letter from a real route must not match it, or a rename would never be caught",
    );
    assert.ok(
      routeExists("GET", "/api/v2/agent/manifest", vocabulary.routes),
      "and the real one must, or the check above is green for the wrong reason",
    );
    assert.ok(
      routeExists("GET", "/api/v2/post-type/Article", vocabulary.routes),
      "a concrete value has to match the mapping's {name}, which is how every page writes it",
    );
  });

  /**
   * SH652 — and the other direction, which is the one nothing here was asking.
   *
   * <p>Every test above walks the *documentation* and checks the product has what a page tells a
   * reader to type. That catches a page describing something that was renamed or invented, and it
   * cannot catch the opposite: a rule the product emits that no page describes. `shio audit`
   * reported ten of them for two blocks while the published page listed the verb and defined none,
   * so the only way to learn what `collapsed` meant was to read `cli/src/audit.mjs`.
   *
   * <p>That inverts what the verb is for. An audit closes the perception loop (P9) without a human
   * round trip, and a finding whose meaning is written nowhere needs exactly the round trip it was
   * meant to remove — from somebody who has the source open.
   *
   * <p>Read off `AUDIT_RULES` rather than a list kept here, so the eleventh rule fails this the day
   * it is added rather than the day a reader hits it.
   */
  test("SH652: every rule `shio audit` can report is defined on the published cli page", () => {
    const page = readFileSync(join(DOCS, "cli.md"), "utf8");
    const rules = Object.keys(AUDIT_RULES);

    // Printed and floored, because `deepEqual([], [])` is how a reader of an empty map passes.
    process.stdout.write(`\nSH652 audit rules: ${rules.length} checked against cli.md\n`);
    assert.ok(rules.length >= 10, `expected the audit's rules, found ${rules.length}`);

    const misses = rules
      .filter((rule) => !page.includes(`\`${rule}\``))
      .sort((a, b) => a.localeCompare(b));

    assert.deepEqual(
      misses,
      [],
      "SH652: `shio audit` reports a rule the published page never names, so a reader who gets "
        + "the finding has to open cli/src/audit.mjs to learn what it means — which is the human "
        + "round trip the audit exists to remove. Add a row to `What shio audit reports`:\n"
        + misses.map((rule) => `  ${rule} (${AUDIT_RULES[rule]})`).join("\n"),
    );

    // The two lines the report prints whether or not anything is wrong. They are not rules, so
    // `AUDIT_RULES` cannot carry them, and they are the answer to "did this page keep its regions"
    // — a reader who does not know that reads `no landmark · 2 headings` as decoration.
    for (const line of ["shape:", "scripts:"]) {
      assert.ok(
        page.includes(`\`${line}\``),
        `SH652: the report always prints \`${line}\`, and a line nobody explained is a line `
          + "nobody joins to anything",
      );
    }
  });

  /**
   * SH697 — the direction a docs-to-product check cannot have.
   *
   * <p>Every test above asks whether a documented name is real. None asks whether a real name is
   * documented, and by construction none can: a tool missing from the page contributes no subject,
   * and a checker handed no subject passes. `shio_marketplace` shipped in SH678 and never reached
   * `mcp.md`, which is a whole capability unadvertised on the page that exists to list them.</p>
   *
   * <p>Held against the **table**, not against every mention: the table is the page's catalogue,
   * and `mcp.md` deliberately names two non-tools in prose to say they are parameters.</p>
   */
  test("SH697: every tool the server registers is on the published tool table", () => {
    const published = new Set(
      blocks.filter((block) => block.kind === "mcp" && block.from === "table")
        .flatMap((block) => block.subjects),
    );
    assert.ok(
      published.size > 0,
      "SH697: no tool table was read at all, so the comparison below would pass by having no "
        + "subjects — which is the exact defect this test exists for",
    );
    const undocumented = [...vocabulary.mcpTools].filter((name) => !published.has(name)).sort();
    assert.deepEqual(
      undocumented,
      [],
      "SH697: this server registers a tool the published tool table does not list, so a real "
        + "capability is unadvertised and the page's own count is wrong. The root README has been "
        + "held to this since SH499 (`readme-surface.test.mjs`); the published documentation was "
        + `the surface with no instrument.\n  published: ${[...published].sort().join(" ")}`,
    );
  });

  /**
   * SH697 — and the number in the prose, derived rather than typed.
   *
   * <p>The page said *"Thirteen"* while `ShMcpServerTest` capped the list at twelve, because the
   * table listed two tools the product had rejected. Spelled out as a word, which is why this reads
   * both forms: the sentence is prose and should stay prose.</p>
   */
  test("SH697: the tool count a page states is the number of tools", () => {
    const WORDS = ["zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
      "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
      "eighteen", "nineteen", "twenty"];
    const size = vocabulary.mcpTools.size;
    const claims = [];
    for (const [file, text] of pages()) {
      // Which counts are about *these* tools, and the rule has to be narrow for SH663's reason.
      // `content-lifecycle.md` says "all five tools below" about the curator's console tools, and
      // reading that as an MCP count would report a page for saying a true thing. So a line counts
      // when it names MCP, or when the page is the one carrying the tool table — derived from the
      // table rather than an allow-list of filenames, which is the thing that rots.
      const isToolPage = toolRows(text).rows.length > 0;
      for (const line of text.split("\n")) {
        if (!isToolPage && !/mcp/i.test(line)) continue;
        for (const m of line.matchAll(/\b([A-Za-z]+|\d+)\s+(?:Model Context Protocol\s+)?tools?\b/g)) {
          const raw = m[1].toLowerCase();
          const value = /^\d+$/.test(raw) ? Number(raw) : WORDS.indexOf(raw);
          // Only a token that IS a number is a count. "the tools" and "MCP tools" read as -1 here
          // and are prose, not a claim.
          if (value >= 0 && value !== size) claims.push({ subject: `${m[1]} tools`, where: [file] });
        }
      }
    }
    assert.deepEqual(
      claims,
      [],
      `SH499/SH697: a page states a number of MCP tools that is not ${size}. A hand-typed count in `
        + "an ungated file is the thing that rots — this one said thirteen for as long as the table "
        + `held two tools that were never built.\n${report(claims)}`,
    );
  });

  /**
   * SH775 — a section type the vocabulary ships and the documentation never names.
   *
   * <p>The sweep this closes found the page listing the SH95 vocabulary still naming seven types
   * after SH763 shipped an eighth. Nothing was wrong with the page when it was written; a type
   * arrived and the sentence did not move, which is how every hand-kept list in this project has
   * gone stale — and the reader's only symptom is a capability they never learn exists.</p>
   *
   * <p>Decidable, which is why it is a gate rather than a habit: the vocabulary is a fixed set with
   * a canonical order (`sectionPostTypes`, serialized into the shipped blueprints), and the
   * documentation either spells a name or it does not. SH322's rule applies — the grep is the
   * deliverable, because it is what finds the *next* one.</p>
   *
   * <p>The blueprint JSON is the source rather than the TypeScript, for the reason
   * `ShConvertedLayoutRenderTest` reads it too: it is what a running instance provisions, so a name
   * that reaches an instance and not a page is exactly the gap being measured.</p>
   */
  test("SH775: every section type the vocabulary ships is named in the documentation", () => {
    const catalogue = join(ROOT, "shio-app", "src", "main", "resources", "post-types", "vocabulary");
    const shipped = readdirSync(catalogue)
      .filter((file) => file.startsWith("Section") && file.endsWith(".json"))
      .map((file) => file.slice(0, -".json".length))
      .sort();
    const prose = readdirSync(DOCS)
      .filter((file) => file.endsWith(".md"))
      .map((file) => readFileSync(join(DOCS, file), "utf8"))
      .join("\n");

    // A word boundary, not a substring. Found by probing this check rather than by review: a
    // deliberately broken page spelling `SectionMarkupX` still satisfied `prose.includes(...)`, so
    // the guard would have passed over the exact staleness it exists to catch. No shipped name is
    // a prefix of another today, which is precisely why the substring form looked fine.
    // `\\b` and not `\b`: inside a template literal `\b` is the BACKSPACE escape, so the pattern
    // becomes U+0008 around the name and matches nothing — which reads as "no type is documented"
    // and is the same false red as a scan that came back empty. Caught by probing this check.
    const named = (name) => new RegExp(`\\b${name}\\b`).test(prose);

    assert.ok(shipped.length >= 7,
      `SH775: the catalogue at ${catalogue} yielded ${shipped.length} section type(s) — a read that `
        + "came back empty would make the assertion below pass over nothing");
    process.stdout.write(`
SH775: ${shipped.length} section type(s) shipped, `
      + `${shipped.filter(named).length} named in the documentation
`);

    assert.deepEqual(
      shipped.filter((name) => !named(name)),
      [],
      "SH775: this type is provisioned by a running instance and named nowhere a reader looks. The "
        + "page listing the vocabulary said seven for as long as it took somebody to notice an "
        + "eighth had shipped — and a curator's only symptom is a capability they never learn "
        + "exists. Name it where the vocabulary is listed.",
    );
  });

  test("SH499: the counts are printed, because a gate that checks nothing passes quietly", () => {
    // SH1068 — every kind the extractor DECLARES, not only the kinds that matched. Two of the
    // three added here found nothing on the pages as they stand, and a report built from what
    // matched would have printed a line that read exactly like a suite checking them: the zero is
    // the finding (no page documents a `resources/read` or a `--checks` group), and a kind that
    // silently stops matching is the same zero arriving later.
    const kinds = [...new Set([...CLAIMS.map((claim) => claim.kind),
      ...blocks.map((block) => block.kind)])].sort();
    const counted = kinds.map((kind) => `${kind} ${subjects(kind).size}`).join(", ");
    const pages = new Set(blocks.map((block) => block.file)).size;
    process.stdout.write(
      `\nSH499 vocabulary: ${blocks.length} executable block(s) over ${pages} page(s) — ${counted}\n`
        + `  checked against ${vocabulary.cliVerbs.size} verb(s), ${vocabulary.routes.size} route(s), `
        + `${vocabulary.mcpTools.size} tool(s), ${vocabulary.properties.size} property key(s)\n`,
    );
    assert.ok(blocks.length > 20, `expected the documented examples, found ${blocks.length}`);
  });
});
