/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH434 — **a refusal that crashes is not a refusal.**
 *
 * With `playwright` absent, `snapshot`, `snapshot --against` and `audit` printed the intended
 * teaching error and then emitted `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING), file
 * src\win\async.c, line 94` and exit **-1073740791** — `0xC0000409`, Windows' code for `abort()`.
 * `main().catch` calls `exit(1)`, so the intent was right and something live was still armed when
 * `process.exit` ran: the browser was resolved *after* the instance had been asked for its sites (and
 * for `clone --render`, after robots.txt had been fetched from somebody else's server).
 *
 * Two reasons it mattered more than a cosmetic exit code. `snapshot --against` is the one command
 * whose gate is on by default (SH386), so a caller distinguishing "the replica differs" from "no
 * browser installed" cannot when both are a crash — and a crash code is not a contract. And a native
 * assertion printed under a Shio command reads as a Shio defect, arriving after a message written to
 * be actionable.
 *
 * So the two halves below, and the first is the one that guards the fix:
 *
 * - **The ordering.** The refusal must arrive with **nothing armed** — asserted as zero requests,
 *   with a `post:` target on the two commands that would otherwise have resolved it against the
 *   instance first. That is the property, and it is checkable without reproducing the abort.
 * - **The exit code**, from the real binary as a child process, which is the only place an exit code
 *   exists. It is skipped where `playwright` resolves: the refusal cannot be reached there, and a
 *   test that quietly passed by running a different path would be worse than one that says so.
 */
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";

import { INSTALL, launchBrowser, requireBrowser } from "../src/browser.mjs";
import * as audit from "../src/commands/audit.mjs";
import * as clone from "../src/commands/clone.mjs";
import * as snapshot from "../src/commands/snapshot.mjs";

const run = promisify(execFile);
const ENTRY = fileURLToPath(new URL("../src/index.mjs", import.meta.url));

/** A context whose `playwright` is missing, and which counts anything that would have been armed. */
function ctxWithoutBrowser() {
  const state = { fetched: [], wrote: [] };
  return {
    state,
    env: {},
    cwd: "/repo",
    stdout: sink(),
    stderr: sink(),
    async import(name) {
      // What Node throws for a package that is not there. `optional`/`requireBrowser` catch it, so
      // the shape matters less than the fact that it rejects — but a plausible one keeps this test
      // honest about what it is standing in for.
      const error = new Error(`Cannot find package '${name}'`);
      error.code = "ERR_MODULE_NOT_FOUND";
      throw error;
    },
    async fetch(url) {
      state.fetched.push(String(url));
      throw new Error("this test asserts nothing is fetched before the refusal");
    },
    fs: {
      async mkdir() {},
      async writeFile(path) {
        state.wrote.push(String(path));
      },
      async readFile(path) {
        const error = new Error(`ENOENT: ${path}`);
        error.code = "ENOENT";
        throw error;
      },
    },
  };
}

function sink() {
  const out = { text: "" };
  out.write = (chunk) => {
    out.text += chunk;
  };
  return out;
}

/**
 * Every command that needs a browser, with a target that makes the *old* order observable.
 *
 * `post:Acme/hello` is the point for `snapshot` and `audit`: it is the target that used to send the
 * instance a `context?include=sites` request before the refusal. An absolute URL would refuse
 * cleanly even with the old code, which is why the first measurement of this defect needed a live
 * instance to see at all.
 */
const NEEDS_A_BROWSER = [
  {
    name: "snapshot",
    call: (ctx) => snapshot.run({}, ["post:Acme/hello"], ctx),
    says: "shio snapshot: this command needs playwright",
    alternative: "shio digest",
  },
  {
    name: "snapshot --against",
    call: (ctx) => snapshot.run({ against: "https://acme.example.com/" }, ["post:Acme/hello"], ctx),
    says: "shio snapshot: this command needs playwright",
    alternative: "shio digest",
  },
  {
    name: "audit",
    call: (ctx) => audit.run({}, ["post:Acme/hello"], ctx),
    says: "shio audit: this command needs playwright",
    alternative: "shio verify",
  },
  {
    // Not in the three the defect was reported for, and the same defect: `--render` resolved the
    // browser when the crawl asked for its first page, so the refusal arrived after robots.txt had
    // been fetched. The file already carried the rule — SH378's comment says the asset-site check
    // runs "before any request at all, including robots.txt" — one line above the import that did
    // not follow it.
    name: "clone --render",
    call: (ctx) => clone.run({ render: true }, ["https://acme.example.com"], ctx),
    says: "shio clone: --render needs playwright",
    alternative: "the crawl reads the HTML the server sent",
  },
];

for (const command of NEEDS_A_BROWSER) {
  test(`SH434: \`shio ${command.name}\` refuses a missing browser before anything is armed`, async () => {
    const ctx = ctxWithoutBrowser();

    const failure = await command.call(ctx).then(() => null, (error) => error);

    assert.ok(failure, "a run that cannot take a picture must not report success");
    assert.match(failure.message, new RegExp(command.says.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")));
    assert.ok(
      failure.message.includes(INSTALL),
      "both halves of the install: the package and the browser are two states, and a message that "
        + "names one leaves the caller one command short",
    );
    assert.ok(
      failure.message.includes(command.alternative),
      "and what to do instead without a browser, which is why the dependency is optional at all",
    );
    assert.deepEqual(
      ctx.state.fetched,
      [],
      "nothing was requested before the refusal. This is the fix: the process had a live connection "
        + "open when it exited, and `process.exit(1)` was measured aborting rather than exiting — so "
        + "a gate read 0xC0000409 where the CLI intended 1",
    );
    assert.deepEqual(ctx.state.wrote, [], "and nothing was written either");
  });
}

test("SH434: the real binary exits 1, not 0xC0000409", async (t) => {
  const installed = await import("playwright").then(() => true, () => false);
  if (installed) {
    // Deliberately not a pass. With the package present the refusal is unreachable, and asserting
    // the exit code of a *different* path is how a guard starts reporting on something else.
    t.skip("playwright resolves here, so the refusal this pins cannot be reached");
    return;
  }

  for (const argv of [
    ["snapshot", "https://acme.example.com/"],
    ["snapshot", "https://acme.example.com/", "--against", "https://original.example.com/"],
    ["audit", "https://acme.example.com/"],
    ["clone", "https://acme.example.com", "--render"],
  ]) {
    const failure = await run(process.execPath, [ENTRY, ...argv])
      .then(() => null, (error) => error);

    assert.ok(failure, `shio ${argv[0]} should exit non-zero without a browser`);
    assert.equal(
      failure.code,
      1,
      `\`shio ${argv.join(" ")}\` exited ${failure.code}. -1073740791 is 0xC0000409 — abort() — `
        + "which means something was still armed when the refusal called process.exit",
    );
    assert.match(failure.stderr, /needs playwright, which is not installed/);
  }
});

/**
 * SH444 — **the optional dependency is optional, not absent.**
 *
 * The complement of everything above, and the reason it needs saying: the tests in this file prove
 * the refusal is *good*, and every other browser test injects its capture through `ctxWith`, so the
 * whole suite passed while `snapshot` and `audit` could not run at all in a checkout that had the
 * package **and** the browsers. `shio-react` declared `playwright` as a devDependency, pnpm's strict
 * isolation put it in `shio-react/node_modules`, and a bare specifier resolves from the *importing*
 * module upward — `cli/src/`, `cli/node_modules`, the root — never through a sibling. So the teaching
 * error was correct for a consumer of `@viglet/shio`, whose own `pnpm add -D playwright` *is* on that
 * upward path, and in this repo it read as advice already followed that changed nothing.
 *
 * They are root devDependencies now, which is the only placement visible to both consumers by the
 * same walk (`cli/src/**` and `shio-react/e2e/console-check.mjs`). This asserts that, because nothing
 * else would: move them back under one package and every test here still passes.
 *
 * Workspace-only by construction — `files: ["src"]` means the published package carries no tests, and
 * a devDependency is not installed by consumers, so this can never fire on somebody else's install.
 */
test("SH444: the browser dependencies resolve from the CLI in this checkout", async () => {
  for (const name of ["playwright", "pixelmatch", "pngjs"]) {
    const resolved = await import(name).then(() => true, () => false);
    assert.ok(
      resolved,
      `\`${name}\` does not resolve from cli/test. It is a **root** devDependency for exactly this `
        + "reason: pnpm keeps a package under the workspace that declares it, and a bare specifier "
        + "never resolves through a sibling — so declaring it in shio-react left the CLI's commands "
        + "refusing in a repo that had it. Run `pnpm install` at the root; if that does not fix it, "
        + "check whether it was moved back into a package's own devDependencies",
    );
  }
});

/**
 * SH726 — **the package is here and the browser is not**, which is the state a runner is in.
 *
 * `browser.mjs` named this second state in its header and decided not to pre-empt it, correctly:
 * whether the package resolves is answerable from a path and whether the binary is downloaded is
 * not, so guessing would refuse machines that work. What was missing is the other half — nothing
 * translated what `launch()` then says, so the reader got Playwright's own box drawing naming
 * `pnpm exec playwright install` and never the line this product publishes in three commands' help.
 *
 * Found by CI, on the one environment where that state is the *normal* one: a plain `pnpm install`
 * fetches the package and not the binary, and `source-corpus.conformance.mjs` holds SH434's rule
 * that a machine with no browser gets the two commands that fix it.
 *
 * Asserted against a fake, because the real second state cannot be produced without deleting
 * somebody's browsers — and the message Playwright prints is the contract being matched, so it is
 * reproduced here verbatim rather than paraphrased.
 */
const PLAYWRIGHT_SAYS = "browserType.launch: Executable doesn't exist at "
  + "/home/runner/.cache/ms-playwright/chromium_headless_shell-1234/chrome-headless-shell-linux64/"
  + "chrome-headless-shell\n"
  + "╔════════════════════════════════════════════════════════════╗\n"
  + "║ Looks like Playwright was just installed or updated.       ║\n"
  + "║ Please run the following command to download new browsers: ║\n"
  + "║     pnpm exec playwright install                           ║\n"
  + "╚════════════════════════════════════════════════════════════╝";

/** A `playwright` whose module resolves and whose chromium will not start. */
function playwrightWithNoBrowser(message = PLAYWRIGHT_SAYS) {
  return {
    chromium: {
      launch: async () => {
        throw new Error(message);
      },
    },
  };
}

test("SH726: a resolved playwright with no browser refuses with this product's install line", async () => {
  const ctx = { env: {}, import: async () => playwrightWithNoBrowser() };
  const playwright = await requireBrowser(
    { command: "snapshot", why: "The browser-free check is `shio digest`." },
    ctx,
  );

  const failure = await launchBrowser(playwright, ctx).then(() => null, (error) => error);

  assert.ok(failure, "SH726: a chromium that cannot start reported success.");
  assert.match(failure.message, new RegExp(INSTALL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
    "SH726: the refusal has to carry the two commands this project publishes. Playwright's own "
    + "message names `pnpm exec playwright install`, which is a third instruction for a reader who "
    + "has already been given one — and it is not the one the docs and `--help` state.");
  assert.match(failure.message, /shio snapshot:/,
    "SH726: and it has to say which command refused, the way the not-installed refusal does — the "
    + "whole point of remembering what was asked for is that both states read alike.");
  assert.match(failure.message, /The browser-free check is `shio digest`\./,
    "SH726: including the sentence that points at the browser-free alternative, which is per-command "
    + "and is the only part of the message a caller cannot guess.");
});

test("SH726: a launch that failed for any other reason keeps its own message", async () => {
  // The guard on the translation. Relabelling an unrelated failure as a missing install sends the
  // reader to a command that changes nothing, which is worse than the raw error they would have had.
  const ctx = { env: {}, import: async () => playwrightWithNoBrowser("Target page, context or browser has been closed") };
  const playwright = await requireBrowser({ command: "audit", why: "irrelevant" }, ctx);

  const failure = await launchBrowser(playwright, ctx).then(() => null, (error) => error);

  assert.ok(failure, "SH726: the fake was supposed to throw.");
  assert.equal(failure.message, "Target page, context or browser has been closed",
    "SH726: only a failure that says the executable is missing may be re-labelled. Anything else is "
    + "a different problem wearing this one's fix.");
  assert.doesNotMatch(failure.message, /playwright install/,
    "SH726: and it must not acquire an install line it has no use for.");
});
