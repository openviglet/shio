/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH142 — the accepted-findings baseline: the third state a real report needs.
 *
 * `shio verify` answers `ok` or `not ok`, and every real site has findings that are
 * neither: an image that is decorative in a way the markup cannot state, a folder
 * deliberately staged empty, a link into a route the front end owns. The first legitimate
 * warning nobody intends to fix is the moment the exit code stops being read — and the
 * errors stop being read with it. A committed baseline of `(rule, address, field)` triples
 * that the gate subtracts is what keeps the exit code meaningful.
 *
 * The one property that stops it rotting into a blanket suppression: **an accepted finding
 * that no longer exists is itself a failure**. Without that, an entry outlives the defect
 * it excused and silently swallows the next real one at the same address. That check is
 * the reason the baseline is a list of triples and not a list of rules.
 *
 * This module is pure — parse, match, subtract, serialize. The IO and the exit code live
 * in `commands/verify.mjs`.
 *
 * Why it is a project file and not an instance setting: what is acceptable is a property
 * of *this repository's* content and belongs in review beside it, and the thing it
 * protects — the exit code — is the CLI's. The server keeps reporting every finding,
 * which is right: an agent asking "what is wrong here" should be told, even about the
 * parts a human already signed off. Only the gate subtracts.
 */

/** Where a project keeps it, next to the content it describes. */
import { REPLICATION_CHECK, replicationRules } from "./verify-replication.mjs";

export const BASELINE_FILE = "shio/verify-baseline.json";

/** Bumped only for a change that an older CLI would read wrongly. */
export const BASELINE_VERSION = 1;

/**
 * The identity of a finding, for matching.
 *
 * `rule`, `address` and `field` — not `message`, which restates the content and would
 * make every copy-edit look like a new defect, and not `severity`, so promoting a rule's
 * severity does not quietly un-accept everything.
 */
export function keyOf(finding) {
  return [finding?.rule ?? "", finding?.address ?? "", finding?.field ?? ""].join("\u0000");
}

/** Parses a baseline document, tolerating an absent file (an empty baseline). */
export function parseBaseline(raw) {
  if (raw === null || raw === undefined || String(raw).trim() === "") {
    return { version: BASELINE_VERSION, accepted: [] };
  }
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    throw new Error(`${BASELINE_FILE} is not valid JSON: ${error.message}`);
  }
  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error(`${BASELINE_FILE} must be an object with an "accepted" array.`);
  }
  if (!Array.isArray(parsed.accepted)) {
    throw new Error(`${BASELINE_FILE} must be an object with an "accepted" array.`);
  }
  return {
    version: parsed.version ?? BASELINE_VERSION,
    accepted: parsed.accepted.map(normalizeEntry).filter((entry) => entry.rule && entry.address),
    // SH214 — a second list, and a second producer. `shio verify` never looks at it and
    // `push --content` never looks at `accepted`, so neither command's staleness rule can fire on
    // the other's entries. One file, because two would be two things to learn and two diffs to
    // review for one idea: somebody looked at this and said it was fine.
    acceptedMoves: (Array.isArray(parsed.acceptedMoves) ? parsed.acceptedMoves : [])
      .map(normalizeMove)
      .filter((entry) => entry.from && entry.to),
  };
}

function normalizeMove(entry) {
  return {
    from: str(entry?.from),
    to: str(entry?.to),
    reason: str(entry?.reason) || undefined,
  };
}

/**
 * The identity of an accepted delete-plus-create (SH214).
 *
 * **Both addresses, and this is the whole rule.** Accepting "the disappearance of
 * post:Blog/old is fine" alone would silence the warning for a *different* new post arriving
 * next week — which is precisely the case the warning exists for, so a one-sided key turns the
 * acceptance into the blanket suppression SH142 is built to prevent.
 */
export function moveKeyOf(move) {
  return `${move?.from ?? ""} -> ${move?.to ?? ""}`;
}

