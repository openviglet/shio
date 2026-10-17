/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The MCP Registry entry (`cli/server.json`) describes the package it installs, and every fact in
 * it is read back from that package here. The registry verifies ownership by matching `name`
 * against `mcpName` in the published package.json, and it installs exactly `packages[].version`,
 * so an entry that drifts from the package is one that either fails to publish or launches a
 * version that is not the one described.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { code } from "./support/source-text.mjs";

// @scans code

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const SERVER = JSON.parse(read("../server.json"));
const PKG = JSON.parse(read("../package.json"));
const MCP_COMMAND = code(read("../src/commands/mcp.mjs"));
const CONTEXT = code(read("../src/context.mjs"));

test("SH1245: the registry entry names this package, and the package claims the entry", () => {
  const [npm] = SERVER.packages;
  assert.equal(npm.registryType, "npm", "SH1245: the entry installs from npm");
  assert.equal(npm.identifier, PKG.name, "SH1245: and names this package");
  assert.equal(PKG.mcpName, SERVER.name, "SH1245: package.json's mcpName is the entry's name, or the registry refuses it");
  assert.ok(PKG.files.includes("src"), "SH1245: the bridge the entry launches ships in the package");
});

test("SH1245: the entry's versions are the package's, which the release stamps together", () => {
  assert.equal(SERVER.version, PKG.version, "SH1245: server.json's version is cli/package.json's");
  for (const p of SERVER.packages) {
    assert.equal(p.version, PKG.version, `SH1245: ${p.identifier} installs ${p.version}, not ${PKG.version}`);
  }
  const workflow = read("../../.github/workflows/publish-js-sdk.yml");
  assert.match(workflow, /cli\/server\.json/, "SH1245: the release workflow stamps and commits server.json");
});

test("SH1245: the entry launches `shio mcp` with the variables the bridge actually reads", () => {
  const [npm] = SERVER.packages;
  assert.deepEqual(npm.transport, { type: "stdio" }, "SH1245: the bridge speaks stdio");
  assert.deepEqual(npm.packageArguments.map((a) => a.value), ["mcp"], "SH1245: the subcommand is mcp");
  assert.match(MCP_COMMAND, /resolveAdmin/, "SH1245: the bridge connects with the console credentials");

  const names = npm.environmentVariables.map((v) => v.name).sort();
  assert.deepEqual(names, ["SHIO_PASSWORD", "SHIO_URL", "SHIO_USER"]);
  for (const name of names) {
    assert.match(CONTEXT, new RegExp(`env\\.${name}\\b`), `SH1245: ${name} is read by resolveAdmin`);
  }
  const url = npm.environmentVariables.find((v) => v.name === "SHIO_URL");
  assert.ok(CONTEXT.includes(`env.SHIO_URL ?? "${url.default}"`), "SH1245: and SHIO_URL's default is the CLI's");
  assert.ok(SERVER.description.length <= 100, "SH1245: the registry caps the description at 100 characters");
});
