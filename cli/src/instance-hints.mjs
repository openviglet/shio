/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH587 — what the instance already knows, said where it is needed.
 *
 * <p>Two facts this instance holds were never spoken by the commands that make them
 * actionable, and twelve replication rounds paid for both. `GET /api/v2/agent/diagnostics`
 * had recorded the exact incident behind a 500 — the column, the truncated value, the frames
 * — and the run was diagnosed instead by locating `store/logs/shio.log` and grepping a stack
 * trace. `shio remember` held four notes on that instance, one of which named the binding a
 * later round re-derived; nothing read them.</p>
 *
 * <p>Neither is a missing capability, which is what makes it P2 rather than a feature
 * request: <i>discovery is a product feature, not a docs problem</i>, and here it is the
 * product that failed to discover its own memory. The repair is not a new surface — it is two
 * sentences said at the moment they change what the reader does next.</p>
 *
 * <h2>Why the notice lives at the dispatcher and not in eighteen commands</h2>
 *
 * <p>Eighteen modules construct a {@code ShioAgentClient}. Wiring a notice into each is
 * eighteen edits and eighteen chances for the nineteenth command to be written without one —
 * the shape SH322 names, where the rule outlives the reader who knew it. `index.mjs` is the
 * one place every command passes through, so the classification below is the whole decision
 * and {@code cli/test/instance-hints.test.mjs} fails the build when a new command is
 * registered without joining a side of it.</p>
 */
import { resolveAdmin } from "./context.mjs";

/**
 * The pointer a 5xx carries (SH587).
 *
 * <p>Only a 5xx. A 4xx answers for itself under P6 — it carries `fix`, `allowed`,
 * `didYouMean` — and pointing a caller at an incident log for a refusal it was already told
 * how to correct is noise. A 500's response body is gone by definition: the endpoint is the
 * only place the cause still exists.</p>
 */
export const DIAGNOSTICS_HINT = "the instance recorded this: GET /api/v2/agent/diagnostics";

/**
 * Commands that run against a Shio instance and should say when it carries notes.
 *
 * <p>Each of these resolves console credentials and speaks the agent surface, so the notes
 * are one request away and the caller is, by definition, working on the instance that holds
 * them.</p>
 */
export const NOTES_COMMANDS = new Set([
  "resolve", "impact", "apply", "publish", "import", "blueprints", "marketplace", "verify",
  "changes", "report", "digest", "preview", "share", "snapshot", "audit", "annotations", "judge", "site", "propose",
  "convert", "diff", "pull", "push", "deploy", "surfaces", "versions", "rollback", "schedule", "post",
  "gate",
  // SH1001 — it speaks the review surface with console credentials, which is the same instance and
  // the same working session the notes are about.
  "review",
  // SH1003 — the work list reads the same instance, and what it prints is the other half of what
  // the notice announces.
  "notes",
  // SH1063 — it reads the instance the notes are about, and "this site reaches no index" is
  // exactly the kind of state a standing note tends to be about.
  "index",
  // SH1064 — it reads the same instance with console credentials, and "we dropped /pricing on
  // purpose, do not redirect it" is exactly the standing decision its orphan list would otherwise
  // invite somebody to undo.
  "redirects",
  // SH1022 — it is the listing an agent opens a session with, against the instance the notes are
  // about, and "which pages are drafts" is exactly the question a standing note usually qualifies.
  "find",
  // SH1034 — it reads the same instance with console credentials and its output invites a
  // DELETION, which is the one place a standing note most needs to be in front of the reader:
  // "keep the press kit even though nothing links it yet" is exactly what a curator writes down.
  "assets",
  // SH1040 — it reads the same instance with console credentials and it WRITES sections onto a
  // page somebody else composed, which is where a standing note is most likely to be about the
  // thing being done: "the pricing block on /about is deliberate" is exactly what a curator
  // writes down and exactly what a pattern apply would otherwise walk over.
  "patterns",
]);

/**
 * The other side of the classification, each with the reason it is not a defect.
 *
 * <p>A reason rather than a bare list, because "why does `clone` not print notes" is a
 * question somebody will ask again and the answer is not derivable from the code.</p>
 */
