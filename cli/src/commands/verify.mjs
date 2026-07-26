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
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const report = await client.verify({
    site: flags.site ?? positionals[0],
    checks: flags.checks,
    limit: flags.limit,
  });

  ctx.stdout.write(render(report));

  const fails = report.errors > 0 || (Boolean(flags.strict) && report.warnings > 0);
  if (fails) {
    ctx.stderr.write(
      `shio verify: ${report.errors} error(s)` +
        (flags.strict ? ` and ${report.warnings} warning(s)` : "") +
        ` — the content is not ready to hand over.\n`,
    );
    return { ...report, exitCode: 1 };
  }
  return report;
}

/**
 * One line per finding plus the fix, indented under it.
 *
 * The fix gets its own line rather than being appended: it is the part a reader acts on,
 * and burying it at the end of a long message is how it stops being read (law P6).
 */
function render(report) {
  const lines = [];
  for (const finding of report.findings ?? []) {
    const where = [finding.address, finding.field].filter(Boolean).join(" · ");
    lines.push(`${finding.severity === "error" ? "✗" : "!"} ${finding.rule}${where ? ` ${where}` : ""}`);
    lines.push(`    ${finding.message}`);
    if (finding.fix) lines.push(`    → ${finding.fix}`);
  }
  const header =
    `shio verify: ${report.ok ? "ok" : "failed"} — ${report.errors} error(s), ` +
    `${report.warnings} warning(s) over ${report.checked} object(s) ` +
    `[${(report.checks ?? []).join(", ")}]`;
  const tail = report.truncated
    ? `\n… showing ${report.findings.length} of ${report.found} findings — raise --limit\n`
    : "";
  return lines.length ? `${header}\n${lines.join("\n")}\n${tail}` : `${header}\n`;
}
