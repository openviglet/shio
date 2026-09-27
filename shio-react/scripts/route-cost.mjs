/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SH1113 — what ONE console route costs, which the whole-console budget cannot see.
 *
 * ## Why a second budget, when SH1043 already measures the console
 *
 * `console-size-budget.json` measures one number for the whole console: what every route pays
 * before the first row. That is the right shape for a budget and the wrong shape for what a
 * curator experiences, which is a *page*. A route that fetches three collections to draw two
 * figures costs exactly the same eager bytes as one that fetches nothing, so the bundle gate
 * reports it as free. SH1050 was that page, and nothing would have caught it.
 *
 * This is not a refinement of that measurement — it is a different measurement taken somewhere
 * else. The bundle gate reads files on disk and cannot know what a page then asks the server for.
 * Only a browser driving a live instance can, which is why the probe half lives in
 * `e2e/console-check.mjs` and runs with conformance rather than with `pnpm test`.
 *
 * ## Per route, never a total
 *
 * A total is what lets a new screen's cost hide inside the sum: add a page that makes nine calls
 * to a console that makes four hundred and the total moves 2%. The entry is per route for the same
 * reason `token-budgets.properties` is per response.
 *
 * ## The three numbers, and why they are held differently
 *
 * - **`requests`** — how many calls the page makes. Held at the measurement with **no headroom**,
 *   deliberately, and it is the only one of the three held that way. A request count is discrete
 *   and deterministic: the page either asks for the post-type catalogue or it does not, and the
 *   number does not drift with a machine or a payload. An extra fetch is precisely the defect this
 *   gate was filed for, so a ceiling with room in it would be a gate that lets the first one
 *   through. `SH1113: a planted fetch fails that route and no other` is that claim, asserted.
 * - **`bytes`** — what came back. Carries headroom, because a row's payload grows with the content
 *   on the instance and a ceiling set to the measurement would refuse the next change instead of
 *   measuring it (SH736, and every raise recorded in the bundle budget).
 * - **`firstRow`** — a **ratio against a baseline route**, not a millisecond count. Milliseconds
 *   move with the machine, so a ceiling in them is one nobody can reproduce and therefore one that
 *   gets raised until it means nothing. The ratio divides that variance out: a page that is three
 *   times the site list on a fast machine is three times the site list on a slow one.
 *
 *   It is a **coarse** guard, and seeded coarsely (`FIRST_ROW_SLACK`, with a floor), which the
 *   first recording run is the argument for: across 52 families one seeded at 26x and several
 *   neighbours at 1.0x, on pages that differ by one list read. A single run cannot establish a
 *   tight time ceiling, and a tight one seeded from it would be a flaky gate — the failure mode
 *   this repository treats as worse than no gate, because it is the one people learn to re-run
 *   past. So this number catches a page that has become an order of magnitude worse and nothing
 *   finer. `requests` is the sharp instrument here, and it is sharp precisely because it is
 *   discrete.
 *
 * ## Families, because the route list is discovered and not declared
 *
 * `console-check` builds its routes from the instance — one post editor per post-type it finds, a
 * site id, a folder id. So `post-edit:Page` is a name only this instance has, and a budget keyed on
 * it would report every other instance's post-types as unbudgeted routes. The key is therefore the
 * **family**: the name up to its first colon. That is also the better claim to make. "The post
 * editor costs eleven requests" is a fact about the console; "the Page editor costs eleven" is a
 * fact about somebody's content. Where a family has several members in one run, the costliest is
 * the one compared — a ceiling that tracked the cheapest would pass on the page that is slow.
 *
 * ## The asymmetry between a missing entry and a missing route
 *
 * The bundle budget treats both as failures, because its chunks are deterministic from a build.
 * These routes are not: an instance with no posts has no post editor to walk. So the two halves are
 * held differently, and the split is the whole of what makes this gate runnable on a thin instance
 * without being toothless on a full one:
 *
 * - A **walked family with no entry is a finding.** A new screen must declare what it costs; that
 *   is the direction the defect actually arrives from.
 * - An **entry nothing walked is a note.** It means this instance lacked the fixture, which is not
 *   a regression. `staleEntries` reports them so a family that has genuinely gone is still visible
 *   — the way SH1044 found four bundle ceilings that had outlived their chunks.
 */

/** Every metric an entry holds, in the order a report prints them. */
export const METRICS = ["requests", "bytes", "firstRow"];

