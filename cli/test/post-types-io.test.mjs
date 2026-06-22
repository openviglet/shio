/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Pure helpers — no build/network needed.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { diffDefs, normalizeDef, planPush, serializeDef } from "../src/post-types-io.mjs";

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
