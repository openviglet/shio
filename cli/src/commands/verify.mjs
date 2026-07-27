/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * `shio verify` (SH99/SH100) — lint the content and its routes, and exit non-zero when
 * something is actually broken.
 *
 * This is the content-side `tsc`, and the exit code is most of the point: it makes the
 * check runnable in CI beside `shio push --check` and `shio apply --check`, so a
 * pull request that would ship a page with an empty required field, a reference to a
 * deleted post, or two pages on one URL fails before a human is asked to look.
 *
 * The severity split is the server's (see ShAgentVerifyService) and this command does
 * not second-guess it: errors fail, warnings are printed and counted. `--strict`
 * promotes warnings for callers who want a stricter gate — opt-in, because a gate that
 * fails on "this folder is empty" is a gate people learn to skip.
 *
 * SH142 adds the third state: a committed `shio/verify-baseline.json` of accepted
 * findings that the gate subtracts, and that fails when an accepted finding no longer
 * exists — see `verify-baseline.mjs` for why that second half is what keeps the file from
 * decaying into a blanket suppression.
 */
import { join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import {
  BASELINE_FILE,
  acceptedFrom,
  applyBaseline,
  describeEntry,
  parseBaseline,
  serializeBaseline,
} from "../verify-baseline.mjs";

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const raw = await client.verify({
    site: flags.site ?? positionals[0],
    checks: flags.checks,
    limit: flags.limit,
    folder: flags.folder,
    address: flags.address,
    since: flags.since,
  });

  const path = typeof flags.baseline === "string" ? flags.baseline : BASELINE_FILE;
  // An explicit `--baseline` that is not there is a mistake worth reporting: a CI job
  // pointing at the wrong path would otherwise pass by checking nothing.
  const baseline = await readBaseline(ctx, path, typeof flags.baseline === "string");

  if (flags.accept) {
    return accept(raw, baseline, path, flags, ctx);
  }

  const report = applyBaseline(raw, baseline);
  ctx.stdout.write(render(report, path));

  const stale = report.staleUnknown ? [] : report.stale;
  const fails =
    report.errors > 0 || (Boolean(flags.strict) && report.warnings > 0) || stale.length > 0;
  if (fails) {
    if (stale.length > 0) {
      ctx.stderr.write(
        `shio verify: ${stale.length} accepted finding(s) in ${path} no longer exist — ` +
          `remove them, or the file stops describing anything.\n`,
      );
    }
    if (report.errors > 0 || (flags.strict && report.warnings > 0)) {
      ctx.stderr.write(
        `shio verify: ${report.errors} error(s)` +
          (flags.strict ? ` and ${report.warnings} warning(s)` : "") +
          ` — the content is not ready to hand over.\n`,
      );
    }
    return { ...report, exitCode: 1 };
  }
  return report;
}

async function readBaseline(ctx, path, explicit) {
  try {
    return parseBaseline(await ctx.fs.readFile(join(ctx.cwd, path), "utf8"));
  } catch (error) {
    if (error?.code === "ENOENT" && !explicit) {
      return parseBaseline(null);
    }
    if (error?.code === "ENOENT") {
      throw new Error(`shio verify: no baseline at ${path}. Create it with --accept.`);
    }
    throw error;
  }
}

/**
 * `--accept` — write the current findings into the baseline.
 *
 * It accepts errors as well as warnings on purpose: SH168's `unknown-field` is an error
 * an instance can legitimately carry while it is being cleaned up, and a baseline that
 * could only hold warnings would send people to `|| true` for exactly the finding class
 * that most needs a record. What keeps it honest is that every accepted entry is named on
 * stdout and lands in a reviewable diff.
 */
async function accept(report, baseline, path, flags, ctx) {
  const { entries, added } = acceptedFrom(report, baseline, flags.reason);
  const target = join(ctx.cwd, path);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeBaseline(entries), "utf8");

  const lines = added.map((entry) => `  + ${describeEntry(entry)}`);
  ctx.stdout.write(
    `shio verify: accepted ${added.length} new finding(s) into ${path} ` +
      `(${entries.length} total)\n${lines.length ? `${lines.join("\n")}\n` : ""}` +
      (report.truncated
        ? `… the report was truncated at ${report.findings.length} of ${report.found} — ` +
          `raise --limit and run --accept again to cover the rest\n`
        : ""),
  );
  return { ...report, accepted: added, baseline: path, written: entries.length };
}

function dirOf(path) {
  const cut = path.lastIndexOf("/");
  return cut <= 0 ? path : path.slice(0, cut);
}

/**
 * One line per finding plus the fix, indented under it.
 *
 * The fix gets its own line rather than being appended: it is the part a reader acts on,
 * and burying it at the end of a long message is how it stops being read (law P6).
 */
function render(report, path) {
  const lines = [];
  for (const finding of report.findings ?? []) {
    const where = [finding.address, finding.field].filter(Boolean).join(" · ");
    lines.push(`${finding.severity === "error" ? "✗" : "!"} ${finding.rule}${where ? ` ${where}` : ""}`);
    lines.push(`    ${finding.message}`);
    if (finding.fix) lines.push(`    → ${finding.fix}`);
  }
  const accepted = report.accepted?.length
    ? `, ${report.accepted.length} accepted`
    : "";
  const header =
    `shio verify: ${report.ok ? "ok" : "failed"} — ${report.errors} error(s), ` +
    `${report.warnings} warning(s)${accepted} over ${report.checked} object(s) ` +
    `[${(report.checks ?? []).join(", ")}]` +
    (report.scope ? ` scope: ${report.scope}` : "");
  const tail = [];
  if (report.truncated) {
    tail.push(`… showing ${report.findings.length} of ${report.found} findings — raise --limit`);
  }
  // A report over a subset that reads as a full one is the SH138 hazard, and the server
  // says so in the payload — repeat it here, because this is the line a human reads.
  if (report.partial) {
    tail.push(`… this run covered a subset of the content — the counts describe that subset`);
  }
  for (const entry of report.stale ?? []) {
    tail.push(`✗ accepted-not-found ${describeEntry(entry)}`);
    tail.push(`    ${path} accepts this finding, but the run did not produce it.`);
    tail.push(`    → Remove the entry: the defect is gone, and a baseline that outlives it hides the next one.`);
  }
  if (report.staleUnknown && report.stale?.length) {
    tail.push(
      `… ${report.stale.length} accepted finding(s) were not seen, but the report is ` +
        `truncated so that proves nothing — raise --limit to check the baseline`,
    );
  }
  const body = lines.length ? `${lines.join("\n")}\n` : "";
  return `${header}\n${body}${tail.length ? `${tail.join("\n")}\n` : ""}`;
}
