/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH588 — the measurement that kept being rewritten.
 *
 * <p>`SKILL.md` carries seven checks for judging a replica. Two are verbs — `verify` and `diff`.
 * The other five are paragraphs, and the most-used is <b>count, do not eyeball</b>: take the props
 * a page emits and compare them against the fields each section type declares, <i>including every
 * collection row</i>, and <i>in both declaration forms</i>, because a value that lands in an
 * attribute is declared by `sh-field-attr` beside the element's own `sh-field`.</p>
 *
 * <p>That procedure was hand-written four times across four rounds, slightly differently each time.
 * <b>Once it was wrong:</b> a complete page read as 9 of 10 because only `data-shio-prop` was
 * counted, and a regression was nearly filed against SH503 on the strength of it. The rule about
 * counting both forms exists in `SKILL.md` <i>because</i> of that, which is the definition of a
 * procedure that cannot be left to a reader.</p>
 *
 * <h2>What this asks that SH524 does not</h2>
 *
 * <p>SH524 asks the question at the <b>template</b>: which declared field does no template draw.
 * This asks it at the <b>page</b>: which field does a template draw and not make editable. They are
 * worth having apart — the second is how SH465 and SH475 both shipped looking reasonable, since a
 * collection row that renders with no `sh-post` and no `sh-field` is a page a human calls finished
 * and an editor cannot touch.</p>
 *
 * <h2>Pure, and no browser</h2>
 *
 * <p>The annotations are emitted by the server's own renderer (`ShRegionComposer`), so they are in
 * the bytes `/preview/...?editor=true` already returns. A browser would add a Chromium process to
 * answer a question that is settled before any script runs — and `shio audit` is the command that
 * pays that cost, deliberately and for a different question.</p>
 */
import { parseAttrs } from "./clone.mjs";

/** The rendered names. `sh-field`/`sh-post` are the authoring form; these are what a page carries. */
export const PROP = "data-shio-prop";
export const PROP_ATTR = "data-shio-prop-attr";
export const POST = "data-shio-post";
export const POST_TYPE = "data-shio-post-type";

/** Same regex trade `clone.mjs` documents: linear, and a `>` inside a quoted value ends a tag. */
const TAG = /<(\/?)([a-zA-Z][-\w:]*)(?=[\s/>])([^>]*)>/g;

/** Elements with no close tag, so nothing may be nested inside them. */
const VOID = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source",
  "track", "wbr",
]);

/**
 * The props a page emits, each attributed to the post that owns it.
 *
 * <p>Ownership is by <b>containment</b>, which is the renderer's own rule: a field resolves to the
 * nearest enclosing `data-shio-post`, so a collection row's declaration wins over the page's
 * without either having to know about the other. Getting that wrong is not cosmetic — it is what
 * turns "the grid's two cards carry no annotation" into "the page has plenty".</p>
 *
 * <p>A field with no enclosing owner is kept as an <b>orphan</b> rather than dropped. The renderer
 * annotates a `sh-field` that resolves to no post on purpose (SH169: a mistake the author can see
 * beats markup that quietly is not editable), so silence here would hide exactly the case the
 * renderer went out of its way to make visible.</p>
 *
 * @param {string} html the rendered page, as served
 * @returns {{posts: Array<{post: string, type: string|null, page: boolean,
 *   fields: Array<{field: string, form: "text"|"attr", attribute: string|null}>}>,
 *   orphans: Array<{field: string, form: string, attribute: string|null}>}}
 */
