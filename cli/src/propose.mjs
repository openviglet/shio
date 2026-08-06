/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH379 — the convert phase's deliverable, which is a **proposal** and not a conversion.
 *
 * ## Two honest modes, and why shipping either one alone would be wrong
 *
 * A captured page can become two things. **Fidelity mode** writes its markup verbatim as a `Theme`
 * plus a `PageLayout` and its `Region`s (SH191 sidecars): it renders identically and *nothing about
 * it is authorable* — a curator editing "the hero headline" is editing an HTML file. **Authorable
 * mode** maps repeated markup onto the SH95 section vocabulary: the page becomes content, presented
 * by the site's own theme (SH507). Both are defensible; neither is the answer, because which one is
 * right is a decision about *this block on this site* and nobody but the reviewer can make it.
 *
 * The sentence that used to end that paragraph was "and the pixels have moved", which framed the
 * appearance as **damage a reviewer weighs**. Under SH507 it is the mode's *contract*: an authorable
 * conversion yields structured, editable content presented by a Shio theme, and the source's
 * appearance is what fidelity is for — it reaches 0.00%. So the choice this file puts in front of a
 * reviewer is **their look, or curate this**, and a report that called one of the two a loss was
 * asking the reader to prevent something nobody intends to prevent.
 *
 * So what ships is the artefact between them: for every candidate block, the pages it recurs on, the
 * section type it looks like, the fields it would carry, and — the half that makes it honest — **what
 * accepting it would discard, kept separate from what it replaces**. Entries are accepted one at a
 * time; an unaccepted block stays in fidelity mode. That is P7 and P8 in one file: the appliable unit
 * is the accepted mapping, and a guess that silently restructured somebody's homepage would be the
 * worst surprise this product could produce.
 *
 * ## Why a real parser here, and only here
 *
 * `scanPage` is a scanner: it answers "what does this page declare" without building a tree, which
 * is the right shape for an inventory and is why SH377 is linear over hostile markup. Mapping markup
 * onto sections is a question about *structure* — is this block three repeats of the same subtree? —
 * and no scanner answers it. The tree is built over `tokenize`'s stream rather than a second
 * tokenizer, so "what is a tag on this page" still has exactly one answer, and the linear-scan
 * property that matters for a stranger's bytes is inherited rather than re-argued.
 *
 * ## Determinism is the whole value
 *
 * A proposal is read, argued with, partially accepted, and re-run. If the second run renumbers the
 * candidates the first run's acceptances are meaningless — so a block's id is a hash of its own
 * structure, candidates are sorted by id, and nothing in the file moves unless the markup did (P5).
 * There is no timestamp, for SH377's reason. And the proposal reads only the cache on disk: a
 * command that re-fetched would produce a different proposal depending on what the source served
 * this morning, which is the one thing an artefact meant to be reviewed cannot do.
 */
// SH476 — imported rather than injected like `tokenize`, because this is not a second reader of
// the markup: the tree is already built when it runs. Injection exists so there is one answer to
// "what is a tag on this page"; a decoder has no such twin, and a copy of the entity table would be
// exactly the drift that argument is about.
import { decodeEntities } from "./clone.mjs";
import { describeTheme } from "./theme.mjs";

/**
 * The proposal's shape version. Bumped when a consumer would read an old file wrongly.
 *
 * <p>**2 since SH436**, which added `requires`: the post-type fields the target instance has to
 * declare before this proposal can be applied. A consumer of a version-1 file sees no preconditions
 * and would take that for "there are none", which is the wrong way round — an applier that skips the
 * check silently writes a theme without its tokens.
 */
export const PROPOSAL_VERSION = 2;

/** Where the proposal lands: derived, beside the inventory it is computed from. */
export const PROPOSAL_FILE = "shio/.shio/conversion-proposal.json";

/**
 * Where an accepted mapping lands — a **project** file, not a derived one.
 *
 * <p>The distinction is the same one `shio/verify-baseline.json` makes: the proposal is recomputed
 * from the capture and may be deleted at any time, while an acceptance is a review decision a human
 * made and must survive every later run. That is why it is not a field inside the proposal.
 */
export const PLAN_FILE = "shio/conversion-plan.json";

/**
 * The SH95 vocabulary, as the fields a mapping would actually fill.
 *
 * <p>Duplicated from `@viglet/shio-sections` on purpose, and this is the one deliberate duplication
 * in the file: the CLI must run `propose` in a project that has not installed the sections package —
 * a capture happens *before* the model exists — and an import that resolved at authoring time and
 * not at conversion time would make the command fail on the site that most needs it. `repeats`
 * names the field a block's repeated units map into, which is what distinguishes a grid from prose.
 */
export const SECTION_TYPES = {
  SectionHero: {
    fields: ["heading", "subheading", "body", "ctaLabel", "ctaHref", "image", "align"],
    repeats: null,
  },
  SectionFeatureGrid: {
    fields: ["heading", "intro", "columns", "features"],
    repeats: { field: "features", of: ["title", "body", "icon"] },
  },
  SectionCta: {
    fields: ["heading", "body", "ctaLabel", "ctaHref", "variant"],
    repeats: null,
  },
  SectionLogoWall: {
    fields: ["heading", "logos"],
    repeats: { field: "logos", of: ["name", "image", "href"] },
  },
  SectionTestimonial: {
    fields: ["heading", "quotes"],
    repeats: { field: "quotes", of: ["quote", "author", "role", "avatar"] },
  },
  SectionFaq: {
    fields: ["heading", "intro", "questions"],
    repeats: { field: "questions", of: ["question", "answer"] },
  },
  SectionRichText: { fields: ["heading", "body"], repeats: null },
};

/**
 * Which of those fields the model declares as a `File` widget (SH471).
 *
 * <h3>Why a second declaration and not a `kind` on the one above</h3>
 *
 * <p>`SECTION_TYPES` says what a block maps *into*; this says what the model on the other side does
 * with the value, which is a fact about the blueprint and not about the markup. Keeping them apart
 * means `propose` — which applies nothing and may run before the model exists — is unchanged, and
 * the fact lands where the one reader that needs it can use it.</p>
 *
 * <h3>The duplication is closed by a check, not by a comment</h3>
 *
 * <p>Same deliberate duplication as `SECTION_TYPES`, for the same reason, and with the same hazard:
 * a `File` field the blueprints gain and this map does not would silently go back to rendering
 * `<img src="">`. `ShConvertedLayoutRenderTest` reads the blueprints and asserts this list is exactly
 * their `File` fields, so the drift is a red build naming the field.</p>
 */
export const SECTION_FILE_FIELDS = {
  SectionHero: { fields: ["image"], rows: {} },
  SectionLogoWall: { fields: [], rows: { logos: ["image"] } },
  SectionTestimonial: { fields: [], rows: { quotes: ["avatar"] } },
};

/** Elements that never have children, so a tree builder must not wait for their close tag. */
const VOID = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source",
  "track", "wbr",
]);

/**
 * Elements an unclosed `<p>` or `<li>` is implicitly closed by.
 *
 * <p>Real markup omits these closers constantly, and a builder that does not know it produces a tree
 * where the whole rest of the page is nested inside the first paragraph — which would make every
 * structural signature below the signature of one enormous block. This is the minimum table that
 * gets real pages right; it is not an HTML5 parser and does not claim to be.
 */
const IMPLICIT_CLOSE = {
  p: new Set(["p", "div", "section", "article", "aside", "header", "footer", "main", "ul", "ol",
    "table", "blockquote", "figure", "h1", "h2", "h3", "h4", "h5", "h6", "form", "pre", "hr"]),
  li: new Set(["li"]),
  dt: new Set(["dt", "dd"]),
  dd: new Set(["dt", "dd"]),
  option: new Set(["option"]),
  tr: new Set(["tr"]),
  td: new Set(["td", "th", "tr"]),
  th: new Set(["td", "th", "tr"]),
};

