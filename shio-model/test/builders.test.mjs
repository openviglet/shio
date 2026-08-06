/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH63 acceptance tests (node:test). These import the BUILT output (dist/) — run
 * `npm run build` first. Coverage mirrors SH62 §9:
 *   - widget-by-widget widgetSettings codec round-trip (encode ↔ decode),
 *   - `postType`/relator ordinal assignment, relator + tab nesting,
 *   - byte-identical serialization vs the hand-checked golden fixture (§8.1).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

import {
  postType,
  text,
  comboBox,
  file,
  relator,
  tab,
  serializeDef,
  toDef,
  encodeChoices,
  decodeChoices,
  encodeFile,
  decodeFile,
  encodeContentSelect,
  decodeContentSelect,
  encodeMultiSelect,
  decodeMultiSelect,
  encodeRelator,
  decodeRelator,
} from "../dist/shio-model.js";
import { news } from "./_news.fixture.mjs";

const read = (rel) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), "utf8");

/* ----------------------------- ordinals --------------------------------- */

test("postType auto-assigns 1-based ordinals by position", () => {
  const def = postType("News", {
    fields: [text("title", { isTitle: true }), text("summary")],
  });
  assert.equal(def.fields[0].ordinal, 1);
  assert.equal(def.fields[1].ordinal, 2);
  assert.equal(def.system, false);
});

test("explicit ordinal is preserved; unset (0) is reassigned by position", () => {
  const def = postType("X", {
    fields: [text("a"), text("b", { ordinal: 99 }), text("c")],
  });
  assert.deepEqual(def.fields.map((f) => f.ordinal), [1, 99, 3]);
});

/* ------------------------- widget codec round-trips --------------------- */

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

test("choices codec round-trips (encode ↔ decode)", () => {
  const settings = { choices: { no: "No", yes: "Yes" }, allowNull: false };
  assert.deepEqual(decodeChoices(encodeChoices(settings)), settings);
});

test("file codec round-trips", () => {
  const s = { folderId: "f-1" };
  assert.equal(encodeFile(s), '{"file":{"folderId":"f-1"}}');
  assert.deepEqual(decodeFile(encodeFile(s)), s);
});

test("contentSelect codec round-trips", () => {
  const s = { folderId: "f-2" };
  assert.equal(encodeContentSelect(s), '{"contentSelect":{"folderId":"f-2"}}');
  assert.deepEqual(decodeContentSelect(encodeContentSelect(s)), s);
});

test("multiSelect codec round-trips", () => {
  const s = { postType: "Tag", folderId: "f-3", references: undefined };
  assert.equal(encodeMultiSelect(s), '{"multiselect":{"postType":"Tag","folderId":"f-3"}}');
  assert.deepEqual(decodeMultiSelect(encodeMultiSelect(s)), s);
});

/**
 * SH154/SH169 — `references: "post"` is what tells the server a Multi Select holds friendly URLs
 * and not tags. Without it the lint cannot see a page naming a section that does not exist, and
 * SH101's digest reads a section-composed page as the hash of nothing.
 */
test("multiSelect can declare that its values are post references", () => {
  const s = { postType: undefined, folderId: undefined, references: "post" };
  assert.equal(encodeMultiSelect(s), '{"multiselect":{"references":"post"}}');
  assert.deepEqual(decodeMultiSelect(encodeMultiSelect(s)), s);
});

test("an unrecognised references value decodes as absent", () => {
  // Narrowed rather than cast: a reader that only knows "post" must not be handed something else
  // and act on it.
  assert.equal(
    decodeMultiSelect('{"multiselect":{"references":"folder"}}').references,
    undefined,
  );
});

test("relator title codec round-trips", () => {
  assert.equal(encodeRelator("Authors"), '{"relator":{"title":"Authors"}}');
  assert.deepEqual(decodeRelator(encodeRelator("Authors")), { title: "Authors" });
});

test("empty settings encode to null (never the string \"null\")", () => {
  assert.equal(encodeFile({}), null);
  assert.equal(encodeContentSelect({}), null);
  assert.equal(encodeMultiSelect({}), null);
  assert.equal(encodeRelator(undefined), null);
  // a settings-free widget carries no widgetSettings
  assert.equal(file("attachment").widgetSettings, null);
});

test("decoders tolerate null / garbage settings", () => {
  assert.deepEqual(decodeFile(null), { folderId: undefined });
  assert.deepEqual(decodeMultiSelect("null"),
    { postType: undefined, folderId: undefined, references: undefined });
  assert.deepEqual(decodeRelator("{bad json"), { title: undefined });
});

/* ------------------------------ nesting --------------------------------- */

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

test("tab is a data-free container", () => {
  const t = tab("meta", { label: "Metadata" });
  assert.equal(t.widget, "Tab");
  assert.equal(t.widgetSettings, null);
  assert.deepEqual(t.fields, []);
});

/* --------------------------- serialization ------------------------------ */

test("serializeDef is deterministic + trailing newline (canonical JSON)", () => {
  const a = serializeDef(postType("X", { fields: [text("b"), text("a")] }));
  const b = serializeDef(postType("X", { fields: [text("b"), text("a")] }));
  assert.equal(a, b);
  assert.ok(a.endsWith("\n"));
});

test("toDef returns the canonical plain def (matches serializeDef)", () => {
  const def = postType("X", { fields: [text("a")] });
  assert.equal(serializeDef(toDef(def)), serializeDef(def));
  assert.equal(toDef(def).system, false);
});

test("byte-identical to the hand-checked golden JSON (§8.1)", () => {
  assert.equal(serializeDef(news), read("./news.golden.json"));
});
