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

import { addressablePath } from "./clone.mjs";

/**
 * What a page declared and what it produced, per fact (SH522).
 *
 * <p>Deliberately not pixels. A picture answers "does it look the same" and needs a browser, a
 * baseline and a human; `shio snapshot` is that and says so. This answers the strictly different
 * question a gate can act on: **is the content still there**.</p>
 *
 * @param {object} declared a page row from the capture's inventory
 * @param {object} produced `scanPage`'s reading of the replica's own bytes
 * @param {string} [mode] how this page was made — see `MODES` (SH577); default `fidelity`
 * @returns {{rows: object[], lost: number, byDesign: number, gained: number}} one row per fact,
 *   with the counts a caller branches on — `lost` is the one that means a defect
 */
export function comparePage(declared, produced, mode = "fidelity") {
  assertComparable(declared, "declared");
  assertComparable(produced, "produced");
  const claimsVerbatim = assertMode(mode).claimsVerbatimFacts;
  const rows = FACTS.map((fact) => {
    const was = fact.of(declared) ?? 0;
    const now = fact.of(produced) ?? 0;
    return {
      fact: fact.name,
      declared: was,
      produced: now,
      delta: now - was,
      // SH577 — whether a shortfall in this fact is damage *on a page made this way*. Carried on the
      // row rather than filtered out, because the number is still worth printing: it is how SH575's
      // missing navigation was found, and a fact removed from the report is a fact nobody can find.
      claimed: claimsVerbatim || !fact.verbatimOnly,
    };
  });
  const short = rows.filter((row) => row.delta < 0);
  return {
    rows,
    // A fact the replica has less of than the source declared **and** the mode claims. `gained` is
    // counted apart and is never a failure: a replica legitimately gains the links a Shio layout
    // adds, and reporting that as damage would make the gate fire on every working conversion.
    lost: short.filter((row) => row.claimed).length,
    // And a shortfall the mode does not claim, which is neither damage nor silence (SH577).
    byDesign: short.filter((row) => !row.claimed).length,
    gained: rows.filter((row) => row.delta > 0).length,
  };
}

/**
 * How a page was made, and therefore what the capture's counts are a claim about (SH577).
 *
 * <p><b>The gate was asking the wrong question of the right command.</b> `diff` compares a replica
 * with the capture that declared it, which is exactly what fidelity answers — the source's own
 * markup, served by Shio, every fact still there, and the bar is 0.00%. It is not what an authorable
 * conversion answers. Composing a page out of the SH95 vocabulary drops the source's nav, its
 * favicon link and its stylesheet tag <i>by design</i>: that is what the vocabulary is for, the
 * captured theme is deliberately not on the page (SH507/SH510), and only what a reviewer accepted is
 * composed at all (SH520). The conformance chain accepted its candidates and then asserted
 * `lost === 0`, so it was red at HEAD for two rounds over chrome an authorable page is designed not
 * to keep — and a red run that is wrong is a red run nobody reads.</p>
 *
 * <p><b>One standard, three modes.</b> The standard does not change: an artefact is judged against
 * what its mode claims. What changes is the claim, and the claim is a property of how the page was
 * made — which the chain already wrote down, in the proposal's composition and the plan's
 * acceptances. Nothing here is inferred from the page's own bytes; that would be the second opinion
 * SH522's header refuses.</p>
 *
 * <p><b>What every mode claims: the content.</b> `textChars` and "the replica serves this path at
 * all" are claims in all three, because a page that lost its prose has lost the thing the conversion
 * existed to move. What only `fidelity` claims are the four facts below marked `verbatimOnly`.</p>
 *
 * <p>The fourth scenario a replication round runs — <b>headless</b>, the Next.js front end over the
 * CDA — is not a mode here and is refused by name in `shio diff --mode`: those pages are served by
 * another origin this command never asks, so accepting the word would answer a question it did not
 * ask (P6's rule about a flag that accepts a value and answers approximately).</p>
 */
export const MODES = {
  fidelity: {
    claimsVerbatimFacts: true,
    says: "the source's own markup, served by Shio — every captured fact is a claim",
  },
  mixed: {
    claimsVerbatimFacts: false,
    says: "some blocks accepted and the rest kept verbatim (SH520) — the accepted blocks' chrome "
      + "is gone by design",
  },
  authorable: {
    claimsVerbatimFacts: false,
    says: "composed from the section vocabulary — the source's nav, stylesheet and favicon are "
      + "gone by design, and only accepted blocks are on the page",
  },
};

