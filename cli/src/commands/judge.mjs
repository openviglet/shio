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
 * <h2>What runs by default, and what stays a judgement (SH688)</h2>
 *
 * <p>`verify` and `diff` are browser-free and always run. `audit` needs Chromium and a page at a
 * time, and it used to be opt-in behind `--browser` — which meant that on the one investigation
 * whose question was *why does this page look wrong*, the only instrument that renders in a browser
 * and the only one that returns geometry printed `NOT RUN — not asked for` and the session went to
 * hand-parsing HTML and then to asking a human for a screenshot. So it now runs whenever
 * `playwright` resolves. The cost is real, and it is a cost the caller can decline (`--no-browser`);
 * declining the only geometric instrument <em>silently</em> is the decision this command should not
 * have been making.</p>
 *
 * <p>`snapshot` stays opt-in and that is not the same case: choosing what to compare pixels with is
 * a judgement, and inventing the baseline whose absence is the whole question would be worse than
 * abstaining. There is nothing to test for — no `--against` means no answer exists.</p>
 *
 * <p>Either way a skipped instrument is `NOT RUN` with the reason, never green: an instrument that
 * did not run is unproven, and a verdict that treats the two the same is the defect this exists to
 * remove (SH589).</p>
 */
import { INSTALL, browserAvailable } from "../browser.mjs";
import { acceptanceBars } from "../acceptance.mjs";
import { describeJudgement, judgeRuns, subjectOf } from "../judge.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("judge");

/** What each instrument is worth spending a browser on, when one is spent at all. */
const DEFAULT_PAGES = 5;

/**
 * Why `audit` did not run, which is now two different sentences (SH688).
 *
 * <p>*"not asked for"* reads as a preference and *"not installed"* reads as a fact, and only one of
 * those is actionable. While the browser was opt-in the first was true and the second was invisible;
 * now that it runs whenever playwright resolves, a skip means either the caller declined it or the
 * dependency is absent — and telling a reader to pass a flag they already passed, or that they chose
 * something they did not, is the shape of a message nobody can act on (P6).</p>
 */
function auditSkipReason(flags) {
  if (flags.noBrowser === true || flags.browser === false) {
    return "declined with --no-browser — drop it to let this run whenever playwright resolves";
  }
  return `playwright is not installed, so the only instrument that renders in a browser cannot run\n`
    + `      ${INSTALL}`;
}

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

  // SH688 — asked, not assumed. `audit` is the only instrument that renders in a browser and the
  // only one that returns geometry, and it sat out the one investigation whose question was what the
  // page looks like. It now runs whenever playwright resolves; `--browser` still forces it and
  // `--no-browser` still declines it, so the flag remains the way to override a wrong guess.
  const audit = flags.browser === true
    || (flags.noBrowser !== true && flags.browser !== false && await browserAvailable(ctx));
  runs.push(await browserInstrument("audit", audit, pages, site, flags, ctx,
    reports, auditSkipReason(flags)));
  // `snapshot` stays opt-in, and it is not the same case as `audit`: there is nothing to test for.
  // No `--against` means no baseline exists, and choosing one for the caller would be inventing the
  // thing whose absence is the question (SH589).
  runs.push(await browserInstrument("snapshot", typeof flags.against === "string" && flags.against,
    pages, site, flags, ctx, reports,
    "not asked for — pass --against <origin>; choosing what to compare pixels with would be "
      + "inventing the baseline whose absence is the question"));

  // SH860 — the bar, and which mode it is applied as. SH865 — **read, not defaulted.** This chose
  // authorable unless told otherwise, and a run whose own `convert` had just reported `12 of 12
  // page(s) in fidelity mode` was graded against the bar that excludes pixel comparison — the one
  // criterion that site was owed. `diff` already derives the mode from the plan and the proposal and
  // reports how many pages are in each, so the answer is taken from the instrument that computed it
  // rather than derived a second time: two readers of one question disagree somewhere, and here the
  // disagreement would be a whole site judged by the wrong standard (SH245's rule).
  const mode = flags.mode === "fidelity" || flags.mode === "authorable" ? flags.mode
    : flags.fidelity === true ? "fidelity" : modeOf(diff);
  const judgement = judgeRuns(runs, { bars: acceptanceBars(), mode });

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

/**
 * Which mode this round is in, read off `diff`'s own answer — or null where it did not run (SH865).
 *
 * <p>`compareCapture` counts the pages in each mode, which is the join of the plan and the proposal
 * that `resolveModes` makes and `convert` reports as *N of N page(s) in fidelity mode*. **A site is
 * fidelity only if every page is**: one authored region means the page is presented by a Shio theme,
 * and the pixel bar is not a test it can pass — which is the exclusion the whole criterion rests on.
 * A mixed run is therefore authorable, held to its content, its structure and its routes.</p>
 *
 * <p><b>Null rather than a default</b>, and that is the fix rather than a detail. Defaulting is what
 * graded a 12-of-12 fidelity site against the authorable bar; `judgeRuns` reads an unstated mode as
 * an abstention by the instrument that would have stated it, which is exactly what happened.</p>
 */
export function modeOf(diff) {
  const counted = diff?.ran ? diff.result?.modes : null;
  if (!counted) return null;
  const total = Object.values(counted).reduce((sum, count) => sum + count, 0);
  if (!total) return null;
  return counted.fidelity === total ? "fidelity" : "authorable";
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
        // SH860 — the kind the bar is keyed on. `verify.<rule>`, and `acceptance.properties` answers
        // every one of them through `verify.*`: criterion 1 is about the count of errors and not
        // about which rule produced one, and per-rule baselines are applied inside `verify` (SH410)
        // before anything reaches here.
        kind: `verify.${finding.rule ?? "unknown"}`,
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
      // SH860 — three kinds, not one, because the bar says different things about them and the
      // criterion is written per row of its own table: loss the mode does not claim (3), a link that
      // sends a reader to the source (SH586), and a route the replica does not answer (2).
      ...pages
        .filter((page) => page.lost > 0 || page.wentHome > 0)
        .map((page) => ({
          subject: page.path,
          kind: page.lost > 0 ? "diff.facts-lost" : "diff.links-went-home",
          why: page.lost > 0
            ? `${page.lost} fact(s) lost`
            : `${page.wentHome} link(s) now point at the source`,
        })),
      ...(comparison.missing ?? []).map((path) => ({
        subject: path, kind: "diff.page-missing", why: "the replica serves nothing here",
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
async function browserInstrument(name, asked, pages, site, flags, ctx, reports, why) {
  // SH688 — the whole reason, from the caller. This used to prepend a hardcoded `not asked for —`,
  // which was true while both were opt-in and became a lie the moment one of them ran by default:
  // "not asked for — playwright is not installed" states a preference and a fact as one clause, and
  // tells a reader they declined something they did not. Each caller now says its own sentence.
  if (!asked) return { name, ran: false, reason: why };
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
      // SH860 — `audit.error` and `snapshot.pixels-differ`. The two are the criterion's opposite
      // ends: an audit error is an increase over the recorded baseline (criteria 4, 5) and holds any
      // round, while a pixel difference is fidelity's whole criterion and is not one of authorable's
      // at all — which is the exclusion that lets a block close.
      flagged.push({
        subject: path,
        kind: name === "audit" ? "audit.error" : "snapshot.pixels-differ",
        why: summarise(name, one.result),
      });
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
