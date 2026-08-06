/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH542 — every vite.config in this repository is ESM, and `__dirname` does not exist in ESM.
 *
 * Vite 8 prints it as a warning today:
 *
 *   (!) Your Vite config uses features that are unsupported by `configLoader: 'native'`, which is
 *       planned to become the default in a future major version of Vite:
 *       - `__dirname` (vite.config.ts:51:25). Use `import.meta.dirname` instead
 *
 * It works now only because the default `bundle` loader rewrites the identifier on the way in. When
 * `native` becomes the default the same line is a `ReferenceError` at config load, which fails
 * `pnpm run compile:build` — i.e. `mvn package` and the release jar — for a reason that has nothing
 * to do with the change being built. Nine configs carried it; every package is `"type": "module"`,
 * so `import.meta.dirname` is available in all of them and is the form that survives either loader.
 *
 * This lives in the CLI's suite for the same reason `assertion-debt.test.mjs` does: it is a reader
 * over the whole repository rather than a test of one package, and the CLI's suite is the one that
 * already walks from the root. It counts what it read, because a walk that silently matches nothing
 * is the failure mode of every lint of this shape (SH322 — the grep is the deliverable).
 */
import assert from "node:assert/strict";
import { readFileSync, readdirSync, statSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const SKIP = new Set(["node_modules", "target", "dist", "dist-ssr", "build", ".git", "store"]);
const CONFIG = /^vite\.config\.(ts|mts|cts|js|mjs|cjs)$/;

/** `__dirname` and `__filename`, ignoring the string that names them in prose. */
const CJS_GLOBALS = /(?<!`)\b(__dirname|__filename)\b/g;

function viteConfigs(dir) {
  const out = [];
  for (const entry of readdirSync(dir)) {
    if (SKIP.has(entry)) continue;
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) out.push(...viteConfigs(path));
    else if (CONFIG.test(entry)) out.push(path);
  }
  return out;
}

test("SH542: no vite.config reaches for a CommonJS global", () => {
  const configs = viteConfigs(ROOT);
  assert.ok(
    configs.length >= 9,
    `Found ${configs.length} vite configs; there were 9 when this shipped. A walk that stops ` +
      `finding them passes for the wrong reason — check SKIP above against the layout.`,
  );
  const offenders = configs
    .map((path) => [path.slice(ROOT.length), readFileSync(path, "utf8").match(CJS_GLOBALS)])
    .filter(([, hits]) => hits)
    .map(([path, hits]) => `${path} uses ${[...new Set(hits)].join(", ")}`);
  assert.deepEqual(
    offenders,
    [],
    `A vite config uses a CommonJS global, and every package here is "type": "module":\n` +
      `${offenders.join("\n")}\n` +
      `Use import.meta.dirname. Vite's default 'bundle' loader rewrites it and warns; under ` +
      `configLoader: 'native' — planned to become the default — it is a ReferenceError at config ` +
      `load, so the console bundle stops building.`,
  );
});
