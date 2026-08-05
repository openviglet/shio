/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH379 — the convert phase's deliverable, which is a **proposal** and not a conversion.
 *
 * ## Two honest modes, and why shipping either one alone would be wrong
 *
 * A captured page can become two things. **Fidelity mode** writes its markup verbatim as a `Theme`
 * plus a `PageLayout` and its `Region`s (SH191 sidecars): it renders identically and *nothing about
 * it is authorable* — a curator editing "the hero headline" is editing an HTML file. **Authorable
 * mode** maps repeated markup onto the SH95 section vocabulary: the page becomes content, and the
 * pixels have moved. Both are defensible; neither is the answer, because which one is right is a
 * decision about *this block on this site* and nobody but the reviewer can make it.
 *
 * So what ships is the artefact between them: for every candidate block, the pages it recurs on, the
 * section type it looks like, the fields it would carry, and — the half that makes it honest — **the
 * markup it would lose**. Entries are accepted one at a time; an unaccepted block stays in fidelity
 * mode. That is P7 and P8 in one file: the appliable unit is the accepted mapping, and a guess that
 * silently restructured somebody's homepage would be the worst surprise this product could produce.
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
import { describeTheme } from "./theme.mjs";

/** The proposal's shape version. Bumped when a consumer would read an old file wrongly. */
export const PROPOSAL_VERSION = 1;

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
 * @returns {{tag: string, attrs: Record<string,string>, children: Array}} the root
 */
export function buildTree(html, tokenize, parseAttrs) {
  const root = { tag: "#root", attrs: {}, children: [] };
  const stack = [root];
  const top = () => stack.at(-1);
  for (const token of tokenize(html)) {
    if (token.type === "text") {
      const text = String(token.text ?? "");
      if (text.trim()) top().children.push({ tag: "#text", text, attrs: {}, children: [] });
      continue;
    }
    const name = token.name;
    if (token.type === "close") {
      const at = stack.findLastIndex((node) => node.tag === name);
      // Not on the stack: a stray closer. Dropping it is the conservative choice — unwinding to
      // nothing would empty the stack and re-root the rest of the document.
      if (at > 0) stack.length = at;
      continue;
    }
    const closes = IMPLICIT_CLOSE;
    while (stack.length > 1 && closes[top().tag]?.has(name)) stack.pop();
    const node = { tag: name, attrs: parseAttrs(token.attrs), children: [] };
    top().children.push(node);
    if (!token.self && !VOID.has(name)) stack.push(node);
  }
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
 */
export function blocksOf(root) {
  let node = contentRoot(root);
  for (;;) {
    const kids = node.children.filter(isElement);
    if (kids.length !== 1 || !GENERIC.has(kids[0].tag) || !kids[0].children.some(isElement)) break;
    const units = unitsAmong(kids[0]);
    if (units.length && units.every((unit) => !SECTIONING.has(unit.tag))) break;
    node = kids[0];
  }
  return node.children.filter((child) => isElement(child) && !isInline(child));
}

const isElement = (node) => node.tag !== "#text";

/** All the visible text under a node, whitespace collapsed. */
export function text(node) {
  if (node.tag === "#text") return String(node.text ?? "").replace(/\s+/g, " ");
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
    name: () => (image?.attrs.alt || (link && text(link))) ?? null,
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
  return { name: postTypeName(block, index), fields };
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
  const read = [];

  for (const page of (inventory.pages ?? []).toSorted((a, b) => byText(a.path, b.path))) {
    const source = markup.get(page.path);
    if (typeof source !== "string") continue;
    read.push(page.path);
    const tree = buildTree(html.stripNonMarkup(source), html.tokenize, html.parseAttrs);
    const blocks = blocksOf(tree);
    blocks.forEach((block, index) => {
      const sig = signature(block);
      const id = idFor(sig);
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
    candidates: rows,
    accepted: rows.filter((row) => row.mode === "authorable").length,
    // SH380 — the theme is the same artefact's other half. A separate file would be a second thing to
    // find, and the two are read together: a block's loss report says "3 class names — all styling",
    // and the theme is where that styling went.
    ...(theme ? { theme } : {}),
    // SH389 — and the third half: the scripts SH377 found and nothing could hold. Computed from the
    // inventory alone, so it needs neither markup nor stylesheets and is present on every proposal a
    // capture can produce.
    scripts: proposeScripts(inventory),
    notes: [...notes, ...(theme?.notes ?? [])],
  };
}

/** One comparator, so "sorted" means the same thing in every field of the file (P5). */
const byText = (a, b) => (a === b ? 0 : a < b ? -1 : 1);

function describe(block, guess, index) {
  if (!guess) {
    return {
      type: null,
      evidence: "nothing in the vocabulary fits this block",
      fields: {},
      loss: lossFor(block, new Set()),
      postType: proposePostType(block, index),
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

  for (const row of proposal.candidates.slice(0, limit)) {
    const where = row.pages.length > 1
      ? `${row.pages.length} pages`
      : row.pages[0];
    lines.push("");
    lines.push(
      `${row.id}  ${row.type ?? `NEW ${row.postType.name}`}  [${row.mode}]  on ${where}` +
        `${row.pages.length > 1 ? ` (e.g. ${row.pages[0]})` : ""}`,
    );
    lines.push(`  why: ${row.evidence}`);
    const fields = Object.entries(row.fields);
    if (fields.length) {
      lines.push(`  fields: ${fields.map(([name, value]) => `${name}=${preview(value)}`).join("  ")}`);
    }
    if (row.postType) {
      lines.push(
        `  new post-type ${row.postType.name}: ` +
          `${row.postType.fields.map((field) => `${field.name}:${field.kind}`).join(", ") || "no fields could be derived"}`,
      );
    }
    const loss = describeLoss(row.loss);
    lines.push(`  loses: ${loss}`);
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

function describeLoss(loss) {
  const parts = [];
  if (loss.elements.length) {
    parts.push(loss.elements.slice(0, 6).map((each) => `${each.count}×<${each.name}>`).join(" "));
  }
  if (loss.behaviour.length) {
    parts.push(`behaviour: ${loss.behaviour.slice(0, 4).map((each) => `${each.count}×${each.name}`).join(" ")}`);
  }
  if (loss.classes) parts.push(`${loss.classes} class name(s) — all styling`);
  return parts.length ? parts.join("; ") : "nothing this reader can see";
}

function preview(value) {
  if (Array.isArray(value)) return `[${value.length}]`;
  const raw = String(value ?? "");
  return raw.length <= 40 ? `"${raw}"` : `"${raw.slice(0, 39)}…"`;
}
