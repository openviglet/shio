/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
 *
 * WHY THE BASE IS FINGERPRINTS AND NOT VALUES (SH148)
 * The base is only ever *compared*, never read: the merged output takes the remote or the
 * local value, and a conflict names fields and writes nothing. Storing the values meant
 * carrying a second copy of the whole site — in every clone, once the file is committed to
 * share the base — so that it could be compared and discarded. Per-field hashes answer the
 * same three questions. See `digestsOf`.
 */
import { createHash } from "node:crypto";

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

  const { doc, conflicts, collisions } = mergeFields(base, remote, local);
  // SH215 — `collisions` travels beside `conflicts` rather than replacing them: it is a conflict
  // for every purpose (nothing is written, the run stops), and it is a *different fact* about why,
  // so a caller can say "the base cannot describe this value" instead of "you both edited it".
  return conflicts.length
    ? { kind: "conflict", doc, conflicts, ...(collisions.length ? { collisions } : {}) }
    : { kind: "merged", doc, conflicts: [] };
}

/**
 * Per-field three-way merge. Metadata (`type`, `folder`) merges by the same rule as a
 * field, because a post moved on one side and retitled on the other is not a conflict
 * either.
 */
function mergeFields(base, remote, local) {
  // The base contributes fingerprints, the other two contribute values (SH148). That split is
  // the whole change: the two branches that consult the base are equality tests, and the value
  // written out always comes from remote or local.
  const baseDigest = base ? digestsOf(base) : {};
  const flat = { remote: flatten(remote), local: flatten(local) };
  const keys = [
    ...new Set([...Object.keys(baseDigest), ...Object.keys(flat.remote), ...Object.keys(flat.local)]),
  ].sort();

  const merged = {};
  const conflicts = [];
  const collisions = [];
  for (const key of keys) {
    // A key absent from the base fingerprints has to compare equal to a key absent from the
    // side being tested — `equal(undefined, undefined)` was true, and without this a field the
    // server added that neither the base nor the file has would come back a *conflict*.
    const b = baseDigest[key] ?? ABSENT;
    const r = flat.remote[key];
    const l = flat.local[key];
    const againstLocal = compareFingerprints(b, fingerprint(l));
    const againstRemote = compareFingerprints(b, fingerprint(r));
    if (equal(r, l)) {
      merged[key] = r;
    } else if (againstLocal === "collision" || againstRemote === "collision") {
      // SH215 — the base's fingerprint hashes the same as this value and cannot be describing it.
      // Before this the hash match alone read as "unchanged" and the edit was dropped in silence;
      // a collision is now the loudest thing the sync has, because the one thing it must not do
      // is lose an edit quietly.
      collisions.push(label(key));
      conflicts.push(label(key));
      merged[key] = l;
    } else if (againstLocal === "same") {
      merged[key] = r;
    } else if (againstRemote === "same") {
      merged[key] = l;
    } else {
      conflicts.push(label(key));
      // The local value is kept in the returned document so `--ours` is a no-op and
      // `--theirs` has something to replace; nothing is written on a conflict anyway.
      merged[key] = l;
    }
  }
  return { doc: unflatten(merged, local), conflicts, collisions };
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
      conflicts.push({
        address,
        fields: result.conflicts,
        // SH215 — carried through so the caller can name the one conflict that is not "you both
        // edited this": a base fingerprint that collides cannot describe the value it indexes.
        ...(result.collisions ? { collisions: result.collisions } : {}),
      });
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
  const { moves, ambiguous, probable } = detectMoves({ base, remote, local }, { posts, removed });
  // A post that moved is no longer "removed": the whole point is that it is the same post at a
  // new address, so leaving it in this list would have `--prune` delete it.
  const moved = new Set(moves.map((move) => addressOf({ site: move.site, url: move.from })));
  return {
    posts,
    conflicts,
    behind,
    removed: removed.filter((address) => !moved.has(address)),
    moves,
    ambiguousMoves: ambiguous,
    probableMoves: probable,
  };
}