/** Tags whose content is prose rather than structure — the leaves a signature stops at. */
const INLINE = new Set([
  "a", "abbr", "b", "br", "cite", "code", "em", "i", "kbd", "mark", "q", "s", "small", "span",
  "strong", "sub", "sup", "time", "u", "var",
]);

/**
 * Whether a node is prose decoration — asked of the **node**, not of its tag (SH425).
 *
 * <p>The set above conflated "inline by HTML default" with "cannot be a block", and one very common
 * pattern lives in the gap: the whole-card-is-a-link, `<a class="card"><h2>…</h2><p>…</p></a>`. With
 * the tag alone deciding, a two-card grid of those filtered to nothing — so the block the vocabulary
 * fits best was the one it could not see, and the only symptom was a candidate count of zero.
 *
 * <p>An inline element containing block content is neither rare nor ambiguous, so the rule is simply
 * that its own children answer the question. A link around a heading is a block; a link inside a
 * sentence is prose, and stays a leaf.
 */
function isInline(node) {
  if (!INLINE.has(node.tag)) return false;
  return !node.children.some((child) => isElement(child) && !INLINE.has(child.tag));
}

/** A block this long in characters is prose, not a call to action. */
const CTA_TEXT_CEILING = 320;

// ---------------------------------------------------------------------------------------------
// The tree
// ---------------------------------------------------------------------------------------------

/**
 * Builds a tree over `tokenize`'s stream.
 *
 * <p>Three rules, each one a real page's fault rather than a specification's. A void element never
 * opens a scope. A close tag for something not on the stack is **dropped** rather than unwinding to
 * it — `</div>` too many is the commonest defect in hand-written markup, and honouring it would
 * close the page's main container and put every later block at top level. And an open tag closes the
 * implicitly-closable elements above it, per the table.
 *
 * @param {string} html the page's markup, already stripped of comments and raw-text bodies
 * @param {(html: string) => Iterable<{type: string, name?: string, attrs?: string, self?: boolean, text?: string}>} tokenize
 * @param {(raw: string) => Record<string, string>} parseAttrs
 * @returns {{tag: string, attrs: Record<string,string>, children: Array, start: number, end: number}}
 *   the root. Every element node carries the half-open `[start, end)` span it occupies in `html`
 *   (SH520) — and, because `stripNonMarkup` blanks rather than deletes, in the source `html` was made
 *   from. A node whose close tag the page never wrote ends where the thing that closed it began.
 */
export function buildTree(html, tokenize, parseAttrs) {
  const root = { tag: "#root", attrs: {}, children: [], start: 0, end: html.length };
  const stack = [root];
  const top = () => stack.at(-1);
  // Everything above `at` was closed by something other than its own tag, so it ends where that
  // something began; the node at `at` — when the caller names one — ends after its own close tag.
  const unwind = (at, upTo, through = null) => {
    for (let index = stack.length - 1; index > at; index -= 1) stack[index].end = upTo;
    stack[at].end = through ?? upTo;
    stack.length = at;
  };
  for (const token of tokenize(html)) {
    if (token.type === "text") {
      const text = String(token.text ?? "");
      if (text.trim()) {
        top().children.push({
          tag: "#text", text, attrs: {}, children: [], start: token.start, end: token.end,
        });
      }
      continue;
    }
    const name = token.name;
    if (token.type === "close") {
      const at = stack.findLastIndex((node) => node.tag === name);
      // Not on the stack: a stray closer. Dropping it is the conservative choice — unwinding to
      // nothing would empty the stack and re-root the rest of the document.
      if (at > 0) unwind(at, token.start, token.end);
      continue;
    }
    const closes = IMPLICIT_CLOSE;
    while (stack.length > 1 && closes[top().tag]?.has(name)) unwind(stack.length - 1, token.start);
    const node = {
      tag: name, attrs: parseAttrs(token.attrs), children: [], start: token.start, end: token.end,
    };
    top().children.push(node);
    if (!token.self && !VOID.has(name)) stack.push(node);
  }
  // A page that never closed what it opened: the rest of the document is inside it, which is what a
  // browser does with it too.
  if (stack.length > 1) unwind(1, html.length);
  return root;
}

/**
 * The subtree a conversion is about: `<main>`, else `[role=main]`, else `<body>`.
 *
 * <p>Not the whole document, and the omission is the point — a site's header and footer are the same
 * markup on every page, so proposing them as sections would propose sixty copies of one nav. They
 * are SH382's and SH389's problem, and this file says so rather than half-solving it.
 */
export function contentRoot(root) {
  const found = find(root, (node) => node.tag === "main" || String(node.attrs.role ?? "").toLowerCase() === "main");
  if (found) return found;
  return find(root, (node) => node.tag === "body") ?? root;
}

function find(node, predicate) {
  if (predicate(node)) return node;
  for (const child of node.children) {
    const hit = find(child, predicate);
    if (hit) return hit;
  }
  return null;
}

// ---------------------------------------------------------------------------------------------
// Candidate blocks
// ---------------------------------------------------------------------------------------------

/**
 * Generic containers — the only thing the walk below is allowed to descend *through*.
 *
 * <p>The distinction that makes it safe. A theme wrapping every section in
 * `<div class="container"><div class="row">` must be walked past, or the page yields one candidate
 * whose signature is the whole page. But a page whose content root holds a single `<section>` is a
 * page with **one block**, and descending into that section would propose its heading and its
 * paragraph as two blocks — the same over-descent, in the direction nobody notices, because the
 * candidate list still looks plausible.
 */
const GENERIC = new Set(["div", "span"]);

/**
 * Elements that are a block by declaration, however many of them there are (SH425).
 *
 * <p>The other half of the stop-before-descending rule below. Two `<section>`s of identical structure
 * inside a theme's `<div class=row>` satisfy "repeated units" exactly as two cards do — and they are
 * two blocks, not one grid, because the source said `section`. So the container is only treated as
 * the candidate when the units it holds are *generic*: a card, a link-wrapped card, a `<div>`. This is
 * the same principle the single-child rule already used — "a sectioning element is a block no matter
 * how alone it is" — applied to the case where there are several of them.
 */
const SECTIONING = new Set(["section", "article", "aside", "nav", "header", "footer", "main", "form"]);

/**
 * The blocks of one page, in source order.
 *
 * <p>A block is a child of the content root, having first walked past any chain of single-child
 * **generic** wrappers. Two conditions, both load-bearing: one child (the moment a node has two,
 * those two are the blocks and nothing has been skipped past), and generic (a sectioning element is
 * a block no matter how alone it is).
 *
 * <p>SH425 added the third, and it is a condition on *stopping* rather than on descending: a node
 * whose own children are repeated units **is** the block. `main > div.wrap > div.cards` used to walk
 * to `div.cards` and return its children — but `div.cards` is the candidate the vocabulary would have
 * called a `SectionFeatureGrid`, and the descent consumed it. The docstring's rule was that two
 * children mean nothing was skipped; the case it missed is a node whose two children are the repeated
 * units. The test is deliberately **shallow** — the units have to be that node's own children, not
 * something deeper — because a subtree-wide test would stop at the outermost wrapper and yield one
 * candidate whose signature is the whole page, which is the same over-descent in the direction nobody
 * notices. And it applies only to *generic* units, or a theme's `<div class=row>` holding two
 * identically-built `<section>`s would collapse two blocks into one (`SECTIONING`).
 *
 * <p>SH505 added the fourth, and it is an exclusion rather than a boundary: an **overlay** is
 * painted over the page and is not part of it. See `isOverlay`.
 */
