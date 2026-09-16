/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Pure (no fs / no network) helpers for "post-types as code" (SH16): a
 * deterministic serialization of a post-type definition, plus the diff/plan
 * logic that drives `shio push`. Keeping these pure makes them unit-testable and
 * keeps the commands thin.
 *
 * A "def" mirrors the backend `ShPostTypeDef`:
 *   { name, title, namePlural, description, workflowPublishEntity, system,
 *     fields: [ { name, label, description, widget, required, isTitle,
 *                 isSummary, ordinal, widgetSettings, fields: [...] } ] }
 */

function byOrdinalThenName(a, b) {
  const o = (a.ordinal ?? 0) - (b.ordinal ?? 0);
  return o !== 0 ? o : String(a.name ?? "").localeCompare(String(b.name ?? ""));
}

/**
 * Normalize one field into a canonical-key-order object with stable defaults.
 *
 * SH914's three indexing keys are the exception to "materialize every key with its default", and
 * deliberately so. Every key above is written out even when it holds the default, which is what
 * makes two defs comparable by string. Doing that for `indexed` would rewrite **every**
 * `shio/post-types/*.json` in every repository on the next `pull` — a diff touching every field of
 * every type, for a feature none of them use. Omitting an unset declaration keeps those files
 * byte-identical (P5) and costs nothing here, because absent and `false` mean the same thing for
 * all three: not indexed.
 */
function normalizeField(field) {
  const normalized = {
    name: field.name ?? null,
    label: field.label ?? null,
    description: field.description ?? null,
    widget: field.widget ?? null,
    required: Boolean(field.required),
    isTitle: Boolean(field.isTitle),
    isSummary: Boolean(field.isSummary),
    ordinal: field.ordinal ?? 0,
    widgetSettings: field.widgetSettings ?? null,
  };
  if (field.indexed) {
    normalized.indexed = true;
    // Only when it differs from the field's own name — the server resolves an absent one to that,
    // so writing it always would put a second copy of the name in every declaration.
    if (field.turingField && field.turingField !== field.name) {
      normalized.turingField = field.turingField;
    }
    if (field.turingType) normalized.turingType = field.turingType;
  }
  // SH1010 — the same exception, and for the same reason: a `constraints` block materialized with
  // ten nulls on every field would rewrite every `shio/post-types/*.json` in every repository on
  // the next pull, for a vocabulary almost none of them use. Absent means no constraints on both
  // sides, so an absent key is the one spelling of that.
  const constraints = normalizeConstraints(field.constraints);
  if (constraints) normalized.constraints = constraints;
  normalized.fields = (field.fields ?? []).slice().sort(byOrdinalThenName).map(normalizeField);
  return normalized;
}

/**
 * The constraint block in canonical key order, or `null` when it says nothing.
 *
 * Only the keys that are actually set, unlike the field itself: this block is optional as a whole,
 * so materializing its defaults would put ten keys on every field that has one and make two blocks
 * that declare the same rule differ by what they leave unsaid. The order is the server's
 * declaration order (`ShFieldConstraints`), which is what keeps a pulled file comparable to a
 * pushed one by string.
 */
function normalizeConstraints(constraints) {
  if (!constraints || typeof constraints !== "object") return null;
  const keys = ["minLength", "maxLength", "pattern", "patternMessage", "min", "max", "values",
    "minItems", "maxItems", "recommendedLength"];
  const normalized = {};
  for (const key of keys) {
    const value = constraints[key];
    if (value === undefined || value === null) continue;
    if (Array.isArray(value)) {
      if (value.length > 0) normalized[key] = value.slice();
      continue;
    }
    normalized[key] = value;
  }
  return Object.keys(normalized).length > 0 ? normalized : null;
}

/**
 * Normalize a def into a canonical, comparison-stable shape: fixed key order,
 * fields sorted by ordinal (then name), consistent defaults. Two defs are
 * "equal" iff their normalized JSON is identical.
 */
export function normalizeDef(def) {
  return {
    name: def.name ?? null,
    title: def.title ?? null,
    namePlural: def.namePlural ?? null,
    description: def.description ?? null,
    workflowPublishEntity: def.workflowPublishEntity ?? null,
    system: Boolean(def.system),
    fields: (def.fields ?? []).slice().sort(byOrdinalThenName).map(normalizeField),
  };
}

/** Deterministic, pretty JSON for a def — what `pull` writes to disk. */
export function serializeDef(def) {
  return `${JSON.stringify(normalizeDef(def), null, 2)}\n`;
}

