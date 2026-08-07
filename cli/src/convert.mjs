/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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

import { SECTION_FILE_FIELDS } from "./propose.mjs";
import {
  VOCABULARY_THEME_CSS, VOCABULARY_THEME_SLUG, VOCABULARY_THEME_TOKENS, vocabularyThemeTitle,
} from "./vocabulary-theme.mjs";

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
/** Where the captured `Theme` post lives — named once, because the layout now references it. */
export const CAPTURED_THEME_URL = `${THEME_FOLDER}/captured`;
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

  // SH552 — the finding the previous command made, acted on. `propose` identifies field values that
  // appear on candidates sharing no page — chrome one level down — and reported them in prose; this
  // pass copied `row.fields` verbatim, so `intro "We respect your privacy"` was written as the
  // accepted section's intro and the replica's home page introduced itself with a cookie banner.
  const chrome = adoptedIndex(proposal);
  const dropped = [];
  const sections = accepted
    .map((entry) => byId.get(entry.block))
    // An accepted candidate always has a type — `propose --accept` refuses a typeless one, whose
    // answer is a post-type of its own — but a plan is a file a human may have edited.
    .filter((row) => row?.type)
    .map((row) => ({
      url: `${SECTIONS_FOLDER}/${row.id}`,
      type: row.type,
      folder: SECTIONS_FOLDER,
      data: withoutChrome(row, chrome, dropped),
    }))
    .sort((a, b) => (a.url < b.url ? -1 : 1));

  const pages = composePages(proposal, new Set(sections.map((post) => post.url)), markup);
  const held = pages.filter((page) => typeof page.data.content === "string");
  // Two counts, because they are two different pages (SH520). `verbatim` is a page nobody accepted
  // anything on — fidelity, whole. `mixed` is a page that kept some blocks and authored others, and
  // it is the shape that used to be silently the first one minus everything it did not accept.
  const verbatim = held.filter((page) => !page.data.sections).length;
  const mixed = held.length - verbatim;

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
  // SH510 — which of the two themes the one layout names, decided before either is written because
  // the answer decides both. SH520 added the third page shape: a **mixed** page carries the source's
  // own markup and the source's own class names, so the captured stylesheet is the one that has
  // anything to say about it, and naming the vocabulary's theme would leave the half a curator did
  // not accept rendered as unstyled text.
  // SH549 — `authorable` used to be `verbatim === 0 && mixed === 0`, a **site-wide** switch, and one
  // block the vocabulary could not name flipped it for every page: eleven authorable pages named the
  // captured theme and shipped a megabyte defining none of the `shio-*` classes they draw, with
  // `page-unstyled` on ten of them. A real site always has such a block. So the layout names the
  // vocabulary theme whenever *any* page composes sections, and the pages that kept verbatim markup
  // get the captured CSS through `capturedFor` below — per page, which is what the render model
  // already allowed and this code was not asking for.
  const authorable = sections.length > 0;
  const theme = pages.length ? layoutTheme({ site, captured: extras.includes("Theme"), authorable }) : null;
  // SH509 — the theme SH507's decision promised and the vocabulary never had. Written only when the
  // layout **names** it: it styles SH508's roles, and a page that is verbatim in whole or in part has
  // none of them, so anywhere else it would be a post nobody renders. `Theme (Shio)` rather than
  // `Theme` in `extras`, because that string is what decides whether the *captured* theme was written.
  if (theme === vocabularyThemeTitle(site)) {
    posts.push(vocabularyThemePost(site));
    extras.push("Theme (Shio)");
  }
  // SH449 wrote the layout only for a verbatim page, and SH464 is what that cost: the authorable
  // path wrote sections, a Page and a Theme and NO PageLayout, so `/preview/<site>/` answered
  // **400 — binds no layout for post-type 'Page'**. Block Q is named "an existing site becomes an
  // authorable Shio site" and neither of its two modes produced one. Every page needs a layout,
  // because the renderer resolves one per post-type; the layout below serves all three modes.
  if (pages.length) {
    // SH549 — the captured stylesheet, reaching only the pages that carry verbatim markup. Named
    // only when the layout named the *vocabulary* theme: a pure-fidelity conversion already names the
    // captured one, and emitting it twice would put a megabyte on the page twice.
    const capturedFor = theme === vocabularyThemeTitle(site)
      && extras.includes("Theme") && held.length > 0;
    posts.push(pageLayout(markup, site, theme, capturedFor));
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
    mixed,
    extras,
    // SH552 — what this command cut, so the command that performed the cut is the one that states
    // it. Sorted, so two runs over one capture report the same lines (P5).
    dropped: dropped.sort((a, b) => (`${a.section}${a.field}` < `${b.section}${b.field}` ? -1 : 1)),
  };
}