export function blocksOf(root, excluded = []) {
  const main = contentRoot(root);
  let node = main;
  for (;;) {
    const kids = node.children.filter(isElement);
    if (kids.length !== 1 || !GENERIC.has(kids[0].tag) || !kids[0].children.some(isElement)) break;
    const units = unitsAmong(kids[0]);
    if (units.length && units.every((unit) => !SECTIONING.has(unit.tag))) break;
    node = kids[0];
  }
  const blocks = node.children.filter((child) => isElement(child) && !isInline(child));
  return [...pageHeader(root, main), ...blocks].filter((block) => {
    const why = notContent(block);
    if (why) excluded.push({ tag: block.tag, why });
    return !why;
  });
}

/**
 * Elements that are a block's whole content and are not content (SH506).
 *
 * <p>One of six candidates on a real site was a `<script>`: `"signature": "script"`, `"fields": {}`,
 * `"loss": {"elements":[{"name":"script","count":1}]}` — the loss line naming the whole of the
 * block — and it proposed the post-type `{"name":"Block1","fields":[]}`. It recurred on all six
 * pages, which is what promoted it: a block seen everywhere looks structural.</p>
 *
 * <p>A script is <b>behaviour</b>, and SH469 gave behaviour its own list, so this is the block
 * walker reaching a node the phase beside it already owns. The rest of the set is the same argument
 * for markup that carries no reading: a `<style>`, a `<link>`, a `<meta>` and a `<noscript>` are all
 * things a page needs and nothing a curator edits.</p>
 */
const NON_CONTENT = new Set(["script", "style", "link", "meta", "noscript", "template"]);

/** Why a block is not a candidate, or `null` when it is one. */
function notContent(block) {
  if (NON_CONTENT.has(block.tag)) return `a <${block.tag}> is behaviour or markup plumbing`;
  return isOverlay(block) ? "an overlay is painted over the page rather than part of it" : null;
}

/**
 * The class names that mean "out of flow", as a class rather than as a declaration.
 *
 * <p>`fixed` is Tailwind's `position: fixed` and the one the measured page used
 * (`fixed inset-x-0 bottom-0 z-50`). The rest are what hand-written CSS and the other frameworks
 * call the same thing. A capture has no computed style — the stylesheet is a separate file and this
 * pass reads markup — so the class name is the evidence available, exactly as `INLINE_TAGS` is in
 * `clone.mjs` for the same reason.</p>
 *
 * <p>Deliberately not `sticky`: a sticky element is **in flow** and scrolls with the page until it
 * pins, so a sticky sub-nav is content in a way a consent banner is not.</p>
 */
const OVERLAY_CLASSES = new Set(["fixed", "position-fixed", "is-fixed", "overlay", "modal", "backdrop"]);

/**
 * Whether a block is painted **over** the page rather than being part of it (SH505).
 *
 * <h3>The measurement</h3>
 *
 * <p>Three candidates on three different pages were proposed with the same `intro`: **"We respect
 * your privacy"** — the cookie consent banner, which sits in
 * `<div aria-live="polite" class="fixed inset-x-0 bottom-0 z-50 …">` at the bottom of every page.
 * Its first paragraph was attributed to a `SectionFaq` on `/` and to a `SectionFeatureGrid` on
 * `/dumont/`, `/turing/` and `/shio/`. A curator opening the FAQ to edit "the intro" would have been
 * editing a consent notice, and the same string would have become content in three places at once.</p>
 *
 * <h3>Why it is `contentRoot`'s rule and not a new one</h3>
 *
 * <p>`contentRoot` already excludes chrome, and the reasoning transfers exactly: an overlay appears
 * on every page, belongs to none of them, and is transient. It is simply neither header nor footer,
 * so the existing exclusion could not reach it. This is the authorable path's version of SH441 —
 * there a chrome *asset* belonged to no block and stranded; here an overlay's *text* belongs to no
 * block and was adopted by three.</p>
 *
 * <h3>Four signals, any one of which is enough</h3>
 *
 * <p>Out-of-flow positioning (as a class or as an inline declaration), `aria-live`, `role="dialog"`
 * or `role="alertdialog"`, and `aria-modal`. Each one is a statement by the author that the region
 * is announced or painted rather than read in order, and a page that carries any of them on a
 * top-level block is describing chrome. `--render` is what makes them present — a consent banner
 * that a script injects is in the markup only after hydration — which is why this is findable rather
 * than a guess.</p>
 */
export function isOverlay(node) {
  const attrs = node?.attrs ?? {};
  if (attrs["aria-live"] || attrs["aria-modal"]) return true;
  const role = String(attrs.role ?? "").toLowerCase().trim();
  if (role === "dialog" || role === "alertdialog") return true;
  if (/position\s*:\s*fixed/i.test(String(attrs.style ?? ""))) return true;
  return String(attrs.class ?? "")
    .toLowerCase()
    .split(/\s+/)
    .some((name) => OVERLAY_CLASSES.has(name));
}

/**
 * The page's own `<header>`, when narrowing to `<main>` walked straight past it (SH426).
 *
 * <p>`contentRoot`'s reason for narrowing is sound and describes the **site** chrome: a header and
 * footer repeated on every page would propose sixty copies of one nav. It does not describe a page's
 * own introductory header, and on `openviglet.github.io` that distinction cost the command the single
 * most convertible block on the page: the chrome is `div.topbar`, and the hero is `header.hero-wrap` —
 * an eyebrow, an `<h1>`, a lead paragraph and two CTA links — sitting as a sibling just before
 * `<main>`. `<header>` for introductory content is what the HTML spec is for, so that is the idiomatic
 * shape rather than an unusual one.
 *
 * <p>The irony worth recording, because it shows the two decisions were made apart: `classify` carries
 * a positional hero test, tried first and only when the caller says the block opened the page,
 * precisely so a hero is not filed as a CTA. On a page shaped like this one, `contentRoot` guaranteed
 * that test could never fire.
 *
 * <p>**The closest one only**, and no attempt to tell a hero from chrome here. A site whose chrome
 * *is* `<header>` gets one candidate — signatures are deduped, so it is one decision on N pages, not
 * N decisions — and the evidence that separates the two is repetition, which the capture already
 * computes and `buildProposal` reports as `pages`. Deciding it structurally would mean guessing from a
 * single page, which is the case with no evidence at all.
 */
function pageHeader(root, main) {
  const narrowed = main.tag === "main" || String(main.attrs.role ?? "").toLowerCase() === "main";
  if (!narrowed) return [];
  const parent = parentOf(root, main);
  if (!parent) return [];
  const kids = parent.children.filter(isElement);
  const before = kids.slice(0, kids.indexOf(main));
  const header = before.filter((node) => node.tag === "header").at(-1);
  return header?.children.some(isElement) ? [header] : [];
}

function parentOf(node, target) {
  for (const child of node.children) {
    if (child === target) return node;
    if (!isElement(child)) continue;
    const found = parentOf(child, target);
    if (found) return found;
  }
  return null;
}

const isElement = (node) => node.tag !== "#text";

/**
 * All the visible text under a node, entities decoded and whitespace collapsed.
 *
 * <p>SH476 — this is the boundary where markup becomes **content**, and every authorable field is
 * either this or an attribute. Decoded here rather than at each caller, because a caller that
 * forgets is a field that double-escapes and nothing downstream can tell: the renderer escapes what
 * it is given, correctly, and the page reads `&amp;` where the source read `&`.</p>
 *
 * <p>Before the collapse, deliberately: `&nbsp;` is whitespace once it is a character, and a field
 * holding a non-breaking space that no `\s` pass touched is a value a curator cannot see and cannot
 * type.</p>
 */
export function text(node) {
  if (node.tag === "#text") return decodeEntities(node.text ?? "").replace(/\s+/g, " ");
  return node.children.map(text).join(" ").replace(/\s+/g, " ").trim();
}

