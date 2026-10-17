/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH377 — the capture pass: what a site that already exists actually contains.
 *
 * ## What this writes, and what it deliberately does not
 *
 * A replication is three phases — capture, convert, prove — and this is the first. It writes **no
 * content**: one inventory at `shio/.shio/source-inventory.json` and a report naming what it found.
 * That separation is the whole point. Everything downstream (SH378's bytes, SH379's markup mapping,
 * SH385's redirect map, SH387's fidelity report) is scoped off this one file, which is what makes a
 * conversion reviewable *before* it writes anything into somebody's instance.
 *
 * <p>`--markup` (SH379) is the one addition, and it does not weaken that: the captured HTML lands in
 * `.shio/`, beside the other derived snapshot, and no post, folder or asset is written. It is opt-in
 * because keeping a stranger's markup in the tree is a decision, and because a default that wrote
 * 200 files would make the sentence above stop being true at a glance.
 *
 * ## Why it is the CLI's half
 *
 * SH268 already settled the rule: a URL the **server** fetches is SH101's request-forgery surface
 * argued a second time, and "the instance does not run a browser" is a standing non-goal — while a
 * page whose markup arrives only after hydration needs one. The browser therefore lives where a
 * browser already lives (SH102's optional dynamic import), and the artefact is a **file**, which is
 * the cheapest thing an agent owns (P4).
 *
 * ## One extractor, two sources of markup
 *
 * `--render` does not add a second extraction path — it changes only where the HTML came from
 * (Playwright's serialized DOM instead of the response body). Two implementations of "what does
 * this page contain" would diverge, and the divergence would show up as a fidelity gap nobody
 * could locate. The inventory records which one produced it, the way `shio snapshot` names the
 * resolution of its comparison rather than letting a reader assume the stronger one.
 *
 * ## No timestamp in the file, on purpose
 *
 * A capture is a point in time, so a `fetchedAt` looks obviously right. It is not: P5 asks for
 * byte-identical serialization, and the only reason to keep an inventory in the tree is to **diff**
 * it — against the next crawl, to see what the source changed. A field that moves on every run
 * makes every diff non-empty and the artefact worthless for the one job it has. When the crawl ran
 * is a fact `git log` already holds.
 */

import { FETCH_CALL, URL_CALL, kindForUrl } from "./clone-assets.mjs";

/**
 * The inventory's shape version. Bumped when a consumer would read an old file wrongly.
 *
 * <p>**2 with SH387**, which added `unfollowed`: internal links the walk saw and never crawled. A
 * consumer of a version-1 file reads that as "nothing was left uncrawled", which is exactly the wrong
 * way round for a *coverage* report — so the version moves rather than the field being optional.
 *
 * <p>**3 since SH431**, which took the non-HTML responses out of `problems` and gave them their own
 * list. Same argument, and it is the reason this is a version rather than an addition: in a
 * version-2 file a data endpoint served correctly *is* a row in `problems`, so a reader that does not
 * know the difference reports it as a page nobody could read — and then calls every count in its own
 * report a lower bound because of it.
 *
 * <p>**4 since SH578**, for the same reason once more: a page row's `assets` was one number covering
 * the page's own references *and* its stylesheet's, and the two are different questions. It is now
 * `{own, style}` — the shape `links` has carried since SH554 — and `shio diff` refuses the old
 * number rather than scoring it, because that is the failure mode: a version-3 row read as `{own}`
 * would compare a page's markup against a replica's markup-plus-inlined-theme and report a gain on
 * a page that lost images.
 *
 * <p>**5 since SH581**, which cut the same fact on the other axis. `own` covered the whole captured
 * document, head and body, while a replica's page is the **body span** it stores under a layout that
 * owns the head. So `{own, head, style}`, and `shio diff` scores `own - head`. Refused rather than
 * scored for SH578's exact reason, one axis along: a version-4 row has no `head`, reading it as `0`
 * says every one of the source's favicons and its manifest lives in the page's body, and the
 * shortfall that produces — measured at **-28 on one page** — is not a loss the replica suffered.
 *
 * <p>**6 since SH867**, which adds a fact rather than splitting one: a per-kind census of the page's
 * own elements. What a replica lost was reported only in the vocabulary `scanPage` happened to
 * count, so a viewport that lost an eyebrow badge, a secondary call to action **and a four-cell
 * statistics band** reported `external links: 15 → 9`. A stats bar is list items and a call to action
 * is an anchor; counted as links, both are six of one number. Refused rather than scored for the
 * reason above: a version-5 row has no census, and reading a missing count as `0` would report every
 * heading and every button on the source as lost on a page that kept them all.</p>
 */
export const INVENTORY_VERSION = 6;

/** Where the inventory lives: beside `content-base.json`, which is the other derived snapshot. */
export const INVENTORY_FILE = "shio/.shio/source-inventory.json";

/**
 * Where `--markup` puts the captured HTML (SH379).
 *
 * <p>Under `.shio/` and not under `shio/content/`, which is the whole reason it is allowed to exist:
 * this is a **derived snapshot of somebody else's site**, the same category as `content-base.json`,
 * and SH377's promise is about *content*. `shio propose` reads only from here, never from the
 * network — so the choice `--render` made is made once, at capture, and every later proposal is
 * reproducible from the tree (P4, P5) instead of depending on what the source served this morning.
 */
export const MARKUP_DIR = "shio/.shio/source-markup";

/** Ceilings. A crawl with no bound is a crawl that finds a calendar and never returns. */
export const DEFAULT_DEPTH = 3;
export const DEFAULT_MAX_PAGES = 200;

/** The robots token this crawler answers to, and the UA it sends. */
export const ROBOTS_AGENT = "shio-clone";

/**
 * Below this much visible text, with scripts present, a page is probably rendered by its own
 * JavaScript — which is the case `--render` exists for, and the case a fetch-only crawl gets
 * silently and completely wrong. Reported as a note rather than guessed at.
 */
const HYDRATION_TEXT_FLOOR = 200;

// ---------------------------------------------------------------------------------------------
// URLs
// ---------------------------------------------------------------------------------------------

/**
 * Resolves one href against the page that carried it, or null when it is not a page or asset.
 *
 * <p>`mailto:`, `tel:`, `javascript:` and `data:` are dropped rather than recorded: an inventory
 * exists to be crawled and downloaded from, and none of those is either. The fragment goes too, so
 * `/about#team` and `/about` are one page instead of two.
 */
export function resolveHref(href, pageUrl) {
  const raw = String(href ?? "").trim();
  if (!raw || raw.startsWith("#")) return null;
  try {
    const url = new URL(raw, pageUrl);
    if (url.protocol !== "http:" && url.protocol !== "https:") return null;
    url.hash = "";
    return url;
  } catch {
    return null;
  }
}

/**
 * Whether a captured path is one a replica can be addressed at (SH557).
 *
 * <p>`post:<site>/<friendly-url>` (SH79) has no room for a `?`: the character terminates the
 * address and everything after it is a query on the request that carries it. So a captured path
 * containing one names a page the target **cannot represent** — not a page it might fail to
 * produce, a page there is no name for.</p>
 *
 * <p>The point of deciding it *here* is the phase. On vilt-group.com, 91 of 107 captured pages were
 * `/en/case-study?p=2`…`?p=24` across five locales; `clone` reported 198 pages and six notes, none
 * of them this, `propose` counted candidates over pages that would never exist, `convert` wrote and
 * published 204 objects, and `shio diff` — the last command in the chain — said *107 page(s)
 * compared, 91 the replica does not serve*. Everything in between was wasted and looked fine. The
 * inventory holds the paths and this is a regex, so the limit was knowable before any of it ran.</p>
 */
export function addressablePath(path) {
  return ADDRESSABLE_PATH.test(String(path ?? ""));
}

/** A friendly URL's grammar, as the SH79 address forms allow it. */
const ADDRESSABLE_PATH = /^\/[A-Za-z0-9\-._~/]*$/;

/** The path an inventory records: everything after the origin, query included. */
export function pathOf(url) {
  const parsed = typeof url === "string" ? new URL(url) : url;
  return `${parsed.pathname}${parsed.search}`;
}

/**
 * Same origin means the same host, strictly.
 *
 * <p>`example.com` and `www.example.com` are different origins here, which looks pedantic and is
 * the opposite: a seed that redirects between them is refused with the host it actually served, so
 * the caller crawls the site the site thinks it is rather than half of it.
 */
export function sameOrigin(url, origin) {
  try {
    const a = typeof url === "string" ? new URL(url) : url;
    const b = typeof origin === "string" ? new URL(origin) : origin;
    return a.host.toLowerCase() === b.host.toLowerCase();
  } catch {
    return false;
  }
}

/**
 * A page's links, split into the two the inventory counts (SH554).
 *
 * <p>`scanPage` emits `links` as an array of raw hrefs, because at scan time a page does not know
 * what origin it is being judged against. The split into internal and external is that second step,
 * and it lived inline in `buildInventory` — so the inventory had counts and a live scan had an
 * array, and `shio diff` read `links.external` off the array shape and got `undefined`. Every
 * replica then reported its external links gone and its internal ones as the *total*, which is how
 * an instrument in the CI gate came to report a loss on pages that had lost nothing.</p>
 *
 * <p>Returned as resolved `URL`s rather than counts because the inventory needs both: the count per
 * page, and the targets — an internal one it never crawled is `unfollowed`, an external one names a
 * host. One walk, one rule, and `countLinks` is the same answer with `.length` taken.</p>
 *
 * @param {{href: string}[]} links `scanPage`'s `links`
 * @param {string|URL} pageUrl the page that carried them, which relative hrefs resolve against
 * @param {string|URL} [originUrl] what counts as internal; the page's own origin when omitted,
 *   which is the right default for a replica judged on the instance that serves it
 */
export function classifyLinks(links, pageUrl, originUrl = pageUrl) {
  const internal = [];
  const external = [];
  for (const link of links ?? []) {
    const url = resolveHref(link?.href, pageUrl);
    if (!url) continue;
    (sameOrigin(url, originUrl) ? internal : external).push(url);
  }
  return { internal, external };
}

/** `classifyLinks` as the two numbers an inventory row and a diff both carry (SH554). */
export function countLinks(links, pageUrl, originUrl = pageUrl) {
  const split = classifyLinks(links, pageUrl, originUrl);
  return { internal: split.internal.length, external: split.external.length };
}

/**
 * A page's assets, split into the page's own references and its stylesheet's (SH578).
 *
 * <p><b>The one fact where a loss can hide inside a gain.</b> SH554 made both sides of `shio diff`
 * come out of one function; this fact still asked a different *question* at each end. The declared
 * side is a source page's own references. The produced side is a replica page **plus the theme
 * inlined into it**, whose every `url()` this scanner correctly finds (SH455) and then counted
 * as if the page had referenced it.</p>
 *
 * <p>Measured on a fidelity replica of vilt-group.com: every page reported exactly <b>+30</b> — 32
 * references of its own and 60 `url()` inside its `&lt;style&gt;`, against a captured theme holding
 * 59. Not noise, and not a gain. A gain is never a failure and that decision is right — a layout
 * adds links, and a gate that called that damage would fire on every working conversion — which is
 * exactly what makes this the fact where a real loss disappears: a page that dropped twenty of its
 * own images and picked up thirty theme `url()` reports <b>+10</b>, and the only instrument in the
 * chain that holds an expectation says the page is fine.</p>
 *
 * <p>So the repair is a boundary and not a formula. `own` is what the page's markup and its own
 * scripts reference; `style` is what a stylesheet does, and a theme's are the same on every page of
 * a site — so `shio diff` compares `own` per page and reports `style` once at the site level, where
 * thirteen identical numbers say less than one.</p>
 *
 * <h3>And the same fact on the other axis (SH581)</h3>
 *
 * <p>`own` is the whole captured **document**. A replica's page is the **body span** a `Page` stores,
 * presented by a layout that owns the head — so the source's nine `&lt;link&gt;` elements, of which
 * the stylesheet is inlined by the layout and the rest are favicons and a manifest, have no page to
 * be missing from. Measured on `/partner/`, the fidelity page of an otherwise authorable conversion:
 * the capture declares `own: 33`, the replica serves 5, `diff` calls it **-28** and exits 1, and the
 * captured markup is 155,881 characters against a stored body of 60,322.</p>
 *
 * <p>Wrong in both directions at once, which is what makes it worth a number rather than an
 * exemption: it overstates a loss of about four as 28, and it would hide a real body-side loss behind
 * a head-side count that happened to cover it. So `head` is carried beside `own` and `shio diff`
 * scores the difference — the same shape as the split above, and for the same reason.</p>
 *
 * @param {{from?: string, where?: string}[]} assets `scanPage`'s `assets`
 */
export function countAssets(assets) {
  let own = 0;
  let head = 0;
  let style = 0;
  for (const asset of assets ?? []) {
    // A row with no `from` predates this split. It is the page's own reference far more often than
    // not, and calling it `own` is the strict reading: it keeps the fact judged rather than excused.
    if (asset?.from === "style") {
      style += 1;
      continue;
    }
    own += 1;
    // And `where` is the strict reading of the second split, in the other direction: a row that does
    // not say it is in the head is counted as body, so an unmarked reference stays scored rather
    // than being excused into a fact nothing judges.
    if (asset?.where === "head") head += 1;
  }
  return { own, head, style };
}

// ---------------------------------------------------------------------------------------------
// robots.txt
// ---------------------------------------------------------------------------------------------

/**
 * Parses robots.txt into the group that applies to this crawler.
 *
 * <p>The rules that matter and are easy to get wrong: consecutive `User-agent` lines share one
 * group; an exact agent match beats `*` entirely rather than merging with it; a **longer** matching
 * rule wins; and on a tie `Allow` wins, which is what lets a site open one path inside a closed
 * subtree. `*` and `$` are honoured in patterns because a site that writes `Disallow: /*?` means it.
 *
 * <p>An absent or unreadable robots.txt allows everything — and says so in the report, because
 * "the site permitted this" and "nobody asked" are different claims about the same crawl.
 */
export function parseRobots(text, agent = ROBOTS_AGENT) {
  const groups = new Map();
  const sitemaps = [];
  let current = [];
  let previousWasAgent = false;
  for (const line of String(text ?? "").split(/\r?\n/)) {
    const stripped = line.replace(/#.*$/, "").trim();
    if (!stripped) continue;
    const split = stripped.indexOf(":");
    if (split < 0) continue;
    const field = stripped.slice(0, split).trim().toLowerCase();
    const value = stripped.slice(split + 1).trim();
    if (field === "sitemap") {
      if (value) sitemaps.push(value);
      continue;
    }
    if (field === "user-agent") {
      const name = value.toLowerCase();
      if (!previousWasAgent) current = [];
      previousWasAgent = true;
      if (!groups.has(name)) groups.set(name, { allow: [], disallow: [], crawlDelay: null });
      current.push(groups.get(name));
      continue;
    }
    previousWasAgent = false;
    for (const group of current) {
      if (field === "disallow") group.disallow.push(value);
      else if (field === "allow") group.allow.push(value);
      else if (field === "crawl-delay") {
        const delay = Number(value);
        if (Number.isFinite(delay) && delay >= 0) group.crawlDelay = delay;
      }
    }
  }
  const wanted = String(agent).toLowerCase();
  const group = groups.get(wanted) ?? groups.get("*") ?? { allow: [], disallow: [], crawlDelay: null };
  return {
    present: groups.size > 0 || sitemaps.length > 0,
    matched: groups.has(wanted) ? wanted : groups.has("*") ? "*" : null,
    sitemaps,
    crawlDelay: group.crawlDelay,
    rules: group,
  };
}

function ruleMatch(pattern, path) {
  const raw = String(pattern ?? "");
  // An empty Disallow means "nothing is disallowed" — it is the standard's way of allowing all,
  // and treating it as a prefix match on "" would refuse the whole site.
  if (!raw) return null;
  const anchored = raw.endsWith("$");
  const body = anchored ? raw.slice(0, -1) : raw;
  const source = body
    .split("*")
    .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join(".*");
  const re = new RegExp(`^${source}${anchored ? "$" : ""}`);
  return re.test(path) ? body.length : null;
}

/** @returns true when this crawler may fetch `path` under `robots`. */
export function robotsAllows(robots, path) {
  const rules = robots?.rules;
  if (!rules) return true;
  let allow = -1;
  let disallow = -1;
  for (const pattern of rules.allow) {
    const length = ruleMatch(pattern, path);
    if (length !== null && length > allow) allow = length;
  }
  for (const pattern of rules.disallow) {
    const length = ruleMatch(pattern, path);
    if (length !== null && length > disallow) disallow = length;
  }
  if (disallow < 0) return true;
  // Longest match wins, and Allow wins a tie — which is what opens one path inside a closed subtree.
  return allow >= disallow;
}

// ---------------------------------------------------------------------------------------------
// The HTML scanner
// ---------------------------------------------------------------------------------------------

const RAW_TEXT = ["script", "style", "textarea", "template"];
const FIELD_TAGS = new Set(["input", "select", "textarea", "button"]);

/**
 * The tags whose boundary is **not** a word boundary, and the whole of SH472's rule.
 *
 * <p>A capture has no computed style, so `display: inline` cannot be read; the tag name is the only
 * evidence available, and this is the set a browser's `innerText` behaves as if it held. Everything
 * absent from it — `div`, `p`, `li`, `td`, `br`, `svg`, `img` — separates. Erring towards separation
 * is the safe side of the same argument SH468 made: splitting yields two words the source did
 * contain, fusing invents one it did not, and only a *visually* fused pair is worth fusing.
 */
const INLINE_TAGS = new Set(["span", "em", "strong", "b", "i", "a", "code", "sup", "sub", "small"]);

/**
 * Elements that never have children, so nothing counting depth may wait for their close tag.
 *
 * <p>Exported for the same reason `tokenize` is: `propose.mjs` builds a tree over this module's
 * token stream and needs the identical answer to "does this tag open anything". Two copies of the
 * set are two answers, and the divergence would land as a tree that nests the rest of a page inside
 * an `<img>` on one reader and not the other (SH477 needed the second copy and imported the first
 * instead).</p>
 */
export const VOID_TAGS = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source",
  "track", "wbr",
]);

/**
 * Removes what would make a tag scanner lie: comments, and the bodies of elements whose content is
 * not markup. The **open tags survive** — `<script src>` and a `<textarea name>` are both facts the
 * inventory needs, and only the text between them is noise.
 *
 * <h3>Blanked, not deleted — which is what makes an offset mean something (SH520)</h3>
 *
 * <p>What is removed is replaced by **exactly as many spaces**, so every index into the result is the
 * same index into the bytes that were passed in. That is the whole of it: `buildTree` reads this
 * string, so a node's span is a span in the *source*, and `convert` can cut an accepted block out of
 * the page a curator captured without re-serializing anything. Deleting made the two coordinate
 * systems differ by an unknown amount, which is why `convert.mjs` recorded that per-block bytes were
 * unavailable and why accepting one block had to discard the rest of the page.</p>
 *
 * <p>It concedes one thing, and it is the safe side of SH468's argument: `foo&lt;!--x--&gt;bar` now
 * reads as two words rather than one, because the comment leaves whitespace where it stood. Erring
 * towards separation splits a pair the source did write; erring the other way invents a word it
 * never contained.</p>
 */
export function stripNonMarkup(html) {
  const blanks = (length) => " ".repeat(length);
  let out = String(html ?? "").replace(/<!--[\s\S]*?-->/g, (match) => blanks(match.length));
  for (const tag of RAW_TEXT) {
    out = out.replace(new RegExp(`(<${tag}\\b[^>]*>)([\\s\\S]*?)(</${tag}\\s*>)`, "gi"),
      (_match, open, body, close) => `${open}${blanks(body.length)}${close}`);
  }
  return out;
}

/**
 * The bodies of a page's `<style>` blocks, in source order (SH427).
 *
 * <p>`stripNonMarkup` **removes** these, which is right for building a tree and is why the theme
 * phase could not see them: `openviglet.github.io` declares its whole palette — sixteen custom
 * properties — inside one `<style>` block and links zero external stylesheets, so `shio propose`
 * emitted `theme: null` with no note. Critical CSS in a `<style>` block is not an edge case; it is
 * what a single-page marketing site and every performance-tuned landing page does.
 *
 * <p>The same coarse boundary as `stripNonMarkup`, and for the same reason (see below): this module
 * reads bytes from a stranger's server, so the pattern stays strictly linear. A block declaring a
 * `type` that is not CSS is skipped rather than parsed as CSS.
 */
export function styleBlocks(html) {
  const out = [];
  const pattern = /<style\b([^>]*)>([\s\S]*?)<\/style\s*>/gi;
  for (const [, attrs, body] of String(html ?? "").matchAll(pattern)) {
    const type = /\btype\s*=\s*["']?([^"'\s>]+)/i.exec(attrs ?? "")?.[1];
    if (type && !/^text\/css$/i.test(type)) continue;
    if (body.trim()) out.push(body);
  }
  return out;
}

/**
 * Class-name patterns a framework toggles as a page scrolls — state, not content (SH517).
 *
 * <p>The arguable half of the task, and it is a list because it has to be: there is no structural
 * way to tell `uk-active` from a class an author wrote. What makes a list defensible here is that
 * every entry names a **framework's own** state convention, documented by that framework, and the
 * cost of a wrong entry is one class attribute rather than any content. Measured on the page that
 * produced this: three `uk-scrollspy-inview` and **fifty-nine** `uk-active`, so a replica opened
 * with its animations already finished and its first slide already chosen.</p>
 */
const STATE_CLASSES = [
  /^uk-(?:scrollspy-)?inview$/,   // UIkit scrollspy, both spellings
  /^uk-active$/,                  // UIkit's current slide / current nav item
  /^aos-animate$/,                // AOS, after the element has entered
  /^is-inview$/, /^in-view$/,     // Locomotive and the hand-written convention
  /^wow\b.*animated$/,            // WOW.js
];

/**
 * What a rendered capture has to give back before its markup is content (SH517).
 *
 * <h3>The measurement, and why it is the same mechanism as `--render`'s value</h3>
 *
 * <p>The source has `<div id="particles"></div>` — empty, filled at runtime by a particles library.
 * The replica had `<div id="particles" style="margin-left: -740px; margin-top: -385px;"><canvas
 * width="1480" height="770">`: `--render` baked the canvas the script had just created, sized for
 * the capture's viewport, and nothing paints on it. So baking the DOM a script produced is **right
 * when that DOM is content** — it is what turned a blank SPA capture into six readable pages — and
 * **wrong when it is machinery**. A canvas is meaningless without the loop that draws on it, and
 * baking one also prevents the library from creating its own.</p>
 *
 * <h3>Three rules, and each one is evidence rather than a guess</h3>
 *
 * <p><b>A canvas the source did not have</b> was made by a script, and the response body is the
 * evidence — it costs no request, being the bytes the navigation already transferred. An
 * <em>authored</em> `<canvas>` is left exactly where it is: it is markup somebody wrote, and its
 * children are fallback content. Only the surplus goes.</p>
 *
 * <p><b>An empty `style=""`</b> is residue: no author writes one, and a script that set and cleared
 * an inline property leaves exactly that. Deliberately narrow — a style attribute with a
 * *declaration* in it is kept, because there is no way to tell one a script wrote from one an
 * author did, and SH515 has just finished making the references inside it reachable.</p>
 *
 * <p><b>A framework's state class</b> (`STATE_CLASSES`) is a scroll position frozen mid-flight.</p>
 *
 * @returns `{html, froze}` — the settled markup and what was given back, because a capture that
 *   freezes behaviour has to say what it froze
 */
export function settleRendered(html, source = "") {
  const froze = { canvas: 0, style: 0, classes: 0 };
  let out = String(html ?? "");
  if (!/<canvas\b/i.test(String(source ?? ""))) {
    out = out.replace(/<canvas\b[^>]*>[\s\S]*?<\/canvas\s*>/gi, () => {
      froze.canvas += 1;
      return "";
    });
  }
  out = out.replace(/\s+style\s*=\s*(?:""|''|"\s+"|'\s+')/gi, () => {
    froze.style += 1;
    return "";
  });
  out = out.replace(/\bclass\s*=\s*"([^"]*)"/gi, (whole, names) => {
    const kept = names.split(/\s+/).filter((name) => {
      const state = name && STATE_CLASSES.some((pattern) => pattern.test(name));
      if (state) froze.classes += 1;
      return name && !state;
    });
    if (kept.length === names.trim().split(/\s+/).filter(Boolean).length) return whole;
    return kept.length ? `class="${kept.join(" ")}"` : "";
  });
  return { html: out, froze };
}

