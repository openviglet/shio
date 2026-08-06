/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH64 — the CLI compile step. `shio/post-types/` may now hold `*.ts`/`*.mjs`
 * DSL sources (authored with `@viglet/shio-model`) alongside `*.json`. This
 * module reads the directory, dynamically `import()`s each DSL module, and
 * collects every `ShPostTypeDef` it exports — so `push`/`build` see a uniform
 * list of plain def objects and the SH16 diff/serialize pipeline is unchanged.
 *
 * The DSL is the source of truth; canonical JSON is the build output. A `.ts`
 * file already `import`s `@viglet/shio-model` and calls `postType(...)`, so the
 * value we collect is a plain `ShPostTypeDef` — the CLI needs no DSL runtime of
 * its own. `.ts` is loaded via the project's own TS toolchain (Node type
 * stripping / the loader `@viglet/create-shio-app` ships) — no new bundler in the CLI.
 */
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const isJson = (name) => name.endsWith(".json");
const isDslSource = (name) => name.endsWith(".ts") || name.endsWith(".mjs");

/** A value is a post-type def iff it has a string `name` and a `fields` array. */
function isDef(value) {
  return (
    Boolean(value) &&
    typeof value === "object" &&
    typeof value.name === "string" &&
    Array.isArray(value.fields)
  );
}

/**
 * Collect every `ShPostTypeDef` a DSL module exports: the default export, any
 * named export, or an exported array of defs (nested arrays flattened). Default
 * is visited first, then named exports in declaration order, for stable output.
 */
export function collectDefsFromModule(mod) {
  const out = [];
  const visit = (value) => {
    if (Array.isArray(value)) value.forEach(visit);
    else if (isDef(value)) out.push(value);
  };
  if (mod && mod.default !== undefined) visit(mod.default);
  for (const [key, value] of Object.entries(mod ?? {})) {
    if (key !== "default") visit(value);
  }
  return out;
}

/**
 * Read all post-type defs from `dir`, compiling DSL sources on the way:
 *   - `*.json` → parsed verbatim
 *   - `*.ts` / `*.mjs` → dynamically imported, defs collected
 * Files are processed in sorted order so `.json` and `.ts` interleave
 * deterministically. A name defined by more than one source is an error
 * (ambiguous source). The module importer is injectable via `ctx.import` so
 * tests can supply a fake loader without touching disk.
 *
 * @returns {Promise<object[]>} the collected `ShPostTypeDef`s
 */
export async function readLocalDefs(ctx, dir) {
  const target = join(ctx.cwd, dir);
  let entries;
  try {
    entries = await ctx.fs.readdir(target);
  } catch {
    throw new Error(`shio: no post-type directory at "${dir}" — run "shio pull" first.`);
  }

  const importer = ctx.import ?? ((url) => import(url));
  const sourceByName = new Map(); // def name -> filename it came from (dup guard)
  const defs = [];

  const add = (def, source) => {
    const prior = sourceByName.get(def.name);
    if (prior) {
      throw new Error(
        `shio: post-type "${def.name}" is defined twice — in "${prior}" and "${source}". ` +
          `Each name must have exactly one source.`
      );
    }
    sourceByName.set(def.name, source);
    defs.push(def);
  };

  for (const name of entries.filter((f) => isJson(f) || isDslSource(f)).sort()) {
    const path = join(target, name);
    if (isJson(name)) {
      add(JSON.parse(await ctx.fs.readFile(path, "utf8")), name);
      continue;
    }
    const mod = await importer(pathToFileURL(path).href);
    const found = collectDefsFromModule(mod);
    if (found.length === 0) {
      throw new Error(
        `shio: DSL module "${name}" exports no post-type — export a postType(...) ` +
          `(as default, a named export, or an array).`
      );
    }
    for (const def of found) add(def, name);
  }

  return defs;
}
