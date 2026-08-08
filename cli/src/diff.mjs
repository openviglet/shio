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
    // SH586 — the shape two unrelated-looking numbers were hiding. See `linksThatWentHome`.
    wentHome: linksThatWentHome(rows),
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
 * Links that changed side rather than went missing (SH586).
 *
 * <h3>Why a derived fact and not two rows a reader joins</h3>
 *
 * <p>A source that writes some of its own links as absolute URLs on its own origin produces, on a
 * fidelity replica that serves them verbatim, exactly this: `internal links -3` beside
 * `external links +3`. **The total is conserved**, which is what makes it invisible — nothing is
 * lost, so no shortfall is damage, and a gain is never a failure. Measured on a real replica it was
 * twenty-two pages with the identical pair, `verify` exiting 0, and eleven review rounds that did
 * not see it: the two numbers read as unrelated, and the rule that would have caught them — *no
 * href carries the source's base* — is a line in a checklist a person runs.</p>
 *
 * <p>So the join is done here, once, and named in the vocabulary of what happened. It is deliberately
 * narrow: **equal and opposite**, because that is the signature of a link that moved rather than of
 * a page that lost three links and happens to have gained three others. A looser rule would fire on
 * an authorable page whose chrome went one way and whose layout added links the other, which is two
 * true facts and not this one.</p>
 */
function linksThatWentHome(rows) {
  const internal = rows.find((row) => row.fact === "internal links");
  const external = rows.find((row) => row.fact === "external links");
  if (!internal || !external || internal.delta >= 0) return 0;
  return external.delta === -internal.delta ? -internal.delta : 0;
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
    // SH578 — the page's **own** references. What a stylesheet references is the other fact, and it
    // is reported once at the site level: see `themeAssets` below.
    // SH581 — and of those, the ones its **body** references. The head is the layout's on every
    // replica of every mode, so it is reported beside the theme rather than scored: two facts a
    // page cannot be held to, both counted, neither hidden inside a number that is a page's.
    name: "body assets",
    of: bodyAssets,
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
 * The assets a page's own **body** references (SH581).
 *
 * <p>`own` minus `head`, and one function so the declared side and the produced side cannot compute
 * it differently — the property SH554 established for `links` and SH578 for the theme, asked once
 * more of the same fact. `assertComparable` is what guarantees both operands are there.</p>
 */
function bodyAssets(page) {
  return page.assets?.own - page.assets?.head;
}

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
  if (Array.isArray(page?.links)) {
    throw new Error(
      `shio diff: the ${side} side carries links as a raw array, which cannot be compared (SH554).\n`
        + "  fix: classify it first — countLinks(doc.links, pageUrl) — so both sides are\n"
        + "       {internal, external}, which is the shape an inventory row records.\n"
        + "  why: scoring the array reads its length as the internal count and zero as the external\n"
        + "       one, so every page reports a loss it did not suffer.",
    );
  }
  // SH578 — the same refusal for the same reason, one fact along. An unsplit `assets` is a number
  // (a version-3 inventory row) or an array (`scanPage`'s own output), and reading either as `own`
  // would compare a page's markup against a replica's markup **plus the theme inlined into it**.
  if (page?.assets !== undefined && typeof page.assets?.own !== "number") {
    throw new Error(
      `shio diff: the ${side} side carries assets as a `
        + `${Array.isArray(page.assets) ? "raw array" : typeof page.assets}, which cannot be `
        + "compared (SH578).\n"
        + "  fix: split it first — countAssets(doc.assets) — so both sides are {own, style}.\n"
        + "  why: the produced side is a page plus the theme inlined into it, so counting one total\n"
        + "       answers a different question at each end. Measured at +30 on every page of a\n"
        + "       fidelity replica, which is the reading under which a page that lost twenty of its\n"
        + "       own images and gained thirty theme url() reports a GAIN.\n"
        + "  note: a capture written before SH578 is version 3 — re-run `shio clone` to get one\n"
        + "       this can read, rather than have it scored on a shape it does not have.",
    );
  }
  // SH581 — the same refusal on the other axis, and it needs its own because a version-4 row has
  // `own` and no `head`: the check above passes, `own - undefined` is NaN, and every comparison
  // against NaN is false — so a page that lost every image would report nothing at all. Silence is
  // the worst of the three readings this fact has had.
  if (page?.assets !== undefined && typeof page.assets?.head !== "number") {
    throw new Error(
      `shio diff: the ${side} side carries no head asset count, so a page's body cannot be `
        + "separated from the layout's head (SH581).\n"
        + "  fix: re-read it with countAssets(doc.assets), which returns {own, head, style}.\n"
        + "  why: `own` is the whole captured document and a replica page is the body span it\n"
        + "       stores under a layout that owns the head. Measured at -28 on one fidelity page\n"
        + "       whose body was unchanged and whose head held nine <link> elements.\n"
        + "  note: a capture written before SH581 is version 4 — re-run `shio clone`.",
    );
  }
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
    const { rows, lost, byDesign, gained, wentHome } = comparePage(page, now, pageMode);
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
      wentHome,
    });
  }
  return {
    pages,
    missing,
    unaddressable,
    modes: counted,
    // SH590 — the arithmetic over what the loop above already computed. Here rather than in the
    // printer because a caller reading `--json` wants the candidate cause as much as a reader does.
    invariant: invariantDeltas(pages),
    theme: themeAssets(declared, produced),
    // SH581 — the other fact a page cannot be held to, counted the same way and for the same reason.
    head: headAssets(declared, produced),
    lost: pages.reduce((total, page) => total + page.lost, 0),
    byDesign: pages.reduce((total, page) => total + page.byDesign, 0),
    // SH586 — across the replica, because one page's three is a curiosity and twenty-two pages'
    // sixty-six is the defect. Summed rather than counted in pages for the same reason the per-page
    // line prints the number: it is what a reader compares against the source's own link count.
    wentHome: pages.reduce((total, page) => total + (page.wentHome ?? 0), 0),
  };
}

