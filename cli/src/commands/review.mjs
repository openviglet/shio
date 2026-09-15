/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio review` (SH1001) — the queue, from a terminal.
 *
 * ## Why a CLI for a screen
 *
 * The review queue is the one surface in this product whose whole job is a person looking, so a
 * command for it reads like a contradiction. It is not: a pipeline that runs an agent wants to
 * *gate* on what the run left behind — "fail the build if anything is still waiting" is one exit
 * code, and until now the only way to ask was to open a browser. It is also the P1 order: a
 * capability the console has and nothing else does is incomplete.
 *
 * ## The dry run reads the server's answer rather than forming its own
 *
 * `decide --dry-run` prints which of the named changes would publish and which would be skipped,
 * from the `draft` flag each entry already carries. That is the same discipline SH996 applied to
 * the console's Publish: the facts are the server's, the rendering is the caller's. Nothing here
 * decides what approving would do.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("review");

/** The three decisions, as the server spells them. */
const KINDS = ["approve", "reject", "hold"];

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const verb = positionals[1] ?? "list";

  if (verb === "list") return list(client, flags, ctx);
  if (verb === "show") return show(client, flags, positionals, ctx);
  if (verb === "decide") return decide(client, flags, positionals, ctx);
  throw new Error(
    `shio review: unknown subcommand "${verb}". It is one of list, show or decide.\n` +
      "Example: shio review decide <session> --reject post:Blog/about --note \"last quarter's prices\"",
  );
}

/** One line per run: what it was for, what it did, and how much is still waiting. */
async function list(client, flags, ctx) {
  const sessions = await client.reviewSessions(flags.limit);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(sessions, null, 2)}\n`);
    return { sessions, waiting: sessions.filter((each) => each.pending > 0).length };
  }
  const waiting = sessions.filter((each) => each.pending > 0);
  const lines = [`shio review: ${sessions.length} run(s), ${waiting.length} waiting`];
  for (const session of sessions) {
    // SH1000 — the premise leads here for the reason it leads in the console: it decides whether
    // the counts underneath are the right counts.
    lines.push(`  ${session.intent ?? "(no intent declared)"}`);
    lines.push(
      `      ${session.agentName ?? session.session}  ${session.changes} change(s)` +
        `  ${session.pending} waiting  by ${session.actor ?? "—"}`,
    );
  }
  ctx.stdout.write(`${lines.join("\n")}\n`);
  // A gate for CI: `shio review list --strict` fails while anything is still waiting.
  return {
    sessions,
    waiting: waiting.length,
    exitCode: flags.strict && waiting.length > 0 ? 1 : 0,
  };
}

/** One run's changes, with whatever has already been decided about each. */
async function show(client, flags, positionals, ctx) {
  const session = requireSession(positionals, "show");
  const detail = await client.reviewDetail(session);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(detail, null, 2)}\n`);
    return detail;
  }
  const decided = new Map((detail.decisions ?? []).map((each) => [each.postId, each]));
  const lines = [`shio review ${session}: ${detail.entries.length} change(s) by ${detail.actor ?? "—"}`];
  for (const entry of detail.entries) {
    const standing = decided.get(entry.postId);
    lines.push(
      `  ${entry.action} ${entry.address ?? entry.postId}` +
        `${standing ? ` — ${standing.decision}${standing.note ? `: ${standing.note}` : ""}` : ""}`,
    );
  }
  // SH1000 — what the run changed the look of, which the action column above cannot say.
  if (detail.appearance?.checked > 0) {
    lines.push(
      `  looks different (${detail.appearance.checked} compared): ` +
        `${detail.appearance.moved.length ? detail.appearance.moved.join(", ") : "none"}`,
    );
  }
  ctx.stdout.write(`${lines.join("\n")}\n`);
  return detail;
}

/** Approve, reject or hold named changes — or say what that would do. */
async function decide(client, flags, positionals, ctx) {
  const session = requireSession(positionals, "decide");
  const asked = KINDS.flatMap((kind) => split(flags[kind]).map((subject) => ({ kind, subject })));
  if (asked.length === 0) {
    throw new Error(
      "shio review decide: name what to decide with --approve, --reject or --hold.\n" +
        "Example: shio review decide <session> --approve post:Blog/about,post:Blog/pricing",
    );
  }
  if (asked.some((each) => each.kind === "reject") && !flags.note) {
    // The server refuses this too, and says so better. Refused here as well because a CI job that
    // learns the rule from a 400 has already spent a round trip on a run it is gating.
    throw new Error(
      "shio review decide: --reject needs --note \"<why>\". A rejection nobody can read teaches "
        + "nothing. Use --hold for \"not now\", which takes no reason.",
    );
  }

  const detail = await client.reviewDetail(session);
  const byAddress = new Map();
  for (const entry of detail.entries) {
    if (entry.address) byAddress.set(entry.address, entry.postId);
    if (entry.postId) byAddress.set(entry.postId, entry.postId);
  }
  const drafted = new Set(detail.entries.filter((entry) => entry.draft).map((entry) => entry.postId));

  const decisions = [];
  const unknown = [];
  for (const { kind, subject } of asked) {
    const postId = byAddress.get(subject);
    if (!postId) {
      unknown.push(subject);
      continue;
    }
    decisions.push({ postId, decision: kind.toUpperCase(), note: flags.note });
  }
  if (unknown.length > 0) {
    throw new Error(
      `shio review decide: this run changed nothing about ${unknown.join(", ")}. ` +
        "`shio review show <session>` lists what it did change.",
    );
  }

  if (flags.dryRun || flags["dry-run"]) {
    const lines = [`shio review decide ${session} (dry run)`];
    for (const decision of decisions) {
      const would =
        decision.decision !== "APPROVE"
          ? decision.decision.toLowerCase()
          : drafted.has(decision.postId)
            ? "publish"
            : "skip — no draft to publish";
      lines.push(`  ${decision.postId} — would ${would}`);
    }
    ctx.stdout.write(`${lines.join("\n")}\n`);
    return { dryRun: true, decisions };
  }

  const outcome = await client.reviewDecide(session, decisions);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(outcome, null, 2)}\n`);
    return outcome;
  }
  const lines = [`shio review decide ${session}: ${outcome.changed} decided`];
  for (const skipped of outcome.skipped) {
    lines.push(`  ${skipped.postId} — skipped: ${skipped.reason}`);
  }
  ctx.stdout.write(`${lines.join("\n")}\n`);
  // A skipped change is not a failure of the call — the server acted on everything it could — but a
  // pipeline that asked for eleven and got nine has to be able to notice.
  return { ...outcome, exitCode: outcome.skipped.length > 0 ? 1 : 0 };
}

function requireSession(positionals, verb) {
  const session = positionals[2];
  if (!session) {
    throw new Error(
      `shio review ${verb}: which run? Pass the session id.\n` +
        "`shio review list` prints them.",
    );
  }
  return session;
}

/** `--approve a,b` and `--approve a --approve b` both mean two. */
function split(value) {
  if (!value) return [];
  return (Array.isArray(value) ? value : [value])
    .flatMap((each) => String(each).split(","))
    .map((each) => each.trim())
    .filter(Boolean);
}
