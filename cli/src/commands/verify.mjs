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
import { ASSETS_DIR } from "../content-assets.mjs";
import {
  CONTENT_DIR,
  FOLDERS_DIR,
  readLocal,
  readLocalAssets,
  readLocalFolders,
} from "../content-store.mjs";
import { readLocalDefs } from "../post-types-compile.mjs";
import { PROJECTION_CHECK, lintProjection, splitChecks } from "../verify-projection.mjs";
import {
  BASELINE_FILE,
  acceptedFrom,
  applyBaseline,
  describeEntry,
  parseBaseline,
  serializeBaseline,
} from "../verify-baseline.mjs";

/** Where the projected post-types live — the same default every other command uses. */
const DEFS_DIR = "shio/post-types";

export async function run(flags, positionals, ctx) {
  const site = flags.site ?? positionals[0];
  // SH212 — the tree's own rules run first, and `--checks projection` runs *only* them: no
  // credentials, no network. A defect the files alone prove has no business waiting for a gate
  // that needs an instance.
  const groups = splitChecks(flags.checks);
  const projection = groups.projection ? await lintTree(flags, ctx, site) : null;

  let raw;
  if (groups.callRemote) {
    const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
    const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
    raw = await client.verify({
      site,
      checks: withDelivery(groups.remote, flags.delivery),
      limit: flags.limit,
      folder: flags.folder,
      address: flags.address,
      since: flags.since,
    });
  } else if (flags.delivery) {
    // A caller who asked for the strongest proof there is and named the one group that cannot
    // leave the process has asked for two different things. Silently dropping one is SH320's
    // failure: a flag that requested a proof and got none reads as a pass.
    throw new Error(
      "shio verify: --delivery proves routes by fetching them, which needs the instance — it "
        + `cannot be combined with \`--checks ${PROJECTION_CHECK}\` alone. Name a server-side `
        + `group too (\`--checks ${PROJECTION_CHECK},routes\`), or drop --delivery.`,
    );
  }
  raw = merge(raw, projection, groups);

  const path = typeof flags.baseline === "string" ? flags.baseline : BASELINE_FILE;
  // An explicit `--baseline` that is not there is a mistake worth reporting: a CI job
  // pointing at the wrong path would otherwise pass by checking nothing.
  const baseline = await readBaseline(ctx, path, typeof flags.baseline === "string");

  if (flags.accept) {
    return accept(raw, baseline, path, flags, ctx);
  }

  // SH410 — what this run could have produced, so staleness is judged against coverage rather
  // than against findings. A group that did not run cannot be evidence that its acceptances are
  // spent, and a `--site` run says nothing about the other sites.
  const report = applyBaseline(raw, baseline, {
    groups: [
      ...(groups.projection ? ["projection"] : []),
      ...(groups.callRemote ? ["remote"] : []),
    ],
    site,
  });
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

/**
 * The tree's own findings (SH212) — post-types, posts and asset bytes, all from disk.
 *
 * A tree with no post-types cannot check anything, and saying so is the honest answer: without
 * this the group would report a warning per post and read as noise rather than as "not run".
 */
async function lintTree(flags, ctx, site) {
  // `readLocalDefs` turns a missing directory into a teaching error naming `shio pull`, which is
  // right for a command whose whole job is the post-types and wrong here: a tree with no local
  // model is a tree this group cannot check, not a tree that is broken.
  const defs = await readLocalDefs(ctx, flags.dir ?? DEFS_DIR).catch(noDefs);
  // SH374 — `sidecars: "report"` rather than the default `load`: the default *refuses* on the
  // first `files:` entry naming nothing, which is right for a write and wrong for a lint. This
  // group's whole point is that the tree's defects arrive as findings, all of them, at once.
  const read = await readLocal(ctx, {
    dir: flags.contentDir ?? CONTENT_DIR,
    site,
    sidecars: "report",
  }).catch(absentRead);
  const posts = Object.values(read.docs);
  const assetsDir = flags.assetsDir ?? ASSETS_DIR;
  const assets = await readLocalAssets(ctx, { dir: assetsDir, site }).catch(absent);
  // SH407 — the folder root is the second place a projection turns a name into a path, and it was
  // outside every tree-only gate: `verify` had never read it at all.
  const folders = await readLocalFolders(ctx, {
    dir: flags.foldersDir ?? FOLDERS_DIR,
    site,
    mode: "report",
  }).catch(absentFolders);

  if (!defs?.length || !posts.length) {
    return {
      findings: [],
      checked: 0,
      note: `${PROJECTION_CHECK}: nothing to check in this tree — it holds `
        + `${posts.length} post(s) and ${defs?.length ?? 0} post-type(s)`,
    };
  }
  return {
    ...lintProjection({
      posts,
      defs,
      assets,
      assetsDir,
      missingSidecars: read.missingSidecars,
      duplicates: read.duplicates,
      // One rule, both roots: the finding is a pair of addresses and a pair of paths either way.
      unportable: [...read.unportable, ...folders.unportable],
      // A scoped run is not a statement about the whole tree, so a `translationOf` it could not
      // have seen is a warning rather than an error (SH143's rule).
      scoped: Boolean(site),
    }),
    note: null,
  };
}

/** A directory the tree does not have is not an error here — it is an empty list. */
function absent(error) {
  if (error?.code === "ENOENT") return [];
  throw error;
}

/**
 * No post-types on disk. Narrow on purpose: a directory that is *there* and holds a duplicate
 * name or a broken JSON file still throws, because that is a defect this run should not hide.
 */
function noDefs(error) {
  if (/no post-type directory at/.test(error?.message ?? "")) return [];
  throw error;
}

function absentFolders(error) {
  if (error?.code === "ENOENT") return { markers: [], unportable: [] };
  throw error;
}

function absentRead(error) {
  if (error?.code === "ENOENT") return { docs: {}, missingSidecars: [], duplicates: [], unportable: [] };
  throw error;
}

/**
 * One report out of two halves.
 *
 * The client's findings go **first**, because they are the ones a reader can act on without
 * leaving the machine. Counts are recomputed rather than added: `ok` is the server's own word
 * for "nothing an error", and a merged report has to mean the same thing.
 */
function merge(remote, projection, groups) {
  const base = remote ?? {
    ok: true,
    checked: 0,
    found: 0,
    findings: [],
    errors: 0,
    warnings: 0,
    checks: [],
    notes: [],
  };
  if (!projection) return base;

  const findings = [...projection.findings, ...(base.findings ?? [])];
  const errors = findings.filter((finding) => finding.severity === "error").length;
  const warnings = findings.filter((finding) => finding.severity !== "error").length;
  const notes = [...(base.notes ?? [])];
  if (projection.note) notes.push(projection.note);
  return {
    ...base,
    ok: errors === 0,
    checks: [PROJECTION_CHECK, ...(base.checks ?? [])],
    checked: (base.checked ?? 0) + projection.checked,
    found: (base.found ?? 0) + projection.findings.length,
    findings,
    errors,
    warnings,
    notes,
    // A projection-only run looked at one tree and no instance, so it is not a statement about
    // the site — the same rule a scoped server run follows (SH143).
    partial: base.partial || !groups.callRemote,
  };
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
  // SH214 — the moves list travels through untouched. This command has no opinion about it, and
  // writing the file without it would make `verify --accept` silently un-accept every rename
  // somebody had reviewed: one command's write erasing another's record.
  await ctx.fs.writeFile(target, serializeBaseline(entries, baseline.acceptedMoves), "utf8");

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
/**
 * `--delivery` (SH141) — ask the server to prove each published route by fetching it.
 *
 * A convenience over `--checks …,delivery`, and it names the groups explicitly rather than
 * sending `delivery` alone: a caller reaching for the stronger check wants it *in addition
 * to* the content lint, and `--checks delivery` on its own would quietly drop the rules that
 * do not need a network. Combining it with `--checks` keeps whatever the caller named.
 *
 * If the instance has fetching off, the server answers a teaching 400 that names the property
 * — deliberately, because a flag that asked for a proof and silently got none is worse than
 * an error.
 */
function withDelivery(checks, delivery) {
  if (!delivery) return checks;
  const named = String(checks ?? "content,routes,diagnostics")
    .split(",")
    .map((each) => each.trim())
    .filter(Boolean);
  if (named.length === 0 || named.includes("all")) return "content,routes,diagnostics,delivery";
  return named.includes("delivery") ? named.join(",") : [...named, "delivery"].join(",");
}

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
  // The server's `notes` are the things a caller must know to read the numbers: a change feed
  // that had more entries than fitted, findings a declaration silenced, routes the delivery
  // check did not reach. They were invisible in this output until SH141 needed them, and an
  // unreported cut reads exactly like a clean pass (SH138).
  for (const note of report.notes ?? []) {
    tail.push(`… ${note}`);
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