export function scanAnnotations(html) {
  const byPost = new Map();
  const orphans = [];
  const stack = [];
  let current = null;

  const own = (postId, { type = null, page = false } = {}) => {
    let row = byPost.get(postId);
    if (!row) {
      row = { post: postId, type: null, page: false, fields: [] };
      byPost.set(postId, row);
    }
    if (type) row.type = type;
    if (page) row.page = true;
    return row;
  };

  TAG.lastIndex = 0;
  let match;
  while ((match = TAG.exec(String(html ?? ""))) !== null) {
    const [, closing, rawName, raw] = match;
    const name = rawName.toLowerCase();
    if (closing) {
      // Tolerant: unwind to the matching open tag if there is one, and ignore a stray close
      // otherwise. Real markup from this renderer is well-formed; a scanner that threw on the one
      // page that is not would be a measurement nobody can run on the page they need it for.
      const at = stack.findLastIndex((frame) => frame.tag === name);
      if (at >= 0) {
        stack.length = at;
        current = stack.length ? stack[stack.length - 1].post : null;
      }
      continue;
    }

    const attrs = parseAttrs(raw);
    const declared = (attrs[POST] ?? "").trim();
    const owner = declared || current;
    if (declared) {
      own(declared, { type: (attrs[POST_TYPE] ?? "").trim() || null, page: name === "body" });
    }

    const fields = [];
    const prop = (attrs[PROP] ?? "").trim();
    if (prop) fields.push({ field: prop, form: "text", attribute: null });
    // SH503's form, and the reason this command exists: one element may carry two fields — a
    // link's text and its destination — and counting only the first under-reports every
    // attribute-valued field. That is how a complete page once read as 9 of 10.
    for (const pair of parsePairs(attrs[PROP_ATTR])) fields.push({ ...pair, form: "attr" });

    if (fields.length) {
      if (owner) own(owner).fields.push(...fields);
      else orphans.push(...fields);
    }

    const selfClosing = /\/\s*$/.test(raw);
    if (!VOID.has(name) && !selfClosing) {
      stack.push({ tag: name, post: owner ?? null });
      current = owner ?? null;
    }
  }

  return { posts: [...byPost.values()], orphans };
}

/** `"title:alt,href:href"` → the pairs, in the order the renderer wrote them. */
function parsePairs(value) {
  const raw = String(value ?? "").trim();
  if (!raw) return [];
  const pairs = [];
  for (const token of raw.split(",")) {
    const cut = token.indexOf(":");
    if (cut <= 0) continue;
    const field = token.slice(0, cut).trim();
    const attribute = token.slice(cut + 1).trim();
    if (field && attribute) pairs.push({ field, attribute });
  }
  return pairs;
}

/**
 * The join: what each post's type declares, against what the page annotated (SH588).
 *
 * <p><b>A field is covered by either form.</b> `data-shio-prop="title"` and
 * `data-shio-prop-attr="title:alt"` both make `title` editable, and requiring the first would fail
 * every page that draws a field into an attribute — the mistake this command exists to stop a
 * reader making by hand.</p>
 *
 * <p><b>More declarations than fields is expected and is not a finding.</b> An empty slot is
 * annotated so a curator can fill it, and one field may be drawn twice (its text and its href), so
 * the count that means something is the count of declared fields with <i>no</i> annotation.</p>
 *
 * @param {object} scan `scanAnnotations`'s output
 * <h3>Drawn is asked, not assumed (SH882)</h3>
 *
 * <p>`missing` is `declared minus annotated`, which is an honest name for what it computes and was
 * being <b>described</b> as something else: *N declared field(s) are RENDERED and not editable*.
 * That sentence is true of SH465's population — a collection row that draws with no `sh-post` and
 * no `sh-field` — and it was a claim about every other member of the same set, made without
 * evidence. Measured on a verbatim page: four fields reported as RENDERED, none of them anywhere in
 * the served bytes.</p>
 *
 * <p>So `rendered` is the subset the page actually draws, decided by looking: a declared field
 * whose <b>stored value</b> appears in the HTML. That is the one worth an exit code — the page
 * shows it and a curator cannot touch it. The rest is a field the template does not draw, which is
 * `shio verify`'s `unused-field` question at the template and not this page's defect. Without
 * `values` the split cannot be made and everything stays in `missing`, reported as the one thing
 * this join can prove: declared, and not annotated.</p>
 *
 * @param {{types: Map<string,string>, fields: Map<string,string[]>,
 *   values: Map<string,object>, html: string}} model
 *   `types` maps a post id to its post-type name; `fields` maps a post-type name to the field names
 *   it declares. A post whose type is unknown is reported as unresolved rather than as complete —
 *   "we could not check this" and "this is fine" are different answers (SH410's rule, one surface
 *   along). `values` maps a post id to its stored data, and with `html` is what decides `rendered`.
 * @returns {{posts: object[], orphans: object[], declared: number, annotated: number,
 *   missing: number, rendered: number, unresolved: number}}
 */
