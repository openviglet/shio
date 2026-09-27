/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH365 — one flag, one meaning, and the lint that finds the next command to break it.
 *
 * ## What SH365 filed, and what a grep turned up
 *
 * The task named `shio snapshot`: it read the target as `flags.address ?? flags.url ?? positionals[0]`
 * while `resolveAdmin` read `flags.url` as the **instance**, so one flag named the page in one line
 * and the server in the next. It did not bite, because the two readings only meet when a caller
 * passes both — and SH265 had made it load-bearing by giving `--url` a third job (`previewBase`, the
 * origin a front-endless site is photographed from). A flag that silently decides which *server* a
 * picture came from produces a plausible-looking artifact, which is the one failure `resolveUrl`'s own
 * comment refuses to risk when it declines to guess an origin.
 *
 * `shio digest` had it too, identically, and nothing had said so. That is SH322's rule: **when a task
 * says "and whatever a grep turns up", the grep is the deliverable** — it lands as a lint *before* the
 * fixes, because that is what finds the call site nobody read. Two commands had the defect and one
 * task named one of them; a hand-written list of "the commands to fix" would have passed forever and
 * said nothing about the second.
 *
 * ## The property, and why it is this one
 *
 * Not "no command reads `flags.url`" — `shio clone` legitimately does, because the thing it takes
 * *is* a URL and it never talks to an instance at all. The defect is narrower and is the one that can
 * be wrong at runtime: **a command that resolves an instance must not read `flags.url` as anything
 * else.** Those two readings in one function are the collision; either alone is a flag with one
 * meaning.
 *
 * Both halves are asserted. The colliding set must be **empty**, and the sole-meaning set is asserted
 * *exactly*, so a new command that gives `--url` a private meaning is a reviewable diff with a reason
 * attached rather than a silent second dialect.
 */
import assert from "node:assert/strict";
import { readFile, readdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { test } from "node:test";

import { uncommented } from "./support/source-text.mjs";

const COMMANDS = join(dirname(fileURLToPath(import.meta.url)), "..", "src", "commands");

/**
 * Commands that read `flags.url` and resolve **no** instance, so the flag has one meaning there.
 *
 * Asserted exactly. Adding an entry is a decision about the CLI's vocabulary — an agent that learned
 * `--url` from fifteen commands and meets a sixteenth where it means something else learns it by
 * being wrong.
 */
const SOLE_MEANING = {
  // `snapshot.mjs` and `digest.mjs` are deliberately absent: SH365's fix moved their legacy reading
  // into `pageTarget`, which lives in `snapshot.mjs`'s *pure* module and is handed a `warn` callback
  // by the two commands that owe anybody backward compatibility. So the deprecation still works and
  // neither command's own source names `flags.url` — which is what makes this lint's set exactly one.
  "clone.mjs":
    "the site to crawl. `clone` is the one command that never talks to a Shio instance — it has no "
    + "`resolveAdmin`, no credentials and no base URL — so `--url` cannot collide with anything here. "
    + "It is also the only command where the *subject* is somebody else's origin.",
};

/**
 * Comments are stripped before the scan, because prose about the defect is not the defect. `audit.mjs`
 * documents SH365 in its header and would otherwise be reported for describing what it avoids.
 */
function code(source) {
  // SH1147 — the shared per-line reading; the cross-line strip this replaced could open inside a
  // string literal and delete the rest of the file.
  return uncommented(source);
}

async function scan() {
  const readsUrl = [];
  const colliding = [];
  for (const name of (await readdir(COMMANDS)).filter((each) => each.endsWith(".mjs")).sort()) {
    const source = code(await readFile(join(COMMANDS, name), "utf8"));
    if (!/\bflags\.url\b/.test(source)) continue;
    readsUrl.push(name);
    if (/\bresolveAdmin\s*\(/.test(source)) colliding.push(name);
  }
  return { readsUrl, colliding };
}

test("SH365: no command reads --url as both the page and the instance", async () => {
  const { colliding } = await scan();

  assert.deepEqual(
    colliding,
    [],
    "these commands read `flags.url` *and* resolve an instance from it, so one flag has two "
      + "meanings in one function — the SH365 defect. Read the target from `--page`/`--address`/a "
      + "positional and leave `--url` meaning the instance, as it does everywhere else.",
  );
});

test("SH365: every command giving --url a private meaning is declared, with a reason", async () => {
  const { readsUrl } = await scan();

  assert.deepEqual(
    readsUrl.sort(),
    Object.keys(SOLE_MEANING).sort(),
    "a command reads `flags.url` and is not declared in SOLE_MEANING. Either read the instance "
      + "through `resolveAdmin(flags, ctx.env)` like every other command, or declare it here with "
      + "the reason its meaning is different — a second dialect nobody wrote down is one an agent "
      + "learns by being wrong.",
  );
  for (const [name, why] of Object.entries(SOLE_MEANING)) {
    assert.ok(why && why.length > 40, `${name}'s entry needs a reason, not a label`);
  }
});

test("SH365: the guard is not vacuous — it can see a collision", async () => {
  // If the scan could not find `resolveAdmin` or `flags.url` at all, both assertions above would
  // pass forever on an empty set, which is the lint-that-reports-zero failure.
  const snapshot = code(await readFile(join(COMMANDS, "snapshot.mjs"), "utf8"));
  const digest = code(await readFile(join(COMMANDS, "digest.mjs"), "utf8"));

  assert.match(snapshot, /resolveAdmin\s*\(/, "the scan's collision half looks for this");
  assert.match(digest, /resolveAdmin\s*\(/);
  // And the other half: something in the tree does read `flags.url`, or the set is empty by accident.
  const { readsUrl } = await scan();
  assert.ok(readsUrl.length > 0, "no command reads flags.url — the scan is finding nothing");
});
