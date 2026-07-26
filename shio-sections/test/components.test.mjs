/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH95 — the invariant that makes the vocabulary shared rather than merely defined:
 * every section post-type has a component that renders it.
 *
 * This one reads the source rather than importing it, deliberately. `components.tsx` is
 * JSX, which Node's type stripping does not handle, so importing it would mean building the
 * package first — turning the cheapest guard in the workspace into its slowest test, for an
 * assertion about a lookup table. The typed half of the same guarantee is `tsc --noEmit`
 * (`pnpm -C shio-sections run typecheck`), which the build runs anyway.
 */
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import { SECTION_TYPES } from "../src/post-types.ts";

const source = await readFile(
  fileURLToPath(new URL("../src/components.tsx", import.meta.url)),
  "utf8",
);

test("SECTION_COMPONENTS has an entry for every section in the vocabulary", () => {
  const registry = source.slice(source.indexOf("export const SECTION_COMPONENTS"));
  assert.ok(registry.length > 0, "the registry is where dispatch happens; it must exist");

  for (const key of Object.keys(SECTION_TYPES)) {
    assert.match(
      registry,
      new RegExp(`SECTION_TYPES\\.${key}\\b`),
      `${SECTION_TYPES[key]} has no component — a page using it would render nothing`,
    );
  }
});

test("every section component is exported by name as well as through the registry", () => {
  // A site that wants one section on a hand-built page must be able to import it directly;
  // a vocabulary reachable only through the dispatcher is a framework, not a library.
  for (const component of [
    "Hero",
    "FeatureGrid",
    "Cta",
    "LogoWall",
    "Testimonial",
    "Faq",
    "RichText",
  ]) {
    assert.match(source, new RegExp(`export function ${component}\\(`), component);
  }
});

test("every image a section renders carries alt text", () => {
  // The most common accessibility defect on a marketing page, and one SH101's render digest
  // reports — so the vocabulary must not be the thing that introduces it.
  const images = source.match(/<img[^>]*>/g) ?? [];
  assert.ok(images.length >= 3, "the vocabulary renders images");
  for (const tag of images) {
    assert.match(tag, /\balt=/, `an <img> without alt: ${tag}`);
  }
});
