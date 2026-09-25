/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH440 — **an exit code is a contract, and a crash code is not one.**
 *
 * `main().catch` called `process.exit(1)` with a fetch handle still live, and on Windows that was
 * measured aborting: exit **-1073740791** (`0xC0000409`) after the teaching error, with `Assertion
 * failed: !(handle->flags & UV_HANDLE_CLOSING), file src\win\async.c, line 94`. SH434 closed one
 * route to that throw and left the exit alone, so `convert --site <absent>`, `verify --site
 * <absent>` and `resolve post:<absent>/x` all still took it. `convert --check`, `apply --check` and
 * `push --content --check` are drift guards whose entire output is the number a pipeline branches
 * on — there, the exit code *is* the product.
 *
 * Three halves, and the middle one is the instrument rather than the fix:
 *
 * - **The disarm**, against fake dispatchers, because the real hazard cannot be reproduced on
 *   demand: what is checkable is that every versioned `undici.globalDispatcher` slot is closed and
 *   that it happens *before* the process ends. Ordering is the property — disarming after the exit
 *   is the defect with extra code.
 * - **One exit for the whole CLI** (SH322/SH338: the grep is the deliverable). The defect was a
 *   single `process.exit` reached from many commands, so the guard is that no second one appears:
 *   the next `process.exit` written beside a command would be invisible until somebody read an exit
 *   code outside a shell, which is how this survived SH434.
 * - **The exit code from the real binary**, driven at a stub server that fails the request — the
 *   only place an exit code exists. It carries a deadline as well as a code, and the deadline is
 *   what gives it teeth: measured, an idle keep-alive socket holds the loop for undici's 4 s
 *   `keepAliveTimeout`, so `process.exitCode = 1` with no disarm exits **1** after **4082 ms**.
 *   Right code, and the naive fix. Disarming first took **79 ms**. A run that takes longer than
 *   two seconds here has stopped disarming even if the code still reads 1.
 */
