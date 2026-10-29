/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
  SITE_MARKER,
  bodyFieldFor,
  fault,
  filePathFor,
  folderMarkerPathFor,
  normalizeUrl,
  storedUrl,
  parseFolderMarker,
  parseSiteMarker,
  siteLocalePolicy,
  serializeSite,
  siteMarkerPathFor,
  parsePost,
  projectedPost,
  serializeFolder,
  serializePost,
  sidecarFieldsFor,
  sidecarsOf,
} from "./content-files.mjs";
import {
  ASSETS_DIR, ASSET_META_DIR, assetPathFor, assetPathOfMeta, metaPathFor,
  parseAssetMeta, serializeAssetMeta,
} from "./content-assets.mjs";
import {
  INSTANCE_NOTES_PATH,
  NOTES_FILE,
  notesPathFor,
  openNotesPathFor,
  parseNotes,
  serializeInstanceNotes,
  serializeNotes,
  serializeOpenNotes,
} from "./content-notes.mjs";
import { addressOf, baseEntryFor } from "./content-sync.mjs";

/** Where the projection lives, and where the snapshot that tracks it lives. */
export const CONTENT_DIR = "shio/content";
/** The projection root itself — what is *about the installation* rather than about a site. */
export const SHIO_DIR = "shio";
/** Folder markers (SH149) — a root of their own; see `FOLDERS_SUBDIR` for why. */
export const FOLDERS_DIR = "shio/folders";
export const BASE_FILE = "shio/.shio/content-base.json";
/**
 * 3 since SH215: a fingerprint carries the canonical form's byte length beside its hash.
 *
 * Older snapshots are read forward rather than rejected — 2's length-free fingerprints compare by
 * hash alone (`compareFingerprints`), which is exactly what SH148 shipped, and 1's full values are
 * digested on the way in. A version that refused would make an upgrade read every field of every
 * project as changed, which is the drift the whole merge exists to avoid.
 */
export const BASE_VERSION = 3;

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
      // SH148 — a v1 snapshot held full values; they are digested on the way in, so an
      // existing project keeps a usable merge base with no re-pull and nothing to migrate.
      // The next write emits v2, and the values are gone for good the moment they are used
      // for the only thing they were ever used for.
      posts: digestPosts(parsed.posts ?? {}),
      // SH149. Absent in a snapshot written before folders projected, which reads as "we
      // have never agreed about any folder" — so every server folder looks newly added and
      // every local marker looks locally added. Both are the safe reading: one writes a
      // marker, the other keeps one, and neither deletes anything.
      folders: Array.isArray(parsed.folders) ? parsed.folders : [],
      // SH161 — site → { key: note }, the last agreed memory. Absent in a pre-SH161
      // snapshot, which reads as "we have never agreed about any note": every server note
      // is an addition and every local one is kept. Both are the safe reading.
      notes: parsed.notes && typeof parsed.notes === "object" ? parsed.notes : {},
      // SH373 — asset path → the sha-256 both sides last agreed on. Absent in a snapshot written
      // before this, which reads as "we have never agreed about any asset": every difference is
      // then an addition on whichever side has it, which is the safe reading in both directions —
      // nothing is downloaded over a local edit and nothing is uploaded over a server one.
      assets: parsed.assets && typeof parsed.assets === "object" ? parsed.assets : {},
      // SH1032 — asset path → key → the value both sides last agreed on. Absent before this,
      // which reads as "never agreed about any description": every key is then an addition on
      // whichever side has it, and neither direction loses a word somebody wrote.
      assetMeta: parsed.assetMeta && typeof parsed.assetMeta === "object" ? parsed.assetMeta : {},
      // SH1122 — site → { key: value }, the settings both sides last agreed on. Absent before this,
      // which reads as "we have never agreed about any setting": every value is then an addition on
      // whichever side has it, so nothing is written over a local edit and nothing is pushed over a
      // server one. The safe reading in both directions, which is the rule every section here follows.
      sites: parsed.sites && typeof parsed.sites === "object" ? parsed.sites : {},
    };
  } catch (error) {
    if (error?.code === "ENOENT") {
      return {
        version: BASE_VERSION, cursor: null, partial: false,
        posts: {}, folders: [], notes: {}, assets: {}, assetMeta: {}, sites: {},
      };
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
    notes: sortedNotes(base.notes),
    // SH373 — sorted by path, so committing the snapshot shows one asset changing and nothing else.
    assets: sortedAssets(base.assets),
    // SH1032 — per KEY, the shape `notes` has, not per asset. Two writers on one image are the
    // only conflict this tree can have (each asset owns its own file), and they are usually
    // writing different keys: an agent describing the image and a curator captioning it. A
    // whole-file hash would make that pair a conflict, which is the merge reporting a collision
    // between two edits that do not touch.
    assetMeta: sortedAssetMeta(base.assetMeta),
    // SH1122 — per KEY, the shape `notes` and `assetMeta` have. A site marker holds four settings a
    // person authors, and a whole-file hash would make "the agent set the url, the curator wrote the
    // description" a conflict between two edits that do not touch.
    sites: sortedSiteSettings(base.sites),
  };
  const target = join(ctx.cwd, path);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, `${JSON.stringify(body, null, 2)}\n`, "utf8");
}