/**
 * How many pages must share a delta before it stops being a coincidence (SH590).
 *
 * <p>Three, because two is one page agreeing with another and the argument here is about
 * <b>invariance</b>: a real page-by-page loss varies with the page, so a number that does not vary
 * is a property of the pipeline. Both sightings this was derived from were far above it — 22 pages
 * of `-3` and 13 pages of `+30` — and the floor is set low deliberately, since a candidate cause
 * that turns out to be a coincidence costs a reader one line, while the alternative cost four
 * rounds.</p>
 */
export const INVARIANT_FLOOR = 3;

/**
 * A delta identical across pages, reported once as a candidate cause (SH590).
 *
 * <h3>The give-away was never the number</h3>
 *
 * <p>`shio diff` on a 25-page replica printed `internal links -3` beside `external links +3` on
 * twenty-two of them. Exactly three, on every page, in both directions — one cause (the source
 * writes three of its own links as absolute URLs and the replica serves them verbatim), read as
 * forty-four ordinary shortfalls for four rounds before anybody fetched a page and looked at the
 * hrefs. The same shape produced SH578: `assets +30` on every page of a fidelity replica, uniform to
 * the unit, which was the inlined theme's own `url()` counted on one side only.</p>
 *
 * <p>Both times what mattered was not the magnitude but the <b>invariance</b>. So the arithmetic is
 * the whole feature: group every non-zero delta by (fact, value), and any group holding
 * {@link INVARIANT_FLOOR} pages or more is one candidate cause with its pages folded behind a count.
 * No new measurement, no browser, no instance — one pass over output the command already computes,
 * which is what makes it the cheapest of the three ways this work has found defects and the one that
 * would have moved a finding four rounds earlier.</p>
 *
 * <p><b>A by-design group is kept and labelled rather than filtered.</b> An authorable replica drops
 * the source's nav on every page, so its shortfall is invariant by construction — and that is an
 * argument for saying it <i>once</i>, not for dropping it: this file's standing rule is that a fact
 * removed from the report is a fact nobody can find (SH575 was read off a line like this one).</p>
 *
 * @param {object[]} pages `compareCapture`'s per-page rows
 * @returns {Array<{fact: string, delta: number, pages: number, of: number, claimed: boolean,
 *   paths: string[]}>} most-shared first; ties broken by fact then delta, so two runs over one
 *   replica print the same bytes (P5)
 */
