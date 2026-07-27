/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH87 — the disk and wire ends of the content projection: walk `shio/content/**`, read
 * the posts a site holds over the agent surface, and keep the base snapshot that makes a
 * three-way merge possible.
 *
 * The pure parts live in `content-files.mjs` (the format) and `content-sync.mjs` (the
 * reconciliation). This module is only IO, and every call takes the injected `ctx.fs` /
 * `ctx.fetch`, so a test can run a whole sync with neither a disk nor a server.
 */
import { join } from "node:path";

import {
  FOLDER_MARKER,
  bodyFieldFor,
  filePathFor,
  folderMarkerPathFor,
  normalizeUrl,
  parseFolderMarker,
  parsePost,
  serializeFolder,
  serializePost,
} from "./content-files.mjs";
import { addressOf } from "./content-sync.mjs";

/** Where the projection lives, and where the snapshot that tracks it lives. */
export const CONTENT_DIR = "shio/content";
/** Folder markers (SH149) — a root of their own; see `FOLDERS_SUBDIR` for why. */
export const FOLDERS_DIR = "shio/folders";
export const BASE_FILE = "shio/.shio/content-base.json";
export const BASE_VERSION = 1;

/**
 * The base snapshot: what the file tree and the server last agreed on, plus the change
 * cursor (SH88) the last pull reached.
 *
 * <p>It is a sync artifact, not authored content — commit it if you want a shared merge
 * base across a team, or ignore it and accept that a first pull on a fresh clone has no
 * base to merge against (which it reports rather than silently taking one side).</p>
 */
export async function readBase(ctx, path = BASE_FILE) {
  try {
    const raw = await ctx.fs.readFile(join(ctx.cwd, path), "utf8");
    const parsed = JSON.parse(raw);
    return {
      version: parsed.version ?? BASE_VERSION,
      cursor: parsed.cursor ?? null,
      partial: Boolean(parsed.partial),
      posts: parsed.posts ?? {},
      // SH149. Absent in a snapshot written before folders projected, which reads as "we
      // have never agreed about any folder" — so every server folder looks newly added and
      // every local marker looks locally added. Both are the safe reading: one writes a
      // marker, the other keeps one, and neither deletes anything.
      folders: Array.isArray(parsed.folders) ? parsed.folders : [],
    };
  } catch (error) {
    if (error?.code === "ENOENT") {
      return { version: BASE_VERSION, cursor: null, partial: false, posts: {}, folders: [] };
    }
    throw error;
  }
}

/** Writes the snapshot deterministically, so committing it produces readable diffs. */
export async function writeBase(ctx, base, path = BASE_FILE) {
  const posts = {};
  for (const address of Object.keys(base.posts ?? {}).sort()) {
    posts[address] = normalizeSnapshot(base.posts[address]);
  }
  const body = {
    version: BASE_VERSION,
    cursor: base.cursor ?? null,
    // Sticky on purpose: a snapshot taken from an incomplete read must keep saying so,
    // because `push --prune` reads this flag to refuse deleting what was never projected.
    partial: Boolean(base.partial),
    posts,
    folders: [...(base.folders ?? [])]
      .map((marker) => ({ site: marker.site, folder: marker.folder }))
      .sort((a, b) => a.site.localeCompare(b.site) || a.folder.localeCompare(b.folder)),
  };
  const target = join(ctx.cwd, path);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, `${JSON.stringify(body, null, 2)}\n`, "utf8");
}

function normalizeSnapshot(doc) {
  const data = {};
  for (const key of Object.keys(doc.data ?? {}).sort()) data[key] = doc.data[key];
  return {
    site: doc.site,
    url: doc.url,
    type: doc.type ?? null,
    folder: doc.folder ?? null,
    data,
  };
}

/**
 * Reads the local projection.
 *
 * @param {object} ctx
 * @param {{ dir?: string, site?: string }} [options]
 * @returns {Promise<Record<string, object>>} address → doc
 */