/**
 * A def's on-disk filename.
 *
 * <h3>Why it can refuse (SH408)</h3>
 *
 * A post-type name is the server's, and it was interpolated straight into a path: `../evil` gave
 * `../evil.json`, which `path.join` resolves outside `shio/post-types/`. This is SH402's defect a
 * fourth time, and it stood because SH403's lint — built to find exactly this — enumerated
 * `content-files.mjs`, `content-assets.mjs` and `content-notes.mjs`, and this module was not on
 * the list. Widening that list is what found it, before any of it was written by hand.
 */
export function fileNameFor(def) {
  const name = String(def?.name ?? "");
  if (/[\\/]/.test(name) || /^\.+$/.test(name)) {
    throw new Error(
      `shio content: post-type name "${name}" cannot be a file name — a separator or a name that `
        + "is dots alone resolves outside `shio/post-types`. Rename the type on the server.",
    );
  }
  return `${name}.json`;
}

/**
 * Classify local defs against the remote set by name + normalized content.
 * @returns {{ added: string[], changed: string[], unchanged: string[], removed: string[] }}
 */
export function diffDefs(localDefs, remoteDefs) {
  const remoteByName = new Map(remoteDefs.map((d) => [d.name, d]));
  const localByName = new Map(localDefs.map((d) => [d.name, d]));
  const added = [];
  const changed = [];
  const unchanged = [];
  for (const local of localDefs) {
    const remote = remoteByName.get(local.name);
    if (!remote) {
      added.push(local.name);
    } else if (serializeDef(local) !== serializeDef(remote)) {
      changed.push(local.name);
    } else {
      unchanged.push(local.name);
    }
  }
  const removed = remoteDefs.filter((d) => !localByName.has(d.name)).map((d) => d.name);
  return { added: added.sort(), changed: changed.sort(), unchanged: unchanged.sort(), removed: removed.sort() };
}

/**
 * Build the concrete push plan from a diff: which defs to PUT and which remote
 * types to DELETE. `--prune` deletes remote types absent locally — but NEVER a
 * system type (those are read-only and the server would reject them anyway).
 *
 * @returns {{ toPut: object[], toDelete: string[] }}
 */
export function planPush(localDefs, remoteDefs, { prune = false } = {}) {
  const { added, changed, removed } = diffDefs(localDefs, remoteDefs);
  const localByName = new Map(localDefs.map((d) => [d.name, d]));
  const remoteByName = new Map(remoteDefs.map((d) => [d.name, d]));
  const { renames, ambiguous, probable } = detectTypeRenames(localDefs, remoteDefs, { added, removed });

  // A type that was renamed is not "added" (the server has it, under its old name) and its old
  // name is not "removed" (nothing was removed) — so both lists lose it. Without the second half,
  // `--prune` would delete the very type the rename preserved, taking its content with it.
  const renamedTo = new Set(renames.map((rename) => rename.to));
  const renamedFrom = new Set(renames.map((rename) => rename.from));

  const toPut = [...added, ...changed]
    .filter((name) => !renamedTo.has(name) || bodyChanged(localByName.get(name), remoteByName))
    .map((name) => localByName.get(name));
  const toDelete = prune
    ? removed.filter((name) => !renamedFrom.has(name) && !remoteByName.get(name)?.system)
    : [];
  return { toPut, toDelete, renames, ambiguousRenames: ambiguous, probableRenames: probable };
}

/**
 * Whether a renamed type still needs an upsert after the rename lands.
 *
 * A rename moves the row and changes nothing else, so a file that was *only* renamed converges
 * with the rename alone — and skipping the PUT is what keeps `--check` reporting no drift on the
 * next run. A file that was renamed *and* edited still needs its fields written.
 */
function bodyChanged(local, remoteByName) {
  const source = [...remoteByName.values()].find((remote) => sameBody(local, remote));
  return !source;
}

/** Two defs with everything but the name equal — identity by content (SH235's signal 2). */
function sameBody(a, b) {
  if (!a || !b) return false;
  const strip = (def) => JSON.stringify({ ...normalizeDef(def), name: null });
  return strip(a) === strip(b);
}

