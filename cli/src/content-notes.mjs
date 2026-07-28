/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH161 — the site memory as a file, and the three-way reconciliation that keeps it honest.
 *
 * A site's SH114 notes are what earlier sessions worked out about it. Until this, they lived
 * only in the instance that learned them: `shio pull --content` did not project them, so
 * rebuilding a site from files silently dropped every conclusion anyone had reached. The
 * compounding saving the memory exists to produce only compounds if it survives a move.
 *
 * Pure — format and merge only. The IO lives in `content-store.mjs` beside the rest.
 */

/** Where a site's notes project to, inside that site's content directory. */
export const NOTES_FILE = ".memory.yaml";

/**
 * The path a site's notes project to, relative to the content root.
 *
 * Inside `shio/content/<site>/`, unlike folder markers (SH149), and the difference is the
 * reason rather than an inconsistency. A folder marker had to live in its own root because
 * under the content directory the path segments are *URL* segments, so a marker there would
 * be claiming a URL prefix is a folder chain. A notes file makes no such claim: it is one
 * file per site, named for what it is, sitting at the site's own root. The leading dot keeps
 * it out of the `**\/*.md` glob that finds posts.
 */
export function notesPathFor(site) {
  return `${site}/${NOTES_FILE}`;
}

/** `Blog/.memory.yaml` → `Blog`. The inverse; null for anything else. */
export function notesSiteOf(relPath) {
  const parts = String(relPath).replace(/\\/g, "/").split("/").filter(Boolean);
  if (parts.length !== 2 || parts[1] !== NOTES_FILE) return null;
  return parts[0];
}

/**
 * Serializes a site's notes.
 *
 * <p>Sorted by key and rendered one entry per key, so the committed file diffs by note
 * rather than by reshuffle (P5). `author` travels because attribution is load-bearing —
 * SH114 keeps it precisely so a human's instruction and an agent's inference stay
 * distinguishable — and `updated` does not: it is this instance's bookkeeping, it would
 * change the file's bytes on every pull that touched a note for any reason, and the
 * question it answers ("when did we learn this") is not one the next session acts on.</p>
 *
 * @param {{ site: string, notes: Array<{key: string, note: string, author?: string}> }} input
 * @returns {string}
 */
export function serializeNotes({ site, notes }) {
  const lines = [
    "# SH114 instance memory for this site — what earlier sessions worked out.",
    "# Written by `shio pull --content`, applied by `shio push --content`.",
    `site: ${scalar(site)}`,
    "notes:",
  ];
  for (const entry of [...notes].sort((a, b) => a.key.localeCompare(b.key))) {
    lines.push(`  - key: ${scalar(entry.key)}`);
    lines.push(`    note: ${scalar(entry.note)}`);
    if (entry.author) lines.push(`    author: ${scalar(entry.author)}`);
  }
  return `${lines.join("\n")}\n`;
}

/**
 * Parses a notes file.
 *
 * <p>A deliberately small reader for a deliberately small format — the same trade
 * `content-files.mjs` makes, and for the same reason: pulling in a YAML library to read four
 * keys would add a dependency to `@viglet/shio` that every `npx shio` install pays for.</p>
 *
 * @param {string} relPath for the error message
 * @param {string} text
 * @returns {{ site: string|null, notes: Array<{key, note, author}> }}
 */
export function parseNotes(relPath, text) {
  let site = null;
  const notes = [];
  let current = null;

  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.replace(/\s+$/, "");
    if (!line || line.trimStart().startsWith("#")) continue;

    const top = /^([A-Za-z][\w-]*):\s*(.*)$/.exec(line);
    if (top) {
      if (top[1] === "site") site = unscalar(top[2]);
      // `notes:` opens the list; anything else at the top level is ignored rather than
      // rejected, so a future key does not make an older CLI refuse the file.
      continue;
    }

    const item = /^\s+-\s+([A-Za-z][\w-]*):\s*(.*)$/.exec(line);
    if (item) {
      if (current) notes.push(current);
      current = { key: null, note: null, author: null };
      current[item[1]] = unscalar(item[2]);
      continue;
    }

    const prop = /^\s+([A-Za-z][\w-]*):\s*(.*)$/.exec(line);
    if (prop && current) current[prop[1]] = unscalar(prop[2]);
  }
  if (current) notes.push(current);

  const usable = notes.filter((entry) => entry.key && entry.note);
  if (usable.length !== notes.length) {
    throw new Error(
      `shio: ${relPath} has ${notes.length - usable.length} entr(y|ies) missing \`key:\` or ` +
        "`note:`. A note is a key and an instruction; an entry with neither is not a note.",
    );
  }
  return { site, notes: usable };
}