/**
 * Which pushes are moves rather than delete-plus-create (SH150).
 *
 * <h3>The defect</h3>
 *
 * A rename is two file edits — the path and the `url:` frontmatter, which the format already
 * refuses to let disagree — and to a state document that reads as one undeclared post at the
 * old URL and one new post at the new. `--prune` deletes the first and creates the second, so
 * the post that comes out the other side has a new id and none of the old one's audit trail,
 * version history or inbound references. That is the workaround SH134 exists to prevent,
 * reached from the file side.
 *
 * <h3>Three signals, in order of how much they prove</h3>
 *
 * 1. **`movedFrom:` in the file** — an explicit declaration, and the only signal that survives
 *    a rename *and* a rewrite in the same push, because after a rewrite there is no content
 *    left to match on.
 * 2. **Identical content** at a disappeared address and an appeared one: same type, same
 *    folder, same field values. This is the common case (`git mv`, edit `url:`, push) and it
 *    needs nothing from the author.
 * 3. **A folder that differs from the server's** for a post declared at an address the server
 *    already has. Not a rename at all — a relocation, and it is here because `post.upsert`
 *    deliberately does not relocate an existing post, so this was a write that reported
 *    success and moved nothing.
 *
 * <h3>What it refuses, and what it only warns about</h3>
 *
 * Two appeared posts with content identical to the same disappeared one are an **ambiguity**,
 * not a coin flip: either could be the rename and the other the copy, and picking wrong moves
 * the wrong post's history. Reported, and the push stops.
 *
 * A disappeared post and an appeared one that are *plausibly* the same (same type, nothing
 * else matching) are **probable**, and probable is not enough to move something — but it is
 * more than enough to say so, because the alternative is a delete-plus-create that destroys
 * history silently. The caller is told what to add to make it a move.
 */
export function detectMoves({ base = {}, remote = {}, local = {} }, { posts = [], removed = [] } = {}) {
  const moves = [];
  const ambiguous = [];
  const probable = [];
  const claimed = new Set();

  // Only a post the server actually has can be moved; one that is merely missing locally and
  // absent upstream has nothing to move.
  const gone = removed.filter((address) => remote[address]);
  const appeared = posts.filter((doc) => {
    const address = addressOf(doc);
    return !remote[address] && !base[address];
  });

  // 1. `movedFrom:` — explicit and exact.
  for (const doc of appeared) {
    if (!doc.movedFrom) continue;
    const from = addressOf({ site: doc.site, url: doc.movedFrom });
    if (local[from]) {
      // Two files, one claiming to be the new home of a post the other still declares. One of
      // them is wrong and no reading of the pair is safe.
      ambiguous.push({
        to: addressOf(doc),
        reason: `its \`movedFrom: ${doc.movedFrom}\` names a post the tree still declares`,
        candidates: [from],
      });
      continue;
    }
    // A `movedFrom` whose source is not on the server is the *state after* a successful move:
    // harmless, and silently ignoring it is what keeps a document re-runnable (SH82).
    if (!remote[from]) continue;
    claimed.add(from);
    moves.push(moveFor(doc, remote[from], doc.movedFrom, true));
  }

  // 2. Identical content — the rename nobody had to declare.
  for (const doc of appeared) {
    if (doc.movedFrom) continue;
    const matches = gone.filter((address) => !claimed.has(address) && sameContent(remote[address], doc));
    if (matches.length > 1) {
      ambiguous.push({
        to: addressOf(doc),
        reason: "more than one post on the server has exactly this content, so which one moved "
          + "here cannot be told from the files",
        candidates: matches,
      });
      continue;
    }
    if (!matches.length) continue;
    const from = matches[0];
    // Two local files identical to the same server post: the same ambiguity from the other side.
    const rivals = appeared.filter((other) => other !== doc && !other.movedFrom
      && sameContent(remote[from], other));
    if (rivals.length) {
      ambiguous.push({
        to: addressOf(doc),
        reason: "another file in this tree has exactly the same content, so which of them is "
          + `the rename of ${from} cannot be told apart`,
        candidates: [from, ...rivals.map(addressOf)],
      });
      continue;
    }
    claimed.add(from);
    moves.push(moveFor(doc, remote[from], urlOf(from), false));
  }

  // 3. A relocation of a post that is not going anywhere else.
  for (const doc of posts) {
    const address = addressOf(doc);
    const server = remote[address];
    if (!server) continue;
    const to = doc.folder ?? null;
    if (to === null || normalizeFolder(to) === normalizeFolder(server.folder)) continue;
    moves.push({ site: doc.site, from: doc.url, to: null, folder: to, explicit: false });
  }

  // What is left over on each side and might be a pair: worth saying, not enough to act on.
  const unclaimed = gone.filter((address) => !claimed.has(address));
  const unresolved = appeared.filter((doc) => !doc.movedFrom && !claimed.has(sourceOf(moves, doc)));
  for (const doc of unresolved) {
    const likely = unclaimed.filter((address) => remote[address]?.type === doc.type);
    // Exactly one candidate of the same type, or nothing to say. Two candidates is a guess
    // dressed as a warning, and a warning naming the wrong post is worse than none.
    if (likely.length === 1) probable.push({ from: likely[0], to: addressOf(doc) });
  }

  return { moves, ambiguous, probable };
}

/** The address a decided move brought this doc from, or `null` if none did. */
function sourceOf(moves, doc) {
  const move = moves.find((candidate) => candidate.site === doc.site && candidate.to === doc.url);
  return move ? addressOf({ site: move.site, url: move.from }) : null;
}

