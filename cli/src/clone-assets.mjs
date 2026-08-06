/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH378 — the bytes a captured page references, which SH377 could only write down.
 *
 * ## Why this is mechanical, and why it is still four decisions
 *
 * SH145 made an asset **a path in the tree**, and `push --content` uploads whatever a `File` field
 * names. So the job is narrow: download every referenced image, font, icon and stylesheet into
 * `shio/assets/<site>/<folder-chain>/<name>` and record the source URL → tree path map, which is
 * the currency every later command already speaks. Four rules make it not quite mechanical, and each
 * one is a consequence of a rule that already exists.
 *
 * **Same-origin only, with a report of what was left behind.** Silently mirroring a CDN's bytes
 * into a customer's media library is a licensing decision, and this tool does not get to make it.
 * The hosts are named so somebody can.
 *
 * **Dedupe by digest.** A source that serves one logo from three URLs must not become three assets:
 * SH210's argument is that a path names one thing, and three paths for one logo means a curator
 * replacing "the logo" replaces a third of it.
 *
 * **The folder chain is the source's own path**, so `/img/hero.png` becomes `<site>/img/hero.png`
 * and the tree reads like the site rather than like a dump.
 *
 * **A name a path cannot hold is refused, never trimmed** — SH256 and SH257 are already the law
 * here, and a crawl is the largest source of hostile names this projection will ever see. It is the
 * only path producer in the tree whose input comes from **a stranger's server**, which is why the
 * decode happens *before* the validation and not after: `%2e%2e` is a traversal wearing a costume,
 * and a validator that runs first sees only an ordinary-looking segment.
 */
import { createHash } from "node:crypto";

/** Ceilings. A crawl that finds a media library must not fill somebody's disk to say so. */
export const DEFAULT_MAX_ASSETS = 500;
export const DEFAULT_MAX_ASSET_BYTES = 25 * 1024 * 1024;

/** The kinds worth downloading: the ones a replica needs to render (§XVII.2's list). */
export const DOWNLOADABLE = new Set(["image", "stylesheet", "font", "icon", "media", "manifest"]);

/**
 * The kind a replica needs to <b>work</b>, which is not the same list (SH469).
 *
 * <h3>Why it was missing, and why it is still not in the set above</h3>
 *
 * <p>Measured on `www.viglet.org`: the replica is 0.33% from the original by pixels and **inert** —
 * the dark/light toggle does nothing and the consent modal will not close, because both are React
 * handlers and `<script type="module" src="/assets/index-LNwgBwpz.js">` is not in the tree. Every
 * instrument agreed it was faithful, which is the worst shape a report can take: a replica that
 * photographs identically and does not work is harder to catch than one that looks wrong.</p>
 *
 * <p>It stays opt-in because of how the faithful photograph is taken. `--render` bakes the
 * post-hydration DOM into the captured markup, so re-adding the bundle asks the same framework to
 * hydrate markup it did not just produce — which may work, may duplicate the page, and may throw.
 * Capturing behaviour is therefore <b>a decision</b> and not a default, exactly as off-origin bytes
 * are. What is not optional is <em>saying</em> that it was discarded: see `withDownloads`' note.</p>
 *
 * <p><b>`data` joined it with SH515</b>, and it belongs on this side of the line rather than in
 * `DOWNLOADABLE` for a reason the two lists already state: a file a `fetch()` names is requested by
 * a script and by nothing else, so on a capture without `--scripts` there is no bundle to ask for
 * it and downloading it would fetch bytes the replica will never serve to anybody. With the flag it
 * is exactly as load-bearing as the bundle — the page that produced this crashed on a 404 body.</p>
 */
export const BEHAVIOUR = new Set(["script", "data"]);

/**
 * Why a behaviour asset is not in the tree when the flag was not passed — the exact string, shared.
 *
 * <p>Exported for `verify-replication.mjs` (SH473), which has to tell this apart from a download
 * that failed: the first is a decision the operator made and the `withDownloads` note already
 * states, the second is a gap. A literal in two modules is two modules that can disagree, and the
 * disagreement would read as a replica whose bundle went missing.</p>
 */
export const BEHAVIOUR_SKIPPED = "behaviour, not captured without --scripts";

/** `.` and `..` — a segment that navigates instead of naming (SH256). */
const TRAVERSAL = new Set([".", ".."]);

