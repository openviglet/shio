/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH707 — which of the agent's verbs a running instance has ever been asked.
 *
 * ## The gap
 *
 * The vocabulary is proved everywhere except where it is used. `shio-app` has forty-odd
 * `ShAgent*Test` classes, all MockMvc. `cli/test/` has sixty-odd module-level suites. And
 * `cli/conformance/`, the only tier with an instance behind it, calls **two** of the twelve MCP
 * tools — because SH561 set out to prove the transport, not the surface it carries.
 *
 * Every gap Block V names is a suite somebody has to write, and the first thing that has to exist
 * is the count. Otherwise the block ships six suites and nobody can say what fraction of the
 * vocabulary that bought. Same rule as SH338: before "write the tests", the instrument.
 *
 * ## Exercised means CALLED, and that distinction is the whole instrument
 *
 * The first draft of this counted any mention of a name anywhere under `conformance/`, and reported
 * five tools covered. Five is wrong: three of them appear in prose. A mention is what a comment
 * does; the claim being measured is that a client had to guess the name right, spell the input,
 * frame the request and read the refusal. So each population is extracted from its **call site**:
 *
 * - MCP tools — `mcp.call("<name>", …)`, the one path `mcp-stdio.mjs` offers.
 * - Batch ops — an `op:` field inside an ops array.
 * - CLI verbs — `import … from "../src/commands/<file>.mjs"`, which is how a conformance scenario
 *   drives a command; there is no shelling out.
 *
 * A looser reader would have made this file green on the day it shipped, which is the one thing it
 * must not be.
 *
 * ## A named list, never a ceiling
 *
 * Same contract as `assertion-debt.test.mjs` (SH527) and `lint-baseline.test.mjs` (SH701): the
 * unexercised verbs are held by name, so covering one is a deletion somebody reviews and adding a
 * verb without live coverage fails here rather than quietly lowering a percentage.
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { test } from "node:test";

import { cliVerbs, mcpTools } from "../conformance/support/product-vocabulary.mjs";

const CONFORMANCE = new URL("../conformance/", import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, "$1");
const BATCH_SERVICE = new URL(
  "../../shio-app/src/main/java/com/viglet/shio/api/agent/ShAgentBatchService.java",
  import.meta.url,
).pathname.replace(/^\/([A-Za-z]:)/, "$1");

/** Every `.mjs` under `conformance/`, which is the only tier that has an instance. */
function conformanceSources(dir = CONFORMANCE, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) conformanceSources(path, out);
    else if (path.endsWith(".mjs")) out.push(path);
  }
  return out;
}

const SOURCE = conformanceSources().map((f) => readFileSync(f, "utf8")).join("\n");

/**
 * The batch op vocabulary, read from the service that dispatches it.
 *
 * <p>Derived rather than listed, for SH697's reason: a hand-typed vocabulary is the thing that
 * rots. `render.provision` is declared elsewhere and deliberately not counted here — this is the
 * content op table, which is what Block V is about.</p>
 */
function batchOps() {
  const source = readFileSync(BATCH_SERVICE, "utf8");
  const found = source.matchAll(/public static final String [A-Z_]+ = "([a-z]+\.[a-z]+)";/g);
  return new Set([...found].map((m) => m[1]));
}

