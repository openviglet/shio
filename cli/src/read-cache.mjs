/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1117 — conditional reads from the CLI, where they are not free.
 *
 * ## The half of SH1048 that was not true
 *
 * SH1048's design said MCP and the CLI benefit for free, since they call the same REST. A browser
 * does: `no-cache` plus an `ETag` means it keeps the body and revalidates, and axios goes through
 * that cache. **Node has none.** `cli/src` sent `If-None-Match` in exactly one place — `clone`
 * probing *external* assets it is copying — and never to Shio's own API, so every `shio pull`,
 * `verify`, `digest` and `context` paid the full body against an unchanged instance, which is the
 * case a CLI is in most of the time.
 *
 * ## Why this keeps the body and `clone` keeps only the validator
 *
 * `clone.mjs` records `etag`/`lastModified` per row and a 304 means "the file on disk is still
 * right" — the body is already somewhere. An API read has nowhere: a 304 carries no payload, so a
 * store that held validators alone would answer a cheap request with nothing to return and have to
 * ask again unconditionally, which is one extra round trip for no saving at all. So this is a
 * response cache **validated by an ETag** rather than a validator store, and the distinction is the
 * whole reason the file has a `body` field somebody will want to delete.
 *
 * ## Invalidated by nothing, because the validator *is* the invalidation
 *
 * There is no TTL and no version to bump. Every hit is confirmed by the server before it is used: a
 * 304 is the instance saying these bytes are current, and a 200 replaces them. That is what makes
 * it safe to keep across invocations, and it is also why a **corrupt** store must fail loudly rather
 * than degrade — an entry whose body no longer matches its digest is bytes of unknown provenance
 * that a 304 would bless. `digest` is the guard, and {@link ShReadCacheCorrupt} is what a caller
 * gets instead of a plausible answer.
 *
 * ## Counted, not claimed
 *
 * Without a count, a store that stopped matching reads exactly like a server that never answers 304
 * — everything works, nothing is saved, and no one can tell. {@link ShReadCache#ledger} is the
 * instrument: conditional requests sent, 304s received, 200s paid for, and the bytes each side of
 * that. `SHIO_DEBUG` prints it; the test asserts it.
 *
 * ## The format half is pure
 *
 * No fs and no network here, like `content-files.mjs`, `gates-store.mjs` and `patterns-store.mjs`:
 * what the store looks like on disk, and what a hit means, are testable without an instance. The one
 * function that touches a disk is {@link fileReadCache}, and it is a wiring of the same class.
 */
import { createHash } from "node:crypto";

/** Beside the other derived snapshots (`clone-progress`, `source-inventory`), never inside content. */
export const READ_CACHE_FILE = "shio/.shio/read-cache.json";

/**
 * The format this module writes, so a reader can refuse one it does not understand.
 *
 * <p>A refusal rather than a migration, and cheaply so: every entry is re-fetchable by definition,
 * which is the one kind of state where throwing the file away costs nothing but a round trip.</p>
 */
export const READ_CACHE_FORMAT = 1;

/**
 * How many bytes of bodies one instance may keep.
 *
 * <p>A cache with no bound is a leak with a nice name: `find` and `read` answer per address, so a
 * long-lived project would accumulate an entry per post ever asked for. Evicted oldest-first by the
 * `at` an entry was last confirmed, which is the LRU that matters here — a read nobody repeats is
 * exactly the one worth dropping.</p>
 */
export const READ_CACHE_BYTES = 4 * 1024 * 1024;

/** A store whose bytes are not the bytes its digest names — a hand-edit, a truncation, a bad write. */
export class ShReadCacheCorrupt extends Error {
  constructor(message) {
    super(message);
    this.name = "ShReadCacheCorrupt";
  }
}

/** `sha256:<hex>` over a body, which is what makes a stored entry checkable rather than trusted. */
export function digestOf(body) {
  return `sha256:${createHash("sha256").update(String(body), "utf8").digest("hex")}`;
}

/** One instance, spelled the one way — a trailing slash is not a second server. */
export function instanceOf(baseUrl) {
  return String(baseUrl ?? "").replace(/\/+$/, "");
}

/**
 * The address half of a key: the path and its query, with the query sorted and the origin dropped.
 *
 * <p>The origin is the <em>other</em> half of the key, so carrying it here as well would file every
 * entry under an instance twice and make the two spellings able to disagree. What is left is the
 * address the way the rest of this project means one.</p>
 *
 * <p>Sorted because `?site=a&depth=2` and `?depth=2&site=a` are one request, and two callers spelling
 * it in two orders would otherwise keep two entries and revalidate neither. The ETag validates the
 * answer either way, so this only decides how often a hit is possible.</p>
 */
export function addressOf(url) {
  const raw = String(url ?? "");
  let path = raw;
  let query = "";
  try {
    const parsed = new URL(raw);
    path = parsed.pathname;
    query = parsed.search.replace(/^\?/, "");
  } catch {
    const cut = raw.indexOf("?");
    path = cut < 0 ? raw : raw.slice(0, cut);
    query = cut < 0 ? "" : raw.slice(cut + 1);
  }
  if (!query) return path;
  const params = [...new URLSearchParams(query).entries()]
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
  const search = params.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join("&");
  return search ? `${path}?${search}` : path;
}

/** An empty store, which is also what an unreadable or out-of-format one becomes. */
export function emptyCache() {
  return { format: READ_CACHE_FORMAT, instances: {} };
}

/**
 * A store off its text, or an empty one.
 *
 * <p>Empty rather than thrown for a file that is absent, unparseable or of another format: none of
 * those is a claim about content, so the honest answer is "nothing is known yet" and the next read is
 * unconditional. A **digest mismatch** is the opposite — the file claims to hold a body it does not
 * — and that is refused at {@link ShReadCache#hit}, where the bad entry is about to be used.</p>
 */
export function parseCache(text) {
  if (typeof text !== "string" || !text.trim()) return emptyCache();
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    return emptyCache();
  }
  if (!parsed || typeof parsed !== "object") return emptyCache();
  if (parsed.format !== READ_CACHE_FORMAT) return emptyCache();
  const instances = parsed.instances && typeof parsed.instances === "object" ? parsed.instances : {};
  return { format: READ_CACHE_FORMAT, instances };
}

