/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio judge --site X` (SH589) — run the instruments, answer once, and say where they disagreed.
 *
 * <p>P2 applied to the perception loop rather than to discovery: one call replaces a session. The
 * reconciliation lives in `judge.mjs` and is pure; this file's whole job is to run the four and
 * translate each one's vocabulary into the subject a disagreement is keyed on.</p>
 *
 * <h2>It composes, it does not fork (P10)</h2>
 *
 * <p>Each instrument is its own command's `run`, called with a sink for a `ctx.stdout` — so a rule
 * that changes in `verify` changes here, and there is no second implementation of any check to
 * drift. The four reports are still obtainable: `--verbose` prints each one underneath the
 * judgement, because a disagreement is a pointer at two reports and a reader who cannot see them
 * has to run the commands again.</p>
 *
 * <h2>Two of the four are opt-in, and the report says so rather than passing</h2>
 *
 * <p>`verify` and `diff` are browser-free and always run. `audit` needs Chromium and a page at a
 * time; `snapshot` needs something to compare against, and choosing that for the caller would be
 * inventing the baseline whose absence is the whole question. So both are `NOT RUN` with the
 * reason and the flag that would run them — never green, because an instrument that did not run is
 * unproven and a verdict that treats the two the same is the defect this exists to remove.</p>
 */
import { describeJudgement, judgeRuns, subjectOf } from "../judge.mjs";

export const HELP = `shio judge --site <name> — run the instruments, answer once, name the disagreements

verify and diff always run (both browser-free). audit and snapshot are opt-in and are
reported NOT RUN rather than green when they are not asked for. Exits 1 when any instrument
that ran found something — a green from three and a red from one is a red.

  --site <name>        The site to judge (or a bare positional)
  --browser            Also run audit, which needs playwright
  --against <origin>   Also run snapshot against this origin (the source you replicated)
  --pages <n>          How many pages audit/snapshot look at (default 5)
  --verbose            Print each instrument's own report underneath the judgement
  --json               The judgement as JSON, for a pipeline

Examples:
  shio judge --site Acme
  shio judge --site Acme --browser --against https://acme.example.com
`;

/** What each instrument is worth spending a browser on, when one is spent at all. */
const DEFAULT_PAGES = 5;

export async function run(flags, positionals, ctx) {
  const site = String(flags.site ?? positionals[0] ?? "").trim();
  if (!site) {
    throw new Error(
      "shio judge: pass the site to judge.\n  Example: shio judge --site Acme",
    );
  }

  const reports = [];
  const runs = [];

  const verify = await instrument("verify", () => import("../commands/verify.mjs"),
    { ...flags, site }, [], ctx, reports);
  runs.push(fromVerify(verify));

  const diff = await instrument("diff", () => import("../commands/diff.mjs"),
    { ...flags, site }, [], ctx, reports);
  runs.push(fromDiff(diff));

  const pages = pagesToLookAt(diff, runs, Number(flags.pages) || DEFAULT_PAGES);

  runs.push(await browserInstrument("audit", flags.browser === true, pages, site, flags, ctx,
    reports, "pass --browser (it needs playwright, and loads one page at a time)"));
  runs.push(await browserInstrument("snapshot", typeof flags.against === "string" && flags.against,
    pages, site, flags, ctx, reports,
    "pass --against <origin> — choosing what to compare pixels with would be inventing the "
      + "baseline whose absence is the question"));

  const judgement = judgeRuns(runs);

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ site, ...judgement }, null, 2)}\n`);
  } else {
    ctx.stdout.write(describeJudgement(judgement, { site }));
    if (flags.verbose) {
      for (const report of reports) {
        ctx.stdout.write(`\n--- ${report.name} ---\n${report.text}`);
      }
    }
  }
  return { site, ...judgement };
}

/**
 * One instrument's own `run`, with its output captured rather than printed.
 *
 * <p>A throw is a <b>did not run</b> and not a finding, which is the distinction the whole verdict
 * turns on: `diff` throws when there is no inventory, and reporting "no capture in this project"
 * as damage to the replica would be the averaging this command exists to refuse.</p>
 */
async function instrument(name, load, flags, positionals, ctx, reports) {
  const text = { value: "" };
  const sink = { write: (chunk) => { text.value += chunk; } };
  try {
    const mod = await load();
    const result = await mod.run(flags, positionals, { ...ctx, stdout: sink, stderr: sink });
    reports.push({ name, text: text.value });
    return { ran: true, result, text: text.value };
  } catch (error) {
    reports.push({ name, text: text.value });
    return { ran: false, reason: String(error?.message ?? error).split("\n")[0] };
  }
}

