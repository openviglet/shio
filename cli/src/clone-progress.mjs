/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The progress channel for `shio clone` (SH543) — how a caller follows a crawl it is not watching.
 *
 * ## Why a file, and not stderr
 *
 * `clone` writes its inventory once, at the end. Under `--render` a depth-2 crawl of a real site
 * can hold a 200-page ceiling with up to 30 s of `networkidle` wait each, so the command is silent
 * for the better part of two hours — and the silence reads the same whether the crawl is working or
 * wedged. It was told apart once by sampling the browser's CPU with `Get-Process`, which is process
 * forensics standing in for a status line.
 *
 * Stderr does not fix that. The agent that starts a long run puts it in the background and is not
 * holding its pipe; a `| tail` swallows both channels until EOF anyway. A file outlives the process,
 * the pipe and the turn, and `Read`/`Grep` are the cheapest tools the caller has (P4). `shio dev
 * --content` already settled the mechanism — append to a `.jsonl` beside the artefact, and treat a
 * lost log as a convenience rather than a failure.
 *
 * ## Why two files
 *
 * Two questions, and they cost differently.
 *
 * `clone-progress.jsonl` grows by one line per unit and answers **what failed** — greppable, and the
 * only record of a page that timed out, since the inventory keeps the outcome but not the cost.
 *
 * `clone-status.json` is rewritten in place and answers **where is it** in a single Read whose size
 * never changes, however long the crawl runs (P3). Nothing in it accumulates: no lists, no history.
 *
 * `startedAt`/`updatedAt` is the pair that matters. A count says how far a run got; only a heartbeat
 * says whether it is still getting anywhere, and that is the question that was asked of the wedged
 * run. `pid` is there for the same reason — the caller who decides a run is not worth finishing
 * needs something to kill.
 */
import { dirname, join } from "node:path";

export const PROGRESS_LOG = "shio/.shio/clone-progress.jsonl";
export const STATUS_FILE = "shio/.shio/clone-status.json";

/** A sink whose every method is a no-op, for `--no-progress` and for tests that assert files. */
const SILENT = {
  phase() {},
  step() {},
  settle: async () => {},
};

/**
 * Opens the progress channel for one run.
 *
 * <p>Never throws and never rejects: a crawl that dies because its status file could not be written
 * has been made worse by the thing meant to make it legible. The first IO failure disables the sink
 * and is reported once, on stderr, so a caller polling a file that stopped moving learns why.
 *
 * @param {{ seed: string, ceiling: object, flags: object }} run what this crawl was asked to do
 * @param {object} ctx the injectable IO context
 */