/** Byte-stable: sorted keys, two-space JSON, one trailing newline — so an unchanged run is no diff. */
export function serializeCache(cache) {
  const instances = {};
  for (const instance of Object.keys(cache.instances ?? {}).sort()) {
    const entries = cache.instances[instance] ?? {};
    const sorted = {};
    for (const address of Object.keys(entries).sort()) {
      const entry = entries[address];
      sorted[address] = { at: entry.at, digest: entry.digest, etag: entry.etag, body: entry.body };
    }
    instances[instance] = sorted;
  }
  return `${JSON.stringify({ format: READ_CACHE_FORMAT, instances }, null, 2)}\n`;
}

/**
 * Drop the oldest entries until one instance's bodies fit {@link READ_CACHE_BYTES}.
 *
 * <p>Pure and exported because the bound is a decision rather than a detail: a reader who wants to
 * know what this file can cost should be able to read the rule and drive it.</p>
 */
export function evict(entries, limit = READ_CACHE_BYTES) {
  const rows = Object.entries(entries)
    .sort(([, a], [, b]) => (b.at ?? 0) - (a.at ?? 0));
  const kept = {};
  let bytes = 0;
  for (const [address, entry] of rows) {
    const size = String(entry.body ?? "").length;
    if (bytes + size > limit) continue;
    bytes += size;
    kept[address] = entry;
  }
  return kept;
}

/**
 * The cache the agent client reads through.
 *
 * <p>IO is injected rather than imported so the whole of this is drivable under plain Node with no
 * disk and no instance — `load()` hands over the text or `null`, `save(text)` takes it back.
 * {@link fileReadCache} is the one wiring that uses `node:fs`.</p>
 */
