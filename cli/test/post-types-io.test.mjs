/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Pure helpers — no build/network needed.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { detectTypeRenames, diffDefs, normalizeDef, planPush, serializeDef } from "../src/post-types-io.mjs";

const news = {
  name: "News",
  title: "News",
  namePlural: "News",
  description: null,
  workflowPublishEntity: null,
  system: false,
  fields: [
    { name: "title", widget: "Text", ordinal: 0, isTitle: true },
    { name: "body", widget: "HTML Editor", ordinal: 1 },
  ],
};

test("serializeDef is deterministic regardless of key/field order", () => {
  const reordered = {
    fields: [
      { ordinal: 1, name: "body", widget: "HTML Editor" },
      { isTitle: true, ordinal: 0, name: "title", widget: "Text" },
    ],
    system: false,
    name: "News",
    title: "News",
    namePlural: "News",
  };
  assert.equal(serializeDef(news), serializeDef(reordered));
  assert.match(serializeDef(news), /\n$/); // trailing newline
});

test("normalizeDef fills defaults and sorts fields by ordinal", () => {
  const n = normalizeDef({ name: "X", fields: [{ name: "b", ordinal: 2 }, { name: "a", ordinal: 1 }] });
  assert.deepEqual(n.fields.map((f) => f.name), ["a", "b"]);
  assert.equal(n.system, false);
  assert.equal(n.fields[0].required, false);
  assert.equal(n.fields[0].widgetSettings, null);
});

test("diffDefs classifies added / changed / unchanged / removed", () => {
  const local = [news, { name: "Blog", fields: [] }];
  const changedNews = { ...news, title: "Headlines" };
  const remote = [changedNews, { name: "Legacy", fields: [] }];

  const d = diffDefs(local, remote);
  assert.deepEqual(d.added, ["Blog"]);
  assert.deepEqual(d.changed, ["News"]);
  assert.deepEqual(d.unchanged, []);
  assert.deepEqual(d.removed, ["Legacy"]);
});

test("managed flag (SH66) is server-only and never registers as drift", () => {
  // The server stamps `managed: true` on code-owned types; a hand-authored local
  // def omits it. normalizeDef excludes the field, so the two stay equal.
  const remote = { ...news, managed: true };
  assert.equal(serializeDef(news), serializeDef(remote));
  assert.equal(normalizeDef(remote).managed, undefined);

  const d = diffDefs([news], [remote]);
  assert.deepEqual(d.unchanged, ["News"]);
  assert.deepEqual(d.changed, []);
});

test("planPush PUTs added+changed and only prunes non-system removals", () => {
  const local = [news];
  const remote = [
    { name: "Legacy", system: false, fields: [] },
    { name: "Folder", system: true, fields: [] }, // system → never pruned
  ];

  const noPrune = planPush(local, remote);
  assert.deepEqual(noPrune.toPut.map((d) => d.name), ["News"]);
  assert.deepEqual(noPrune.toDelete, []);

  const pruned = planPush(local, remote, { prune: true });
  assert.deepEqual(pruned.toDelete, ["Legacy"]); // "Folder" (system) excluded
});

// ── SH235: a renamed file is a rename, not a create plus a delete ────────────────────────

/** The same body under two names — what `git mv shio/post-types/X.json Y.json` produces. */
function bodied(name, extra = {}) {
  return { name, title: "Story", namePlural: "Stories", description: null, system: false,
    fields: [{ name: "headline", label: "Headline", widget: "Text", ordinal: 1 }], ...extra };
}

test("an identical body under a new name is a rename, and --prune does not delete the old one", () => {
  const plan = planPush([bodied("Note")], [bodied("Article")], { prune: true });

  assert.deepEqual(plan.renames, [{ from: "Article", to: "Note", explicit: false }]);
  // The half that matters: without it, --prune deletes the type the rename just preserved,
  // and it holds every post ever written against it.
  assert.deepEqual(plan.toDelete, []);
  // ...and no upsert, because a rename alone converges — which is what keeps --check quiet
  // on the next run.
  assert.deepEqual(plan.toPut, []);
});

test("movedFrom survives a rename plus an edit, which no content match can", () => {
  const edited = bodied("Note", { movedFrom: "Article" });
  edited.fields = [...edited.fields, { name: "standfirst", widget: "Text", ordinal: 2 }];

  const plan = planPush([edited], [bodied("Article")], { prune: true });

  assert.deepEqual(plan.renames, [{ from: "Article", to: "Note", explicit: true }]);
  assert.deepEqual(plan.toDelete, []);
  // The body changed too, so the rename is followed by an upsert.
  assert.deepEqual(plan.toPut.map((d) => d.name), ["Note"]);
});

test("a movedFrom whose source is gone is ignored, so a push is re-runnable", () => {
  // The state *after* a successful rename: the server already has `Note`.
  const plan = planPush([bodied("Note", { movedFrom: "Article" })], [bodied("Note")], { prune: true });

  assert.deepEqual(plan.renames, []);
  assert.deepEqual(plan.toPut, []);
  assert.deepEqual(plan.toDelete, []);
});

