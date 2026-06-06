/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Runs against the built bundle.
 *   npm run build && npm test
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { generateShioTypes } from "../dist/shio-client.js";

/** Minimal field-schema factory. */
function field(name, overrides = {}) {
  return {
    name,
    label: null,
    description: null,
    widget: "Text",
    required: false,
    multiple: false,
    isTitle: false,
    isSummary: false,
    ordinal: 0,
    fields: [],
    ...overrides,
  };
}

test("emits a banner, the ShioPost import, and the post-type map", () => {
  const out = generateShioTypes([
    { name: "News", title: "News", namePlural: "News", description: null, fields: [] },
  ]);
  assert.match(out, /AUTO-GENERATED/);
  assert.match(out, /import type \{ ShioPost \} from "@viglet\/shio-client";/);
  assert.match(out, /export interface NewsAttrs \{/);
  assert.match(out, /export type NewsPost = ShioPost & \{/);
  assert.match(out, /postType: "News";/);
  assert.match(out, /attrs: NewsAttrs;/);
  assert.match(out, /export interface ShioPostTypes \{/);
  assert.match(out, /News: NewsPost;/);
  assert.match(out, /export type ShioPostTypeName = keyof ShioPostTypes;/);
});

test("maps widgets to the right TS types", () => {
  const out = generateShioTypes([
    {
      name: "Article",
      title: null,
      namePlural: null,
      description: null,
      fields: [
        field("title", { widget: "Text", required: true, ordinal: 0 }),
        field("tags", { widget: "Multi Select", multiple: true, ordinal: 1 }),
        field("flags", { widget: "Check Box", multiple: true, ordinal: 2 }),
        field("body", { widget: "HTML Editor", ordinal: 3 }),
      ],
    },
  ]);
  assert.match(out, /title: string;/, "required scalar is non-optional string");
  assert.match(out, /tags\?: string\[\];/, "Multi Select → string[]");
  assert.match(out, /flags\?: string\[\];/, "Check Box → string[]");
  assert.match(out, /body\?: string;/, "optional scalar gets ?");
});

test("relator fields become arrays of inlined object types", () => {
  const out = generateShioTypes([
    {
      name: "Page",
      title: null,
      namePlural: null,
      description: null,
      fields: [
        field("blocks", {
          widget: "Relator",
          multiple: true,
          ordinal: 0,
          fields: [
            field("heading", { widget: "Text", required: true, ordinal: 0 }),
            field("link", { widget: "Text", ordinal: 1 }),
          ],
        }),
      ],
    },
  ]);
  assert.match(out, /blocks\?: Array<\{/);
  assert.match(out, /heading: string;/);
  assert.match(out, /link\?: string;/);
});

test("quotes non-identifier field and post-type names; sorts deterministically", () => {
  const a = generateShioTypes([
    { name: "Zeta", title: null, namePlural: null, description: null, fields: [] },
    { name: "Alpha", title: null, namePlural: null, description: null,
      fields: [field("weird-name", { ordinal: 0 })] },
  ]);
  // Alpha sorts before Zeta regardless of input order.
  assert.ok(a.indexOf("AlphaAttrs") < a.indexOf("ZetaAttrs"));
  assert.match(a, /"weird-name"\?: string;/);
});

test("honours importFrom for in-tree generation", () => {
  const out = generateShioTypes(
    [{ name: "News", title: null, namePlural: null, description: null, fields: [] }],
    { importFrom: "../client" }
  );
  assert.match(out, /from "\.\.\/client";/);
});

test("renders field labels as JSDoc", () => {
  const out = generateShioTypes([
    {
      name: "News",
      title: null,
      namePlural: null,
      description: null,
      fields: [field("title", { label: "Headline", ordinal: 0 })],
    },
  ]);
  assert.match(out, /\/\*\* Headline \*\//);
});
