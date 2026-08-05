/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH417 — the verb the proposal was built to make safe: an accepted plan, as content.
 *
 * ## Why this is a translation and not an engine
 *
 * SH379 deliberately stopped at the proposal — `--accept` writes a review decision into
 * `shio/conversion-plan.json` and applies nothing, because making review and mutation one command is
 * the surprise P7 forbids. That was right, and it left the pipeline one verb short: the plan was a
 * file whose only reader was the report that produced it.
 *
 * Everything the missing verb needs already exists. For each accepted candidate the proposal holds
 * the section type and the field values; SH380 holds the theme as DTCG tokens plus CSS whose `url()`
 * calls already point at tree paths; SH389 holds the scripts. Writing them is `shio apply`'s
 * desired-state document — idempotent, `--dry-run`-able, and already the one write path (P10). So
 * this module is a **pure function from two artefacts to one document**, and the command around it
 * does the IO.
 *
 * ## What SH417 left out, and why it was the whole deliverable (SH438)
 *
 * Sections, a theme and scripts — and nothing that *ordered* them. A section no page composes is not
 * deliverable by anything, so the first real run created three posts, answered 404 at
 * `/preview/<site>/`, and had the replication gate report `source-page-missing` for `/`. So this also
 * composes one `Page` per captured page from `proposal.pages`, which records each page's blocks in
 * source order. Everything it needs was already held; see `composePages`.
 *
 * ## The two rules the translation owes
 *
 * **Nothing is written for a candidate the current proposal does not hold.** An acceptance from a run
 * taken before the source changed names a block that no longer exists — exactly the staleness a
 * structural id makes detectable — and an apply must refuse rather than guess. The refusal names the
 * ids and the command that re-reads them, because the fix is a re-read and an accept, not an edit.
 *
 * **The write lands in DRAFT.** The blueprints publish because a starter is meant to be looked at;
 * a conversion of somebody else's site is not, and P7's draft-default is the whole reason a human is
 * in this loop. `publish` is therefore absent from every row rather than false — absent means "leave
 * the publish state alone", so re-running this cannot unpublish a page a curator has since published.
 */

/** Where a converted section lands. One folder, because a section is addressed by its own url. */
export const SECTIONS_FOLDER = "/sections";

/** Where the captured theme and the captured scripts land — one site-wide post each. */
export const THEME_FOLDER = "/theme";

/**
 * Where a composed page lands, and the post-type that composes it (SH438).
 *
 * <p>The site root, and `Page` — the shape the `marketing-site` blueprint already writes down:
 * `url: "/"`, `folder: "/"`, `data.sections` as an ordered list of section friendly URLs. This is a
 * translation into that shape rather than a new one, which is why the names are its names.</p>
 */
export const PAGES_FOLDER = "/";
export const PAGE_TYPE = "Page";

/**
 * The document that makes an accepted plan true.
 *
 * @param {object} input
 * @param {object} input.proposal the proposal computed from the capture **now**
 * @param {{accepted?: Array<{block: string, type: string}>}} input.plan the reviewed decisions
 * @param {string} input.site the site to write into
 * @param {boolean} [input.createSite] make the site as the run's first step (SH439). The key is
 *   written into the document only when asked, so an `--out` file is the same bytes it was
 *   before for every caller that did not ask (P5) — and carries the intent for the one that did,
 *   which is what makes `shio apply conversion.json` mean what `shio convert` meant
 * @returns {{document: object|null, stale: string[], sections: object[], extras: string[]}}
 */
