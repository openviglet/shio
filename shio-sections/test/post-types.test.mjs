/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH95 — the section vocabulary's content-model half.
 *
 * Requires @viglet/shio-model to be built (pnpm -C shio-model run build), the same
 * prerequisite the CLI suite has on @viglet/shio-client and for the same reason: the
 * canonical serializer lives there, and there is exactly one of it.
 */
import assert from "node:assert/strict";
import { test } from "node:test";

import { serializeDef } from "@viglet/shio-model";

import {
  PAGE_TYPE,
  SECTION_TYPES,
  page,
  sectionFeatureGrid,
  sectionHero,
  sectionPostTypes,
} from "../src/post-types.ts";

const byName = (name) => sectionPostTypes().find((def) => def.name === name);

test("the vocabulary is a fixed, ordered set", () => {
  assert.deepEqual(
    sectionPostTypes().map((def) => def.name),
    [
      "Page",
      "SectionCta",
      "SectionFaq",
      "SectionFeatureGrid",
      "SectionHero",
      "SectionLogoWall",
      "SectionRichText",
      "SectionTestimonial",
    ],
  );
  // The order is part of the contract: these definitions are serialized into blueprint
  // packages, and an iteration order that varied would produce different bytes from the
  // same source (P5).
  assert.deepEqual(sectionPostTypes().map((def) => def.name), Object.values({
    page: PAGE_TYPE,
    cta: SECTION_TYPES.cta,
    faq: SECTION_TYPES.faq,
    featureGrid: SECTION_TYPES.featureGrid,
    hero: SECTION_TYPES.hero,
    logoWall: SECTION_TYPES.logoWall,
    richText: SECTION_TYPES.richText,
    testimonial: SECTION_TYPES.testimonial,
  }));
});

test("a Page composes sections by address, in order", () => {
  const sections = page().fields.find((field) => field.name === "sections");

  // Not a Content Select (one post) and not a Relator (one fixed child shape): a page's
  // sections are heterogeneous and ordered, which is what makes them composable. And they
  // are addresses rather than ids, so no UUID has to survive between calls (SH79).
  assert.equal(sections.widget, "Multi Select");
  assert.match(sections.description, /friendly URLs/i);
  assert.equal(page().fields.find((field) => field.name === "title").isTitle, true);
  assert.equal(page().fields.find((field) => field.name === "title").required, true);
});

test("every section has exactly one title field, so the console shows something useful", () => {
  for (const def of sectionPostTypes()) {
    const titles = def.fields.filter((field) => field.isTitle);
    assert.equal(titles.length, 1, `${def.name} should have one title field`);
  }
});

test("the repeatable sections use a Relator with named children", () => {
  const features = sectionFeatureGrid().fields.find((field) => field.name === "features");

  assert.equal(features.widget, "Relator");
  assert.deepEqual(features.fields.map((field) => field.name), ["title", "body", "icon"]);
  assert.equal(JSON.parse(features.widgetSettings).relator.title, "Feature");
  // Ordinals are 1-based by position, assigned by the DSL — the console renders in this
  // order and the digest reads headings in it.
  assert.deepEqual(features.fields.map((field) => field.ordinal), [1, 2, 3]);
});

test("the choice fields encode to the legacy widgetSettings string, not to an object", () => {
  const align = sectionHero().fields.find((field) => field.name === "align");
  const settings = JSON.parse(align.widgetSettings);

  assert.equal(settings.choices, "left: Left\ncenter: Centered");
  assert.equal(settings.allowNull, 1);
});

test("serialization is deterministic — the same source is always the same bytes", () => {
  for (const def of sectionPostTypes()) {
    assert.equal(serializeDef(def), serializeDef(byName(def.name)));
    assert.match(serializeDef(def), /\n$/);
  }
});
