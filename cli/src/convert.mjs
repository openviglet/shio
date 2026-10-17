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

import { normalizeUrl } from "./content-files.mjs";
import { SECTION_FILE_FIELDS, SECTION_REQUIRED, SECTION_TYPES } from "./propose.mjs";
import {
  collidingProperties,
  VOCABULARY_THEME_CSS, VOCABULARY_THEME_SLUG, VOCABULARY_THEME_TOKENS, vocabularyThemeTitle,
  vocabularyTokensFrom,
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

/**
 * What the one generated layout is called (SH573).
 *
 * <h3>Why it stopped saying `(fidelity)`</h3>
 *
 * <p>That was accurate when one layout served one mode. **SH464 made a single layout serve all
 * three**, so the word had been decoration since — decoration shaped like a fact, which is the worst
 * kind: SH559 had to *quote* this title in the preview banner rather than print it, because a
 * reader looking at `layout <site> (fidelity)` beside an unstyled page reasonably concluded the
 * conversion had picked the wrong mode. A quoted wrong name is a smaller defect, not a closed one.
 * The banner states the mode from the page's own fields; this states what the post is.</p>
 *
 * <h3>The migration, which is the actual content of SH573</h3>
 *
 * <p>This string is the key in `site.postTypeLayout` **and** the layout post's `TITLE`, and that is
 * what makes a rename safe rather than a schema change. Both are written from this one function in
 * one document, so:</p>
 *
 * <ul>
 *   <li><b>A new conversion is self-consistent</b> the moment the constant changes.</li>
 *   <li><b>A site converted before and never re-run stays self-consistent too</b> — its stored
 *       binding names the old title and its layout post carries the old title, so it renders
 *       correctly forever. Nothing dangles; the two vintages simply differ by a word.</li>
 *   <li><b>A re-run migrates it</b>, and cannot half-migrate it. The layout post is addressed by
 *       <em>url</em> (`/theme/layout`), so a re-run updates the same post rather than creating a
 *       second one, and the binding it writes in the same document comes from the same call. The
 *       old title is not left behind as an orphan and there is no window where the binding names a
 *       title no post has.</li>
 * </ul>
 *
 * <p>So the answer to "does the rename carry existing sites across" is <b>yes, on their next
 * conversion, and never behind anybody's back</b>. Re-running `convert` is already a write the
 * operator asked for, and it already rewrites this post's `HTML`; what SH573 does not do is reach
 * into a site nobody re-converted, which would be a write nobody asked for on a site somebody may
 * have edited since. `ShConvertedLayoutRenderTest` is the standing proof that the renderer never
 * reads meaning out of this title.</p>
 */
export const generatedLayoutTitle = (site) => `${site} (layout)`;

/**
 * Where a composed page lands, and the post-type that composes it (SH438).
 *
 * <p>The site root, and `Page` — the shape the `marketing-site` blueprint already writes down:
 * `url: "/"`, `folder: "/"`, `data.sections` as an ordered list of section friendly URLs. This is a
 * translation into that shape rather than a new one, which is why the names are its names.</p>
 */
export const PAGES_FOLDER = "/";

/** Where the menu a source states about itself is written (SH878). */
export const MENU_FOLDER = "/menu";

/**
 * The `Menu` post's title, which is the string `{{#menu title=}}` in the layout looks up.
 *
 * <p><b>Spelled twice on purpose, and pinned rather than interpolated (SH880).</b>
 * {@link PAGE_BODY_HTML} is a <em>cross-language contract</em>: `ShConvertedLayoutRenderTest`
 * slices it out of this file's bytes between two backticks, so a `${…}` inside it reaches the JVM
 * uninterpolated. Writing the title as an interpolation shipped a layout asking for a menu called
 * `${MENU_TITLE}`, which matches nothing and renders the `{{else}}` fallback — a working-looking
 * nav, with every JavaScript assertion green because in JavaScript the literal interpolates. The
 * two spellings are held equal by `convert.test.mjs`, which is the cheap half of the pair.</p>
 */
export const MENU_TITLE = "Primary";

/** The two post-types a curated menu is made of, shared with the SH95 vocabulary. */
export const MENU_TYPE = "Menu";
export const MENU_ITEM_TYPE = "MenuItem";
export const PAGE_TYPE = "Page";

/**
 * The system post-type a `--home <path>` front door is written as (SH576).
 *
 * <p>`Redirect` is SH385's, and the renderer resolves it on the read that already ran: a post whose
 * `url` is the path it answers and whose `TO` is where it sends the reader. So a front door needs no
 * route, no rewrite rule and no second addressing scheme — it is one post at `/`.</p>
 */
export const REDIRECT_TYPE = "Redirect";
export const REDIRECT_TO = "TO";

/**
 * The document that makes an accepted plan true.
 *
 * @param {object} input
 * @param {object} input.proposal the proposal computed from the capture **now**
 * @param {{accepted?: Array<{block: string, type: string}>}} input.plan the reviewed decisions
 * @param {string} input.site the site to write into
 * @param {boolean} [input.scratch] declare the created site disposable (SH591) — written only
 *   alongside `createSite`, because only a creator can honestly say a site was never meant to be
 *   kept
 * @param {boolean} [input.createSite] make the site as the run's first step (SH439). The key is
 *   written into the document only when asked, so an `--out` file is the same bytes it was
 *   before for every caller that did not ask (P5) — and carries the intent for the one that did,
 *   which is what makes `shio apply conversion.json` mean what `shio convert` meant
 * @returns {{document: object|null, stale: string[], sections: object[], extras: string[]}}
 */
export function buildConversion({
  proposal, plan = {}, site, createSite = false, scratch = false, markup = new Map(), home = null,
  inventory = null,
  // SH868 — the third mode, asked for. Default false so plain fidelity is what it has always been.
  annotate = false,
}) {
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
  // SH684 — the chrome values a required field kept, held apart from the dropped ones because they
  // are the opposite decision and a reader has to be able to tell which one was made about a field.
  // `declined` is the third outcome, and it is neither of the other two: a candidate that would be
  // nothing but chrome.
  const kept = [];
  const declined = [];
  // SH857 — the fourth outcome, and the only one that is about a *row* rather than a field. Held
  // here rather than left to `propose` alone because a proposal is a file: one written before this
  // guard existed, or edited by hand, still carries `features: [{}, {}]`, and re-running `convert`
  // is what a curator looking at two empty cards will reach for.
  const emptied = [];
  const sections = accepted
    .map((entry) => byId.get(entry.block))
    // An accepted candidate always has a type — `propose --accept` refuses a typeless one, whose
    // answer is a post-type of its own — but a plan is a file a human may have edited.
    .filter((row) => row?.type)
    .map((row) => ({
      url: `${SECTIONS_FOLDER}/${row.id}`,
      type: row.type,
      folder: SECTIONS_FOLDER,
      data: withoutEmptyRows(withoutChrome(row, chrome, dropped, kept, declined), row, emptied),
    }))
    // SH684 — `null` is a decline, and the block behind it stays verbatim: `composePages` is handed
    // the urls that were written, so a section missing from this list is one its page keeps as
    // markup. Exactly what `--accept all` would have done, had it known yet what chrome was.
    .filter((post) => post.data)
    .sort((a, b) => (a.url < b.url ? -1 : 1));

  // SH763 — two lists out of one pass: the pages, and the `SectionMarkup` posts their `sections`
  // now name. Kept apart from `sections` above because those are the blocks a reviewer *accepted*,
  // and every count and report in this file means that by the word.
  const { pages, markup: markupPosts, obstructions, annotations, annotationFields } = composePages(
    proposal, new Set(sections.map((post) => post.url)), markup, annotate,
  );
  // SH576 — the one address the grammar does not spell, written when the source gave us none.
  const frontDoor = homePost(pages, site, home);
  if (frontDoor) pages.unshift(frontDoor.post);
  const markupUrls = new Set(markupPosts.map((post) => post.url));
  // A page carries verbatim markup in `content`, in a `SectionMarkup`, or in both — and SH763 made
  // the second one possible. Reading only `content` would count a page whose kept markup all sits
  // between accepted blocks as fully authored, which is the number `capturedFor` reads.
  const held = pages.filter((page) => typeof page.data.content === "string"
    || (page.data.sections ?? []).some((url) => markupUrls.has(url)));
  // Two counts, because they are two different pages (SH520). `verbatim` is a page nobody accepted
  // anything on — fidelity, whole. `mixed` is a page that kept some blocks and authored others, and
  // it is the shape that used to be silently the first one minus everything it did not accept.
  const verbatim = held.filter((page) => !page.data.sections).length;
  const mixed = held.length - verbatim;

  // SH584 — computed here rather than in the report, because `--json` and the corpus read it too.
  const unfollowed = unfollowedTargets(inventory, pages);
  // SH878 — the menu the source states about itself, before the vocabulary is computed: the two
  // types it needs have to reach `postTypes` or the write is refused on a type nobody provisioned,
  // which is SH607's defect met one post-type family later.
  const menu = menuPosts(inventory, pages);

  const extras = [];
  // The Redirect post-type has to exist on the target before a `--home` redirect can be written,
  // the same way `Theme` and `SiteScripts` do. A home *page* is a `Page` and needs nothing.
  if (frontDoor?.home.as === "redirect") extras.push("Redirect");
  const posts = [...sections, ...markupPosts, ...pages];
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
  // SH831 — the captured palette, under this theme's own names. Computed whether or not the theme is
  // written, because `describeConversion` reports the colours that found no source and a run that
  // named none would be the silent default the whole task is about.
  const palette = vocabularyTokensFrom(proposal?.theme?.rows);
  // SH853 — the names both `:root` blocks would declare on a page that carries both. Computed from
  // the two trees this run is about to write, not from a constant, because one of them is the
  // source's: the `shio-` prefix makes the answer empty by construction and the *check* is what
  // says so on this capture, rather than the cascade deciding by which `<style>` came second.
  const collides = collidingProperties(proposal?.theme?.tokens, palette.tokens);
  if (theme === vocabularyThemeTitle(site)) {
    posts.push(vocabularyThemePost(site, palette.tokens));
    extras.push("Theme (Shio)");
  }
  // SH449 wrote the layout only for a verbatim page, and SH464 is what that cost: the authorable
  // path wrote sections, a Page and a Theme and NO PageLayout, so `/preview/<site>/` answered
  // **400 — binds no layout for post-type 'Page'**. Block Q is named "an existing site becomes an
  // authorable Shio site" and neither of its two modes produced one. Every page needs a layout,
  // because the renderer resolves one per post-type; the layout below serves all three modes.
  // SH607 — the SH95 types this document writes posts of, sorted so two conversions of one capture
  // are the same bytes (P5). Filtered to the vocabulary on purpose: a block the vocabulary has no
  // type for is proposed as a `Block*` post-type of its own (SH379), and naming one here would
  // trade its honest `No post-type named 'BlockPricing'` for a refusal about a catalogue that was
  // never going to carry it.
  //
  // SH830 — computed above the layout rather than below it, because the layout is now written
  // *against* it: the template branches for the types this conversion creates and for no others.
  const vocabulary = [
    ...(pages.length ? [PAGE_TYPE] : []),
    ...new Set(sections.map((post) => post.type).filter((type) => type in SECTION_TYPES)),
    // SH763 — named from the posts rather than from `SECTION_TYPES`, because no block maps into it:
    // `propose` cannot derive a markup section and does not know the name. It has to be here all the
    // same — a post-type this document writes and does not provision is the refusal SH607 fixed for
    // the other ten, met one type later.
    ...(markupPosts.length ? [MARKUP_TYPE] : []),
    // SH878 — the two the menu is made of, named for the same reason `SectionMarkup` is: this
    // document writes posts of them and nothing else would provision them.
    ...(menu ? [MENU_TYPE, MENU_ITEM_TYPE] : []),
  ].sort((a, b) => (a < b ? -1 : 1));
  if (pages.length) {
    // SH549 — the captured stylesheet, reaching only the pages that carry verbatim markup. Named
    // only when the layout named the *vocabulary* theme: a pure-fidelity conversion already names the
    // captured one, and emitting it twice would put a megabyte on the page twice.
    const capturedFor = theme === vocabularyThemeTitle(site)
      && extras.includes("Theme") && held.length > 0;
    posts.push(pageLayout(markup, site, theme, capturedFor, sectionFieldsOf(vocabulary),
      sectionTypesOf(vocabulary)));
    extras.push("PageLayout");
  }
  // SH878 — after the layout that names the menu, which is the order every other pair here uses.
  if (menu) posts.push(...menu.posts);

  const folders = [
    SECTIONS_FOLDER,
    ...(pages.length ? [PAGES_FOLDER] : []),
    ...(extras.length ? [THEME_FOLDER] : []),
    // SH878 — its own folder, so a curator editing the menu is not hunting for four posts among
    // the pages. Written only where there is a menu, so a capture that stated none is the same
    // bytes it was (P5).
    ...(menu ? [MENU_FOLDER] : []),
  ];
  return {
    document: {
      site,
      // SH439 — present only when asked. Creating a site nobody named is the surprise P7 forbids,
      // and an absent key is what every document written before this said.
      ...(createSite ? { createSite: true } : {}),
      // SH591 — only alongside `createSite`, which is the guarantee rather than a limitation: the
      // flag says the site was never meant to be kept, and only its creator can say that honestly.
      // It rides in the document a human can read *before* the run, so `shio site rm --scratch`
      // removing it later is not the surprise P7 forbids — the declaration preceded the content.
      ...(createSite && scratch ? { scratch: true } : {}),
      // SH607 — the model this conversion writes against, installed before it writes. Every other
      // type a conversion emits is a *system* one the instance provisions on demand (`Theme`,
      // `SiteScripts`, `Redirect`, `PageLayout`, `File`), which is why the omission was invisible:
      // the two that are not — `Page` and the SH95 sections — shipped only inside a blueprint, so
      // the whole chain failed on a bare instance and passed on every instance anybody tried it on.
      ...(vocabulary.length ? { postTypes: vocabulary } : {}),
      // SH871 put the annotated names here as `postTypeFields`, because SH868 had made each of
      // them a field on `Page`. SH872 gave the values back to the markup they came from, so a
      // conversion declares nothing per capture and this key is gone — the op it compiled to
      // (`render.provision`'s `data.fields`) stays, as the door an agent widens a type of its own
      // through without a `PUT` that would clobber somebody's field.
      // SH449 — the edge that turns a layout into a rendered page. Written only when there is a
      // layout to bind, so a document that does not need it is the same bytes it was before (P5).
      ...(pages.length ? { postTypeLayout: { [PAGE_TYPE]: generatedLayoutTitle(site) } } : {}),
      folders: folders.map((path) => ({ path })),
      posts,
    },
    stale: [],
    sections,
    // SH763 — reported apart from `sections` for the reason it is built apart: these are runs of the
    // source's own markup, not blocks anybody made authorable, and a count that merged the two would
    // read as a conversion having done more than it did.
    markup: markupPosts,
    pages,
    verbatim,
    mixed,
    extras,
    // SH576 — `{written, from}` when this run had to invent a front door, absent when the source
    // had one. Returned rather than only printed, because `describeConversion` is not the only
    // reader: the corpus asserts it, and a note a caller cannot query is one only a person can act
    // on (P9).
    ...(frontDoor ? { home: frontDoor.home } : {}),
    // SH552 — what this command cut, so the command that performed the cut is the one that states
    // it. Sorted, so two runs over one capture report the same lines (P5).
    dropped: dropped.sort((a, b) => (`${a.section}${a.field}` < `${b.section}${b.field}` ? -1 : 1)),
    // SH684 — the other decision, reported the same way and for the same reason. A keep nobody
    // prints reads as a derivation that found the heading on the page, which is how the seven
    // `required-field-empty` errors were invisible from either side of it. Absent when empty: a
    // conversion whose chrome touched no required field has nothing to say here (P3).
    ...(kept.length
      ? { kept: kept.sort((a, b) => (`${a.section}${a.field}` < `${b.section}${b.field}` ? -1 : 1)) }
      : {}),
    // SH684 — and the third outcome, which is the one that most needs saying: an accepted candidate
    // that is not in `sections` has silently become verbatim markup, and a reviewer reading a count
    // of sections would read that as a block the capture never held.
    ...(declined.length
      ? { declined: declined.sort((a, b) => (a.section < b.section ? -1 : 1)) }
      : {}),
    // SH857 — the rows this run refused to write, so the count of cards on the replica and the count
    // of units in the capture can be reconciled from the report rather than by looking at the page.
    ...(emptied.length
      ? { emptied: emptied.sort((a, b) => (`${a.section}${a.field}` < `${b.section}${b.field}` ? -1 : 1)) }
      : {}),
    // SH858 — the overlays cut out because nothing on the page could dismiss them. Returned as well
    // as printed, for SH576's reason: the corpus asserts it and `--json` is a caller too.
    ...(obstructions.length ? { obstructions } : {}),
    // SH868 — the pages annotated in place, and the fields the instance owes before an edit to one
    // can be written. Both returned, for the reason above and one more: `requirePostTypeFields`
    // reads the second, so a run that annotates says what it needs rather than failing at the batch.
    ...(annotations.length ? { annotations, annotationFields } : {}),
    // SH584 — the bound the operator chose, joined to the pages this run is writing. Returned and
    // not only printed, for SH576's reason one line up: the corpus asserts it, and `--json` is a
    // caller too.
    ...(unfollowed ? { unfollowed } : {}),
    // SH878 — how many entries the menu took from the source's own navigation, and how many rows
    // the capture recorded. Both, because the difference is the report: a source stating six
    // entries of which two are site-wide is a menu of two and a reader has to be able to see that
    // the other four were considered.
    ...(menu ? { menu: { items: menu.items, stated: (inventory?.navigation ?? []).length } } : {}),
    // SH831 — which of the theme's colours took a captured value and which kept a default. Present
    // only where the theme was written: a fidelity conversion never renders this theme, and reporting
    // a palette nothing paints with would be a note about an artefact that is not on the site.
    // SH846 — and which of them was *offered* a value and refused it, which is neither of the two
    // above: a slot the source said nothing about and a slot the source said something unusable
    // about kept the same default for different reasons, and only one of them is a defect a curator
    // can go and fix in the source.
    ...(theme === vocabularyThemeTitle(site)
      ? {
        palette: {
          mapped: palette.mapped,
          unmapped: palette.unmapped,
          rejected: palette.rejected,
          // SH849 — the slots the bridge names no source role for, so `--json` can do the same
          // arithmetic the report line does rather than having to know this theme's shape.
          unbridged: palette.unbridged,
          ...(palette.unchecked.length ? { unchecked: palette.unchecked } : {}),
          // SH852 — and the pair the floor judged, failed, and was not allowed to fix, which is the
          // one state worse than either: the theme ships a pair nobody can read and the run said so
          // rather than counting two filled slots as two successes.
          ...(palette.unheld.length ? { unheld: palette.unheld } : {}),
          // SH853 — a name both `:root` blocks on a mixed page declare. Empty by construction since
          // the `shio-` prefix, and reported rather than asserted because the other half of the
          // intersection is the *source's* and arrives with a capture, not with a commit here.
          ...(collides.length ? { collides } : {}),
        },
      }
      : {}),
  };
}

/**
 * The links this conversion writes that point at pages the crawl never followed (SH584).
 *
 * <h3>The fact is established at phase one and stated at phase five, as damage</h3>
 *
 * <p>`shio clone --depth 2` over `openviglet.github.io` recorded `unfollowed: 22` — the model
 * catalogue's vendor pages, one level past the ceiling, each with its path and reference count.
 * That is the crawl being honest about its own bound, and it is written down before anything is
 * converted. Twelve minutes and three commands later `shio verify` exits 1 with **22
 * `page-link-broken` errors**, one per unfollowed path, on a replica whose only other error was
 * SH467's. Reading the run, twenty-three errors look like a broken conversion; it took opening
 * `source-inventory.json` and matching the lists one to one to learn that twenty-two of them were
 * a flag somebody chose.</p>
 *
 * <p>The instance cannot know this — it has never seen the capture — so the phase that says it is
 * the one already holding both halves. This is SH557's shape and its second instance, which is why
 * it is worth a rule and not only a fix: SH557 was a trait of the source, this is a bound of the
 * run, and both were knowable at phase one and arrived as a red gate at phase five.</p>
 *
 * <h3>Why it is a join and not a copy of `inventory.unfollowed`</h3>
 *
 * <p>An unfollowed target can still end up written — `--home` redirects one, and a later crawl at a
 * higher `--depth` covers others — so echoing the capture's own count would report a page this run
 * serves as a page it does not. Subtracting what is being written makes the number the one
 * `verify` will produce, which is the only version of it worth printing.</p>
 */
function unfollowedTargets(inventory, pages) {
  const rows = Array.isArray(inventory?.unfollowed) ? inventory.unfollowed : [];
  if (!rows.length || !pages.length) return null;
  const written = new Set(pages.map((page) => normalizeUrl(page.url)));
  const outstanding = rows
    .filter((row) => typeof row?.path === "string" && row.path)
    // A query string never survives into a friendly URL (SH557), and the target is the path.
    .map((row) => ({ path: row.path.split("?")[0], refs: Number(row.refs) || 0 }))
    .filter((row) => row.path && !written.has(normalizeUrl(row.path)));
  if (!outstanding.length) return null;
  return {
    paths: outstanding.length,
    refs: outstanding.reduce((total, row) => total + row.refs, 0),
    first: outstanding.slice(0, 3).map((row) => row.path),
  };
}

/**
 * The menu the source states about itself, as `Menu` and `MenuItem` posts (SH878).
 *
 * <h3>What was being drawn instead, and what the source had said</h3>
 *
 * <p>The generated layout listed every `Page` by {@code title}, alphabetically — and a page's title
 * is the capture's reading of the document's own {@code <title>}, which is written for search.
 * SH873 measured that shape on a blueprint; measured here on the {@code theme-heavy} archetype, a
 * source whose menu says <b>Northwind &amp; Co</b> then <b>About</b> was replicated as
 * <b>About &amp; contact</b> then <b>Northwind &amp; Co</b>: one label wrong, the order inverted,
 * and nothing about either visible to a reader who had not seen the source.</p>
 *
 * <p>The capture had the answer the whole time. {@code inventory.navigation} is
 * {@code <nav>}/{@code [role=navigation]} only (SH432 drew that boundary so a privacy policy in a
 * footer is not a menu item), deduped, <b>in source order</b>, and each row carries {@code on} —
 * how many captured pages it appears on.</p>
 *
 * <h3>`on` is the inclusion rule, and it is SH432's own argument</h3>
 *
 * <p>"A link on every page is a menu, a link on one page is a link" is why that count exists, so a
 * row is an item when it appears on <b>every page this capture kept markup for</b>. On the measured
 * archetype that admits the two real entries and drops two decoys — a body link reading
 * {@code Read about us} and a second {@code Home} — which no rule about labels or positions would
 * have separated.</p>
 *
 * <p>An entry is written only where its target is a page this conversion actually wrote. An
 * external one is left out rather than given an `href`: {@code MenuItem.href} exists for exactly
 * that and SH382's lint refuses a row setting both, but a replica pointing off-site from its
 * primary menu is a decision a person makes, not one a capture implies.</p>
 *
 * @returns `{posts, items}` — the posts to write and how many entries the menu holds; `null` where
 *          the source stated no navigation, which is five of the seven corpus archetypes
 */
function menuPosts(inventory, pages) {
  const rows = Array.isArray(inventory?.navigation) ? inventory.navigation : [];
  if (!rows.length || !pages.length) return null;
  const byUrl = new Map(pages.map((page) => [normalizeUrl(page.url), page.url]));
  const items = [];
  const taken = new Set();
  for (const row of rows) {
    const label = typeof row?.label === "string" ? row.label.trim() : "";
    const href = typeof row?.href === "string" ? row.href.split("?")[0] : "";
    // Site-wide, by the count SH432 built for this question. `pages.length` and not the capture's
    // own page count: a page nobody wrote is not a page the menu can be site-wide across.
    if (!label || !href || Number(row.on) < pages.length || row.external) continue;
    const target = byUrl.get(normalizeUrl(href));
    if (!target) continue;
    const slug = slugOf(label) || `item-${items.length + 1}`;
    if (taken.has(slug)) continue;
    taken.add(slug);
    items.push({
      url: `${MENU_FOLDER}/${slug}`,
      type: MENU_ITEM_TYPE,
      folder: MENU_FOLDER,
      data: { label, target: [target] },
    });
  }
  if (!items.length) return null;
  return {
    posts: [
      ...items,
      {
        url: `${MENU_FOLDER}/primary`,
        type: MENU_TYPE,
        folder: MENU_FOLDER,
        // Source order, which is the one ordering a source states outright. `sort` would be this
        // function inventing an editorial decision the capture already recorded.
        data: { title: MENU_TITLE, items: items.map((item) => item.url) },
      },
    ],
    items: items.length,
  };
}

/** A label as a friendly-URL segment — the same alphabet SH79's addresses accept. */
function slugOf(label) {
  return String(label)
    // `\p{M}` and not a codepoint range typed as characters: a combining-mark range written
    // literally is invisible in a diff and one editor's normalisation away from matching nothing.
    .normalize("NFD").replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 40);
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
 *
 * <h3>The guard was on one side of the seam (SH684)</h3>
 *
 * <p>`propose --accept all` already declines a candidate whose REQUIRED field the derivation cannot
 * fill — *"the section would be content `shio verify` refuses"*. Chrome-dropping then emptied that
 * same kind of field on candidates which had passed that guard, and the two decisions could not see
 * each other: acceptance happens before chrome is known, chrome-dropping happens after acceptance is
 * final. Measured on `www.viglet.org` at `542142e0` — four blocks declined for a missing `heading`,
 * then **seven** `required-field-empty` errors on seven *different* ids, produced by the phase that
 * runs after the guard. Every authorable replication of a site with repeated marketing chrome ended
 * red for a reason no phase reported as its own.</p>
 *
 * <p>So a chrome value its type declares required is <b>kept</b>, and the keep is printed the way a
 * drop is. The cost is stated rather than hidden: a section can end up with a heading that reads
 * generically, because it is a heading the source repeated. That is a value a curator sees in the
 * console and edits in one gesture — where the alternative was a replica the gate refuses, on ids no
 * message named. Declining the candidate here instead was the other honest exit, and it spends seven
 * authorable blocks to save seven headings: the worse trade for a replica whose point is being
 * editable.</p>
 */
function withoutChrome(row, chrome, dropped, kept, declined) {
  if (!chrome.size) return { ...row.fields };
  // Read off the model, never off `row.fields`: `fieldsFor` writes a key only where it found a
  // value, so the obligation belongs to the type and the type is what is asked (SH582's rule).
  const required = new Set(SECTION_REQUIRED[row.type] ?? []);
  const hits = new Map();
  for (const [field, value] of Object.entries(row.fields ?? {})) {
    if (typeof value === "string" && chrome.has(`${field}\u0000${value.trim()}`)) {
      hits.set(field, value);
    }
  }

  // The bound, and it is the whole reason this is two passes (SH684). Keeping a required chrome
  // value is only meaningful where the section keeps something of its own: a candidate whose fields
  // are *all* chrome, kept, is the cookie banner SH552 removed from the replica's home page — the
  // fixture that caught it is SH552's own end-to-end test, which went red the moment the keep was
  // unconditional. So a section that would be nothing but chrome is declined instead, which is what
  // `propose` does one phase earlier with the same sentence, and its block stays verbatim.
  const owed = [...hits.keys()].filter((field) => required.has(field)).sort();
  const ownContent = Object.entries(row.fields ?? {}).some(([field, value]) =>
    !hits.has(field) && (typeof value === "string" ? value.trim() !== "" : value != null));
  if (owed.length && !ownContent) {
    declined.push({ section: row.id, type: row.type, fields: owed });
    return null;
  }

  // Second pass in the source's own key order, so two runs over one capture write byte-identical
  // data (P5) — appending the kept values after the loop would order them by chrome-ness instead.
  const data = {};
  for (const [field, value] of Object.entries(row.fields ?? {})) {
    if (!hits.has(field)) {
      data[field] = value;
      continue;
    }
    if (required.has(field)) {
      kept.push({ section: row.id, field, value, type: row.type });
      data[field] = value;
      continue;
    }
    dropped.push({ section: row.id, field, value });
  }
  return data;
}

/**
 * One section's data with every all-blank collection row removed (SH857).
 *
 * <h3>The card with nothing in it</h3>
 *
 * <p>Measured on a replica: `post:VigR7/sections/bcb58dcbb` carried `heading` and
 * `features: [{}, {}]`, and the page drew two grey rectangles under the heading. SH832 is what made
 * it visible rather than what caused it — before that, an empty row resolved `{{TEXT}}` up the
 * context chain, borrowed its parent's title, and drew a card repeating the section heading: wrong,
 * and it looked populated.</p>
 *
 * <h3>Why here and in `propose`, and NOT in the layout</h3>
 *
 * <p>The rationale proposed the layout as the cheaper side. It is the wrong side, for a reason the
 * layout itself states forty lines up: a row is addressed by its **position** —
 * `sh-field="features.{{@index}}.title"`, which `ShPostDraftMergeService` resolves into
 * `data.features[0].title`. A layout that skipped a row would renumber every row after it, so a
 * curator editing the third card through the Universal Editor would write into the second. Filtering
 * has to happen where the array is *written*, or not at all — and Handlebars cannot express "this row
 * has something in it" without a helper, the capability set being closed by SH183.</p>
 *
 * <p>So `propose` stops deriving the row and this stops writing one it is handed anyway. Both, because
 * a proposal is a file that outlives the run that wrote it.</p>
 */
function withoutEmptyRows(data, row, emptied) {
  if (!data) return data;
  const out = {};
  for (const [field, value] of Object.entries(data)) {
    if (!Array.isArray(value)) {
      out[field] = value;
      continue;
    }
    const rows = value.filter((each) => !isBlankRow(each));
    if (rows.length !== value.length) {
      emptied.push({ section: row.id, field, rows: value.length - rows.length, type: row.type });
    }
    // Absent rather than `[]`, which is `withoutChrome`'s own rule for a field: `{{#if features}}`
    // guards the whole list, so an empty array and a missing key render identically and only one of
    // them is a value a curator has to notice and delete.
    if (rows.length) out[field] = rows;
  }
  return out;
}

/** A row map no value of which says anything — the shape that renders as an empty card (SH857). */
function isBlankRow(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return false;
  return Object.values(value).every((each) =>
    each === undefined || each === null || String(each).trim() === "");
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
  "shio-nav",        // the site's own navigation, on a page with no verbatim markup (SH575)
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
 *
 * <h3>Drawing the title and declaring it are two decisions (SH766)</h3>
 *
 * <p>`title` used to be annotated in <b>one</b> place: the `{{#unless content}}` branch, which a
 * mixed page never takes. Measured by SH685's reader on a twelve-page replica, ten of them mixed:
 * `(Page) 0/5` — the page's own title rendered by the captured `<h1>` and editable by nobody, while
 * every section around it was. A curator could rewrite a hero's heading and not the page's title.</p>
 *
 * <p>The conflation was between <em>drawing</em> the title and <em>declaring</em> it. Shio has to
 * draw it only where nothing else does — on a composed page, which has no captured heading — and it
 * has to declare it always. So the branch stays and a second one carries the declaration on the
 * pages the first excludes, in a `hidden` element: the visible title on a mixed page is the source's
 * own, and injecting a second `&lt;h1&gt;` above somebody else's header to make a field reachable
 * would be a worse defect than the one it fixes. The same shape `SectionTestimonial.quotes` already
 * uses on the React side — annotate without drawing — for the same reason.</p>
 */
export const PAGE_BODY_HTML = `{{#unless content}}<nav class="shio-nav" aria-label="Site"><ul>{{#menu title="Primary"}}<li>{{#if url}}<a href="{{url}}">{{name}}</a>{{else}}<span>{{name}}</span>{{/if}}</li>{{else}}{{#query type="Page" sort="title" limit="50"}}<li><a href="{{link}}">{{title}}</a></li>{{/query}}{{/menu}}</ul></nav>{{/unless}}
{{#if content}}{{{content}}}{{/if}}
{{#unless content}}<div class="shio-container"><h1 class="shio-page-title" sh-field="title">{{title}}</h1></div>{{/unless}}
{{#if content}}<div hidden sh-field="title">{{title}}</div>{{/if}}
{{#getRelation sections}}
{{#if html}}<div sh-post="{{id}}" sh-field="html">{{{html}}}</div>{{else}}
<section class="shio-section" sh-post="{{id}}" data-shio-section="{{type}}" data-shio-align="{{align}}" data-shio-variant="{{variant}}" data-shio-columns="{{columns}}">
<div class="shio-container shio-stack">
{{#if heading}}<h2 class="shio-heading" sh-field="heading">{{heading}}</h2>{{/if}}
{{#if subheading}}<p class="shio-subheading" sh-field="subheading">{{subheading}}</p>{{/if}}
{{#if intro}}<p class="shio-intro" sh-field="intro">{{intro}}</p>{{/if}}
{{#if body}}<div class="shio-body" sh-field="body">{{{body}}}</div>{{/if}}
{{#if image}}<p class="shio-media"><img sh-field="image" src="{{file_url image}}" alt="{{heading}}"></p>{{/if}}
{{#if ctaLabel}}<p class="shio-actions"><a class="shio-button" sh-field="ctaLabel" sh-field-attr="ctaHref:href" href="{{ctaHref}}">{{ctaLabel}}</a></p>{{/if}}
{{#if features}}<ul class="shio-grid">{{#each features}}<li class="shio-card" data-shio-row="features" data-shio-icon="{{./icon}}" sh-field-attr="features.{{@index}}.icon:data-shio-icon">{{#if ./title}}<h3 sh-field="features.{{@index}}.title">{{./title}}</h3>{{/if}}{{#if ./body}}<p sh-field="features.{{@index}}.body">{{./body}}</p>{{/if}}</li>{{/each}}</ul>{{/if}}
{{#if questions}}<div class="shio-stack">{{#each questions}}<div class="shio-card" data-shio-row="questions">{{#if ./question}}<h3 sh-field="questions.{{@index}}.question">{{./question}}</h3>{{/if}}{{#if ./answer}}<div class="shio-body" sh-field="questions.{{@index}}.answer">{{{./answer}}}</div>{{/if}}</div>{{/each}}</div>{{/if}}
{{#if logos}}<ul class="shio-grid">{{#each logos}}<li class="shio-media" data-shio-row="logos">{{#if ./image}}<img sh-field="logos.{{@index}}.image" src="{{file_url ./image}}" alt="{{./name}}">{{/if}}{{#if ./href}}<a sh-field="logos.{{@index}}.name" sh-field-attr="logos.{{@index}}.href:href" href="{{./href}}">{{./name}}</a>{{/if}}{{#unless ./href}}<span sh-field="logos.{{@index}}.name">{{./name}}</span>{{/unless}}</li>{{/each}}</ul>{{/if}}
{{#if quotes}}<div class="shio-grid">{{#each quotes}}<blockquote class="shio-card" data-shio-row="quotes">{{#if ./quote}}<p sh-field="quotes.{{@index}}.quote">{{./quote}}</p>{{/if}}{{#if ./avatar}}<img class="shio-media" sh-field="quotes.{{@index}}.avatar" src="{{file_url ./avatar}}" alt="{{./author}}">{{/if}}{{#if ./author}}<cite sh-field="quotes.{{@index}}.author">{{./author}}</cite>{{/if}}{{#if ./role}}<span sh-field="quotes.{{@index}}.role">{{./role}}</span>{{/if}}</blockquote>{{/each}}</div>{{/if}}
</div>
</section>
{{/if}}
{{/getRelation}}`;

/**
 * The three fields the section element carries as **attributes** rather than as a branch (SH830).
 *
 * <p>Spelled out because they are the one shape {@link pageBodyFor} cannot recognise structurally: a
 * `{{#if F}}…{{/if}}` line says which field it is gated on, and ` data-shio-align="{{align}}"` says
 * only that it interpolates something. `type` is beside them and is deliberately *not* here — it is a
 * key `getRelation` puts on every row, so no post-type has to declare it.</p>
 */
const BODY_ATTRIBUTE_FIELDS = Object.freeze(["align", "variant", "columns"]);

/**
 * Every field name the section types this conversion creates declare (SH830).
 *
 * <p>Read from `SECTION_TYPES` — the same table `propose` maps blocks with — plus the row fields a
 * `repeats` names, because `{{#each features}}{{icon}}` is a reference like any other. `html` comes
 * from `SectionMarkup`, which is in no table for SH763's reason: no block maps into it.</p>
 */
/**
 * The types of `vocabulary` that a Page's `sections` array can hold (SH1039).
 *
 * <p>What the generated layout declares as `sh-accepts` on the element composing the sections, and
 * therefore what the editor is told may be added to a converted page. Derived from the same table
 * `sectionFieldsOf` reads rather than listed again: a third copy of the vocabulary is the drift
 * SH245 bans, and this one would be the copy that decides what a curator is offered.</p>
 *
 * <p>`Page` is excluded because a page is not a section of another page, and `Menu`/`MenuItem`
 * because the layout renders those through `{{#menu}}` and not through the array. `SectionMarkup`
 * is in — it is in no table for SH763's reason, and a curator placing a block of kept markup is the
 * case it exists for.</p>
 */
export function sectionTypesOf(vocabulary) {
  return vocabulary.filter((type) => type in SECTION_TYPES || type === MARKUP_TYPE);
}

export function sectionFieldsOf(vocabulary) {
  const names = new Set();
  for (const type of vocabulary) {
    const spec = SECTION_TYPES[type];
    if (!spec) continue;
    for (const field of spec.fields) names.add(field);
    for (const field of spec.repeats?.of ?? []) names.add(field);
  }
  if (vocabulary.includes(MARKUP_TYPE)) names.add("html");
  return names;
}

/**
 * {@link PAGE_BODY_HTML} with the branches this conversion has no type for removed (SH830).
 *
 * <h3>The tool warning about the file the tool just wrote</h3>
 *
 * <p>The generated layout is one template with a branch per field across the whole vocabulary, and
 * `convert` creates only the types a capture actually produced. A replication onto an **empty**
 * instance — 66 objects, exit 0, four routes 200 — therefore ended with two of its four `verify`
 * warnings against the layout `convert` had written seconds earlier:</p>
 *
 * <pre>! template-unknown-field post:VigR3/theme/layout · align
 * ! template-unknown-field post:VigR3/theme/layout · subheading</pre>
 *
 * <p>Both are `SectionHero` fields, and that capture accepted no hero. The warning is correct — it
 * lists what `{{#if}}` does provide and says why it is a warning — and it is **guaranteed**: any
 * conversion not using every section type warns, and the count measures what the source happened to
 * contain rather than anything wrong. A warning nobody can act on is what teaches a reader to stop
 * reading warnings, which is the one thing this suite's value rests on.</p>
 *
 * <h3>Filtered here rather than authored per conversion</h3>
 *
 * <p>`PAGE_BODY_HTML` stays a **constant with no interpolation** — SH464's seam, the bytes
 * `ShConvertedLayoutRenderTest` slices out of this file and renders through the real engine. Building
 * the body by concatenation would have left that test reading a fragment, so the complete template is
 * still the thing under test and this removes from it. The other way out — creating the vocabulary
 * whole so every branch resolves — provisions sixteen post-types a site does not use, which is the
 * opposite of the direction SH763 chose.</p>
 *
 * <p>Two shapes and nothing else, both asserted by `convert.test.mjs` so a template edit that leaves
 * this reader behind fails rather than silently keeping every branch: a line that is one whole
 * `{{#if F}}…{{/if}}`, and a line that opens `{{#if F}}…{{else}}` whose `{{/if}}` stands alone
 * later — the markup branch, whose *else* arm is the authorable page and must survive. Only inside
 * `{{#getRelation sections}}`: outside it the references are the `Page`'s own fields, which this
 * function knows nothing about and must not judge.</p>
 */
export function pageBodyFor(fields) {
  const declared = (name) => fields.has(name);
  const lines = PAGE_BODY_HTML.split("\n");
  const kept = [];
  let inSections = false;
  let closing = 0;
  for (const line of lines) {
    if (line.startsWith("{{#getRelation sections}}")) inSections = true;
    if (!inSections) {
      kept.push(line);
      continue;
    }
    if (line === "{{/if}}" && closing > 0) {
      closing -= 1;
      continue;
    }
    const whole = /^\{\{#if (\w+)\}\}.*\{\{\/if\}\}$/.exec(line);
    if (whole && !declared(whole[1])) continue;
    const opens = /^\{\{#if (\w+)\}\}.*\{\{else\}\}$/.exec(line);
    if (opens && !declared(opens[1])) {
      closing += 1;
      continue;
    }
    kept.push(strippedAttributes(line, declared));
    if (line.startsWith("{{/getRelation}}")) inSections = false;
  }
  return kept.join("\n");
}

/** ` data-shio-align="{{align}}"` and its two siblings, gone where nothing declares the field. */
function strippedAttributes(line, declared) {
  let stripped = line;
  for (const field of BODY_ATTRIBUTE_FIELDS) {
    if (declared(field)) continue;
    stripped = stripped.split(` data-shio-${field}="{{${field}}}"`).join("");
  }
  return stripped;
}

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
 * <h3>And the two lines the lint asked for on every replica this ever produced (SH593)</h3>
 *
 * <p>Every conversion ended with `head-no-canonical` and `head-no-description` against
 * `post:<site>/theme/layout`. Both findings were **correct** — SH337 put `post.canonical` on the
 * model precisely so a template can emit it — and both were <b>unclearable by construction</b>,
 * because the layout they scold is written by this function and the next run regenerates the same
 * file and reports the same two. Shio telling itself off. The cost was never the two lines: a
 * warning list is read for as long as it is actionable, and these headed every list for twelve
 * rounds, including the round whose whole report was `0 error(s), 2 warning(s)` — the first fully
 * green verify on a real replica, whose entire remaining output was noise nobody could act on.
 * SH421 is the same shape from the other side, and the pair is worth reading together.</p>
 *
 * <p><b>`{{post.summary}}` rather than `{{description}}`</b>, though the standard `Page` declares a
 * `description` field with `isSummary: true` and the words "used as the page summary and the meta
 * description" in its own definition. `post.summary` is a key of the render frame itself, present
 * whatever the type declares, so the tag is empty on a `Page` somebody built by hand instead of the
 * write failing — and on the type this chain actually writes against, `post.summary` <i>is</i> that
 * field. A layout that renders for one shape of `Page` and 500s for another would be a worse defect
 * than the warning it removes.</p>
 *
 * <p>What makes the tag carry something is the other half: `composePages` now writes the
 * `description` the capture already recorded. The value was scanned, stored in the inventory and
 * dropped at the conversion, so the meta tag would have been honestly empty on every page — which
 * is lint-satisfying theatre, and one level up it is the same defect as the warning.</p>
 *
 * <p>What is deliberately **not** declared is `align`, `variant` and `columns`. Those are drawn into
 * attributes too, and they are `Combo Box` fields — a closed set of choices — while the bridge's
 * edit surface is a text box. Offering a curator a box in which three strings work and the rest
 * silently do nothing is worse than sending them to the console form, so they stay the form's job in
 * the same way adding a row does. `ShConvertedLayoutRenderTest` holds that exemption and asserts its
 * premise: a presentation field that becomes free text fails there rather than staying exempt.</p>
 */
function pageLayout(markup, site, theme, capturedFor = false, fields = null, accepts = null) {
  const lang = langOf(markup);
  // SH830 — every branch when the caller names no vocabulary, which is what the older callers and
  // the fixtures expect; filtered to what the conversion's own types declare when it does.
  const body = fields ? pageBodyFor(fields) : PAGE_BODY_HTML;
  // SH1039 — what may be added here. `{{#getRelation sections}}` is a block and not an element, so
  // the element composing the sections is `<body>` itself, which is where the server's pass finds
  // the container and therefore where the declaration belongs. Written only when the caller named a
  // vocabulary: an older caller's layout is the bytes it was (P5), and an absent declaration is
  // "nothing is known to fit", never "anything".
  const declares = accepts && accepts.length ? ` sh-accepts="${accepts.join(" ")}"` : "";
  return {
    url: FIDELITY_LAYOUT_URL,
    type: "PageLayout",
    folder: THEME_FOLDER,
    data: {
      TITLE: generatedLayoutTitle(site),
      // A THEME naming a post the site does not have is a marked failure at render time
      // (`data-shio-theme-missing`), so it is named only when that theme post is in this document.
      ...(theme ? { THEME: theme } : {}),
      HTML: `<!doctype html>
<html${lang ? ` lang="${lang}"` : ""}>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{{post.title}}</title>
${HEAD_CONVENTIONS_HTML}
<style data-shio-theme="{{theme.title}}">{{theme.css}}</style>
${capturedFor ? `${CAPTURED_STYLE_HTML}\n` : ""}</head>
<body class="shio-page"${declares}>
${body}
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
/**
 * The two tags the head lint asked for on every replica this ever produced (SH593).
 *
 * <p>Its own export, and a single-quoted literal with no interpolation, for SH464's reason: the
 * rule this satisfies is enforced by `ShHeadConventions` in the **JVM**, so the only honest
 * assertion is the Java one running that lint over *these bytes*. A second copy in a test would
 * pass forever after this line changed, which is the failure mode of every inventory that lives
 * beside the thing it describes rather than inside it.</p>
 */
export const HEAD_CONVENTIONS_HTML =
  '<meta name="description" content="{{post.summary}}">\n<link rel="canonical" href="{{post.canonical}}">';

/**
 * The page that carries verbatim markup and no `content` (SH772).
 *
 * <h3>What `content` stopped answering</h3>
 *
 * <p>`{{#if content}}` was the whole of "this page kept the source's markup" when SH549 shipped. It
 * is not any more: SH763 moved every run after the first section into `SectionMarkup` posts, so a
 * page whose `<body>` <b>begins</b> with an accepted block has no leading run at all — no `content`,
 * no captured stylesheet, and the source's own class names rendered against a sheet that never
 * arrived. Reproduced before it was fixed: `content` absent, one markup section, `mixed: 1`.</p>
 *
 * <h3>What a `{{#if}}` in a head can see, which is the question SH772 asked</h3>
 *
 * <p>The page's own fields, and any post it can name by a **literal** address — which is how the
 * line above resolves the captured theme at all. What it cannot do is ask an <em>aggregate</em>
 * question about a relation: `getRelation` yields each row as a plain context with <b>no data
 * frame</b> (`options.fn(row)`), so there is no `@first` or `@index` to fire once on, and
 * Handlebars has no `any`. That rules out both shapes that would have been cheaper than this one,
 * and it is why the second branch emits per row rather than once.</p>
 *
 * <h3>The bound, stated</h3>
 *
 * <p>The typical count is <b>one</b>: this shape means the accepted blocks start at the top, and a
 * capture's accepted blocks are contiguous (the observation SH763's own rationale rests on), so the
 * kept markup is the single run after the last of them — one copy, exactly what the first branch
 * emits today. More than one needs markup <em>between</em> two accepted blocks on a page with no
 * leading run, and `describeConversion` says so when it writes one, because a megabyte repeated is
 * a payload regression and a reviewer should hear it from the command that caused it (P9).</p>
 *
 * <h3>It says whose sheet it is (SH829)</h3>
 *
 * <p>`data-shio-theme="{{title}}"` is the captured `Theme` post's own title, which `getRelation`
 * already puts on every row — so a replica's region table prints `VigR3 (captured)` beside the
 * layout's `VigR3 (Shio)` instead of `style[2]` beside `style[1]`, and a reader stops having to make
 * a second call to learn which ordinal is which. An **attribute**, because a title is content and
 * Handlebars escapes an attribute value; HTML's own `title` on a `<style>` would have been the
 * obvious spelling and is the wrong one — it declares an *alternate stylesheet set*, and two sheets
 * with different titles means the browser paints with one of them.</p>
 */
export const CAPTURED_STYLE_HTML =
  '{{#if content}}{{#getRelation "/theme/captured"}}'
  + '<style data-shio-theme="{{title}}">{{CSS}}</style>{{/getRelation}}{{/if}}'
  + '{{#unless content}}{{#getRelation sections}}{{#if html}}'
  + '{{#getRelation "/theme/captured"}}'
  + '<style data-shio-theme="{{title}}">{{CSS}}</style>{{/getRelation}}'
  + '{{/if}}{{/getRelation}}{{/unless}}';

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
/**
 * The site's front door, when the source did not have one (SH576).
 *
 * <h3>What was measured</h3>
 *
 * <p>`post:<site>/` is a site's home page — the one address SH433 exists for because the SH79
 * grammar does not spell it. A source that answers `/` gives the conversion a home page for free. A
 * source that does not gives it nothing, **and nothing is what was written**. The `locale-prefixed`
 * archetype is the case and it is an ordinary shape rather than an exotic one: every page lives
 * under `/en-us/` or `/pt-br/`, `/` is a 404 at the source, and the conversion wrote pages at both
 * prefixes and no page at `/`. So `/sites/<site>/default/en-us/` — the first URL anybody opens, and
 * the one the console frames — answered nothing, while the run reported every page it wrote and
 * every one of them was correct.</p>
 *
 * <h3>Three answers were defensible; the current one was not</h3>
 *
 * <p>XVII.81 named them: redirect to whatever the source's own root redirected to, write a home page
 * that lists what is there, or refuse and say which page to make the front door. **Writing neither a
 * page nor a note is the one that is not**, and that is what this replaces.</p>
 *
 * <p>The first is unavailable for this archetype rather than rejected, and since SH563 the reason is
 * the source's rather than the capture's: the inventory now carries `redirectedTo` on any row the
 * crawl reached through a hop, so "the locale the source redirected to" is a fact a run *can* hold —
 * but a source whose `/` **404s** never redirected anywhere, so no row carries it and there is
 * nothing to reproduce. Inventing the target from the first path would still be a guess wearing a
 * redirect's clothes. The case where `/` does 301 does not reach here at all: the crawl records a
 * page at `/`, so the `PAGES_FOLDER` check below returns null and the source's own front door is
 * written.</p>
 *
 * <p>So the default is the second and `--home` is the third, **turned from a refusal into an
 * instruction** (P6). A refusal would stop a conversion in which every page is right over a page the
 * source never had; the note names the flag instead, and a run nobody reads still produces a site
 * with a root.</p>
 *
 * <h3>Why an empty page is a real front door and not a stub</h3>
 *
 * <p>A `Page` with a title, no `content` and no `sections` renders the layout's `{{#unless content}}`
 * branch — which since SH575 is the site's own navigation over `{{#query type="Page"}}`. So the
 * generated home page lists every page of the replica and is a place a reader can start from, with
 * no content invented on the source's behalf: P7's rule, since the alternative shapes all put words
 * on a page nobody wrote.</p>
 *
 * @param pages the composed pages, before this
 * @param site the site name, which is the only title this can honestly give a page nobody wrote
 * @param home the reviewer's `--home <path>`, or null
 * @returns `{post, home}` when one had to be written, `null` when the source already answers `/`
 */
function homePost(pages, site, home) {
  if (pages.some((page) => page.url === PAGES_FOLDER)) return null;
  if (!pages.length) return null; // Nothing to be the front door *of*.

  if (home) {
    // Matched on the path with a trailing slash ignored, and the **page's own** spelling is what
    // gets written: a source's paths are its own (`/en-us/` carries the slash) and a redirect to a
    // path that differs from the post's friendly URL by one character is a redirect to nothing.
    const asked = trimPath(home);
    const target = pages.find((page) => trimPath(page.url) === asked)?.url;
    if (!target) {
      throw new Error(
        `shio convert: --home ${home} is not a page this conversion writes.\n`
          + `  allowed: ${pages.map((page) => page.url).slice(0, 12).join(", ")}`
          + `${pages.length > 12 ? `, … ${pages.length - 12} more` : ""}\n`
          + "  why: the front door redirects to one of the replica's own pages, and a redirect to a\n"
          + "       path nothing serves is a 404 with an extra hop. Omit --home to get a home page\n"
          + "       listing the site's pages instead.",
      );
    }
    return {
      post: {
        url: PAGES_FOLDER,
        type: REDIRECT_TYPE,
        folder: PAGES_FOLDER,
        // Not permanent: a reviewer's first answer to "which page is the front door" is a decision
        // they may revise, and a 301 is the one redirect a browser caches past the correction.
        data: { title: `${site} (home)`, [REDIRECT_TO]: target },
      },
      home: { as: "redirect", to: target },
    };
  }
  return {
    post: {
      url: PAGES_FOLDER,
      type: PAGE_TYPE,
      folder: PAGES_FOLDER,
      // No `content` and no `sections`: the layout's own `{{#unless content}}` branch is the page.
      data: { title: site },
    },
    // `+ 1` for itself: the nav is `{{#query type="Page"}}` over the folder this page is in, so the
    // home page is one of its own rows. A count that excluded it would be a number the reader can
    // see is wrong the moment they open the page.
    home: { as: "page", lists: pages.length + 1 },
  };
}

/** `en-us`, `/en-us` and `/en-us/` are one request — the leading slash and the trailing one off. */
function trimPath(path) {
  return `/${String(path).trim().replace(/^\/+/, "").replace(/\/+$/, "")}`;
}

function composePages(proposal, sectionUrls, markup = new Map(), annotate = false) {
  const pages = [];
  const markupPosts = [];
  const taken = new Set();
  // SH858 — the overlays cut out of the kept markup, per page.
  const obstructions = [];
  // SH868 — the pages whose kept markup was annotated in place, and how many fields each gained.
  const annotations = [];
  // SH871/SH872 — the distinct names, across every page. A name carries the block hash it came
  // from and one block signature recurs across pages by construction — that is what made it a
  // candidate — so eleven annotated pages address 40 distinct names in 92 places. Both numbers are
  // true: this is the first, and `annotations` above carries the second per page.
  const declaredNames = new Set();
  for (const page of proposal?.pages ?? []) {
    const seen = new Set();
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
      // structural collision. A repeat is therefore a cut with **no url**, and SH763's interleaving
      // reads that as "join the markup across this hole" rather than as a place to put a section.
      cut.push({ span: page.spans?.[at] ?? null, url: seen.has(url) ? null : url });
      seen.add(url);
    });
    // Document order, which is the order the interleaved markup has to be read in. `page.blocks` is
    // already in it; sorting says so rather than assuming it, and costs nothing on the shape where
    // a span is missing — that page falls back to the single-part form either way.
    if (cut.every((each) => Array.isArray(each.span))) {
      cut.sort((a, b) => a.span[0] - b.span[0]);
    }
    const sections = cut.map((each) => each.url).filter(Boolean);
    // SH868 — the mapping spent the other way, on a page whose markup is all one post's. See
    // `annotateVerbatim` for why that is the boundary rather than an oversight.
    const html = markup.get(page.path);
    // SH868 — asked for, never assumed. `source-corpus.test.mjs` holds that *fidelity is defined by
    // having no editable field*, and it caught this on the run that added it: a page annotated by
    // default would move the mode boundary silently and falsify what the skill file says about
    // `data-shio-prop`. This is a **third mode**, so it is a flag — plain fidelity is byte-for-byte
    // what it was, and `--annotate` is how a caller asks for the source's pixels AND its fields.
    const annotated = sections.length || !annotate
      ? { stamps: [], fields: {} }
      : annotateVerbatim(html, page);
    const stamped = Object.keys(annotated.fields);
    if (stamped.length) {
      annotations.push({ path: page.path, fields: stamped.length });
      // SH872 — the names, once each, and nothing more. SH871 emitted a `requires`-shaped row per
      // name because SH868 had made each of them a field on `Page`; the storage is the markup
      // again, so there is nothing for the instance to declare and a row saying so would be a
      // requirement that is not one. Kept as a list because `--json` has a reader and "which
      // blocks did this page make editable" is the question `shio annotations` asks live.
      for (const field of stamped) declaredNames.add(field);
    }
    // SH858 — the overlays whose dismiss behaviour this capture does not hold. Counted per page, so
    // the report names the pages rather than a total nobody can locate.
    const drop = overlaysToDrop(html, page.overlays, cut);
    if (drop.length) obstructions.push({ path: page.path, overlays: drop.length });
    const body = contentFor({ html, sections, kept, cut, drop, stamps: annotated.stamps });
    if (!sections.length && body === null) continue;
    // SH763 — the kept markup, interleaved. `body` is a string when there is one part (a wholly
    // verbatim page, or a split this could not place), and a list of parts when there are more.
    const composed = interleave({ body, cut, page, taken });
    composed.posts.forEach((post) => markupPosts.push(post));
    pages.push({
      url: page.path,
      type: PAGE_TYPE,
      folder: PAGES_FOLDER,
      // `title` is required on `Page`, and the capture recorded one. The path is the fallback rather
      // than the site name: it is what makes two untitled pages two rows a human can tell apart.
      data: {
        title: titleOf(page),
        // SH593 — the capture read this off the source's own `<meta name="description">` and the
        // conversion dropped it. `description` is `isSummary` on the `Page` this chain writes
        // against, so it is what `{{post.summary}}` renders into the layout's meta tag. Absent when
        // the source declared none, because an empty key is a value and "the source said nothing"
        // is not one.
        ...(page.description ? { description: page.description } : {}),
        // SH763 — the accepted sections with the kept markup between them, in render order. What
        // used to be `contentAfter` is a `SectionMarkup` post in this list, so the order is the
        // list's and N splits work instead of one.
        ...(composed.sections.length ? { sections: composed.sections } : {}),
        // `content` keeps its meaning and its place: the markup that stands **before** the first
        // section, which the layout emits above the loop, and the whole body on a page nobody
        // accepted anything on. It is what `{{#if content}}` in the head asks to decide whether this
        // page needs the captured stylesheet, and a fidelity replica is byte-identical to before.
        ...(composed.content === null ? {} : { content: composed.content }),
        // SH872 — and NOT the annotated values. SH868 proposed that the annotation is the field and
        // the markup is the storage, then wrote each element's own text into a key on `Page` as
        // well — which put a field per captured block on the type every page in the instance
        // shares: 4 fields became 44, and `shio annotations` reported 30 of them NOT editable on
        // the home page, 27 belonging to other pages' blocks. The text is already in `content`,
        // and `ShRenderModel` substitutes only where a stored value DIFFERS from what the markup
        // says (SH870) — so an unedited annotation storing nothing is not a gap, it is the whole
        // mechanism. A curator's edit stores the one key they touched, and `ShAnnotatedFields`
        // is what stops the two readers of the model calling it undeclared.
      },
    });
  }
  return {
    pages: pages.sort((a, b) => (a.url < b.url ? -1 : 1)),
    // Sorted for the reason every other list here is (P5): two conversions of one capture are the
    // same bytes, whatever order the proposal listed its pages in.
    markup: markupPosts.sort((a, b) => (a.url < b.url ? -1 : 1)),
    obstructions: obstructions.sort((a, b) => (a.path < b.path ? -1 : 1)),
    annotations: annotations.sort((a, b) => (a.path < b.path ? -1 : 1)),
    // SH872 — the distinct fields this run's markup makes editable, as names. It was a list of
    // `requires` rows (SH868) and then a list this run declared (SH871), and it is neither now:
    // the markup that carries the annotation is the storage, so the instance is owed nothing and
    // there is no post-type to name. Sorted for the reason every other list here is (P5).
    annotationFields: [...declaredNames].sort((a, b) => (a < b ? -1 : 1)),
  };
}

/** The post-type a run of kept markup is written as (SH763). */
export const MARKUP_TYPE = "SectionMarkup";

/**
 * The kept markup and the accepted sections, in the order the source had them (SH763).
 *
 * <h3>What this replaces</h3>
 *
 * <p>SH693 wrote the trailing half into a `Page.contentAfter` field, and **no `Page` declared one** —
 * so every conversion against a real instance was refused at the batch with `'Page' does not declare
 * a field named 'contentAfter'`. Declaring it would have been the wrong repair: it encodes position
 * rather than meaning, and it admits exactly one split, so markup standing *between* two accepted
 * blocks had nowhere to go and was silently grouped with the rest.</p>
 *
 * <p>Here the markup between two sections is a section — one `SectionMarkup` post per run — so the
 * page's own list carries the order, N splits work, and a curator can move or replace a raw block
 * from the same control they compose the rest of the page with.</p>
 *
 * <h3>The first part is still `content`</h3>
 *
 * <p>It is the source's header and nav, it renders above the loop, and it is the condition the head
 * reads to decide whether this page needs the captured stylesheet. Moving it too would have made a
 * fidelity replica a different document for no gain.</p>
 *
 * @param body a string (one part) or the list `splitAroundSections` produced
 * @param cut  the accepted blocks in document order, a `null` url meaning a repeat occurrence
 * @returns `{content, sections, posts}` — the field (`null` for none), the list, and its posts
 */
function interleave({ body, cut, page, taken }) {
  const listed = cut.map((each) => each.url).filter(Boolean);
  // One part, which is every shape but the interleaved one: a wholly verbatim page (no sections at
  // all), a page whose blocks were all accepted (`null`), or a split this could not place, which
  // falls back to the leading half rather than losing the remainder.
  if (!Array.isArray(body)) {
    return { content: typeof body === "string" ? body : null, sections: listed, posts: [] };
  }
  const posts = [];
  const sections = [];
  // `null` rather than `""`, and the leading part is trimmed for emptiness the way SH693's was: a
  // field holding whitespace is a value a curator has to notice and delete, and `{{#if content}}` —
  // which decides both the nav branch and the captured stylesheet — reads whitespace as true.
  let content = null;
  let buffer = body[0] ?? "";
  let placed = false;
  cut.forEach((each, at) => {
    const next = body[at + 1] ?? "";
    if (!each.url) {
      // A repeat occurrence: the section is already in the list, so the markup on either side of the
      // hole is one run. Joining is what keeps the page's bytes in the order the source wrote them.
      buffer += next;
      return;
    }
    if (!placed) {
      content = buffer.trim() ? buffer : null;
      placed = true;
    } else if (buffer.trim()) {
      const post = markupPost(page, posts.length + 1, buffer, taken);
      posts.push(post);
      sections.push(post.url);
    }
    sections.push(each.url);
    buffer = next;
  });
  if (placed && buffer.trim()) {
    const post = markupPost(page, posts.length + 1, buffer, taken);
    posts.push(post);
    sections.push(post.url);
  }
  return { content, sections, posts };
}

/**
 * One run of kept markup as a post, addressed by the page it came from (SH763).
 *
 * <p>The address is derived rather than hashed so a reviewer reading `/sections/markup-about-2` in
 * the console knows which page it belongs to and where in it — the one thing a curator needs from a
 * block that has no title of its own. Two paths can slug to one name (`/a/b` and `/a-b`), and a
 * collision would be an upsert overwriting the first run with the second: silent content loss. So
 * the taken set disambiguates, deterministically, in page order (P5).</p>
 */
function markupPost(page, index, html, taken) {
  const slug = String(page.path ?? "")
    .trim()
    .replace(/^\/+/, "")
    .replace(/\/+$/, "")
    .replace(/[^A-Za-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase() || "home";
  let url = `${SECTIONS_FOLDER}/markup-${slug}-${index}`;
  for (let n = 2; taken.has(url); n += 1) {
    url = `${SECTIONS_FOLDER}/markup-${slug}-${index}-${n}`;
  }
  taken.add(url);
  return { url, type: MARKUP_TYPE, folder: SECTIONS_FOLDER, data: { html } };
}

/**
 * One page's verbatim half: the whole body, the remainder around its sections, or nothing (SH520).
 *
 * @param kept how many of the page's blocks were left in fidelity mode
 * @param cut the accepted blocks in document order, `{span, url}` — a span is `null` for one the
 *            proposal cannot place
 */
function contentFor({ html, sections, kept, cut, drop = [], stamps = [] }) {
  // SH868 — one walk over the original offsets, because both edits index them: a drop removes bytes
  // and a stamp inserts an attribute, and applying either first moves the other's addresses. Only
  // this branch can carry stamps — a page with sections is one `annotateVerbatim` declines.
  if (!sections.length) {
    return bodyOf(spliceAll(html, [...drop.map((span) => ({ span, mark: "" })), ...stamps]));
  }
  // Everything was accepted: the sections are the page, and there is no remainder. Writing the body
  // here would render every block twice.
  if (!kept) return null;
  // A span the proposal does not carry is a block this cannot cut, and cutting the others would
  // leave that one rendered both as markup and as a section. Refusing the remainder is the honest
  // half-answer, and it is exactly what the previous release wrote.
  if (!cut.length || cut.some((each) => !each.span)) return null;
  return splitAroundSections(html, cut, drop);
}

/**
 * The overlay spans on a page that has no behaviour to dismiss them with (SH858).
 *
 * <h3>The measurement</h3>
 *
 * <p>The replica served `www.viglet.org`'s cookie banner: painted correctly, fixed over the content,
 * with **Decline** and **Accept analytics** buttons and a close cross, and zero `onclick` or
 * `addEventListener` in that region on a page carrying **no `&lt;script src&gt;` at all**. `clone`
 * predicted it in those words — *"the replica will render and not respond — a handler, a toggle, a
 * modal that will not close"* — and predicting a defect is not the same as it being acceptable. This
 * one is a permanent obstruction of the page's own content, and every reader meets it.</p>
 *
 * <h3>Why an overlay and not chrome in general</h3>
 *
 * <p>`propose` already refuses these as candidates, in the words this reads back: *an overlay is
 * painted over the page rather than part of it*. An overlay is the one kind of chrome **whose entire
 * purpose is behaviour** — it exists to appear, be answered, and go away — so keeping its markup and
 * dropping its script keeps the one part that has no value alone. And a consent banner that cannot
 * record consent is not a consent banner: shipping one that looks functional and stores nothing is
 * worse than shipping none, which is the reading SH421 already gives a dropped site script.</p>
 *
 * <h3>The condition is a script anywhere on the page, and deliberately that coarse</h3>
 *
 * <p>Whether *this* overlay's dismiss handler was captured is not decidable from markup — the code is
 * in a file, minified, and this instance does not run a browser. What is decidable is whether the page
 * carries any behaviour at all, and a page with none cannot dismiss anything. So a page that captured
 * scripts keeps its overlay untouched: dropping content from a page that does carry behaviour would be
 * the over-reach, and `--scripts` remains how a caller asks for the working one.</p>
 */
function overlaysToDrop(html, overlays, cut) {
  if (typeof html !== "string" || !Array.isArray(overlays) || !overlays.length) return [];
  if (/<script\b/i.test(html)) return [];
  const inside = cut.map((each) => each.span).filter(Array.isArray);
  return overlays
    .filter((span) => Array.isArray(span) && Number.isInteger(span[0]) && Number.isInteger(span[1]))
    // An overlay standing inside an accepted block is not in the kept markup, and cutting it would
    // make two spans overlap — which `splitAroundSections` correctly refuses by falling back, losing
    // the drop and the interleaving together.
    .filter((span) => !inside.some(([start, end]) => span[0] < end && start < span[1]))
    .sort((a, b) => a[0] - b[0]);
}

/**
 * The field a verbatim block's annotation names, and it is invented on purpose (SH868).
 *
 * <p>A wholly verbatim page is **one post** holding many blocks, so the roles collide: two heroes
 * both derive `heading`, and one field cannot hold two elements' text. The name therefore carries the
 * block it came from — `b1a2c3dHeading` — which is unique by construction and traceable back to the
 * candidate a reviewer read, which is SH380's rule that a token nobody can trace to the source is a
 * guess. Alphanumeric because it becomes a post-type field name.</p>
 */
export const verbatimField = (block, role) =>
  `${block}${role.charAt(0).toUpperCase()}${role.slice(1)}`;

/**
 * `sh-field` stamped onto the nodes `propose` derived a value from (SH868).
 *
 * <h3>The mapping spent the other way</h3>
 *
 * <p>`propose` already knows which node became `heading` and which became `intro`. That mapping is
 * spent one way today — read the values out, write them into a new post, render that post through a
 * Shio template — and everything hard about replication follows from the last step: the palette
 * bridge and its elected accent, a generated layout branching over types, a hero losing its second
 * button because `SectionHero` declares one. Spend it the other way and the markup is the source's.</p>
 *
 * <h3>Wholly verbatim pages only, and that is a boundary rather than an oversight</h3>
 *
 * <p>A page with accepted sections splits its kept markup into runs, each a `SectionMarkup` post
 * (SH763), and the fields have to live on <b>the post holding the bytes they annotate</b> — so an
 * offset would first have to be mapped to a run. A page with no accepted block is one post and one
 * span of bytes, which is the case SH864 measured (12 of 12 fidelity) and the one the mode is about.
 * A mixed page keeps exactly what it kept before.</p>
 *
 * <h3>Edits rather than a rewritten string, and that is not a style choice</h3>
 *
 * <p>The first draft returned stamped markup, and the overlay drop stopped working: `page.overlays`
 * and the section spans index the page's **original** bytes, and inserting an attribute moves every
 * offset after it. So this returns the edits and {@link contentFor} applies them in the same single
 * ascending walk that marks the sections and cuts the overlays — the rule SH858 already had to
 * learn, met again one edit kind along.</p>
 *
 * @returns `{stamps, fields}` — the insertions to make, and the values those annotations address
 */
function annotateVerbatim(html, page) {
  const spans = [];
  const fields = {};
  (page?.blocks ?? []).forEach((block, at) => {
    const derived = page?.fieldSpans?.[at];
    if (!derived) return;
    for (const [role, span] of Object.entries(derived).sort()) {
      const open = openTagEnd(html, span);
      if (open === null) continue;
      const name = verbatimField(block, role);
      spans.push({ span: [open, open], mark: ` ${FIELD_ATTR}="${name}"` });
      // The value is the element's own text, so nothing renders differently until a curator edits
      // it — `ShRenderModel` substitutes only where the field DIFFERS from what the markup says
      // (SH870), which is what keeps annotating free in pixels.
      fields[name] = textAt(html, span);
    }
  });
  return { stamps: spans, fields };
}

/** The attribute `sh-field` is spelled as, shared with the renderer's own constant. */
const FIELD_ATTR = "sh-field";

/** Where an element's open tag ends, so an attribute can be inserted before the `>`. */
function openTagEnd(html, [start, end]) {
  if (typeof html !== "string" || !Number.isInteger(start) || start < 0 || end > html.length) {
    return null;
  }
  const close = html.indexOf(">", start);
  if (close < 0 || close >= end) return null;
  // `<img … />` — the attribute goes before the slash, or the markup gains ` sh-field="x"/`.
  return html[close - 1] === "/" ? close - 1 : close;
}

/** The element's visible text, collapsed the way `propose` collapsed it when it read the value. */
function textAt(html, [start, end]) {
  return html.slice(start, end).replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

/** Ascending, so an earlier insertion cannot move a later offset. */
function spliceAll(html, edits) {
  // A page with no markup on disk reaches here with `undefined`, which `without` has always guarded
  // and this inherited responsibility for the moment it replaced that call on the no-sections path.
  if (typeof html !== "string" || !edits.length) return html;
  const ordered = [...edits].sort((a, b) => a.span[0] - b.span[0]);
  let out = "";
  let cursor = 0;
  for (const { span: [start, end], mark } of ordered) {
    if (start < cursor) continue;
    out += html.slice(cursor, start) + mark;
    cursor = end;
  }
  return out + html.slice(cursor);
}

/** Where the sections go, marked in the bytes before anything is cut out of them (SH693). */
const SECTIONS_MARK = "\u0000shio:sections\u0000";

/**
 * The kept markup as **N + 1** parts, one per gap between the accepted blocks (SH693, SH763).
 *
 * <h3>Concatenation is not composition</h3>
 *
 * <p>The generated layout emitted `{{{content}}}` whole and then appended every section, so there
 * was no position at which a section could land inside the kept markup. On a mixed page the source's
 * header, nav <b>and footer</b> therefore all preceded every section — by construction, on every
 * such page, in every replication. Measured by offset in the served home page (1,113,507 chars):
 * header at 95%, footer at 98%, then sections #1–#8 to 99%.</p>
 *
 * <p>The information was never missing: `cut` is where each accepted block stood, and this function
 * used to remove those spans and glue the remainder into one string. Splitting instead of gluing is
 * the whole change.</p>
 *
 * <h3>The approximation SH693 stated, and which SH763 removed</h3>
 *
 * <p>SH693 produced <b>two</b> parts, because the layout had exactly two fields to put them in, and
 * said so: markup that sat <em>between</em> two accepted blocks could not be placed. That was the
 * cheap two-thirds of the answer — chrome is overwhelmingly outside the run of blocks, which is what
 * makes it chrome — and the remaining third is now free, because the parts no longer land in fields.
 * A run of markup is a `SectionMarkup` post in the page's own `sections` list, so there is a place
 * for every gap and one mark per cut is all this has to emit.</p>
 *
 * <h3>Why the marks go in before the cut</h3>
 *
 * <p>The spans address the original bytes, so this walks them in ascending order and rebuilds the
 * document as it goes — each block's bytes replaced by the mark that stands for it. Cutting first
 * and marking after would leave every offset past the first hole wrong by the size of the holes
 * before it, which is the class of bug that reads as "the footer moved a bit".</p>
 *
 * @returns the parts, or a single string when the split could not be placed
 */
function splitAroundSections(html, cut, drop = []) {
  if (typeof html !== "string") return bodyOf(html);
  const spans = cut.map((each) => each.span);
  // SH858 — one ascending walk over both kinds of span, because both address the *original* offsets:
  // a section's bytes become a mark and an overlay's become nothing. Cutting the overlays in a second
  // pass would leave every section span past the first hole wrong by the size of the holes before it,
  // which is the class of bug SH693's own header calls "the footer moved a bit".
  const marks = [
    ...spans.map((span) => ({ span, mark: SECTIONS_MARK })),
    ...drop.map((span) => ({ span, mark: "" })),
  ].sort((a, b) => a.span[0] - b.span[0]);
  let marked = "";
  let cursor = 0;
  for (const { span: [start, end], mark } of marks) {
    // Overlapping, reversed or out-of-range spans are a proposal this cannot cut faithfully. The
    // fallback is the pre-SH693 answer — the remainder in one part — rather than a page missing it.
    if (!Number.isInteger(start) || !Number.isInteger(end)
      || start < cursor || end < start || end > html.length) {
      return bodyOf(without(html, spans));
    }
    marked += html.slice(cursor, start) + mark;
    cursor = end;
  }
  marked += html.slice(cursor);
  const body = bodyOf(marked);
  const parts = typeof body === "string" ? body.split(SECTIONS_MARK) : [];
  if (parts.length !== spans.length + 1) {
    // A mark fell outside <body> — a block accepted from the head, which the vocabulary does not
    // produce. Fall back to the single-part form rather than losing the remainder.
    return bodyOf(without(html, spans));
  }
  return parts;
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
function vocabularyThemePost(site, tokens) {
  return {
    url: `${THEME_FOLDER}/${VOCABULARY_THEME_SLUG}`,
    type: "Theme",
    folder: THEME_FOLDER,
    data: {
      TITLE: vocabularyThemeTitle(site),
      CSS: VOCABULARY_THEME_CSS,
      // SH831 — the captured palette's values under this theme's own names, so the composed
      // regions are painted in the source's colours instead of this file's defaults.
      TOKENS: `${JSON.stringify(tokens ?? VOCABULARY_THEME_TOKENS, null, 2)}\n`,
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
export function describeConversion({ document, sections, markup = [], pages = [], verbatim = 0,
                                     mixed = 0, extras,
                                     requires = [], dropped = [], kept = [], declined = [],
                                     emptied = [], obstructions = [],
                                     annotations = [], annotationFields = [],
                                     home = null, unfollowed = null, palette = null,
                                     menu = null },
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
  // SH763 — the runs of kept markup, counted apart from the sections above. They ARE sections now,
  // and merging them into that number would read as a conversion having made more of the page
  // authorable than it did — which is the count a reviewer decides the next round of acceptances on.
  if (markup.length) {
    lines.push(
      `  ${markup.length} run(s) of kept markup written as SectionMarkup — the source's own bytes, `
        + "in the page's list where they stood, movable and replaceable like any other section",
    );
    // SH772 — the one shape whose captured stylesheet is emitted per run rather than once, because
    // a head cannot ask an aggregate question about a relation. Said by the command that produces
    // it: a megabyte repeated is a payload regression, and a reviewer hearing it here can move an
    // acceptance rather than discover it as page weight (P9).
    const markupUrls = new Set(markup.map((post) => post.url));
    const runs = (page) => (page.data?.sections ?? []).filter((url) => markupUrls.has(url)).length;
    const repeated = pages.filter((page) => typeof page.data?.content !== "string" && runs(page) > 1);
    for (const page of repeated) {
      lines.push(
        `    ${page.url} begins with an accepted block and keeps ${runs(page)} runs, so the `
          + "captured stylesheet is referenced once per run. Accept a block later on the page, or "
          + "leave the first one verbatim, and it goes back to one",
      );
    }
  }
  // SH510 — which presentation the reviewer is getting, said out loud. Under SH507 it is the mode's
  // contract rather than an implementation detail, and naming it is also the cheapest statement of
  // the property the task is about: one stylesheet reaches the page, and this is the one.
  const theme = document.posts.find((post) => post.type === "PageLayout")?.data?.THEME;
  if (theme) {
    lines.push(`  presented by ${theme}, the one stylesheet the layout names`);
  }
  // SH831 — the palette said out loud, both halves. `propose` already reported that the extracted
  // tokens "change nothing on their own"; they change something now, and the reader has to be able to
  // tell a colour that took a captured value from one that kept a default it was never offered — the
  // difference between a theme that matched the source and one that only looks like it might have.
  if (palette) {
    // SH846 — three counts rather than two, because a rejected slot used to be indistinguishable
    // from one nothing was offered for, and the run reported `--shio-color-surface: #0000` as one of
    // three tokens taken from the captured stylesheet. A refusal is the most actionable line here:
    // the source *has* a value for that role and it is one this theme will not paint with.
    const rejected = palette.rejected ?? [];
    // SH849 — and the slots that are in none of the three, said as a count. A reader given
    // "0 of 9" over a theme with fifteen tokens cannot tell whether six are missing from the report
    // or from the theme; naming them instead would be six constant lines on every run, which is the
    // clause everybody stops reading. `vocabularyTokensFrom` derives the list from the two
    // constants, so this number cannot drift from the theme it describes.
    const unbridged = palette.unbridged?.length ?? 0;
    lines.push(
      `  palette: ${palette.mapped.length} of `
        + `${palette.mapped.length + palette.unmapped.length + rejected.length} `
        + "token(s) from the captured stylesheet, under this theme's own names"
        + `${rejected.length ? `, ${rejected.length} refused` : ""}`
        + `${unbridged ? `, and ${unbridged} slot(s) this theme has no source role for` : ""}`,
    );
    for (const row of palette.mapped) {
      // SH855 — and, for the accent, the signal that elected it. `accent#1` names a rank and the rank
      // was the whole claim, so a run reporting `color.emerald.500 (accent#1)` on a site whose every
      // button is orange gave a reader nothing to disagree with. The other eight slots carry no such
      // clause: their rank *is* the evidence (P3).
      lines.push(`    --${row.name.replace(".", "-")} ${preview(row.value)} from ${row.from} `
        + `(${row.role})${row.elected ? `, elected by ${row.elected}` : ""}`);
    }
    for (const row of rejected) {
      lines.push(
        `    --${row.name.replace(".", "-")} ${preview(row.value)} REFUSED: ${row.detail}. `
          + "This theme's default stands; fix it in the source, or edit the Theme post's TOKENS",
      );
    }
    for (const row of palette.unmapped) {
      lines.push(
        `    --${row.name.replace(".", "-")} ${preview(row.value)}: the source states no `
          + `${row.role}, so this theme's default stands. Edit the Theme post's TOKENS to set it`,
      );
    }
    // A check that could not run is not a check that passed — the silence this project keeps paying
    // for. Named here rather than counted, because there are at most two of them (CONTRAST_PAIRS).
    for (const row of palette.unchecked ?? []) {
      // SH856 — the value, not the space. `oklch()` is converted now, so what reaches this line is
      // `lab()`, `lch()` or `color()`, and "a colour space this reader does not parse" told a curator
      // which of two halves to look at only by leaving it out. Naming both where both are unreadable
      // is the case a singular clause got wrong.
      const unreadable = row.unreadable?.length
        ? row.unreadable.map((each) => preview(each)).join(" and ")
        : "one of them";
      lines.push(
        `    --${row.text.replace(".", "-")} ${preview(row.value)} on `
          + `--${row.on.replace(".", "-")} ${preview(row.against)} — contrast NOT checked: `
          + `${unreadable} is a value this reader cannot convert. Look at the page`,
      );
    }
    // SH854 — the third of the floor's answers, and the worst: judged, failed, and nothing this run
    // was allowed to move. It is not a refusal — no slot was reverted, so `rejected` would report a
    // value the source filled as one it did not — and it is not unchecked, because the arithmetic
    // ran. Named last because it is the line a reader has to act on: the pair ships unreadable, and
    // `detail` carries the slot the source never stated, which is the half a curator can fix.
    for (const row of palette.unheld ?? []) {
      lines.push(
        `    --${row.text.replace(".", "-")} ${preview(row.value)} on `
          + `--${row.on.replace(".", "-")} ${preview(row.against)} — contrast NOT HELD: ${row.detail}. `
          + "Both halves ship as they are; state the missing slot in the source, or edit the Theme "
          + "post's TOKENS",
      );
    }
    // SH853 — a name both `:root` blocks declare. Empty by construction since every family here is
    // prefixed `shio-`, so a line printed at all means a *source* reached into that namespace: the
    // one case the prefix cannot rule out, and the one the cascade would otherwise settle by which
    // `<style>` came second. Named, because renaming the token is the fix and it takes the name.
    for (const name of palette.collides ?? []) {
      lines.push(
        `    ${name} — declared by BOTH this theme and the captured one, so on a mixed page whichever`
          + " <style> comes second wins it. Rename this theme's token in the Theme post's TOKENS",
      );
    }
  }
  // SH576 — the note whose absence was the defect. Every page of a locale-prefixed source is written
  // correctly and none of them is at `/`, so a run that says only what it wrote describes a complete
  // success and produces a site with no root. P7: the one page here that the source did not have is
  // the one the reviewer has to be told about.
  if (home?.as === "page") {
    lines.push(
      `  the source answers nothing at / — a home page was written, listing the ${home.lists} page(s)`
        + " this replica serves. It is the site's front door and the console frames it; edit it, or"
        + " re-run with --home <path> to redirect / to one of the pages instead",
    );
  }
  if (home?.as === "redirect") {
    lines.push(`  the source answers nothing at / — / redirects to ${home.to} (--home)`);
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
  // SH684 — the opposite decision, printed with the same weight. Before this, chrome emptied a
  // REQUIRED field on seven sections of a www.viglet.org run and `verify` reported them as
  // `required-field-empty` on ids no earlier phase had mentioned — the guard was on the propose side
  // of the seam and nowhere on this one. The keep is a cost, not a success: the value is chrome, and
  // saying so here is what lets a curator go and correct the seven rather than discover them.
  if (kept.length) {
    const fields = [...new Set(kept.map((row) => row.field))].sort();
    lines.push(
      `  ${kept.length} chrome value(s) kept because their type requires the field — `
        + `${fields.join(", ")}: dropping them would write a section \`shio verify\` refuses `
        + "(`required-field-empty`), which is what `shio propose` declines a candidate for. "
        + "Repeated across pages, so it may read generically — edit it in the console",
    );
    for (const row of kept.slice(0, 3)) {
      lines.push(`    ${row.section}.${row.field} (${row.type}) ${preview(row.value)}`);
    }
  }
  // SH684 — the decline, said out loud for SH449's reason: the block is still on the page, and a
  // reviewer who sees only the section count would read its absence as a block the capture missed.
  if (declined.length) {
    lines.push(
      `  ${declined.length} accepted candidate(s) declined here — every value they carry is chrome, `
        + "so the section would be a consent banner rather than the page's content (SH552). The "
        + "block stays verbatim, which is what `shio propose --accept all` does with the same case "
        + "one phase earlier",
    );
    for (const row of declined.slice(0, 3)) {
      lines.push(`    ${row.section} (${row.type}) required: ${row.fields.join(", ")}`);
    }
  }
  // SH857 — the row that said nothing, and the count is the point: the capture holds three repeated
  // units and the replica draws two cards, and without this line the only way to learn why is to open
  // the section in the console. `propose` reports the same fact about its own derivation; this reports
  // it about the file it was handed, which is not always the same file that command wrote.
  if (emptied.length) {
    const total = emptied.reduce((sum, row) => sum + row.rows, 0);
    lines.push(
      `  ${total} collection row(s) not written — every field of the row was empty, so the layout `
        + "would draw a card with nothing in it. Filtering at render would renumber the rows after "
        + "it and misaddress `features.N.title`, so the drop is here, where the array is written",
    );
    for (const row of emptied.slice(0, 3)) {
      lines.push(`    ${row.section}.${row.field} (${row.type}) — ${row.rows} row(s)`);
    }
  }
  // SH858 — a cut this command makes to the page's *own* bytes, which is the one it most owes a
  // reader: everything else here is a field. `clone` predicted the shape and predicting a defect is
  // not the same as it being acceptable, so the line says what was removed and how to keep it.
  if (obstructions.length) {
    const total = obstructions.reduce((sum, row) => sum + row.overlays, 0);
    lines.push(
      `  ${total} overlay(s) removed from the kept markup on ${obstructions.length} page(s) — a `
        + "fixed banner or modal on a page that captured NO script cannot be dismissed and no longer "
        + "records anything, so it obstructs the content forever. Re-capture with shio clone "
        + "--scripts to keep the working one",
    );
    for (const row of obstructions.slice(0, 3)) {
      lines.push(`    ${row.path} — ${row.overlays} overlay(s)`);
    }
  }
  // SH868 — the third mode, said out loud on the run that produces it. A page annotated in place is
  // the source's own pixels AND a set of editable fields, which the criterion said was a
  // contradiction until SH864 measured it; a reader has to be told which pages got it and what the
  // instance owes before an edit can be written.
  if (annotations.length) {
    const total = annotations.reduce((sum, row) => sum + row.fields, 0);
    lines.push(
      `  ${total} field(s) annotated in place on ${annotations.length} verbatim page(s) — the `
        + "markup is the source's, byte for byte, and an edit to one of these shows through "
        // SH871 — the two numbers are different and both are true: a name repeats across pages by
        // construction, because a block signature recurring IS what made it a candidate. Saying
        // only the total read as forty preconditions being ninety-two.
        + `(SH870). ${annotationFields.length} distinct field(s), stored in the markup that `
        + `carries them (SH872) — ${PAGE_TYPE} gains nothing and declares nothing`,
    );
    for (const row of annotations.slice(0, 3)) {
      lines.push(`    ${row.path} — ${row.fields} field(s)`);
    }
  }
  // SH584 — the bound this run inherited, in the run that inherits it. Said here rather than left
  // to `verify` because only this phase holds both halves: the instance has never seen the capture,
  // so at the gate these are indistinguishable from links the conversion broke. Phrased as a
  // prediction and not as a finding — nothing is wrong yet, and the two answers are both legitimate.
  if (unfollowed) {
    lines.push(
      `  ${unfollowed.refs} link(s) on the pages being converted point at ${unfollowed.paths} page(s) `
        + "this crawl did not follow, so they will verify as `page-link-broken`: raise --depth and "
        + "capture again, or accept them with shio verify --accept. It is a bound this run chose, "
        + `not damage the replica caused (first: ${unfollowed.first.join(" ")})`,
    );
  }
  // SH878 — the menu the source stated, and how much of what it stated became one. Both numbers,
  // because the difference is the whole report: an entry left out was considered and dropped for a
  // reason a reader can check, and a bare count of items reads as everything the source had.
  if (menu) {
    lines.push(
      `  menu: ${menu.items} entry(ies) from the source's own navigation, in ITS order, of `
        + `${menu.stated} link(s) it recorded — an entry is one that appears on every page `
        + "captured, which is what separates a menu from a link (SH432). Written as a Menu of "
        + "MenuItems a curator can relabel, reorder or delete",
    );
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