/** One move: the rename, plus the relocation when the destination folder differs too. */
function moveFor(doc, server, fromUrl, explicit) {
  const folder = doc.folder ?? null;
  const relocates = folder !== null && normalizeFolder(folder) !== normalizeFolder(server.folder);
  return {
    site: doc.site,
    from: fromUrl,
    to: doc.url,
    folder: relocates ? folder : null,
    explicit,
  };
}

/** Same post-type, same folder, same field values — identity by content. */
function sameContent(a, b) {
  if (!a || !b) return false;
  if ((a.type ?? null) !== (b.type ?? null)) return false;
  if (normalizeFolder(a.folder) !== normalizeFolder(b.folder)) return false;
  return stable(dataOf(a)) === stable(dataOf(b));
}

function dataOf(doc) {
  const data = {};
  for (const [name, value] of Object.entries(doc?.data ?? {})) {
    if (value !== undefined) data[name] = value;
  }
  return data;
}

function normalizeFolder(folder) {
  if (folder == null) return null;
  const segments = String(folder).split("/").filter(Boolean);
  return segments.length ? `/${segments.join("/")}` : "/";
}

/** `post:Blog/blog/hello` → `/blog/hello`. */
function urlOf(address) {
  const cut = String(address).indexOf("/");
  return cut < 0 ? "/" : String(address).slice(cut);
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
 * `siteMarkers` (SH1122) adds the site's own settings — `url`, `furl`, `description` and SH1056's
 * locale policy — as a `settings` block on the document, which the server compiles to one more
 * `site.upsert`. Like a folder marker it also makes the site a subject: a tree holding nothing but a
 * `_site.md` emits a document, because configuring a site before writing a page in it is how a site
 * gets built and was the one step that had to leave the tree.
 *
 * @param {{site: string, folder: string}[]} [options.markers] declared empty folders
 * @param {{site: string, url?: string, furl?: string, description?: string,
 *          locales?: object}[]} [options.siteMarkers] declared site settings
 * @param {string[]} [options.sites] sites to emit a document for even with no posts —
 *   without it a site holding only folders produces nothing, which is the "scaffold the
 *   structure before any content exists" case SH149 is for
 */
export function documentsFor(posts, {
  prune = false,
  markers = [],
  siteMarkers = [],
  sites = [],
  moves = [],
  folderMoves = [],
} = {}) {
  const bySite = new Map();
  for (const post of posts) {
    if (!bySite.has(post.site)) bySite.set(post.site, []);
    bySite.get(post.site).push(post);
  }
  const movesBySite = new Map();
  for (const move of moves) {
    if (!move?.site) continue;
    if (!movesBySite.has(move.site)) movesBySite.set(move.site, []);
    movesBySite.get(move.site).push(move);
  }
  for (const site of movesBySite.keys()) {
    if (!bySite.has(site)) bySite.set(site, []);
  }
  const markersBySite = new Map();
  for (const marker of markers) {
    if (!marker?.site) continue;
    if (!markersBySite.has(marker.site)) markersBySite.set(marker.site, []);
    markersBySite.get(marker.site).push(marker.folder ?? "/");
  }
  // SH211 — a folder rename, keyed by `site|<destination>` so the folder entry that declares the
  // destination is the one that carries it. A folder move has no second axis: its whole name
  // chain is its address, so the destination *is* the entry (SH137).
  const folderMovesBySite = new Map();
  for (const move of folderMoves) {
    if (!move?.site || !move.from || !move.to) continue;
    folderMovesBySite.set(`${move.site}|${normalizeFolder(move.to)}`, normalizeFolder(move.from));
    if (!markersBySite.has(move.site)) markersBySite.set(move.site, []);
    // A renamed section whose folder no post names still has to be declared, or the document
    // would ask for a move to a path it never mentions.
    markersBySite.get(move.site).push(normalizeFolder(move.to));
  }
  // SH1122 — a `_site.md` is a declaration about a site, so it makes the site a subject the same
  // way a folder marker does. Without this a tree that has been configured and has no page in it
  // yet would push nothing at all.
  const settingsBySite = new Map();
  for (const marker of siteMarkers) {
    if (!marker?.site) continue;
    settingsBySite.set(marker.site, marker);
  }
  for (const site of [...markersBySite.keys(), ...settingsBySite.keys(), ...sites]) {
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
      // SH211 — `movedFrom` only on the entry that is the destination of a rename, and omitted
      // everywhere else, so a document without one serializes exactly as it did before.
      folders: [...folders].sort().map((path) => {
        const movedFrom = folderMovesBySite.get(`${site}|${normalizeFolder(path)}`);
        return movedFrom ? { path, movedFrom } : { path };
      }),
      // SH150 — sorted, and omitted entirely when there are none, so a document without
      // renames serializes exactly as it did before this existed.
      moves: (movesBySite.get(site) ?? [])
        .slice()
        .sort((a, b) => a.from.localeCompare(b.from))
        .map((move) => ({
          from: move.from,
          to: move.to ?? undefined,
          folder: move.folder ?? undefined,
        })),
      posts: sitePosts.map((post) => ({
        url: post.url,
        type: post.type ?? undefined,
        folder: post.folder ?? "/",
        // SH348 — honoured on *create* only (see ShAgentBatch.Op#locale): an upsert that
        // relocalized an existing post would make a routine push change what language a live page
        // claims to be in. Create is where the loss was, since that is when a tree describes a
        // site somewhere else.
        locale: post.locale ?? undefined,
        // SH348 — honoured on create, like `locale`. The server orders a declaring post after the
        // ones that do not declare, so listing the original anywhere in the tree is enough.
        translationOf: post.translationOf ?? undefined,
        data: post.data ?? {},
        // Absent means "leave the publish state alone" on the server side, so a file that
        // says nothing about publishing cannot unpublish a live page (P7).
        publish: typeof post.publish === "boolean" ? post.publish : undefined,
      })),
      // SH1122 — the four keys and nothing else, and `site` is deliberately not among them: the
      // document already names its site, and a second spelling of it is a second thing that can
      // disagree. Omitted entirely when the marker declares nothing, so a document for a site with
      // no `_site.md` serializes exactly as it did before this existed.
      settings: settingsFor(settingsBySite.get(site)),
      prune,
    };
  }).map((document) => {
    if (!document.moves.length) delete document.moves;
    if (document.settings === undefined) delete document.settings;
    return document;
  });
}