/** SH1122 — site → key → the agreed value, both levels sorted (P5). */
function sortedSiteSettings(sites) {
  const out = {};
  for (const site of Object.keys(sites ?? {}).sort()) {
    const settings = sites[site] ?? {};
    const kept = {};
    for (const key of Object.keys(settings).sort()) {
      if (settings[key] !== undefined) kept[key] = settings[key];
    }
    out[site] = kept;
  }
  return out;
}

/** Asset path → agreed sha-256, sorted (P5). */
function sortedAssets(assets) {
  const out = {};
  for (const path of Object.keys(assets ?? {}).sort((a, b) => a.localeCompare(b))) {
    out[path] = assets[path];
  }
  return out;
}

/** Asset path → key → agreed value, both levels sorted (SH1032), the way `notes` is. */
function sortedAssetMeta(assetMeta) {
  const out = {};
  for (const path of Object.keys(assetMeta ?? {}).sort((a, b) => a.localeCompare(b))) {
    const keys = assetMeta[path] ?? {};
    const keyed = {};
    for (const key of Object.keys(keys).sort((a, b) => a.localeCompare(b))) keyed[key] = keys[key];
    out[path] = keyed;
  }
  return out;
}

/** Site → key → note, both levels sorted, so the committed diff shows an edit (P5). */
function sortedNotes(notes) {
  const out = {};
  for (const site of Object.keys(notes ?? {}).sort()) {
    const entries = notes[site] ?? {};
    const keyed = {};
    for (const key of Object.keys(entries).sort()) keyed[key] = entries[key];
    out[site] = keyed;
  }
  return out;
}

/** Every entry as fingerprints, whichever shape it was stored in (SH148). */
function digestPosts(posts) {
  const out = {};
  for (const [address, entry] of Object.entries(posts)) out[address] = baseEntryFor(entry);
  return out;
}

/**
 * One base entry, as fingerprints (SH148).
 *
 * Callers keep handing whole documents — a pull's merge result, a push's declared post — and
 * the conversion happens here, in the one place that writes the file. `baseEntryFor` also
 * accepts an entry that is already digested, so re-writing a snapshot that was read from disk
 * is a no-op rather than a hash of a hash.
 */
function normalizeSnapshot(doc) {
  const entry = baseEntryFor(doc);
  const digest = {};
  // Sorted, so the committed file's diff shows a changed field and not a reshuffle (P5).
  for (const key of Object.keys(entry.digest).sort((a, b) => a.localeCompare(b))) {
    digest[key] = entry.digest[key];
  }
  return { site: entry.site, url: entry.url, digest };
}

/**
 * Reads the local projection.
 *
 * @param {object} ctx
 * @param {{ dir?: string, site?: string }} [options]
 * @returns {Promise<Record<string, object>>} address → doc
 */
export async function readLocal(ctx, { dir = CONTENT_DIR, site = null, sidecars = "load" } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  const docs = {};
  const missingSidecars = [];
  const paths = new Map();
  for (const relPath of files.sort()) {
    if (!relPath.toLowerCase().endsWith(".md")) continue;
    if (site && !relPath.startsWith(`${site}/`)) continue;
    const text = await ctx.fs.readFile(join(root, relPath), "utf8");
    const doc = parsePost(relPath, text);
    if (sidecars === "report") {
      // SH374 — `report` exists for the one caller that must not stop at the first offender.
      // `loadSidecars` refuses, which is right for a *write* (publishing a layout with no HTML
      // from a push that reported success is the worse reading) and wrong for a *lint*: an
      // exception on the first missing file is exactly the shape SH212 replaced with findings.
      missingSidecars.push(...await reportMissingSidecars(ctx, root, relPath, doc));
    } else {
      await loadSidecars(ctx, root, relPath, doc);
    }
    const address = addressOf(doc);
    // SH400 — the assignment below is a silent drop when two files agree on an address, so
    // record every path that reached it before one of them stops being reachable.
    if (paths.has(address)) paths.get(address).push(relPath);
    else paths.set(address, [relPath]);
    docs[address] = doc;
  }
  const duplicates = duplicatesFrom(paths, dir);
  const unportable = unportableFrom(paths, dir);
  if (sidecars === "report") return { docs, missingSidecars, duplicates, unportable };
  if (duplicates.length) throw duplicateFault(duplicates[0]);
  if (unportable.length) throw unportableFault(unportable[0]);
  return docs;
}

/**
 * Which addresses more than one file claims (SH400) — `[{ address, site, url, paths }]`, sorted.
 *
 * `pathToUrl` drops a trailing `index` segment, so `blog/index.md` and `blog.md` project to one
 * URL, and the map `readLocal` builds keeps whichever it read last. A pull never writes the
 * collision — `urlToPath` emits `index.md` only for the site root — which is exactly why nothing
 * catches it: the tree is the authoring surface (P4), and an agent reaching for the `index.md`
 * convention is not told it has spelled a name it already used a second way.
 */
function duplicatesFrom(paths, dir) {
  const out = [];
  for (const [address, relPaths] of paths) {
    if (relPaths.length < 2) continue;
    const { site, url } = splitAddress(address);
    out.push({ address, site, url, paths: relPaths.map((relPath) => `${dir}/${relPath}`) });
  }
  return out.sort((a, b) => a.address.localeCompare(b.address));
}