/**
 * A `url()` call, as it appears in a stylesheet (SH452).
 *
 * <p>Deliberately the same pattern `theme.mjs` rewrites with, and deliberately a second copy of it:
 * the two modules run at different times — this one before anything is downloaded, that one after —
 * and importing across them would make a capture depend on the theme builder. What would be a real
 * defect is the two *disagreeing*, and `clone-assets.test.mjs` asserts they find the same set.</p>
 *
 * <p>Exported for `clone.mjs`, which scans a page's inline `<style>` blocks with it (SH455). That is
 * an import in the capture direction — the scanner already depends on this module's `kindForUrl` —
 * so it adds a copy of the pattern to neither side.</p>
 */
export const URL_CALL = /url\(\s*(?:"([^"]*)"|'([^']*)'|([^)'"\s]+))\s*\)/gi;

/**
 * A `fetch()` whose argument is a literal path (SH515).
 *
 * <p>The reference shape that is neither markup nor CSS, and the one that produced a **crash**
 * rather than a missing image: `content/home-insights.json` was never fetched, so
 * `insightsData.find is not a function` on a 404 body. A page that draws a section from a data file
 * references that file as surely as an `<img>` references its bytes.</p>
 *
 * <p>Three quotings, and the template literal only when it holds **no interpolation**: `${…}` means
 * the path is computed, and a capture that guessed at one would download a URL the page never asks
 * for. What is left out is every other way a string can reach `fetch` — a variable, a concatenation,
 * an `import()`. Those are not knowable without running the script, and the honest boundary is
 * "a literal, as written".</p>
 *
 * <p>`ShPreviewLinks.FETCH_CALL` is the same grammar in Java, and the two are a deliberate second
 * copy for the reason its own `CSS_URL` is: they are on different sides of the wire, and what would
 * be a real defect is the two disagreeing. `references-page.html` is the fixture both read, and both
 * suites assert the same set out of it.</p>
 */
export const FETCH_CALL =
  /\bfetch\(\s*(?:"([^"]*)"|'([^']*)'|`([^`$]*)`)/g;

/**
 * What kind of asset a URL names, from its extension alone (SH452).
 *
 * <p>The crawl gets a `kind` from the markup that referenced it — a `<link rel="icon">` is an icon
 * whatever it is called — and a `url()` in a stylesheet carries no such evidence, so the extension
 * is all there is. It only has to be good enough for two decisions: whether the reference is worth
 * downloading at all, and what a `soft-404` check should expect. Anything unrecognised is `image`,
 * which is what a `url()` in CSS overwhelmingly is and which downloads; the alternative — refusing
 * what it cannot classify — would drop the sprite sheet nobody gave an extension.</p>
 */
export function kindForUrl(value) {
  const path = String(value ?? "").split("?")[0].split("#")[0].toLowerCase();
  if (/\.(woff2?|ttf|otf|eot)$/.test(path)) return "font";
  if (/\.css$/.test(path)) return "stylesheet";
  if (/\.(mp4|webm|ogv|mp3|ogg|wav)$/.test(path)) return "media";
  if (/\.ico$/.test(path)) return "icon";
  return "image";
}

/**
 * Every same-origin asset a stylesheet references, as inventory rows (SH452).
 *
 * <h3>The defect this closes</h3>
 *
 * <p>`clone` builds its asset list from the **markup**. A stylesheet's `url()` is not markup, so a
 * webfont, a background image and an icon sprite are invisible to it — and for a *fidelity* replica,
 * whose whole claim is that it renders identically, the stylesheet is where most of the remaining
 * bytes live. Measured on a real run: `--assets` wrote exactly one file (a `<link rel="icon">` logo)
 * and the replica rendered in a serif fallback because both of its webfonts 404'd, while `propose`
 * said so in a note nobody had to act on: "2 url() reference(s) are not in the asset map".</p>
 *
 * <h3>Resolved against the stylesheet, never against the page</h3>
 *
 * <p>`url(../fonts/x.woff2)` in `/assets/css/site.css` is `/assets/fonts/x.woff2`. Resolving it
 * against the page that linked the stylesheet names a file that does not exist, and it is the most
 * common single defect in anything that touches CSS urls — which is why `base` is required here and
 * in `theme.mjs`' `rewriteUrls`, the module that has to arrive at the *same* key for the rewrite to
 * find the bytes this pass downloaded.</p>
 *
 * <h3>What it does not take</h3>
 *
 * <p>`data:` and `about:` are already portable. Off-origin is skipped for SH378's reason — mirroring
 * somebody else's bytes is a licensing decision this tool does not make — and lands in the same
 * external report as an off-origin `<img>`, so the hosts are named once. A reference already in the
 * inventory is dropped, since the markup found it first and its `kind` is the better one.</p>
 *
 * @param {string} css   the stylesheet's bytes, as text
 * @param {string} base  the stylesheet's own absolute URL
 * @param {string} origin the source's origin, for the same-origin test
 * @returns {Array<{path: string, kind: string, external: boolean, refs: number}>}
 */