/**
 * The bodies of a page's inline `<script>` blocks, in source order (SH515).
 *
 * <p>`styleBlocks`' twin, and it exists for the same reason: `stripNonMarkup` blanks these before
 * the token loop, so a reference inside one is invisible to the scanner that reads the markup. The
 * references that live here are `fetch()` calls — a page that draws a section from a JSON file, and
 * the one shape whose absence is a **crash** rather than a missing image.</p>
 *
 * <p>Inline only. A `<script src>` is a separate file the inventory already records as an asset
 * (SH473), and its own `fetch()` calls are a reference inside somebody else's bundle — reachable
 * only by re-scanning a downloaded script, which is a larger thing than this and a different task.
 * A `type` that is not JavaScript is skipped rather than scanned: `application/ld+json` is data
 * about the page, and a `fetch(` in it would be a string in somebody's structured data.</p>
 */
export function scriptBlocks(html) {
  const out = [];
  const pattern = /<script\b([^>]*)>([\s\S]*?)<\/script\s*>/gi;
  for (const [, attrs, body] of String(html ?? "").matchAll(pattern)) {
    if (/\bsrc\s*=/i.test(attrs ?? "")) continue;
    const type = /\btype\s*=\s*["']?([^"'\s>]+)/i.exec(attrs ?? "")?.[1];
    if (type && !/^(text|application)\/(java|ecma)script$|^module$/i.test(type)) continue;
    if (body.trim()) out.push(body);
  }
  return out;
}

/**
 * The tag boundary, and it is deliberately **coarse**.
 *
 * <p>The precise version — a repeated group of `name(=value)?` with quoted alternatives — has nested
 * quantifiers over overlapping character classes, which is exponential backtracking. That is a
 * curiosity in most code and a defect here: this is the one module in the repository that parses
 * bytes fetched from a **stranger's server**, so a pathological page would hang the crawl rather
 * than fail it, and a hang is the failure mode nobody can diagnose from a report.
 *
 * <p>Two things make this strictly linear rather than merely better. `[^>]*` is greedy over a class
 * that cannot match its own terminator, so there is exactly one way to reach the `>`. And the
 * lookahead after the tag name is what stops the *name* class and the *attribute* class overlapping
 * — without it, `<aaaa…` with no `>` makes the engine try every split of the run between them,
 * which is quadratic. Neither is a hypothetical: `clone.test.mjs` feeds it 20k of exactly that.
 *
 * <p>The price is one real limitation, kept here rather than in a commit message: a `>` inside a
 * **quoted attribute value** ends the tag early, so that tag's later attributes are missed. Real
 * markup writes it `&gt;`, the loss is one attribute on one element, and it is bounded — which is
 * the trade a tool pointed at the open internet should make.
 */
const TAG = /<(\/?)([a-zA-Z][-\w:]*)(?=[\s/>])([^>]*)>/g;
const ATTR = /([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]*)))?/g;