/**
 * The room a `bytes` ceiling is expected to leave over its measurement.
 *
 * <p>The same 4% the bundle budget's headroom check asks for, and for the same reason: a ceiling
 * set to the total refuses the next change rather than measuring it.</p>
 */
export const BYTES_HEADROOM = 0.04;

/**
 * The family a probe's name belongs to — the name up to its first colon.
 *
 * <p>See the class note: the route list is discovered from the instance, so `post-edit:Page` is a
 * name only one instance has and `post-edit` is the claim worth holding a ceiling for.</p>
 */
export function familyOf(name) {
  return String(name).split(":")[0];
}

/**
 * The costliest member of each family, keyed by family.
 *
 * <p>Costliest by request count, then by bytes: those are the two that do not move with the
 * machine. A family whose members disagree is reported at its worst, because a ceiling set to the
 * cheapest member passes on the expensive one — which is the same "a total hides a page" mistake
 * one level down.</p>
 */
export function foldFamilies(measured) {
  const worst = {};
  for (const [name, row] of Object.entries(measured)) {
    const family = familyOf(name);
    const held = worst[family];
    if (!held || row.requests > held.requests
        || (row.requests === held.requests && row.bytes > held.bytes)) {
      worst[family] = { ...row, name };
    }
  }
  return worst;
}

/**
 * How much slack a seeded `firstRow` ceiling carries over the ratio that was measured, and the
 * floor below which it is not set at all. Both are wide on purpose — see the class note.
 */
export const FIRST_ROW_SLACK = 3;
export const FIRST_ROW_FLOOR = 2;

/** Ceilings for a family nobody has costed yet: exact on requests, with room on the other two. */
export function seedCeilings(measurement, baselineMs) {
  return {
    requests: measurement.requests,
    bytes: Math.max(1000, Math.ceil((measurement.bytes * (1 + BYTES_HEADROOM * 2)) / 1000) * 1000),
    firstRow: measurement.ms == null
      ? null
      : Math.max(FIRST_ROW_FLOOR,
        Math.ceil((measurement.ms / baselineMs) * FIRST_ROW_SLACK * 10) / 10),
  };
}

/**
 * One family's measured cost against its entry.
 *
 * @param family the family, as both the probe and the budget spell it
 * @param entry the budget's ceilings plus its `why`
 * @param measured `{ requests, bytes, ms, name }` as the probe recorded them
 * @param baselineMs the baseline family's `ms` from the same run, which makes `ms` a ratio
 * @returns this family's findings, each a sentence naming what to do about it
 */
export function compareFamily(family, entry, measured, baselineMs) {
  const findings = [];

  if (!entry.why || !entry.why.trim()) {
    findings.push(`${family}: the entry carries no \`why\`. A ceiling without a sentence is a `
      + "number the next person raises without knowing what it was protecting — which is the state "
      + "this gate exists to leave behind, not to reproduce.");
  }
  if (measured.requests > entry.requests) {
    findings.push(`${family}: ${measured.requests} requests on ${measured.name}, against a ceiling `
      + `of ${entry.requests}. This ceiling is held at the measurement on purpose: an extra fetch `
      + "per route is the defect SH1113 was filed for, so there is no room in it to absorb one. "
      + "Either the page gained a call it does not need — a collection read to draw a figure, a "
      + "catalogue refetched per row — or the call is right and the ceiling is raised in the same "
      + "commit, with the reason written into its `why`.");
  }
  if (measured.bytes > entry.bytes) {
    findings.push(`${family}: ${measured.bytes} bytes on ${measured.name}, against a ceiling of `
      + `${entry.bytes}. Payloads grow with the content on an instance, so this ceiling has room `
      + "in it; over it means the page is reading more than it draws, or the ceiling is stale.");
  }
  if (measured.ms != null && entry.firstRow != null) {
    const ratio = Math.round((measured.ms / baselineMs) * 100) / 100;
    if (ratio > entry.firstRow) {
      findings.push(`${family}: first row at ${ratio}x the baseline route, against a ceiling of `
        + `${entry.firstRow}x. The ratio is what makes this reproducible on another machine — a `
        + "regression here is the page waiting on something, usually a read it makes in series "
        + "with one it could make beside.");
    }
  }
  return findings;
}

/**
 * Every walked family's cost against the committed budget.
 *
 * @throws Error on the shapes that would make a pass meaningless — nothing measured, or a baseline
 *     the run never observed, which would leave every ratio relative to nothing
 */