export function assetsReferencedBy(css, base, origin) {
  const found = new Map();
  for (const match of String(css ?? "").matchAll(URL_CALL)) {
    const raw = (match[1] ?? match[2] ?? match[3] ?? "").trim();
    if (!raw || /^(?:data|about):/i.test(raw) || raw.startsWith("#")) continue;
    let url;
    try {
      url = new URL(raw, base);
    } catch {
      continue;
    }
    if (url.protocol !== "http:" && url.protocol !== "https:") continue;
    const external = !sameHost(url, origin);
    // The inventory's own currency: a same-origin asset is a path, an off-origin one is a URL.
    // Matching that here is what lets these rows go through `planAssets` unchanged.
    const path = external ? url.href : `${url.pathname}${url.search}`;
    const existing = found.get(path);
    if (existing) {
      existing.refs += 1;
      continue;
    }
    found.set(path, { path, kind: kindForUrl(url.pathname), external, refs: 1 });
  }
  return [...found.values()].sort((a, b) => (a.path === b.path ? 0 : a.path < b.path ? -1 : 1));
}

function sameHost(url, origin) {
  try {
    return url.host.toLowerCase() === new URL(origin).host.toLowerCase();
  } catch {
    return false;
  }
}

/**
 * Characters no filesystem this tree targets can hold in a name, plus the control range.
 *
 * <p>`:` and `|` are legal on POSIX and not on Windows. Refusing both everywhere is deliberate: a
 * tree that projects on Linux and fails to check out on Windows is worse than one that refuses the
 * asset on both, and the projection's whole promise is that it is movable.
 */