/**
 * The `(field, value)` pairs the proposal marked as chrome (SH552).
 *
 * <h3>Read, never re-derived</h3>
 *
 * <p>The rule that decides what counts as adopted lives in `propose.mjs` and has to stay there: it
 * needs every candidate's page set, which is a fact about the whole capture rather than about the
 * section being written. So this reads `proposal.adopted` and matches on it exactly. Re-deriving the
 * predicate here would be a second implementation of one judgement, free to disagree with the note a
 * reviewer approved — and the reviewer approved the note.</p>
 *
 * <p>Keyed on the pair and not on the field name: a `heading` that is chrome on one value is ordinary
 * content on another, and dropping a whole field would take the section's own heading with it. The
 * value is compared trimmed, because that is how `propose` compared it.</p>
 */
function adoptedIndex(proposal) {
  const index = new Set();
  for (const row of proposal?.adopted ?? []) {
    if (typeof row?.field === "string" && typeof row?.value === "string") {
      index.add(`${row.field}\u0000${row.value.trim()}`);
    }
  }
  return index;
}

/**
 * One candidate's fields with the chrome values left out (SH552).
 *
 * <p>Left out rather than emptied: an absent field renders nothing, and a field holding `""` is a
 * value a curator has to notice and delete. `{{#if}}` guards every field in the layout (SH465), so
 * absence is already the shape that renders nothing at all.</p>
 *
 * <p>Every drop is recorded, because a cut nobody states reads as a capture that did not contain the
 * thing — which is the failure this task is the seventh instance of.</p>
 */
function withoutChrome(row, chrome, dropped) {
  if (!chrome.size) return { ...row.fields };
  const data = {};
  for (const [field, value] of Object.entries(row.fields ?? {})) {
    if (typeof value === "string" && chrome.has(`${field}\u0000${value.trim()}`)) {
      dropped.push({ section: row.id, field, value });
      continue;
    }
    data[field] = value;
  }
  return data;
}

/**
 * The structural layer a theme lays out with — named by **role**, never by section type (SH508).
 *
 * <p>SH507 decided a converted page is presented by a Shio theme. A theme needs something to lay
 * out, and the generated layout had nothing: `section > h2 > ul > li` and eight `shio-*` names, with
 * no container to bound a measure, no stack to space siblings and no grid to put cards beside each
 * other. The source needed five nested wrappers to place one heading — `max-w-5xl mx-auto`, a flex
 * row, a `shrink-0`, a `flex-1 text-center md:text-left` — and this layout had none of those
 * elements at all. So the tree comes before the stylesheet, and it is the vocabulary's own rather
 * than a translation of anybody's.</p>
 *
 * <p><b>Roles, because a theme has to survive the next section type.</b> Every name here describes
 * what an element *does* — bound, space, arrange, carry an image, hold the actions — so a theme
 * written against them keeps working when an eighth type ships. A structural class that named a
 * type would make the theme a list of types.</p>
 *
 * <p>The column count is not a role: it is `SectionFeatureGrid.columns`, already on the section as
 * `data-shio-columns`, so a theme reads `[data-shio-columns="3"] .shio-grid` and the contract has
 * both ends without the template writing the number twice.</p>
 *
 * <p><b>SH556 — and that last sentence is the whole rule, which the templates then broke sixteen
 * times.</b> A class is a *role*; a per-type or per-instance handle is a `data-*` attribute. The
 * templates emitted the roles and then `shio-section-<Type>`, `shio-features`, `shio-feature`,
 * `shio-feature-title`, `shio-quote-avatar` and eleven more, none of them defined anywhere — so
 * SH521's lint fired on all eleven authorable pages of a converted site and was right: *this page
 * draws with 5 class name(s) its own stylesheet does not define, and it loads no other*. Which list
 * should move looked like a real choice — emit only roles and lose the per-type hook, or grow the
 * theme by sixteen and make it a component stylesheet — and it was not one, because <b>the hook was
 * never lost: it is already on the same element</b>. `data-shio-section="{{type}}"` sat beside
 * `class="shio-section-{{type}}"`, and `data-shio-row="features"` beside `class="shio-feature"`.
 * The per-type handle was spelled twice, exactly what the `data-shio-columns` paragraph above
 * forbids, so removing the class costs a curator nothing: `[data-shio-section="SectionHero"]` and
 * `[data-shio-row="features"] h3` address what the classes addressed. The leaves were already
 * styled by element — `.shio-card h3`, `.shio-card p`, `.shio-card cite` — so their class names
 * carried no shape either.</p>
 */