test("ambiguity is refused rather than guessed", () => {
  const twoCandidates = detectTypeRenames([bodied("Note")],
    [bodied("Article"), bodied("Story")],
    { added: ["Note"], removed: ["Article", "Story"] });
  assert.deepEqual(twoCandidates.renames, []);
  assert.equal(twoCandidates.ambiguous.length, 1);
  assert.deepEqual(twoCandidates.ambiguous[0].candidates, ["Article", "Story"]);

  // Two local files identical to the same server type: both are unresolvable, and both are
  // named — the reader has to know which files to fix, not just that a conflict exists.
  const twoLocals = detectTypeRenames([bodied("Note"), bodied("Memo")], [bodied("Article")],
    { added: ["Memo", "Note"], removed: ["Article"] });
  assert.deepEqual(twoLocals.renames, []);
  assert.deepEqual(twoLocals.ambiguous.map((item) => item.to), ["Memo", "Note"]);

  // A movedFrom naming a type the tree still declares: one of the two files is wrong.
  const stillDeclared = detectTypeRenames([bodied("Note", { movedFrom: "Article" }), bodied("Article")],
    [bodied("Article")], { added: ["Note"], removed: [] });
  assert.deepEqual(stillDeclared.renames, []);
  assert.match(stillDeclared.ambiguous[0].reason, /still declares/);
});

test("a plausible but unprovable pair is reported, never acted on", () => {
  const renamed = bodied("Note");
  renamed.fields = [{ name: "headline", label: "Different label", widget: "Text", ordinal: 1 }];

  const result = detectTypeRenames([renamed], [bodied("Article")],
    { added: ["Note"], removed: ["Article"] });

  assert.deepEqual(result.renames, []);
  assert.deepEqual(result.probable, [{ from: "Article", to: "Note" }]);
});

test("a system post-type is never a rename candidate", () => {
  const system = { ...bodied("Folder"), system: true };
  const result = detectTypeRenames([bodied("Note")], [system],
    { added: ["Note"], removed: ["Folder"] });

  assert.deepEqual(result.renames, []);
  assert.deepEqual(result.probable, []);
});

/*
 * SH914 — a post-type field can declare the Turing field it indexes into.
 *
 * The keys are the one exception to this file's "materialize every key with its default" rule, and
 * the reason is the one P5 cares about: writing `indexed: false` on every field would rewrite every
 * `shio/post-types/*.json` in every repository on the next pull, producing a diff that touches
 * every field of every type for a feature none of them use.
 */
test("SH914: a field that declares nothing serializes exactly as it did before", () => {
  const before = serializeDef(news);
  assert.ok(!before.includes("indexed"), "SH914: an undeclared field must not gain a key");
  assert.ok(!before.includes("turingType"));

  // Explicit falsehood is the same as absence, so a def that spells it out still round-trips clean.
  const spelled = structuredClone(news);
  spelled.fields[0].indexed = false;
  spelled.fields[0].turingType = null;
  assert.equal(serializeDef(spelled), before,
    "SH914: `indexed: false` and absent mean the same thing, so the bytes must too");
});

test("SH914: a declared field carries its type, and its name only when it differs", () => {
  const school = structuredClone(news);
  school.fields.push({ name: "tuition", widget: "Text", ordinal: 2, indexed: true, turingType: "DOUBLE" });

  const field = normalizeDef(school).fields.find((f) => f.name === "tuition");
  assert.equal(field.indexed, true);
  assert.equal(field.turingType, "DOUBLE");
  assert.ok(!("turingField" in field),
    "SH914: the server resolves an absent turingField to the field's own name, so writing it "
      + "always would put a second copy of the name in every declaration");

  school.fields[2].turingField = "school_tuition";
  assert.equal(normalizeDef(school).fields.find((f) => f.name === "tuition").turingField,
    "school_tuition");
});

test("SH914: a declaration is drift when it changes, and not otherwise", () => {
  const declared = structuredClone(news);
  declared.fields[1].indexed = true;
  declared.fields[1].turingType = "TEXT";

  assert.notEqual(serializeDef(declared), serializeDef(news),
    "SH914: adding a declaration has to show up as a change, or a pull would hide it");
  assert.equal(serializeDef(declared), serializeDef(structuredClone(declared)),
    "SH914: and an unchanged declaration must be byte-identical (P5)");
});

test("SH1010: a field that declares no constraints serializes exactly as it did before", () => {
  const before = serializeDef(news);
  assert.ok(!before.includes("constraints"),
    "SH1010: a block materialized with ten nulls on every field would rewrite every "
      + "shio/post-types/*.json in every repository on the next pull, for a vocabulary almost "
      + "none of them use");

  // An empty block and an absent one are the same fact, so they have to be the same bytes.
  const spelled = structuredClone(news);
  spelled.fields[0].constraints = {};
  assert.equal(serializeDef(spelled), before);

  const nulled = structuredClone(news);
  nulled.fields[0].constraints = { minLength: null, pattern: null, values: [] };
  assert.equal(serializeDef(nulled), before,
    "SH1010: and a block whose every key is unset says nothing, which is what absent means");
});

test("SH1010: a declared constraint is carried, in the server's own key order", () => {
  const slugged = structuredClone(news);
  slugged.fields[0].constraints = {
    maxLength: 40,
    pattern: "[a-z0-9-]+",
    patternMessage: "lower-case letters, digits and hyphens only",
    values: ["a", "b"],
  };

  const field = normalizeDef(slugged).fields.find((f) => f.name === news.fields[0].name);
  assert.deepEqual(Object.keys(field.constraints),
    ["maxLength", "pattern", "patternMessage", "values"],
    "SH1010: the order is ShFieldConstraints' declaration order, which is what keeps a pulled "
      + "file comparable to a pushed one by string");
  assert.deepEqual(field.constraints.values, ["a", "b"]);

  assert.notEqual(serializeDef(slugged), serializeDef(news),
    "SH1010: adding a constraint has to show up as a change, or a pull would hide it");
});