/**
 * A block's structural fingerprint — what makes "the same block on twelve pages" a computable claim.
 *
 * <p>Tags and nesting only: **no text, no classes, no attributes**. Text is excluded because the
 * point is to recognise one hero on twelve pages whose headline differs on each; classes are
 * excluded because a theme that appends `is-active` to the current nav item would otherwise make
 * every page's block unique. Depth is capped, because past three levels the signature stops
 * discriminating between blocks and starts discriminating between authors.
 */
export function signature(node, depth = 3) {
  if (node.tag === "#text") return "";
  if (depth === 0) return node.tag;
  const kids = node.children
    .filter(isElement)
    .map((child) => signature(child, depth - 1))
    .filter(Boolean);
  return kids.length ? `${node.tag}(${kids.join(",")})` : node.tag;
}

/**
 * The repeated units inside a block, if it has any.
 *
 * <p>"Repeated" means: two or more element children of the same parent sharing one signature, where
 * each of them **has element children of its own**. Two is deliberate rather than three — a
 * two-column feature grid is a real thing, and a rule that needed three would send it to
 * `SectionRichText`, which loses the structure permanently.
 *
 * <p>The leaf condition is the one that stops this being nonsense. Three consecutive `<p>`s are three
 * children with one signature, and without it every paragraph of prose is a "repeated unit" — so an
 * About page becomes a feature grid whose features are its sentences. A unit is a *container*: the
 * wrapper around a heading and its text, which is what a card, a quote and a disclosure all are.
 */
export function repeatedUnits(block) {
  let best = unitsAmong(block);
  const visit = (node) => {
    for (const child of node.children.filter(isElement)) {
      const found = unitsAmong(child);
      if (found.length > best.length) best = found;
      visit(child);
    }
  };
  visit(block);
  return best;
}

/**
 * The repeated units among **one node's own children** — the rule above, one level deep.
 *
 * <p>Split out for SH425, which needs to ask the question before descending through a wrapper and
 * must not be answered by something further down. One implementation, so the two callers cannot
 * disagree about what a repeated unit is.
 */
function unitsAmong(node) {
  const groups = new Map();
  for (const child of node.children.filter(isElement)) {
    if (isInline(child) || !child.children.some(isElement)) continue;
    const key = signature(child, 2);
    const group = groups.get(key);
    if (group) group.push(child);
    else groups.set(key, [child]);
  }
  let best = [];
  for (const group of groups.values()) {
    if (group.length >= 2 && group.length > best.length) best = group;
  }
  return best;
}

// ---------------------------------------------------------------------------------------------
// Classification
// ---------------------------------------------------------------------------------------------

/**
 * Which section type a block looks like, with the evidence that says so.
 *
 * <p>**The order is the design decision**, and it is most-specific-first: a block of three
 * `<blockquote>`s also satisfies "repeated units", so a rule set evaluated grid-first would file
 * every testimonial wall as a feature grid and the loss would be invisible — the fields are the same
 * shape. Each rule returns its own evidence string, because a classification a reviewer cannot
 * *check* is a guess wearing a type name, and the whole artefact exists to be checked.
 *
 * <p>Returns `null` when nothing matches, which is a real answer: SH379's rule is that a block the
 * vocabulary has no type for becomes a **new post-type the conversion emits**, never a section
 * forced into the nearest wrong shape.
 */
export function classify(block, context = {}) {
  const units = repeatedUnits(block);
  const headings = descendants(block).filter((node) => /^h[1-6]$/.test(node.tag));
  const images = descendants(block).filter((node) => node.tag === "img" || node.tag === "svg");
  const links = descendants(block).filter((node) => node.tag === "a");
  const body = text(block);

  if (descendants(block).filter((node) => node.tag === "details").length >= 2) {
    return { type: "SectionFaq", evidence: "two or more <details>, which is a disclosure list" };
  }
  if (units.length >= 2 && units.every((unit) => /\?\s*$/.test(firstHeadingText(unit) || ""))) {
    return { type: "SectionFaq", evidence: `${units.length} repeated units whose headings are questions` };
  }
  const quotes = descendants(block).filter((node) => node.tag === "blockquote" || node.tag === "cite");
  if (quotes.length >= 1 && (units.length >= 2 || quotes.length >= 2)) {
    return { type: "SectionTestimonial", evidence: `${quotes.length} <blockquote>/<cite> element(s)` };
  }
  if (units.length >= 3 && units.every((unit) => hasImage(unit) && text(unit).length <= 40)) {
    return { type: "SectionLogoWall", evidence: `${units.length} repeated image-only units` };
  }
  if (units.length >= 2 && units.filter((unit) => text(unit).length > 0).length === units.length) {
    return { type: "SectionFeatureGrid", evidence: `${units.length} repeated units, each with text` };
  }
  // A hero is **positional**, and that is the only honest signal for it: structurally it is a
  // heading, a line of prose and a button, which is exactly what a CTA is too. So it is tested first
  // and only when the caller says this block opened the page — otherwise every homepage's hero is
  // filed as a call to action, which is a plausible-looking answer and the wrong one. `first` comes
  // from the caller because this function must not have to know where it was called from.
  if (context.first && headings.length >= 1 && images.length <= 2 && !units.length) {
    return { type: "SectionHero", evidence: "the first block of the page, with a heading" };
  }
  if (!units.length && body.length <= CTA_TEXT_CEILING && links.length >= 1 && links.length <= 2 && headings.length <= 1) {
    return { type: "SectionCta", evidence: `${body.length} chars of text and ${links.length} link(s)` };
  }
  if (!units.length && body.length > 0 && descendants(block).some((node) => node.tag === "p")) {
    return { type: "SectionRichText", evidence: "prose with no repeated structure" };
  }
  return null;
}

function firstHeadingText(node) {
  const heading = descendants(node).find((each) => /^h[1-6]$/.test(each.tag));
  return heading ? text(heading) : "";
}

function hasImage(node) {
  return descendants(node).some((each) => each.tag === "img" || each.tag === "svg" || each.tag === "picture");
}

/** Every element under a node, itself excluded. */
export function descendants(node) {
  const out = [];
  const walk = (current) => {
    for (const child of current.children) {
      if (child.tag === "#text") continue;
      out.push(child);
      walk(child);
    }
  };
  walk(node);
  return out;
}

// ---------------------------------------------------------------------------------------------
// The fields a mapping would carry, and what it would lose
// ---------------------------------------------------------------------------------------------

/**
 * The field values a mapping would produce, and the nodes it consumed producing them.
 *
 * <p>The consumed set is the reason this returns two things: the loss report below is computed as
 * *everything the fields did not take*, which is the only definition that cannot under-report. An
 * implementation that listed known-lossy cases instead would be silent about the case nobody thought
 * of, and this artefact's job is to be trusted about exactly that.
 */
export function fieldsFor(type, block) {
  const spec = SECTION_TYPES[type];
  const consumed = new Set();
  const fields = {};
  const take = (node) => {
    if (!node) return null;
    consumed.add(node);
    for (const each of descendants(node)) consumed.add(each);
    return node;
  };

  const headings = descendants(block).filter((node) => /^h[1-6]$/.test(node.tag));
  const heading = headings[0];
  if (heading && spec.fields.includes("heading")) fields.heading = text(take(heading));

  const units = spec.repeats ? repeatedUnits(block) : [];
  if (spec.repeats && units.length) {
    fields[spec.repeats.field] = units.map((unit) => unitFields(spec.repeats.of, unit, take));
  }

  const prose = descendants(block)
    .filter((node) => node.tag === "p" && !consumed.has(node) && text(node));
  const lead = prose[0];
  for (const name of ["subheading", "intro"]) {
    if (lead && spec.fields.includes(name) && fields[name] === undefined) {
      fields[name] = text(take(lead));
      break;
    }
  }
  if (spec.fields.includes("body")) {
    const rest = prose.filter((node) => !consumed.has(node));
    if (rest.length) fields.body = rest.map((node) => text(take(node))).join("\n\n");
  }

  const link = descendants(block).find((node) => node.tag === "a" && !consumed.has(node) && text(node));
  if (link && spec.fields.includes("ctaLabel")) {
    fields.ctaLabel = text(link);
    fields.ctaHref = link.attrs.href ?? "";
    take(link);
  }
  const image = descendants(block).find((node) => node.tag === "img" && !consumed.has(node));
  if (image && spec.fields.includes("image")) {
    fields.image = image.attrs.src ?? "";
    take(image);
  }
  return { fields, consumed };
}