export const LAYOUT_ROLES = Object.freeze([
  "shio-page",       // <body> — the vertical rhythm *between* sections
  "shio-section",    // one section, which is what `.shio-page`'s rhythm spaces
  "shio-container",  // the outer bound: one measure, centred, so a section can still go full bleed
  "shio-stack",      // the vertical rhythm *inside* one section
  "shio-grid",       // columns, counted from the nearest `data-shio-columns`
  "shio-card",       // one bounded unit of a grid or a stack
  "shio-media",      // an image, on the image or on the one element wrapping it
  "shio-actions",    // where a call to action lives
  "shio-button",     // and what it looks like
]);

/**
 * The five roles that name a *passage of text* rather than a box (SH556).
 *
 * <p>They were always in the theme and never in a list, which is how {@link LAYOUT_ROLES} came to
 * be checked in both directions while these were checked in neither. Separate from the structural
 * eight because they are: a theme may restyle `shio-heading` without touching a single box.</p>
 */
export const TEXT_ROLES = Object.freeze([
  "shio-page-title",
  "shio-heading",
  "shio-subheading",
  "shio-intro",
  "shio-body",
]);

/**
 * Every class name the generated templates are allowed to draw (SH556).
 *
 * <p>The set SH521's lint measures a page against, from this side. It is the union and nothing
 * else: a name the templates emit and this does not hold is a name the theme does not define, which
 * is `page-unstyled` on every page that renders it.</p>
 */
export const VOCABULARY_CLASSES = Object.freeze([...LAYOUT_ROLES, ...TEXT_ROLES]);

/**
 * The three roles that are **pure wrappers** — they bound, space or arrange, and nothing else.
 *
 * <p>Two properties SH185 imposes on them, both held by `convert.test.mjs`. A wrapper carries no
 * `sh-post`: ownership resolves to the nearest annotated ancestor, so a wrapper that declared one
 * would silently reassign every field under it to the wrong post. And a wrapper carries no
 * `sh-field` — it is presentation, and a curator has nothing to edit in it. The other five roles are
 * *not* in this list on purpose: `shio-card` and `shio-media` land on elements that do declare
 * fields (a feature row draws its `icon` into an attribute), and the rule would be false of them.</p>
 */
