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

/** A listening server of our own, so the deadline can be driven without the fixture's content. */
async function listening() {
  const server = createServer((_req, res) => res.end("ok"));
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  return { server, port: server.address().port };
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

test("SH698: a held socket makes the deadline fire, and the reason names the state", async (t) => {
  const { server, port } = await listening();

  // A raw connection that sends nothing and closes nothing — exactly the state `server.close`
  // waits on, and exactly what it says nothing about.
  //
  // **Waited for from the SERVER's end, which is SH725.** The client's `connect` fires on the
  // SYN-ACK; the server accepts on a later turn of its own loop, and in between `getConnections()`
  // is 0 and `close` has nothing to wait for — so it completes, no deadline fires, and this test
  // reports the very defect SH698 exists to find. Forty attempts on Windows never reproduced it and
  // a Linux runner reddened the gate. A precondition has to be observed where it becomes true.
  const accepted = new Promise((resolve) => server.once("connection", resolve));
  const held = connect(port, "127.0.0.1");
  await new Promise((resolve, reject) => {
    held.once("connect", resolve);
    held.once("error", reject);
  });
  await accepted;
  t.after(() => {
    held.destroy();
    server.close();
  });

  const open = await new Promise((resolve) => server.getConnections((err, count) => resolve(err ? -1 : count)));
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
