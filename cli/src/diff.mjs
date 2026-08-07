/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH522 — both sides are held, and nothing compared them.
 *
 * ## The question the product could not answer
 *
 * Shio holds both sides of every comparison it does not make. A **capture** is a declaration of the
 * source: six pages, eleven links, one image, this much text. A **proposal** is a declaration of
 * intent. A **desired-state document** is a declaration of the write. And the artefact each one
 * produces is compared with none of them.
 *
 * So "is this what it was supposed to be?" — the question every replication round asks — had no
 * answer in the product, and was answered by hand. That hand has been wrong twice: *zero broken
 * references* on a page with a missing logo, and *106 editable fields* on a page that had lost most
 * of its content. Both times the declaration was on disk, the artefact was on the wire, and the join
 * was forty lines of Python written from memory.
 *
 * ## Why this is a join rather than an analysis
 *
 * **Every artefact carries the identity of what declared it**, and that is what makes it cheap: the
 * inventory names the capture, the proposal names the inventory, the document names the proposal.
 * Nothing has to be guessed or re-fetched — the chain is already written down and nobody had walked
 * it.
 *
 * ## One scanner, both sides
 *
 * The declared counts came out of `scanPage`, so the produced ones come out of `scanPage` too. That
 * is SH245's rule and it is the whole of why this can be trusted: a second reader of "how many links
 * does this page have" would disagree with the first somewhere, and the disagreement would read as a
 * replica that lost content. The comparison below is arithmetic over two runs of one function.
 *
 * SH554 — and one function was not enough, because `scanPage` is only *half* of what an inventory
 * row records. Its `links` is an array of raw hrefs; the row's is `{internal, external}`, split by
 * a rule that lived inline in `buildInventory` and ran on the declared side only. Both steps are
 * now shared — `scanPage` then `countLinks` — and `assertComparable` refuses a side that skipped
 * the second, because the wrong shape did not crash here: it scored, plausibly, as a total lost.
 */

/**
 * What a page declared and what it produced, per fact (SH522).
 *
 * <p>Deliberately not pixels. A picture answers "does it look the same" and needs a browser, a
 * baseline and a human; `shio snapshot` is that and says so. This answers the strictly different
 * question a gate can act on: **is the content still there**.</p>
 *
 * @param {object} declared a page row from the capture's inventory
 * @param {object} produced `scanPage`'s reading of the replica's own bytes
 * @returns {{rows: object[], lost: number, gained: number}} one row per fact, with the two counts
 *   a caller branches on — `lost` is the one that means a defect
 */
export function comparePage(declared, produced) {
  assertComparable(declared, "declared");
  assertComparable(produced, "produced");
  const rows = FACTS.map((fact) => {
    const was = fact.of(declared) ?? 0;
    const now = fact.of(produced) ?? 0;
    return { fact: fact.name, declared: was, produced: now, delta: now - was };
  });
  return {
    rows,
    // A fact the replica has less of than the source declared. `gained` is counted apart and is
    // never a failure: a replica legitimately gains the links a Shio layout adds, and reporting
    // that as damage would make the gate fire on every run of a working conversion.
    lost: rows.filter((row) => row.delta < 0).length,
    gained: rows.filter((row) => row.delta > 0).length,
  };
}

/**
 * The facts both sides record, which is what bounds this to a join.
 *
 * <p>Each one is read off `scanPage`'s output on the produced side and off the inventory row on the
 * declared side — and the inventory row was itself written from `scanPage`. Nothing here is derived
 * twice or by two different rules.</p>
 *
 * <p>`textChars` is compared with a floor rather than exactly: prose survives a conversion and its
 * whitespace does not, so an exact comparison would report every page as lost. The floor is a
 * proportion, because "300 characters short" means something different on a landing page and on a
 * legal notice.</p>
 */
const FACTS = [
  { name: "internal links", of: (page) => page.links?.internal },
  { name: "external links", of: (page) => page.links?.external },
  { name: "assets", of: (page) => (Array.isArray(page.assets) ? page.assets.length : page.assets) },
  { name: "forms", of: (page) => (Array.isArray(page.forms) ? page.forms.length : page.forms) },
];