/**
 * One site marker as a document's `settings` block, or `undefined` when it declares nothing.
 *
 * <p>The server filters this to the same four keys where it builds the op, and that duplication is
 * deliberate: a document is written by whoever is pushing, so the rule has to hold for a hand-written
 * one too. What this side adds is that the tree never *writes* a key the server would drop — a file
 * whose contents are silently ignored is the failure §LXXIV.12 is about, one layer along.</p>
 */
function settingsFor(marker) {
  if (!marker) return undefined;
  const settings = {};
  for (const key of SITE_SETTING_KEYS) {
    if (key === "locales") continue;
    if (typeof marker[key] === "string" && marker[key].trim() !== "") settings[key] = marker[key];
  }
  if (marker.locales && typeof marker.locales === "object") settings.locales = marker.locales;
  return Object.keys(settings).length ? settings : undefined;
}

/**
 * The site settings a marker carries, and the order they are compared in.
 *
 * <p>Shared with the document builder above rather than spelled twice: what a marker projects and
 * what a push may send have to be the same list, or the tree writes a key nothing reads. SH1283
 * added `pageType`, the type a new page in the site starts as, which the server's settings op takes
 * after the bindings so the type it names is bound when it is checked.</p>
 */
export const SITE_SETTING_KEYS = ["url", "furl", "description", "pageType", "locales"];

/**
 * Reconciles a site's settings three ways, per key (SH1122).
 *
 * <h2>Why per key, and why not the folder marker's rule</h2>
 *
 * <p>`reconcileFolders` says the server wins and no conflict is possible, because a folder marker
 * carries no value a person authors — it records that a folder exists, which is the server's fact. A
 * <b>site</b> marker is the opposite: every key in it is something somebody chose. So the rule is
 * `reconcileNotes`' — <b>per key, neither side wins, and a key both sides moved is reported</b> — and
 * it is per key rather than per file for the reason `assetMeta` is: "the agent set the url, the curator
 * wrote the description" is two edits that do not touch, and a whole-file hash would call it a
 * collision.</p>
 *
 * <h2>What a conflict does to the file</h2>
 *
 * <p>A marker is one document, so there is no per-key write: a site with any conflicted key is left
 * exactly as it is and reported, which is what the notes loop does one root over. Writing the other
 * three and leaving the fourth would produce a file that is half the server's and half the tree's with
 * nothing saying which.</p>
 *
 * @param {Record<string, Record<string, unknown>>} sides.base   site → key → last agreed value
 * @param {{site: string}[]} sides.remote  the instance's sites, as the context pack reports them
 * @param {{site: string}[]} sides.local   the markers on disk
 * @returns {{write: object[], deleteLocal: string[], conflicts: object[],
 *            agreed: Record<string, Record<string, unknown>>}}
 */