/**
 * Which pushes are post-type renames rather than create-plus-delete (SH235).
 *
 * <h3>The defect</h3>
 *
 * The tree is keyed by file name, so `git mv shio/post-types/Article.json Note.json` reads as
 * "create `Note`" plus, with `--prune`, "delete `Article`" — the create-and-orphan pair SH163
 * exists to remove, with a delete on the end aimed at a type that still holds every post ever
 * written against it. SH163 put the rename on the server; this is how `push` reaches it.
 *
 * <h3>Two signals, and refusal in between</h3>
 *
 * 1. **`movedFrom` in the local def** — explicit, and the only signal that survives a rename plus
 *    an edit in the same push, because after an edit there is no body left to match on. It is an
 *    *instruction*: `pull` never writes it, and it is ignored once the source is gone, which is
 *    what keeps a push re-runnable.
 * 2. **Identical body** — the plain `git mv`, needing nothing from the author: one added name
 *    whose def equals one removed name's, everything but `name` compared.
 *
 * Ambiguity is **refused**, not guessed: two candidates is a coin flip with a content model's
 * posts on the table. A pair that is plausible but unprovable — same field names, different
 * bodies — is reported as `probable` with the `movedFrom` line to add, because silence there is
 * how a delete-plus-create destroys history without mentioning it.
 *
 * @returns {{ renames: {from: string, to: string, explicit: boolean}[],
 *             ambiguous: {to: string, reason: string, candidates: string[]}[],
 *             probable: {from: string, to: string}[] }}
 */
export function detectTypeRenames(localDefs, remoteDefs, { added = [], removed = [] } = {}) {
  const localByName = new Map(localDefs.map((d) => [d.name, d]));
  const remoteByName = new Map(remoteDefs.map((d) => [d.name, d]));
  const renames = [];
  const ambiguous = [];
  const probable = [];
  const claimed = new Set();
  // A system type is not renamable on the server, so it is never a candidate here either.
  const gone = removed.filter((name) => !remoteByName.get(name)?.system);

  // 1. `movedFrom` — explicit and exact.
  for (const name of added) {
    const from = localByName.get(name)?.movedFrom;
    if (!from) continue;
    if (localByName.has(from)) {
      ambiguous.push({
        to: name,
        reason: `its \`movedFrom: ${from}\` names a post-type the tree still declares`,
        candidates: [from],
      });
      continue;
    }
    // The state *after* a successful rename: harmless, and ignoring it keeps the push idempotent.
    if (!remoteByName.has(from)) continue;
    if (remoteByName.get(from)?.system) {
      ambiguous.push({
        to: name,
        reason: `\`${from}\` is a system post-type and cannot be renamed`,
        candidates: [from],
      });
      continue;
    }
    claimed.add(from);
    renames.push({ from, to: name, explicit: true });
  }

  // 2. Identical body — the rename nobody had to declare.
  for (const name of added) {
    if (localByName.get(name)?.movedFrom) continue;
    const local = localByName.get(name);
    const matches = gone.filter((from) => !claimed.has(from) && sameBody(local, remoteByName.get(from)));
    if (matches.length > 1) {
      ambiguous.push({
        to: name,
        reason: "more than one post-type on the server has exactly this definition, so which one "
          + "was renamed cannot be told from the files",
        candidates: matches,
      });
      continue;
    }
    if (!matches.length) continue;
    const from = matches[0];
    const rivals = added.filter((other) => other !== name && !localByName.get(other)?.movedFrom
      && sameBody(localByName.get(other), remoteByName.get(from)));
    if (rivals.length) {
      ambiguous.push({
        to: name,
        reason: "another local definition is identical to the same server post-type, so which of "
          + `them is the rename of ${from} cannot be told apart`,
        candidates: [from, ...rivals],
      });
      continue;
    }
    claimed.add(from);
    renames.push({ from, to: name, explicit: false });
  }

  // What is left on each side and might be a pair: worth saying, not enough to act on.
  const unclaimed = gone.filter((name) => !claimed.has(name));
  for (const name of added) {
    if (localByName.get(name)?.movedFrom || renames.some((rename) => rename.to === name)) continue;
    const likely = unclaimed.filter((from) => sameFieldNames(localByName.get(name), remoteByName.get(from)));
    // Exactly one candidate, or nothing to say: two is a guess dressed as a warning.
    if (likely.length === 1) probable.push({ from: likely[0], to: name });
  }

  return {
    renames: renames.sort((a, b) => a.to.localeCompare(b.to)),
    ambiguous: ambiguous.sort((a, b) => a.to.localeCompare(b.to)),
    probable: probable.sort((a, b) => a.to.localeCompare(b.to)),
  };
}

/** Same field names, in any order — a rename plus an edit looks like this. */
function sameFieldNames(a, b) {
  if (!a || !b) return false;
  const names = (def) => (def.fields ?? []).map((field) => field.name).sort().join("\u0000");
  const own = names(a);
  return own.length > 0 && own === names(b);
}
