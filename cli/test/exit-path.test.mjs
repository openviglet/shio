/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

test("SH440: a dispatcher that refuses to close does not hold up the exit", async () => {
  const global = {};
  global[Symbol.for("undici.globalDispatcher.1")] = {
    async destroy() {
      throw new Error("socket is already gone");
    },
  };
  const ended = [];

  await finish(3, { proc: { exit: (code) => ended.push(code) }, global });

  assert.deepEqual(ended, [3], "the caller decided a code; a teardown error is not a reason to lose it");
});

test("SH440: the request layer is disarmed *before* the process ends", async () => {
  const order = [];
  const global = globalWithDispatchers(order);
  const proc = {
    exit(code) {
      order.push(`exit.${code}`);
    },
  };

  await finish(1, { proc, global });

  assert.deepEqual(
    order,
    ["destroy.1", "destroy.2", "exit.1"],
    "this ordering is the whole fix: `process.exit` with a fetch handle still live is what asserted "
      + "in libuv's teardown and returned 0xC0000409 instead of 1",
  );
  assert.equal(proc.exitCode, 1, "and the code is set before the await, so a teardown that dies still carries it");
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
      const failure = await run(process.execPath, [ENTRY, ...argv, "--url", url, "--user", "u", "--password", "p"])
        .then(() => null, (error) => error);
      const ms = Number(process.hrtime.bigint() - started) / 1e6;

      assert.ok(failure, `shio ${argv[0]} should exit non-zero when the instance refuses`);
      assert.equal(
        failure.code,
        1,
        `\`shio ${argv.join(" ")}\` exited ${failure.code}. -1073740791 is 0xC0000409 — abort() — and `
          + "means process.exit ran while a fetch handle was still live",
      );
      assert.ok(
        ms < 2000,
        `\`shio ${argv[0]}\` took ${Math.round(ms)}ms. Under 100ms is the disarm; ~4000ms is undici's `
          + "keepAliveTimeout, which means the exit is waiting the socket out instead of closing it",
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