export function reconcileSiteSettings({ base = {}, remote = [], local = [] }) {
  const remoteBySite = new Map((remote ?? []).filter((each) => each?.site)
    .map((each) => [each.site, each]));
  const localBySite = new Map((local ?? []).filter((each) => each?.site)
    .map((each) => [each.site, each]));
  const sites = [...new Set([
    ...Object.keys(base ?? {}), ...remoteBySite.keys(), ...localBySite.keys(),
  ])].sort();

  const write = [];
  const deleteLocal = [];
  const conflicts = [];
  const agreed = {};

  for (const site of sites) {
    const was = (base ?? {})[site] ?? {};
    const there = remoteBySite.get(site) ?? null;
    const here = localBySite.get(site) ?? null;
    // A site the instance no longer holds. The marker goes, and only when this tree wrote it: a
    // marker for a site that never existed here is the author's and not a stale projection.
    if (!there) {
      if (here && Object.hasOwn(base ?? {}, site)) deleteLocal.push(site);
      continue;
    }

    const settled = {};
    const clashes = [];
    let moved = false;
    for (const key of SITE_SETTING_KEYS) {
      const before = canonical(was[key]);
      const remoteValue = canonical(there[key]);
      const localValue = canonical(here?.[key]);
      const remoteMoved = remoteValue !== before;
      const localMoved = here != null && localValue !== before;

      if (remoteMoved && localMoved && remoteValue !== localValue) {
        clashes.push({ site, key, remote: there[key] ?? null, local: here?.[key] ?? null });
        continue;
      }
      // The server's value unless the tree moved this key on its own, which is the same split the
      // notes merge makes: a local edit nobody has pushed yet is not something a read may overwrite.
      const winner = localMoved && !remoteMoved ? here?.[key] : there[key];
      if (winner !== undefined && winner !== null && winner !== "") settled[key] = winner;
      if (remoteMoved) moved = true;
    }

    if (clashes.length) {
      conflicts.push(...clashes);
      continue;
    }
    agreed[site] = Object.fromEntries(
      SITE_SETTING_KEYS.filter((key) => settled[key] !== undefined)
        .map((key) => [key, settled[key]]));
    // Written when the server moved, and when this tree has no file at all: a site whose marker has
    // never been projected needs one even if nothing changed since the last agreement.
    if (moved || here == null) write.push({ site, ...settled });
  }

  return { write, deleteLocal, conflicts, agreed };
}

/**
 * One comparable spelling per value, so an object and its re-encoding are one value.
 *
 * <p>`locales` is a document, and two readings of the same policy are the same policy: comparing by
 * reference would report a change on every pull, which is the drift the merge exists to avoid (P5).
 * A blank and an absent value are also one thing here, for the reason the marker treats them so.</p>
 */
function canonical(value) {
  if (value === undefined || value === null) return null;
  if (typeof value === "string") return value.trim() === "" ? null : value;
  return JSON.stringify(value);
}

/**
 * Splits a site's document into several that each fit the server's op ceiling (SH199).
 *
 * <h3>Why raising the ceiling is not the fix</h3>
 *
 * SH138 gave a compiled document its own ceiling (2000, against a hand-written batch's 200)
 * because a document's op count is not a measure of what the caller asked for. That moved the
 * dead end; it did not remove it. One document per site was the only shape push knew, so a
 * tree past the new ceiling failed exactly as before — and "raise it again" is not available,
 * because the bound exists so that one transaction stays a transaction.
 *
 * <h3>Why a pruning push cannot be split, which is the whole finding</h3>
 *
 * Prune deletes what a document does *not* declare, inside the folders it *does* — so a split
 * changes what prune means, and the two designs that suggest themselves are both unsafe:
 *
 * <ul>
 *   <li><b>Split by declared folder subtree.</b> Every document `documentsFor` builds declares
 *       the site root, because folders come from `ancestorsOf`, which always yields `/`. So each
 *       part's prune is scoped to the *whole site*, and part one would delete everything part
 *       two declares.</li>
 *   <li><b>A content pass, then one pruning pass declaring every folder and no posts.</b> A
 *       prune with no declared posts deletes every post under those folders. It converges, on
 *       an empty site.</li>
 * </ul>
 *
 * So a pruning push stays one document, and a tree too large for one is a **teaching refusal**
 * rather than a split that quietly means something else. Everything else — a plain push, a
 * `--check` drift guard, the common case — splits freely: upserts are independent and
 * idempotent, so the union of the parts converges exactly where the whole would have.
 *
 * @param {object[]} documents one per site, from `documentsFor`
 * @param {{maxOps?: number}} [options] declaration ops per document
 * @returns {object[]} documents in a stable order, each within the ceiling
 */
