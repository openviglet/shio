/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { parseArgs } from "../src/args.mjs";

test("parses --key value, --key=value and positionals", () => {
  const { flags, positionals } = parseArgs(
    ["my-site", "--url", "http://x", "--out=lib/t.d.ts"],
  );
  assert.equal(positionals[0], "my-site");
  assert.equal(flags.url, "http://x");
  assert.equal(flags.out, "lib/t.d.ts");
});

test("kebab-case flags become camelCase", () => {
  const { flags } = parseArgs(["--import-from", "@viglet/shio-client"]);
  assert.equal(flags.importFrom, "@viglet/shio-client");
});

test("declared booleans do not swallow the next token", () => {
  const { flags, positionals } = parseArgs(["--dry-run", "extra"], { booleans: ["dry-run"] });
  assert.equal(flags.dryRun, true);
  assert.deepEqual(positionals, ["extra"]);
});

test("-h and -y shorthands", () => {
  assert.equal(parseArgs(["-h"]).flags.help, true);
  assert.equal(parseArgs(["-y"]).flags.yes, true);
});