/**
 * One tag's attributes, **entity-decoded** (SH550).
 *
 * <h3>Why the decode is here and not where the URL is parsed</h3>
 *
 * <p>SH476 decoded markup's *text* and left attributes alone, with a note saying that unescaping a
 * query string "belongs where the URL is parsed". That was the right instinct and the wrong address,
 * and one shape proves it: `style="background-image: url(&quot;../img/11.png&quot;)"`. By the time
 * `resolveHref` sees anything, `URL_CALL` has already run — and its unquoted branch excludes literal
 * quotes but not `&quot;`, so it captures `&quot;../img/11.png&quot;` whole and yields the asset path
 * `/br/&quot;../img/introducing-vilt/11.png&quot;`, reported as <i>no safe path for this name</i> and
 * never fetched. A decode after that point cannot recover it: the delimiters the CSS grammar needed
 * are inside the token. The same holds for `&amp;` in a query string, and that form is the dangerous
 * one — a same-origin URL joined with a literal `&amp;` is a <em>different request</em> and nothing
 * about it looks wrong in a listing.</p>
 *
 * <p>So the decode happens where the markup stops being markup, which for an attribute is here. It
 * is also the only place that gives <b>exactly one pass</b> over each value, which SH476 requires:
 * decoding both here and at the URL would turn `&amp;quot;` into `"` and lose the distinction the
 * author was drawing. Raw-text bodies — `<style>`, `<script>` — are deliberately <b>not</b> decoded
 * anywhere, because entities are not recognised inside them; the page that produced this finding
 * carries the unescaped spelling in a `<style>` block and the escaped one in an attribute, and after
 * this the two produce one reference instead of one download and one failure.</p>
 */
export function parseAttrs(raw) {
  const out = {};
  if (!raw) return out;
  ATTR.lastIndex = 0;
  let match;
  while ((match = ATTR.exec(raw)) !== null) {
    const name = match[1]?.toLowerCase();
    if (!name) continue;
    out[name] = decodeEntities(match[2] ?? match[3] ?? match[4] ?? "");
  }
  return out;
}

/** `"a.png 1x, b.png 2x"` → `["a.png", "b.png"]`. */
function srcset(value) {
  return String(value ?? "")
    .split(",")
    .map((candidate) => candidate.trim().split(/\s+/)[0])
    .filter(Boolean);
}

function assetKind(tag, url, attrs) {
  const rel = String(attrs?.rel ?? "").toLowerCase();
  const extension = (/\.([a-z0-9]+)$/i.exec(url.pathname)?.[1] ?? "").toLowerCase();
  if (tag === "script") return "script";
  if (rel.includes("icon")) return "icon";
  if (rel === "manifest") return "manifest";
  if (rel === "stylesheet" || extension === "css") return "stylesheet";
  if (["woff", "woff2", "ttf", "otf", "eot"].includes(extension)) return "font";
  if (tag === "img" || tag === "picture" || ["png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico"].includes(extension)) {
    return "image";
  }
  if (tag === "video" || tag === "audio" || ["mp4", "webm", "mp3", "ogg", "wav", "mov"].includes(extension)) {
    return "media";
  }
  if (["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip", "csv"].includes(extension)) return "document";
  return "other";
}

/**
 * Everything one page declares, read off its markup.
 *
 * <p>Pure: the same string always yields the same record, which is what lets the crawl be tested
 * without a network and the extractor be tested without a crawl. It is a **scanner, not a parser** —
 * it does not build a tree, because nothing here needs one and a tree over hostile markup is a much
 * larger thing. SH379, which has to *map* markup onto sections, is where a real parser earns its
 * keep.
 *
 * <p>Navigation is `<nav>` and `[role=navigation]` only. A footer's links are links; calling them
 * navigation would put a privacy policy in a site's primary menu, and the whole value of this
 * section is that it is the menu **as the source spells it**.
 */