export function buildConversion({ proposal, plan = {}, site, createSite = false }) {
  const accepted = (plan.accepted ?? []).map((entry) => ({ ...entry }));
  const byId = new Map((proposal?.candidates ?? []).map((row) => [row.id, row]));
  const stale = accepted.filter((entry) => !byId.has(entry.block)).map((entry) => entry.block).sort();
  if (stale.length) {
    return { document: null, stale, sections: [], pages: [], extras: [] };
  }

  const sections = accepted
    .map((entry) => byId.get(entry.block))
    // An accepted candidate always has a type — `propose --accept` refuses a typeless one, whose
    // answer is a post-type of its own — but a plan is a file a human may have edited.
    .filter((row) => row?.type)
    .map((row) => ({
      url: `${SECTIONS_FOLDER}/${row.id}`,
      type: row.type,
      folder: SECTIONS_FOLDER,
      data: { ...row.fields },
    }))
    .sort((a, b) => (a.url < b.url ? -1 : 1));

  const pages = composePages(proposal, new Set(sections.map((post) => post.url)));

  const extras = [];
  const posts = [...sections, ...pages];
  if (proposal?.theme?.rows?.length) {
    posts.push(themePost(proposal.theme, site));
    extras.push("Theme");
  }
  if (proposal?.scripts?.length) {
    posts.push(scriptsPost(proposal.scripts, site));
    extras.push("SiteScripts");
  }

  const folders = [
    SECTIONS_FOLDER,
    ...(pages.length ? [PAGES_FOLDER] : []),
    ...(extras.length ? [THEME_FOLDER] : []),
  ];
  return {
    document: {
      site,
      // SH439 — present only when asked. Creating a site nobody named is the surprise P7 forbids,
      // and an absent key is what every document written before this said.
      ...(createSite ? { createSite: true } : {}),
      folders: folders.map((path) => ({ path })),
      posts,
    },
    stale: [],
    sections,
    pages,
    extras,
  };
}

/**
 * One `Page` per captured page, composing the sections that page was made of (SH438).
 *
 * <h3>Why this is the difference between three posts and a replica</h3>
 *
 * <p>SH417 wrote the sections, the theme and the scripts, and nothing that ordered them. Measured on
 * a real run: three posts created, `/preview/viglet2/` answered **404**, and `verify --checks
 * replication` reported `source-page-missing` for `/` — the gate's own statement that the
 * replication was not done. A section no page composes is not deliverable by anything, so the
 * replica of a one-page site ended as two orphans and the step that turned them into a site was the
 * one step still done by hand. That is the manual work this phase existed to remove, moved up a
 * level from the block to the page.</p>
 *
 * <h3>The three rules it inherits rather than decides</h3>
 *
 * <p><b>The source's URL.</b> SH385's rule — a replica keeps the original's URL space — so the page's
 * friendly URL is the captured path, which for a home page is `/`: the post that `post:&lt;site&gt;/`
 * names (SH433) and the one the renderer looks for when `/preview/&lt;site&gt;/` is asked for.</p>
 *
 * <p><b>DRAFT.</b> `publish` is absent here as it is on every other row, so re-running cannot
 * unpublish a page a curator has since published, and nothing appears live because a converter ran
 * (P7).</p>
 *
 * <p><b>Only what was accepted, and only where there is something to compose.</b> A candidate left
 * in `fidelity` mode stays verbatim markup, so it is not a section and cannot be in the array. A
 * captured page whose blocks were *all* left verbatim therefore composes nothing, and gets no
 * `Page`: writing an empty one would report success for a page that serves nothing, which is this
 * same defect one level down — and `source-page-missing` is right to keep asking about it.</p>
 */
function composePages(proposal, sectionUrls) {
  const pages = [];
  for (const page of proposal?.pages ?? []) {
    const seen = new Set();
    const sections = [];
    for (const id of page.blocks ?? []) {
      const url = `${SECTIONS_FOLDER}/${id}`;
      // First occurrence wins. A signature repeated on one page is still one section post with one
      // set of fields, so listing it twice would ask the page to render the same content twice —
      // a decision for the reviewer, not a thing to infer from a structural collision.
      if (!sectionUrls.has(url) || seen.has(url)) continue;
      seen.add(url);
      sections.push(url);
    }
    if (!sections.length) continue;
    pages.push({
      url: page.path,
      type: PAGE_TYPE,
      folder: PAGES_FOLDER,
      // `title` is required on `Page`, and the capture recorded one. The path is the fallback rather
      // than the site name: it is what makes two untitled pages two rows a human can tell apart.
      data: { title: titleOf(page), sections },
    });
  }
  return pages.sort((a, b) => (a.url < b.url ? -1 : 1));
}

