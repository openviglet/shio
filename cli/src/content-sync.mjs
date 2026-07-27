/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH87 + SH89 — the reconciliation engine behind `shio pull --content` /
 * `shio push --content`. Pure: no fs, no network, no clock.
 *
 * THREE VERSIONS, NOT TWO
 * A sync with only "mine" and "theirs" has to pick one, and picking is a clobber. Both
 * actors here are legitimate writers — a human curating in the console and an agent
 * writing files are the whole product — so the engine keeps the **base**: what the two
 * sides last agreed on. With it, "changed" is a fact rather than a guess, and the common
 * case (one side changed, the other did not) resolves with no conflict at all.
 *
 * This is the SH66 lesson applied to content: the fix for two writers is an explicit
 * boundary, not last-writer-wins.
 *
 * WHY THE MERGE IS PER FIELD
 * A post is not one value. A human fixing a headline and an agent rewriting the body are
 * not in conflict, and a document-level merge would call them one — which trains people
 * to pass `--theirs` reflexively, which is last-writer-wins with extra steps.
 *
 * WHY THERE ARE NO CONFLICT MARKERS
 * `<<<<<<<` in a field value is *content*. The next push would send it to the server and
 * the next publish would put it on the site. So a conflict leaves the file untouched and
 * is reported instead; `pull --content` can write the server's copy beside it as
 * `<file>.remote` (`--show-remote`) so a diff is still one command away.
 */

/**
 * Metadata that participates in the merge, alongside the fields.
 *
 * `publish` is deliberately **not** in this list. The projection holds the DRAFT only —
 * publishing stays an explicit act (P7), and a file tree that also recorded the published
 * copy would make "what does the site show?" a question about which of two files you
 * opened. So `publish:` in a file is an *instruction* that push forwards, never state that
 * pull writes back; treating it as state would mean a pull deleted the instruction the
 * moment the server reported nothing about it.
 */
const META_KEYS = ["type", "folder"];

/**
 * The address a post is keyed by in the base snapshot and every report.
 *
 * @param {{ site: string, url: string }} doc
 */
export function addressOf({ site, url }) {
  return `post:${site}${url}`;
}

/**
 * Reconciles one post across the three versions.
 *
 * Any of the three may be absent, and the absences are the interesting cases: a post
 * missing from `remote` but present in `base` was deleted on the server; missing from
 * `local` but present in `base` was deleted in the file tree. Both are intentional acts
 * by someone, so neither is undone silently.
 *
 * @param {object} versions
 * @param {object|null} versions.base   what the two sides last agreed on
 * @param {object|null} versions.remote what the server holds now
 * @param {object|null} versions.local  what the file says now
 * @returns {{ kind: string, doc: object|null, conflicts: string[] }}
 *   `kind` is one of `unchanged`, `remote-changed`, `local-changed`, `merged`,
 *   `remote-added`, `local-added`, `remote-deleted`, `local-deleted`, `both-deleted`,
 *   `conflict`
 */
export function reconcile({ base = null, remote = null, local = null }) {
  if (!remote && !local) return { kind: "both-deleted", doc: null, conflicts: [] };

  if (!remote) {
    if (!base) return { kind: "local-added", doc: local, conflicts: [] };
    return same(base, local)
      // The server deleted it and nobody touched the file: the deletion is the news.
      ? { kind: "remote-deleted", doc: null, conflicts: [] }
      // Deleted there, edited here. Deleting the file would throw away an edit; keeping
      // it would resurrect a deleted post on the next push. The human decides.
      : { kind: "conflict", doc: local, conflicts: ["deleted-remotely"] };
  }

  if (!local) {
    if (!base) return { kind: "remote-added", doc: remote, conflicts: [] };
    return same(base, remote)
      ? { kind: "local-deleted", doc: null, conflicts: [] }
      : { kind: "conflict", doc: remote, conflicts: ["deleted-locally"] };
  }

  if (same(remote, local)) return { kind: "unchanged", doc: remote, conflicts: [] };
  if (base && same(base, local)) return { kind: "remote-changed", doc: remote, conflicts: [] };
  if (base && same(base, remote)) return { kind: "local-changed", doc: local, conflicts: [] };

  const { doc, conflicts } = mergeFields(base, remote, local);
  return conflicts.length
    ? { kind: "conflict", doc, conflicts }
    : { kind: "merged", doc, conflicts: [] };
}

/**
 * Per-field three-way merge. Metadata (`type`, `folder`) merges by the same rule as a
 * field, because a post moved on one side and retitled on the other is not a conflict
 * either.
 */
function mergeFields(base, remote, local) {
  const flat = { base: flatten(base), remote: flatten(remote), local: flatten(local) };
  const keys = [
    ...new Set([...Object.keys(flat.base), ...Object.keys(flat.remote), ...Object.keys(flat.local)]),
  ].sort();

  const merged = {};
  const conflicts = [];
  for (const key of keys) {
    const b = flat.base[key];
    const r = flat.remote[key];
    const l = flat.local[key];
    if (equal(r, l)) {
      merged[key] = r;
    } else if (equal(b, l)) {
      merged[key] = r;
    } else if (equal(b, r)) {
      merged[key] = l;
    } else {
      conflicts.push(label(key));
      // The local value is kept in the returned document so `--ours` is a no-op and
      // `--theirs` has something to replace; nothing is written on a conflict anyway.
      merged[key] = l;
    }
  }
  return { doc: unflatten(merged, local), conflicts };
}

