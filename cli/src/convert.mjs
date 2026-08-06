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
 * The fidelity layout — where it lands and what it is called (SH449).
 *
 * <p>One per site rather than one per page, and that is forced rather than chosen: the renderer
 * resolves a layout through `site.postTypeLayout`, which is keyed by **post-type**, so every `Page`
 * of a site renders through the same template. What differs per page therefore has to live on the
 * page, which is what `Page.content` is for.
 */
export const FIDELITY_LAYOUT_URL = `${THEME_FOLDER}/layout`;
export const fidelityLayoutTitle = (site) => `${site} (fidelity)`;

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
export function buildConversion({ proposal, plan = {}, site, createSite = false, markup = new Map() }) {
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

  const pages = composePages(proposal, new Set(sections.map((post) => post.url)), markup);
  const verbatim = pages.filter((page) => typeof page.data.content === "string").length;

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
  // SH449 wrote the layout only for a verbatim page, and SH464 is what that cost: the authorable
  // path wrote sections, a Page and a Theme and NO PageLayout, so `/preview/<site>/` answered
  // **400 — binds no layout for post-type 'Page'**. Block Q is named "an existing site becomes an
  // authorable Shio site" and neither of its two modes produced one. Every page needs a layout,
  // because the renderer resolves one per post-type; the layout below serves both modes.
  if (pages.length) {
    posts.push(pageLayout(markup, site, extras.includes("Theme")));
    extras.push("PageLayout");
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
      // SH449 — the edge that turns a layout into a rendered page. Written only when there is a
      // layout to bind, so a document that does not need it is the same bytes it was before (P5).
      ...(pages.length ? { postTypeLayout: { [PAGE_TYPE]: fidelityLayoutTitle(site) } } : {}),
      folders: folders.map((path) => ({ path })),
      posts,
    },
    stale: [],
    sections,
    pages,
    verbatim,
    extras,
  };
}

/**
 * The layout's `<body>`, verbatim and with **no interpolation** (SH464).
 *
 * <p>Its own constant for one reason: it is the half a **Java** test has to be able to read. The
 * seam this task closes is between a template this file writes and a renderer in another language
 * that has to annotate it, and every earlier failure of that kind was a copy of one side asserted
 * against itself — SH245's standing lesson. `ShConvertedLayoutRenderTest` extracts these bytes from
 * this file and renders them through the real engine, so "the layout works" is a measurement rather
 * than a claim, and there is no second copy to drift.</p>
 *
 * <p>Nothing here interpolates, which is what makes the extraction a slice between two backticks
 * rather than a JavaScript parser.</p>
 */
export const PAGE_BODY_HTML = `{{#if content}}{{{content}}}{{/if}}
{{#unless content}}<h1 class="shio-page-title" sh-field="title">{{title}}</h1>{{/unless}}
{{#getRelation sections}}
<section class="shio-section shio-section-{{type}}" sh-post="{{id}}" data-shio-section="{{type}}" data-shio-align="{{align}}" data-shio-variant="{{variant}}" data-shio-columns="{{columns}}">
{{#if heading}}<h2 class="shio-heading" sh-field="heading">{{heading}}</h2>{{/if}}
{{#if subheading}}<p class="shio-subheading" sh-field="subheading">{{subheading}}</p>{{/if}}
{{#if intro}}<p class="shio-intro" sh-field="intro">{{intro}}</p>{{/if}}
{{#if body}}<div class="shio-body" sh-field="body">{{{body}}}</div>{{/if}}
{{#if image}}<p class="shio-image"><img sh-field="image" src="{{file_url image}}" alt="{{heading}}"></p>{{/if}}
{{#if ctaLabel}}<p class="shio-cta"><a class="shio-cta-link" sh-field="ctaLabel" href="{{ctaHref}}">{{ctaLabel}}</a></p>{{/if}}
</section>
{{/getRelation}}`;