/**
 * What kind of thing an element is, in the vocabulary a **reader** would use (SH867).
 *
 * <h3>The measurement</h3>
 *
 * <p>One viewport of a replica lost an eyebrow badge, a secondary call to action, a four-cell
 * statistics band and half the hero's type scale. `verify` reported **0 errors**; `diff` reported
 * `external links: 15 → 9`; `judge` reported eleven findings, all of one kind. A call to action is an
 * anchor and a stats bar is a list of them, so counted as links both are six of one number — and a
 * reviewer told six links are missing looks for six links.</p>
 *
 * <h3>What markup can and cannot decide, stated rather than blurred</h3>
 *
 * <p>These four are **structural**: an `<h2>` is a heading in any stylesheet, a `<button>` is a
 * control, an `<li>` is one of a set, an `<img>` is a picture. The other two symptoms are not, and
 * this deliberately does not guess at them — an *eyebrow badge* is a `<span>` some CSS makes a pill,
 * and *half the type scale* is a computed font size. Both need the rendered pair, which is SH867's
 * second move and is not this one; a census that guessed at them would report a `<span>` count that
 * moves with every wrapper anybody adds.</p>
 *
 * <p>A `role` wins over the tag, because that is the author saying what the thing is: `<a
 * role="button">` is a control however it is spelled, and `role="presentation"` on a list is the
 * author saying its items are not a set.</p>
 */
export function elementKind(name, attrs = {}) {
  const role = String(attrs.role ?? "").trim().toLowerCase();
  if (role === "presentation" || role === "none") return null;
  if (role === "button" || role === "heading" || role === "img") {
    return role === "img" ? "image" : role;
  }
  if (name === "button") return "button";
  if (name === "input") {
    const type = String(attrs.type ?? "").trim().toLowerCase();
    return type === "submit" || type === "button" || type === "reset" ? "button" : null;
  }
  if (/^h[1-6]$/.test(name)) return "heading";
  if (name === "li") return "listItem";
  if (name === "img") return "image";
  return null;
}

/** The kinds a census may hold, sorted — the facts `diff` scores and the order it prints them in. */
export const ELEMENT_KINDS = Object.freeze(["button", "heading", "image", "listItem"]);

export function scanPage(html, pageUrl) {
  const doc = {
    lang: null,
    title: null,
    description: null,
    canonical: null,
    alternates: [],
    links: [],
    assets: [],
    scripts: [],
    forms: [],
    nav: [],
    textChars: 0,
    // SH477 — how many decorative marks this page's link labels turned out to contain. The count is
    // the deliverable, not a diagnostic: the rule below reads one signal off one site's markup, and
    // a rule that fires on a single fixture is a rule that fires on nobody else's page. Carried up
    // into the inventory so **every** crawl says how often it was right, on the operator's own
    // source rather than on ours.
    selfLabelled: 0,
    // SH867 — what kinds of thing the page's body is made of. See `elementKind`.
    elements: {},
  };
  let navDepth = 0;
  let form = null;
  let anchor = null;
  // SH477 — element depth *inside* the current anchor, which is what tells a labelled element's
  // close tag from any other. Reset by `<a>` and never read outside one, so an unbalanced tag
  // before the anchor cannot move it: the worst a broken page can do here is leave a frame unclosed,
  // and a frame that never closes never drops anything.
  let anchorDepth = 0;
  let inTitle = false;
  // SH581 — which half of the document the loop is in. A replica stores a page's **body** and gets
  // its head from the layout, so a reference in the source's head is not a fact about the page.
  // Closed by `</head>` *or* by `<body>`, because real markup omits the closer constantly, and it
  // starts false: a document with no `<head>` at all is a fragment, and calling all of it body is
  // the strict reading — the one that keeps a reference scored rather than excused.
  let inHead = false;
  const addAsset = (href, tag, attrs) => {
    const url = resolveHref(href, pageUrl);
    if (!url) return;
    // `from` — which of the three passes below found this reference (SH578). See `countAssets`.
    doc.assets.push({
      url: url.href,
      kind: assetKind(tag, url, attrs),
      from: "markup",
      ...(inHead ? { where: "head" } : {}),
    });
  };

  // SH455 — the `url()` calls inside the page's own `<style>` blocks, which `stripNonMarkup` removed
  // before the loop below ever saw them.
  //
  // <p>SH452 made a *downloaded* stylesheet's references discoverable, and that is the case with an
  // external `<link rel="stylesheet">`. A page whose CSS is inline has no such file, so its webfonts
  // reached the theme builder — which reads `styleBlocks` too — and never reached the inventory the
  // downloader reads. Measured on `openviglet.github.io`: `--assets` wrote 1 file, the `<link
  // rel="icon">` logo, while `propose` named two webfonts as 404-on-the-replica and told the reader
  // to re-run the very capture that could not fetch them (P6 — a fix that is not one).
  //
  // <p>`kindForUrl` rather than `assetKind`: a `url()` carries no tag and no `rel`, so the extension
  // is the only evidence, and its unrecognised default is `image` — which downloads — where
  // `assetKind`'s is `other`, which does not.
  for (const body of styleBlocks(html)) {
    for (const match of body.matchAll(URL_CALL)) {
      const raw = (match[1] ?? match[2] ?? match[3] ?? "").trim();
      const url = resolveHref(raw, pageUrl);
      if (url) doc.assets.push({ url: url.href, kind: kindForUrl(url.pathname), from: "style" });
    }
  }

  // SH515 — and the strings a `fetch()` names, which are neither markup nor CSS and which the
  // rewriter rebases. `content/home-insights.json` was rebased onto the serving base, never
  // downloaded, and the page then threw `insightsData.find is not a function` on a 404 body: one
  // pass knew the reference existed and the pass that could fetch it did not. `data` rather than a
  // guess from the extension, because what makes this a reference is the call and not the file
  // name — and because that kind is what puts it behind `--scripts`, where its caller already is.
  for (const body of scriptBlocks(html)) {
    for (const match of body.matchAll(FETCH_CALL)) {
      const raw = (match[1] ?? match[2] ?? match[3] ?? "").trim();
      const url = raw ? resolveHref(raw, pageUrl) : null;
      if (url) doc.assets.push({ url: url.href, kind: "data", from: "script" });
    }
  }

  for (const token of tokenize(stripNonMarkup(html))) {
    if (token.type === "text") {
      // SH565 — decoded here, which is the seam both earlier fixes routed around. SH476 decodes
      // text "when it becomes a field" (`propose.mjs`' `text()` and `alt`) and SH550 decodes
      // attribute VALUES, stating in its own words that raw-text bodies are not decoded. A
      // `<title>` is RCDATA: entity-bearing, and reached by neither. So `Northwind &amp; Co` was
      // stored as the five characters `&amp;` and re-escaped on the way out, on BOTH paths — the
      // title does not travel through `propose`, so fidelity is wrong too, where SH476 was
      // authorable only. It compounds on re-capture: each pass escapes what the last one stored.
      if (inTitle && !doc.title) doc.title = collapse(decodeEntities(token.text)) || null;
      // SH468 — a separator, because an append after the first crosses an element boundary: the
      // tokenizer emits one text token per run, so two of them in one anchor were separated by a
      // tag. Without one a 48×48 brand tile beside its label produced `DuDumont DEP`, `ShViglet Shio`,
      // `TuTuring ES` — a token the source never contained, and `collapse()` then normalised
      // whitespace that was never inserted. A fabricated word is worse than a dropped one: `Dumont
      // DEP` and `DuDumont DEP` both look like content, so nothing downstream — not `verify`, not a
      // digest, not a reviewer skimming a menu — can tell which one is the source's.
      //
      // SH472 — but only at a boundary the *eye* sees, which is what `anchor.gap` records. SH468
      // separated unconditionally and named the trade; the trade turned out to be the bug, because
      // the mark is `<span>D</span><span>u</span>` and two sibling runs inside one inline element
      // are one word. `DuDumont DEP` became `D u Dumont DEP` — no fabricated token, and still not
      // the source's label. `INLINE_TAGS` is the narrower rule, and it costs no determinism: the
      // set is fixed, so the same bytes still yield the same record (P5). What it does concede is
      // `<em>Open</em>Source` → `OpenSource`, and that concession is the point — it is one word to
      // a reader, one word in `innerText`, and one word here.
      // SH670 — decoded here, because a link's label is **content**: it is what `shio report` shows
      // a curator and what SH432 called the whole argument for curating navigation as content. The
      // same document's `<title>` has been decoded since SH565 and this had not, so one capture gave
      // two answers for the same bytes and the menu a human was asked to keep read `Northwind &amp;
      // Co`. SH476's rule decides it: decode where markup becomes content, once.
      //
      // At the accumulation and not in `pushLink`, which is the difference that matters. Attribute
      // values are decoded at the tokenizer (SH550), so SH477's self-label test — does this frame's
      // text equal its own `aria-label` — was comparing a decoded attribute against raw text, and a
      // mark spelled `aria-label="A&amp;B"` over `A&amp;B` could never match. Decoding the runs
      // makes both sides content; every offset in `anchor.drops` is recorded after this point, so
      // the marks move with the text rather than against it.
      if (anchor) {
        const run = decodeEntities(token.text);
        anchor.text += anchor.gap ? ` ${run}` : run;
        anchor.gap = false;
      }
      doc.textChars += collapse(token.text).length;
      continue;
    }
    const { name } = token;
    // SH472 — both directions, and before the `close` branch returns: a run ends at `</div>` as
    // surely as the next one begins at `<p>`, and the anchor's own `<a>` cannot reach here because
    // the switch below is what creates it.
    if (anchor && !INLINE_TAGS.has(name)) anchor.gap = true;
    if (token.type === "close") {
      // SH477 — before the `</a>` branch below hands the anchor off, and before `inTitle` and the
      // rest, because a labelled element's own close tag is the only place its text is known to be
      // complete. `name !== "a"` keeps the anchor's own closer out of the depth count; the `</a>`
      // case is handled where the anchor is pushed.
      if (anchor && name !== "a") {
        anchorDepth = Math.max(anchorDepth - 1, 0);
        closeSelfLabels(anchor, anchorDepth);
      }
      if (name === "title") inTitle = false;
      if (name === "head") inHead = false;
      if (name === "nav") navDepth = Math.max(navDepth - 1, 0);
      if (name === "form" && form) {
        doc.forms.push(form);
        form = null;
      }
      if (name === "a" && anchor) {
        pushLink(doc, anchor);
        anchor = null;
      }
      continue;
    }
    const attrs = parseAttrs(token.attrs);
    // SH867 — the census, before the switch and outside the head. See `ELEMENT_KINDS`.
    if (!inHead) {
      const kind = elementKind(name, attrs);
      if (kind) doc.elements[kind] = (doc.elements[kind] ?? 0) + 1;
    }
    // SH515 — the `url()` calls in a `style` **attribute**, which are markup and are not a
    // stylesheet, so neither pass above saw them. `background-image:url('img/about-us.png')` is how
    // a hero image reaches a page that has no `<img>` for it, and the rewriter has always rebased
    // it; the whole finding is that the pass that could fetch it did not know it was there.
    if (attrs.style?.includes("(")) {
      for (const match of attrs.style.matchAll(URL_CALL)) {
        const raw = (match[1] ?? match[2] ?? match[3] ?? "").trim();
        const url = raw ? resolveHref(raw, pageUrl) : null;
        if (url) doc.assets.push({ url: url.href, kind: kindForUrl(url.pathname) });
      }
    }
    switch (name) {
      case "html":
        if (attrs.lang) doc.lang = attrs.lang.trim();
        break;
      case "head":
        inHead = true;
        break;
      case "body":
        inHead = false;
        break;
      case "title":
        inTitle = true;
        break;
      case "base":
        // Deliberately unhandled beyond a note: a <base href> silently re-roots every relative URL
        // on the page, so guessing wrong here would mis-record the whole inventory.
        if (attrs.href) doc.baseHref = attrs.href;
        break;
      case "meta":
        if (String(attrs.name ?? "").toLowerCase() === "description") {
          doc.description = collapse(attrs.content ?? "") || null;
        }
        break;
      case "link": {
        const rel = String(attrs.rel ?? "").toLowerCase();
        if (rel === "canonical" && attrs.href) doc.canonical = resolveHref(attrs.href, pageUrl)?.href ?? null;
        else if (rel === "alternate" && attrs.hreflang && attrs.href) {
          const url = resolveHref(attrs.href, pageUrl);
          if (url) doc.alternates.push({ hreflang: attrs.hreflang.trim(), url: url.href });
        } else if (["stylesheet", "icon", "shortcut icon", "apple-touch-icon", "manifest", "preload"].includes(rel)) {
          addAsset(attrs.href, name, attrs);
        }
        break;
      }
      case "script":
        if (attrs.src) {
          const url = resolveHref(attrs.src, pageUrl);
          if (url) {
            doc.scripts.push({
              url: url.href,
              async: "async" in attrs,
              defer: "defer" in attrs,
              type: attrs.type || null,
            });
            // SH473 — and an asset, which is the half that was missing. `doc.scripts` is the
            // *third-party* list: `buildInventory` drops every same-origin row from it, because
            // SH389 models the hosts a site borrows behaviour from. So the site's own bundle was
            // recorded nowhere a downloader reads, and `--scripts` — whose help says it takes it —
            // had nothing to take. `BEHAVIOUR`, `wantedKind` and the `withDownloads` note all
            // shipped with SH469 and all of them were unreachable for the same reason: `planAssets`
            // walks `assets`, and no row in it ever had `kind: "script"`.
            //
            // Unconditional here, and the flag stays where it is: the inventory is the record of
            // what the page references, and `planAssets` is the one place that decides what is
            // fetched. An off-origin script is now an off-origin *asset* too, which is the same
            // fact the third-party section reports and the same decision SH378 already made.
            addAsset(attrs.src, name, attrs);
          }
        }
        break;
      case "img":
      case "source":
      case "video":
      case "audio":
      case "embed":
        if (attrs.src) addAsset(attrs.src, name, attrs);
        if (attrs.poster) addAsset(attrs.poster, name, attrs);
        for (const candidate of srcset(attrs.srcset)) addAsset(candidate, name, attrs);
        break;
      case "nav":
        navDepth += 1;
        break;
      case "a":
        if (attrs.href) {
          anchor = {
            href: attrs.href,
            text: "",
            // SH472 — `false`, so the first run never carries a leading separator. `collapse()`
            // would eat it, but only because it eats every leading space; relying on that would
            // make the flag mean two things.
            gap: false,
            nav: navDepth > 0 || String(attrs.role ?? "") === "navigation",
            // SH477 — the open `aria-label` frames, and the spans of `text` they turned out to own.
            labels: [],
            drops: [],
          };
          anchorDepth = 0;
        }
        break;
      case "form":
        form = {
          action: attrs.action ? (resolveHref(attrs.action, pageUrl)?.href ?? attrs.action) : pathOf(pageUrl),
          method: String(attrs.method ?? "get").toLowerCase(),
          fields: [],
        };
        break;
      default:
        break;
    }
    // SH477 — an element that encloses text *inside* an anchor, and whether it names itself. The
    // anchor's own `aria-label` is excluded deliberately: on an `<a>` that attribute is the link's
    // accessible name, which an author is entitled to spell as the visible text, and dropping on it
    // would mean a link that labels itself has no label at all.
    if (anchor && name !== "a" && !token.self && !VOID_TAGS.has(name)) {
      anchorDepth += 1;
      const label = collapse(attrs["aria-label"] ?? "");
      if (label) anchor.labels.push({ label, depth: anchorDepth, mark: anchor.text.length });
    }
    if (String(attrs.role ?? "").toLowerCase() === "navigation" && name !== "a") navDepth += 1;
    if (FIELD_TAGS.has(name) && form) {
      const type = name === "input" ? String(attrs.type ?? "text").toLowerCase() : name;
      // An unnamed control submits nothing, so it is not a field a conversion has to reproduce.
      if (attrs.name || type === "submit") {
        form.fields.push({
          name: attrs.name || null,
          type,
          required: "required" in attrs,
        });
      }
    }
    if (token.self && name === "a" && anchor) {
      pushLink(doc, anchor);
      anchor = null;
    }
  }
  if (anchor) pushLink(doc, anchor);
  if (form) doc.forms.push(form);
  return doc;
}