/** Resolves a conflict in one direction (`--ours` / `--theirs`), field by field. */
export function resolveConflict({ base = null, remote = null, local = null }, side) {
  if (side !== "ours" && side !== "theirs") {
    throw new Error(`shio content: unknown conflict side "${side}" (expected ours or theirs).`);
  }
  if (side === "ours") {
    return { kind: local ? "local-changed" : "local-deleted", doc: local, conflicts: [] };
  }
  return { kind: remote ? "remote-changed" : "remote-deleted", doc: remote, conflicts: [] };
}

/**
 * What `push --content` should declare for each post the file tree holds.
 *
 * The subtle rule, and the one that makes push safe: a post the file tree did **not**
 * change but the server did is declared with the **server's** values. Declaring the base
 * values instead would quietly revert a human's console edit, and omitting the post
 * entirely would put it outside the declared set, where `--prune` would delete it. So it
 * is declared exactly as it already is — a no-op that keeps the edit and keeps the post
 * inside the document's scope.
 *
 * @param {Map<string, object>|object} versions `{ base, remote, local }` keyed by address
 * @returns {{ posts: object[], conflicts: {address: string, fields: string[]}[],
 *            behind: string[], removed: string[] }}
 */
export function planPushContent({ base = {}, remote = {}, local = {} }, { force = false } = {}) {
  const posts = [];
  const conflicts = [];
  const behind = [];
  const removed = [];

  for (const address of Object.keys(local).sort()) {
    const result = reconcile({
      base: base[address] ?? null,
      remote: remote[address] ?? null,
      local: local[address],
    });
    if (result.kind === "conflict" && !force) {
      conflicts.push({ address, fields: result.conflicts });
      continue;
    }
    if (result.kind === "remote-changed") {
      // The server is ahead here. Declaring it as-is is the no-op that preserves it.
      behind.push(address);
      posts.push(result.doc ?? local[address]);
      continue;
    }
    posts.push(result.kind === "conflict" ? local[address] : result.doc ?? local[address]);
  }

  for (const address of Object.keys(base).sort()) {
    if (!local[address] && remote[address]) removed.push(address);
  }
  return { posts, conflicts, behind, removed };
}

/**
 * Turns reconciled posts into desired-state documents, one per site (SH82's shape).
 *
 * Folders are declared from the posts' own `folder` values plus every ancestor: prune is
 * bounded by the declared folders, and a document that declared `/blog/2026` without
 * `/blog` would put a whole level outside its own scope.
 *
 * `markers` (SH149) adds the folders that no post names — an empty section, which is what
 * a `_folder.md` exists to declare. It is a union with the derived set, and their ancestors
 * are added for the same reason. **It widens prune's scope**, and that is the correct
 * semantics rather than a hazard to special-case: declaring `/archive` means the tree owns
 * `/archive`, and the identical widening already happened when a *post* named it. A marker
 * only reaches the same rule through a different file.
 *
 * @param {{site: string, folder: string}[]} [options.markers] declared empty folders
 * @param {string[]} [options.sites] sites to emit a document for even with no posts —
 *   without it a site holding only folders produces nothing, which is the "scaffold the
 *   structure before any content exists" case SH149 is for
 */
export function documentsFor(posts, { prune = false, markers = [], sites = [] } = {}) {
  const bySite = new Map();
  for (const post of posts) {
    if (!bySite.has(post.site)) bySite.set(post.site, []);
    bySite.get(post.site).push(post);
  }
  const markersBySite = new Map();
  for (const marker of markers) {
    if (!marker?.site) continue;
    if (!markersBySite.has(marker.site)) markersBySite.set(marker.site, []);
    markersBySite.get(marker.site).push(marker.folder ?? "/");
  }
  for (const site of [...markersBySite.keys(), ...sites]) {
    if (!bySite.has(site)) bySite.set(site, []);
  }
  return [...bySite.keys()].sort().map((site) => {
    const sitePosts = bySite.get(site).slice().sort((a, b) => a.url.localeCompare(b.url));
    const folders = new Set();
    for (const post of sitePosts) {
      for (const ancestor of ancestorsOf(post.folder ?? "/")) folders.add(ancestor);
    }
    for (const chain of markersBySite.get(site) ?? []) {
      for (const ancestor of ancestorsOf(chain)) folders.add(ancestor);
    }
    return {
      site,
      folders: [...folders].sort().map((path) => ({ path })),
      posts: sitePosts.map((post) => ({
        url: post.url,
        type: post.type ?? undefined,
        folder: post.folder ?? "/",
        data: post.data ?? {},
        // Absent means "leave the publish state alone" on the server side, so a file that
        // says nothing about publishing cannot unpublish a live page (P7).
        publish: typeof post.publish === "boolean" ? post.publish : undefined,
      })),
      prune,
    };
  });
}