/**
 * Which files a case-insensitive filesystem would hold as one (SH401) — the local half.
 *
 * SH401's other half stops a *pull* from writing `hello.md` and `Hello.md`, because Windows and
 * macOS keep one of them and the run reports both. This is the same pair arriving the other way:
 * authored by hand on Linux, where they coexist happily and every gate passes. It is still a tree
 * nobody on another machine can check out, and the projection is committed and shared (P4) — so
 * the rule is the same in both directions, and a tree that is only valid on one filesystem is not
 * one this command calls clean.
 *
 * Two files reaching **one address** is SH400's finding and not this one; these are two addresses.
 */
function unportableFrom(paths, dir) {
  const folded = new Map();
  for (const [address, relPaths] of paths) {
    for (const relPath of relPaths) {
      const key = relPath.toLowerCase();
      if (folded.has(key)) folded.get(key).push({ address, relPath });
      else folded.set(key, [{ address, relPath }]);
    }
  }
  const out = [];
  for (const entries of folded.values()) {
    // One address with two spellings cannot happen — a path decides the address — so anything
    // here is genuinely two posts, which is what makes it SH400's finding and not a duplicate.
    if (entries.length < 2) continue;
    const sorted = [...entries].sort((a, b) => a.relPath.localeCompare(b.relPath));
    out.push({
      addresses: sorted.map((entry) => entry.address),
      paths: sorted.map((entry) => `${dir}/${entry.relPath}`),
    });
  }
  return out.sort((a, b) => a.paths[0].localeCompare(b.paths[0]));
}

/** The refusal a write gets for an unportable pair (SH401), naming both files. */
function unportableFault(entry) {
  return fault(entry.paths[0],
    `it and ${entry.paths.slice(1).join(", ")} differ only in case, so a Windows or macOS `
      + "checkout of this tree holds one of them and loses the other. Rename one of the posts "
      + "(`url:` and the file together) — `shio verify` lists every such pair");
}

function splitAddress(address) {
  const rest = address.slice("post:".length);
  const cut = rest.indexOf("/");
  return cut < 0 ? { site: rest, url: "/" } : { site: rest.slice(0, cut), url: rest.slice(cut) };
}

/**
 * The refusal a write gets (SH400), on the argument that already governs a missing sidecar.
 *
 * Delivering one of two files is worse than stopping: `post.upsert` declares *whole* state, so the
 * survivor's body replaces the other's on the server, from a command that reported success. The
 * message names both paths because the fix is a choice between them and nothing else can make it.
 */
function duplicateFault(duplicate) {
  return fault(duplicate.paths[0],
    `it and ${duplicate.paths.slice(1).join(", ")} both project to \`${duplicate.address}\`. `
      + "A trailing `index` segment is dropped from a path's URL, so `<name>/index.md` and "
      + "`<name>.md` name one post. Keep one of them — `shio verify` lists every such pair");
}

/** Which of a post's `files:` entries name nothing (SH374) — reported, never thrown. */
async function reportMissingSidecars(ctx, root, relPath, doc) {
  const dir = relPath.slice(0, relPath.lastIndexOf("/") + 1);
  const missing = [];
  for (const [field, name] of Object.entries(doc.sidecarFiles ?? {})) {
    try {
      doc.data[field] = await ctx.fs.readFile(join(root, `${dir}${name}`), "utf8");
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
      missing.push({ doc, field, name, expected: `${dir}${name}` });
    }
  }
  return missing;
}

/**
 * Fills in the values the frontmatter's `files:` block points at (SH191).
 *
 * <h3>Why a missing sidecar is a refusal</h3>
 *
 * The alternative readings are both worse than stopping. Treating it as an empty value
 * publishes a layout with no HTML — a blank site, from a push that reported success.
 * Treating it as an absent field makes `push --content` declare a post without the field it
 * has, and `post.upsert` declares *whole* state, so the server's copy is cleared too. Same
 * class as the `url:`-versus-path refusal: half a rename is not a state anyone meant.
 */
async function loadSidecars(ctx, root, relPath, doc) {
  const entries = Object.entries(doc.sidecarFiles ?? {});
  if (!entries.length) return doc;
  const dir = relPath.slice(0, relPath.lastIndexOf("/") + 1);
  for (const [field, name] of entries) {
    try {
      doc.data[field] = await ctx.fs.readFile(join(root, `${dir}${name}`), "utf8");
    } catch (error) {
      if (error?.code !== "ENOENT") throw error;
      throw new Error(
        `shio content: ${relPath}: its \`files: ${field}: ${name}\` names a file that is not ` +
          `there (expected ${dir}${name}). Restore it, or delete the \`files:\` line and put ` +
          "the value back in `fields:`.",
      );
    }
  }
  return doc;
}

/**
 * Reads the folder markers (SH149).
 *
 * @returns {Promise<{site: string, folder: string}[]>} sorted, so a document built from
 *   this is byte-stable whatever order the filesystem hands the entries back (P5)
 */