function unitFields(names, unit, take) {
  const out = {};
  const heading = descendants(unit).find((node) => /^h[1-6]$/.test(node.tag));
  const paragraph = descendants(unit).find((node) => node.tag === "p");
  const quote = descendants(unit).find((node) => node.tag === "blockquote" || node.tag === "q");
  const cite = descendants(unit).find((node) => node.tag === "cite");
  const image = descendants(unit).find((node) => node.tag === "img");
  const link = descendants(unit).find((node) => node.tag === "a");
  const pick = {
    title: () => heading && text(heading),
    question: () => heading && text(heading),
    quote: () => (quote ? text(quote) : paragraph && text(paragraph)),
    author: () => cite && text(cite),
    role: () => null,
    answer: () => paragraph && text(paragraph),
    body: () => paragraph && text(paragraph),
    // SH476 — an `alt` is the one field taken from an attribute and stored as prose, so it crosses
    // the same boundary `text` does. `icon`, `avatar`, `image` and `href` are URLs and are left
    // alone here: they are resolved and rewritten downstream, and unescaping a query string is a
    // decision that belongs where the URL is parsed.
    name: () => (decodeEntities(image?.attrs.alt ?? "") || (link && text(link))) ?? null,
    icon: () => image?.attrs.src ?? null,
    avatar: () => image?.attrs.src ?? null,
    image: () => image?.attrs.src ?? null,
    href: () => link?.attrs.href ?? null,
  };
  for (const name of names) {
    const value = pick[name]?.();
    if (value) out[name] = value;
  }
  take(unit);
  return out;
}

/**
 * What accepting this mapping would throw away.
 *
 * <p>Computed as the complement of what the fields consumed, so it is complete by construction. Three
 * categories, because they are three different conversations. **Elements** the section cannot hold —
 * an inline `<svg>`, a `<video>`, a third-party embed — are content that disappears. **Behaviour** is
 * an `onclick`, a `data-*` a script reads, or an `id` an in-page anchor targets: the page still looks
 * right and stops working, which is the worst of the three to discover later. **Styling** is every
 * class name in the block, reported as a count and not a list: the section renders with the
 * vocabulary's own markup, so *all* of it goes, and that is one fact rather than forty.
 */
export function lossFor(block, consumed) {
  const elements = [];
  const behaviour = [];
  const classes = new Set();
  // The block **itself** is in the walk, and it is the one every draft of this forgot: a section's
  // own class list is the styling a reviewer is agreeing to lose, and it is usually the whole theme
  // hook — `class="hero dark"` on the wrapper, nothing on the children.
  for (const node of [block, ...descendants(block)]) {
    for (const [name, value] of Object.entries(node.attrs)) {
      if (name === "class") {
        for (const each of String(value).split(/\s+/).filter(Boolean)) classes.add(each);
        continue;
      }
      if (name.startsWith("on") || name.startsWith("data-") || name === "id" || name === "style") {
        behaviour.push(`${node.tag}[${name}]`);
      }
    }
    // SH425 — `isInline`, not the tag: an `<a>` wrapping a card's heading and paragraph is
    // structure a conversion drops, and a reviewer agreeing to the loss should see it. A link inside
    // a sentence is still prose and still excluded, which is what the tag set is for.
    if (consumed.has(node) || isInline(node)) continue;
    if (LAYOUT.has(node.tag)) continue;
    elements.push(node.tag);
  }
  return {
    elements: tally(elements),
    behaviour: tally(behaviour),
    classes: classes.size,
  };
}

/** Tags that carry no content of their own — losing a `<div>` loses nothing but its class. */
const LAYOUT = new Set(["div", "section", "article", "header", "footer", "aside", "ul", "ol", "li",
  "figure", "figcaption", "picture", "dl", "dt", "dd", "p", "br", "hr"]);

function tally(names) {
  const counts = new Map();
  for (const name of names) counts.set(name, (counts.get(name) ?? 0) + 1);
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => (a.count === b.count ? byText(a.name, b.name) : b.count - a.count));
}

// ---------------------------------------------------------------------------------------------
// A block the vocabulary has no type for
// ---------------------------------------------------------------------------------------------

/**
 * The post-type an unmatched block would need (SH379's last rule).
 *
 * <p>Derived from what the block *contains*, and named from its own strongest signal — a heading, a
 * landmark, a class — because a generated `Block7` is a name the next session has to re-derive. The
 * field kinds are `@viglet/shio-model`'s vocabulary: a short string is `text`, prose is `htmlEditor`,
 * an image is `file`, repeated units are a `relator`. It is *proposed*, never written: emitting a
 * post-type is an apply, and this file's whole contract is that it applies nothing.
 */
export function proposePostType(block, index) {
  const units = repeatedUnits(block);
  const fields = [];
  const heading = descendants(block).find((node) => /^h[1-6]$/.test(node.tag));
  if (heading) fields.push({ name: "heading", kind: "text", from: heading.tag });
  const paragraphs = descendants(block).filter((node) => node.tag === "p" && text(node));
  if (paragraphs.length === 1) fields.push({ name: "body", kind: "textArea", from: "p" });
  else if (paragraphs.length > 1) fields.push({ name: "body", kind: "htmlEditor", from: `${paragraphs.length} p` });
  if (descendants(block).some((node) => node.tag === "img")) {
    fields.push({ name: "image", kind: "file", from: "img" });
  }
  if (descendants(block).some((node) => node.tag === "video" || node.tag === "iframe")) {
    fields.push({ name: "embedUrl", kind: "text", from: "video/iframe" });
  }
  if (units.length >= 2) {
    fields.push({ name: "items", kind: "relator", from: `${units.length} repeated ${units[0].tag}` });
  }
  // SH506, and the more general of that task's two rules — it covers a case nobody has hit yet. A
  // post-type with **zero** derivable fields is not a conversion of anything: nothing can be
  // authored in it, `verify` would report an empty type, and the name is a counter (`Block1`, then
  // `Block2`) so it says nothing either. `null` rather than an empty-field proposal, because the
  // reader's question is "what would I get" and the honest answer here is "nothing".
  return fields.length ? { name: postTypeName(block, index), fields } : null;
}

function postTypeName(block, index) {
  const heading = descendants(block).find((node) => /^h[1-6]$/.test(node.tag));
  const source = text(heading ?? { tag: "#root", children: [], attrs: {} })
    || String(block.attrs["data-block"] ?? block.attrs.id ?? "")
    || String(block.attrs.class ?? "").split(/\s+/).find((each) => each.length > 3)
    || "";
  const words = source
    .replace(/[^A-Za-z0-9 ]+/g, " ")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 3)
    .map((word) => word[0].toUpperCase() + word.slice(1).toLowerCase());
  return words.length ? `Block${words.join("")}` : `Block${index + 1}`;
}

// ---------------------------------------------------------------------------------------------
// The proposal
// ---------------------------------------------------------------------------------------------

