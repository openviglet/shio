/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

/** Normalize one field into a canonical-key-order object with stable defaults. */
function normalizeField(field) {
  return {
    name: field.name ?? null,
    label: field.label ?? null,
    description: field.description ?? null,
    widget: field.widget ?? null,
    required: Boolean(field.required),
    isTitle: Boolean(field.isTitle),
    isSummary: Boolean(field.isSummary),
    ordinal: field.ordinal ?? 0,
    widgetSettings: field.widgetSettings ?? null,
    fields: (field.fields ?? []).slice().sort(byOrdinalThenName).map(normalizeField),
  };
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

/** A def's on-disk filename. */
export function fileNameFor(def) {
  return `${def.name}.json`;
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
  const toPut = [...added, ...changed].map((name) => localByName.get(name));
  const toDelete = prune ? removed.filter((name) => !remoteByName.get(name)?.system) : [];
  return { toPut, toDelete };
}