/** `verify`'s findings carry SH79 addresses; the subject is the path they name. */
function fromVerify(run) {
  if (!run.ran) return { name: "verify", ran: false, reason: run.reason };
  const report = run.result ?? {};
  const findings = report.findings ?? [];
  return {
    name: "verify",
    ran: true,
    exitCode: report.exitCode ?? 0,
    // Everything it looked at, which for a site-scoped run is every address it reported on plus
    // every address it cleared. The report carries the findings; the cleared set is what its
    // `checked` count summarises, so the honest `covered` is the findings' own subjects plus the
    // site — enough to disagree about a page it named, and no claim about one it did not.
    covered: findings.map((finding) => subjectOf(finding.address)).filter(Boolean),
    flagged: findings
      .filter((finding) => finding.severity !== "warning")
      .map((finding) => ({
        subject: subjectOf(finding.address),
        why: `${finding.rule}${finding.field ? ` (${finding.field})` : ""}`,
      }))
      .filter((row) => row.subject),
  };
}

/** `diff`'s subject is already a path, and every compared page is covered. */
function fromDiff(run) {
  if (!run.ran) return { name: "diff", ran: false, reason: run.reason };
  const comparison = run.result ?? {};
  const pages = comparison.pages ?? [];
  return {
    name: "diff",
    ran: true,
    exitCode: comparison.exitCode ?? 0,
    covered: pages.map((page) => page.path),
    flagged: [
      ...pages
        .filter((page) => page.lost > 0 || page.wentHome > 0)
        .map((page) => ({
          subject: page.path,
          why: page.lost > 0
            ? `${page.lost} fact(s) lost`
            : `${page.wentHome} link(s) now point at the source`,
        })),
      ...(comparison.missing ?? []).map((path) => ({
        subject: path, why: "the replica serves nothing here",
      })),
    ],
  };
}

/**
 * `audit` and `snapshot`, when they were asked for.
 *
 * <p>Bounded by `--pages` because both cost a browser load each, and unbounded is how a judgement
 * over a 200-page replica becomes something nobody runs. The bound is <b>declared on the report</b>
 * rather than applied quietly — a silently truncated coverage set reads as "everything was fine".</p>
 */
async function browserInstrument(name, asked, pages, site, flags, ctx, reports, how) {
  if (!asked) return { name, ran: false, reason: `not asked for — ${how}` };
  if (!pages.length) {
    return { name, ran: false, reason: "no page to look at: nothing named one, and `diff` found "
      + "no capture to take a page list from" };
  }

  const load = name === "audit"
    ? () => import("../commands/audit.mjs")
    : () => import("../commands/snapshot.mjs");
  const covered = [];
  const flagged = [];
  let ran = false;
  let reason = null;
  for (const path of pages) {
    const target = `post:${site}${path}`;
    const extra = name === "snapshot" ? { against: flags.against } : {};
    const one = await instrument(name, load, { ...flags, ...extra, site }, [target], ctx, reports);
    if (!one.ran) {
      // The first refusal is the instrument's, not this page's: a missing browser does not become
      // a page-level finding, and the remaining pages would refuse identically.
      reason = one.reason;
      break;
    }
    ran = true;
    covered.push(path);
    if ((one.result?.exitCode ?? 0) !== 0 || (one.result?.counts?.errors ?? 0) > 0) {
      flagged.push({ subject: path, why: summarise(name, one.result) });
    }
  }
  if (!ran) return { name, ran: false, reason: reason ?? "it refused before it looked at a page" };
  return { name, ran: true, exitCode: flagged.length ? 1 : 0, covered, flagged, capped: pages.length };
}

function summarise(name, result) {
  if (name === "audit") {
    const counts = result?.counts ?? {};
    return `${counts.errors ?? 0} error(s), ${counts.total ?? 0} finding(s) a browser observed`;
  }
  const changed = result?.changed ?? result?.difference ?? null;
  return changed === null ? "the pixels differ" : `${changed} different`;
}

/**
 * Which pages are worth a browser load, when one is being spent.
 *
 * <p>The home page, then every page another instrument already flagged — which is exactly where a
 * disagreement is informative, since an instrument agreeing with nobody about a page nobody else
 * mentioned is the cheapest thing this command can find and the least interesting. The rest of the
 * budget goes to pages `diff` compared, in its order, so the sample is deterministic.</p>
 */
function pagesToLookAt(diff, runs, limit) {
  const compared = (diff.ran ? diff.result?.pages ?? [] : []).map((page) => page.path);
  const flaggedAlready = runs.flatMap((run) => (run.flagged ?? []).map((row) => row.subject))
    .filter((subject) => typeof subject === "string" && subject.startsWith("/"));
  const ordered = ["/", ...flaggedAlready, ...compared];
  const seen = [];
  for (const path of ordered) {
    if (!seen.includes(path) && (path === "/" || compared.includes(path))) seen.push(path);
    if (seen.length >= Math.max(1, limit)) break;
  }
  return seen;
}