import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { createServer } from "node:http";
import { readdir, readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import { test } from "node:test";

import { disarmRequestLayer, finish, requestLayerHandles } from "../src/exit.mjs";

const run = promisify(execFile);
const ENTRY = fileURLToPath(new URL("../src/index.mjs", import.meta.url));
const SRC = fileURLToPath(new URL("../src/", import.meta.url));

/** A `globalThis` stand-in carrying the dispatcher slots Node 26 actually exposes. */
function globalWithDispatchers(record) {
  const global = { unrelated: {} };
  for (const version of [1, 2]) {
    global[Symbol.for(`undici.globalDispatcher.${version}`)] = {
      async destroy() {
        record.push(`destroy.${version}`);
      },
      async close() {
        record.push(`close.${version}`);
      },
    };
  }
  // A slot that is not the request layer, to pin that the sweep is by prefix and not by count.
  global[Symbol.for("some.other.registry")] = { async destroy() { record.push("wrong"); } };
  return global;
}

test("SH440: the disarm closes every versioned dispatcher slot, and nothing else", async () => {
  const record = [];
  const global = globalWithDispatchers(record);

  assert.equal(
    requestLayerHandles(global).length,
    2,
    "Node 26 carries `undici.globalDispatcher.1` and `.2`; matching one version would leave the "
      + "other armed, which is the failure this file could not otherwise see",
  );

  const disarmed = await disarmRequestLayer(global);

  assert.deepEqual(record, ["destroy.1", "destroy.2"]);
  assert.equal(disarmed.length, 2, "and it reports what it closed — finding nothing must not read as success");
  assert.ok(!record.includes("wrong"), "a foreign Symbol.for slot is not the request layer");
});

test("SH440: `close()` is the fallback where a dispatcher has no `destroy()`", async () => {
  const record = [];
  const global = {};
  global[Symbol.for("undici.globalDispatcher.9")] = {
    async close() {
      record.push("close.9");
    },
  };

  assert.deepEqual(await disarmRequestLayer(global), ["Symbol(undici.globalDispatcher.9)"]);
  assert.deepEqual(record, ["close.9"]);
});

test("SH440: a dispatcher that refuses to close does not lose the code", async () => {
  const global = {};
  global[Symbol.for("undici.globalDispatcher.1")] = {
    async destroy() {
      throw new Error("socket is already gone");
    },
  };
  const proc = { exit() {}, exitCode: 0 };

  await finish(3, { proc, global, schedule: () => ({ unref() {} }) });

  assert.equal(proc.exitCode, 3, "the caller decided a code; a teardown error is not a reason to lose it");
});

test("SH445: the request layer is disarmed, and then nothing calls process.exit", async () => {
  const order = [];
  const global = globalWithDispatchers(order);
  const proc = {
    exit(code) {
      order.push(`exit.${code}`);
    },
  };

  const watchdog = await finish(1, { proc, global, schedule: () => ({ unref() { order.push("unref"); } }) });

  assert.deepEqual(
    order,
    ["destroy.1", "destroy.2", "unref"],
    "SH440 asserted `exit.1` at the end of this list, and that call was the surviving crash: with a "
      + "request behind it, `process.exit` runs while a piped stdout write is still in flight and "
      + "libuv signals the completion on a closing handle. The disarm is what makes returning "
      + "affordable; returning is what fixes it",
  );
  assert.equal(proc.exitCode, 1, "the code is set before the await, so a teardown that dies still carries it");
  assert.ok(watchdog, "and a watchdog is armed for the case a loop is held by something else");
});

test("SH1093: the exit reports what it disarmed only when asked, so a slow exit says why", async () => {
  const lines = [];
  const proc = {
    exit() {}, exitCode: 0, env: { SHIO_EXIT_TRACE: "1" },
    getActiveResourcesInfo: () => ["PipeWrap"],
    stderr: { write: (text) => lines.push(text) },
  };
  await finish(1, { proc, global: globalWithDispatchers([]), schedule: () => ({ unref() {} }) });
  assert.deepEqual(lines, ["shio-exit: disarmed 2 slot(s); active PipeWrap\n"],
    "SH1093: a red keep-alive run could not say whether the layer was disarmed or something else "
      + "held the loop");

  const quiet = [];
  await finish(1, {
    proc: { ...proc, env: {}, stderr: { write: (text) => quiet.push(text) } },
    global: {}, schedule: () => ({ unref() {} }),
  });
  assert.deepEqual(quiet, [], "SH1093: every caller that did not ask keeps a clean stderr");
});

test("SH445: the watchdog is unref'd, so it never delays a healthy exit", async () => {
  // The whole reason it is safe to arm at all. An unref'd timer does not hold the loop, so on the
  // ordinary path the process is gone long before it fires — it exists only for the case SH440 kept
  // `process.exit` for, where a hang would otherwise be the outcome.
  let unrefs = 0;
  const armed = [];
  const proc = { exit() {}, exitCode: 0 };
  const schedule = (fn, ms) => {
    armed.push(ms);
    return { unref: () => { unrefs += 1; } };
  };

  await finish(1, { proc, global: {}, schedule });

  assert.equal(unrefs, 1, "unref'd exactly once — a ref'd watchdog would add five seconds to every failure");
  assert.deepEqual(armed, [5000]);
});

test("SH440: `process.exit` lives in exit.mjs and nowhere else in the CLI", async () => {
  const offenders = [];
  for (const file of await sources(SRC)) {
    if (file.endsWith("exit.mjs")) continue;
    const code = stripComments(await readFile(file, "utf8"));
    if (/\bprocess\.exit\s*\(/.test(code) || /\bexit\b[^\n]*from\s+"node:process"/.test(code)) {
      offenders.push(file.slice(SRC.length));
    }
  }
  assert.deepEqual(
    offenders,
    [],
    "SH440 replaced one `process.exit` reached from many commands with one exit path that disarms "
      + "the request layer first. A second call site brings the abort back for whichever command "
      + "owns it, and nothing else in the suite would notice: import `finish` from ./exit.mjs",
  );
});

/**
 * SH445 — the response the live instance actually sends, header for header.
 *
 * <p>This is the difference between a test that passes and a test that <em>reproduces</em>. SH440's
 * stub below answers with two headers and a short body, and the binary exits 1 against it at the
 * broken revision as well as the fixed one — which is why SH440 shipped believing it was done.
 * Bisected, deterministically over three runs: the two-header and the `set-cookie` variants exit 1,
 * and only the full set reproduces the abort. The trigger is a threshold rather than any one header's
 * meaning, so the honest thing is to send what the instance sends and not to trim it to taste.</p>
 */
const LIVE_INSTANCE_HEADERS = {
  "content-type": "application/problem+json",
  "set-cookie": ["XSRF-TOKEN=651a74ee; Path=/; HttpOnly", "SHJSESSIONID=E441F709; Path=/; HttpOnly"],
  "x-xsrf-token": "651a74ee",
  "access-control-expose-headers": "X-XSRF-TOKEN",
  "x-shio-est-tokens": "78",
  "x-content-type-options": "nosniff",
  "x-xss-protection": "0",
};

test("SH445: the real binary exits 1 against the response a live instance sends", async () => {
  // The assertion SH440 needed and did not have. At the shipping revision this exits **-1073740791**
  // — 0xC0000409, Windows' abort() — after printing the teaching error, on `resolve` and on `verify`,
  // against the instance and against this stub alike. Two tasks in a row were measured against a
  // harness rather than against the failing command; this is the harness that fails first.
  const server = createServer((req, res) => {
    res.writeHead(404, LIVE_INSTANCE_HEADERS);
    res.end(JSON.stringify({
      type: "https://shio.viglet.com/errors/unknown-site",
      title: "No readable site 'absent-xyz'",
      detail: "No site with that id or name is visible to this credential.",
      fix: "Address a site that exists, or add a `site.upsert` op for it earlier in the same batch.",
      example: "{\"op\":\"site.upsert\",\"address\":\"site:absent-xyz\"}",
    }));
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();

  try {
    for (const argv of [["resolve", "post:absent-xyz/x"], ["verify", "--site", "absent-xyz"]]) {
      const failure = await run(process.execPath, [
        ENTRY, ...argv, "--url", `http://127.0.0.1:${port}`, "--user", "u", "--password", "p",
      ]).then(() => null, (error) => error);

      assert.ok(failure, `shio ${argv[0]} should exit non-zero when the instance refuses`);
      assert.equal(
        failure.code,
        1,
        `\`shio ${argv.join(" ")}\` exited ${failure.code}. -1073740791 (and 3221226505 unsigned) is `
          + "0xC0000409 — abort(). The exit path called process.exit with a piped stdout write still "
          + "in flight, which libuv completes on a handle it is closing",
      );
      assert.doesNotMatch(
        failure.stderr,
        /UV_HANDLE_CLOSING|Assertion failed/,
        "and no native assertion under a Shio command, which reads as a Shio defect",
      );
    }
  } finally {
    server.close();
  }
});

test("SH440: the real binary exits 1 for a remote failure, and does not wait out keep-alive", async () => {
  const server = createServer((req, res) => {
    // A teaching body (SH80) over a socket the server keeps open — the state a thrown HTTP error
    // leaves behind, and the one measured holding the loop for 4 s without the disarm.
    res.writeHead(409, { "content-type": "application/json", connection: "keep-alive" });
    res.end(JSON.stringify({ title: "no such site", detail: "site `absent-xyz` is not on this instance" }));
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const { port } = server.address();
  const url = `http://127.0.0.1:${port}`;

  try {
    for (const argv of [
      ["resolve", "post:absent-xyz/hello"],
      ["verify", "--site", "absent-xyz"],
    ]) {
      const started = process.hrtime.bigint();
      const failure = await run(process.execPath, [ENTRY, ...argv, "--url", url, "--user", "u", "--password", "p"],
        { env: { ...process.env, SHIO_EXIT_TRACE: "1" } })
        .then(() => null, (error) => error);
      const ms = Number(process.hrtime.bigint() - started) / 1e6;

      assert.ok(failure, `shio ${argv[0]} should exit non-zero when the instance refuses`);
      assert.equal(
        failure.code,
        1,
        `\`shio ${argv.join(" ")}\` exited ${failure.code}. -1073740791 is 0xC0000409 — abort() — and `
          + "means process.exit ran while a fetch handle was still live",
      );
      // SH1093 — two claims, asserted separately, so a red says which one broke. The disarm is
      // observed directly, from the child's own trace; the wall clock only has to tell "closed" from
      // "waited the 4 s keep-alive out", so its bound sits under that and far above any scheduling
      // delay a loaded machine adds (50 idle and 30 loaded runs measured none over 2 s).
      const trace = failure.stderr.match(/shio-exit: disarmed (\d+) slot\(s\); active (.*)/);
      assert.ok(trace, `\`shio ${argv[0]}\` left no exit trace, so the exit path was not finish()`);
      assert.ok(Number(trace[1]) >= 1,
        `\`shio ${argv[0]}\` disarmed ${trace[1]} dispatcher slot(s): the request layer was never found, `
          + "so the socket is left to undici's 4 s keepAliveTimeout");
      assert.ok(
        ms < 3500,
        `\`shio ${argv[0]}\` took ${Math.round(ms)}ms with ${trace[1]} slot(s) disarmed and ${trace[2]} `
          + "still active. ~4000ms is undici's keepAliveTimeout; with the layer disarmed, whatever is "
          + "listed as active is what held the loop",
      );
    }
  } finally {
    server.close();
  }
});

/** Every `.mjs` under `src/`, recursively. */
async function sources(dir) {
  const found = [];
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const path = `${dir}${entry.name}`;
    if (entry.isDirectory()) found.push(...await sources(`${path}/`));
    else if (entry.name.endsWith(".mjs")) found.push(path);
  }
  return found.sort();
}

/**
 * Drop comments, so the guard reads code and not prose about it.
 *
 * <p>`browser.mjs`, `clone.mjs` and `snapshot.mjs` each explain this abort in a comment that spells
 * `process.exit(1)` — SH434's record of it. A grep that counted those would be a guard nobody can
 * make pass.</p>
 */
function stripComments(code) {
  return code.replace(/\/\*[\s\S]*?\*\//g, "").replace(/(^|\s)\/\/.*$/gm, "$1");
}