function pushLink(doc, anchor) {
  const text = collapse(labelOf(anchor, doc));
  doc.links.push({ href: anchor.href, text, nav: anchor.nav });
  if (anchor.nav) doc.nav.push({ label: text, href: anchor.href });
}

/**
 * Closes every `aria-label` frame the tag just left, and records the ones that named themselves
 * (SH477).
 *
 * <p>The signal is in the markup and needs no heuristic. A product tile on `www.viglet.org` is a
 * 48×48 graphic whose two letters are *drawn*, and it carries `aria-label="Du"` over visible text
 * that is also `Du`. An accessible name repeating what is already on screen is the author saying the
 * element is decorative — that is what `aria-label` is for on a graphic — so the run adds nothing to
 * the link's label, and `Du Dumont DEP` becomes a menu entry a curator deletes by hand.</p>
 *
 * <p>The comparison is against the frame's **whole** text and not against one run, because the mark
 * the site actually serves is `<span>D</span><span>u</span>` — two runs, one per glow-animation
 * delay, and neither of them equals `Du` (SH472 measured exactly this). Nesting is why the drop is
 * recorded rather than applied: an outer frame that matches covers the inner ones, which are dropped
 * from the list here so the same characters are never removed twice.</p>
 *
 * <p>It is narrow on purpose. Not "drop short runs" and not "drop uppercase runs" — both eat real
 * content, and `DEP`, sitting in the same string, is the counter-example.</p>
 */
function closeSelfLabels(anchor, depth) {
  while (anchor.labels.length && anchor.labels[anchor.labels.length - 1].depth > depth) {
    const frame = anchor.labels.pop();
    if (collapse(anchor.text.slice(frame.mark)) !== frame.label) continue;
    while (anchor.drops.length && anchor.drops[anchor.drops.length - 1].start >= frame.mark) {
      anchor.drops.pop();
    }
    anchor.drops.push({ start: frame.mark, end: anchor.text.length });
  }
}

/**
 * The anchor's text with its decorative marks removed — unless they were all of it (SH477).
 *
 * <p>The bound is the point. The rule exists to strip a mark sitting *beside* the words, and an
 * anchor whose only text is a self-labelling element is the opposite case: there the visible text
 * **is** the label, the author merely spelled the accessible name the same way, and dropping it
 * would replace a readable menu entry with an empty one. A capture that loses a link's label is a
 * worse record than one that carries a spare word, because nothing downstream can tell an empty
 * label from a link that never had one.</p>
 */
function labelOf(anchor, doc) {
  if (!anchor.drops.length) return anchor.text;
  const drops = [...anchor.drops].sort((a, b) => a.start - b.start);
  let kept = "";
  let at = 0;
  for (const drop of drops) {
    kept += anchor.text.slice(at, drop.start);
    at = Math.max(at, drop.end);
  }
  kept += anchor.text.slice(at);
  if (!collapse(kept)) return anchor.text;
  doc.selfLabelled += drops.length;
  return kept;
}

function collapse(text) {
  return String(text ?? "").replace(/\s+/g, " ").trim();
}

/**
 * The file one page's captured markup goes in (SH379).
 *
 * <p>Derived from the path, never from a counter: the point of the cache is that a second crawl
 * overwrites the same file, so the diff is what the *source* changed. The digest suffix is
 * unconditional rather than added on collision — a name that depends on whether some other page
 * happened to be captured too would make the bytes depend on crawl order, which is the property P5
 * asks for and the reason `--max-pages` cuts a sorted frontier.
 */
export function markupFileFor(path) {
  const raw = String(path ?? "/");
  const slug = raw
    .replace(/[^a-zA-Z0-9._-]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .toLowerCase();
  return `${slug || "index"}.${digest8(raw)}.html`;
}

/**
 * A short, stable hash. Not a cryptographic claim — it exists so two paths that slugify alike get
 * two files, and it is FNV-1a because this module must not grow a dependency and `node:crypto` in a
 * pure function makes the whole file async for one 32-bit number.
 */
function digest8(text) {
  let hash = 0x811c9dc5;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, "0");
}