export async function readLocalFolders(ctx, { dir = FOLDERS_DIR, site = null, mode = "load" } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  const markers = [];
  const paths = new Map();
  for (const relPath of files.sort()) {
    const name = relPath.split("/").pop() ?? "";
    // Only the marker name counts. A stray file here is the author's, not ours to
    // interpret — reading it as a folder would invent one from a typo.
    if (name.toLowerCase() !== FOLDER_MARKER) continue;
    if (site && !relPath.startsWith(`${site}/`)) continue;
    const marker = parseFolderMarker(relPath, await ctx.fs.readFile(join(root, relPath), "utf8"));
    markers.push(marker);
    // SH407 — the local half, exactly as SH400/SH401 do it for posts one root over.
    paths.set(`folder:${marker.site}${marker.folder}`, [relPath]);
  }
  markers.sort((a, b) => a.site.localeCompare(b.site) || a.folder.localeCompare(b.folder));
  const unportable = unportableFrom(paths, dir);
  if (mode === "report") return { markers, unportable };
  if (unportable.length) throw unportableFault(unportable[0]);
  return markers;
}

/**
 * Reads the site markers (SH1122) — the settings half of the folders root.
 *
 * <p>Walks only the site directories' own level: a `_site.md` lives beside the site root's
 * `_folder.md` and nowhere else, so `Blog/news/_site.md` is not a deeper site, it is a mistake, and
 * `parseSiteMarker` refuses it by path rather than this walk guessing what was meant.</p>
 *
 * @returns {Promise<{site: string, url?: string, furl?: string, description?: string,
 *                    locales?: object}[]>} sorted by site, so a document built from this is
 *   byte-stable whatever order the filesystem hands the entries back (P5)
 */
export async function readLocalSites(ctx, { dir = FOLDERS_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  const markers = [];
  for (const relPath of files.sort()) {
    const name = relPath.split("/").pop() ?? "";
    if (name.toLowerCase() !== SITE_MARKER) continue;
    if (site && !relPath.startsWith(`${site}/`)) continue;
    markers.push(parseSiteMarker(relPath, await ctx.fs.readFile(join(root, relPath), "utf8")));
  }
  markers.sort((a, b) => a.site.localeCompare(b.site));
  return markers;
}

/**
 * Writes one site marker (SH1122).
 *
 * <p>Whole-file, like every other marker: the four settings live in one document, so there is no
 * per-key write and the caller hands over the settings it means to keep. `reconcileSiteSettings` is
 * what decides which those are.</p>
 */
export async function writeSiteMarker(ctx, marker, { dir = FOLDERS_DIR } = {}) {
  const relPath = siteMarkerPathFor(marker);
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeSite(marker), "utf8");
  return relPath;
}

/** Removes one site marker; one that is already gone is not an error. */
export async function deleteSiteMarker(ctx, marker, { dir = FOLDERS_DIR } = {}) {
  const relPath = siteMarkerPathFor(marker);
  try {
    await ctx.fs.unlink(join(ctx.cwd, dir, relPath));
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
  return relPath;
}

/** Writes one folder marker, creating the directories on the way. */
export async function writeFolderMarker(ctx, marker, { dir = FOLDERS_DIR } = {}) {
  const relPath = folderMarkerPathFor(marker);
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeFolder(marker), "utf8");
  return relPath;
}

/**
 * One site's notes as the file tree holds them (SH161), or `null` when there is no file.
 *
 * `null` and `[]` are different answers and both callers depend on the difference: no file
 * means this tree has never projected the site's memory, so a push must not read it as
 * "every note was deleted".
 */
export async function readLocalNotes(ctx, site, { dir = CONTENT_DIR } = {}) {
  const relPath = notesPathFor(site);
  try {
    const text = await ctx.fs.readFile(join(ctx.cwd, dir, relPath), "utf8");
    return parseNotes(relPath, text).notes;
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
}

/**
 * Writes one site's open-notes projection (SH1003), or removes it when nothing is open.
 *
 * Removed rather than left empty: a file listing no notes reads as "this site has been checked and
 * is clear", which is true — and a stale one listing a note somebody closed reads the same way and
 * is not. The absence is the honest empty state.
 */
export async function writeOpenNotes(ctx, { site, notes }, { dir = CONTENT_DIR } = {}) {
  const relPath = openNotesPathFor(site);
  const target = join(ctx.cwd, dir, relPath);
  if (!notes.length) {
    try {
      await ctx.fs.rm(target);
      return relPath;
    } catch {
      return null;
    }
  }
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeOpenNotes({ site, notes }), "utf8");
  return relPath;
}

/** Writes one site's notes file. */
export async function writeLocalNotes(ctx, { site, notes }, { dir = CONTENT_DIR } = {}) {
  const relPath = notesPathFor(site);
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeNotes({ site, notes }), "utf8");
  return relPath;
}

/**
 * The instance-wide notes as the file tree holds them (SH231), or `null` when there is no file.
 *
 * <p>Written under {@code SHIO_DIR} rather than {@code CONTENT_DIR}: these describe the
 * installation, not any site, and a push must be able to find them without knowing which sites
 * this tree covers.</p>
 */
export async function readLocalInstanceNotes(ctx, { dir = SHIO_DIR } = {}) {
  try {
    const text = await ctx.fs.readFile(join(ctx.cwd, dir, INSTANCE_NOTES_PATH), "utf8");
    return parseNotes(`${dir}/${INSTANCE_NOTES_PATH}`, text).notes;
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
}

/** Writes the instance-wide notes file (SH231). */
export async function writeLocalInstanceNotes(ctx, { notes }, { dir = SHIO_DIR } = {}) {
  const target = join(ctx.cwd, dir, INSTANCE_NOTES_PATH);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializeInstanceNotes({ notes }), "utf8");
  return `${dir}/${INSTANCE_NOTES_PATH}`;
}

