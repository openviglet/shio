/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH798 — `pnpm conformance:docs`, with the output kept and the outcome recorded.
 *
 * ## A gate you could run without leaving evidence you ran it
 *
 * SH755 wrapped `pnpm test` and `pnpm conformance` so each leaves a `.gates/<name>.json` — the exit
 * code, the commit, the time — which is what `ShDeclaredRedTest` and `red-suites.test.mjs` read to
 * answer *what colour is this tree*. This one ran `node --test` directly and wrote nothing.
 *
 * It is not a subset inheriting its parent's answer. It is a **separate job** in `suites.yml`, with
 * its own trigger, its own input (a checkout of `vigletdocs`) and its own way of going red — which
 * it was, for twelve commits, while the full conformance gate had not been run at all (SH797).
 *
 * The cost was exact rather than theoretical: SH797 ran this gate four times, green, and every Java
 * run afterwards still reported `conformance — NOT this tree, so not a colour`, because the stamp it
 * named belonged to a different gate and a different commit. That sentence was true of the gate it
 * names and false of the work actually done, and the two are indistinguishable from the report.
 * SH770's rule with nothing to apply it to.
 *
 * ## Deliberately the same shape as the other two
 *
 * Same log-then-stamp, same `runWithLog`, same exit code passed through. Two wrappers that differed
 * would be two behaviours to remember, which is the argument `conformance-with-log.mjs` makes for
 * matching the JS one, one gate earlier.
 */
import { join } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

import { stamp } from "./gate-stamp.mjs";
import { runWithLog } from "./test-with-log.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** One fixed name, for the reason the other two logs take: the useful comparison is the last run. */
const LOG = join(ROOT, "shio-conformance-docs.log");

/**
 * The command, unchanged from what `package.json` ran before this wrapper existed. Wrapping a gate
 * must not quietly change which scenarios it runs — the same rule, and the same sentence, as the
 * conformance wrapper this is modelled on.
 */
export const PNPM_ARGS = ["--reporter", "append-only",
  "--filter", "@viglet/shio", "run", "conformance:docs"];

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const code = await runWithLog("pnpm", PNPM_ARGS, LOG, { label: "The docs conformance suites" });
  stamp(ROOT, "docs", code);
  process.exit(code);
}