export class ShReadCache {
  /**
   * @param {object} [io]
   * @param {() => (string|null)} [io.load] the stored text, or null when there is none
   * @param {(text: string) => void} [io.save] persist the text
   * @param {() => number} [io.now] the clock, for the `at` an entry is confirmed at
   */
  constructor({ load, save, now } = {}) {
    this.loadText = load ?? (() => null);
    this.saveText = save ?? (() => {});
    this.now = now ?? (() => Date.now());
    this.cache = parseCache(this.loadText());
    this.dirty = false;
    /** Counted, not claimed — see the class comment for why this exists at all. */
    this.ledger = { conditional: 0, revalidated: 0, full: 0, receivedBytes: 0, savedBytes: 0 };
  }

  #entries(instance) {
    this.cache.instances[instance] ??= {};
    return this.cache.instances[instance];
  }

  /**
   * What is known about this read, checked before it is offered.
   *
   * @throws {ShReadCacheCorrupt} when the stored body is not the body its digest names
   */
  hit(baseUrl, url) {
    const entry = this.#entries(instanceOf(baseUrl))[addressOf(url)];
    if (!entry || typeof entry.etag !== "string" || typeof entry.body !== "string") return null;
    if (entry.digest !== digestOf(entry.body)) {
      throw new ShReadCacheCorrupt(
        `${READ_CACHE_FILE} holds a body for ${addressOf(url)} that is not the body its digest names.`
        + " A 304 would bless bytes of unknown provenance, so this is refused rather than used."
        + ` fix: delete ${READ_CACHE_FILE}, or pass --no-read-cache for this run.`);
    }
    return entry;
  }

  /** The headers that let the instance answer 304, and the count of having asked. */
  conditionalHeaders(hit) {
    if (!hit?.etag) return {};
    this.ledger.conditional += 1;
    return { "If-None-Match": hit.etag };
  }

  /** A 304: the stored bytes are current, so they are served and their cost is what was saved. */
  revalidated(baseUrl, url, hit) {
    this.ledger.revalidated += 1;
    this.ledger.savedBytes += hit.body.length;
    const entries = this.#entries(instanceOf(baseUrl));
    entries[addressOf(url)] = { ...hit, at: this.now() };
    this.dirty = true;
    return hit.body;
  }

  /** A 200: these bytes replace whatever was held, under the tag the instance gave them. */
  received(baseUrl, url, { etag, body }) {
    this.ledger.full += 1;
    this.ledger.receivedBytes += String(body ?? "").length;
    if (!etag || typeof body !== "string") return body;
    const instance = instanceOf(baseUrl);
    const entries = this.#entries(instance);
    entries[addressOf(url)] = { at: this.now(), digest: digestOf(body), etag, body };
    this.cache.instances[instance] = evict(entries);
    this.dirty = true;
    return body;
  }

  /** Write the store back, and only when something changed — an unchanged run touches no file. */
  flush() {
    if (!this.dirty) return false;
    this.saveText(serializeCache(this.cache));
    this.dirty = false;
    return true;
  }

  /** One line for `SHIO_DEBUG`, which is the only place this is printed rather than asserted. */
  report() {
    const { conditional, revalidated, full, receivedBytes, savedBytes } = this.ledger;
    return `shio read-cache: ${conditional} conditional, ${revalidated} revalidated `
      + `(${savedBytes} bytes not sent), ${full} full (${receivedBytes} bytes)`;
  }
}

/**
 * The same cache, over a real directory.
 *
 * <p>Failures are swallowed on purpose and only here: a cache that cannot be read or written must
 * never be the reason a command fails, because every entry in it is re-fetchable. A **corrupt**
 * entry is not this case and is not swallowed — see {@link ShReadCache#hit}.</p>
 *
 * @param {object} fs a `node:fs` shape — `readFileSync`, `writeFileSync`, `mkdirSync`
 * @param {string} [file] where the store lives, relative to the working directory
 */
export function fileReadCache(fs, file = READ_CACHE_FILE) {
  return new ShReadCache({
    load: () => {
      try {
        return fs.readFileSync(file, "utf8");
      } catch {
        return null;
      }
    },
    save: (text) => {
      try {
        fs.mkdirSync(file.slice(0, file.lastIndexOf("/")), { recursive: true });
        fs.writeFileSync(file, text, "utf8");
      } catch {
        /* a cache that cannot be written is a cache, not a failure */
      }
    },
  });
}