/**
 * Folds the captured pages into one proposal.
 *
 * <p>A candidate is a *signature*, not a block: the same hero on twelve pages is one decision to
 * review, twelve times cheaper than twelve. `pages` is what tells the reviewer whether accepting it
 * is a page-level change or a site-level one, and it is the field that makes recurrence — the thing
 * a scanner could not see — the artefact's unit.
 *
 * @param {{pages: Array<{path: string, markup?: string}>}} inventory the SH377 capture
 * @param {Map<string, string>} markup path → the page's HTML, read from the cache
 * @param {{tokenize: Function, parseAttrs: Function, stripNonMarkup: Function}} html the shared reader
 * @param {{accepted?: Array<{block: string, type: string}>}} [plan] the review decisions so far
 * @param {ReturnType<import("./theme.mjs").buildTheme>|null} [theme] SH380's theme half, or null
 */
export function buildProposal({ inventory, markup, html, plan = {}, theme = null }) {
  const accepted = new Map((plan.accepted ?? []).map((entry) => [entry.block, entry]));
  const candidates = new Map();
  const notes = [];
  // SH505/SH506 — what the walk refused to call a candidate, so the cut is stated rather than
  // silent. A shortened list of candidates reads exactly like a shorter list of candidates (SH138),
  // and on a six-candidate site the reader is checking every row.
  const excluded = [];
  const read = [];
  const composition = [];

  for (const page of (inventory.pages ?? []).toSorted((a, b) => byText(a.path, b.path))) {
    const source = markup.get(page.path);
    if (typeof source !== "string") continue;
    read.push(page.path);
    const tree = buildTree(html.stripNonMarkup(source), html.tokenize, html.parseAttrs);
    const blocks = blocksOf(tree, excluded);
    // SH438 — the page's own blocks, in the order the source had them. `candidates` is keyed by
    // signature, so it can say *which* pages a block is on and never in what order: a candidate is
    // one decision on N pages, which is the whole reason it is cheap to review. This is the other
    // axis, and without it the sections a page composes could only be ordered by id — an ordering
    // the source never had.
    const order = [];
    // SH520 — where each of those blocks *is*, as a half-open span in the page's own bytes. The
    // blocks array says what a page is made of; this says which bytes each one is, which is what
    // `convert` needs to cut an accepted block out and keep the rest of the page verbatim. Two
    // integers per block, parallel to `blocks`, and only meaningful together with it.
    const spans = [];
    composition.push({ path: page.path, title: page.title ?? null, blocks: order, spans });
    blocks.forEach((block, index) => {
      const sig = signature(block);
      const id = idFor(sig);
      order.push(id);
      spans.push(Number.isInteger(block.start) && Number.isInteger(block.end)
        ? [block.start, block.end]
        : null);
      const guess = classify(block, { first: index === 0 });
      const existing = candidates.get(id);
      if (existing) {
        existing.pages.push(page.path);
        // A block whose first appearance was mid-page and whose second is first-on-page is a hero
        // the first reading could not see. Keeping the stronger classification is why `first` is
        // passed in rather than inferred: the same signature legitimately gets two answers.
        if (!existing.type && guess) {
          Object.assign(existing, describe(block, guess, existing.index));
        }
        return;
      }
      candidates.set(id, {
        id,
        signature: sig,
        pages: [page.path],
        index,
        tag: block.tag,
        ...describe(block, guess, candidates.size),
      });
    });
  }

  const uncached = (inventory.pages ?? []).filter((page) => !markup.get(page.path)).length;
  if (uncached) {
    notes.push(
      `${uncached} of ${inventory.pages.length} captured page(s) have no markup on disk, so they ` +
        `are not in this proposal — re-run shio clone <url> --markup`,
    );
  }
  if (inventory.crawl?.truncated) {
    notes.push("the capture this reads was PARTIAL, so a block that recurs site-wide may look page-local here");
  }
  // SH426 — the recurrence evidence, said out loud. Including a page's own `<header>` means a site
  // whose chrome is `<header>` now yields it as a candidate, and the honest way to tell that from a
  // hero is that chrome is on *every* page. Three pages is the floor for saying so: on two, a hero
  // with one structure is on all of them too, and a hint that fires on every small capture is one a
  // reviewer learns to ignore.
  const everywhere = read.length >= 3
    ? [...candidates.values()].filter((row) => new Set(row.pages).size === read.length)
    : [];
  if (everywhere.length) {
    notes.push(
      `${everywhere.length} candidate(s) appear on all ${read.length} pages read, which is what site `
        + `chrome looks like: ${everywhere.map((row) => row.id).join(" ")} — a header or a nav that `
        + "recurs everywhere is one decision about the site, not a section per page",
    );
  }

  if (excluded.length) {
    const why = [...new Set(excluded.map((row) => row.why))].sort(byText);
    notes.push(
      `${excluded.length} top-level block(s) were not proposed as candidates, because a candidate `
        + `is content and these are not: ${why.join("; ")}`,
    );
  }
  // SH505 — the tell, said out loud on every proposal. The predicate above excludes the overlays
  // this pass can recognise; this reports the shape *whatever* produced it, because a field value
  // that recurs on candidates sharing no page is not that section's content. It is the same
  // reasoning one level up from `everywhere`: recurrence across pages is what chrome looks like,
  // and a field is the unit a curator would edit.
  const adopted = adoptedValues([...candidates.values()]);
  if (adopted.length) {
    notes.push(
      `${adopted.length} field value(s) appear on candidates that share no page, which is what `
        + "chrome looks like one level down — a consent banner, a floating widget, a toast. A field "
        + `is one section's content or it is not content: ${adopted.slice(0, 3).join("; ")}`,
    );
  }

  const rows = [...candidates.values()]
    .map(({ index, ...rest }) => ({
      ...rest,
      pages: [...new Set(rest.pages)].sort(byText),
      mode: accepted.has(rest.id) ? "authorable" : "fidelity",
      ...(accepted.has(rest.id) ? { acceptedAs: accepted.get(rest.id).type } : {}),
    }))
    .sort((a, b) => byText(a.id, b.id));

  return {
    version: PROPOSAL_VERSION,
    source: inventory.source ?? null,
    read: read.toSorted(byText),
    // SH438 — what orders the sections a page composes. `read` says which pages were understood;
    // this says what each one is made of, which is the array `Page.sections` takes.
    pages: composition,
    candidates: rows,
    accepted: rows.filter((row) => row.mode === "authorable").length,
    // SH380 — the theme is the same artefact's other half. A separate file would be a second thing to
    // find, and the two are read together: a block's `replaces` line names the source's styling, and
    // the theme is where that styling went.
    ...(theme ? { theme } : {}),
    // SH389 — and the third half: the scripts SH377 found and nothing could hold. Computed from the
    // inventory alone, so it needs neither markup nor stylesheets and is present on every proposal a
    // capture can produce.
    scripts: proposeScripts(inventory),
    // SH436 — what the target must already declare for this to be appliable, computed from what the
    // proposal actually holds.
    requires: requirements({ theme, scripts: proposeScripts(inventory) }),
    notes: [...notes, ...(theme?.notes ?? [])],
  };
}

/**
 * Field values carried by candidates that share no page (SH505).
 *
 * <p>Scalar fields only, and only values long enough to be prose: a `heading` of "Contact" on two
 * sections is a coincidence a site is entitled to, and "We respect your privacy" attributed to a FAQ
 * and to a feature grid is not. The floor is deliberately a length rather than a word list — the
 * property is structural ("this value belongs to no one page"), and a list of known banner strings
 * would be a list that only ever finds the sites somebody has already looked at.</p>
 *
 * @returns rows like `intro "We respect your privacy" — b1a2, c3d4`, sorted, at most one per value
 */