export function splitDocuments(documents, { maxOps = 1500 } = {}) {
  const out = [];
  for (const document of documents) {
    if (document.prune || opsOf(document) <= maxOps) {
      out.push(document);
      continue;
    }
    const folders = document.folders ?? [];
    const moves = document.moves ?? [];
    // Every part carries the folders and the moves. Folders are cheap, idempotent and needed
    // by whichever posts land in the part; a move is also idempotent (a move whose source is
    // gone is a noop, SH150), and repeating it is cheaper than working out which part its post
    // ended up in.
    const overhead = folders.length + moves.length;
    const room = Math.max(1, maxOps - overhead);
    const posts = document.posts ?? [];
    for (let index = 0; index < posts.length; index += room) {
      const slice = posts.slice(index, index + room);
      out.push({
        ...document,
        folders,
        ...(moves.length ? { moves } : {}),
        posts: slice,
        prune: false,
      });
    }
  }
  return out;
}

/**
 * The ops a document compiles to, counted the way the server counts them.
 *
 * A declared `publish` is a second op on top of the upsert, which is easy to forget and is
 * what makes a document of 1000 posts 2000 ops. Prune deletes are *not* counted: how many
 * there are depends on what is on the server, which the client does not know — which is
 * another reason a pruning document is left whole rather than split to a client-side estimate.
 */
