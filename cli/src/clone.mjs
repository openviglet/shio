/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

/** The inventory's shape version. Bumped when a consumer would read an old file wrongly. */
export const INVENTORY_VERSION = 1;

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
 * Removes what would make a tag scanner lie: comments, and the bodies of elements whose content is
 * not markup. The **open tags survive** — `<script src>` and a `<textarea name>` are both facts the
 * inventory needs, and only the text between them is noise.
 */
export function stripNonMarkup(html) {
  let out = String(html ?? "").replace(/<!--[\s\S]*?-->/g, "");
  for (const tag of RAW_TEXT) {
    out = out.replace(new RegExp(`(<${tag}\\b[^>]*>)[\\s\\S]*?(</${tag}\\s*>)`, "gi"), "$1$2");
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

export function parseAttrs(raw) {
  const out = {};
  if (!raw) return out;
  ATTR.lastIndex = 0;
  let match;
  while ((match = ATTR.exec(raw)) !== null) {
    const name = match[1]?.toLowerCase();
    if (!name) continue;
    out[name] = match[2] ?? match[3] ?? match[4] ?? "";
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
  };
  let navDepth = 0;
  let form = null;
  let anchor = null;
  let inTitle = false;
  const addAsset = (href, tag, attrs) => {
    const url = resolveHref(href, pageUrl);
    if (!url) return;
    doc.assets.push({ url: url.href, kind: assetKind(tag, url, attrs) });
  };

  for (const token of tokenize(stripNonMarkup(html))) {
    if (token.type === "text") {
      if (inTitle && !doc.title) doc.title = collapse(token.text) || null;
      if (anchor) anchor.text += token.text;
      doc.textChars += collapse(token.text).length;
      continue;
    }
    const { name } = token;
    if (token.type === "close") {
      if (name === "title") inTitle = false;
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
    switch (name) {
      case "html":
        if (attrs.lang) doc.lang = attrs.lang.trim();
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
          anchor = { href: attrs.href, text: "", nav: navDepth > 0 || String(attrs.role ?? "") === "navigation" };
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
  doc.links.push({ href: anchor.href, text: collapse(anchor.text), nav: anchor.nav });
  if (anchor.nav) doc.nav.push({ label: collapse(anchor.text), href: anchor.href });
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
 * Which captured pages get a markup file, and which two would land on one (SH379).
 *
 * <p>The collision is *reported*, never resolved by renaming: 32 bits over a bounded crawl makes it
 * astronomically unlikely, and the failure it would cause if it happened silently — a proposal built
 * from a page's markup under another page's path — is exactly the class of defect this block exists
 * to avoid. Sorted by file so the writes happen in the same order every run.
 */
export function planMarkup(visited = []) {
  const files = [];
  const byFile = new Map();
  for (const entry of visited) {
    if (typeof entry?.html !== "string" || !entry.doc) continue;
    const path = pathOf(entry.url);
    const file = markupFileFor(path);
    const item = { url: entry.url, path, file, html: entry.html };
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
 * The token stream, shared rather than reimplemented.
 *
 * <p>Exported for SH379, which builds a **tree** over exactly these tokens. Two tokenizers would be
 * two answers to "what is a tag on this page", and the divergence would land as a fidelity gap
 * nobody could locate — the same argument `--render` settles by changing only where the HTML came
 * from. It also means the linear-scan property above is a property of *both* readers, and this is
 * still the only module in the repository reading a stranger's bytes.
 */
export function* tokenize(html) {
  TAG.lastIndex = 0;
  let last = 0;
  let match;
  while ((match = TAG.exec(html)) !== null) {
    if (match.index > last) yield { type: "text", text: html.slice(last, match.index) };
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
    };
    last = TAG.lastIndex;
  }
  if (last < html.length) yield { type: "text", text: html.slice(last) };
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
  const problems = [];

  for (const entry of [...visited].sort((a, b) => (pathOf(a.url) < pathOf(b.url) ? -1 : 1))) {
    const path = pathOf(entry.url);
    if (entry.error || !entry.doc) {
      problems.push({ path, status: entry.status ?? 0, reason: entry.error ?? "not html" });
      continue;
    }
    const doc = entry.doc;
    let internal = 0;
    let outbound = 0;
    for (const link of doc.links) {
      const url = resolveHref(link.href, entry.url);
      if (!url) continue;
      if (sameOrigin(url, originUrl)) internal += 1;
      else {
        outbound += 1;
        const host = url.host.toLowerCase();
        external.set(host, (external.get(host) ?? 0) + 1);
      }
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
      // form to reproduce, not sixty. `on` is how many pages carry it and `path` is where it was
      // first seen, which is the page a conversion opens.
      const key = `${form.method} ${form.action} ${form.fields.map((field) => field.name ?? field.type).join(",")}`;
      const existing = forms.get(key);
      if (existing) existing.on += 1;
      else forms.set(key, { path, action: form.action, method: form.method, fields: form.fields, on: 1 });
    }
    for (const item of doc.nav) {
      const url = resolveHref(item.href, entry.url);
      const target = url ? (sameOrigin(url, originUrl) ? pathOf(url) : url.href) : item.href;
      const key = `${item.label}\u0000${target}`;
      const existing = navigation.get(key);
      if (existing) existing.on += 1;
      else navigation.set(key, { label: item.label, href: target, external: url ? !sameOrigin(url, originUrl) : false, on: 1 });
    }
    pages.push({
      path,
      title: doc.title,
      description: doc.description,
      lang: doc.lang,
      status: entry.status,
      depth: entry.depth,
      links: { internal, external: outbound },
      assets: doc.assets.length,
      forms: doc.forms.length,
      textChars: doc.textChars,
      ...(doc.baseHref ? { baseHref: doc.baseHref } : {}),
      ...(entry.rendered ? { rendered: true } : {}),
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
    forms: [...forms.values()].sort(byPathThenAction),
    // NOT sorted — a menu's order is the fact this section exists to record.
    navigation: [...navigation.values()],
    scripts: [...scripts.values()].sort((a, b) => (a.host < b.host ? -1 : 1)),
    outbound: [...external.entries()]
      .map(([host, refs]) => ({ host, refs }))
      .sort((a, b) => (a.host < b.host ? -1 : 1)),
    problems: problems.sort(byPath),
    notes: collected,
  };
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
    `${item.label || "(no label)"} → ${item.href}${item.external ? " (external)" : ""}  on ${item.on} page(s)`);
  section("forms", inventory.forms, (form) => {
    const fields = form.fields.slice(0, 8).map((field) => field.name ?? `(${field.type})`).join(", ");
    const on = form.on > 1 ? `  on ${form.on} page(s)` : "";
    return `${form.path} → ${form.method.toUpperCase()} ${form.action}  ${form.fields.length} field(s): ${fields}${on}`;
  });
  section("assets", byKind(inventory.assets), (row) =>
    `${row.kind}: ${row.count}${row.external ? ` (${row.external} off-origin)` : ""}  e.g. ${row.sample}`);
  section("third-party scripts", inventory.scripts, (script) =>
    `${script.host}  ${script.refs} reference(s)  e.g. ${truncate(script.sample, 60)}`);
  section("could not be read", inventory.problems, (problem) =>
    `${problem.path}  ${problem.status || "-"}  ${problem.reason}`);

  if (crawl.truncated) {
    lines.push(
      `  note: the crawl stopped at its ceiling, so this inventory is PARTIAL — raise --depth or --max-pages`,
    );
  }
  for (const note of inventory.notes) lines.push(`  note: ${note}`);
  return `${lines.join("\n")}\n`;
}

/** Assets grouped by kind: 400 image rows are noise, "image: 400" is the fact. */
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