function adoptedValues(rows) {
  const seen = new Map();
  for (const row of rows) {
    for (const [field, value] of Object.entries(row.fields ?? {})) {
      if (typeof value !== "string" || value.trim().length < ADOPTED_FLOOR) continue;
      // `\u0000` and not a space: the key is a pair, and a field name cannot hold a NUL
      // while a value can hold anything - so it is the one separator that cannot be in it.
      const key = `${field}\u0000${value.trim()}`;
      seen.set(key, [...(seen.get(key) ?? []), row]);
    }
  }
  const found = [];
  for (const [key, carriers] of seen) {
    if (carriers.length < 2) continue;
    const pages = carriers.map((row) => new Set(row.pages));
    // Disjoint across *every* pair: two sections of one page sharing a string is a duplication on
    // that page, which is a different fact and one the reviewer can see for themselves.
    const shared = pages.some((set, at) =>
      pages.some((other, index) => index !== at && [...set].some((path) => other.has(path))));
    if (shared) continue;
    const [field, value] = key.split("\u0000");
    found.push(`${field} ${JSON.stringify(preview(value))} — ${carriers.map((row) => row.id).sort(byText).join(", ")}`);
  }
  return found.sort(byText);
}

/** Below this many characters a repeated field value is a coincidence rather than an adoption. */
const ADOPTED_FLOOR = 12;

/**
 * The fields the target instance must declare before this proposal can be applied (SH436).
 *
 * <h3>Why the precondition lives here</h3>
 *
 * <p>On a real instance `Theme` declared `TITLE`, `CSS` and `JAVASCRIPT` and **no `TOKENS`**: the
 * post-type predates SH195, `ensure` deliberately never touches an existing one, and SH289's
 * reconcile is additive *and* opt-in. All three of those are right — a widget change is a different
 * decision from a missing field — and the repair is one call. The gap is *when* anybody learns.
 * `clone` and `propose` never talk to an instance by design, so the first moment a missing field
 * matters is when a token is written, and a project spends real effort on a capture, a conversion and
 * a review before finding out the target cannot hold half of it.
 *
 * <p>So the artefact states its own preconditions. It costs no request and no instance: the theme half
 * knows it needs `Theme.TOKENS`, the scripts half knows it needs `SiteScripts.SCRIPTS`, and an applier
 * has something to check before it writes rather than a failure to explain afterwards. `shio verify`
 * still carries the finding — that is the right home for the *check* — this is the same fact, said
 * early enough to act on.
 *
 * <p>Every row names the repair, because a precondition a reader cannot satisfy is a complaint.
 */
function requirements({ theme, scripts }) {
  const rows = [];
  if (theme?.rows?.length) {
    rows.push(needs("Theme", "TOKENS", "the captured design tokens are written into it"));
  }
  if (scripts?.length) {
    rows.push(needs("SiteScripts", "SCRIPTS", "the third-party scripts the capture found go into it"));
  }
  return rows;
}

function needs(postType, field, why) {
  return {
    postType,
    field,
    why,
    fix: `POST /api/v2/render/post-types/reconcile adds it and changes nothing else (or a `
      + `render.provision op with data.reconcile:true). GET /api/v2/render/post-types/drift `
      + `answers whether it is needed without writing.`,
  };
}

/** One comparator, so "sorted" means the same thing in every field of the file (P5). */
const byText = (a, b) => (a === b ? 0 : a < b ? -1 : 1);

function describe(block, guess, index) {
  if (!guess) {
    const postType = proposePostType(block, index);
    return {
      type: null,
      evidence: "nothing in the vocabulary fits this block",
      fields: {},
      loss: lossFor(block, new Set()),
      // SH506 — absent rather than empty when there is nothing to derive. An absent key is what a
      // reader gets for "this block offers no post-type"; `{fields: []}` reads as an offer.
      ...(postType ? { postType } : {}),
    };
  }
  const { fields, consumed } = fieldsFor(guess.type, block);
  return {
    type: guess.type,
    evidence: guess.evidence,
    fields,
    loss: lossFor(block, consumed),
  };
}

/**
 * The site-wide scripts a capture found, as a `SiteScripts` list somebody can accept (SH389).
 *
 * <h3>The half that makes SH389 a task at all</h3>
 *
 * SH377 already inventories the third-party scripts a source loads, and until there was somewhere to
 * put them they were **silently dropped** — so the replica was subtly not the site: no consent banner,
 * no analytics, no chat. This proposes the JSON the `SCRIPTS` field takes, in the order the capture saw
 * them, which is the property the whole task is about: consent has to load before what it gates.
 *
 * <h3>Every entry is proposed for the head, deliberately</h3>
 *
 * A tag the source loaded in the head and a replica loads at the end of the body is a different site,
 * and moving one *later* is the direction that breaks a consent ordering. A reviewer moving an entry
 * down is a decision; this command proposing it is not.
 *
 * <h3>A category is recognised, never inferred</h3>
 *
 * A consent tool cannot be deduced from a URL, but the common hosts are recognisable by name, and an
 * entry whose category nobody set is one a reviewer has to classify by hand — which for a consent gate
 * is exactly the work that matters. So a known host gets a `category`, everything else gets `""`, and
 * the report counts how many need a human. A guess presented as a fact would be worse than a blank.
 */
export function proposeScripts(inventory) {
  return (inventory?.scripts ?? [])
    .filter((script) => script?.host)
    .map((script) => ({
      src: /^https?:\/\//.test(script.sample ?? "")
        ? script.sample
        : `https://${script.host}${script.sample ?? ""}`,
      inline: "",
      position: "head",
      defer: Boolean(script.defer),
      async: Boolean(script.async),
      category: categoryOf(script.host),
      refs: script.refs ?? 0,
    }));
}

/**
 * The consent category a host is recognisably in, or `""`.
 *
 * A short list on purpose. A longer one buys little and risks a wrong label on somebody's consent
 * gate, which is the one field here where being confidently wrong is worse than being empty.
 */
function categoryOf(host) {
  const name = String(host ?? "").toLowerCase();
  // Consent first, because it is the category whose ORDER is the compliance property — and the one
  // worth recognising even if nothing else were.
  if (/cookiebot|onetrust|cookielaw|osano|iubenda|termly|usercentrics/.test(name)) return "consent";
  if (/googletagmanager|google-analytics|segment|mixpanel|plausible|matomo|hotjar/.test(name)) {
    return "analytics";
  }
  if (/doubleclick|adservice|adsystem|linkedin|tiktok|facebook/.test(name)) return "marketing";
  if (/intercom|drift|zendesk|crisp|tawk|livechat/.test(name)) return "functional";
  return "";
}

/**
 * A candidate's id: a hash of its structure and nothing else.
 *
 * <p>Which is what makes an acceptance outlive a re-run. A positional id would renumber the moment
 * the source added a block, and every entry in `conversion-plan.json` would then name a different
 * candidate than the one the reviewer read — silently, since a plan file cannot tell.
 */
export function idFor(sig) {
  let hash = 0x811c9dc5;
  const text = String(sig);
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return `b${hash.toString(16).padStart(8, "0")}`;
}

/** Byte-stable JSON, so a re-run's diff is what the source changed and not what the file felt like. */
export function serializeProposal(proposal) {
  return `${JSON.stringify(proposal, null, 2)}\n`;
}

/** Byte-stable JSON for the review decisions, sorted so two accepts in either order are one file. */
export function serializePlan(plan) {
  const accepted = [...(plan.accepted ?? [])]
    .map((entry) => ({ block: entry.block, type: entry.type, ...(entry.reason ? { reason: entry.reason } : {}) }))
    .sort((a, b) => byText(a.block, b.block));
  return `${JSON.stringify({ version: PROPOSAL_VERSION, accepted }, null, 2)}\n`;
}

