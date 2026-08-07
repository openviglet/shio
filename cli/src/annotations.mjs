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
 * @param {{types: Map<string,string>, fields: Map<string,string[]>}} model
 *   `types` maps a post id to its post-type name; `fields` maps a post-type name to the field names
 *   it declares. A post whose type is unknown is reported as unresolved rather than as complete —
 *   "we could not check this" and "this is fine" are different answers (SH410's rule, one surface
 *   along).
 * @returns {{posts: object[], orphans: object[], declared: number, annotated: number,
 *   missing: number, unresolved: number}}
 */
export function joinDeclared(scan, { types = new Map(), fields = new Map() } = {}) {
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
    };
  });

  return {
    posts,
    orphans: scan.orphans,
    declared: posts.reduce((total, row) => total + row.declared.length, 0),
    annotated: posts.reduce((total, row) => total + row.annotated.length, 0),
    missing: posts.reduce((total, row) => total + row.missing.length, 0),
    unresolved: posts.filter((row) => !row.resolved).length,
  };
}

/**
 * What a reader sees: the answer first, then the arithmetic that produced it.
 *
 * <p>The line that was hand-written four times is `4/4` per post, so it is printed exactly like
 * that — and the two numbers a reader has to get right, the collection rows and the attribute
 * form, are the ones the format makes impossible to skip.</p>
 */
export function describeAnnotations(report, { target } = {}) {
  const { posts, orphans, declared, annotated, missing, unresolved } = report;
  const lines = [
    `shio annotations: ${posts.length} post(s) on ${target ?? "the page"} — `
      + `${annotated} of ${declared} declared field(s) annotated`
      + `${missing ? `, ${missing} NOT editable` : ""}`
      + `${unresolved ? `, ${unresolved} whose type this could not resolve` : ""}`,
  ];
  for (const row of posts) {
    const label = `${row.post}${row.type ? `  (${row.type})` : ""}${row.page ? "  [page]" : ""}`;
    if (!row.resolved) {
      lines.push(`  ${label}  ${row.annotated.length} annotated — type not resolved, NOT checked`);
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
  if (missing) {
    lines.push(
      `  ${missing} declared field(s) are RENDERED and not editable. Rendering is not annotation: `
        + "a collection row that draws with no sh-post and no sh-field looks finished and cannot "
        + "be curated (SH465, SH475)",
    );
  }
  return `${lines.join("\n")}\n`;
}