/**
 * The shape check that would have caught SH554 at the boundary it crossed.
 *
 * <p>The header above claims both sides come out of `scanPage`, and they do — but `scanPage`'s
 * `links` is an *array* of hrefs, and an inventory row's is `{internal, external}`. `FACTS` used to
 * paper over the difference with `page.links?.internal ?? page.links?.length`, so an unclassified
 * side reported its total as internal and zero as external: a plausible pair of numbers, wrong on
 * every page, and red in the CI gate SH548 had just put this command into. Callers pass the
 * produced side through `countLinks` (SH554); this refuses the raw shape rather than scoring it,
 * because the failure mode is not a crash — it is four believable facts.</p>
 */
function assertComparable(page, side) {
  if (!Array.isArray(page?.links)) return;
  throw new Error(
    `shio diff: the ${side} side carries links as a raw array, which cannot be compared (SH554).\n`
      + "  fix: classify it first — countLinks(doc.links, pageUrl) — so both sides are\n"
      + "       {internal, external}, which is the shape an inventory row records.\n"
      + "  why: scoring the array reads its length as the internal count and zero as the external\n"
      + "       one, so every page reports a loss it did not suffer.",
  );
}

/** Below this share of the source's text, a page has lost prose rather than whitespace. */
export const TEXT_FLOOR = 0.6;

/**
 * The whole comparison, page by page (SH522).
 *
 * @param {object[]} declared the capture's `pages`
 * @param {Map<string, object>} produced path → `scanPage`'s reading of the replica, or absent when
 *   the replica did not serve that path at all
 * @returns {{pages: object[], missing: string[], lost: number}}
 */
export function compareCapture(declared, produced) {
  const pages = [];
  const missing = [];
  for (const page of [...declared].sort((a, b) => (a.path < b.path ? -1 : 1))) {
    const now = produced.get(page.path);
    if (!now) {
      // Not a comparison at all: the page is absent, which `verify --checks replication` already
      // reports as `source-page-missing`. Named here rather than counted as "everything lost", or
      // one absent page would read as four separate defects.
      missing.push(page.path);
      continue;
    }
    const { rows, lost, gained } = comparePage(page, now);
    const declaredText = page.textChars ?? 0;
    const producedText = now.textChars ?? 0;
    const textLost = declaredText > 0 && producedText < declaredText * TEXT_FLOOR;
    pages.push({
      path: page.path,
      title: { declared: page.title ?? null, produced: now.title ?? null },
      rows,
      text: { declared: declaredText, produced: producedText, lost: textLost },
      lost: lost + (textLost ? 1 : 0),
      gained,
    });
  }
  return {
    pages,
    missing,
    lost: pages.reduce((total, page) => total + page.lost, 0),
  };
}

/**
 * What a reader sees, and it leads with the answer rather than with the table.
 *
 * <p>The question is "is this what it was supposed to be", so the first line answers it. A page that
 * matched prints one line, because a report whose clean rows are as long as its broken ones is one
 * nobody reads to the end (SH138's rule, applied to a comparison instead of to a cut).</p>
 */
export function describeDiff({ pages, missing, lost }, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 1) : 20;
  const lines = [
    `shio diff: ${pages.length} page(s) compared with the capture that declared them`
      + `${missing.length ? `, ${missing.length} the replica does not serve` : ""}`
      + ` — ${lost ? `${lost} fact(s) lost` : "nothing lost"}`,
  ];
  for (const page of pages.slice(0, limit)) {
    if (!page.lost && !page.gained) {
      lines.push(`  ${page.path}  as declared`);
      continue;
    }
    lines.push(`  ${page.path}`);
    if (page.title.declared !== page.title.produced) {
      lines.push(`    title: ${JSON.stringify(page.title.declared)} → ${JSON.stringify(page.title.produced)}`);
    }
    for (const row of page.rows) {
      if (row.delta === 0) continue;
      lines.push(
        `    ${row.fact}: ${row.declared} → ${row.produced}`
          + `${row.delta < 0 ? `  (${row.delta})` : `  (+${row.delta})`}`,
      );
    }
    if (page.text.lost) {
      lines.push(`    text: ${page.text.declared} → ${page.text.produced} characters`);
    }
  }
  if (pages.length > limit) {
    lines.push(`  … ${pages.length - limit} more page(s) compared`);
  }
  for (const path of missing.slice(0, limit)) {
    lines.push(`  ${path}  the replica serves nothing here`);
  }
  lines.push(
    "  a fact the replica GAINED is not a loss — a layout adds links, and a gate that called "
      + "that damage would fire on every working conversion",
  );
  return `${lines.join("\n")}\n`;
}