/**
 * Three-way reconciliation over one site's notes.
 *
 * <p>The same discipline as the post merge, one dimension simpler: a note is a single value,
 * so there are no per-field merges and the only outcomes per key are take-one-side, delete,
 * or conflict.</p>
 *
 * <p><b>Absence is the interesting case, and it is why the base is required.</b> A key the
 * server has and the file does not is *either* a note somebody just wrote on the server *or*
 * a note somebody deleted from the file — and existence alone cannot tell those apart. The
 * base is what distinguishes them, exactly as SH149 needed it for folder markers. With no
 * base entry, both sides' presences read as additions, which is the safe reading: it writes
 * and it never deletes.</p>
 *
 * @param {{ base: Record<string,string>, remote: Array, local: Array }} input
 *   `base` maps key → the note text last agreed on.
 * @returns {{ write: Array, deleteLocal: string[], forgetRemote: string[], conflicts: Array,
 *            agreed: Record<string,string> }}
 */
export function reconcileNotes({ base = {}, remote = [], local = [] }) {
  const remoteByKey = index(remote);
  const localByKey = index(local);
  const keys = [...new Set([...Object.keys(base), ...remoteByKey.keys(), ...localByKey.keys()])].sort();

  const write = [];
  const deleteLocal = [];
  const forgetRemote = [];
  const conflicts = [];
  const agreed = {};

  for (const key of keys) {
    const wasAgreed = Object.hasOwn(base, key);
    const before = base[key] ?? null;
    const there = remoteByKey.get(key) ?? null;
    const here = localByKey.get(key) ?? null;

    const remoteMoved = (there?.note ?? null) !== before;
    const localMoved = (here?.note ?? null) !== before;

    if (!remoteMoved && !localMoved) {
      // Identical on both sides, or absent from both. Only the first is worth remembering.
      if (there) agreed[key] = there.note;
      continue;
    }
    if (remoteMoved && !localMoved) {
      if (!there) {
        deleteLocal.push(key);
      } else {
        write.push(there);
        agreed[key] = there.note;
      }
      continue;
    }
    if (localMoved && !remoteMoved) {
      if (!here) {
        // Locally deleted a note both sides agreed on. Only meaningful when there *was* an
        // agreement — otherwise "absent here, absent there" is not a deletion at all.
        if (wasAgreed && there) forgetRemote.push(key);
      } else {
        agreed[key] = here.note;
      }
      continue;
    }
    // Both moved. Identical edits are not a conflict — they are the same conclusion reached
    // twice, which is exactly what a shared convention looks like.
    if (there && here && there.note === here.note) {
      agreed[key] = there.note;
      continue;
    }
    conflicts.push({ key, remote: there?.note ?? null, local: here?.note ?? null });
  }

  return { write, deleteLocal, forgetRemote, conflicts, agreed };
}

function index(notes) {
  const map = new Map();
  for (const entry of notes ?? []) {
    if (entry?.key) map.set(entry.key, entry);
  }
  return map;
}

/**
 * Quoted whenever a bare scalar would not come back exactly (SH254).
 *
 * <p>This used to claim it was "the same rule `content-files` uses" and was missing one of that
 * rule's clauses: `encodeScalar` quotes when `value !== value.trim()`, while the charset below
 * happily allows a <b>trailing space</b> — and `parseNotes` strips trailing whitespace off every
 * line, so the value could not survive. The consequence was not cosmetic: with an empty base (a
 * fresh clone), `reconcileNotes` saw the server's `"terse "` and the file's `"terse"` as two
 * different edits and reported a <b>conflict</b>, on every pull, forever, while the message
 * advised editing the file or running `shio remember` — neither of which can settle a value the
 * format drops on the way in.</p>
 *
 * <p><b>The two rules are deliberately still two.</b> These are separate readers on purpose
 * (SH161 chose a small dedicated one over a YAML dependency for four keys), and their bare-scalar
 * charsets differ because their parsers do. What has to agree is not the output but the
 * <i>property</i> — that a value comes back exactly — and that is asserted directly, as a table
 * over adversarial values run against all three of this tree's round trips (SH255). A comment
 * claiming parity is what made this invisible to review in the first place.</p>
 */
function scalar(value) {
  const text = String(value ?? "");
  const bare =
    /^[A-Za-z0-9][\w .,/@-]*$/.test(text) &&
    // The clause that was missing. `parseNotes` trims, so trailing whitespace has to be quoted
    // or it is simply gone.
    text === text.trim() &&
    !/^(true|false|null|yes|no|on|off)$/i.test(text);
  return bare ? text : JSON.stringify(text);
}

function unscalar(raw) {
  const text = String(raw ?? "").trim();
  if (text.startsWith('"')) {
    try {
      return JSON.parse(text);
    } catch {
      return text.replace(/^"|"$/g, "");
    }
  }
  return text;
}
