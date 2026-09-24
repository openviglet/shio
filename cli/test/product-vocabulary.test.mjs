/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1068 — the readers the docs gate checks against, checked themselves.
 *
 * ## The failure this exists to make impossible
 *
 * `docs-vocabulary.conformance.mjs` asks whether every name a published page spells still exists
 * in this product. Every one of its answers comes from a reader in `support/product-vocabulary.mjs`
 * that scans source, and a reader that quietly stops matching does not fail: it returns fewer
 * names, and a page naming one of the missing ones passes. The gate would then be green *because*
 * it had gone blind, which is the shape SH789 named one directory over.
 *
 * So each reader is asserted twice: it finds what this repository actually declares, and it finds
 * a name planted in a source only this test can see. The second half is what a shrinking regex
 * cannot survive — `batchOps` reading a fixture that declares `post.teleport` must answer with it.
 *
 * ## Why it is here rather than in `cli/conformance/`
 *
 * It needs no instance and no docs checkout, so it belongs in the gate that runs on every push.
 * The conformance suite is where the *documents* are judged; this is where the instrument that
 * judges them is.
 */
// @scans code reason: it reads the Java sources the vocabulary readers read, to assert they
// answer with what those sources declare — the scan IS the subject here (SH740).
import assert from "node:assert/strict";
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import {
  batchOps, mcpResources, mcpTools, productVocabulary, verifyChecks,
} from "../conformance/support/product-vocabulary.mjs";
import { code } from "./support/source-text.mjs";

/** A throwaway directory holding one planted source, which is the whole fixture. */
function planted(name, body) {
  const dir = mkdtempSync(join(tmpdir(), "sh1068-"));
  mkdirSync(join(dir, "nested"), { recursive: true });
  writeFileSync(join(dir, "nested", name), body, "utf8");
  return { dir, file: join(dir, "nested", name) };
}

test("SH1068: every population the gate judges against has a reader that found something", () => {
  const vocabulary = productVocabulary();

  const counts = Object.fromEntries(
    Object.entries(vocabulary).map(([name, set]) => [name, set.size]),
  );
  process.stdout.write(`\nSH1068 product vocabulary: ${JSON.stringify(counts)}\n`);

  assert.deepEqual(Object.keys(vocabulary).sort(), [
    "batchOps", "cliVerbs", "mcpResources", "mcpTools", "properties", "routes", "verifyChecks",
  ], "SH1068: a population with no reader is a population the docs gate cannot check at all, "
    + "which is how an op name went unchecked from SH81 until this");
  for (const [name, size] of Object.entries(counts)) {
    assert.ok(size > 0, `SH1068: the ${name} reader found nothing, so every check against it `
      + "would pass by being empty");
  }
});

test("SH1068: the op reader answers with a name planted where only it can see it", () => {
  const { file } = planted("ShAgentBatchService.java",
    'class X { public static final String POST_TELEPORT = "post.teleport"; }');

  assert.ok(batchOps(file).has("post.teleport"),
    "SH1068: this is the assertion that survives a shrinking regex — the reader must answer with "
    + "an op it has never seen before, not merely with the ones already in the tree");
});

test("SH1068: the tool and resource readers do the same, from a directory", () => {
  const { dir } = planted("ShTeleportTool.java",
    'class X { String name() { return "shio_teleport"; } String uri() { return "shio://teleport"; } }');

  assert.ok(mcpTools(dir).has("shio_teleport"));
  assert.ok(mcpResources(dir).has("shio://teleport"));
});

test("SH1068: the check reader answers with a planted enum constant", () => {
  const { file } = planted("ShAgentCheck.java", "public enum ShAgentCheck {\n    TELEPORT,\n}\n");

  assert.ok(verifyChecks(file).has("teleport"));
});

test("SH1068: a reader that finds nothing fails loudly rather than answering empty", () => {
  const { file } = planted("ShAgentBatchService.java", "class X { }");

  assert.throws(() => batchOps(file), /SH1068: no batch op names/u,
    "SH1068: an empty answer is what a blind reader returns, so the readers refuse to give one");
});

test("SH1068: every op the spec table documents is an op the vocabulary knows", () => {
  // The cross-check the conformance gate cannot make from the docs' side: `ShAgentOps` is what
  // `shio://ops` publishes and `ShAgentBatchService.OPS` is what the dispatcher accepts. A name in
  // one and not the other is a capability documented and unreachable, or reachable and undocumented.
  const specs = new Set([...readOps().matchAll(/ShAgentBatchService\.([A-Z_]+),/gu)]
    .map((match) => match[1]));
  const constants = new Set([...readBatchService()
    .matchAll(/public static final String ([A-Z_]+) = "[a-z]+\.[a-z]+";/gu)].map((m) => m[1]));

  const undocumented = [...constants].filter((name) => !specs.has(name)).sort();
  assert.deepEqual(undocumented, [],
    "SH1068: an op the dispatcher accepts and the spec table does not describe is a key an agent "
    + "has to guess, which is what SH332 wrote ShAgentOps for. ShAgentOpsContractTest fails on it "
    + "from the Java side; this is the same claim from the side that reads the vocabulary.");
  assert.ok(specs.size >= constants.size,
    "SH1068: the spec table lost entries relative to the vocabulary");
});

function readOps() {
  return readSource("api/agent/ShAgentOps.java");
}

function readBatchService() {
  return readSource("api/agent/ShAgentBatchService.java");
}

/**
 * The source with its comments removed (SH740).
 *
 * <p>Not ceremony here: `ShAgentOps`'s javadoc quotes op names, and `ShAgentBatchService` carries
 * a paragraph of prose about the vocabulary above every constant. A cross-check reading both
 * would otherwise credit a spec that exists only in a sentence about specs.</p>
 */
function readSource(path) {
  const root = new URL("../../shio-app/src/main/java/com/viglet/shio/", import.meta.url).pathname
    .replace(/^\/([A-Za-z]:)/u, "$1");
  return code(readFileSync(join(root, path), "utf8"));
}