function titleOf(page) {
  const title = typeof page.title === "string" ? page.title.trim() : "";
  return title || page.path;
}

/**
 * The captured theme as one `Theme` post (SH195's fields, SH380's halves).
 *
 * <p>`TOKENS` is the DTCG tree serialized, which is what `ShThemeTokens.parse` reads, and `CSS` is
 * the kept stylesheet — the one SH424 took the lifted declarations out of, so editing a token now
 * reaches the page. Field names are the render vocabulary's own (uppercase); a section's are the
 * blueprint's (lowercase). Two conventions, and this is where they meet.
 */
function themePost(theme, site) {
  return {
    url: `${THEME_FOLDER}/captured`,
    type: "Theme",
    folder: THEME_FOLDER,
    data: {
      TITLE: `${site} (captured)`,
      CSS: theme.css ?? "",
      TOKENS: `${JSON.stringify(theme.tokens ?? {}, null, 2)}\n`,
    },
  };
}

/** The captured third-party scripts as one `SiteScripts` post (SH389's ordered list). */
function scriptsPost(scripts, site) {
  return {
    url: `${THEME_FOLDER}/scripts`,
    type: "SiteScripts",
    folder: THEME_FOLDER,
    data: {
      TITLE: `${site} (captured scripts)`,
      SCRIPTS: `${JSON.stringify(scripts, null, 2)}\n`,
    },
  };
}

/**
 * What a caller reads before deciding to write.
 *
 * <p>Rows, not a count: the point of the whole pipeline is that somebody sees what is about to become
 * content. The preconditions come last because they are the sentence that decides whether the write
 * can work at all (SH436).
 */
export function describeConversion({ document, sections, pages = [], extras, requires = [] },
                                   options = {}) {
  const lines = [];
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 1) : 20;
  lines.push(
    `shio convert: ${sections.length} section(s)`
      // SH438 — counted separately from the extras, because it is the count that says whether the
      // replica serves anything: sections with no page composing them are orphans, and the run that
      // wrote only those reported success.
      + `${pages.length ? ` in ${pages.length} page(s)` : ""}`
      + `${extras.length ? ` + ${extras.join(" + ")}` : ""} into site ${document.site}`,
  );
  for (const post of document.posts.slice(0, limit)) {
    const title = Object.values(post.data).find((value) => typeof value === "string" && value.trim());
    lines.push(`  ${post.url}  ${post.type}${title ? `  ${preview(title)}` : ""}`);
  }
  if (document.posts.length > limit) {
    lines.push(`  … ${document.posts.length - limit} more in the document`);
  }
  lines.push("  every post lands in DRAFT — publishing is a separate, explicit call (P7)");
  for (const row of requires) {
    lines.push(`  requires: ${row.postType}.${row.field} — ${row.why}`);
  }
  return `${lines.join("\n")}\n`;
}

function preview(value) {
  const text = String(value).replace(/\s+/g, " ").trim();
  return text.length > 60 ? `${text.slice(0, 57)}…` : text;
}

/**
 * The refusal for a plan the current proposal cannot satisfy (SH417's first rule).
 *
 * <p>Named ids and the two commands that resolve it, because the fix is a re-read and a fresh
 * acceptance: an id is a hash of the block's structure, so one that no longer exists means the source
 * changed under the review, and writing anything for it would be applying a decision nobody made
 * about the markup that is there now.
 */
export function staleMessage(stale, out) {
  return (
    `shio convert: ${stale.length} accepted candidate(s) are not in the current proposal: `
      + `${stale.join(" ")}\n`
      + "An id is a hash of the block's structure, so one that is gone means the source changed after\n"
      + "the acceptance was recorded — applying it would write a decision nobody made about this\n"
      + "markup. Re-read the proposal and accept again:\n"
      + `  shio propose${out ? ` --out ${out}` : ""}\n`
      + "  shio propose --accept <id>"
  );
}