/**
 * The shell every page of a converted site renders through (SH449, SH464).
 *
 * <h3>One layout, both modes — because the renderer allows exactly one</h3>
 *
 * <p>A layout is resolved through `site.postTypeLayout`, which is keyed by **post-type**, so every
 * `Page` of a site renders through one template and "a layout per mode" is not a shape that exists.
 * SH449 wrote this one only when a page had verbatim markup; SH464 measured what that left: the
 * authorable path wrote sections, a Page and a Theme and no layout at all, so `/preview/<site>/`
 * answered **400 — binds no layout for post-type 'Page'**, and the console — which frames the site's
 * front end or, failing that, that same preview — had nothing to frame either. A mixed conversion
 * was worse than either: the fidelity layout rendered `{{{content}}}` and a section page has none,
 * so it served a blank body and reported success.</p>
 *
 * <h3>What is verbatim and what cannot be</h3>
 *
 * <p>The source's `&lt;body&gt;` — where the classes, the structure and everything a reader sees
 * live — is carried onto the page post byte for byte. The `&lt;head&gt;` is **rebuilt**, and that is
 * not a shortcut: the one thing a replica must change is where its CSS comes from. The source links
 * a stylesheet at the source's origin; the replica inlines the captured `Theme` through
 * `{{theme.css}}`, which is what makes SH424's token edits reach the page. A head copied verbatim
 * would keep pointing at somebody else's server.</p>
 *
 * <h3>Why `{{{content}}}` and not one Region per block</h3>
 *
 * <p>SH191's sidecar shape needs each block's own bytes, and this pipeline cannot produce them:
 * `buildTree` records no source offsets, so the only route back to markup is re-serializing a tree
 * built from `stripNonMarkup`'s output — which has already dropped the comments and the raw-text
 * bodies. "Verbatim" and "re-serialized" are different claims, and writing the second while the
 * documentation promised the first is the defect SH449 exists to fix, not a shape to reproduce one
 * level down. A curator can still lift a block into a Region by hand; nothing here prevents it.</p>
 *
 * <h3>And the authorable half, which is the one that makes a replica editable</h3>
 *
 * <p>`{{#getRelation sections}}` walks the `Page.sections` list in order — SH247's helper, which
 * takes the field's *value* rather than its name and so needs no access to the post-type. Each row
 * carries the section's own fields plus its `id` and `type`, which is what lets one template serve
 * the whole SH95 vocabulary: `sh-post="{{id}}"` binds the block to the **section** post rather than
 * to the page, and `sh-field` on each element is what the renderer turns into `data-shio-prop` for
 * SH185's editor bridge. That pairing is the whole of SH464 — fidelity mode already proved the
 * renderer annotates whatever the template declares, and verbatim markup declares nothing, so it
 * rendered with **zero** `data-shio-prop` and the editor loaded over a page with nothing to offer.</p>
 *
 * <p>The fields are rendered as a **union with a guard each**, not branched on `{{type}}`: the
 * vocabulary is closed (seven section types, `heading` in every one of them), the engine registers a
 * closed helper set with no equality helper, and a union means a new section type that reuses these
 * names renders without a template change. A field a section does not carry renders nothing.
 * `variant`, `align` and `columns` are presentation choices rather than prose, so they ride as
 * `data-` attributes for the theme's CSS to read instead of being printed as text.</p>
 */
function pageLayout(markup, site, hasTheme) {
  const lang = langOf(markup);
  return {
    url: FIDELITY_LAYOUT_URL,
    type: "PageLayout",
    folder: THEME_FOLDER,
    data: {
      TITLE: fidelityLayoutTitle(site),
      // A THEME naming a post the site does not have is a marked failure at render time
      // (`data-shio-theme-missing`), so it is named only when the theme post is in this document.
      ...(hasTheme ? { THEME: `${site} (captured)` } : {}),
      HTML: `<!doctype html>
<html${lang ? ` lang="${lang}"` : ""}>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{post.title}}</title>
<style>{{theme.css}}</style>
</head>
<body>
${PAGE_BODY_HTML}
</body>
</html>
`,
    },
  };
}

