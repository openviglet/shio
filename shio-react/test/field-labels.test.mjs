/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH305 — the panel labels a row from the row's own type, and the decision is testable.
 *
 * The defect was invisible because it was right about the common case: SH303 loads the *page's*
 * post-type, which is correct for a layout interpolating the page's own fields and wrong for every
 * row of a `{{#query}}` or `{{#getRelation}}` listing — and a name the page's schema lacks falls back
 * to the field name, which is indistinguishable from a type that declares no labels.
 *
 * The effect that fetches cannot be tested here (no React harness in this workspace, and SH306's
 * jsdom is for the page-side bridge, not for components). The *decision* and the *mapping* can, so
 * they live in a module of their own rather than inside a hook — which is also what makes the
 * fallback's rules something you can read.
 */

import assert from "node:assert/strict";
import { test } from "node:test";

import {
  labelForProp, labelSource, labelSourceKey, labelsFrom, panelEntries, relatorLabels, relatorPath,
} from "../src/app/editor/field-labels.ts";

/** Quilhara's Trail, as the post-type API declares it: the shape the panel was mislabelling. */
const TRAIL = [
  { name: "name", label: "Name", widget: "Text" },
  { name: "summary", label: "Summary", widget: "Text Area" },
  {
    name: "facts", label: "Facts", widget: "Relator", widgetSettings: "{\"relator\":{\"title\":\"Fact\"}}",
    fields: [{ name: "label", label: "Label" }, { name: "value", label: "Value" }],
  },
];

test("SH1417: a Relator's leaf is labelled from the Relator, not shown as its path", () => {
  assert.deepEqual(relatorPath("facts.0.label"), { relator: "facts", index: 0, child: "label" });
  assert.equal(relatorPath("summary"), null);
  assert.equal(relatorPath("facts.label"), null, "no index, no item");

  assert.equal(labelForProp(TRAIL, "facts.1.value"), "Facts · Fact 2 · Value",
    "the panel showed `facts.1.value` here, which names nothing a curator can see on the page");
  assert.equal(labelForProp(TRAIL, "summary"), "Summary");
  assert.equal(labelForProp(TRAIL, "body.0.text"), "",
    "a head no Relator declares keeps the caller's fallback instead of a guessed title");
});

test("SH1417: the item title falls back to the Relator's label, and a 'null' setting does not crash", () => {
  const bare = [{ name: "links", label: "Links", widget: "Relator", widgetSettings: "null", fields: [] }];
  assert.equal(relatorLabels(bare, "links")?.itemTitle, "Links");
  assert.equal(relatorLabels(TRAIL, "summary"), null, "a field that is not a Relator has no items");
});

test("SH1417: the flat field list becomes one group per Relator, items in index order", () => {
  const fields = [
    { prop: "name" },
    { prop: "facts.1.label" }, { prop: "facts.1.value" },
    { prop: "facts.0.label" }, { prop: "facts.0.value" },
    { prop: "summary" },
    { prop: "body.0.text" },
  ];
  const entries = panelEntries(fields, TRAIL);

  assert.deepEqual(entries.map((e) => e.kind === "field" ? e.field.prop : `group:${e.relator}`),
    ["name", "group:facts", "summary", "body.0.text"],
    "four entries, not seven boxes: the group sits where its first leaf was");
  const group = entries[1];
  assert.deepEqual(group.items.map((item) => [item.index, item.fields.map((f) => f.child)]),
    [[0, ["label", "value"]], [1, ["label", "value"]]]);
  assert.deepEqual(group.labels.children, { label: "Label", value: "Value" });
});

test("labels come from declared fields, and a blank label is absent rather than empty", () => {
  const labels = labelsFrom([
    { name: "TITLE", label: "Headline" },
    { name: "TEXT", label: "  " },
    { name: "  ", label: "Nameless" },
    { name: "SUMMARY", label: "  Summary  " },
    null,
  ]);

  assert.deepEqual(labels, { TITLE: "Headline", SUMMARY: "Summary" },
    "an empty string would render as a missing label; absent lets the panel fall back to the name");
  assert.deepEqual(labelsFrom(undefined), {}, "no fields is not an error");
});

test("a selected post is read from the post, not the page's type", () => {
  const source = labelSource({ postId: "row-2", postType: "Page" });

  assert.deepEqual(source, { kind: "post", postId: "row-2" },
    "SH305: the post's own type is the only source that is right for a row of another type");
});

test("SH335: the post is the source for everyone, not only for whoever minted a token", () => {
  // The old signature took `hasToken`, so the right source was chosen for a curator who had made a
  // WRITE token and silently not for anyone else - labels degraded by credential rather than by
  // what the selection actually said. The console reads its own post on the session.
  assert.deepEqual(labelSource({ postId: "row-2", postType: "Page" }),
    { kind: "post", postId: "row-2" });
});

test("with no post named at all it falls back to the page's type, which is SH303's behaviour", () => {
  const source = labelSource({ postId: "  ", postType: "Page" });

  assert.deepEqual(source, { kind: "post-type", postType: "Page" },
    "degraded labels beat a blank panel");
});

test("nothing to load is a state, not a fetch", () => {
  assert.deepEqual(labelSource({}), { kind: "none" },
    "no post and no page type: there is nothing to ask");
  assert.deepEqual(labelSource(null), { kind: "none" });
  assert.deepEqual(labelSource({ postId: "   ", postType: "  " }), { kind: "none" },
    "blank is absent - a fetch for `/post/   ` is a 404 the panel would report as no labels");
});

test("the cache key distinguishes the two sources, so a row does not reuse the page's labels", () => {
  const post = labelSourceKey(labelSource({ postId: "row-2", postType: "Page" }));
  const type = labelSourceKey(labelSource({ postId: "  ", postType: "Page" }));

  assert.equal(post, "post:row-2");
  assert.equal(type, "type:Page");
  assert.notEqual(post, type,
    "keyed by name alone, selecting a row after a page field would have skipped the fetch - which "
    + "is the defect, cached");
  assert.equal(labelSourceKey(labelSource({ postId: "row-2" })), "post:row-2",
    "and selecting a second field of the same post refetches nothing");
});
