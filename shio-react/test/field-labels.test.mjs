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

import { labelSource, labelSourceKey, labelsFrom }
  from "../src/app/console/universal-editor/field-labels.ts";

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
  const source = labelSource({ postId: "row-2", postType: "Page" }, true);

  assert.deepEqual(source, { kind: "post", postId: "row-2" },
    "SH305: the post's own type is the only source that is right for a row of another type - "
    + "GET /api/v2/cda/post/{id} answers with that post's declared fields");
});

test("without a WRITE token it falls back to the page's type, which is SH303's behaviour", () => {
  const source = labelSource({ postId: "row-2", postType: "Page" }, false);

  assert.deepEqual(source, { kind: "post-type", postType: "Page" },
    "the CDA read needs the token the editor already requires to save, so no token means the "
    + "fallback - degraded labels beat a blank panel, and the editor could not have saved anyway");
});

test("nothing to load is a state, not a fetch", () => {
  assert.deepEqual(labelSource({ postId: "row-2" }, false), { kind: "none" },
    "no token and no page type: there is nothing to ask");
  assert.deepEqual(labelSource(null, true), { kind: "none" });
  assert.deepEqual(labelSource({ postId: "   ", postType: "  " }, true), { kind: "none" },
    "blank is absent - a fetch for `/post/   ` is a 404 the panel would report as no labels");
});

test("the cache key distinguishes the two sources, so a row does not reuse the page's labels", () => {
  const post = labelSourceKey(labelSource({ postId: "row-2", postType: "Page" }, true));
  const type = labelSourceKey(labelSource({ postId: "row-2", postType: "Page" }, false));

  assert.equal(post, "post:row-2");
  assert.equal(type, "type:Page");
  assert.notEqual(post, type,
    "keyed by name alone, selecting a row after a page field would have skipped the fetch - which "
    + "is the defect, cached");
  assert.equal(labelSourceKey(labelSource({ postId: "row-2" }, true)), "post:row-2",
    "and selecting a second field of the same post refetches nothing");
});