export function compare(budget, measured) {
  const entries = budget.routes ?? {};
  const walked = foldFamilies(measured);
  const names = Object.keys(walked);
  if (!names.length) {
    throw new Error("SH1113: no route was measured. A budget gate handed nothing to compare "
      + "reports no findings, which is indistinguishable from a console that costs nothing — the "
      + "report-zero shape every reader in this repository is written against.");
  }

  const baseline = budget.baseline;
  const baselineRow = walked[baseline];
  if (!baselineRow || !(baselineRow.ms > 0)) {
    throw new Error(`SH1113: the baseline route '${baseline}' was not observed loading, so every `
      + "`firstRow` ratio would be against nothing. It is named in the budget rather than picked "
      + "per run precisely so a run cannot quietly change what the numbers are relative to — pick "
      + "the cheapest route the console has and keep it there.");
  }

  const findings = [];
  for (const family of names.sort()) {
    const entry = entries[family];
    if (!entry) {
      findings.push(`${family}: this route has no entry in the budget. A screen that arrives `
        + "without a ceiling is the one case this gate cannot report on, so it is a finding rather "
        + "than a pass — run `console:check --record-cost` against an instance and commit what it "
        + "measured, with a sentence saying what the route is for.");
      continue;
    }
    findings.push(...compareFamily(family, entry, walked[family], baselineRow.ms));
  }
  findings.push(...belowFloor(budget));
  return findings;
}

/** The route every other entry is measured on top of: a 404 renders the shell and nothing else. */
export const FLOOR_ROUTE = "unknown-route";

/**
 * SH1172 — a ceiling below the shell's own floor, which no page can meet.
 *
 * <p>Every route this budget costs renders inside the shell, so it pays the shell's reads before
 * its own. The first seed held eleven byte ceilings at 1–2 KB against a 404 page that reads 3 KB,
 * because it was taken from a run the connection pool was failing: a page that can never pass is a
 * gate somebody learns to re-seed past, which is worse than no gate. So an entry under the floor is
 * a finding of its own, naming the seed rather than the page.</p>
 */
export function belowFloor(budget) {
  const floor = budget.routes?.[FLOOR_ROUTE];
  if (!floor) return [];
  const findings = [];
  for (const [family, entry] of Object.entries(budget.routes)) {
    if (family === FLOOR_ROUTE) continue;
    for (const metric of ["requests", "bytes"]) {
      if (entry[metric] != null && floor[metric] != null && entry[metric] < floor[metric]) {
        findings.push(`${family}: the ${metric} ceiling ${entry[metric]} is below the shell's own floor`
          + ` (${FLOOR_ROUTE}: ${floor[metric]}), so no page can meet it. The seed is wrong, not the`
          + " page — re-run `console:check --base <jar> --record-cost` on a healthy instance.");
      }
    }
  }
  return findings;
}

/**
 * Entries this run never walked — reported, never failed. See the class note's last section.
 */
export function staleEntries(budget, measured) {
  const walked = foldFamilies(measured);
  return Object.keys(budget.routes ?? {})
    .filter((family) => !walked[family])
    .sort()
    .map((family) => `${family}: the budget holds a ceiling this run never walked. On a thin `
      + "instance that is a missing fixture and not a regression, which is why it is a note. If "
      + "the route is gone, so is its entry: a ceiling that outlives its route is a number nobody "
      + "is measuring that reads as coverage.");
}

/**
 * The `bytes` headroom check, reported rather than failed.
 *
 * <p>Separate from {@link compare} on purpose: a ceiling that has been grown into is not a
 * regression, it is a warning that the *next* change will be refused rather than measured. The
 * bundle budget makes the same split.</p>
 */
export function headroom(budget, measured) {
  const walked = foldFamilies(measured);
  const notes = [];
  for (const [family, entry] of Object.entries(budget.routes ?? {})) {
    const row = walked[family];
    if (!row || row.bytes > entry.bytes) continue;
    const room = (entry.bytes - row.bytes) / entry.bytes;
    if (room < BYTES_HEADROOM) {
      notes.push(`${family}: ${Math.round(room * 1000) / 10}% of byte room left, under the `
        + `${BYTES_HEADROOM * 100}% this asks for — raise it now, with a reason, rather than `
        + "letting the next change be refused instead of measured.");
    }
  }
  return notes;
}