export function invariantDeltas(pages, { floor = INVARIANT_FLOOR } = {}) {
  const groups = new Map();
  for (const page of pages) {
    for (const row of page.rows ?? []) {
      if (!row.delta) continue;
      const key = `${row.fact} ${row.delta}`;
      let group = groups.get(key);
      if (!group) {
        group = { fact: row.fact, delta: row.delta, claimed: row.claimed !== false, paths: [] };
        groups.set(key, group);
      }
      // One page cannot be its own corroboration: a fact appears once per page in `FACTS`, but
      // guarding it here keeps the count honest if that ever stops being true.
      if (!group.paths.includes(page.path)) group.paths.push(page.path);
    }
  }
  return [...groups.values()]
    .filter((group) => group.paths.length >= floor)
    .map((group) => ({ ...group, pages: group.paths.length, of: pages.length }))
    .sort((a, b) => b.pages - a.pages
      || a.fact.localeCompare(b.fact)
      || a.delta - b.delta);
}

/**
 * What a stylesheet references, for the whole site, once (SH578).
 *
 * <p>A theme is **one artefact**. On a fidelity replica its `url()` calls are inlined into every
 * page's `&lt;style&gt;`, identically, so the per-page number is the same figure repeated: thirteen
 * copies of "59" say nothing thirteen times, and reported per page they scored as thirteen gains
 * that could each hide a real loss of the page's own images.</p>
 *
 * <p>The <b>max</b> rather than a sum or an average, and `uniform` says whether that mattered. A sum
 * would multiply one artefact by the page count; an average would invent a number no page has. The
 * max is the site's theme as at least one page carries it, and when the pages disagree — a second
 * theme, a page the conversion missed — `uniform: false` is the fact worth reading, which is why it
 * is reported rather than smoothed away.</p>
 */
function themeAssets(declared, produced) {
  const styleOf = (page) => (typeof page?.assets?.style === "number" ? page.assets.style : 0);
  const producedRows = [...declared]
    .map((page) => produced.get(page.path))
    .filter(Boolean);
  const seen = new Set(producedRows.map(styleOf));
  return {
    declared: Math.max(0, ...declared.map(styleOf)),
    produced: Math.max(0, ...producedRows.map(styleOf)),
    // One value across every page it could read, which is what "a theme is one artefact" predicts.
    uniform: seen.size <= 1,
    pages: producedRows.length,
  };
}

/**
 * What each side's `&lt;head&gt;` references, for the whole site, once (SH581).
 *
 * <p>Same shape and same argument as `themeAssets` one fact along, and it is deliberately **not**
 * scored. A replica's head is the layout's on every page of every mode, so the source's favicons,
 * its manifest and its stylesheet link have no page to be missing from — but the numbers are still
 * worth a line, because "the source's head carried nine and the replica's carries three" is a fact
 * about the *layout*, and the way it used to be reported was as damage on each page's own count.</p>
 *
 * <p>The <b>max</b> rather than a sum, and `uniform` beside it: a head that differs page to page is
 * the interesting case — an alternate, a canonical, a page-specific preload — and smoothing it into
 * an average would invent a number no page has.</p>
 */
function headAssets(declared, produced) {
  const headOf = (page) => (typeof page?.assets?.head === "number" ? page.assets.head : 0);
  const producedRows = [...declared].map((page) => produced.get(page.path)).filter(Boolean);
  return {
    declared: Math.max(0, ...declared.map(headOf)),
    produced: Math.max(0, ...producedRows.map(headOf)),
    uniform: new Set(producedRows.map(headOf)).size <= 1,
    pages: producedRows.length,
  };
}

/**
 * `--against proposal` — did the intent become the artefact (SH522)?
 *
 * <h2>The link of the chain this walks</h2>
 *
 * <p>The capture declares the source and `compareCapture` judges the served page against it. The
 * proposal declares the <em>intent</em>: these blocks, accepted, become sections carrying these
 * fields, and this page composes those. Nothing had ever compared that with what the instance
 * actually holds — so "the conversion said it would make eleven sections" and "the site has
 * eleven sections" were two facts a person put side by side by hand.</p>
 *
 * <h2>It reads the instance, not the wire</h2>
 *
 * <p>Deliberately different from the capture comparison, which fetches the route because the
 * question there is what a *reader* gets. The question here is whether the write did what the plan
 * said, and the write's product is content — so both sides are documents, and the comparison is a
 * set difference rather than a scan. A page that composes the right sections and serves them badly
 * is `--against capture`'s finding, and keeping the two apart is what lets a reader tell "the
 * conversion did not do what it promised" from "the conversion did, and the layout is wrong".</p>
 *
 * @param plan     the reviewed decisions: `{ accepted: [{ block, type }] }`
 * @param proposal the candidates and each page's composition, from `conversion-proposal.json`
 * @param held     address → the post the instance holds, as `{ data }`; absent means it has none
 */
