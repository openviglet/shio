/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH698 — a teardown that will not finish must say so.
 *
 * ## What this is, and what it is not
 *
 * `fixture-traits.test.mjs` was seen to fail **at file level with every assertion in it green**,
 * about once in five `pnpm test` runs, printing `'test failed'` and naming nothing. A teardown that
 * never settles is one of the few shapes that reports exactly that, and `server.close(resolve)` is
 * silent about it forever — it waits on open connections with no deadline and no report.
 *
 * This is **not** the cure, and saying so is the point. The two mechanisms §XXI.11 proposed were
 * measured and neither holds: `close` resolves in under a millisecond with eight connections
 * outstanding, and the port has been ephemeral since SH448. Eighteen consecutive runs — twelve
 * ordinary, six at `--test-concurrency=32` — did not reproduce the failure. Nothing here claims to
 * have removed a cause nobody has found.
 *
 * What it removes is the silence. A second occurrence that names the server, the elapsed deadline
 * and the socket count is a second *data point*; a second `'test failed'` is a second mystery.
 */
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { connect } from "node:net";
import { test } from "node:test";

import { CLOSE_DEADLINE_MS, closeFixture, serveFixture } from "../conformance/support/fixture-site.mjs";

/**
 * A listening server of our own, so the deadline can be driven without the fixture's content.
 *
 * <p>`handler` defaults to answering at once. A test that needs a socket `close` cannot reclaim
 * passes one that never answers, so the request arrives and stays in flight.</p>
 */
async function listening(handler = (_req, res) => res.end("ok")) {
  const server = createServer(handler);
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  return { server, port: server.address().port };
}

/** How many connections the server itself reports, or -1 where it cannot say. */
function connections(server) {
  return new Promise((resolve) => server.getConnections((err, count) => resolve(err ? -1 : count)));
}

/**
 * A socket the server has accepted, waited for from the SERVER's end — which is SH725.
 *
 * <p>The client's `connect` fires on the SYN-ACK; the server accepts on a later turn of its own
 * loop, and in between `getConnections()` is 0 and `close` has nothing to wait for. A precondition
 * has to be observed where it becomes true.</p>
 */
async function accept(server, port) {
  const accepted = new Promise((resolve) => server.once("connection", resolve));
  const socket = connect(port, "127.0.0.1");
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });
  await accepted;
  return socket;
}

test("SH698: an ordinary teardown closes well inside the deadline", async () => {
  const source = await serveFixture();
  // The request pattern the suite actually makes, because the refuted theory was about the sockets
  // those leave behind.
  for (const path of ["/", "/traits", "/css/site.css", "/img/hero.png"]) {
    await fetch(`${source.origin}${path}`);
  }

  const started = Date.now();
  await source.close();
  const elapsed = Date.now() - started;

  assert.ok(elapsed < CLOSE_DEADLINE_MS,
    `SH698: an ordinary teardown took ${elapsed}ms against a ${CLOSE_DEADLINE_MS}ms deadline. This `
    + "is the measurement that refuted the keep-alive theory — if it starts failing, that theory "
    + "was right after all and a lingering socket is the cause.");
});

/**
 * SH780 — closing twice is one close, and the second call joins the first.
 *
 * <p>The flake this belongs to was libuv aborting on an async handle already closing, under
 * `--test-force-exit`. The flag is gone (measured: the suite exits on its own, and the abort does
 * not reproduce without it), which removes the race — and closing twice is still a thing a teardown
 * should survive rather than a thing nothing happens to do today. Two `close()` calls used to be two
 * `server.close()` calls, and the second one's callback fires with `ERR_SERVER_NOT_RUNNING`.</p>
 */
test("SH780: closing a fixture twice resolves twice and closes once", async () => {
  const source = await serveFixture();
  await fetch(`${source.origin}/`);

  const first = source.close();
  const second = source.close();
  assert.strictEqual(first, second,
    "SH780: the second call joins the in-flight close rather than starting another — a second "
      + "`server.close()` is what hands libuv a handle that is already closing");

  await assert.doesNotReject(() => Promise.all([first, second]),
    "and neither caller sees ERR_SERVER_NOT_RUNNING, which is what the second close used to answer");
  await assert.doesNotReject(() => source.close(),
    "including a third, after the first has settled: idempotent means idempotent");
});