export function joinDeclared(scan, {
  types = new Map(), fields = new Map(), values = new Map(), html = "",
} = {}) {
  const posts = scan.posts.map((row) => {
    const type = row.type ?? types.get(row.post) ?? null;
    const declared = type && fields.has(type) ? [...fields.get(type)] : null;
    const annotated = [...new Set(row.fields.map((entry) => entry.field))].sort();
    const missing = declared
      ? declared.filter((field) => !annotated.includes(field)).sort()
      : [];
    return {
      post: row.post,
      type,
      page: row.page,
      resolved: Boolean(declared),
      declared: declared ? declared.slice().sort() : [],
      annotated,
      // Every declaration, both forms, kept for a reader who has to go and look at one.
      forms: row.fields,
      missing,
      // SH882 — of those, the ones the page can be seen to draw. Empty where nothing supplied the
      // values, which is the honest answer and not zero findings.
      rendered: missing.filter((field) => drawn(values.get(row.post), field, html)),
    };
  });

  return {
    posts,
    orphans: scan.orphans,
    declared: posts.reduce((total, row) => total + row.declared.length, 0),
    annotated: posts.reduce((total, row) => total + row.annotated.length, 0),
    missing: posts.reduce((total, row) => total + row.missing.length, 0),
    rendered: posts.reduce((total, row) => total + row.rendered.length, 0),
    unresolved: posts.filter((row) => !row.resolved).length,
  };
}

/**
 * Whether this post's stored value for `field` can be seen on the page (SH882).
 *
 * <p>The value and not the field name: a name never appears in rendered output, which is why the
 * old claim could not have been checked even in principle. Strings only, and non-empty — a number
 * or a boolean drawn into a page is indistinguishable from the same characters occurring anywhere
 * else, and a blank value is drawn as nothing.</p>
 *
 * <p><b>Markup is compared as its text.</b> An `Ace Editor - HTML` field holds a fragment the
 * renderer re-serialises, so byte equality is the wrong test and would answer *not drawn* for the
 * one field a verbatim page certainly draws. Collapsing both sides to their visible words is what
 * makes the comparison about the page rather than about jsoup.</p>
 *
 * <p>Substring rather than a parse, for `ShAnnotatedFields`' reason inverted: a parse of every page
 * for every field costs the command its whole budget, and the population is a post's own declared
 * fields, which is single digits.</p>
 *
 * <h3>A coincidence is not evidence (SH883)</h3>
 *
 * <p>That comment used to end *a false positive here costs one field reported as drawn when it is a
 * coincidence*, and the guard was against a non-string. On the first live run a
 * `SectionFeatureGrid` reported `columns` as DRAWN: the value is the string `"3"`, the page carried
 * no `data-shio-columns` at all, and the match was that character occurring somewhere in several
 * thousand. Beside two findings that were real, which is the worst place for a false one.</p>
 *
 * <p>A length threshold closes it and is a number nothing derives. What does derive is the thing an
 * annotation attaches to: an element whose <b>content</b> is the field. So a value of <b>one
 * token</b> has to be a complete text node or a complete attribute value, and only a value of
 * several may match as a substring — which is what keeps a whole markup blob answering correctly,
 * the case a fidelity page turns on. `"3"` fails the first; `Acme` passes it on
 * {@code <title>Acme</title>}.</p>
 */
function drawn(data, field, html) {
  const value = data && typeof data === "object" ? data[field] : null;
  if (typeof value !== "string" || !value.trim()) return false;
  // SH882 — a value carrying annotations of its own is not an uncuratable field. `--annotate`
  // stamps `sh-field` onto the blocks inside a verbatim page's `content` (SH868), so the markup is
  // drawn AND its editable parts are declared; calling the whole blob uncuratable is the same
  // overstatement this task is about, one level in. What it deliberately does not ask is whether
  // EVERY block inside was stamped — a block the proposal derived no field for is not annotated and
  // should not be, and this join has no way to tell those apart.
  if (value.includes(`${FIELD_ATTR}=`)) return false;
  const wanted = words(value);
  if (!wanted) return false;
  // SH883 — one token has to BE something on the page, not merely occur in it.
  return wanted.includes(" ") ? words(html).includes(wanted) : contentsOf(html).has(wanted);
}

/**
 * Every complete text node and attribute value on the page, collapsed (SH883).
 *
 * <p>What a one-token value is compared against. Both, because a field is drawn as an element's
 * text or into one of its attributes — SH503's two forms, which is the same pair `data-shio-prop`
 * and `data-shio-prop-attr` exist for — and a value drawn into `alt` or `href` is drawn.</p>
 */