export function compareProposal({ plan, proposal, held, sectionsFolder, pagesFolder }) {
  const accepted = new Set((plan?.accepted ?? []).map((entry) => entry?.block).filter(Boolean));
  const byId = new Map((proposal?.candidates ?? []).map((row) => [row.id, row]));

  const sections = [];
  for (const id of [...accepted].sort()) {
    const url = `${sectionsFolder}/${id}`;
    const candidate = byId.get(id);
    const post = held.get(url) ?? null;
    if (!candidate) {
      // Accepted in the plan and absent from the proposal: a hand-edited plan, which the file
      // invites. Reported rather than skipped — it is the one case where the two declarations
      // disagree with each other before the artefact is even involved.
      sections.push({ url, status: "undeclared", missing: [], extra: [] });
      continue;
    }
    if (!post) {
      sections.push({ url, status: "absent", missing: fieldsOf(candidate), extra: [] });
      continue;
    }
    const promised = new Set(fieldsOf(candidate));
    const actual = new Set(Object.keys(post.data ?? {}));
    const missing = [...promised].filter((field) => !actual.has(field)).sort();
    // A field the artefact gained is never a failure, for `compareCapture`'s reason: the write adds
    // `title` and the post-type may declare more. Reported, never counted against the run.
    const extra = [...actual].filter((field) => !promised.has(field)).sort();
    sections.push({ url, status: missing.length ? "incomplete" : "ok", missing, extra });
  }

  const pages = [];
  for (const page of (proposal?.pages ?? []).toSorted((a, b) => (a.path < b.path ? -1 : 1))) {
    const promised = [...new Set((page.blocks ?? [])
      .filter((id) => accepted.has(id))
      .map((id) => `${sectionsFolder}/${id}`))];
    if (!promised.length) continue;
    const url = `${pagesFolder}${page.path}`.replace(/\/{2,}/g, "/");
    const post = held.get(url) ?? held.get(page.path) ?? null;
    if (!post) {
      pages.push({ path: page.path, status: "absent", missing: promised, extra: [] });
      continue;
    }
    const actual = new Set(listOf(post.data?.sections));
    const missing = promised.filter((each) => !actual.has(each));
    const extra = [...actual].filter((each) => !promised.includes(each)).sort();
    pages.push({
      path: page.path,
      status: missing.length ? "incomplete" : "ok",
      missing,
      extra,
    });
  }

  const broken = sections.filter((row) => row.status !== "ok").length
    + pages.filter((row) => row.status !== "ok").length;
  return { sections, pages, broken };
}

/** A candidate's promised field names, whichever shape the proposal recorded them in. */
function fieldsOf(candidate) {
  const fields = candidate?.fields;
  if (Array.isArray(fields)) {
    return fields.map((f) => (typeof f === "string" ? f : f?.name)).filter(Boolean).sort();
  }
  return fields && typeof fields === "object" ? Object.keys(fields).sort() : [];
}

/** `sections` is a list on the wire and may arrive as one string; both are one page's composition. */
function listOf(value) {
  if (Array.isArray(value)) return value.filter((each) => typeof each === "string");
  return typeof value === "string" && value ? [value] : [];
}

/** The proposal comparison as a reader sees it — the answer first, then only what went wrong. */
export function describeProposalDiff(comparison, options = {}) {
  const { sections, pages, broken } = comparison;
  const limit = Number(options.limit) > 0 ? Number(options.limit) : 20;
  const lines = [];

  lines.push(broken === 0
    ? `shio diff: the artefact matches the proposal — ${sections.length} section(s) and `
      + `${pages.length} composed page(s), all present with the fields the plan accepted.`
    : `shio diff: ${broken} of ${sections.length + pages.length} declared item(s) are not what the `
      + "proposal said they would be.");
  lines.push("");

  const wrong = [
    ...sections.filter((row) => row.status !== "ok").map((row) => ({ ...row, what: row.url })),
    ...pages.filter((row) => row.status !== "ok").map((row) => ({ ...row, what: `page ${row.path}` })),
  ];
  for (const row of wrong.slice(0, limit)) {
    lines.push(`  ${row.status.toUpperCase().padEnd(11)} ${row.what}`);
    if (row.missing.length) lines.push(`    missing: ${row.missing.join(", ")}`);
  }
  if (wrong.length > limit) lines.push(`  … ${wrong.length - limit} more`);
  if (broken === 0) {
    // The gained half, said once and never as a failure: a post-type declares fields the proposal
    // never promised, and a reader comparing counts by hand would otherwise ask about the
    // difference every time.
    const gained = sections.reduce((sum, row) => sum + row.extra.length, 0);
    if (gained) lines.push(`  (${gained} field(s) the artefact carries beyond the proposal — the `
      + "post-type's own, never a failure)");
  }
  return `${lines.join("\n").trimEnd()}\n`;
}