/**
 * Which probable moves this run may keep quiet about, and which acceptances it spent.
 *
 * <h3>Spent, not stale — the one place this diverges from SH142</h3>
 *
 * A `verify` finding persists until the defect is fixed, so an accepted entry the run no longer
 * produces means the file has rotted and failing is right. A probable move is the opposite: it is
 * **transient**. Once the push happens, the new post is on the server and the old one is deleted
 * or declared, so the pair can never be produced again — the entry would be stale immediately
 * after the push that honoured it, and SH142's rule would fail the next run for doing exactly
 * what was asked.
 *
 * So the entry is **consumed** by the push that uses it. That keeps SH142's actual goal by a
 * stronger mechanism than a check: an acceptance cannot outlive its event, so there is nothing
 * left to rot. A plan consumes nothing, because a plan writes nothing.
 *
 * @param {{from: string, to: string}[]} probable this run's unprovable renames
 * @param {{acceptedMoves: object[]}} baseline
 * @returns {{ report: object[], silenced: object[], keep: object[] }} what to warn about, what
 *   was forgiven, and the acceptances this run did not use — which is what the file keeps
 */
export function applyMoveBaseline(probable, baseline) {
  const accepted = new Map(
    (baseline?.acceptedMoves ?? []).map((entry) => [moveKeyOf(entry), entry]),
  );
  const report = [];
  const silenced = [];
  for (const move of probable ?? []) {
    const key = moveKeyOf(move);
    if (accepted.has(key)) {
      silenced.push({ ...move, reason: accepted.get(key).reason });
      accepted.delete(key);
      continue;
    }
    report.push(move);
  }
  return { report, silenced, keep: [...accepted.values()] };
}

/** This run's probable moves, merged into whatever the file already accepts (SH214). */
export function acceptedMovesFrom(probable, baseline, reason) {
  const merged = new Map(
    (baseline?.acceptedMoves ?? []).map((entry) => [moveKeyOf(entry), entry]),
  );
  const added = [];
  for (const move of probable ?? []) {
    const key = moveKeyOf(move);
    if (merged.has(key)) continue;
    const entry = { from: move.from, to: move.to, ...(reason ? { reason } : {}) };
    merged.set(key, entry);
    added.push(entry);
  }
  return { entries: sortMoves([...merged.values()]), added };
}

/** From, then to — so a diff shows what changed and nothing else (P5). */
export function sortMoves(entries) {
  return [...entries].sort((a, b) =>
    (a.from ?? "").localeCompare(b.from ?? "") || (a.to ?? "").localeCompare(b.to ?? ""));
}

function normalizeEntry(entry) {
  return {
    rule: str(entry?.rule),
    address: str(entry?.address),
    field: str(entry?.field) || undefined,
    reason: str(entry?.reason) || undefined,
  };
}

function str(value) {
  return typeof value === "string" ? value.trim() : "";
}

/**
 * Subtracts the baseline from a report.
 *
 * Returns the report with `findings`, `errors` and `warnings` describing only what is
 * *not* accepted, plus:
 *  - `accepted` — the findings that were subtracted, so the run still says what it forgave
 *  - `stale`    — covered entries nothing matched, which is a failure of its own
 *  - `unproven` — entries this run could not have produced, which is not a failure (SH411)
 *  - `staleUnknown` — true when the report was truncated, so no entry can be judged
 *
 * <h3>Staleness is a claim about coverage, not about findings (SH410)</h3>
 *
 * "Nothing matched this entry" only implies "the defect is gone" when the run *could* have
 * produced it. Truncation was the first way to lose that guarantee and the only one guarded: a
 * shortened list means an entry can look unmatched purely because its finding did not fit, and
 * failing on that is the SH138 hazard inverted — a partial input read as a complete one.
 *
 * Group selection and site scoping lose it exactly the same way, and they are the normal case
 * rather than the edge. `--checks projection` sets `callRemote: false`, so the report holds tree
 * findings only; every acceptance for a server rule then matched nothing, was called stale, and
 * **failed the build** — breaking the server-free gate SH212 built for CI on any project holding
 * a baseline. `--checks content` broke it in reverse, and `--site Blog` condemned every other
 * site's acceptances.
 *
 * So the caller passes what it covered. An entry is only judged when this run ran the group its
 * rule belongs to *and* the scope its address falls in; anything else is unproven, which is what
 * `staleUnknown` already means and what its message already says.
 *
 * @param {{groups?: string[]|null, site?: string|null}} [coverage] what this run could have found
 */