export function cloneProgress({ seed, ceiling, flags = {} }, ctx) {
  if (flags.noProgress === true) return SILENT;

  const now = typeof ctx.clock === "function" ? ctx.clock : Date.now;
  const logPath = join(ctx.cwd, typeof flags.progressLog === "string" ? flags.progressLog : PROGRESS_LOG);
  const statusPath = join(ctx.cwd, typeof flags.statusFile === "string" ? flags.statusFile : STATUS_FILE);
  const startedAtMs = now();
  const startedAt = new Date(startedAtMs).toISOString();

  let live = true;
  let phase = "starting";
  let total = null;
  let done = 0;
  let current = null;
  const counts = { ok: 0, failed: 0, skipped: 0 };
  // Serialised behind one promise: `step` is called from a loop that does not await it, and two
  // overlapping rewrites of the same status file can interleave into a half-written object — which
  // a poller would read as corrupt exactly when it is being read most.
  let tail = Promise.resolve();

  const disable = (error) => {
    if (!live) return;
    live = false;
    ctx.stderr?.write?.(
      `shio clone: progress could not be written (${error?.message ?? error}); the crawl continues.\n`,
    );
  };

  // What a terminal write adds to the status and not only to the log. Small and scalar by
  // construction: this is the file that must not grow, and the poller that reads `state: "failed"`
  // needs the reason in the same read or it has to go looking for a log to find out why.
  let terminal = null;

  const status = (state) => ({
    command: "clone",
    state,
    ...terminal,
    pid: ctx.pid ?? process.pid,
    seed,
    phase,
    done,
    total,
    remaining: total === null ? null : Math.max(0, total - done),
    current,
    counts: { ...counts },
    // The ceilings in force, because they are what a caller decides with: a run 13 pages into a
    // ceiling of 200 at 30 s each is a decision to make now, not in ninety minutes.
    ceiling,
    startedAt,
    updatedAt: new Date(now()).toISOString(),
    elapsedMs: now() - startedAtMs,
    etaMs: eta(done, total, now() - startedAtMs),
  });

  const write = (state, event) => {
    if (!live) return;
    tail = tail
      .then(async () => {
        await ctx.fs.mkdir(dirname(statusPath), { recursive: true });
        if (event) {
          // `state` on every line, not only the terminal one: the last line of a log whose process
          // was killed says `running`, which is how a reader tells a crawl that was stopped from one
          // that finished. A step row carries `n`; the terminal row does not, and that is what
          // distinguishes them.
          await ctx.fs.appendFile(
            logPath,
            `${JSON.stringify({ at: new Date(now()).toISOString(), state, phase, ...readable(event) })}\n`,
            "utf8",
          );
        }
        await ctx.fs.writeFile(statusPath, `${JSON.stringify(status(state), null, 2)}\n`, "utf8");
      })
      .catch(disable);
  };

  // The log is truncated rather than appended to. Two runs' lines in one file cannot be told apart
  // by anything in them, and the second run's reader would grep the first one's failures.
  tail = tail
    .then(async () => {
      await ctx.fs.mkdir(dirname(logPath), { recursive: true });
      await ctx.fs.writeFile(logPath, "", "utf8");
    })
    .catch(disable);
  write("starting");

  return {
    /** Enter a named phase. `total` is its own ceiling — the crawl's and the download's differ. */
    phase(name, ceilingOfPhase = null) {
      phase = name;
      total = ceilingOfPhase;
      done = 0;
      current = null;
      write("running");
    },

    /**
     * One unit finished. `outcome` is `ok`, `failed` or `skipped`; everything else in `event` is
     * written to the log verbatim and never to the status, which must not grow.
     *
     * <p>`total` may be restated, because one of the two phases genuinely grows: a stylesheet's
     * `url()` calls append to the download plan while that plan is being walked. Re-reading it each
     * step is the difference between a total that is right and one that was right once.
     */
    step({ outcome = "ok", path = null, total: restated, ...event }) {
      done += 1;
      current = path;
      if (typeof restated === "number") total = restated;
      if (counts[outcome] !== undefined) counts[outcome] += 1;
      write("running", { n: done, total, path, outcome, ...event });
    },

    /**
     * The terminal write, and the reason a poller can stop. Awaited by the caller, unlike `step`:
     * a status file that still says `running` after the process exited is the ambiguity this whole
     * file exists to remove.
     */
    async settle(state, event = null) {
      // Scalars only, and filtered rather than trusted: `settle` is called with whatever the command
      // knew at the end, and one array folded in here would undo the constant-size guarantee that is
      // the reason this file is separate from the log.
      terminal = readable(Object.fromEntries(
        Object.entries(event ?? {}).filter(([, value]) => value === null || typeof value !== "object"),
      ));
      write(state, event);
      await tail;
    },
  };
}

/**
 * One log line's worth of an event, with the terminal's own noise taken out.
 *
 * <p>Playwright's timeout message arrives carrying ANSI colour and its multi-line call log, which is
 * written for somebody staring at a terminal. This file is read by grep. Left in, one page's failure
 * spans four lines of a format whose whole premise is one line per unit, and the escape sequences
 * defeat the pattern that would have found it.
 */
function readable(event) {
  return Object.fromEntries(
    Object.entries(event).map(([key, value]) => [
      key,
      typeof value === "string"
        // eslint-disable-next-line no-control-regex -- the point is the control characters
        ? value.replace(/\u001b\[[0-9;]*m/g, "").replace(/\s*\n\s*/g, " ").trim()
        : value,
    ]),
  );
}

/** Remaining time at the rate achieved so far. Null until there is a rate, and never a guess. */
function eta(done, total, elapsedMs) {
  if (!done || total === null || total <= done) return null;
  return Math.round((elapsedMs / done) * (total - done));
}
