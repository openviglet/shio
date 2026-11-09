/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
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
  MENU_ITEM_TYPE,
  MENU_TYPE,
  PAGE_TYPE,
  SECTION_TYPES,
  menu,
  menuItem,
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
      "Menu",
      "MenuItem",
      "Page",
      "SectionCta",
      "SectionFaq",
      "SectionFeatureGrid",
      "SectionForm",
      "SectionGallery",
      "SectionHero",
      "SectionLogoWall",
      "SectionMarkup",
      "SectionRichText",
      "SectionTestimonial",
    ],
  );
  // The order is part of the contract: these definitions are serialized into blueprint
  // packages, and an iteration order that varied would produce different bytes from the
  // same source (P5).
  assert.deepEqual(sectionPostTypes().map((def) => def.name), Object.values({
    menu: MENU_TYPE,
    menuItem: MENU_ITEM_TYPE,
    page: PAGE_TYPE,
    cta: SECTION_TYPES.cta,
    faq: SECTION_TYPES.faq,
    featureGrid: SECTION_TYPES.featureGrid,
    form: SECTION_TYPES.form,
    gallery: SECTION_TYPES.gallery,
    hero: SECTION_TYPES.hero,
    logoWall: SECTION_TYPES.logoWall,
    markup: SECTION_TYPES.markup,
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

test("a Menu composes items by address, the same way a Page composes sections (SH382)", () => {
  const items = menu().fields.find((field) => field.name === "items");

  // One shape to learn. And `references: post` is what makes the whole post-type free: without it a
  // menu entry naming a deleted page is invisible to `shio verify`, and SH101's digest reads the
  // menu as the hash of nothing.
  assert.equal(items.widget, "Multi Select");
  assert.equal(JSON.parse(items.widgetSettings).multiselect.references, "post");
  // `postType` stays open on purpose: an item is a MenuItem, a nested Menu, or any page at all.
  assert.equal(JSON.parse(items.widgetSettings).multiselect.postType, undefined);
});

test("a MenuItem's target is a Multi Select, because a Content Select would store a UUID (SH382)", () => {
  const target = menuItem().fields.find((field) => field.name === "target");

  // It holds one address, which looks like the wrong field kind. It is the right one: `references`
  // is declared on Multi Select alone, so this is the only shape the lint and the digest already
  // understand — and a Content Select stores a post *id*, which would put a UUID in a content file
  // and break SH79's rule that no address a human writes is a UUID.
  assert.equal(target.widget, "Multi Select");
  assert.equal(JSON.parse(target.widgetSettings).multiselect.references, "post");
  assert.equal(menuItem().fields.find((field) => field.name === "href").widget, "Text");
});

/**
 * The one type whose content cannot be its name, and cannot be given one either (SH763).
 *
 * A signed list rather than a rule, for the reason `field-reachability.test.mjs` keeps its
 * exemptions as lists: "the markup one" is a rule that would silently absorb the next section
 * somebody forgot to give a title.
 *
 * `SectionMarkup` holds a run of a captured page's own bytes. Marking that field `isTitle` would
 * put a `<footer>` in a `VARCHAR(255)` column, and adding a `label` beside it would not be free
 * either: `ShConvertedLayoutRenderTest` requires every declared field to be **drawn** on the page,
 * so a label would mean injecting a heading into markup this type exists to leave alone. What a
 * curator reads in the console is the friendly URL — `/sections/markup-about-2`, which names the
 * page the run came from and where in it.
 */
const NO_TITLE_FIELD = new Set(["SectionMarkup"]);

test("every section has exactly one title field, so the console shows something useful", () => {
  for (const def of sectionPostTypes()) {
    const titles = def.fields.filter((field) => field.isTitle);
    assert.equal(
      titles.length,
      NO_TITLE_FIELD.has(def.name) ? 0 : 1,
      `${def.name} should have one title field`,
    );
  }
});

test("SH763: the type with no title field is declared, not inferred", () => {
  const declared = new Set(sectionPostTypes().map((def) => def.name));
  for (const name of NO_TITLE_FIELD) {
    assert.ok(
      declared.has(name),
      `${name} is exempted here and no longer declared — an exemption for a type that does not `
        + `exist is a line nobody will remove, and the next reader will trust it.`,
    );
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

test("SH1356: a gallery row is a File and a caption, and its alt text stays the asset's own", () => {
  const gallery = byName(SECTION_TYPES.gallery);
  assert.ok(gallery, "SectionGallery is in the vocabulary a package provisions");
  const images = gallery.fields.find((field) => field.name === "images");

  assert.equal(images.widget, "Relator");
  assert.deepEqual(images.fields.map((field) => field.name), ["image", "caption"],
    "no `alt` on the row: a second alt field would be a copy of the media library's that drifts, "
      + "and `image-no-alt` checks the asset's");
  assert.equal(images.fields[0].widget, "File");
  assert.equal(images.fields[0].required, true, "a row with no picture is not a gallery item");
  assert.ok(gallery.fields.some((field) => field.name === "columns"),
    "the feature grid's `columns` choice, so the theme's existing rule lays it out");
});

test("SH1386: a form section names a post type and a token, and declares no fields of its own", () => {
  const form = byName(SECTION_TYPES.form);
  assert.ok(form, "SectionForm is in the vocabulary a package provisions");
  assert.deepEqual(form.fields.map((field) => field.name),
    ["heading", "intro", "formType", "submitLabel", "formToken"],
    "SH1386: the fields are the named post type's, so a Relator of inputs here would be a second "
      + "form builder beside the content model");
  assert.equal(form.fields.find((field) => field.name === "formType").required, true);
  assert.equal(form.fields.find((field) => field.name === "formToken").required, false,
    "SH1386: an empty token is how a package ships the section closed until the curator opens it");
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
