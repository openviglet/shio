/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH695 — the regions reader for the side that `diff` reads.
 *
 * ## Why there are two of these
 *
 * SH687 gave the digest a region list: the page's landmarks in document order, each saying whether
 * an editor owns its markup (`sections`) or the conversion kept the source's (`verbatim`). It lives
 * in Java, in `ShHtmlRegions`, and reads the page **the instance renders**.
 *
 * `shio diff` reads a different artefact — the HTML the replica **serves** — through `scanPage` in
 * `clone.mjs`, which named `regions` zero times. So SH692's rule (a verbatim region judged against
 * the source, a section region against its declaration) had its reader on one side of the comparison
 * and nothing on the other.
 *
 * ## A stack, not a tree, and not a tag count
 *
 * The question is containment: *is this landmark inside an authored section*. SH687 answered it with
 * Jsoup and argued that counting tags is how a scanner attributes a region to the wrong owner — and
 * that is true of counting **occurrences**. A stack is a different thing: push on an open tag, pop
 * on its close, and "am I inside one" is the depth. It answers ancestry exactly, needs no tree, and
 * needs no dependency — which matters, because `cli/package.json` declares **no third-party
 * dependency at all** and adding an HTML parser to gain this would be the first.
 *
 * ## What it is deliberately not
 *
 * Not a general parser. It reads open and close tags, skips the two raw-text elements whose bodies
 * can contain anything that looks like markup, and treats a void element as having no children. Bad
 * nesting costs it a wrong depth — the same trade `ShHtmlOutline` documents — and the worst that can
 * do is attribute a region to the wrong owner, which is why the fixtures both readers are asserted
 * against (`test/fixtures/regions.json`) include the awkward shapes rather than only the neat ones.
 */

/** The same skeleton `ShHtmlRegions` counts, so the two cannot describe different pages. */
export const LANDMARK_TAGS = Object.freeze([
  "header", "nav", "main", "section", "article", "aside", "footer", "form", "table",
]);

/** What marks a region as content somebody authored — the pair the generated layout writes. */
export const AUTHORED_ATTRS = Object.freeze(["sh-post", "data-shio-section"]);

/** A region whose markup an editor owns. */
export const SECTIONS = "sections";

/** A region whose markup came from the source and was kept as it was. */
export const VERBATIM = "verbatim";

/** Elements with no close tag, so nothing may be pushed for them. */
const VOID = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param",
  "source", "track", "wbr",
]);

/** Elements whose body is text, not markup — a `</section>` inside one closes nothing. */
const RAW_TEXT = new Set(["script", "style", "textarea", "title"]);

const TAG = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:[^>"']|"[^"]*"|'[^']*')*)>/g;

/**
 * The page's landmarks in document order, with the owner of each one's markup.
 *
 * @param {string} html the HTML the replica serves
 * @returns {{tag: string, order: number, markup: string}[]} one entry per landmark, 1-based order
 */
export function readRegions(html) {
  if (typeof html !== "string" || !html.trim()) return [];
  const regions = [];
  const stack = [];
  let authoredDepth = 0;
  let order = 0;
  let rawText = null;

  TAG.lastIndex = 0;
  let match;
  while ((match = TAG.exec(html)) !== null) {
    const [, closing, rawName, attrs] = match;
    const tag = rawName.toLowerCase();

    // Inside <script>/<style>, only that element's own close tag means anything.
    if (rawText) {
      if (closing && tag === rawText) rawText = null;
      continue;
    }
    if (!closing && RAW_TEXT.has(tag)) {
      rawText = tag;
      continue;
    }

    if (closing) {
      // Pop to the matching open tag. An unclosed <p> before a </div> must not take the div's
      // frame with it, and a close tag matching nothing on the stack closes nothing at all.
      const at = stack.findLastIndex((frame) => frame.tag === tag);
      if (at < 0) continue;
      for (let i = stack.length - 1; i >= at; i--) {
        if (stack[i].authored) authoredDepth--;
      }
      stack.length = at;
      continue;
    }

    const authored = AUTHORED_ATTRS.some((name) => hasAttribute(attrs, name));
    const isLandmark = LANDMARK_TAGS.includes(tag);
    if (isLandmark) {
      order++;
      // `authored || authoredDepth > 0` — the element's own marker counts, because the generated
      // layout writes `sh-post` ON the section element rather than around it.
      regions.push({ tag, order, markup: authored || authoredDepth > 0 ? SECTIONS : VERBATIM });
    }
    // Void elements have no children, so they open no frame.
    //
    // A trailing slash does NOT make a non-void element self-closing, and the first version of this
    // treated it as if it did — which is how `<section sh-post=a1 /><footer>` came out with the
    // footer as verbatim while the Java reader said `sections`. HTML is not XHTML: the slash is
    // ignored and the section stays open, so the footer really is inside it, and a browser agrees
    // with Jsoup. Found by the shared fixtures on their first run (SH695), before either reader
    // scored anything — which is what having two implementations agree by assertion is for.
    if (VOID.has(tag)) continue;
    if (authored) authoredDepth++;
    stack.push({ tag, authored });
  }
  return regions;
}

/** Whether a tag's attribute string carries `name`, bare or valued. */
function hasAttribute(attrs, name) {
  const pattern = new RegExp(`(^|[\\s/])${name}(\\s*=|[\\s/>]|$)`, "i");
  return pattern.test(attrs);
}

/** How many of a page's landmarks an editor owns — the number `diff` scores per region with. */
export function countByMarkup(regions) {
  const counts = { [VERBATIM]: 0, [SECTIONS]: 0 };
  for (const region of regions) counts[region.markup] += 1;
  return counts;
}
