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
 * Its input is a **different repository** — `vigletdocs.github.io/docs-shio` — which a clone of
 * this one does not have. `cli/test/` must run with nothing external, so a gate there would have
 * to skip when the checkout is absent, and a skip is the failure mode this whole directory exists
 * to refuse (`support/instance.mjs`: *absent is a failure, never a skip*). Here the bargain is
 * already opt-in, so an absent checkout is a **failed run naming the fix**, exactly as an
 * unreachable instance is. `cli/test/assertion-debt.test.mjs` records the same boundary from the
 * other side: 21 Block R ids sit in `ELSEWHERE` because no suite here could see their deliverable.
 * These two files are what changes that.
 */
import assert from "node:assert/strict";
import { existsSync } from "node:fs";
import { describe, test } from "node:test";

import { extract } from "./docs-examples.mjs";
import { productVocabulary, routeExists } from "./support/product-vocabulary.mjs";

/**
 * Where the published pages are.
 *
 * <p>An override rather than a hard-coded path, because the one thing certain about a sibling
 * checkout is that it is not in the same place on two machines.</p>
 */
const DOCS = process.env.SHIO_DOCS_DIR
  ?? "D:/Git/viglet/vigletdocs/vigletdocs.github.io/docs-shio";

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
        "let the 2018 pages rot in the first place. Clone the docs repo and point at it:\n" +
        "  git clone https://github.com/vigletdocs/vigletdocs.github.io\n" +
        "  SHIO_DOCS_DIR=<path>/docs-shio pnpm conformance",
    );
  }
  return extract(DOCS);
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

  test("SH499: the counts are printed, because a gate that checks nothing passes quietly", () => {
    const kinds = [...new Set(blocks.map((block) => block.kind))].sort();
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