/**
 * SH1146 — the premise SH698's deadline test rested on, asserted instead of assumed.
 *
 * <p>`closeFixture` calls `closeIdleConnections()`, and an accepted socket that has sent nothing is
 * idle by that definition. Measured on Node 26 Linux: the close then completes in 0ms with the
 * socket still outstanding. SH698's held-socket test held exactly that and so reported, on every
 * Linux runner, the silence it exists to catch — green on Windows, red in CI, for as long as nobody
 * wrote this line down.</p>
 *
 * <p>So this is not a test of the fixture's behaviour for its own sake. It is the assertion that
 * would have caught SH1146 at the boundary it crossed: if an idle socket ever starts holding the
 * close open again, this fails here and names the id, instead of the test below quietly passing for
 * the wrong reason.</p>
 */
test("SH1146: an idle accepted socket does not hold the close, so it cannot hold a deadline", async (t) => {
  const { server, port } = await listening();
  const idle = await accept(server, port);
  t.after(() => {
    idle.destroy();
    server.close();
  });

  const open = await connections(server);
  assert.equal(open, 1,
    `SH1146: the server reports ${open} connection(s) after accepting one, so what follows is not `
    + "measuring a socket at all.");

  const started = Date.now();
  await assert.doesNotReject(() => closeFixture(server, 1000),
    "SH1146: an accepted socket that has sent nothing is idle, and `closeFixture` reclaims it on "
    + "purpose. If this rejects, `closeIdleConnections()` no longer reaches such a socket and the "
    + "test below can go back to holding one — but the change has to be made deliberately, because "
    + "it is the difference between SH698's instrument firing and SH698's instrument being unable "
    + "to fire.");
  const elapsed = Date.now() - started;
  assert.ok(elapsed < 1000,
    `SH1146: the close took ${elapsed}ms, so the idle socket was waited on rather than reclaimed.`);
});

test("SH698: a held socket makes the deadline fire, and the reason names the state", async (t) => {
  // A request that arrives and is never answered — the one state `close` cannot reclaim, and
  // exactly what it says nothing about.
  //
  // **Not merely an accepted socket, which is SH1146.** A connection that has sent nothing is
  // *idle*, `closeFixture` closes idle connections by design, and on Node 26 Linux the teardown
  // then genuinely finishes — so this test passed on Windows and reported on every Linux runner the
  // very defect it exists to find. An in-flight request makes the connection **active** on every
  // platform, which is a premise that does not move with the runtime.
  let arrived;
  const request = new Promise((resolve) => { arrived = resolve; });
  const { server, port } = await listening(() => arrived());

  const held = await accept(server, port);
  held.write("GET /never-answered HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");
  await request;
  t.after(() => {
    held.destroy();
    server.close();
  });

  const open = await connections(server);
  assert.equal(open, 1,
    `SH725: the server reports ${open} connection(s) after accepting one. Everything below measures `
    + "what `close` does with a socket outstanding, so a run that starts without one is not "
    + "measuring it — which is how this test came to assert the opposite of what it checks.");

  const error = await closeFixture(server, 150).then(() => null, (e) => e);

  assert.ok(error,
    "SH698: a server with a live socket reported itself closed. That is the silence this whole "
    + "task is about — the teardown neither finishes nor complains.");
  assert.match(error.message, /SH698/,
    "SH698: the failure has to carry the id, or the next person reading a red run has the same "
    + "nothing the first one had.");
  assert.match(error.message, /did not close within 150ms/,
    "SH698: it must say how long it waited. 'Slow' and 'stuck' are different findings and the "
    + "elapsed deadline is what separates them.");
  assert.match(error.message, /1 connection\(s\) still open/,
    "SH698: and how many sockets. A non-zero count means a request outlived its test; zero means "
    + "`close` is waiting on something that is not a socket. Those point at different bugs, and "
    + "this message is the only place the distinction gets drawn.");
});

test("SH698: a clean close leaves no timer behind", async () => {
  // An instrument that made every suite pay its own deadline at teardown would have traded one
  // defect for another, so this checks the resource rather than the clock.
  //
  // Measured, because the first version of this test asserted more than it checked: removing
  // `clearTimeout` alone does NOT fail it — `getActiveResourcesInfo` does not report an `unref`'d
  // handle — and removing `clearTimeout` *and* `unref` does. So what it guards is the pair: after a
  // clean close the deadline must not be a live handle. A timer that leaks but stays `unref`'d
  // still slips past, and nothing here pretends otherwise.
  const before = process.getActiveResourcesInfo().filter((r) => r === "Timeout").length;
  const { server } = await listening();

  await closeFixture(server, 10_000);

  const after = process.getActiveResourcesInfo().filter((r) => r === "Timeout").length;
  assert.equal(after, before,
    `SH698: the deadline timer outlived a clean close (${before} -> ${after}). It must be cleared `
    + "by `close`'s own callback; a timer that only stops mattering because it was `unref`'d still "
    + "fires, and this instrument is supposed to cost a closed fixture nothing at all.");
});
