/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH499 — the repository's front door counts what the product has, or the build says so.
 *
 * ## What was actually wrong, found by the Block R sweep
 *
 * `README.md`'s surface table — the first thing anybody reads about this product — claimed **13
 * Model Context Protocol tools** and named them. Two of the thirteen, `shio_blueprint` and
 * `shio_forget`, **do not exist**: `tools/list` on a running instance returns eleven, and eleven
 * is what the `"shio_*"` literals in `api/agent/mcp/tools/` declare. The CLI row claimed **29
 * verbs** against a registry of thirty. Both had been wrong for some time and nothing could have
 * noticed, because `docs/agents/docs-process.md` records the reason in one line: there are no
 * roadkeep markers in that file and `export --readme` is not wired here, so every number in it is
 * hand-typed and nothing derives it.
 *
 * ## Why this is the deliverable rather than the corrected numbers
 *
 * SH338's rule, and `agents.md` states it as always in scope: when a review finds a class of
 * defect, the instrument that finds every *other* instance lands before the fixes. Correcting
 * "13" to "11" fixes today and restores the exact condition that produced it. The reader this
 * needs already existed — `cli/conformance/support/product-vocabulary.mjs`, written for SH499's
 * documentation instruments — and it reads only in-repo sources, so unlike its two conformance
 * siblings this one needs no instance and no sibling checkout and belongs in `pnpm test`.
 *
 * ## The site is held too, and it is the control
 *
 * `shio-site/src/lib/site-content.ts` lists the same eleven tools and is **correct**, which is the
 * useful half of the finding: the site has a discipline the README does not, because
 * `shio-site`'s own build gates its copy. Holding both here means the two cannot drift apart
 * either — the failure that produced `shio_forget` was one surface being edited alone.
 */
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { describe, test } from "node:test";

import { cliVerbs, mcpTools } from "../conformance/support/product-vocabulary.mjs";

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const readme = readFileSync(join(ROOT, "README.md"), "utf8");
const siteContent = readFileSync(join(ROOT, "shio-site/src/lib/site-content.ts"), "utf8");

/** The one row of the surface table that names the tools. */
const mcpRow = readme.split("\n").find((line) => /Model Context Protocol tools/.test(line));
/** The one row that counts the verbs. */
const cliRow = readme.split("\n").find((line) => /\bverbs\b/.test(line) && line.startsWith("|"));

const namesIn = (text) => new Set([...text.matchAll(/shio_[a-z_]+/g)].map(([name]) => name));

describe("SH499: the README's surface table is derived from the product, not remembered", () => {
  test("every MCP tool the README names is one the server registers", () => {
    assert.ok(mcpRow, "the surface table no longer has a row naming the MCP tools");
    const tools = mcpTools();
    const claimed = [...namesIn(mcpRow)];
    const invented = claimed.filter((name) => !tools.has(name));
    assert.deepEqual(
      invented,
      [],
      "SH499: README.md names an MCP tool this product does not have. This is the front door — a "
        + "reader who tries one of these gets `Unknown tool` from tools/call and no reason to "
        + `believe the rest of the page.\n  registered: ${[...tools].sort().join(" ")}`,
    );
    const missing = [...tools].filter((name) => !claimed.includes(name));
    assert.deepEqual(
      missing,
      [],
      "SH499: a tool this server registers is missing from the README's list, so the count below "
        + "is right for the wrong reason and a real capability is unadvertised.",
    );
  });

  test("the tool count in the README is the number of tools", () => {
    const counted = /(\d+)\s+Model Context Protocol tools/.exec(mcpRow);
    assert.ok(counted, "the MCP row no longer states a count, which is the thing that rots");
    assert.equal(
      Number(counted[1]),
      mcpTools().size,
      "SH499: README.md counts a different number of MCP tools than the product has. It said 13 "
        + "when there were 11, for long enough that nobody knows when it stopped being true — "
        + "which is what a hand-typed number in an ungated file is for.",
    );
  });

  test("the verb count in the README is the size of the command registry", () => {
    assert.ok(cliRow, "the surface table no longer has a row counting the CLI verbs");
    const counted = /(\d+)\s+verbs/.exec(cliRow);
    assert.ok(counted, "the CLI row no longer states a count");
    assert.equal(
      Number(counted[1]),
      cliVerbs().size,
      "SH499: README.md counts a different number of CLI verbs than `COMMANDS` in "
        + "cli/src/index.mjs registers. A verb added without touching the README is the whole "
        + "failure mode, and it is one line to keep true.",
    );
  });

  test("the public site and the README name the same tools", () => {
    const site = namesIn(siteContent);
    const tools = mcpTools();
    const wrong = [...site].filter((name) => !tools.has(name));
    assert.deepEqual(
      wrong,
      [],
      "SH499: shio-site names a tool that does not exist. Its rule is that every claim is about a "
        + "real surface, and this is that rule enforced rather than trusted.",
    );
    const absent = [...tools].filter((name) => !site.has(name));
    assert.deepEqual(
      absent,
      [],
      "SH499: a registered tool appears on neither the site's list — the two surfaces drifting "
        + "apart is exactly how README.md came to advertise shio_forget.",
    );
  });
});