/**
 * What a reader sees, and it leads with the answer rather than with the table.
 *
 * <p>The question is "is this what it was supposed to be", so the first line answers it. A page that
 * matched prints one line, because a report whose clean rows are as long as its broken ones is one
 * nobody reads to the end (SH138's rule, applied to a comparison instead of to a cut).</p>
 */
export function describeDiff(comparison, options = {}) {
  const { pages, missing, unaddressable = [], lost, byDesign = 0, modes, theme, head,
    wentHome = 0, invariant = [] } = comparison;
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
      + `${byDesign ? `, ${byDesign} the mode does not claim` : ""}`
      // SH586 — on the headline for the same reason `byDesign` is: it is the number that reads as
      // nothing. `nothing lost` is true of a replica whose every page links back to the original,
      // and a reader who stops at the headline should not be told only the true half.
      + `${wentHome ? `, ${wentHome} link(s) now point at the source` : ""}`,
  ];
  // SH590 — before the per-page table, because it is a candidate CAUSE and the table is the
  // evidence. A reader who meets `internal links -3` twenty-two times reads twenty-two findings;
  // the same reader meeting it once, with the count, reads one — which is what it is. The per-page
  // rows are still printed underneath, because a fact removed from the report is a fact nobody can
  // find, and because the cause is a candidate rather than a verdict.
  if (invariant.length) {
    lines.push(
      `  ${invariant.length} delta(s) identical across pages — a real loss VARIES with the page, so `
        + "a constant is a property of the pipeline. One cause each, not one finding per page:",
    );
    for (const group of invariant) {
      const shown = group.paths.slice(0, 3).join(", ");
      lines.push(
        `    ${group.fact}: ${group.delta > 0 ? `+${group.delta}` : group.delta}`
          + `  on ${group.pages} of ${group.of} page(s)`
          + `${group.claimed ? "" : "  (by design — said once rather than per page)"}`
          + `  e.g. ${shown}${group.paths.length > 3 ? ` … +${group.paths.length - 3}` : ""}`,
      );
    }
  }
  for (const page of pages.slice(0, limit)) {
    if (!page.lost && !page.byDesign && !page.gained && !page.wentHome) {
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
    // SH586 — the join, in the words of what happened. The two rows above are still printed, because
    // a fact removed from the report is a fact nobody can find; this says what they mean together.
    if (page.wentHome) {
      lines.push(
        `    ${page.wentHome} link(s) now point at the SOURCE — internal became external and the `
          + "total is conserved, so nothing reads as lost. The source writes them as absolute URLs "
          + "on its own origin and this page serves them verbatim: clicking one leaves the replica",
      );
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
  // SH578 — the theme, once, in its own words and outside the scoring. Printed whenever either side
  // has one, because "the page's own references shrank while its theme's grew" is only readable if
  // both numbers are on the report; the fact that used to hide it was their sum.
  if (theme && (theme.declared || theme.produced)) {
    lines.push(
      `  theme: ${theme.declared} → ${theme.produced} url() inside a stylesheet, across `
        + `${theme.pages} page(s) — one artefact, counted once and never scored. A page's OWN `
        + "references are the `assets` fact above"
        + (theme.uniform
          ? ""
          : "; and the pages disagree about this number, which a single theme cannot explain"),
    );
  }
  // SH581 — and the head, on the same terms. The line exists because the alternative is not silence:
  // it is these references counted into every page's own total, where a shortfall of nine reads as
  // nine images a page lost.
  if (head && (head.declared || head.produced)) {
    lines.push(
      `  head: ${head.declared} → ${head.produced} reference(s) in the document head, across `
        + `${head.pages} page(s) — the layout's on every replica, counted once and never scored. `
        + "A page's BODY references are the `body assets` fact above"
        + (head.uniform ? "" : "; and the pages disagree, which one layout cannot explain"),
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
