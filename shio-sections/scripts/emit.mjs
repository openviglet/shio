#!/usr/bin/env node
/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Emits the section vocabulary as canonical post-type JSON into the blueprint packages
 * that ship it (SH95 → SH94).
 *
 * Why the JSON is committed rather than generated at build time. A blueprint package is
 * read from the classpath by the running instance, so its post-types have to exist as
 * files in the jar — and a file that is generated during the Maven build would make the
 * Java build depend on the JS toolchain for content that changes about twice a year. So
 * the JSON is committed, this script is how it is regenerated, and `emit.test.mjs` fails
 * the build if the two ever disagree. One source of truth, with a guard instead of a
 * convention.
 *
 *   node scripts/emit.mjs            # rewrite the committed JSON
 *   pnpm -C shio-sections test       # assert it matches
 *
 * Requires @viglet/shio-model to be built (pnpm -C shio-model run build).
 */
import { mkdir, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { serializeDef } from "@viglet/shio-model";

import { sectionPostTypes } from "../src/post-types.ts";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "..", "..");

/**
 * Packages that carry the vocabulary, each getting the **whole** set.
 *
 * Not a per-package subset, deliberately: provisioning is idempotent, the files are small,
 * and a site that received the full vocabulary can grow a testimonial section next month
 * without applying a second blueprint. A subset would trade a few kilobytes for a
 * modelling step later.
 */
export const TARGETS = [
  join(repo, "shio-app/src/main/resources/blueprints/marketing-site/post-types"),
  join(repo, "shio-app/src/main/resources/blueprints/landing/post-types"),
];

/** The files one target directory must contain: post-type name → canonical JSON. */
export function files() {
  const out = new Map();
  for (const def of sectionPostTypes()) {
    out.set(`${def.name}.json`, serializeDef(def));
  }
  return out;
}

async function main() {
  const contents = files();
  for (const target of TARGETS) {
    await mkdir(target, { recursive: true });
    for (const [name, json] of contents) {
      await writeFile(join(target, name), json, "utf8");
    }
    process.stdout.write(`${target}: ${contents.size} post-type(s)\n`);
  }
}

if (process.argv[1] && process.argv[1].endsWith("emit.mjs")) {
  await main();
}