export function applyBaseline(report, baseline, coverage = {}) {
  const entries = baseline?.accepted ?? [];
  if (entries.length === 0) {
    return { ...report, accepted: [], stale: [], unproven: [], staleUnknown: false };
  }
  const wanted = new Map();
  for (const entry of entries) {
    wanted.set(keyOf(entry), entry);
  }

  const kept = [];
  const accepted = [];
  const matched = new Set();
  for (const finding of report.findings ?? []) {
    const key = keyOf(finding);
    if (wanted.has(key)) {
      matched.add(key);
      accepted.push({ ...finding, reason: wanted.get(key).reason });
    } else {
      kept.push(finding);
    }
  }

  // SH410 — only entries this run could have produced are candidates. The rest are not evidence
  // of anything, and `staleUnknown` says so rather than this pretending they were checked.
  const judged = entries.filter((entry) => covers(entry, report, coverage));
  const stale = judged.filter((entry) => !matched.has(keyOf(entry)));
  // SH411 — the entries this run could not have produced, as a **list of their own**. SH410
  // folded them into `staleUnknown`, which `verify` reads all-or-nothing because it used to mean
  // "the report was truncated" — a property of the whole report. One unproven entry then silenced
  // every proven one, so a scoped run reported no stale entry at all and the baseline could rot in
  // exactly the way SH142 exists to prevent. "Spent" and "not checked" are two statements.
  const unproven = entries.filter((entry) => !covers(entry, report, coverage));
  const errors = kept.filter((finding) => finding.severity === "error").length;
  return {
    ...report,
    findings: kept,
    errors,
    warnings: kept.length - errors,
    // `found` keeps counting what the server produced — the accepted ones happened, they
    // were just forgiven, and a report that pretended otherwise could not be reconciled
    // with the server's own numbers.
    ok: errors === 0,
    accepted,
    stale,
    unproven,
    // Truncation only, as before SH410: it is the one cause that makes the *whole* list of
    // findings untrustworthy, so it is the one that may suppress the whole report.
    staleUnknown: Boolean(report.truncated),
  };
}

/**
 * Whether this run could have produced the finding an entry accepts (SH410).
 *
 * Two questions, and both are about the *run* rather than the entry. Which check groups ran — an
 * entry whose rule belongs to a group this run did not ask for was never going to match. And which
 * site — a scoped run says nothing about the others (SH143's rule, one file over).
 *
 * The group is read from the findings' own `check` where the report reports one, and from the
 * rules the projection group owns otherwise, because a rule name is the only thing a baseline
 * entry carries. An unknown rule counts as covered: judging it is the pre-SH410 behaviour, and a
 * new rule nobody has classified should keep failing loudly rather than be quietly forgiven.
 */
function covers(entry, report, { groups = null, site = null } = {}) {
  if (site && !addressInSite(entry.address, site)) return false;
  if (!groups) return true;
  return groups.includes(groupOf(entry.rule));
}

function addressInSite(address, site) {
  const rest = String(address ?? "").slice(String(address ?? "").indexOf(":") + 1);
  const slash = rest.indexOf("/");
  return (slash < 0 ? rest : rest.slice(0, slash)) === site;
}

/**
 * The rules the tree-only group owns; every other rule is the server's.
 *
 * Exported for `verify-baseline.test.mjs`, which derives the same set from the rule literals in
 * `verify-projection.mjs` and fails when they disagree (SH411). It was hand-kept for one task and
 * that was already one too many: every rule in it was added by a task in this run, and a rule added
 * without touching it here is silently classified as the server's.
 */
export function projectionRules() {
  return PROJECTION_RULES;
}

const REPLICATION_RULES = replicationRules();

