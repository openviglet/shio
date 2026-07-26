/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH90 — `shio dev --content`: keep `shio/content/**` and the instance in step, and say
 * what happened in a form an agent can read.
 *
 * WHY THE EVENT LOG IS JSONL AND NOT PROSE
 * A long build produces a long scrollback, and a failure buried in it costs the agent a
 * reconstruction — read the tail, guess which lines belong together, guess what failed.
 * One JSON object per line makes that a `grep`: every event carries its kind, its counts
 * and its timestamp, so "did anything conflict while I was working?" is answerable without
 * reading the narrative. Human-readable lines still go to stdout; the log is the record.
 *
 * WHICH DIRECTION WINS
 * Neither, which is the point. A file change triggers a push; a change-feed entry triggers
 * a pull; and both run through SH89's three-way merge, so the loop cannot turn a
 * simultaneous edit into a clobber — it turns it into a reported conflict and stops
 * touching that post.
 */
import { join } from "node:path";

import * as contentPull from "./content-pull.mjs";
import * as contentPush from "./content-push.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { BASE_FILE, CONTENT_DIR, readBase } from "../content-store.mjs";

/** Where the machine-readable record goes. */
export const EVENT_LOG = "shio/.shio/dev-events.jsonl";

/**
 * Runs one reconciliation in both directions and records what it did.
 *
 * Pull first, always: bringing the server's edits down before sending the tree up is what
 * makes a conflict *visible* instead of overwritten. A conflict stops the push for that
 * run — pushing "everything except the conflicted post" would leave the tree and the
 * instance in a state neither side asked for.
 *
 * @param {object} flags   command flags (site, content-dir, prune, …)
 * @param {object} ctx     the injected IO context
 * @param {{ reason: string }} options what woke the loop, for the log
 */
export async function syncOnce(flags, ctx, { reason = "manual" } = {}) {
  const events = [];
  const emit = async (event) => {
    events.push(event);
    await appendEvent(ctx, flags, event);
  };

  let pulled = null;
  try {
    pulled = await contentPull.run({ ...flags, content: true }, [], ctx);
    await emit({
      event: "pull",
      reason,
      written: pulled.written?.length ?? 0,
      deleted: pulled.deleted?.length ?? 0,
      conflicts: pulled.conflicts?.length ?? 0,
      cursor: pulled.cursor ?? null,
    });
  } catch (error) {
    await emit({ event: "error", phase: "pull", reason, message: error.message });
    return { events, ok: false };
  }

  if (pulled.conflicts?.length) {
    await emit({
      event: "blocked",
      phase: "push",
      reason: "conflicts",
      addresses: pulled.conflicts.map((conflict) => conflict.address),
    });
    return { events, ok: false, pulled };
  }

  try {
    const pushed = await contentPush.run({ ...flags, content: true }, [], ctx);
    await emit({ event: "push", reason, changed: pushed.changed ?? 0, ops: pushed.ops ?? 0 });
    return { events, ok: true, pulled, pushed };
  } catch (error) {
    await emit({ event: "error", phase: "push", reason, message: error.message });
    return { events, ok: false, pulled };
  }
}

/**
 * Starts the loop: a file watcher on the content tree and a poll of the change feed.
 *
 * Returns a handle with `stop()` rather than blocking, so `shio dev` can own the process
 * and a test can drive a tick without a real clock.
 */
export async function run(flags, _positionals, ctx) {
  const dir = flags.contentDir ?? CONTENT_DIR;
  const intervalMs = Math.max(1, Number(flags.interval ?? 5)) * 1000;
  const debounceMs = Math.max(0, Number(flags.debounce ?? 400));
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  ctx.stdout.write(
    `shio dev --content: watching ${dir} and polling for changes every ${intervalMs / 1000}s ` +
      `(events → ${flags.eventLog ?? EVENT_LOG})\n`,
  );
  await syncOnce(flags, ctx, { reason: "startup" });

  let running = false;
  let pending = null;
  const trigger = (reason) => {
    if (running) {
      // Coalesce: a burst of writes is one sync, and a sync that arrives mid-run reruns
      // once afterwards rather than queueing per file.
      pending = reason;
      return;
    }
    running = true;
    syncOnce(flags, ctx, { reason })
      .catch((error) => ctx.stderr.write(`shio dev --content: ${error.message}\n`))
      .finally(() => {
        running = false;
        if (pending) {
          const next = pending;
          pending = null;
          trigger(next);
        }
      });
  };

  let debounce = null;
  // Injectable so a test can drive the loop with no filesystem at all; the real one is
  // imported here rather than at module scope so importing this command stays cheap.
  const watchImpl = ctx.watch ?? (await import("node:fs")).watch;
  const watcher = watchImpl(join(ctx.cwd, dir), { recursive: true }, () => {
    if (debounce) clearTimeout(debounce);
    debounce = setTimeout(() => trigger("file-change"), debounceMs);
  });

  const timer = setInterval(async () => {
    try {
      const base = await readBase(ctx, flags.baseFile ?? BASE_FILE);
      const changes = await client.changes({ site: flags.site, since: base.cursor ?? undefined, limit: 1 });
      // A window (no cursor yet) means the tree has never been pulled, so there is nothing
      // to compare against and the startup sync already covered it.
      if (!changes.window && changes.found > 0) trigger("remote-change");
    } catch (error) {
      ctx.stderr.write(`shio dev --content: change poll failed: ${error.message}\n`);
    }
  }, intervalMs);

  return {
    stop() {
      clearInterval(timer);
      if (debounce) clearTimeout(debounce);
      watcher?.close?.();
    },
    trigger,
  };
}

async function appendEvent(ctx, flags, event) {
  const line = `${JSON.stringify({ at: new Date().toISOString(), ...event })}\n`;
  ctx.stdout.write(line);
  const path = join(ctx.cwd, flags.eventLog ?? EVENT_LOG);
  try {
    await ctx.fs.mkdir(dirOf(path), { recursive: true });
    await ctx.fs.appendFile(path, line, "utf8");
  } catch (error) {
    // The log is a convenience; losing it must not stop the sync it describes.
    ctx.stderr.write(`shio dev --content: could not write the event log: ${error.message}\n`);
  }
}

function dirOf(path) {
  const normalized = String(path).replace(/\\/g, "/");
  const cut = normalized.lastIndexOf("/");
  return cut <= 0 ? normalized : normalized.slice(0, cut);
}
