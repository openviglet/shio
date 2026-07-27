/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
 */
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
    return `shio: the server answered ${status}.${raw}\n${debug ? `  url: ${err.url}\n` : ""}`;
  }
  const lines = [`shio: ${problem.title ?? `the server answered ${status}`}`];
  if (problem.detail) lines.push(`  ${problem.detail}`);
  if (problem.at) lines.push(`  at: ${problem.at}`);
  if (problem.didYouMean) lines.push(`  did you mean: ${problem.didYouMean}`);
  if (Array.isArray(problem.allowed) && problem.allowed.length) {
    lines.push(`  allowed: ${problem.allowed.join(", ")}`);
  }
  if (problem.fix) lines.push(`  fix: ${problem.fix}`);
  if (debug) lines.push(`  url: ${err.url}`);
  return `${lines.join("\n")}\n`;
}