const PROJECTION_RULES = new Set([
  "asset-missing",
  "asset-path-unsafe",
  "asset-check-skipped",
  "asset-unsafe-type",
  "sidecar-missing",
  "translation-target-missing",
  "duplicate-address",
  "path-collision",
  "menu-item-no-link",
  "menu-item-ambiguous-link",
]);

/**
 * `"projection"` or `"remote"` — a two-way split, and deliberately not three.
 *
 * An "unknown" bucket that counted as covered would reinstate the defect: `required-field-empty`
 * is not in the projection list, and treating it as unclassified is exactly how a tree-only run
 * came to judge it. Every rule this CLI does not own is the server's, which is true by
 * construction — the server is the only other thing that produces findings.
 */
function groupOf(rule) {
  if (PROJECTION_RULES.has(rule)) return "projection";
  // SH387 - a third *selection* group, and still not a third bucket in the sense SH410 refused:
  // the set is closed, every rule this CLI owns is in one of the two local lists, and everything
  // else is the server's by construction. What it buys is that `--checks projection` alone no
  // longer counts a replication acceptance as covered, which is the false positive SH410 exists
  // to prevent.
  if (REPLICATION_RULES.has(rule)) return REPLICATION_CHECK;
  return "remote";
}

/**
 * The baseline that would accept everything this run found, merged over an existing one.
 *
 * Merged rather than replaced, because a run narrowed by `--site` or `--checks` sees only
 * part of the project and a replacing write would silently drop every entry outside that
 * scope. The cost is that removing an entry is a hand edit — which is correct: un-accepting
 * a defect is a decision, and `--accept` is the tool for the opposite one.
 */
export function acceptedFrom(report, baseline, reason) {
  const merged = new Map();
  for (const entry of baseline?.accepted ?? []) {
    merged.set(keyOf(entry), entry);
  }
  const added = [];
  for (const finding of report.findings ?? []) {
    const key = keyOf(finding);
    if (merged.has(key)) {
      continue;
    }
    const entry = {
      rule: finding.rule,
      address: finding.address,
      field: finding.field || undefined,
      reason: str(reason) || undefined,
    };
    merged.set(key, entry);
    added.push(entry);
  }
  return { entries: sortEntries([...merged.values()]), added };
}

/** Rule, then address, then field — so a diff shows what changed and nothing else (P5). */
export function sortEntries(entries) {
  return [...entries].sort((a, b) =>
    (a.rule ?? "").localeCompare(b.rule ?? "") ||
    (a.address ?? "").localeCompare(b.address ?? "") ||
    (a.field ?? "").localeCompare(b.field ?? ""));
}

/**
 * The file, byte-identical for the same set of entries.
 *
 * Keys are written in a fixed order and `undefined` ones are dropped, so re-running
 * `--accept` on an unchanged project produces no diff at all.
 */
export function serializeBaseline(entries, moves = []) {
  const document = {
    version: BASELINE_VERSION,
    note:
      "Findings reviewed and accepted. `shio verify` subtracts these and fails if one " +
      "no longer exists — remove the entry when you fix the defect.",
    accepted: sortEntries(entries).map((entry) => ({
      rule: entry.rule,
      address: entry.address,
      ...(entry.field ? { field: entry.field } : {}),
      ...(entry.reason ? { reason: entry.reason } : {}),
    })),
    // SH214 — omitted entirely when empty, so a project that never accepted a rename keeps a file
    // byte-identical to the one SH142 wrote.
    ...(moves.length
      ? {
          acceptedMoves: sortMoves(moves).map((entry) => ({
            from: entry.from,
            to: entry.to,
            ...(entry.reason ? { reason: entry.reason } : {}),
          })),
        }
      : {}),
  };
  return `${JSON.stringify(document, null, 2)}\n`;
}

/** One accepted rename, for a message. */
export function describeMove(entry) {
  return `${entry.from} -> ${entry.to}`;
}

/** One entry, for a message. */
export function describeEntry(entry) {
  const where = [entry.address, entry.field].filter(Boolean).join(" · ");
  return `${entry.rule}${where ? ` ${where}` : ""}`;
}
