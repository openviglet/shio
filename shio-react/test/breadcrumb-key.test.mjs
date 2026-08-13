/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH716 — a breadcrumb whose keys repeat, on the most ordinary page this console has.
 *
 * ## The run that found it
 *
 *     FAIL post-edit:File  /console/content/post/490bd18c-…
 *       error: Encountered two children with the same key, `%s`.
 *
 * `page-header.tsx` keyed each crumb on `item.label`. `post.edit.page.tsx` pushes the site name and
 * then the folder name, and for a post in a site's **root** folder those are the same string. That
 * is the default shape of a new site, so the console's own fixture reproduced it: site `teste`,
 * root folder `teste`.
 *
 * ## What is asserted, and what is not
 *
 * The decision, not the format. `crumbKey` may return anything it likes as long as a list a curator
 * can actually produce comes out unique — so the cases below are real lists, and the one that
 * matters is the exact list the failing page built. A test that asserted the string `"#2"` would
 * pass on a rule that helped nobody and fail on a better one.
 *
 * The rendering is **not** asserted: that `page-header.tsx` calls this rather than keying on the
 * label again needs a renderer, and this workspace has none. What stands there instead is
 * `console:check`, which is what caught the defect in the first place — it fails on any React
 * console error, so a return to `key={item.label}` fails that probe again.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { crumbKey } from "../src/lib/breadcrumb-key.ts";

/** Keys for a whole list, which is the only scope uniqueness means anything in. */
const keysOf = (items) => items.map((item, index) => crumbKey(item, index));

test("SH716: the list the failing page built comes out unique", () => {
  // Verbatim from post.edit.page.tsx for a post in a site's root folder, with the site and the
  // folder sharing a name — which is what a root folder is.
  const items = [
    { label: "Sites", href: "/console/content" },
    { label: "teste", href: "/console/content/object/4ed6d98a-bc54-46d8-8e33-b03d4a383ea2" },
    { label: "teste", href: "/console/content/object/543e4561-e7c6-47b3-bfea-4ffa08f87ab3" },
    { label: "index.html" },
  ];

  const keys = keysOf(items);

  assert.equal(new Set(keys).size, keys.length,
    "SH716: two crumbs share a key on the page this defect was found on. A React key is an "
    + "identity claim, so two children under one key let the reconciler keep one crumb's state on "
    + "the other's props. Keying on the label made a curator's choice of folder name load-bearing, "
    + "and naming a folder after its site is not a mistake — it is what a root folder is.");
});

test("SH716: two crumbs with the same label and different targets are different crumbs", () => {
  const keys = keysOf([
    { label: "Docs", href: "/console/content/object/a" },
    { label: "Docs", href: "/console/content/object/b" },
  ]);

  assert.notEqual(keys[0], keys[1],
    "SH716: two folders may share a name; they cannot share an address. The address is what "
    + "identity has to come from.");
});

test("SH716: the terminal crumb has no target, and position is enough for it", () => {
  // There is exactly one crumb without an href — the page you are on — so its index identifies it.
  const keys = keysOf([
    { label: "Sites", href: "/console/content" },
    { label: "Sites" },
  ]);

  assert.notEqual(keys[0], keys[1],
    "SH716: a crumb with no href must not collide with one that has the same label and a target.");
});

test("SH716: the old rule fails these, which is why they are worth running", () => {
  // The reader's own guard (SH701's contract, applied here): a uniqueness assertion passes trivially
  // on any list of distinct labels, so it is only evidence if the superseded rule fails it.
  const items = [
    { label: "teste", href: "/console/content/object/site" },
    { label: "teste", href: "/console/content/object/folder" },
  ];
  const byLabel = items.map((item) => item.label);

  assert.equal(new Set(byLabel).size, 1,
    "SH716: keying on the label collapses these two crumbs to one key — the defect, reproduced "
    + "here so the assertions above are known to be about something.");
  assert.equal(new Set(keysOf(items)).size, 2);
});