/**
 * A URL on the capture's <b>own origin</b>, written as the path it already means (SH586).
 *
 * <h3>The defect: a replica whose links go home</h3>
 *
 * <p>`openviglet.github.io/model-catalog/models/azure/` writes three of its own links as absolute
 * URLs on its own origin. A fidelity replica serves the markup verbatim, so clicking *model
 * catalog* on the copy <b>leaves for the original</b>. Measured across the whole replica: twenty-two
 * pages, exactly three each, identical on every one — `internal links: 9 → 6 (-3)` beside
 * `external links: 3 → 6 (+3)`. The total is conserved, which is what made it invisible: nothing is
 * missing, three links per page changed side. `shio verify` exits 0 on that replica, because *no
 * href carries the source's base* is check 2 of a checklist a person runs and not a rule the gate
 * holds — which is how it survived eleven rounds.</p>
 *
 * <h3>Why here and not at render</h3>
 *
 * <p>SH466 rebases a root-relative href at render and deliberately leaves an absolute one alone,
 * which is right for a genuinely off-site link. The one case it is wrong for is the absolute URL
 * that <b>is</b> the source's own origin — and the render cannot tell, because nothing on the
 * instance records what the source was. The capture knows it exactly: it is the crawl's own
 * `origin`. So this is not an edit that points the markup at the replica — the markup still carries
 * the source's own paths, as it must for SH437's asset pass and `snapshot --against` to work. It
 * writes the source's link the short way, which is what SH512 already established for a
 * document-relative reference: record what the source meant, and let SH466 rebase it.</p>
 *
 * <h3>Both spellings, and no heuristic</h3>
 *
 * <p>The scheme-ful `https://host/x` and the protocol-relative `//host/x` are the same claim. A URL
 * on the source's own origin *is* the source's own path by definition, so this needs no judgement
 * about which links are navigation — it is a substitution, applied wherever the origin appears as
 * an attribute value. A bare origin with no path (`href="https://host"`) means the root and becomes
 * `/`, which is the one case a naive strip would turn into an empty href.</p>
 */
export function rebaseOwnOrigin(html, originUrl) {
  if (typeof html !== "string" || !html || !originUrl) return html;
  let origin;
  try {
    origin = new URL(originUrl).origin;
  } catch {
    return html;
  }
  // `https://host` and `//host` — the second is what a source writes to inherit the page's scheme.
  const forms = [origin, origin.slice(origin.indexOf("//"))];
  let out = html;
  for (const form of forms) {
    const pattern = new RegExp(
      `(["'(])${escapeRegExp(form)}(/[^"'()\\s,]*)?(?=["'()\\s,]|$)`, "g");
    out = out.replace(pattern, (_whole, open, path) => `${open}${path || "/"}`);
  }
  return out;
}

/** The metacharacters a URL can carry — a host has dots and a path has `?` and `+`. */
function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/**
 * Which captured pages get a markup file, and which two would land on one (SH379).
 *
 * <p>The collision is *reported*, never resolved by renaming: 32 bits over a bounded crawl makes it
 * astronomically unlikely, and the failure it would cause if it happened silently — a proposal built
 * from a page's markup under another page's path — is exactly the class of defect this block exists
 * to avoid. Sorted by file so the writes happen in the same order every run.
 */
export function planMarkup(visited = [], originUrl = null) {
  const files = [];
  const byFile = new Map();
  for (const entry of visited) {
    if (typeof entry?.html !== "string" || !entry.doc) continue;
    const path = pathOf(entry.url);
    const file = markupFileFor(path);
    const item = { url: entry.url, path, file, html: rebaseOwnOrigin(entry.html, originUrl) };
    const existing = byFile.get(file);
    if (existing) existing.push(item);
    else byFile.set(file, [item]);
  }
  const collisions = [];
  for (const [file, group] of [...byFile.entries()].sort((a, b) => (a[0] < b[0] ? -1 : 1))) {
    if (group.length === 1) files.push(group[0]);
    else collisions.push({ file, paths: group.map((item) => item.path).sort() });
  }
  return { files, collisions };
}

/**
 * The named entities this decoder knows, and the reason it is a list rather than the standard's.
 *
 * <p>HTML5 names about two and a half thousand references, almost all of which no page a capture
 * will meet ever writes. Shipping the table would be forty kilobytes for the ones below; deriving it
 * would need a browser, and the instance does not run one. So: the punctuation, currency and legal
 * marks a site actually spells by name, plus every numeric form — and <b>an unknown name is left
 * verbatim</b>, which is the only safe default. `&thetasym;` surviving as itself is a rendering
 * imperfection a reader can report; `&thetasym;` becoming an empty string is content that vanished.
 */
const ENTITIES = new Map(Object.entries({
  amp: "&", lt: "<", gt: ">", quot: '"', apos: "'",
  nbsp: " ", ensp: " ", emsp: " ", thinsp: " ", shy: "­",
  ndash: "–", mdash: "—", hellip: "…", bull: "•", middot: "·",
  laquo: "«", raquo: "»", lsquo: "‘", rsquo: "’", ldquo: "“", rdquo: "”",
  prime: "′", Prime: "″", dagger: "†", Dagger: "‡", permil: "‰", sect: "§", para: "¶",
  copy: "©", reg: "®", trade: "™", deg: "°", micro: "µ",
  euro: "€", pound: "£", yen: "¥", cent: "¢",
  times: "×", divide: "÷", minus: "−", plusmn: "±", frasl: "⁄",
  frac12: "½", frac14: "¼", frac34: "¾", sup2: "²", sup3: "³",
  iexcl: "¡", iquest: "¿", szlig: "ß",
}));

const ENTITY = /&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});/g;

/**
 * Markup's text, as text (SH476).
 *
 * <h3>The boundary this is, and the double escape that happens without it</h3>
 *
 * <p>A source writes `rerank &amp; media models` and a reader sees `rerank & media models`. The
 * authorable path stored the five characters `&amp;` in a field, the renderer escaped the `&` it
 * found — correctly, it is emitting text into HTML — and the replica showed `rerank &amp; media
 * models`. Neither step is wrong; the missing one is the decode where markup becomes **content**.
 * It compounds: every re-capture escapes what the last one stored.</p>
 *
 * <p>Worse than a display bug, and the reason it is an error rather than a wart: the editor field
 * holds `&amp;` too, so a curator who types the correction stores `&` and the next capture
 * overwrites it. It is content a human cannot repair through the surface built for repairing
 * content. Fidelity mode never had it — verbatim markup keeps the entity and renders it once — which
 * is why it survived until the authorable path was tested.</p>
 *
 * <p><b>One pass, left to right</b>, so `&amp;lt;` decodes to the text `&lt;` and not to `<`: a
 * second pass over the output would decode the source's own escaped entity and lose the distinction
 * the author was drawing. Surrogates and the C0 range are refused rather than emitted, because a
 * lone surrogate is a string later writers cannot serialise.</p>
 */
export function decodeEntities(value) {
  const text = String(value ?? "");
  if (!text.includes("&")) return text;
  return text.replace(ENTITY, (whole, body) => {
    if (body[0] !== "#") return ENTITIES.get(body) ?? whole;
    const code = body[1] === "x" || body[1] === "X"
      ? Number.parseInt(body.slice(2), 16)
      : Number.parseInt(body.slice(1), 10);
    if (!Number.isFinite(code) || code <= 0 || code > 0x10ffff) return whole;
    if (code >= 0xd800 && code <= 0xdfff) return whole;
    if (code < 0x20 && code !== 0x09 && code !== 0x0a) return whole;
    return String.fromCodePoint(code);
  });
}

/**
 * The token stream, shared rather than reimplemented.
 *
 * <p>Exported for SH379, which builds a **tree** over exactly these tokens. Two tokenizers would be
 * two answers to "what is a tag on this page", and the divergence would land as a fidelity gap
 * nobody could locate — the same argument `--render` settles by changing only where the HTML came
 * from. It also means the linear-scan property above is a property of *both* readers, and this is
 * still the only module in the repository reading a stranger's bytes.
 *
 * <p>Every token carries its half-open `[start, end)` span (SH520). Free — the scanner already knows
 * both numbers — and it is what lets a tree built over `stripNonMarkup`'s blanked copy address the
 * original bytes.</p>
 */
export function* tokenize(html) {
  TAG.lastIndex = 0;
  let last = 0;
  let match;
  while ((match = TAG.exec(html)) !== null) {
    if (match.index > last) {
      yield { type: "text", text: html.slice(last, match.index), start: last, end: match.index };
    }
    // The self-closing slash is read off the end of the attribute chunk rather than captured by its
    // own optional group: an optional group beside a lazy class is one more ambiguity for the engine
    // to explore, and this is the module that reads a stranger's bytes.
    const raw = match[3] ?? "";
    const self = raw.endsWith("/");
    yield {
      type: match[1] ? "close" : "open",
      name: match[2].toLowerCase(),
      attrs: self ? raw.slice(0, -1) : raw,
      self,
      start: match.index,
      end: TAG.lastIndex,
    };
    last = TAG.lastIndex;
  }
  if (last < html.length) yield { type: "text", text: html.slice(last), start: last, end: html.length };
}

// ---------------------------------------------------------------------------------------------
// Locales
// ---------------------------------------------------------------------------------------------

/**
 * How a locale shows up in a URL — which is the fact a conversion needs and the one an `hreflang`
 * alone does not give.
 *
 * <p>`prefix` (`/pt-br/sobre`), `subdomain` (`pt.example.com`), `query` (`?lang=pt`), `host` (a
 * different domain entirely), `default` for `x-default`, and `unknown` when the tag appears nowhere
 * in the URL — which is a real answer and not a failure: a site may key the locale off a cookie,
 * and a replica that guessed a prefix would be wrong in a way nothing later could detect.
 */
export function localeShape(hreflang, url, origin) {
  const tag = String(hreflang ?? "").trim().toLowerCase();
  if (!tag) return "unknown";
  if (tag === "x-default") return "default";
  const language = tag.split("-")[0];
  let target;
  let base;
  try {
    target = new URL(url);
    base = new URL(origin);
  } catch {
    return "unknown";
  }
  if (target.hostname.toLowerCase() !== base.hostname.toLowerCase()) {
    const label = target.hostname.toLowerCase().split(".")[0];
    return label === tag || label === language ? "subdomain" : "host";
  }
  const first = target.pathname.split("/").filter(Boolean)[0]?.toLowerCase();
  if (first === tag || first === language) return "prefix";
  for (const value of target.searchParams.values()) {
    const lowered = String(value).toLowerCase();
    if (lowered === tag || lowered === language) return "query";
  }
  return "unknown";
}

// ---------------------------------------------------------------------------------------------
// The inventory
// ---------------------------------------------------------------------------------------------