// ---------------------------------------------------------------------------------------------
// The report
// ---------------------------------------------------------------------------------------------

/**
 * What the caller reads: one line per candidate, plus the detail that makes it decidable.
 *
 * <p>Every section is bounded by `--limit` and every cut is declared, per `shio verify`'s rule — a
 * truncated report that did not say so reads as a complete one, and for this artefact that would
 * mean a reviewer believing they had seen every block that is about to change.
 */
export function describeProposal(proposal, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 0) : 20;
  const lines = [];
  const total = proposal.candidates.length;
  const unmatched = proposal.candidates.filter((row) => !row.type).length;
  lines.push(
    `shio propose: ${total} candidate block(s) across ${proposal.read.length} page(s) — ` +
      `${proposal.accepted} accepted (authorable), ${total - proposal.accepted} in fidelity mode, ` +
      `${unmatched} the vocabulary has no type for`,
  );
  lines.push("  an unaccepted block stays verbatim markup; accept one with --accept <id>");
  // SH511 — the choice stated as two good answers rather than as one with a penalty. Without this
  // the loss list reads as an argument for fidelity on every candidate, since dropped classes always
  // sound worse than none.
  lines.push("  fidelity keeps the source's appearance and offers no fields; authorable gives these "
    + "fields, presented by the site's own theme");

  for (const row of proposal.candidates.slice(0, limit)) {
    const where = row.pages.length > 1
      ? `${row.pages.length} pages`
      : row.pages[0];
    lines.push("");
    lines.push(
      // SH506 — a typeless candidate may now have no post-type to offer either, and the honest
      // label for it is what it is: a block the vocabulary does not fit and nothing can be derived
      // from. `NEW undefined` was what reading `.name` off an absent proposal would have printed.
      `${row.id}  ${row.type ?? (row.postType ? `NEW ${row.postType.name}` : "no type")}  `
        + `[${row.mode}]  on ${where}` +
        `${row.pages.length > 1 ? ` (e.g. ${row.pages[0]})` : ""}`,
    );
    lines.push(`  why: ${row.evidence}`);
    const fields = Object.entries(row.fields);
    if (fields.length) {
      lines.push(`  fields: ${fields.map(([name, value]) => `${name}=${preview(value)}`).join("  ")}`);
    }
    if (row.postType) {
      lines.push(
        `  new post-type ${row.postType.name}: `
          + `${row.postType.fields.map((field) => `${field.name}:${field.kind}`).join(", ")}`,
      );
    }
    // SH511 — two lines, because they are two conversations. `discards` is damage and the reviewer
    // decides whether to accept it; `replaces` is the mode's contract and there is nothing to weigh.
    // An empty half is omitted rather than printed as "nothing", and both empty is one line: the
    // split doubled the per-candidate cost, and paying it to say twice that there is nothing to say
    // is the part of that with no reader (P3).
    const discarded = describeDiscarded(row.loss);
    const replaced = describeReplaced(row.loss);
    if (discarded || !replaced) {
      lines.push(`  discards: ${discarded || "nothing this reader can see"}`);
    }
    if (replaced) lines.push(`  replaces: ${replaced}`);
  }
  if (total > limit) lines.push("", `… ${total - limit} more candidate(s) in ${options.out ?? PROPOSAL_FILE}`);
  if (proposal.theme) {
    lines.push("");
    lines.push(describeTheme(proposal.theme, options).trimEnd());
  }
  if (proposal.scripts?.length) {
    lines.push("");
    lines.push(describeScripts(proposal.scripts, options).trimEnd());
  }
  // SH436 — printed before the "nothing was applied" line, because it is the sentence that decides
  // whether applying this will work at all, and a reader who stops at the candidates has still read it.
  if (proposal.requires?.length) {
    lines.push("");
    lines.push(`requires on the target instance (${proposal.requires.length}):`);
    for (const row of proposal.requires) {
      lines.push(`  ${row.postType}.${row.field} — ${row.why}`);
    }
    lines.push(`  ${proposal.requires[0].fix}`);
  }
  lines.push("", `proposal: ${options.out ?? PROPOSAL_FILE} (nothing was applied)`);
  // The theme's own notes are already in `proposal.notes`, which is what `describeTheme` prints too —
  // so they are dropped here rather than printed twice. One list, one place.
  for (const note of proposal.notes.filter((note) => !(proposal.theme?.notes ?? []).includes(note))) {
    lines.push(`  note: ${note}`);
  }
  return `${lines.join("\n")}\n`;
}

/**
 * The site-wide scripts, and how many still need a category (SH389).
 *
 * The unclassified count is the line that matters: those are the entries a human has to place in a
 * consent gate, and it is the only number here that names remaining work rather than a finding.
 */
function describeScripts(scripts, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 1) : 20;
  const unclassified = scripts.filter((row) => !row.category).length;
  const lines = [
    `site scripts: ${scripts.length} third-party script(s) the source loaded`
      + (unclassified ? `, ${unclassified} with no category a reviewer has to set` : ""),
  ];
  for (const row of scripts.slice(0, limit)) {
    lines.push(`  ${row.category || "(uncategorised)"}  ${row.src}`
      + `  ${row.refs} page(s)${row.defer ? "  defer" : ""}${row.async ? "  async" : ""}`);
  }
  if (scripts.length > limit) lines.push(`  … ${scripts.length - limit} more in the proposal`);
  lines.push("  note: every entry is proposed for the head — moving one later is the direction that "
    + "breaks a consent ordering, so a reviewer does that deliberately");
  return lines.join("\n");
}

/**
 * What accepting this block **discards** — the half that is still a loss (SH511).
 *
 * <p>One list became two, and this is the one a reviewer should weigh. `114×<circle> 81×<path>
 * 48×<svg>` is content the conversion genuinely drops — an icon set is not presentation — and
 * `behaviour: 3×onclick` is a page that still looks right and stops working. Neither is replaced by
 * anything; both are gone.</p>
 */
function describeDiscarded(loss) {
  const parts = [];
  if (loss.elements.length) {
    parts.push(loss.elements.slice(0, 6).map((each) => `${each.count}×<${each.name}>`).join(" "));
  }
  if (loss.behaviour.length) {
    parts.push(`behaviour: ${loss.behaviour.slice(0, 4).map((each) => `${each.count}×${each.name}`).join(" ")}`);
  }
  return parts.join("; ");
}

/**
 * What accepting it **replaces** — the mode's contract, stated as one rather than as damage (SH511).
 *
 * <p>`loses: 311 class name(s) — all styling` read as an argument for fidelity on **every** candidate,
 * because 311 dropped classes always sounds worse than none. That is how a session accepted candidates
 * and then went looking for the styling as though it should have survived. Under SH507 the source's
 * appearance is not kept by this mode by design, so the count stays — a reviewer is entitled to know
 * how much of the source's styling is being set aside — and the sentence around it stops calling a
 * decision a loss.</p>
 *
 * <p><b>The line carries the count and the label does the rest</b>, and that is P3 rather than
 * terseness for its own sake. The first draft repeated "presented by the site's own theme instead" on
 * every candidate and took the report from ≈74 to ≈120 tokens per candidate — landing exactly on
 * `propose-cost.test.mjs`' ceiling. What the sentence says is true of the *mode*, not of this block,
 * so it belongs in the header once (P2). Repeating it N times is paying per candidate for a fact that
 * does not vary.</p>
 */
function describeReplaced(loss) {
  return loss.classes ? `${loss.classes} class name(s) of the source's styling` : "";
}

function preview(value) {
  if (Array.isArray(value)) return `[${value.length}]`;
  const raw = String(value ?? "");
  return raw.length <= 40 ? `"${raw}"` : `"${raw.slice(0, 39)}…"`;
}
