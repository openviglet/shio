/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH440 — **the CLI's one exit.**
 *
 * `main().catch` called `process.exit(1)` while a fetch handle was still live, and on Windows that
 * was measured aborting instead of exiting: `Assertion failed: !(handle->flags &
 * UV_HANDLE_CLOSING), file src\win\async.c, line 94`, exit **-1073740791** — `0xC0000409`,
 * Windows' code for `abort()`. SH434 removed one way to *reach* that throw with a handle armed
 * (the optional browser is now resolved before anything is requested). It did not touch the exit,
 * so every other thrown remote error still took it: `convert --site <absent>`, `verify --site
 * <absent>`, `resolve post:<absent>/x`. What crashes is an error thrown from a path that already
 * made an HTTP request; a command that instead *returns* a non-zero `exitCode` unwinds normally
 * first and was always fine.
 *
 * It matters because for `convert --check`, `apply --check` and `push --content --check` the exit
 * code **is** the product — a drift guard's whole output is a number a pipeline branches on, and a
 * crash code cannot be told apart from a runner killing the process. Non-zero is the safe
 * direction, which is why it sat unnoticed.
 *
 * So the fix is here rather than in any command: disarm the request layer, then exit. Two
 * measurements shaped it, both on an idle keep-alive socket — the state a thrown HTTP error leaves
 * behind once the client has read the failed body:
 *
 * - **`process.exitCode = 1` and return is not the fix.** The loop does drain, after undici's 4 s
 *   `keepAliveTimeout`: measured **4082 ms** to exit 1. Every failed remote command would pay it,
 *   and a request the server never answers holds the loop open with no timeout at all.
 * - **Disarming first, then exiting, took 79 ms** — and the abort's precondition is gone, because
 *   by the time `process.exit` runs there is no fetch handle left to assert on.
 *
 * `exit()` stays rather than being replaced by `exitCode`: it is what guarantees termination when
 * something *other* than the request layer holds the loop, and after the disarm it is safe. The
 * success path deliberately does not come through here — `process.exit(0)` truncates a pending
 * stdout write on a pipe, and `shio report --json` is a large one.
 */

/**
 * The prefix every undici global-dispatcher slot on `globalThis` shares.
 *
 * <p>Node's `fetch` reads its dispatcher from a `Symbol.for()` slot, and the slot is versioned —
 * Node 26 carries `undici.globalDispatcher.1` **and** `.2`. Matching the prefix rather than naming
 * a version is the point: a Node upgrade that renumbers the slot must not silently leave the
 * request layer armed, which is a failure this file would not otherwise notice.</p>
 */
const DISPATCHER_SLOT = "Symbol(undici.globalDispatcher";

/**
 * Every dispatcher `fetch` may have left armed on `global`, in slot order.
 *
 * <p>Exported because it is the interesting half to test: the disarm is only as good as what it
 * finds, and finding nothing is indistinguishable from succeeding.</p>
 */
export function requestLayerHandles(global = globalThis) {
  return Object.getOwnPropertySymbols(global)
    .filter((slot) => String(slot).startsWith(DISPATCHER_SLOT))
    .map((slot) => ({ slot: String(slot), dispatcher: global[slot] }))
    .filter(({ dispatcher }) =>
      typeof dispatcher?.destroy === "function" || typeof dispatcher?.close === "function");
}

/**
 * Close what the request layer left open, and report which slots were disarmed.
 *
 * <p>`destroy()` over `close()`: `close()` waits for in-flight requests to finish, which on an
 * error path is waiting for the request that already failed. `destroy()` aborts them, which is the
 * whole intent — nothing after this call is going to read a response.</p>
 *
 * <p>A dispatcher that refuses to close must not stop the exit. Swallowing that is deliberate: the
 * caller is on its way out with a code already decided, and a teardown error printed after a
 * teaching one is the noise SH434 was about.</p>
 */
export async function disarmRequestLayer(global = globalThis) {
  const disarmed = [];
  for (const { slot, dispatcher } of requestLayerHandles(global)) {
    try {
      await (typeof dispatcher.destroy === "function" ? dispatcher.destroy() : dispatcher.close());
      disarmed.push(slot);
    } catch {
      // Nothing to say and nowhere useful to say it; see above.
    }
  }
  return disarmed;
}

/**
 * How long a disarmed process may still be busy before it is ended by force (SH445).
 *
 * <p>The watchdog is `unref`'d, so it does not hold the loop open and never fires on a healthy run —
 * the process is gone long before it. It exists only for the case SH440 kept `process.exit` for: a
 * loop held by something that is *not* the request layer, where a hang is worse than a hard exit.</p>
 */
const FORCE_EXIT_AFTER_MS = 5000;

/**
 * End the process with `code`, with nothing from the request layer still armed.
 *
 * <p>The only place the CLI exits non-zero. `exit-path.test.mjs` asserts that, because the defect
 * this replaces was one `process.exit` call reached from many commands — and the next one added
 * beside a command would be invisible until somebody read an exit code outside a shell.</p>
 *
 * <h2>It does not call `process.exit`, and that is the fix (SH445)</h2>
 *
 * <p>SH440 disarmed the request layer and then called `process.exit` anyway, reasoning that the call
 * guarantees termination. It kept the crash: measured against a live instance at the shipping
 * revision, `resolve post:&lt;absent&gt;/x` and `verify --site &lt;absent&gt;` still printed their
 * teaching error, then the libuv assertion, then **-1073740791**.</p>
 *
 * <p>What the disarm removed was real — `getActiveResourcesInfo()` goes from
 * `["PipeWrap","TCPSocketWrap","Immediate"]` to `["PipeWrap"]`, so the socket and the dispatcher are
 * genuinely gone. `PipeWrap` is **stdio**, and on Windows a stdout/stderr connected to a pipe is
 * *asynchronous*, so `process.exit` can run with a write still in flight and libuv then signals its
 * completion on a closing handle. That is why it reproduces under a pipe and under redirection and
 * not in a terminal, and why writing to stderr and exiting is clean on its own: it takes a request
 * *and* a pending write, which is exactly the combination no harness had.</p>
 *
 * <p>So the disarm is what makes returning affordable, and returning is what fixes it. SH440
 * measured that `exitCode` alone waits out undici's 4 s `keepAliveTimeout`; with the layer already
 * disarmed there is nothing left to wait for, and the loop drains at once. The two halves only work
 * together, which is why SH440 looked complete.</p>
 *
 * @param {number} code the exit code the caller decided
 * @param {object} [io] injected for tests: the process to end, the global to sweep, the scheduler
 * @returns {object} the watchdog handle, so a test can prove it was armed and unref'd
 */
export async function finish(code, { proc = process, global = globalThis, schedule = setTimeout } = {}) {
  // Set before the await, so a teardown that dies for its own reasons still carries the decision.
  proc.exitCode = code;
  await disarmRequestLayer(global);
  // Deliberately no `proc.exit(code)` here. See above: with a live request behind it, that call is
  // the abort. The process ends by the loop draining, which the disarm has just made immediate.
  const watchdog = schedule(() => proc.exit(code), FORCE_EXIT_AFTER_MS);
  // `unref` is what keeps the watchdog from being the thing that holds the loop open for 5 seconds.
  watchdog?.unref?.();
  return watchdog;
}