export async function readLocal(ctx, { dir = CONTENT_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  const docs = {};
  for (const relPath of files.sort()) {
    if (!relPath.toLowerCase().endsWith(".md")) continue;
    if (site && !relPath.startsWith(`${site}/`)) continue;
    const text = await ctx.fs.readFile(join(root, relPath), "utf8");
    const doc = parsePost(relPath, text);
    docs[addressOf(doc)] = doc;
  }
  return docs;
}

/**
 * Reads the folder markers (SH149).
 *
 * @returns {Promise<{site: string, folder: string}[]>} sorted, so a document built from
 *   this is byte-stable whatever order the filesystem hands the entries back (P5)
 */
export async function readLocalFolders(ctx, { dir = FOLDERS_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  const markers = [];
  for (const relPath of files.sort()) {
    const name = relPath.split("/").pop() ?? "";
    // Only the marker name counts. A stray file here is the author's, not ours to
    // interpret — reading it as a folder would invent one from a typo.
    if (name.toLowerCase() !== FOLDER_MARKER) continue;
    if (site && !relPath.startsWith(`${site}/`)) continue;
    markers.push(parseFolderMarker(relPath, await ctx.fs.readFile(join(root, relPath), "utf8")));
  }
  return markers.sort((a, b) => a.site.localeCompare(b.site) || a.folder.localeCompare(b.folder));
}

/** Writes one folder marker, creating the directories on the way. */
export async function writeFolderMarker(ctx, marker, { dir = FOLDERS_DIR } = {}) {
  const relPath = folderMarkerPathFor(marker);
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeFolder(marker), "utf8");
  return relPath;
}

/** Removes one folder marker; a marker that is already gone is not an error. */
export async function deleteFolderMarker(ctx, marker, { dir = FOLDERS_DIR } = {}) {
  const relPath = folderMarkerPathFor(marker);
  try {
    await ctx.fs.unlink(join(ctx.cwd, dir, relPath));
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
  return relPath;
}

/**
 * Every folder each site holds, read from the context pack's sitemap (SH149).
 *
 * <h3>Why the sitemap and not a new endpoint</h3>
 *
 * The sitemap already emits a row per folder with its structural path, which is exactly
 * and only what a marker needs. A `GET /agent/folders` would be a fourth contract
 * restating it (law P10), and the folder list is not the expensive part of a projection —
 * the post bodies are.
 *
 * <h3>Why it walks, and what it does when it cannot finish</h3>
 *
 * The sitemap is budget-bounded and reports `truncatedAfter` to resume from. A short
 * folder list has the same consequence a short post list has (SH146): `push --prune`
 * would read the folders it never saw as "delete these", and deleting a folder takes
 * every post beneath it. So this walks to the end and reports `truncated` when it could
 * not — the caller routes that into the same refusal.
 *
 * @returns {Promise<{ folders: {site: string, folder: string}[], truncated: boolean }>}
 */
export async function readRemoteFolders(client, { sites = [], maxPages = 200 } = {}) {
  const folders = [];
  let truncated = false;

  for (const site of sites) {
    let from;
    let pages = 0;
    for (;;) {
      // `include=sitemap` and no `ids`: a marker needs the structural path and nothing
      // else, and the UUID was measured as the largest per-row cost in this payload (SH121).
      const { body } = await client.getContext({ site, include: "sitemap", from });
      const pack = JSON.parse(body);
      for (const node of pack?.sitemap ?? []) {
        if (node?.kind === "folder" && node.path != null) {
          folders.push({ site, folder: normalizeUrl(node.path) });
        }
      }
      pages += 1;
      const budget = pack?.budget ?? {};
      if (!budget.truncated) break;
      // `truncated` with no `truncatedAfter` means nothing fit at all, so there is no row
      // to resume after — the walk cannot continue and must say so rather than loop.
      if (!budget.truncatedAfter || budget.truncatedAfter === from || pages >= maxPages) {
        truncated = true;
        break;
      }
      from = budget.truncatedAfter;
    }
  }
  return { folders: dedupeFolders(folders), truncated };
}

function dedupeFolders(folders) {
  const seen = new Map();
  for (const marker of folders) seen.set(`${marker.site}|${marker.folder}`, marker);
  return [...seen.values()].sort(
    (a, b) => a.site.localeCompare(b.site) || a.folder.localeCompare(b.folder),
  );
}

/**
 * The sites a local projection holds — `shio/content/<site>/**` (SH173).
 *
 * This is the scope both commands should read the server with. It is derived rather than
 * configured on purpose: `--site` existing is not a reason for the tool to need telling what
 * its own directory already says.
 *
 * `markers` (SH149) counts too: a site scaffolded as folders and not yet filled holds no
 * posts, and leaving it out of the scope would make its own push read the whole instance.
 */
export function sitesOf(docs, markers = []) {
  const sites = new Set();
  for (const doc of Object.values(docs ?? {})) {
    if (doc?.site) sites.add(doc.site);
  }
  for (const marker of markers ?? []) {
    if (marker?.site) sites.add(marker.site);
  }
  return [...sites].sort((a, b) => a.localeCompare(b));
}

async function walk(ctx, root, prefix) {
  let entries;
  try {
    entries = await ctx.fs.readdir(join(root, prefix), { withFileTypes: true });
  } catch (error) {
    if (error?.code === "ENOENT") return [];
    throw error;
  }
  const out = [];
  for (const entry of entries) {
    const name = entry.name ?? String(entry);
    // A dot-directory is ours (`.shio`) or the tool's (`.git`); either way it is not content.
    if (name.startsWith(".")) continue;
    const rel = prefix ? `${prefix}/${name}` : name;
    if (typeof entry.isDirectory === "function" ? entry.isDirectory() : false) {
      out.push(...(await walk(ctx, root, rel)));
    } else {
      out.push(rel);
    }
  }
  return out;
}

/** Writes one post to its file, creating the folders on the way. */
export async function writeDoc(ctx, doc, bodyField, { dir = CONTENT_DIR } = {}) {
  const relPath = filePathFor(doc);
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializePost(doc, bodyField), "utf8");
  return relPath;
}

/** Removes a post's file. A missing file is already the desired state. */
export async function deleteDoc(ctx, doc, { dir = CONTENT_DIR } = {}) {
  const relPath = filePathFor(doc);
  try {
    await ctx.fs.unlink(join(ctx.cwd, dir, relPath));
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
  return relPath;
}

/** Writes the server's copy beside the local file, for a conflict a human has to read. */
export async function writeRemoteSidecar(ctx, doc, bodyField, { dir = CONTENT_DIR } = {}) {
  const relPath = `${filePathFor(doc)}.remote`;
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializePost(doc, bodyField), "utf8");
  return relPath;
}

/**
 * Reads every post the server holds, as projection documents.
 *
 * <h3>Why a truncated read is an error, not a warning</h3>
 *
 * A short read is not a smaller projection — it is a projection that omits posts, and the
 * very next `push --content --prune` would read those omissions as "delete these". A partial
 * read therefore refuses to proceed unless the caller says `--allow-partial`, and the
 * snapshot it produces stays marked partial so a later prune refuses too. Same rule as
 * SH138: never let a cut read as complete.
 *
 * Since SH146 the cap is no longer a dead end: `find` issues a cursor and `findAll` walks it
 * to the end, so `truncated` here now means the *server* could not offer one — an instance
 * older than SH146, where a large site genuinely cannot be projected. The refusal stays
 * exactly as strict, because the property it protects has not changed; what changed is how
 * rarely it fires.
 *
 * <h3>Scope, and why it is not the whole instance</h3>
 *
 * `sites` is the set the local tree holds (`shio/content/<site>/**`). Reading beyond it was the
 * SH173 defect: an un-addressable post in an unrelated site aborted the whole command, and
 * `--site` "worked around" a scope the command could have derived itself.
 *
 * @returns {Promise<{ docs: Record<string, object>, truncated: boolean, count: number,
 *                     skipped: {address: string, reason: string}[] }>}
 */
export async function readRemote(client, { site = null, sites = [], limit = 200, batch = 25 } = {}) {
  // SH173 — read the sites this tree holds, not the whole instance. Without it a second site
  // whose posts have no friendly URL took the command down, and that site was content the
  // caller never asked about: the CI gate failed on somebody else's data. `shio/content/<site>`
  // already names the ones that matter, so reading anything else was work with a downside and
  // no upside.
  const scopes = site ? [site] : (sites.length ? sites : [null]);
  const docs = {};
  const skipped = [];
  let truncated = false;
  let count = 0;

  for (const scope of scopes) {
    // SH146 — every page, not the first one. `limit` is now the page size rather than a
    // ceiling on the projection: a site with 900 posts is five reads, not a refusal.
    const found = await client.findAll({ site: scope, limit });
    truncated = truncated || Boolean(found.truncated);
    const addresses = (found.results ?? []).map((row) => row.address).filter(Boolean);
    count += addresses.length;

    for (let index = 0; index < addresses.length; index += batch) {
      const slice = addresses.slice(index, index + batch);
      for (const doc of await client.read(slice)) {
        // SH171 gives a per-row error instead of a 404 for the whole call, so an
        // un-addressable post is now a *finding* the caller can be told about rather than
        // the end of the command.
        if (doc?.error) {
          skipped.push({ address: doc.address, reason: doc.error.title ?? doc.error.type ?? "unreadable" });
          continue;
        }
        const parsed = fromAgentDoc(doc);
        if (parsed) docs[addressOf(parsed)] = parsed;
      }
    }
  }
  return { docs, truncated, count, skipped };
}

/**
 * `post:Blog/blog/hello` + an agent read → a projection document.
 *
 * The publish state is deliberately absent: the projection is the DRAFT (see
 * `content-sync.mjs`), so nothing here can flip a live page by being written to disk.
 */
export function fromAgentDoc(doc) {
  const address = String(doc?.address ?? "");
  if (!address.startsWith("post:")) return null;
  const rest = address.slice("post:".length);
  const slash = rest.indexOf("/");
  if (slash < 0) return null;
  return {
    site: rest.slice(0, slash),
    url: normalizeUrl(rest.slice(slash)),
    type: doc.type ?? null,
    folder: doc.folder == null ? null : normalizeUrl(doc.folder),
    data: doc.data && typeof doc.data === "object" ? doc.data : {},
  };
}

/**
 * Body-field lookup by post-type name, from the lossless defs (`shio pull`'s source).
 *
 * A type with no rich-text field simply has no body, and every file records the field it
 * used, so reading a projection back never needs this map at all — it is only consulted
 * when writing.
 */
export function bodyFields(defs) {
  const map = new Map();
  for (const def of defs ?? []) {
    if (def?.name) map.set(def.name, bodyFieldFor(def));
  }
  return map;
}

function dirOf(path) {
  const normalized = String(path).replace(/\\/g, "/");
  const cut = normalized.lastIndexOf("/");
  return cut <= 0 ? normalized : normalized.slice(0, cut);
}