function contentsOf(html) {
  const contents = new Set();
  const source = String(html ?? "");
  // Attribute values, either quoting — the same regex trade `clone.mjs` documents: linear, and a
  // `>` inside a quoted value ends a tag.
  for (const match of source.matchAll(/=\s*"([^"]*)"|=\s*'([^']*)'/g)) {
    const value = words(match[1] ?? match[2] ?? "");
    if (value) contents.add(value);
  }
  // Text nodes: whatever sits between one tag's close and the next tag's open.
  for (const match of source.matchAll(/>([^<]*)</g)) {
    const value = words(match[1]);
    if (value) contents.add(value);
  }
  return contents;
}

/** The authoring attribute a stored value carries where its own parts were made editable (SH868). */
const FIELD_ATTR = "sh-field";

/** Visible words, collapsed — the form both sides of the comparison are reduced to. */
function words(value) {
  return String(value).replace(/<[^>]*>/g, " ").replace(/\s+/g, " ").trim();
}

/**
 * What a reader sees: the answer first, then the arithmetic that produced it.
 *
 * <p>The line that was hand-written four times is `4/4` per post, so it is printed exactly like
 * that — and the two numbers a reader has to get right, the collection rows and the attribute
 * form, are the ones the format makes impossible to skip.</p>
 */
export function describeAnnotations(report, { target } = {}) {
  const { posts, orphans, declared, annotated, missing, rendered = 0, unresolved } = report;
  const lines = [
    `shio annotations: ${posts.length} post(s) on ${target ?? "the page"} — `
      + `${annotated} of ${declared} declared field(s) annotated`
      // SH882 — the headline says what the join proves. `NOT editable` was a verdict on a set that
      // includes every field the template simply does not draw, and on a verbatim page that is all
      // of them: the count read as four defects on a page with none.
      + `${missing ? `, ${missing} declared with no annotation` : ""}`
      + `${rendered ? ` (${rendered} of them DRAWN on the page)` : ""}`
      + `${unresolved ? `, ${unresolved} whose type this could not resolve` : ""}`,
  ];
  for (const row of posts) {
    const label = `${row.post}${row.type ? `  (${row.type})` : ""}${row.page ? "  [page]" : ""}`;
    if (!row.resolved) {
      // SH685 — the same sentence, now backed by an exit code. It used to be the whole guard: prose
      // saying "NOT checked" while nothing failed as the count grew, which is how eight unchecked
      // posts of nine sat under a headline that read like a measurement of the page.
      lines.push(
        `  ${label}  ${row.annotated.length} annotated — type not resolved, NOT checked: the page `
          + "renders sh-post for a post this instance cannot read",
      );
      continue;
    }
    lines.push(
      `  ${label}  ${row.annotated.length}/${row.declared.length}`
        + `${row.missing.length ? `  missing: ${row.missing.join(", ")}` : ""}`,
    );
    // Both forms, per field, because "which of these is an attribute" is the question the hand
    // count got wrong — and a field drawn only into an attribute looks absent without this line.
    for (const entry of row.forms) {
      lines.push(`      ${entry.field}${entry.attribute ? `  @${entry.attribute}` : ""}`);
    }
  }
  for (const orphan of orphans) {
    lines.push(
      `  ${orphan.field}: annotated with no enclosing ${POST} — the editor renders it read-only`,
    );
  }
  if (declared && annotated > declared) {
    lines.push(
      "  more annotations than declared fields is expected: an empty slot is annotated so a "
        + "curator can fill it, and one field may be drawn twice (its text and its href)",
    );
  }
  // SH882 — two sentences, because they are two findings and only one of them is this page's.
  if (rendered) {
    lines.push(
      `  ${rendered} declared field(s) are DRAWN on this page and not editable. Rendering is not `
        + "annotation: a collection row that draws with no sh-post and no sh-field looks finished "
        + "and cannot be curated (SH465, SH475)",
    );
  }
  if (missing - rendered > 0) {
    lines.push(
      `  ${missing - rendered} more are declared and neither drawn nor annotated, which is not a `
        + "defect on this page: a verbatim replica declares the vocabulary's fields and draws the "
        + "source's own markup instead. Whether a template should draw one is `shio verify`'s "
        + "unused-field, asked of the template",
    );
  }
  return `${lines.join("\n")}\n`;
}