/**
 * The source's own `lang`, when every captured page agreed on one.
 *
 * <p>Omitted rather than guessed when they disagree or none declared it. A wrong `lang` is a
 * accessibility and a translation defect that reads as content, and "unstated" is what the source
 * said.</p>
 */
function langOf(markup) {
  const langs = new Set();
  for (const html of markup.values()) {
    const found = /<html\b[^>]*\blang\s*=\s*"([^"]{1,35})"/i.exec(String(html));
    langs.add(found ? found[1] : "");
  }
  return langs.size === 1 ? [...langs][0] : "";
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
 * <p><b>Only what was accepted goes into `sections`</b> — a candidate left in `fidelity` mode is not
 * a section and cannot be in the array.</p>
 *
 * <h3>And the page is written either way now (SH449)</h3>
 *
 * <p>SH438 skipped a page whose blocks were all left verbatim, on the reasoning that an empty `Page`
 * reports success for a page that serves nothing. The reasoning was right and the premise was
 * wrong: such a page is not empty, it is **fidelity**, which `propose.mjs`' own header documents as
 * the mode where the markup is kept as it stands. Nothing wrote it, so the mode was a string on a
 * report — accept nothing and `convert` emitted the Theme alone, leaving the site 404-ing at every
 * captured path and the coverage gate correctly reporting `source-page-missing` for all of them.
 *
 * <p>So a page with no accepted section gets its captured `&lt;body&gt;` verbatim in `content`, and
 * `source-page-missing` stops being right about it because the page now serves the source's markup.
 * A page with neither — no accepted section and no markup on disk — is still skipped, because that
 * is the one case where there is genuinely nothing to serve.</p>
 *
 * @param {Map<string, string>} markup path → the page's captured HTML, the same cache the proposal
 *   was computed from. Empty for a caller that has none, which yields exactly SH438's behaviour.
 */
function composePages(proposal, sectionUrls, markup = new Map()) {
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
    const body = sections.length ? null : bodyOf(markup.get(page.path));
    if (!sections.length && body === null) continue;
    pages.push({
      url: page.path,
      type: PAGE_TYPE,
      folder: PAGES_FOLDER,
      // `title` is required on `Page`, and the capture recorded one. The path is the fallback rather
      // than the site name: it is what makes two untitled pages two rows a human can tell apart.
      data: {
        title: titleOf(page),
        ...(sections.length ? { sections } : { content: body }),
      },
    });
  }
  return pages.sort((a, b) => (a.url < b.url ? -1 : 1));
}

/**
 * A captured page's `&lt;body&gt;`, inner HTML only and otherwise untouched.
 *
 * <p>Returns `null` — not `""` — when there is no markup or no body, because the caller's question
 * is "is there something to serve" and an empty string answers it wrongly: a `Page` written with
 * `content: ""` renders a blank document and reports success, which is the shape SH438 was right to
 * refuse. A document with no `&lt;body&gt;` tag at all is taken whole: a fragment is what a
 * hand-written page often is, and refusing it would drop the page over a tag no browser needs.</p>
 */
function bodyOf(html) {
  if (typeof html !== "string" || !html.trim()) return null;
  const open = /<body\b[^>]*>/i.exec(html);
  if (!open) return html.trim();
  const start = open.index + open[0].length;
  const close = html.toLowerCase().lastIndexOf("</body>");
  const inner = (close > start ? html.slice(start, close) : html.slice(start)).trim();
  return inner || null;
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
export function describeConversion({ document, sections, pages = [], verbatim = 0, extras,
                                     requires = [] }, options = {}) {
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
  // SH449 — said out loud, because the two modes produce very different content out of one command
  // and the count is the reviewer's evidence that the blocks they did not accept were kept.
  if (verbatim) {
    lines.push(
      `  ${verbatim} of ${pages.length} page(s) in fidelity mode — the source's own markup, `
        + `rendered through ${document.postTypeLayout?.[PAGE_TYPE]}`,
    );
  }
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