/**
 * The instance-wide notes as the server holds them (SH231) — the ones with no scope.
 *
 * <p>The mirror of {@link readRemoteNotes}, which drops exactly these. Read with no
 * {@code site}, because a scoped read returns a site's notes plus the instance's and the
 * separation is the point.</p>
 */
export async function readRemoteInstanceNotes(agent) {
  const memory = await agent.memory({});
  return (memory?.notes ?? [])
    .filter((note) => !note.scope)
    .map((note) => ({ key: note.key, note: note.note, author: note.author ?? null }));
}

/** Removes a site's notes file; one that is already gone is not an error. */
export async function deleteLocalNotes(ctx, site, { dir = CONTENT_DIR } = {}) {
  const relPath = notesPathFor(site);
  try {
    await ctx.fs.unlink(join(ctx.cwd, dir, relPath));
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
  return relPath;
}

/**
 * One site's notes as the server holds them (SH161) — its own, never the instance-wide ones.
 *
 * `GET /agent/memory?site=` returns the site's notes *plus* the instance-wide ones, because
 * that is the shape a session wants to read. Projecting them all would write another
 * instance's house rules into this repository and push them back somewhere else, which is the
 * surprise P7 exists to prevent — so the instance-wide rows are dropped here rather than
 * asking the server for a second shape.
 */
export async function readRemoteNotes(agent, site) {
  const memory = await agent.memory({ site });
  return (memory?.notes ?? [])
    .filter((note) => note.scope && note.scope.toLowerCase() === String(site).toLowerCase())
    .map((note) => ({ key: note.key, note: note.note, author: note.author ?? null }));
}

/**
 * Every site's own settings, off the context pack's site rows (SH1122).
 *
 * <h3>Why the pack, and why this makes no request of its own</h3>
 *
 * SH1160 put `furl`, `description` and the stored locale policy on the row precisely so this needs no
 * endpoint: a `GET /agent/sites` would be a fourth contract restating one the pull already makes (law
 * P10), and the console's whole-entity site read answers the 5 MB `postTypeLayout` column along with
 * the four short values a marker wants.
 *
 * <p>It is a <b>pure mapper</b> rather than a read because the first version fetched the pack itself
 * and took a settled pull from twelve requests to thirteen — caught by SH398's cost guard, which is
 * the one that measured a 208-request pull down to this. `readRemoteFolders` asks for `sites` beside
 * the `sitemap` it was already asking for, on the first page only, so the whole of this rides a call
 * that was being made anyway (P2).</p>
 *
 * <h3>`localePolicy`, not the three discovery fields</h3>
 *
 * Those three each omit what says the ordinary thing — a default matching the instance's, a
 * single-locale list — and carry no fallback chain at all, so a policy rebuilt from them would be
 * invented in the parts they leave out. `localePolicy` is the stored document, and it is absent for
 * every site that declared nothing, which is the distinction SH1056 drew: a derived policy is an
 * observation, and a marker carrying one would make it a declaration on the next push.
 *
 * @returns {Promise<{site: string, url?: string, furl?: string, description?: string,
 *                    locales?: object}[]>} sorted by site (P5)
 */
export function siteMarkersFrom(rows, { sites = [] } = {}) {
  const wanted = new Set(sites);
  const out = [];
  for (const row of rows ?? []) {
    if (!row?.name) continue;
    if (wanted.size && !wanted.has(row.name)) continue;
    const marker = { site: row.name };
    for (const [key, value] of [["url", row.url], ["furl", row.furl],
      ["description", row.description], ["pageType", row.pageType]]) {
      if (typeof value === "string" && value.trim() !== "") marker[key] = value;
    }
    const policy = siteLocalePolicy(row.localePolicy);
    if (policy !== undefined) marker.locales = policy;
    out.push(marker);
  }
  return out.sort((a, b) => a.site.localeCompare(b.site));
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
 * @param {boolean} [options.ids] also return `folderIds`: `<site>|<chain>` → folder id, which
 *   SH145 needs because an asset upload targets a folder by id while its path names a chain
 * @param {boolean} [options.withSites] SH1122 — also return `siteRows`, the pack's own site rows,
 *   which `siteMarkersFrom` turns into markers. Asked for on the FIRST page of each site's walk and
 *   no other: the rows do not paginate, so repeating the section per page would pay for the same
 *   answer once per page of sitemap. Folded in here rather than read separately because a settled
 *   pull's request count is guarded (SH398) and a second `getContext` broke it
 * @returns {Promise<{ folders: {site: string, folder: string}[], folderIds: Map<string,string>,
 *                     truncated: boolean, siteRows: object[] }>}
 */
export async function readRemoteFolders(client, {
  sites = [], maxPages = 200, ids = false, withSites = false,
} = {}) {
  const folders = [];
  const folderIds = new Map();
  const siteRows = [];
  let truncated = false;

  for (const site of sites) {
    let from;
    let pages = 0;
    for (;;) {
      // `include=sitemap`, and `ids` only when a caller needs them: the UUID was measured as
      // the largest per-row cost in this payload (SH121), so a marker sync does not pay for it.
      const include = withSites && pages === 0 ? "sitemap,sites" : "sitemap";
      const { body } = await client.getContext({ site, include, from, ids: ids || undefined });
      const pack = JSON.parse(body);
      if (withSites && pages === 0) siteRows.push(...(pack?.sites ?? []));
      for (const node of pack?.sitemap ?? []) {
        if (node?.kind === "folder" && node.path != null) {
          const chain = normalizeUrl(node.path);
          // SH1301 — a gated folder's row carries its gate, and the marker carries it on.
          const gate = node.visibility
            ? { visibility: node.visibility, ...(node.groups?.length ? { groups: [...node.groups].sort() } : {}) }
            : {};
          folders.push({ site, folder: chain, ...gate });
          if (node.id) folderIds.set(`${site}|${chain}`, node.id);
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
  return { folders: dedupeFolders(folders), folderIds, truncated, siteRows };
}

/**
 * The asset inventory, as paths (SH145).
 *
 * Composed from two reads the instance already has: the media listing (id, file name,
 * containing folder id) and the sitemap's folder chains, joined on the folder id. There is no
 * new endpoint, because the two halves of an asset path already exist separately — the server
 * simply never had a reason to put them together.
 *
 * @returns {Promise<{ byId: Map<string,string>, byPath: Map<string,object>,
 *                     folderIds: Map<string,string>, truncated: boolean }>}
 */
export async function readRemoteAssets(agent, admin, {
  sites = [],
  folders = null,
  maxFolderReads = MAX_FOLDER_READS,
} = {}) {
  const { folderIds, truncated } = await readRemoteFolders(agent, { sites, ids: true });
  const chains = new Map();
  for (const [key, id] of folderIds) chains.set(id, key.slice(key.indexOf("|") + 1));

  const scope = scopeOf(folders, folderIds, maxFolderReads);
  const byId = new Map();
  const byPath = new Map();
  const unprojectable = [];
  for (const site of sites) {
    // Site-scoped by name: the listing takes an id, and the sitemap read above already proved
    // the site is readable, so resolving it is one call and not a guess. Since SH213 a caller
    // that knows which folders it needs is answered per folder instead.
    const requests = scope
      ? (scope.get(site) ?? []).map((folderId) => ({ folderId }))
      : [{ siteId: site }];
    for (const request of requests) {
      const listed = await admin.listStaticFiles(request);
      // An instance without the media listing answers something that is not a list; that is a
      // site with no projectable assets, not a crash halfway through a pull.
      for (const item of Array.isArray(listed) ? listed : []) {
        if (!item?.id || !item.fileName) continue;
        const chain = chains.get(item.folderId) ?? "/";
        const path = assetPathFor({ ...item, siteName: item.siteName ?? site }, chain);
        if (path === null) {
          // SH256 — a name that contains a separator or navigates has no safe file on disk.
          // Skipped rather than fatal (SH173's rule), and named, because the `File` field
          // pointing at it stays an id and the operator would otherwise have no idea which
          // asset went missing.
          unprojectable.push({ id: item.id, site, folder: chain, fileName: item.fileName });
          continue;
        }
        byId.set(item.id, path);
        byPath.set(path, item);
      }
    }
  }
  return { byId, byPath, folderIds, truncated, unprojectable, scoped: Boolean(scope) };
}

/**
 * How many per-folder listings are worth doing instead of one per site (SH213), measured (SH375).
 *
 * <h3>What the number is, and what it is not</h3>
 *
 * SH213 argued eight from "a bounded, uninteresting number of requests" and left the crossover
 * unmeasured. `ShStaticFileListingBenchmarkTest` measured it and found the premise was wrong in the
 * dangerous direction: `folderId` was applied **in memory** after every published `File` post in
 * the tenant had been loaded and stat-ed, so a folder listing read the whole library — one folder
 * cost 69 rows against the whole site's 69 — and F listings cost F whole-library reads. The
 * narrowing was buying payload and paying for it in server work.
 *
 * The predicate is in the query now, and the same benchmark puts covering the whole library one
 * folder at a time at **84 rows against 69** — about a fifth more, which is the fixed tail read F
 * times. So server work is no longer what bounds this: **round trips are**, and a round trip is a
 * client-side cost no benchmark in this repository can measure. Eight survives as a latency budget
 * rather than a work budget, which is a claim about the deployment and therefore the caller's to
 * change — hence `--max-folder-reads`.
 */
const MAX_FOLDER_READS = 8;

/**
 * `site` → the folder ids to list, or **null** for "list whole sites" (SH213).
 *
 * A referenced folder the server does not have contributes nothing: its assets cannot exist, so
 * there is nothing to resolve and the push will upload. That is why an empty scope is still a
 * scope — it means *no listing at all*, which is the correct answer for a tree whose asset folders
 * are all new, and it is not the same as `null`.
 */
function scopeOf(folders, folderIds, maxFolderReads) {
  if (!Array.isArray(folders)) return null;
  const wanted = new Map();
  const seen = new Set();
  for (const entry of folders) {
    if (!entry?.site) continue;
    const key = `${entry.site}|${normalizeUrl(entry.folder ?? "/")}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const id = folderIds.get(key);
    if (!id) continue;
    if (!wanted.has(entry.site)) wanted.set(entry.site, []);
    wanted.get(entry.site).push(id);
  }
  const reads = [...wanted.values()].reduce((sum, ids) => sum + ids.length, 0);
  if (reads > maxFolderReads) return null;
  // Sorted, so two runs over one tree send the same requests in the same order (P5).
  for (const ids of wanted.values()) ids.sort((a, b) => a.localeCompare(b));
  return wanted;
}

/** Every asset file on disk, as tree-relative paths (SH145). */
export async function readLocalAssets(ctx, { dir = ASSETS_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir);
  const files = await walk(ctx, root, "");
  return files
    .filter((relPath) => !site || relPath.startsWith(`${site}/`))
    .sort((a, b) => a.localeCompare(b));
}

/** Reads one asset's bytes. */
export async function readAssetBytes(ctx, path, { dir = ASSETS_DIR } = {}) {
  return ctx.fs.readFile(join(ctx.cwd, dir, path));
}

/**
 * Every asset that has a metadata file, as ASSET paths (SH1032).
 *
 * <p>Asset paths rather than file paths, because every caller wants to line them up against the
 * bytes: `push` asks "does this asset have words", and `verify` asks "is there a file describing
 * an asset that is gone". A `.json` that names no asset is the second, and it is reported rather
 * than deleted — the tree is the curator's.</p>
 */
export async function readLocalAssetMeta(ctx, { dir = ASSET_META_DIR, site = null } = {}) {
  const files = await walk(ctx, join(ctx.cwd, dir), "");
  return files
    .map((relPath) => assetPathOfMeta(relPath))
    .filter((assetPath) => assetPath && (!site || assetPath.startsWith(`${site}/`)))
    .sort((a, b) => a.localeCompare(b));
}

/** One asset's metadata, or null when nothing describes it. Throws on a file that is not ours. */
export async function readAssetMetaOrNull(ctx, assetPath, { dir = ASSET_META_DIR } = {}) {
  const relPath = metaPathFor(assetPath);
  // SH403 — a path that navigates rather than names has no file, and asking for one would join
  // outside the tree. Null is "nothing describes it", which is exactly true of a path like this.
  if (!relPath) return null;
  let text;
  try {
    text = await ctx.fs.readFile(join(ctx.cwd, dir, relPath), "utf8");
  } catch (error) {
    if (error?.code === "ENOENT") return null;
    throw error;
  }
  return parseAssetMeta(text, `${dir}/${relPath}`);
}

/**
 * Writes one asset's metadata, and REMOVES the file when nothing is left to say (SH1032).
 *
 * <p>The removal is the half that is easy to leave out and wrong to. A file kept after its last
 * key was cleared says "this image is described" to every reader that only checks for the file —
 * `verify`'s finding, a grep, the next push — and the next push would then send back the words a
 * curator deleted. An empty document and an absent one have to mean the same thing, so only one
 * of them is allowed to exist.</p>
 */
export async function writeAssetMeta(ctx, assetPath, meta, { dir = ASSET_META_DIR } = {}) {
  const relPath = metaPathFor(assetPath);
  if (!relPath) return null;
  const target = join(ctx.cwd, dir, relPath);
  const body = serializeAssetMeta(meta);
  if (body.trim() === "{}") {
    await ctx.fs.rm?.(target, { force: true });
    return null;
  }
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, body, "utf8");
  return assetPath;
}

/** Writes one asset's bytes, creating the directories on the way. */
export async function writeAssetBytes(ctx, path, bytes, { dir = ASSETS_DIR } = {}) {
  const target = join(ctx.cwd, dir, path);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, bytes);
  return path;
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

/**
 * Writes one post to its file plus its sidecars, creating the folders on the way (SH191).
 *
 * A sidecar the previous version of this file named and this one does not is **removed**, so
 * a field that stopped being code (or stopped existing) does not leave a `.html` behind that
 * nothing references. That is read off the file on disk rather than inferred, because only
 * the file knows what the last write produced.
 *
 * @param {{name: string, ext: string}[]} [options.sidecars] the post-type's code fields
 */
export async function writeDoc(ctx, doc, bodyField, { dir = CONTENT_DIR, sidecars = [] } = {}) {
  const relPath = filePathFor(doc);
  const target = join(ctx.cwd, dir, relPath);
  const stale = new Set(Object.values(await namedSidecars(ctx, dir, relPath)));
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  const written = sidecarsOf(doc, sidecars);
  for (const sidecar of written) {
    stale.delete(sidecar.name);
    await ctx.fs.writeFile(join(ctx.cwd, dir, sidecar.path), sidecar.text, "utf8");
  }
  await ctx.fs.writeFile(target, serializePost(doc, bodyField, sidecars), "utf8");
  for (const name of stale) await unlinkQuietly(ctx, join(ctx.cwd, dir, dirOf(relPath), name));
  return relPath;
}

/**
 * Removes a post's file and every sidecar it named. A missing file is already the desired state.
 */
export async function deleteDoc(ctx, doc, { dir = CONTENT_DIR } = {}) {
  const relPath = filePathFor(doc);
  for (const name of Object.values(await namedSidecars(ctx, dir, relPath))) {
    await unlinkQuietly(ctx, join(ctx.cwd, dir, dirOf(relPath), name));
  }
  await unlinkQuietly(ctx, join(ctx.cwd, dir, relPath));
  return relPath;
}

/**
 * Writes the server's copy beside the local file, for a conflict a human has to read.
 *
 * Deliberately **whole**: no sidecars, so the values stay in `fields:` and the artifact is one
 * file to open and diff. A `.remote` that spawned its own `.html` and `.js` would leave three
 * files nobody named, and they would not be cleaned up by the write that resolves the conflict.
 */
export async function writeRemoteSidecar(ctx, doc, bodyField, { dir = CONTENT_DIR } = {}) {
  const relPath = `${filePathFor(doc)}.remote`;
  const target = join(ctx.cwd, dir, relPath);
  await ctx.fs.mkdir(dirOf(target), { recursive: true });
  await ctx.fs.writeFile(target, serializePost(doc, bodyField), "utf8");
  return relPath;
}

/** The `files:` block of the post file already on disk, or `{}` when there is none. */
async function namedSidecars(ctx, dir, relPath) {
  try {
    const text = await ctx.fs.readFile(join(ctx.cwd, dir, relPath), "utf8");
    return parsePost(relPath, text).sidecarFiles ?? {};
  } catch {
    // Absent, or unparseable — either way there is nothing this write is responsible for
    // cleaning up, and refusing to write over a broken file would strand the tree.
    return {};
  }
}

async function unlinkQuietly(ctx, target) {
  try {
    await ctx.fs.unlink(target);
  } catch (error) {
    if (error?.code !== "ENOENT") throw error;
  }
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
/** `post:Blog/about` → `/about`; anything else (absent, or another site) → `undefined`. */
function translationUrl(address) {
  if (typeof address !== "string" || !address.startsWith("post:")) return undefined;
  const rest = address.slice("post:".length);
  const slash = rest.indexOf("/");
  return slash < 0 ? undefined : normalizeUrl(rest.slice(slash));
}

export function fromAgentDoc(doc) {
  const address = String(doc?.address ?? "");
  if (!address.startsWith("post:")) return null;
  const rest = address.slice("post:".length);
  const slash = rest.indexOf("/");
  if (slash < 0) return null;
  return {
    site: rest.slice(0, slash),
    // SH585 — the stored spelling when the read reports one, the address's path otherwise. The
    // read omits `url` whenever the two agree, which is every post that does not carry a trailing
    // slash, so this falls back for almost all of them and the tree is byte-identical to before.
    url: doc.url ? storedUrl(doc.url) : normalizeUrl(rest.slice(slash)),
    type: doc.type ?? null,
    folder: doc.folder == null ? null : normalizeUrl(doc.folder),
    // SH348 — the read reports a locale only when it is not the instance default, so this is
    // `undefined` for a monolingual site and the file is byte-identical to before.
    locale: doc.locale ? String(doc.locale) : undefined,
    // SH348 — the read reports the group's anchor as a `post:` address; the tree stores the URL,
    // because a file already knows its own site (the path names it) and a cross-site translation is
    // not a thing a site-scoped tree can hold.
    translationOf: translationUrl(doc.translationOf),
    // SH1058 — the read reports it only when true, so a page whose source has not moved, and every
    // page of a monolingual site, carries nothing and its file is byte-identical to before.
    translationStale: doc.translationStale === true ? true : undefined,
    // SH1301 — the post's own members-only gate, reported only when it has one.
    visibility: doc.visibility ?? undefined,
    groups: doc.visibility && doc.groups?.length ? [...doc.groups].sort() : undefined,
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

/**
 * Sidecar-field lookup by post-type name (SH191) — the same shape as {@link bodyFields}.
 *
 * Only the *write* side consults it, exactly like the body field: every file records the
 * sidecars it produced in its own `files:` block, so reading a projection back never depends
 * on the model being reachable. That is what lets a hand-authored tree be pushed from a clone
 * that has never pulled the post-types.
 */
export function sidecarFields(defs) {
  const map = new Map();
  for (const def of defs ?? []) {
    if (def?.name) map.set(def.name, sidecarFieldsFor(def));
  }
  return map;
}

/**
 * Every server document, as the file tree will hold it (SH253).
 *
 * <p>Both commands call this on the way in, so the base, the server side and the file side are
 * compared in one domain and a value the projection cannot represent reads as *unchanged*
 * rather than as an edit that never lands. See `projectedPost` for why fingerprinting the
 * projected document is only half the fix, and `idsToPaths` (SH145) for the precedent.</p>
 *
 * <p>A document this cannot project is passed through untouched. It is the wrong reading — that
 * post keeps the old defect — but the alternative is taking down a whole pull over one row, and
 * a projection that refuses is a projection nobody can run.</p>
 */
export function projectRemote(docs, defs) {
  const bodyField = bodyFields(defs);
  const sidecarField = sidecarFields(defs);
  const out = {};
  for (const [address, doc] of Object.entries(docs ?? {})) {
    try {
      out[address] = projectedPost(
        doc,
        doc?.bodyField ?? bodyField.get(doc?.type) ?? null,
        sidecarField.get(doc?.type) ?? [],
      );
    } catch {
      out[address] = doc;
    }
  }
  return out;
}

function dirOf(path) {
  const normalized = String(path).replace(/\\/g, "/");
  const cut = normalized.lastIndexOf("/");
  return cut <= 0 ? normalized : normalized.slice(0, cut);
}