export function opsOf(document) {
  const posts = document?.posts ?? [];
  const publishes = posts.filter((post) => typeof post.publish === "boolean").length;
  return (document?.folders?.length ?? 0) + (document?.moves?.length ?? 0) + posts.length + publishes;
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

/**
 * Which folders were renamed rather than deleted and re-created (SH211).
 *
 * <h3>The defect, and why it is worse than SH150's</h3>
 *
 * SH149 gave a folder a marker file; it did not give it a move. So renaming
 * `shio/folders/Blog/news/_folder.md` to `.../updates/_folder.md` reads as one folder gone and
 * one arrived: the push declares `/updates`, and `--prune` deletes `/news`. A post deleted that
 * way loses one post's history; a **folder** deleted that way takes **every post beneath it**,
 * because `ShFolderWriteService` cascades.
 *
 * <h3>The signals, in order of how much they prove</h3>
 *
 * 1. **`movedFrom:` in the marker** — explicit, exact, and the only signal that survives a
 *    rename the shape of the subtree does not explain.
 * 2. **A subtree that matches** — one gone root and one appeared root whose descendants have the
 *    same relative shape. This is the common case (`git mv`, push) and needs nothing from the
 *    author. It is what makes a section rename work: `/news` + `/news/2026` disappearing while
 *    `/updates` + `/updates/2026` appear is **one** move, not two, because `folder.move` takes
 *    the subtree with it.
 * 3. **One of each** — exactly one unexplained gone root and one unexplained appeared root, with
 *    shapes that do not match. Still unambiguous; the content changed as well as the name.
 *
 * <h3>What it reports, and when that is a refusal</h3>
 *
 * More than one candidate on either side is an **ambiguity**, not a coin flip: pairing wrongly
 * moves the wrong section, and the alternative to guessing is one line of `movedFrom:` in the
 * marker. It is reported and nothing is moved.
 *
 * Whether that stops the push is the **caller's** decision and depends on `--prune`, because
 * that is the only thing an unpaired folder is in danger from: without it the old folder is left
 * alone and refusing would block the legitimate case — one section removed and two added — with
 * no way for the author to say "neither of these is a rename". The one ambiguity that is always
 * a refusal carries `contradictory: true`: a marker whose `movedFrom` names a folder the tree
 * *still declares* is a tree that cannot be read at all, whatever the push is doing.
 *
 * <p>Note the interaction with the friendly-URL rule: a folder move changes **no post's URL**
 * (SH137's whole point), so this is a structural rename with no delivery consequence — which is
 * what makes it safe to infer rather than requiring a declaration.</p>
 *
 * @param {object} sides
 * @param {{site: string, folder: string}[]} sides.base   the folders both sides last agreed on
 * @param {{site: string, folder: string, movedFrom?: string}[]} sides.local markers on disk
 * @param {Set<string>} [sides.declared] every `site|folder` the tree declares, markers and the
 *   ancestors of every projected post — a folder a post names is declared even with no marker
 * @returns {{ moves: {site: string, from: string, to: string, explicit: boolean}[],
 *             ambiguous: {site: string, from?: string, to?: string, reason: string,
 *                         candidates: string[]}[] }}
 */
export function detectFolderMoves({ base = [], local = [], declared = null }) {
  const key = (site, folder) => `${site}|${normalizeFolder(folder)}`;
  const declaredKeys = declared ?? new Set(local.map((marker) => key(marker.site, marker.folder)));
  const moves = [];
  const ambiguous = [];

  const bySite = new Map();
  const site = (name) => {
    if (!bySite.has(name)) bySite.set(name, { gone: [], appeared: [] });
    return bySite.get(name);
  };
  for (const marker of base) {
    if (!marker?.site) continue;
    const folder = normalizeFolder(marker.folder);
    // The site root is never a move: it is where every folder address resolves from, and the
    // server refuses a move onto or off it.
    if (folder === "/") continue;
    if (!declaredKeys.has(key(marker.site, folder))) site(marker.site).gone.push(folder);
  }
  const baseKeys = new Set(base.map((marker) => key(marker.site, marker.folder)));
  for (const marker of local) {
    if (!marker?.site) continue;
    const folder = normalizeFolder(marker.folder);
    if (folder === "/") continue;
    if (!baseKeys.has(key(marker.site, folder))) site(marker.site).appeared.push(folder);
  }

  const sites = [...bySite.keys()].sort((a, b) => a.localeCompare(b));
  for (const name of sites) {
    const { gone, appeared } = bySite.get(name);
    // 1. `movedFrom:` — explicit, and it consumes both ends before anything is inferred.
    const explicitTargets = new Set();
    const explicitSources = new Set();
    for (const marker of local.filter((row) => row.site === name && row.movedFrom)) {
      const from = normalizeFolder(marker.movedFrom);
      const to = normalizeFolder(marker.folder);
      if (declaredKeys.has(key(name, from))) {
        // Two markers, one claiming to be the new home of a folder the other still declares.
        ambiguous.push({
          site: name,
          to,
          // Flagged: this one is a *contradiction* rather than an unproved guess, so it is
          // refused whatever the push is doing. The others only matter when something is
          // about to be deleted.
          contradictory: true,
          reason: `its \`movedFrom: ${from}\` names a folder the tree still declares`,
          candidates: [from],
        });
        continue;
      }
      moves.push({ site: name, from, to, explicit: true });
      explicitTargets.add(to);
      explicitSources.add(from);
    }

    const goneRoots = rootsOf(gone).filter((path) => !explicitSources.has(path));
    const appearedRoots = rootsOf(appeared).filter((path) => !explicitTargets.has(path));
    const unpairedGone = [];
    const takenTargets = new Set();

    // 2. A subtree whose shape matches — the rename nobody had to declare.
    for (const from of goneRoots) {
      const shape = shapeUnder(from, gone);
      const matches = appearedRoots.filter(
        (to) => !takenTargets.has(to) && sameShape(shape, shapeUnder(to, appeared)),
      );
      if (matches.length > 1) {
        ambiguous.push({
          site: name,
          from,
          reason: "more than one folder in the tree has exactly this shape, so which one is the "
            + "rename cannot be told from the files",
          candidates: matches,
        });
        continue;
      }
      if (!matches.length) {
        unpairedGone.push(from);
        continue;
      }
      takenTargets.add(matches[0]);
      moves.push({ site: name, from, to: matches[0], explicit: false });
    }

    // 3. One of each left over: unambiguous even though the shapes differ, because the section
    //    was edited as well as renamed. More than one on either side is a guess, and a guess here
    //    moves the wrong section.
    const unpairedAppeared = appearedRoots.filter((to) => !takenTargets.has(to));
    if (!unpairedGone.length || !unpairedAppeared.length) continue;
    if (unpairedGone.length === 1 && unpairedAppeared.length === 1) {
      moves.push({ site: name, from: unpairedGone[0], to: unpairedAppeared[0], explicit: false });
      continue;
    }
    ambiguous.push({
      site: name,
      reason: `${unpairedGone.length} folder(s) left the tree and ${unpairedAppeared.length} `
        + "arrived, so which is a rename of which cannot be told from the files",
      candidates: [...unpairedGone, ...unpairedAppeared],
    });
  }

  return { moves, ambiguous };
}

/** The paths in `all` that no other path in `all` contains — the tops of the subtrees. */
function rootsOf(all) {
  return [...new Set(all)]
    .sort()
    .filter((path) => !all.some((other) => other !== path && path.startsWith(`${other}/`)));
}

/** The descendants of `root` in `all`, as paths relative to it. */
function shapeUnder(root, all) {
  return all
    .filter((path) => path.startsWith(`${root}/`))
    .map((path) => path.slice(root.length))
    .sort();
}

function sameShape(a, b) {
  return a.length === b.length && a.every((path, index) => path === b[index]);
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

/**
 * Comparison of two posts: identical metadata and identical field values.
 *
 * Either side may be a **digest base** (SH148) rather than a document, because that is all
 * the merge base keeps now — and it is enough, since every question the merge asks is an
 * equality test.
 */
export function same(a, b) {
  if (!a || !b) return false;
  const left = digestsOf(a);
  const right = digestsOf(b);
  const keys = new Set([...Object.keys(left), ...Object.keys(right)]);
  // Field by field since SH215, not one string against another: a whole-digest comparison cannot
  // tell a collision from a match, and it would also read a v2 base's length-free fingerprints as
  // different from every new one. A collision answers `false` here on purpose — that routes into
  // `mergeFields`, which is where the finding is produced.
  for (const key of keys) {
    if (compareFingerprints(left[key] ?? ABSENT, right[key] ?? ABSENT) !== "same") return false;
  }
  return true;
}

/**
 * The per-field fingerprints of a post — the whole merge base (SH148).
 *
 * <h3>Why a hash is not a compromise here</h3>
 *
 * The base used to hold a full copy of every projected field, which is one extra copy of the
 * site on disk and — since committing it is the reasonable choice for a team, so the merge
 * base is shared — one extra copy in every clone. It bought nothing, because the base value
 * is **never read**: `equal(a, b)` is `stable(a) === stable(b)`, so all three questions the
 * merge asks ("did this side change", "did that side", "did both") are string comparisons
 * against the base, and the merged output always takes the *remote* or *local* value. Even a
 * conflict never shows the base — it names the fields and writes nothing. So the values were
 * stored so that they could be compared and then discarded.
 *
 * <h3>What it costs</h3>
 *
 * 64 bits per field. Two different values hashing the same would read as "this field did not
 * change", which is the failure mode every hash-based sync accepts (git included) at far
 * wider scope than one post's field list. The snapshot goes from O(content) to O(fields).
 *
 * Accepts a document *or* an already-digested base entry, so one function serves both the
 * writer and a v1 snapshot being read forward.
 */
export function digestsOf(docOrBase) {
  if (docOrBase && typeof docOrBase.digest === "object" && docOrBase.digest !== null) {
    return docOrBase.digest;
  }
  const digest = {};
  for (const [key, value] of Object.entries(flatten(docOrBase))) {
    digest[key] = fingerprint(value);
  }
  return digest;
}

/** A base entry: the identity, verbatim, plus the fingerprints (SH148). */
export function baseEntryFor(doc) {
  return {
    // Kept as values because they are not compared, they are *used*: a pull deletes a file by
    // the address the base remembers, so a digest here would leave nothing to delete.
    site: doc.site,
    url: doc.url,
    digest: digestsOf(doc),
  };
}

/**
 * One field's fingerprint — a hash of the canonical form `equal` already compares, and since
 * SH215 the canonical form's **byte length** beside it: `<16 hex>.<bytes>`.
 *
 * <h3>The length is not there to make collisions rarer</h3>
 *
 * It would, incidentally, and that is not the point. SH148's trade was deliberate and the risk it
 * accepted is one every hash-based sync accepts. What was not deliberate is *how* a collision
 * fails: two different values hashing the same read as "this field did not change", so the edit is
 * dropped and the sync reports success. A sync that loses an edit must at minimum be able to say
 * so.
 *
 * The length is what makes it **sayable**. Compared as a whole string it would merely be sixteen
 * more bits; compared *part by part* it is a proof: hashes equal and lengths different means the
 * two values cannot be the same value, so this is a collision rather than an unchanged field —
 * which is a finding, not a silent skip. See {@link compareFingerprints}.
 */
export function fingerprint(value) {
  const canonical = stable(value);
  const hash = createHash("sha256").update(canonical).digest("hex").slice(0, 16);
  return `${hash}.${Buffer.byteLength(canonical, "utf8")}`;
}

/**
 * Two fingerprints: `"same"`, `"different"`, or **`"collision"`** (SH215).
 *
 * <h3>Why three answers and not two</h3>
 *
 * `same` and `different` are what the merge has always asked for. `collision` is the third state
 * that used to be indistinguishable from `same`: the hashes agree, so the old comparison called
 * the field unchanged and dropped the edit, and the run said nothing. Equal hashes with unequal
 * lengths *prove* two different values, so the answer is neither — and the caller turns it into a
 * conflict, which is the loudest thing a sync has.
 *
 * <h3>A base written before this still compares by hash alone</h3>
 *
 * A v2 snapshot's fingerprints carry no length. Comparing the whole string against a new one
 * would read every field of every existing project as changed on the first sync after an upgrade,
 * so a missing length means "unknown" and never "different" — the comparison degrades to exactly
 * what SH148 shipped, which is the honest behaviour for a base that cannot answer.
 */
export function compareFingerprints(a, b) {
  const left = partsOf(a);
  const right = partsOf(b);
  if (left.hash !== right.hash) return "different";
  if (left.length !== undefined && right.length !== undefined && left.length !== right.length) {
    return "collision";
  }
  return "same";
}

function partsOf(value) {
  const text = typeof value === "string" ? value : "";
  const cut = text.indexOf(".");
  if (cut < 0) return { hash: text, length: undefined };
  const length = Number(text.slice(cut + 1));
  return {
    hash: text.slice(0, cut),
    length: Number.isFinite(length) ? length : undefined,
  };
}

/** The fingerprint of "this field is not here", so absence compares like a value. */
const ABSENT = fingerprint(undefined);

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
