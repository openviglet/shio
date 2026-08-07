/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH173 — how a failure reaches a human.
 *
 * Its own module rather than a function in `index.mjs`, because that file runs the CLI on
 * import: a test that wants to check the formatting should not have to start the tool.
 */
/**
 * Renders a failure for a human (SH173).
 *
 * A remote failure used to print `Shio CDA 404 for <seven hundred characters of URL>` and
 * nothing else, while the SH80 teaching body — which Shio always sends, and which names the
 * problem, the fix and the allowed values — sat unread on the error object. So the body is what
 * gets printed, and the URL moves behind `SHIO_DEBUG`: it is diagnostic detail, not the message.
 *
 * <h2>And a body that parses and teaches nothing is still what the server said (SH513)</h2>
 *
 * <p>`shio convert` on a real site died with one line — `shio: the server answered 400` — and a URL
 * under `SHIO_DEBUG`. The endpoint had answered with Spring's default body
 * (`{"timestamp":…,"status":400,"error":"Bad Request","path":"/api/v2/staticfile/upload"}`), which
 * is valid JSON holding **none** of the six keys below, so it parsed into `problem`, took the
 * `title ?? "the server answered 400"` branch, and every other line was skipped. The raw-body
 * fallback could not reach it either — that branch fires only when the body does not parse. So the
 * one shape this module could not print was a JSON body that is not a teaching document, which is
 * exactly the shape a defect produces: `path` and `error` were on the wire and nobody saw them.</p>
 *
 * <h2>And a 500 names where its cause still lives (SH587)</h2>
 *
 * <p>The one status whose body cannot say anything useful is the one this module could add a
 * sentence to: see `recorded` below.</p>
 *
 * <p>Diagnosing that cost a log (a 400 is a client error and is not logged), a hand-built probe
 * against an endpoint whose contract I guessed wrong, and finally a byte-size listing. P6 is why it
 * is a defect rather than terseness: every 4xx in Shio carries a fix because an agent is the reader,
 * and a teaching error the CLI discards is a teaching error nobody receives — the endpoint pays for
 * it and the caller sees `400`. It reaches every write the CLI makes, since `convert`, `push`,
 * `apply`, `init` and the batch surface all speak the same convention.</p>
 */
import { DIAGNOSTICS_HINT } from "./instance-hints.mjs";

export function formatError(err, { debug = false } = {}) {
  const status = err && typeof err === "object" ? err.status : undefined;
  if (typeof status !== "number") {
    return `shio: ${err?.message ?? err}\n`;
  }
  let problem = null;
  try {
    problem = err.body ? JSON.parse(err.body) : null;
  } catch {
    problem = null;
  }
  if (!problem || typeof problem !== "object") {
    // No teaching body: say the status and the body as it came, which is still better than a URL.
    const raw = typeof err.body === "string" && err.body.trim() ? `\n  ${err.body.trim()}` : "";
    return (
      `shio: the server answered ${status}.${raw}\n` +
      recorded(status) +
      `${debug ? `  url: ${err.url}\n` : ""}`
    );
  }
  const lines = [`shio: ${problem.title ?? `the server answered ${status}`}`];
  if (problem.detail) lines.push(`  ${problem.detail}`);
  if (problem.at) lines.push(`  at: ${problem.at}`);
  if (problem.didYouMean) lines.push(`  did you mean: ${problem.didYouMean}`);
  if (Array.isArray(problem.allowed) && problem.allowed.length) {
    lines.push(`  allowed: ${problem.allowed.join(", ")}`);
  }
  if (problem.fix) lines.push(`  fix: ${problem.fix}`);
  // SH513 — the body parsed and taught nothing, so print what it *did* say. Without this the whole
  // document is discarded on exactly the failures that are hardest to diagnose: a refusal outside
  // the SH80 convention has no `title` and no `fix`, and its `error` and `path` are the only
  // evidence there is.
  if (!TEACHES.some((key) => carries(problem, key))) {
    lines.push(...saidBy(problem));
  }
  const pointer = recorded(status);
  if (pointer) lines.push(pointer.replace(/\n$/, ""));
  if (debug) lines.push(`  url: ${err.url}`);
  return `${lines.join("\n")}\n`;
}

/**
 * Where the cause of a 5xx still is (SH587).
 *
 * <p>A 500's response body is gone the moment it is printed, and the instance kept the rest:
 * `GET /api/v2/agent/diagnostics` answers with the column, the truncated value, `count`,
 * `firstSeen`/`lastSeen`, a `fix` and the frames. A replication round diagnosed exactly such a
 * failure by locating `store/logs/shio.log` and grepping a stack trace while the endpoint held
 * all of it — which is a P2 defect (discovery is a product feature) in the product that
 * declares P2, not a docs gap.</p>
 *
 * <p><b>5xx only.</b> A 4xx carries `fix`/`allowed`/`didYouMean` under P6 and has already told
 * the caller what to do; sending them to an incident log as well would dilute the instruction
 * they were given. Nothing is fetched here — this module is pure and a failure path is the
 * worst possible place to make a request.</p>
 */
function recorded(status) {
  return status >= 500 ? `  ${DIAGNOSTICS_HINT}\n` : "";
}

/** The SH80 keys. A body holding one of them is a teaching document and prints as one. */
const TEACHES = ["title", "detail", "at", "didYouMean", "allowed", "fix"];

const carries = (problem, key) =>
  Array.isArray(problem[key]) ? problem[key].length > 0 : Boolean(problem[key]);

/**
 * A non-teaching body, as the lines it holds (SH513).
 *
 * <p>Its scalar fields, in the order the server sent them, which for Spring's default is
 * `timestamp`, `status`, `error`, `path` — and `path` is the one that names the endpoint that
 * refused. Nested values are skipped rather than serialized: this is a last resort for a document
 * nobody designed for a reader, and printing a stack trace or a `_embedded` tree into a terminal
 * would bury the four lines that matter. Capped for the same reason, with the cut declared, because
 * a silently shortened error is the shape this whole module exists to remove.</p>
 */
function saidBy(problem) {
  const rows = Object.entries(problem)
    .filter(([, value]) => value !== null && value !== "" && typeof value !== "object");
  const shown = rows.slice(0, 8).map(([key, value]) => `  ${key}: ${value}`);
  if (rows.length > shown.length) {
    shown.push(`  … ${rows.length - shown.length} more field(s) in the body`);
  }
  return shown.length ? shown : ["  the body carried no field this could print"];
}