export const NOTES_EXEMPT = new Map([
  ["init", "scaffolds a project; there may be no instance yet, and none is required"],
  // SH1065 — it reads the instance and writes a file, so a note is one request away. Exempt for
  // `import`'s reason inverted: the output is a zip on disk, and a notice about what a curator
  // asked for on THIS instance is advice for the instance the package is going to.
  ["export", "writes a package for another instance; a note about this one travels with it "
    + "inside the zip (SH161), which is where it is useful"],
  ["types", "speaks the CDA with a token — the memory endpoint is not on that surface"],
  ["dev", "long-running; it delegates to types and would print the notice once, at boot"],
  ["build", "compiles local sources; it never opens a connection"],
  ["mcp", "a stdio transport bridge — its context pack already carries the notes"],
  ["context", "the pack it prints carries the notes verbatim (SH114); this would double them"],
  ["remember", "it is the notes — printing their count above them says nothing new"],
  ["clone", "runs against an external site, not against a Shio instance"],
  ["doctor", "its whole output is the state of the instance; a notice about one more fact would "
    + "compete with the four it just listed, and an unreachable instance has no notes to count"],
  // SH1050 — the same reason as `doctor`, and one more: this command already PRINTS the open-note
  // count as one of its own rows, so the notice would announce a number two lines above itself.
  ["status", "it reports the open notes as one of its own rows; the notice would be the same "
    + "count, twice, on one screen"],
]);

/**
 * Says how many notes this instance holds and under which keys, or nothing at all.
 *
 * <p>The count and the keys, never the prose. P3 owns the prose: a note can be a paragraph,
 * there can be dozens, and reprinting them above every command's own output would be a token
 * cost paid on every run to deliver something the reader may not want. A key is enough to
 * decide, and `shio remember` is one call away — which is the point, because the failure this
 * repairs was never "the notes were unreadable", it was "nobody knew there were any".</p>
 *
 * <p><b>Silent on failure, always.</b> An instance that is down, credentials that are wrong, a
 * server too old to answer — none of those are what the caller asked about, and a command
 * that starts by complaining about a request it made on its own initiative is worse than one
 * that says nothing. The command's own error, a moment later, is the honest report.</p>
 *
 * @param {{memory: (query: object) => Promise<{found: number, notes: object[]}>}} client
 * @param {{site?: string|null, stream: {write: (s: string) => void}}} options
 * @returns {Promise<{found: number, keys: string[]}|null>}
 */
export async function writeNotesNotice(client, { site = null, stream } = {}) {
  let memory = null;
  try {
    memory = await client.memory(site ? { site } : {});
  } catch {
    return null;
  }
  const notes = Array.isArray(memory?.notes) ? memory.notes : [];
  if (notes.length === 0) return null;

  const found = typeof memory.found === "number" ? memory.found : notes.length;
  // Sorted and de-duplicated so two runs against one instance print the same bytes (P5); the
  // server's own ordering puts instance-wide notes first, which is a reading order, not a
  // stable identity.
  const keys = [...new Set(notes.map((note) => label(note)))].sort();
  const read = site ? `shio remember --site ${site}` : "shio remember";
  stream.write(
    `shio: this instance carries ${found} note(s): ${keys.join(", ")} — read them with \`${read}\`\n`,
  );
  return { found, keys };
}

/**
 * The notice for one command invocation, or nothing when that command is exempt.
 *
 * <p>The client is imported lazily: `index.mjs` loads its command module on demand precisely
 * so `shio build` does not pay for the HTTP layer, and a top-level import here would undo
 * that for every invocation including the exempt ones.</p>
 */
export async function noticeInstanceNotes(command, flags, ctx) {
  if (!NOTES_COMMANDS.has(command)) return null;
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  let client;
  try {
    const { ShioAgentClient } = await import("./agent-client.mjs");
    client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  } catch {
    return null;
  }
  return writeNotesNotice(client, {
    site: typeof flags.site === "string" ? flags.site : null,
    stream: ctx.stderr,
  });
}

/** `key` for an instance-wide note, `key@site` for a scoped one — `shio remember`'s spelling. */
function label(note) {
  const key = String(note?.key ?? "").trim();
  return note?.scope ? `${key}@${note.scope}` : key;
}