export const WRAPPER_ROLES = Object.freeze(["shio-container", "shio-stack", "shio-grid"]);

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
{{#unless content}}<div class="shio-container"><h1 class="shio-page-title" sh-field="title">{{title}}</h1></div>{{/unless}}
{{#getRelation sections}}
<section class="shio-section" sh-post="{{id}}" data-shio-section="{{type}}" data-shio-align="{{align}}" data-shio-variant="{{variant}}" data-shio-columns="{{columns}}">
<div class="shio-container shio-stack">
{{#if heading}}<h2 class="shio-heading" sh-field="heading">{{heading}}</h2>{{/if}}
{{#if subheading}}<p class="shio-subheading" sh-field="subheading">{{subheading}}</p>{{/if}}
{{#if intro}}<p class="shio-intro" sh-field="intro">{{intro}}</p>{{/if}}
{{#if body}}<div class="shio-body" sh-field="body">{{{body}}}</div>{{/if}}
{{#if image}}<p class="shio-media"><img sh-field="image" src="{{file_url image}}" alt="{{heading}}"></p>{{/if}}
{{#if ctaLabel}}<p class="shio-actions"><a class="shio-button" sh-field="ctaLabel" sh-field-attr="ctaHref:href" href="{{ctaHref}}">{{ctaLabel}}</a></p>{{/if}}
{{#if features}}<ul class="shio-grid">{{#each features}}<li class="shio-card" data-shio-row="features" data-shio-icon="{{icon}}" sh-field-attr="features.{{@index}}.icon:data-shio-icon">{{#if title}}<h3 sh-field="features.{{@index}}.title">{{title}}</h3>{{/if}}{{#if body}}<p sh-field="features.{{@index}}.body">{{body}}</p>{{/if}}</li>{{/each}}</ul>{{/if}}
{{#if questions}}<div class="shio-stack">{{#each questions}}<div class="shio-card" data-shio-row="questions">{{#if question}}<h3 sh-field="questions.{{@index}}.question">{{question}}</h3>{{/if}}{{#if answer}}<div class="shio-body" sh-field="questions.{{@index}}.answer">{{{answer}}}</div>{{/if}}</div>{{/each}}</div>{{/if}}
{{#if logos}}<ul class="shio-grid">{{#each logos}}<li class="shio-media" data-shio-row="logos">{{#if image}}<img sh-field="logos.{{@index}}.image" src="{{file_url image}}" alt="{{name}}">{{/if}}{{#if href}}<a sh-field="logos.{{@index}}.name" sh-field-attr="logos.{{@index}}.href:href" href="{{href}}">{{name}}</a>{{/if}}{{#unless href}}<span sh-field="logos.{{@index}}.name">{{name}}</span>{{/unless}}</li>{{/each}}</ul>{{/if}}
{{#if quotes}}<div class="shio-grid">{{#each quotes}}<blockquote class="shio-card" data-shio-row="quotes">{{#if quote}}<p sh-field="quotes.{{@index}}.quote">{{quote}}</p>{{/if}}{{#if avatar}}<img class="shio-media" sh-field="quotes.{{@index}}.avatar" src="{{file_url avatar}}" alt="{{author}}">{{/if}}{{#if author}}<cite sh-field="quotes.{{@index}}.author">{{author}}</cite>{{/if}}{{#if role}}<span sh-field="quotes.{{@index}}.role">{{role}}</span>{{/if}}</blockquote>{{/each}}</div>{{/if}}
</div>
</section>
{{/getRelation}}`;

/**
 * Which theme the one layout names — the whole of SH510, in one decision.
 *
 * <h3>What was being sent</h3>
 *
 * <p>An authorable page inlined the captured theme and could not apply it: **1,062,141 of the page's
 * 1,066,436 bytes** were a stylesheet whose ~2,894 rules met 8 `shio-*` names. Under SH507 that is no
 * longer a defect to repair but a payload to stop sending — a megabyte on every page of a converted
 * site, served on the public route with `max-age=300`, for rules that match nothing. And with SH509's
 * theme in the document there would be **two stylesheets on one page whose order decides the
 * outcome**, which is the accident SH424 spent a task removing, reintroduced by inclusion rather than
 * by cascade.</p>
 *
 * <h3>The mechanism is the name, not a second `<style>`</h3>
 *
 * <p>The layout still emits exactly one `{{theme.css}}`. What changes is **which theme resolves it**:
 * a layout names its `THEME` by title, so an authorable conversion names `<site> (Shio)` and a
 * fidelity one names `<site> (captured)`. That is the render model as it already is — no branch in
 * the template, no second handle, and the vocabulary theme keeps its token compilation (a literal
 * copy of SH509's CSS in the layout would be a second copy of one stylesheet and would render with
 * the `:root` block missing).</p>
 *
 * <h3>The capture keeps its bytes, and SH549 is what `authorable` had to stop meaning</h3>
 *
 * <p>The captured `Theme` post is written exactly as before: **fidelity** inlines it and reaches
 * 0.00% with it, and the same site converted the other way needs every byte. SH510 made
 * `authorable` mean `verbatim === 0 && mixed === 0` so that a conversion with any verbatim page
 * still named the captured theme — refusing to make one page **worse** to make another better, and
 * saying that a mixed site's composed pages stay unstyled, *exactly where they are today*.</p>
 *
 * <p><b>What that decision did not anticipate is the frequency.</b> A real site always has a block
 * the vocabulary cannot name; the first attempt on the first site had one, so `verbatim === 0` is
 * not the rare case but the normal one, and a site-wide switch turned **one** unmatched block into a
 * site-wide regression — twelve pages carrying 1,062,146 characters of CSS that defines none of the
 * `shio-*` classes they draw, `page-unstyled` on ten of them. So `authorable` is now simply "any
 * page composes sections", and the trade SH510 refused is not made either: the pages that kept
 * verbatim markup get the captured CSS **per page**, through `capturedStyleFor`. Both claims hold at
 * once, which is what the render model already allowed and this function was not asking for.</p>
 *
 * @returns {string|null} the title to name, or `null` when the document holds no theme at all
 */
function layoutTheme({ site, captured, authorable }) {
  if (authorable) {
    return vocabularyThemeTitle(site);
  }
  return captured ? `${site} (captured)` : null;
}

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
 *
 * <h3>And the collections, which were half the vocabulary rendering as a bare heading (SH465)</h3>
 *
 * <p>SH464's union drew six scalar fields and **no collection**, and four of the seven section types
 * are collection-shaped: `SectionFeatureGrid.features`, `SectionFaq.questions`,
 * `SectionLogoWall.logos` and `SectionTestimonial.quotes`. So those four rendered their heading and
 * stopped, and `SectionLogoWall` — which has *only* `heading` and `logos` — rendered as a bare
 * `<h2>`. Measured on a replica: the section post held both feature cards with their `title` and
 * `body`, and the page contained the heading and none of the cards. That is worse than losing it at
 * capture, because every instrument said it was there — `verify` clean, the post reading back
 * complete, the field editable in the console — and the page a curator approves was missing half its
 * content.</p>
 *
 * <p>The four are `Relator` widgets, and this is the one place the vocabulary's own shape had to be
 * read rather than assumed: an SH95 `Relator` declares **nested fields**, so its value is an inline
 * array of row objects living in the section's own `data` — not a list of references to other posts.
 * That makes `{{#getRelation}}` the wrong helper (it resolves ids and friendly URLs, and would
 * stringify a row map into nothing) and Handlebars' own `{{#each}}` the right one: `fieldsOf` already
 * converts a JSON array into the `List<Map>` a section walks. One `{{#each}}` per collection, wrapped
 * in `{{#if}}` so an absent one renders no empty container.</p>
 *
 * <h3>The protocol grew the indexed address, which is what the rows were waiting for (SH475)</h3>
 *
 * <p>SH465 left a row unannotated and said why: SH185 addresses a field as a flat `prop` on the post
 * a `sh-post` ancestor names, so `sh-field="body"` on a row would write `data.body` on the
 * **section** — a field `SectionFeatureGrid` does not declare — and `title` would rename the post.
 * That reasoning was right and the conclusion was a stop: five editable fields on a page whose two
 * feature cards hold the actual content, and four of the seven section types are collection-shaped.</p>
 *
 * <p><b>The row is not a post, so it cannot be addressed as one.</b> An SH95 `Relator` declares
 * nested fields — the value is an inline array of row maps in the section's own `data`, with no id
 * to put in an `sh-post`. So the address is a **path**: `sh-field="features.{{@index}}.title"`, which
 * `annotateEditable` passes through unchanged (it never parsed a prop) and
 * `ShPostDraftMergeService` resolves into `data.features[0].title`. Three segments, the middle one
 * an integer, the array and the row already there — anything else is still a flat key, so every
 * existing prop writes exactly as it did.</p>
 *
 * <p>`data-shio-row="<field>"` stays: it names the collection a row came from, which the path
 * repeats but a stylesheet and a reader cannot read out of an `sh-field`. What a row still does not
 * get is a way to be **added, removed or reordered** — the path addresses a value, not a list — so
 * that stays the console form's job.</p>
 *
 * <h3>And the fields that are drawn into an attribute, which had nowhere to be declared (SH503)</h3>
 *
 * <p>Counting the annotations against the fields a section actually fills gave **9 of 10**, and the
 * one missing was `ctaHref` — the destination of the call to action. `sh-field` declares what an
 * element's *content* is, so **one element declares one field**, and a link is two: its text and its
 * target. `sh-field="ctaLabel"` took the anchor, so the href was captured, stored, rendered into the
 * page and never declared, and a curator could rename the button and not repoint it.</p>
 *
 * <p>`sh-field-attr="FIELD:ATTRIBUTE"` is the second declaration, and it lands on three elements
 * here: the CTA link's `href`, a logo row's `href`, and a feature row's `icon` — which has no text
 * node at all, it is a `data-` attribute the theme's CSS reads. `image` never had the problem only
 * because its element is the `<img>` and the field it declares *is* the attribute.</p>
 *
 * <p>What is deliberately **not** declared is `align`, `variant` and `columns`. Those are drawn into
 * attributes too, and they are `Combo Box` fields — a closed set of choices — while the bridge's
 * edit surface is a text box. Offering a curator a box in which three strings work and the rest
 * silently do nothing is worse than sending them to the console form, so they stay the form's job in
 * the same way adding a row does. `ShConvertedLayoutRenderTest` holds that exemption and asserts its
 * premise: a presentation field that becomes free text fails there rather than staying exempt.</p>
 */
function pageLayout(markup, site, theme, capturedFor = false) {
  const lang = langOf(markup);
  return {
    url: FIDELITY_LAYOUT_URL,
    type: "PageLayout",
    folder: THEME_FOLDER,
    data: {
      TITLE: fidelityLayoutTitle(site),
      // A THEME naming a post the site does not have is a marked failure at render time
      // (`data-shio-theme-missing`), so it is named only when that theme post is in this document.
      ...(theme ? { THEME: theme } : {}),
      HTML: `<!doctype html>
<html${lang ? ` lang="${lang}"` : ""}>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{post.title}}</title>
<style>{{theme.css}}</style>
${capturedFor ? `${CAPTURED_STYLE_HTML}\n` : ""}</head>
<body class="shio-page">
${PAGE_BODY_HTML}
</body>
</html>
`,
    },
  };
}

/**
 * The captured stylesheet, on the pages that carry verbatim markup and on no others (SH549).
 *
 * <h3>Why a second `<style>` here is right where SH510's was wrong</h3>
 *
 * <p>SH510 refused two stylesheets on one page, and it was refusing something else: an authorable
 * page inlining a megabyte that matched none of its classes, with the vocabulary theme beside it and
 * the cascade deciding. This is not that. `{{#if content}}` is a **per-page** condition — `content`
 * holds the source's own markup — so an authorable page still receives exactly one stylesheet and
 * is byte-identical to what SH510 produced. Only a page that kept the source's class names gets the
 * source's CSS, which is the page that cannot render without it.</p>
 *
 * <h3>By reference, so there is still one copy</h3>
 *
 * <p>`{{#getRelation}}` resolves a friendly URL inside the current site and yields the post's own
 * fields (SH247), so the captured `Theme` post remains the single copy: SH424's token edits still
 * reach the page, which a literal copy in this template would have broken — the objection SH510
 * raised against inlining SH509's CSS here, and it applies equally in this direction.</p>
 *
 * <p>Second in the head, after `{{theme.css}}`, so the source's rules win the collisions a reset or a
 * `:root` block would otherwise decide against the page's own markup. A **mixed** page gets both and
 * needs both — its authored half draws `shio-*` and its verbatim half draws the source's names — and
 * that page is the one the site-wide switch left half-unstyled whichever way it went.</p>
 *
 * <h3>A constant with no interpolation, which is SH464's seam</h3>
 *
 * <p>`ShConvertedLayoutRenderTest` reads this string out of this file and renders it in the JVM, so
 * the one thing neither language can check alone — that a **literal** argument to
 * `{{#getRelation}}` resolves at all, when the helper's declared shape is a field path — is checked
 * by the half that would fail. The URL is therefore spelled here rather than interpolated from
 * `CAPTURED_THEME_URL`, and `convert.test.mjs` asserts the two agree.</p>
 */
export const CAPTURED_STYLE_HTML =
  '{{#if content}}{{#getRelation "/theme/captured"}}<style>{{CSS}}</style>{{/getRelation}}{{/if}}';

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
 * <h3>And a page is not one mode, which is what the promise always said (SH520)</h3>
 *
 * <p>SH449 wrote `content` **or** `sections`, so accepting one block on a page discarded every other
 * block on it. Measured on `viglet.org`: accepting the FAQ left a `Page` holding a 6-byte `title` and
 * one section, where the same page in fidelity holds 42,371 bytes — the hero, the product grid and
 * the footer gone, and a megabyte of captured CSS still served for markup that no longer existed.
 * `propose` prints the opposite in the same run — *an unaccepted block stays verbatim markup* — and
 * that promise is the whole reason accepting one block is an incremental decision rather than an
 * all-or-nothing rewrite.</p>
 *
 * <p>So a mixed page gets **both**: `sections` for what was accepted, and `content` holding the
 * page's own bytes with exactly the accepted blocks cut out. Verbatim in the sense SH449 insisted
 * on — a slice of the captured file, not a re-serialization — because `stripNonMarkup` blanks rather
 * than deletes and the proposal records each block's span. The remainder keeps the source's order
 * among itself; the sections render after it, because a layout is resolved per post-type and there
 * is one `{{{content}}}` in it.</p>
 *
 * <p>The two modes either side of it are byte-identical to what they were: accept nothing and the
 * whole body is the content, accept every block on the page and there is no remainder to write.
 * A proposal taken before SH520 has no spans, and such a page composes sections alone rather than
 * duplicating the accepted markup — the old behaviour, which a fresh `shio propose` replaces.</p>
 *
 * @param {Map<string, string>} markup path → the page's captured HTML, the same cache the proposal
 *   was computed from. Empty for a caller that has none, which yields exactly SH438's behaviour.
 */
function composePages(proposal, sectionUrls, markup = new Map()) {
  const pages = [];
  for (const page of proposal?.pages ?? []) {
    const seen = new Set();
    const sections = [];
    const cut = [];
    let kept = 0;
    (page.blocks ?? []).forEach((id, at) => {
      const url = `${SECTIONS_FOLDER}/${id}`;
      if (!sectionUrls.has(url)) {
        kept += 1;
        return;
      }
      // The block became a section, so its bytes must not also be in the remainder — whichever
      // occurrence it is. First occurrence wins for the *list*: a signature repeated on one page is
      // still one section post with one set of fields, so listing it twice would ask the page to
      // render the same content twice — a decision for the reviewer, not a thing to infer from a
      // structural collision.
      cut.push(page.spans?.[at] ?? null);
      if (seen.has(url)) return;
      seen.add(url);
      sections.push(url);
    });
    const body = contentFor({ html: markup.get(page.path), sections, kept, cut });
    if (!sections.length && body === null) continue;
    pages.push({
      url: page.path,
      type: PAGE_TYPE,
      folder: PAGES_FOLDER,
      // `title` is required on `Page`, and the capture recorded one. The path is the fallback rather
      // than the site name: it is what makes two untitled pages two rows a human can tell apart.
      data: {
        title: titleOf(page),
        ...(sections.length ? { sections } : {}),
        ...(body === null ? {} : { content: body }),
      },
    });
  }
  return pages.sort((a, b) => (a.url < b.url ? -1 : 1));
}

/**
 * One page's verbatim half: the whole body, the remainder around its sections, or nothing (SH520).
 *
 * @param kept how many of the page's blocks were left in fidelity mode
 * @param cut the spans of the blocks that became sections — `null` for one the proposal cannot place
 */
function contentFor({ html, sections, kept, cut }) {
  if (!sections.length) return bodyOf(html);
  // Everything was accepted: the sections are the page, and there is no remainder. Writing the body
  // here would render every block twice.
  if (!kept) return null;
  // A span the proposal does not carry is a block this cannot cut, and cutting the others would
  // leave that one rendered both as markup and as a section. Refusing the remainder is the honest
  // half-answer, and it is exactly what the previous release wrote.
  if (!cut.length || cut.some((span) => !span)) return null;
  return bodyOf(without(html, cut));
}

/**
 * The page's bytes with the given half-open spans removed, last one first so the earlier offsets
 * still address what they addressed.
 */
function without(html, spans) {
  if (typeof html !== "string") return html;
  let out = html;
  for (const [start, end] of [...spans].sort((a, b) => b[0] - a[0])) {
    if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end > out.length) continue;
    out = out.slice(0, start) + out.slice(end);
  }
  return out;
}

/**
 * Points every `File` field at the post that holds those bytes (SH471).
 *
 * <h3>The measurement that chose between the two options</h3>
 *
 * <p>`propose` writes `fields.image = <img src>` — the source's own path, e.g. `/img/hero.png` — and
 * the authorable path stored that string in a `File` field, which the layout draws with
 * `{{file_url image}}`. That helper resolves a **post id** and answers `""` for anything else, so a
 * converted `SectionHero` rendered `<img src="">`, and SH465's two row fields inherited it.</p>
 *
 * <p>The task offered two fixes and asked for a measurement first. The measurement settled it, and
 * not on the axis the options were drawn on. **The authorable path was uploading no bytes at all**:
 * `sendAssets` ran under `if (!planOnly && verbatim)`, an SH437-era guard from when only fidelity
 * markup referenced the source's paths. So teaching `file_url` to pass a non-id value through would
 * have emitted `src="/img/hero.png"` against a site that serves nothing there — a broken image that
 * *looks* like a fix. Three more things decided the rest: it would put a non-id in a `File` field,
 * which is exactly the `dangling-reference` shape SH470 had just finished removing; `{{#image}}`
 * (SH390) reads `WIDTH`/`HEIGHT` off the `File` post, so a path leaves `srcset` permanently empty on
 * the very replicas SH386 grades for fidelity; and the console's File widget resolves ids.</p>
 *
 * <p>So the bytes go up on both paths, and the id is stored. The vocabulary keeps one meaning, which
 * is what makes every other reader work without being told about conversions.</p>
 *
 * <h3>Declared fields, never a value that merely looks like a path</h3>
 *
 * <p>Only the fields `SECTION_FILE_FIELDS` names are rewritten. Matching any string that equals an
 * uploaded path would have been shorter and is wrong on a case this vocabulary really has: a
 * `SectionLogoWall` row carries both `image` and `href`, and a logo that links to its own artwork
 * would have had its **link** turned into a post id.</p>
 *
 * @param document the desired-state document, not mutated
 * @param ids source path → the `File` post's id, from `sendAssets`
 * @returns `{document, resolved, unresolved}` — the counts, because a field left holding a path is
 *   the defect this exists for and a silent zero would read as success
 */
export function withResolvedFileFields(document, ids) {
  const resolved = [];
  const unresolved = [];
  if (!document?.posts?.length || !(ids instanceof Map) || !ids.size) {
    return { document, resolved, unresolved };
  }
  const posts = document.posts.map((post) => {
    const spec = SECTION_FILE_FIELDS[post?.type];
    if (!spec || !post.data) return post;
    const data = { ...post.data };
    for (const field of spec.fields) {
      data[field] = pointAtFile(data[field], ids, `${post.url}.${field}`, resolved, unresolved);
    }
    for (const [row, fields] of Object.entries(spec.rows ?? {})) {
      if (!Array.isArray(data[row])) continue;
      data[row] = data[row].map((entry, at) => {
        if (!entry || typeof entry !== "object") return entry;
        const copy = { ...entry };
        for (const field of fields) {
          copy[field] = pointAtFile(copy[field], ids,
            `${post.url}.${row}[${at}].${field}`, resolved, unresolved);
        }
        return copy;
      });
    }
    return { ...post, data };
  });
  return { document: { ...document, posts }, resolved, unresolved };
}

/** One field: the id of the post holding those bytes, or the value exactly as it was. */
function pointAtFile(value, ids, where, resolved, unresolved) {
  if (typeof value !== "string" || !value.trim()) return value;
  const id = ids.get(value.trim());
  if (id) {
    resolved.push(where);
    return id;
  }
  // An absolute URL, a `data:` image, or a path the capture never fetched. Left as written — the
  // capture is evidence, and a field this cannot resolve is a fact worth reporting rather than
  // blanking.
  unresolved.push(`${where} = ${value.trim()}`);
  return value;
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
    url: CAPTURED_THEME_URL,
    type: "Theme",
    folder: THEME_FOLDER,
    data: {
      TITLE: `${site} (captured)`,
      CSS: theme.css ?? "",
      TOKENS: `${JSON.stringify(theme.tokens ?? {}, null, 2)}\n`,
    },
  };
}

/**
 * The vocabulary's own theme as a `Theme` post (SH509).
 *
 * <p>Same shape as `themePost` above, and that is the point: SH507 decided an authorable conversion
 * presents with a Shio theme, and a theme in this product is **content**. So it is written, named and
 * edited exactly the way the captured one is — a curator can change a colour without touching the
 * CLI, and a site with a theme of its own overrides this by naming that one on the layout. The bytes
 * live in `vocabulary-theme.mjs`, which is where the argument for each rule is.</p>
 */
function vocabularyThemePost(site) {
  return {
    url: `${THEME_FOLDER}/${VOCABULARY_THEME_SLUG}`,
    type: "Theme",
    folder: THEME_FOLDER,
    data: {
      TITLE: vocabularyThemeTitle(site),
      CSS: VOCABULARY_THEME_CSS,
      TOKENS: `${JSON.stringify(VOCABULARY_THEME_TOKENS, null, 2)}\n`,
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
export function describeConversion({ document, sections, pages = [], verbatim = 0, mixed = 0, extras,
                                     requires = [], dropped = [] }, options = {}) {
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
  // SH520 — the count that says the promise was kept. A reviewer who accepts one block on a page is
  // told, in the run that writes it, that the blocks they did not accept are still on the page.
  if (mixed) {
    lines.push(
      `  ${mixed} of ${pages.length} page(s) mixed — the accepted blocks as sections, the rest of `
        + "each page kept as the source's own markup",
    );
  }
  // SH510 — which presentation the reviewer is getting, said out loud. Under SH507 it is the mode's
  // contract rather than an implementation detail, and naming it is also the cheapest statement of
  // the property the task is about: one stylesheet reaches the page, and this is the one.
  const theme = document.posts.find((post) => post.type === "PageLayout")?.data?.THEME;
  if (theme) {
    lines.push(`  presented by ${theme} — the one stylesheet the layout names`);
  }
  // SH552 — the same cut, stated by the command that performed it. `propose` reported the finding and
  // nothing acted on it for six blocks running (SH452, SH469, SH429 among them): the diagnosis
  // shipped as a sentence somebody had to act on, and the somebody was a command. A drop nobody
  // prints reads as a capture that never held the thing.
  if (dropped.length) {
    const fields = [...new Set(dropped.map((row) => row.field))].sort();
    lines.push(
      `  ${dropped.length} field value(s) dropped as chrome — ${fields.join(", ")}: a value on `
        + "candidates that share no page is nobody's content (a consent banner, a toast). "
        + "`shio propose` names each one and its carriers",
    );
    for (const row of dropped.slice(0, 3)) {
      lines.push(`    ${row.section}.${row.field} ${preview(row.value)}`);
    }
  }
  for (const post of document.posts.slice(0, limit)) {
    const title = Object.values(post.data).find((value) => typeof value === "string" && value.trim());
    lines.push(`  ${post.url}  ${post.type}${title ? `  ${preview(title)}` : ""}`);
  }
  if (document.posts.length > limit) {
    lines.push(`  … ${document.posts.length - limit} more in the document`);
  }
  // SH546 — the instruction now names the command that performs it. It said the true thing and
  // left the caller to find the verb, which for two blocks meant leaving the CLI: the tier this
  // binary *is* had no publish, so the step after the longest command in it was raw HTTP.
  lines.push(
    "  every post lands in DRAFT — publishing is a separate, explicit call (P7): " +
      `shio publish --site ${document.site ?? "<name>"} --all`,
  );
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