/**
 * Folds the crawl into the one file everything downstream reads.
 *
 * <p>Sorted everywhere it can be — pages by path, assets by path, locales by tag — because two
 * crawls of an unchanged site must produce the same bytes or the diff that makes this artefact
 * useful is noise (P5). The one exception is `navigation`, which is kept in **source order**: the
 * order *is* the data there, and sorting a menu alphabetically would destroy the only thing the
 * section records.
 *
 * @param visited `[{ url, status, depth, doc, redirectedTo, contentType, error }]`
 */
export function buildInventory({ origin, seed, visited = [], robots, options = {}, notes = [] }) {
  const originUrl = new URL(origin);
  // SH379 — which page rows carry a markup file. Keyed by path, because that is what the row is
  // keyed by and the crawl may have reached one path through a redirect from another.
  const markup = new Map((options.markup?.files ?? []).map((item) => [item.path, item.file]));
  const pages = [];
  const assets = new Map();
  const scripts = new Map();
  const locales = new Map();
  const forms = new Map();
  const navigation = new Map();
  const external = new Map();
  const unfollowed = new Map();
  const problems = [];
  const data = [];

  for (const entry of [...visited].sort((a, b) => (pathOf(a.url) < pathOf(b.url) ? -1 : 1))) {
    const path = pathOf(entry.url);
    // SH431 — a non-HTML 200 before a failure, because the two are different answers and only one of
    // them is a defect. `problems` is what the crawl could not read; this is what was never a page.
    if (entry.notHtml && !entry.error) {
      data.push({ path, status: entry.status ?? 0, type: entry.notHtml });
      continue;
    }
    if (entry.error || !entry.doc) {
      problems.push({ path, status: entry.status ?? 0, reason: entry.error ?? "not html" });
      continue;
    }
    const doc = entry.doc;
    // SH554 — the split is `classifyLinks`, which `shio diff` also calls on the replica's own bytes.
    // It used to be this loop and nowhere else, which is exactly why the two sides of the comparison
    // stopped being the same shape.
    const links = classifyLinks(doc.links, entry.url, originUrl);
    const internal = links.internal.length;
    const outbound = links.external.length;
    const crawled = new Set(visited.map((each) => pathOf(each.url)));
    for (const url of links.internal) {
      // SH387 — an internal target no row in this inventory covers. Computed against the *visited*
      // set rather than against `pages`, because a page that answered 500 is a page the crawl
      // reached: it belongs to `problems`, and calling it uncrawled as well would put one gap in
      // two lists that can disagree.
      const target = pathOf(url);
      if (!crawled.has(target)) {
        unfollowed.set(target, (unfollowed.get(target) ?? 0) + 1);
      }
    }
    for (const url of links.external) {
      const host = url.host.toLowerCase();
      external.set(host, (external.get(host) ?? 0) + 1);
    }
    for (const asset of doc.assets) {
      const url = new URL(asset.url);
      const key = url.href;
      const existing = assets.get(key);
      if (existing) existing.refs += 1;
      else {
        assets.set(key, {
          path: sameOrigin(url, originUrl) ? pathOf(url) : url.href,
          kind: asset.kind,
          external: !sameOrigin(url, originUrl),
          refs: 1,
        });
      }
    }
    for (const script of doc.scripts) {
      const url = new URL(script.url);
      if (sameOrigin(url, originUrl)) continue;
      const host = url.host.toLowerCase();
      const existing = scripts.get(host);
      if (existing) existing.refs += 1;
      else scripts.set(host, { host, refs: 1, sample: pathOf(url), async: script.async, defer: script.defer });
    }
    for (const alternate of doc.alternates) {
      const tag = alternate.hreflang;
      const existing = locales.get(tag);
      if (existing) existing.pages += 1;
      else {
        locales.set(tag, {
          locale: tag,
          shape: localeShape(tag, alternate.url, originUrl),
          sample: sameOrigin(alternate.url, originUrl) ? pathOf(new URL(alternate.url)) : alternate.url,
          pages: 1,
        });
      }
    }
    if (doc.lang) {
      const declared = locales.get(doc.lang);
      // `declared` is weaker than an hreflang shape and must not overwrite one — but it still
      // counts the page, or a site whose only locale signal is <html lang> reports "1 page".
      if (declared) declared.pages += 1;
      else locales.set(doc.lang, { locale: doc.lang, shape: "declared", sample: path, pages: 1 });
    }
    for (const form of doc.forms) {
      // Deduped like navigation, and for the same reason: one newsletter signup in a footer is one
      // form to reproduce, not sixty. `path` is where it was first seen, which is the page a
      // conversion opens. SH432 — the pages are a *set*: the count said pages and added one per
      // sighting, so a form repeated in a header and a footer claimed two pages from one.
      const key = `${form.method} ${form.action} ${form.fields.map((field) => field.name ?? field.type).join(",")}`;
      const existing = forms.get(key);
      if (existing) {
        existing.occurrences += 1;
        existing.seen.add(path);
      } else {
        forms.set(key, {
          path,
          action: form.action,
          method: form.method,
          fields: form.fields,
          occurrences: 1,
          seen: new Set([path]),
        });
      }
    }
    for (const item of doc.nav) {
      const url = resolveHref(item.href, entry.url);
      const target = url ? (sameOrigin(url, originUrl) ? pathOf(url) : url.href) : item.href;
      const key = `${item.label}\u0000${target}`;
      const existing = navigation.get(key);
      // SH432 — the defect this fixes, and the field it matters most for. A capture of ONE page
      // reported `on 2 page(s)` against all three menu items, because the source repeats its nav in
      // a topbar and again in a footer: what was counted was occurrences and what was printed was
      // pages. A navigation item's reach is the whole argument for curating it as content — a link on
      // every page is a menu, a link on one page is a link — so doubling it wherever a site repeats
      // its nav (most sites) makes the number say the opposite of what a reviewer needs. And it
      // cannot be read as a lower bound the way a truncated count can: a count above the pages
      // captured is not conservative, it is wrong.
      if (existing) {
        existing.occurrences += 1;
        existing.seen.add(path);
      } else {
        navigation.set(key, {
          label: item.label,
          href: target,
          external: url ? !sameOrigin(url, originUrl) : false,
          occurrences: 1,
          seen: new Set([path]),
        });
      }
    }
    pages.push({
      path,
      title: doc.title,
      description: doc.description,
      lang: doc.lang,
      status: entry.status,
      depth: entry.depth,
      links: { internal, external: outbound },
      // SH578 — `{own, style}`, the same shape and the same reason as `links`' `{internal, external}`
      // one line up: a count whose two halves answer different questions is a count that scores
      // rather than crashes when the sides disagree.
      assets: countAssets(doc.assets),
      forms: doc.forms.length,
      textChars: doc.textChars,
      // SH867 — always written, including as an empty object, and that is what the version bump is
      // for: absent means *a reader older than 6 wrote this row*, and a reader that filled it in as
      // zero would report every heading on the source as lost. `{}` means the page has none.
      elements: doc.elements,
      ...(doc.baseHref ? { baseHref: doc.baseHref } : {}),
      // SH477 — how many of this page's link labels held a decorative mark that named itself. Only
      // when non-zero and NOT an `INVENTORY_VERSION` bump, on SH592's reading: nothing consumes it,
      // so a capture written without it is readable by every reader and merely reports no marks.
      ...(doc.selfLabelled ? { selfLabelled: doc.selfLabelled } : {}),
      // SH563 — the hop this page was reached through. `visit` follows one redirect by hand and
      // returns `redirectedTo` precisely so the 301 is VISIBLE in the inventory rather than erased
      // by the fetch layer; its own JSDoc listed the field and this row dropped it, so it was
      // documented and discarded in one file. `status` is not the workaround: by the time the row
      // is built the redirect is resolved, so it holds the 200 and a reader cannot tell a
      // redirected path from a served one. Without it `/docs/guia` and `/docs/guia/` are captured
      // as two pages with the same body and no statement that one is the other's canonical, so a
      // conversion writes the body twice and rebuilds neither the choice nor the 301 that every
      // external link to the old form needs.
      ...(entry.redirectedTo ? { redirectedTo: entry.redirectedTo } : {}),
      ...(entry.rendered ? { rendered: true } : {}),
      // SH592 — what this page said about being asked for again, so the next `--refresh` can ask
      // conditionally and be told 304 instead of being handed the page. Optional by design and NOT
      // an inventory version bump: no reader needs them, so a capture written without them is
      // readable by everything and merely re-fetches once.
      ...(entry.etag ? { etag: entry.etag } : {}),
      ...(entry.lastModified ? { lastModified: entry.lastModified } : {}),
      ...(markup.has(path) ? { markup: markup.get(path) } : {}),
    });
  }

  const thin = pages.filter((page) => page.textChars < HYDRATION_TEXT_FLOOR);
  const collected = [...notes];
  if (!robots?.present) {
    collected.push("no robots.txt was served, so nothing restricted this crawl — the site did not permit it, it was never asked");
  }
  if (robots?.sitemaps?.length) {
    collected.push(
      `robots.txt names ${robots.sitemaps.length} sitemap(s), which this crawl did NOT walk: ` +
        `${robots.sitemaps.slice(0, 3).join(" ")} — a page reachable only from a sitemap is missing from this inventory`,
    );
  }
  if (thin.length && !options.rendered) {
    collected.push(
      `${thin.length} page(s) carry almost no text with scripts present, which is what a ` +
        `client-rendered page looks like to a fetch — re-run with --render if the replica has to ` +
        `contain their content (first: ${thin.slice(0, 3).map((page) => page.path).join(" ")})`,
    );
  }
  for (const collision of options.markup?.collisions ?? []) {
    collected.push(
      `${collision.paths.join(" and ")} both name the markup file ${collision.file}, so none of ` +
        `them was written — a proposal built from one page's markup under another page's path is ` +
        `the one defect a cache must not have`,
    );
  }
  if (options.markup && !markup.size && pages.length) {
    collected.push("--markup was passed but no page's markup could be cached, so shio propose has nothing to read");
  }
  // SH557 — the one note that can stop a run rather than qualify it. Ordered before `<base href>`
  // because it is not advice about how to read the inventory: it is how much of this source the
  // target can hold at all, and a reader who stops after the first note should have this one.
  const unaddressable = pages.filter((page) => !addressablePath(page.path));
  if (unaddressable.length) {
    collected.push(
      `${unaddressable.length} of ${pages.length} captured page(s) differ only by a query string, ` +
        `and no friendly URL can hold a "?" — the replica cannot be addressed at those paths and ` +
        `will serve nothing there. Decide now whether this source can be replicated: converting ` +
        `them writes posts no route reaches (first: ` +
        `${unaddressable.slice(0, 3).map((page) => page.path).join(" ")})`,
    );
  }
  // SH477 — the measurement, reported on every crawl rather than argued once from one site. The
  // rule reads a signal off `www.viglet.org`'s markup, and the honest thing to do with a rule that
  // narrow is to make each capture say how often it fired on the source in front of it: a total of
  // zero is as much a finding as a total of forty.
  const marked = pages.filter((page) => page.selfLabelled);
  if (marked.length) {
    const total = marked.reduce((sum, page) => sum + page.selfLabelled, 0);
    collected.push(
      `${total} link label(s) on ${marked.length} of ${pages.length} page(s) enclosed a decorative ` +
        `mark — an element whose aria-label repeats its own visible text, which is how a site spells ` +
        `"this graphic is not content" — and the mark was left out of the label, so the menu a ` +
        `conversion writes carries the source's words and not a logo's letters ` +
        `(first: ${marked.slice(0, 3).map((page) => page.path).join(" ")})`,
    );
  }
  const withBase = pages.filter((page) => page.baseHref);
  if (withBase.length) {
    collected.push(
      `${withBase.length} page(s) declare a <base href>, which re-roots every relative URL on ` +
        `them — the paths recorded for those pages are resolved against the page, not the base`,
    );
  }

  return {
    version: INVENTORY_VERSION,
    source: { origin: originUrl.origin, seed },
    crawl: {
      depth: options.depth ?? DEFAULT_DEPTH,
      maxPages: options.maxPages ?? DEFAULT_MAX_PAGES,
      rendered: Boolean(options.rendered),
      // SH379 — whether the markup a conversion proposal reads is on disk. A reader that had to
      // infer this from "does any page row carry a markup field" would read a crawl that cached
      // nothing and one that was never asked to as the same crawl.
      markup: Boolean(options.markup),
      pages: pages.length,
      truncated: Boolean(options.truncated),
      robots: {
        present: Boolean(robots?.present),
        matched: robots?.matched ?? null,
        crawlDelay: robots?.crawlDelay ?? null,
        sitemaps: robots?.sitemaps ?? [],
      },
    },
    pages,
    // Sorted: an inventory is diffed against the next crawl, and a set that moves on its own makes
    // every diff non-empty.
    assets: [...assets.values()].sort(byPath),
    locales: [...locales.values()].sort((a, b) => (a.locale < b.locale ? -1 : 1)),
    forms: [...forms.values()].map(reach).sort(byPathThenAction),
    // NOT sorted — a menu's order is the fact this section exists to record.
    navigation: [...navigation.values()].map(reach),
    scripts: [...scripts.values()].sort((a, b) => (a.host < b.host ? -1 : 1)),
    outbound: [...external.entries()]
      .map(([host, refs]) => ({ host, refs }))
      .sort((a, b) => (a.host < b.host ? -1 : 1)),
    problems: problems.sort(byPath),
    /**
     * A 200 that was never a page (SH431) — the site's data surface.
     *
     * <p>`catalog-v1.json`, a `.txt`, an `.xml` feed: linked on purpose, served correctly, and not
     * HTML. Kept because a data endpoint may well deserve replicating and a reader should be able to
     * see one, and kept **out of `problems`** because that list means "the crawl could not read
     * this", which is a different claim with a different fix. Filing 24 of them as unreadable pages
     * is what made a complete capture report two dozen phantom gaps.
     */
    data: data.sort(byPath),
    /**
     * Internal links the walk saw and never followed (SH387).
     *
     * <p>The one field SH377 could not have known it needed. Every internal link the crawl *did*
     * follow is a row in `pages`, so a coverage report can already ask about it — but a link cut by
     * `--depth`, by `--max-pages` or by robots.txt is in **neither** list, and the reader of a
     * fidelity report would count it as absent from the source rather than absent from the capture.
     * Those two are opposite claims.
     *
     * <p>Deduplicated with a reference count and sorted, like `assets`: a site's footer links to the
     * privacy page from every page, and one row per occurrence would be the whole crawl again.
     */
    unfollowed: [...unfollowed.entries()]
      .map(([path, refs]) => ({ path, refs }))
      .sort(byPath),
    notes: collected,
  };
}

