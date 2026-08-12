/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH671 — the held-findings ledger, read by both tiers instead of by one.
 *
 * ## Why this file exists
 *
 * `open-findings.json` is how the corpus keeps an assertion in the gate while the defect it names is
 * still open: red-and-held is *reported*, red-and-unheld is a **failure**, and green-and-held is a
 * failure too, because the fix landed and the ledger was not told. That machinery lived inside
 * `cli/test/source-corpus.test.mjs`, so it was a property of the **fast tier** rather than of the
 * corpus — and the live tier, `cli/conformance/source-corpus.conformance.mjs`, had no `holding()`
 * call at all.
 *
 * <p>The consequence was measured rather than argued: the live tier was red at HEAD on `malformed`,
 * `red-suites.json` held `exceptions: []`, and there was **no third place** the red could be
 * declared — so a gate this project calls one of its three sat in the state SH579 says does not
 * exist, and the instrument that would have reported it was the one that was red.</p>
 *
 * ## One ledger, two tiers, and why the entries are scoped
 *
 * A finding belongs to the tier that can observe it. `/deep/page` being *asked for* is a claim only
 * a socket can make; a conversion document's field values are one only the offline chain produces.
 * So an entry carries `tier`, each tier holds its own subset, and each checks **its own** entries in
 * both directions. Without the scope the two tiers would fail each other's entries as unexercised —
 * a stale-key report that means nothing but "the other driver did not run this".
 *
 * <p>`tier` defaults to `"fast"` where an entry omits it, which is the value every entry written
 * before this file would have had. The list was empty when this shipped, so that default is a
 * statement about the format rather than a migration.</p>
 *
 * <p>Extracted rather than copied, and that is the point: this repository has an open task (SH245)
 * about a shared verb whose second copy was the careless one, four times. A ledger whose two readers
 * can disagree about what "held" means is that defect with the stakes of a gate.</p>
 */
import { readFile } from "node:fs/promises";
import assert from "node:assert/strict";

/** The tier an entry belongs to. Absent means the offline chain, which is where the format began. */
export const tierOf = (finding) => finding.tier ?? "fast";

/** `archetype/assertion` — the key a `holding()` call names, unique within a tier. */
export const keyOf = (finding) => `${finding.archetype}/${finding.assertion}`;

/** The ledger as written. Both tiers read the same bytes, so neither can hold a private list. */
export async function ledger() {
  return JSON.parse(await readFile(new URL("./open-findings.json", import.meta.url), "utf8"));
}

/**
 * The held list for one tier, and the `holding()` that runs an assertion under it.
 *
 * <p>Returns `held` (this tier's entries), `exercised` (the keys a run actually reached),
 * `reported` (the red-and-held cases, for the run's own summary) and `holding` itself. The caller
 * owns the both-directions test, because *where* a stale key is reported differs per tier — the
 * fast tier judges it in its group G, the live tier at the end of its own describe.</p>
 */
export function holdings(findings, tier) {
  const held = new Map(
    findings.filter((finding) => tierOf(finding) === tier).map((finding) => [keyOf(finding), finding]),
  );
  const exercised = new Set();
  const reported = [];

  function holding(archetype, assertion, check) {
    const key = `${archetype}/${assertion}`;
    exercised.add(key);
    const entry = held.get(key);
    let failure = null;
    try {
      check();
    } catch (error) {
      failure = error;
    }
    if (!entry) {
      if (failure) throw failure;
      return;
    }
    if (!failure) {
      throw new assert.AssertionError({
        message: `${entry.id} is GREEN: ${key} passes, and open-findings.json still holds it.\n`
          + `  what it held: ${entry.what}\n`
          + "  delete that entry — a fix that lands without the list being told turns this suite "
          + "back into one that reports a number nobody can act on",
      });
    }
    reported.push({ ...entry, key, message: failure.message });
  }

  return { held, exercised, reported, holding };
}