/** A mode this cannot judge, named rather than silently accepted — see `MODES`. */
const NOT_A_MODE = {
  headless: "the Next.js front end over the CDA serves those pages from another origin, which this "
    + "command never asks — `shio diff` reads /preview/<site><path> on the instance",
};

export function assertMode(mode) {
  const known = MODES[mode];
  if (known) return known;
  const extra = NOT_A_MODE[mode] ? `\n  why: ${NOT_A_MODE[mode]}` : "";
  throw new Error(
    `shio diff: ${JSON.stringify(mode)} is not a conversion mode this can judge (SH577).\n`
      + `  allowed: ${Object.keys(MODES).join(", ")}${extra}\n`
      + "  why: the mode says what the capture's counts are a CLAIM about. A fidelity replica\n"
      + "       keeps every fact; an authorable page drops the source's chrome on purpose.",
  );
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
 * legal notice. It is also the one fact **every** mode claims — see `MODES`.</p>
 *
 * <p><b>`verbatimOnly` marks a fact only a fidelity replica claims (SH577)</b>, with the reason
 * beside it, because the reason is what a reader of the report needs and it is different for each
 * one. These are not "facts that do not matter": a shortfall is still counted, still printed and
 * still says how far short — it is how SH575's missing navigation was found — it just is not
 * damage on a page whose making removed it on purpose.</p>
 */
const FACTS = [
  {
    name: "internal links",
    of: (page) => page.links?.internal,
    verbatimOnly: true,
    why: "the source's nav and footer are chrome the vocabulary does not match, so they are not "
      + "composed; what the layout emits instead is the site's own navigation (SH575)",
  },
  {
    name: "external links",
    of: (page) => page.links?.external,
    verbatimOnly: true,
    why: "same: an off-host link the source carried in its chrome has no section to live in",
  },
  {
    name: "assets",
    of: (page) => (Array.isArray(page.assets) ? page.assets.length : page.assets),
    verbatimOnly: true,
    why: "the captured theme is deliberately not on an authorable page (SH510) and the favicon "
      + "link is chrome, so a page keeps only the assets its accepted sections carry",
  },
  {
    name: "forms",
    of: (page) => (Array.isArray(page.forms) ? page.forms.length : page.forms),
    verbatimOnly: true,
    why: "the SH95 vocabulary has no form section, so a source's form is not among the candidates "
      + "a reviewer can accept",
  },
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
 * @param {{mode?: string, modes?: Map<string, string>}} [options] how the pages were made (SH577).
 *   `modes` is per path and `mode` is the fallback for a path it does not name; both default to
 *   `fidelity`, which is what this did before it could tell the difference.
 * @returns {{pages: object[], missing: string[], lost: number, byDesign: number, modes: object}}
 */
export function compareCapture(declared, produced, { mode = "fidelity", modes } = {}) {
  const pages = [];
  const missing = [];
  const unaddressable = [];
  const counted = Object.fromEntries(Object.keys(MODES).map((name) => [name, 0]));
  for (const page of [...declared].sort((a, b) => (a.path < b.path ? -1 : 1))) {
    const now = produced.get(page.path);
    if (!now) {
      // SH557 — two different claims, and only one of them is about the replica. A path carrying a
      // `?` is one no friendly URL can hold (SH79), so the replica was never going to serve it: that
      // is a **source trait the target cannot represent**, and reporting it as an absence puts it in
      // the vocabulary of damage. Measured on vilt-group.com: *107 page(s) compared, 91 the replica
      // does not serve* — 91 paginated case-study query strings, read by everyone who saw it as a
      // catastrophic replication failure. It is one fact about the source, knowable at capture.
      (addressablePath(page.path) ? missing : unaddressable).push(page.path);
      continue;
    }
    const pageMode = modes?.get(page.path) ?? mode;
    const { rows, lost, byDesign, gained } = comparePage(page, now, pageMode);
    counted[pageMode] += 1;
    const declaredText = page.textChars ?? 0;
    const producedText = now.textChars ?? 0;
    // The content, which every mode claims: an authorable page is judged on what it should KEEP.
    const textLost = declaredText > 0 && producedText < declaredText * TEXT_FLOOR;
    pages.push({
      path: page.path,
      mode: pageMode,
      title: { declared: page.title ?? null, produced: now.title ?? null },
      rows,
      text: { declared: declaredText, produced: producedText, lost: textLost },
      lost: lost + (textLost ? 1 : 0),
      byDesign,
      gained,
    });
  }
  return {
    pages,
    missing,
    unaddressable,
    modes: counted,
    lost: pages.reduce((total, page) => total + page.lost, 0),
    byDesign: pages.reduce((total, page) => total + page.byDesign, 0),
  };
}

/**
 * What a reader sees, and it leads with the answer rather than with the table.
 *
 * <p>The question is "is this what it was supposed to be", so the first line answers it. A page that
 * matched prints one line, because a report whose clean rows are as long as its broken ones is one
 * nobody reads to the end (SH138's rule, applied to a comparison instead of to a cut).</p>
 */
export function describeDiff(comparison, options = {}) {
  const { pages, missing, unaddressable = [], lost, byDesign = 0, modes } = comparison;
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 1) : 20;
  const lines = [
    `shio diff: ${pages.length} page(s) compared with the capture that declared them`
      + `${describeModes(modes)}`
      + `${missing.length ? `, ${missing.length} the replica does not serve` : ""}`
      + `${unaddressable.length ? `, ${unaddressable.length} no friendly URL can address` : ""}`
      + ` — ${lost ? `${lost} fact(s) lost` : "nothing lost"}`
      // SH577 — said on the headline, because it is the number that used to be indistinguishable
      // from damage. A reader who sees only "nothing lost" on an authorable replica cannot tell a
      // conversion that kept everything from one whose chrome was dropped as designed.
      + `${byDesign ? `, ${byDesign} the mode does not claim` : ""}`,
  ];
  for (const page of pages.slice(0, limit)) {
    if (!page.lost && !page.byDesign && !page.gained) {
      lines.push(`  ${page.path}  as declared`);
      continue;
    }
    lines.push(`  ${page.path}${page.mode && page.mode !== "fidelity" ? `  (${page.mode})` : ""}`);
    if (page.title.declared !== page.title.produced) {
      lines.push(`    title: ${JSON.stringify(page.title.declared)} → ${JSON.stringify(page.title.produced)}`);
    }
    for (const row of page.rows) {
      if (row.delta === 0) continue;
      lines.push(
        `    ${row.fact}: ${row.declared} → ${row.produced}`
          + `${row.delta < 0 ? `  (${row.delta})` : `  (+${row.delta})`}`
          // The count is still here, and so is the reason it is not damage. A fact dropped from the
          // report is a fact nobody can find: this is the line SH575 was read off.
          + `${row.delta < 0 && !row.claimed ? `  by design — ${reasonFor(row.fact)}` : ""}`,
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
  // SH557 — after the losses and in its own words, because it is not one. These paths were never
  // going to be served: a `?` cannot appear in a friendly URL, so there is no address to serve them
  // at, and the run that produced them was doomed before `convert` wrote its first object.
  for (const path of unaddressable.slice(0, limit)) {
    lines.push(`  ${path}  no friendly URL can hold a "?" — never addressable, not lost`);
  }
  if (unaddressable.length > limit) {
    lines.push(`  … ${unaddressable.length - limit} more unaddressable page(s)`);
  }
  if (unaddressable.length) {
    lines.push(
      `  ${unaddressable.length} of ${pages.length + missing.length + unaddressable.length} `
        + "captured page(s) cannot be addressed at all. That is a trait of the SOURCE, not a defect "
        + "in the replica, and `shio clone` says it too — a limit learned here has already cost a "
        + "proposal, an upload and a publish",
    );
  }
  lines.push(
    "  a fact the replica GAINED is not a loss — a layout adds links, and a gate that called "
      + "that damage would fire on every working conversion",
  );
  // SH577 — and the same sentence for the other direction, once, where it is earned. An authorable
  // page is judged on what it should KEEP: its prose, and that the route serves it at all.
  if (byDesign) {
    lines.push(
      `  ${byDesign} shortfall(s) the mode does not claim — an authorable page drops the source's `
        + "chrome on purpose, so what it is held to is the content: the text floor, and whether the "
        + "route serves the page at all. Re-run with --mode fidelity to judge every fact",
    );
  }
  return `${lines.join("\n")}\n`;
}

/** The `why` beside the fact in `FACTS`, which is what makes a by-design line actionable (SH577). */
function reasonFor(name) {
  return FACTS.find((fact) => fact.name === name)?.why ?? "the mode does not claim this fact";
}

/** `, 9 authorable` — the headline's own statement of what standard it applied (SH577). */
function describeModes(modes) {
  const named = Object.entries(modes ?? {}).filter(([, count]) => count > 0);
  // Silent when every page is fidelity: that is the mode this command had before it could tell the
  // difference, and a line saying so on every run of the common case is a line nobody reads.
  if (!named.length || (named.length === 1 && named[0][0] === "fidelity")) return "";
  return `, ${named.map(([name, count]) => `${count} ${name}`).join(" + ")}`;
}