const ILLEGAL = /[<>:"|?*\u0000-\u001f]/;

/** Windows device names, which a file cannot be called even with an extension. */
const RESERVED = /^(con|prn|aux|nul|com[1-9]|lpt[1-9])(\.|$)/i;

/** ext4, APFS and NTFS all stop here, and a longer name is a truncation nobody asked for. */
const MAX_NAME_BYTES = 255;

/**
 * The tree path for one captured asset URL, or **null** when it has no safe path.
 *
 * <h3>Why null rather than a throw</h3>
 *
 * The input is a URL somebody else's server put in somebody else's markup — the most clearly
 * not-ours input any path producer in this tree takes. Aborting a 500-asset download over one
 * hostile filename is SH173's mistake, so the caller skips it, counts it, and the report names it.
 *
 * <h3>What it refuses, and the escape that is not the one you would guess</h3>
 *
 * The obvious hazard is a dot segment, and it is **not** the live one: a URL parser resolves those
 * before anybody sees them, and it resolves the encoded form too. Measured, in Node 26 —
 * `/img/%2e%2e/%2e%2e/etc/passwd` arrives as `pathname === "/etc/passwd"`. The first draft of this
 * comment claimed the opposite and the SH403 lint refused the claim, which is what a probe is for.
 *
 * <p>What survives is **`%2F` (and `%5C`)**, and for a reason that makes it the dangerous one: a
 * parser must not decode an encoded slash, because doing so would change the path's *structure* —
 * so `/img/..%2f..%2fetc/passwd` keeps its escapes verbatim through normalization. A decode for
 * readability (which this does, so the tree holds `héro.png` and not `h%C3%A9ro.png`) then hands
 * itself back the separator the parser was careful to preserve. Decode first, validate after; the
 * other order looks identical and checks nothing.
 *
 * @param {string|URL} sourceUrl the asset's absolute URL
 * @param {string} site the site name the replica will live under
 * @returns {string|null} `<site>/<chain>/<name>`, or null
 */
export function capturedAssetPathFor(sourceUrl, site) {
  let url;
  try {
    url = typeof sourceUrl === "string" ? new URL(sourceUrl) : sourceUrl;
  } catch {
    return null;
  }
  // A URL ending in `/` names a directory, and a directory has no bytes. Naming the last path
  // segment as the file would invent `<site>/img` for `/img/`.
  if (!url?.pathname || url.pathname.endsWith("/")) return null;
  const raw = url.pathname.split("/").filter(Boolean);
  if (!raw.length) return null;

  const names = [];
  for (const segment of raw) {
    let decoded;
    try {
      decoded = decodeURIComponent(segment);
    } catch {
      // A malformed escape (`%zz`, a lone `%`) has no one reading — and guessing between the raw
      // text and a partial decode would put two names on one asset.
      return null;
    }
    if (!safeName(decoded)) return null;
    names.push(decoded);
  }
  if (!safeName(site)) return null;
  return [site, ...names].join("/");
}

/** Every rule a single path segment has to satisfy. Refusal, never repair (SH257). */
function safeName(name) {
  const value = String(name ?? "");
  if (!value) return false;
  // SH257: trimming would write a file whose name is not the name, and every later read disagrees
  // with its own path.
  if (value !== value.trim()) return false;
  if (TRAVERSAL.has(value)) return false;
  // After decoding, a separator means the segment is claiming to be a path.
  if (/[\\/]/.test(value)) return false;
  if (ILLEGAL.test(value)) return false;
  if (RESERVED.test(value)) return false;
  // Windows drops a trailing dot silently, which is the same class of defect as trimming.
  if (value.endsWith(".")) return false;
  return Buffer.byteLength(value, "utf8") <= MAX_NAME_BYTES;
}

/**
 * What the download pass will do, decided before it does any of it.
 *
 * <p>A plan first, for the reason every other write in this CLI has one: the caller can read what
 * 400 requests are about to be, and the counts in the report cover what was *considered* rather than
 * what happened to succeed.
 *
 * @param inventory the SH377 inventory
 * @param options `site`, `maxAssets`, `maxAssetBytes`, `scripts` (SH469's `--scripts`)
 */
export function planAssets(inventory, { site, maxAssets = DEFAULT_MAX_ASSETS, scripts = false } = {}) {
  const plan = [];
  const skipped = [];
  const external = new Map();
  const origin = inventory?.source?.origin ?? "";
  for (const asset of inventory?.assets ?? []) {
    if (asset.external) {
      const host = hostOf(asset.path);
      external.set(host, (external.get(host) ?? 0) + 1);
      // Not a defect and not an oversight: a decision this tool is not entitled to make.
      skipped.push({ source: asset.path, kind: asset.kind, reason: "off-origin" });
      continue;
    }
    if (!wantedKind(asset.kind, scripts)) {
      skipped.push({ source: asset.path, kind: asset.kind, reason: skipReasonFor(asset.kind, scripts) });
      continue;
    }
    const url = absolute(asset.path, origin);
    const file = url ? capturedAssetPathFor(url, site) : null;
    if (!file) {
      skipped.push({ source: asset.path, kind: asset.kind, reason: "no safe path for this name" });
      continue;
    }
    if (plan.length >= maxAssets) {
      skipped.push({ source: asset.path, kind: asset.kind, reason: "past --max-assets" });
      continue;
    }
    plan.push({ source: asset.path, url, file, kind: asset.kind, refs: asset.refs ?? 1 });
  }
  return {
    // Most-referenced first, and this decides something rather than tidying: when two URLs turn out
    // to hold identical bytes, the one downloaded first becomes the file and the other points at it.
    // Sorted by path — the inventory's own order — that made an incidental `/img/hero-again.png` the
    // canonical name for a logo `/img/hero.png` referenced forty times. The name a site *uses* is
    // the one a curator will look for. Ties fall back to the path, so the choice is still stable (P5).
    plan: plan.sort(byRefsThenFile),
    skipped,
    external: [...external.entries()].map(([host, refs]) => ({ host, refs })).sort(byHost),
  };
}

/** Whether this run downloads that kind at all — the render list, plus behaviour on request. */
function wantedKind(kind, scripts) {
  return DOWNLOADABLE.has(kind) || (scripts && BEHAVIOUR.has(kind));
}

/**
 * Why a kind was left, and the two reasons are not one (SH469).
 *
 * <p>"Not a renderable asset" is true of a script and says the wrong thing about it: a `.docx` the
 * crawl saw is not part of the page, and the site's own bundle <em>is</em> the page's behaviour. A
 * reader who cannot tell those apart reads one number and concludes the replica is complete.</p>
 */
function skipReasonFor(kind, scripts) {
  return !scripts && BEHAVIOUR.has(kind) ? BEHAVIOUR_SKIPPED : "not a renderable asset";
}

function byRefsThenFile(a, b) {
  if (a.refs !== b.refs) return b.refs - a.refs;
  if (a.file === b.file) return 0;
  return a.file < b.file ? -1 : 1;
}

function byHost(a, b) {
  if (a.host === b.host) return 0;
  return a.host < b.host ? -1 : 1;
}

function hostOf(value) {
  try {
    return new URL(value).host.toLowerCase();
  } catch {
    return String(value);
  }
}

function absolute(path, origin) {
  try {
    return new URL(path, origin).href;
  } catch {
    return null;
  }
}

/**
 * Where one downloaded asset lands, given everything that has already landed.
 *
 * <p>This is where three of the four rules actually happen, and it is pure so that each is a test
 * rather than a comment.
 *
 * <ul>
 *   <li><b>deduped</b> — these bytes are already in the tree under another path. SH210: a path names
 *       one thing, so the second URL points at the first file and no second copy is written.</li>
 *   <li><b>collision</b> — this path is taken by <em>different</em> bytes. `/logo.png?v=1` and
 *       `?v=2` are one path and two files, and there is no honest way to keep both under a name the
 *       source chose. The first wins, the second is named, and nothing is silently overwritten (P7).</li>
 *   <li><b>soft-404</b> — the response is an HTML page where an image was expected. Writing it would
 *       put a 404 page in the media library under a `.png` name, which every later check reads as a
 *       present asset. The one failure this pass can detect and the server cannot.</li>
 * </ul>
 *
 * @param state `{ byDigest: Map<string,string>, byPath: Map<string,string> }`, mutated
 */
export function placeAsset(state, { source, file, bytes, contentType, kind }) {
  const type = String(contentType ?? "").split(";")[0].trim().toLowerCase();
  if (/^text\/html$/.test(type) && kind !== "manifest") {
    return { action: "soft-404", source, file, detail: `served ${type}, expected a ${kind}` };
  }
  const digest = sha256Of(bytes);
  const already = state.byDigest.get(digest);
  if (already) {
    return { action: "deduped", source, file: already, digest, sameAs: already };
  }
  const taken = state.byPath.get(file);
  if (taken && taken !== digest) {
    return { action: "collision", source, file, digest, detail: `already written from a different source` };
  }
  state.byDigest.set(digest, file);
  state.byPath.set(file, digest);
  return { action: "wrote", source, file, digest, bytes: byteLengthOf(bytes) };
}

/** The SHA-256 an asset is deduped by — the same value `shio push --content` compares (SH210). */
export function sha256Of(value) {
  return createHash("sha256").update(asBuffer(value)).digest("hex");
}

function byteLengthOf(value) {
  return asBuffer(value).length;
}

function asBuffer(value) {
  return value instanceof Uint8Array ? value : Buffer.from(String(value), "utf8");
}

/**
 * Folds the download's outcomes back into the inventory the crawl wrote.
 *
 * <p>Back into the **same file** rather than into a second one, so the source URL → tree path map
 * has one home. Two files would be two things to keep in step, and SH379's markup rewrite reads the
 * map — a stale copy of it would rewrite an `<img src>` to a path holding somebody else's bytes.
 */
export function withDownloads(inventory, { site, results = [], skipped = [], external = [] }) {
  const bySource = new Map();
  for (const result of results) bySource.set(result.source, result);
  for (const entry of skipped) bySource.set(entry.source, { action: "skipped", ...entry });

  const assets = (inventory.assets ?? []).map((asset) => {
    const outcome = bySource.get(asset.path);
    if (!outcome) return asset;
    if (outcome.action === "wrote" || outcome.action === "deduped") {
      return {
        ...asset,
        file: outcome.file,
        sha256: outcome.digest,
        ...(outcome.action === "deduped" ? { dedupedWith: outcome.sameAs } : {}),
      };
    }
    return { ...asset, unavailable: outcome.reason ?? outcome.detail ?? outcome.action };
  });

  const counted = tally(results);
  const notes = [...(inventory.notes ?? [])];
  // SH469 — the phase that discards behaviour names it, and names it as behaviour. The counting was
  // already here: the crawler saw the `<script>` tags in order to skip them, and the only thing
  // missing was a sentence a reader could act on. Every other instrument agrees the replica is
  // faithful — the clone reports `rendered`, the proposal is silent, `verify` is silent, the
  // snapshot says 0.33% — and all of them are measuring the photograph.
  const dropped = skipped.filter((entry) => BEHAVIOUR.has(entry.kind));
  if (dropped.length) {
    const own = dropped.filter((entry) => entry.reason !== "off-origin");
    notes.push(
      `${dropped.length} script(s) were NOT captured, so the replica will render and not respond — `
        + `a handler, a toggle, a modal that will not close. `
        + (own.length
          ? `${own.length} of them belong to this site: re-run with --scripts to take them, and read `
            + `the note on --render first, because a baked DOM plus its own bundle is a hydration `
            + `nobody asked for. `
          : `All of them are off-origin, which is SH378's decision and not this one. `)
        + dropped.slice(0, 5).map((entry) => entry.source).join(" "),
    );
  }
  if (external.length) {
    notes.push(
      `${external.reduce((sum, each) => sum + each.refs, 0)} asset reference(s) point off-origin and ` +
        `were NOT downloaded — mirroring another host's bytes into a media library is a licensing ` +
        `decision, not a fetch: ${external.slice(0, 5).map((each) => each.host).join(" ")}`,
    );
  }
  if (counted.collision) {
    notes.push(
      `${counted.collision} asset(s) wanted a path another asset's bytes already hold (a source ` +
        `serving different files from one name) — the first is kept and both are named, because a ` +
        `path has to name one thing`,
    );
  }
  if (counted["soft-404"]) {
    notes.push(
      `${counted["soft-404"]} reference(s) answered with an HTML page where bytes were expected — a ` +
        `soft 404, which would otherwise land in the tree as a present asset`,
    );
  }
  return {
    ...inventory,
    assets,
    download: {
      site,
      wrote: counted.wrote ?? 0,
      deduped: counted.deduped ?? 0,
      skipped: skipped.length,
      failed: (counted.failed ?? 0) + (counted.collision ?? 0) + (counted["soft-404"] ?? 0),
      bytes: results.reduce((sum, each) => sum + (each.bytes ?? 0), 0),
    },
    notes,
  };
}

function tally(results) {
  const counts = {};
  for (const result of results) counts[result.action] = (counts[result.action] ?? 0) + 1;
  return counts;
}

/** The download half of the report, appended to the crawl's. */
export function describeDownloads(inventory, options = {}) {
  const download = inventory?.download;
  if (!download) return "";
  const limit = Number.isFinite(Number(options.limit)) ? Math.max(Number(options.limit), 0) : 10;
  const lines = [
    `assets: wrote ${download.wrote}, deduped ${download.deduped}, skipped ${download.skipped}, ` +
      `unavailable ${download.failed} (${kb(download.bytes)} into shio/assets/${download.site}/)`,
  ];
  const written = (inventory.assets ?? []).filter((asset) => asset.file && !asset.dedupedWith);
  for (const asset of written.slice(0, limit)) {
    lines.push(`  ${asset.path} → ${asset.file}`);
  }
  if (written.length > limit) lines.push(`  … ${written.length - limit} more in the inventory`);
  const unavailable = (inventory.assets ?? []).filter((asset) => asset.unavailable);
  for (const asset of unavailable.slice(0, limit)) {
    lines.push(`  unavailable ${asset.path} — ${asset.unavailable}`);
  }
  if (unavailable.length > limit) {
    lines.push(`  … ${unavailable.length - limit} more unavailable, all in the inventory`);
  }
  return `${lines.join("\n")}\n`;
}

function kb(bytes) {
  const value = Number(bytes) || 0;
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}