/** Tools reached through `mcp.call("…")` — the only way `mcp-stdio.mjs` speaks to the server. */
function toolsCalled() {
  return new Set([...SOURCE.matchAll(/\.call\(\s*["'`]([a-z_]+)["'`]/g)].map((m) => m[1]));
}

/** Ops reached as `op: "…"` inside an ops array handed to the batch surface. */
function opsCalled() {
  return new Set([...SOURCE.matchAll(/\bop:\s*["'`]([a-z]+\.[a-z]+)["'`]/g)].map((m) => m[1]));
}

/**
 * Verbs whose command module a scenario imports.
 *
 * <p>`content-pull.mjs` and `content-push.mjs` back the `pull` and `push` verbs, so the file name
 * is mapped rather than assumed — a reader that missed that would report two covered verbs as
 * uncovered and send somebody to write a suite that exists.</p>
 */
const MODULE_VERB = { "content-pull": "pull", "content-push": "push", "content-watch": "watch" };

function verbsCalled() {
  const imported = [...SOURCE.matchAll(/from\s+["'`][^"'`]*\/src\/commands\/([a-z-]+)\.mjs["'`]/g)]
    .map((m) => MODULE_VERB[m[1]] ?? m[1]);
  return new Set(imported);
}

/**
 * What no live suite has ever asked for — held by name.
 *
 * <h2>Measured at SH707, and the numbers in §XXIII.1 were both wrong</h2>
 *
 * <p>That section says thirteen MCP tools and thirty-three CLI commands. The product says
 * <b>twelve</b> and <b>thirty-one</b>, read from the registries by `product-vocabulary.mjs` — the
 * same drift SH697 found in the published docs, in the file that was arguing against hand-typed
 * counts. Both figures here are derived, so this note is the last time either is typed.</p>
 *
 * <h2>The shape of what is missing</h2>
 *
 * <p>The two tools that are called are `shio_context` and `shio_read` — a read and a read. Nothing
 * that <em>writes</em> has ever been spoken over a wire: not `shio_write`, not `shio_apply`, not
 * `shio_publish`. The op table is the same story from the other end — the ops a conformance
 * scenario uses are the ones building a fixture needs, so `post.upsert` and `post.publish` are
 * covered and every op that <b>removes</b> or <b>moves</b> something is not. Delete and move are
 * where SH202, SH203 and SH243 all found defects.</p>
 */
const UNEXERCISED = {
  tools: [
    "shio_apply", "shio_assets", "shio_changes", "shio_digest", "shio_find",
    "shio_marketplace", "shio_publish", "shio_remember", "shio_verify", "shio_write",
  ],
  ops: [
    "folder.delete", "folder.move", "folder.restore", "folder.upsert",
    "post.delete", "post.move", "post.restore", "post.unpublish", "site.delete",
  ],
  verbs: [
    "annotations", "apply", "audit", "blueprints", "build", "changes", "context", "deploy", "dev",
    // `pull` is NOT here: `replication.conformance.mjs` imports `content-pull.mjs`, which is the
    // module behind that verb. The first draft listed it, and the mapping above is what caught it —
    // a reader keyed on file names alone would have sent somebody to write a suite that exists.
    "digest", "doctor", "impact", "import", "judge", "marketplace", "mcp", "publish",
    "push", "remember", "report", "resolve", "site", "snapshot", "types",
  ],
};

test("SH707: the live tier's coverage of the agent vocabulary is the declared list", () => {
  const populations = {
    tools: { all: mcpTools(), called: toolsCalled() },
    ops: { all: batchOps(), called: opsCalled() },
    verbs: { all: cliVerbs(), called: verbsCalled() },
  };

  const missing = {};
  const lines = [];
  for (const [name, { all, called }] of Object.entries(populations)) {
    missing[name] = [...all].filter((verb) => !called.has(verb)).sort();
    lines.push(`  ${all.size - missing[name].length}/${all.size} ${name}`);
  }
  console.log(`SH707 live coverage — what a running instance has ever been asked:\n${lines.join("\n")}`);

  assert.deepEqual(missing, {
    tools: [...UNEXERCISED.tools].sort(),
    ops: [...UNEXERCISED.ops].sort(),
    verbs: [...UNEXERCISED.verbs].sort(),
  },
  "SH707: the set of verbs no live suite has ever called is not the declared one. If you covered "
  + "one, delete it from UNEXERCISED in the same commit — an entry claiming a gap that is closed "
  + "sends the next person to write a suite that exists. If you ADDED a verb, it arrived with no "
  + "live coverage: either write the scenario, or say in the diff why a MockMvc proof is enough "
  + "for this one. A percentage would have absorbed both silently, which is why this is a list.");
});

test("SH707: exercised means called, not mentioned", () => {
  // The guard on the reader itself. The first draft matched any occurrence of a name under
  // `conformance/` and reported five tools covered; three of those five are prose. A reader that
  // cannot tell a comment from a call flatters every number it prints.
  const called = toolsCalled();

  assert.ok(SOURCE.includes("shio_write"),
    "SH707: `shio_write` is named somewhere under conformance/ — in prose. If this fails the "
    + "premise of this test has changed and the next assertion proves nothing.");
  assert.ok(!called.has("shio_write"),
    "SH707: the reader counted a tool that is only mentioned. Exercised has to mean a client "
    + "framed the request and read the answer; anything looser makes this instrument report "
    + "coverage that nobody wrote.");

  assert.deepEqual([...called].filter((t) => mcpTools().has(t)).sort(), ["shio_context", "shio_read"],
    "SH707: the live tier calls exactly two real tools, both reads. This is the headline finding "
    + "and it is asserted rather than described, so the day it stops being true something says so.");
});

test("SH707: the populations are read from the product, not from this file", () => {
  // SH697's rule. Every count above is derived; if a registry ever reads empty, this file would
  // report perfect coverage of nothing and pass.
  assert.ok(mcpTools().size >= 10, "SH707: the MCP registry read as empty or nearly so");
  assert.ok(cliVerbs().size >= 25, "SH707: the CLI command registry read as empty or nearly so");
  assert.ok(batchOps().size >= 10, "SH707: the batch op table read as empty or nearly so");

  assert.ok(!mcpTools().has("shio_not_a_tool"),
    "SH707: `shio_not_a_tool` is the name mcp-transport.conformance.mjs calls to assert a refusal. "
    + "It must never be in the registry — and a reader that took its population from the call "
    + "sites rather than from the product would have invented it.");
});