/**
 * Turns the pages a row was **seen on** into the two counts it reports (SH432).
 *
 * <p>`on` is distinct pages, which is what the label always promised, and `occurrences` is every
 * sighting — kept rather than dropped, because "this link appears twice on every page" is itself a
 * fact about a menu, and a reader who can see both can tell a menu from a repetition. Emitted in a
 * fixed field order and with the working set removed, so the file is still byte-stable (P5).
 *
 * <p>No `INVENTORY_VERSION` bump, and that is a decision rather than an omission: nothing *consumes*
 * `on` — the coverage gate reads `forms` for its fields and never for its counts — so no reader can
 * misread an older file through it. SH431 bumped because a consumer would have; this one is read by a
 * human, in a report generated from the capture it describes.
 */
function reach({ seen, ...row }) {
  return { ...row, on: seen.size, occurrences: row.occurrences };
}

function byPath(a, b) {
  if (a.path === b.path) return 0;
  return a.path < b.path ? -1 : 1;
}

function byPathThenAction(a, b) {
  if (a.path !== b.path) return a.path < b.path ? -1 : 1;
  if (a.action === b.action) return 0;
  return a.action < b.action ? -1 : 1;
}

/** Byte-stable JSON, so the file re-serializes identically and the diff means something (P5). */
export function serializeInventory(inventory) {
  return `${JSON.stringify(inventory, null, 2)}\n`;
}

// ---------------------------------------------------------------------------------------------
// The report
// ---------------------------------------------------------------------------------------------

/**
 * What the caller reads. Every section is bounded by `--limit` and every cut is declared: the
 * inventory holds everything, and a truncated report that did not say so would read as a complete
 * one — which is the failure `shio verify`'s `notes` exist to prevent.
 */
export function describeInventory(inventory, options = {}) {
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 0) : 10;
  const lines = [];
  const { crawl, source } = inventory;
  lines.push(
    `shio clone: ${source.origin} — ${inventory.pages.length} page(s), ` +
      `${inventory.assets.length} asset(s), ${inventory.forms.length} form(s), ` +
      `${inventory.locales.length} locale(s) ` +
      `[depth ${crawl.depth}, max ${crawl.maxPages}, ${crawl.rendered ? "rendered" : "fetched"}]`,
  );
  lines.push(`  inventory: ${options.out ?? INVENTORY_FILE} (no content was written)`);
  const cached = inventory.pages.filter((page) => page.markup).length;
  if (cached) {
    lines.push(`  markup:    ${MARKUP_DIR}/ — ${cached} page(s), which is what shio propose reads`);
  } else if (crawl.markup === false) {
    lines.push(`  markup:    not cached — add --markup if the next phase is shio propose`);
  }

  const section = (title, rows, render) => {
    if (!rows.length) return;
    lines.push(`${title} (${rows.length})`);
    for (const row of rows.slice(0, limit)) lines.push(`  ${render(row)}`);
    if (rows.length > limit) lines.push(`  … ${rows.length - limit} more in the inventory`);
  };

  section("pages", inventory.pages, (page) =>
    `${page.path}${page.title ? `  "${truncate(page.title, 60)}"` : "  (no <title>)"}` +
      `${page.lang ? ` [${page.lang}]` : ""}`);
  section("locales", inventory.locales, (locale) =>
    `${locale.locale}  ${locale.shape}  ${locale.sample}  on ${locale.pages} page(s)`);
  section("navigation", inventory.navigation, (item) =>
    `${item.label || "(no label)"} → ${item.href}${item.external ? " (external)" : ""}`
      + `  on ${item.on} page(s)${repeats(item)}`);
  section("forms", inventory.forms, (form) => {
    const fields = form.fields.slice(0, 8).map((field) => field.name ?? `(${field.type})`).join(", ");
    const on = form.on > 1 ? `  on ${form.on} page(s)${repeats(form)}` : repeats(form);
    return `${form.path} → ${form.method.toUpperCase()} ${form.action}  ${form.fields.length} field(s): ${fields}${on}`;
  });
  section("assets", byKind(inventory.assets), (row) =>
    `${row.kind}: ${row.count}${row.external ? ` (${row.external} off-origin)` : ""}  e.g. ${row.sample}`);
  section("third-party scripts", inventory.scripts, (script) =>
    `${script.host}  ${script.refs} reference(s)  e.g. ${truncate(script.sample, 60)}`);
  section("could not be read", inventory.problems, (problem) =>
    `${problem.path}  ${problem.status || "-"}  ${problem.reason}`);
  // SH431 — its own heading, because the reader's question about these two lists is different: one
  // asks "what is broken on the source", the other "does the replica need a data surface too".
  section("not a page (served, not HTML)", inventory.data, (row) =>
    `${row.path}  ${row.status || "-"}  ${row.type}`);

  if (crawl.truncated) {
    lines.push(
      `  note: the crawl stopped at its ceiling, so this inventory is PARTIAL — raise --depth or --max-pages`,
    );
  }
  for (const note of inventory.notes) lines.push(`  note: ${note}`);
  return `${lines.join("\n")}\n`;
}

/** Assets grouped by kind: 400 image rows are noise, "image: 400" is the fact. */
/**
 * `, twice per page` — printed only when the sightings outnumber the pages (SH432).
 *
 * <p>Silent otherwise, because on most rows the two counts are the same and a second number that
 * always says nothing is a number a reader stops reading. When they differ it is the interesting
 * case: a menu repeated in a footer, which is what made the page count wrong in the first place.
 */
function repeats(row) {
  const times = Number(row.occurrences ?? 0);
  const pages = Number(row.on ?? 0);
  if (!pages || times <= pages) return "";
  return `, ${times} occurrence(s)`;
}

function byKind(assets) {
  const kinds = new Map();
  for (const asset of assets) {
    const existing = kinds.get(asset.kind);
    if (existing) {
      existing.count += 1;
      if (asset.external) existing.external += 1;
    } else {
      kinds.set(asset.kind, { kind: asset.kind, count: 1, external: asset.external ? 1 : 0, sample: asset.path });
    }
  }
  return [...kinds.values()].sort((a, b) => (a.kind < b.kind ? -1 : 1));
}

function truncate(text, length) {
  const raw = String(text ?? "");
  return raw.length <= length ? raw : `${raw.slice(0, length - 1)}…`;
}
