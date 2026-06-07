/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Skeleton tests (SH63). Run with `npm test` (node:test). These import the
 * BUILT output (dist/) — run `npm run build` first, or wire ts loading like the
 * other JS packages. The acceptance anchor (SH62 §8.1) is the byte-identical
 * round-trip vs a hand-written fixture JSON.
 */
import { test } from "node:test";
import assert from "node:assert/strict";

import {
  postType,
  text,
  comboBox,
  relator,
  serializeDef,
  encodeChoices,
  decodeChoices,
} from "../dist/shio-model.js";

test("postType auto-assigns 1-based ordinals by position", () => {
  const def = postType("News", {
    fields: [text("title", { isTitle: true }), text("summary")],
  });
  assert.equal(def.fields[0].ordinal, 1);
  assert.equal(def.fields[1].ordinal, 2);
  assert.equal(def.system, false);
});

test("comboBox encodes choices object to the legacy wire string", () => {
  const field = comboBox("category", {
    choices: { tech: "Technology", biz: "Business" },
    allowNull: true,
  });
  assert.equal(
    field.widgetSettings,
    JSON.stringify({ choices: "tech: Technology\nbiz: Business", allowNull: 1 })
  );
});

test("choices codec round-trips", () => {
  const settings = { choices: { no: "No", yes: "Yes" }, allowNull: false };
  assert.deepEqual(decodeChoices(encodeChoices(settings)), settings);
});

test("relator nests + ordinals its children", () => {
  const def = postType("Article", {
    fields: [relator("authors", { title: "Authors", fields: [text("name"), text("email")] })],
  });
  const rel = def.fields[0];
  assert.equal(rel.widget, "Relator");
  assert.equal(rel.widgetSettings, JSON.stringify({ relator: { title: "Authors" } }));
  assert.equal(rel.fields[0].ordinal, 1);
  assert.equal(rel.fields[1].ordinal, 2);
});

test("serializeDef is deterministic + trailing newline (canonical JSON)", () => {
  const a = serializeDef(postType("X", { fields: [text("b"), text("a")] }));
  const b = serializeDef(postType("X", { fields: [text("b"), text("a")] }));
  assert.equal(a, b);
  assert.ok(a.endsWith("\n"));
});
