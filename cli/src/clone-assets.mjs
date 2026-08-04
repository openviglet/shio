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

/** `.` and `..` — a segment that navigates instead of naming (SH256). */
const TRAVERSAL = new Set([".", ".."]);

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
 * @param options `site`, `maxAssets`, `maxAssetBytes`
 */
export function planAssets(inventory, { site, maxAssets = DEFAULT_MAX_ASSETS } = {}) {
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
    if (!DOWNLOADABLE.has(asset.kind)) {
      skipped.push({ source: asset.path, kind: asset.kind, reason: "not a renderable asset" });
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