/**
 * Reconciles folder markers three ways (SH149).
 *
 * <h3>Why a folder needs a base at all</h3>
 *
 * A marker holds no values, so there is nothing to merge — existence is the whole state.
 * But existence alone is ambiguous in exactly one case, and it is the destructive one: a
 * marker on disk that the server does not have is *either* a folder scaffolded locally and
 * not yet pushed, *or* a folder that was deleted on the server. Without a base the two are
 * indistinguishable, and guessing wrong either deletes the author's new declaration or
 * silently resurrects a folder somebody removed. So the base snapshot records the folders
 * both sides last agreed on, and the third value decides.
 *
 * <h3>Why a folder a post already implies gets no marker</h3>
 *
 * A marker declares a folder *nothing else does*. Writing one for every folder would double
 * the file count of a projection to restate what each post's `folder:` already says, and
 * every one of those files would then be a thing to keep in sync. `implied` is that set.
 *
 * @param {object} sides
 * @param {{site: string, folder: string}[]} sides.base   last agreed
 * @param {{site: string, folder: string}[]} sides.remote what the server holds
 * @param {{site: string, folder: string}[]} sides.local  markers on disk
 * @param {Set<string>} [sides.implied] `site|folder` keys a projected post already declares
 * @returns {{ write: object[], delete: object[], keep: object[], unchanged: object[],
 *             agreed: object[] }}
 */
export function reconcileFolders({ base = [], remote = [], local = [], implied = new Set() }) {
  const key = (marker) => `${marker.site}|${marker.folder}`;
  const index = (list) => new Map((list ?? []).map((marker) => [key(marker), marker]));
  const baseIndex = index(base);
  const remoteIndex = index(remote);
  const localIndex = index(local);

  const out = { write: [], delete: [], keep: [], unchanged: [], agreed: [] };
  for (const id of [...new Set([...baseIndex.keys(), ...remoteIndex.keys(), ...localIndex.keys()])].sort()) {
    const onServer = remoteIndex.has(id);
    const onDisk = localIndex.has(id);
    const marker = remoteIndex.get(id) ?? localIndex.get(id) ?? baseIndex.get(id);

    if (onServer && onDisk) {
      // Both sides have it and there is nothing to compare — leave the file byte-identical
      // rather than rewriting it, so a pull cannot report a change nobody made.
      out.unchanged.push(marker);
      out.agreed.push(marker);
      continue;
    }
    if (onServer) {
      // The server has a folder the tree does not declare. A post that already names it has
      // declared it, so only the rest need a file.
      out.agreed.push(marker);
      if (!implied.has(id)) out.write.push(marker);
      continue;
    }
    if (baseIndex.has(id)) {
      // Agreed on before, gone from the server now: a deletion, so the marker goes with it.
      out.delete.push(marker);
      continue;
    }
    // On disk, never agreed, not on the server: authored locally and not pushed yet. Keeping
    // it is the whole point of the feature — `push --content` is what makes it real.
    out.keep.push(marker);
    out.agreed.push(marker);
  }
  return out;
}

/** `/a/b` → `["/", "/a", "/a/b"]`. */
export function ancestorsOf(path) {
  const segments = String(path ?? "/")
    .split("/")
    .filter(Boolean);
  const out = ["/"];
  let walked = "";
  for (const segment of segments) {
    walked += `/${segment}`;
    out.push(walked);
  }
  return out;
}

/** Comparison of two posts: identical metadata and identical field values. */
export function same(a, b) {
  if (!a || !b) return false;
  return stable(flatten(a)) === stable(flatten(b));
}

// --- internals ---

function flatten(doc) {
  const flat = {};
  for (const key of META_KEYS) {
    const value = doc?.[key];
    if (value !== undefined) flat[`@${key}`] = value;
  }
  const data = doc?.data && typeof doc.data === "object" ? doc.data : {};
  for (const [name, value] of Object.entries(data)) {
    if (value !== undefined) flat[`.${name}`] = value;
  }
  return flat;
}

function unflatten(flat, template) {
  const doc = { site: template.site, url: template.url, data: {} };
  for (const key of META_KEYS) {
    if (flat[`@${key}`] !== undefined) doc[key] = flat[`@${key}`];
  }
  for (const [key, value] of Object.entries(flat)) {
    if (key.startsWith(".")) doc.data[key.slice(1)] = value;
  }
  if (template.bodyField) doc.bodyField = template.bodyField;
  // Carried, not merged: see the META_KEYS note.
  if (template.publish !== undefined) doc.publish = template.publish;
  return doc;
}

function label(key) {
  return key.startsWith("@") ? key.slice(1) : `fields.${key.slice(1)}`;
}

function equal(a, b) {
  return stable(a) === stable(b);
}

/** Key-order-independent stringification, so a re-serialized object is not a change. */
function stable(value) {
  if (value === undefined) return "\u0000undefined"; // a sentinel no JSON value can produce
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stable).join(",")}]`;
  return `{${Object.keys(value)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stable(value[key])}`)
    .join(",")}}`;
}
