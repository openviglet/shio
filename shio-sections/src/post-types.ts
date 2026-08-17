/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * The section vocabulary (SH95) — the content model half.
 *
 * Why a fixed vocabulary is the highest-leverage thing in Block J. The expensive part
 * of "now change the site" is not the write, it is the **archaeology**: an agent
 * re-deriving what a previous session invented, because every site invented its own
 * post-types with its own field names for the same four ideas. A known set of sections
 * makes "add a testimonial section to the pricing page" a single operation over a shared
 * language instead of a design exercise — and the same language is what the console
 * renders, what the Universal Editor (SH10) manipulates and what SH101's render digest
 * can check structurally.
 *
 * The composition rule, which is the one design decision here worth arguing about: a
 * `Page` names its sections by **friendly URL**, in order, in a Multi Select. Not by id
 * (SH79's whole point is that an agent should never have to carry a UUID between calls),
 * and not by a Relator of typed children (a Relator's children are one fixed shape, and
 * a page's sections are deliberately heterogeneous — that is what makes them composable).
 * A list of addresses is ordered, human-readable in the console, diffable in a content
 * file, and resolvable by the front end with one CDA call per section.
 */
import {
  aceHtml,
  comboBox,
  file,
  htmlEditor,
  multiSelect,
  postType,
  relator,
  text,
  textArea,
  type ShPostTypeDef,
} from "@viglet/shio-model";

/** The post-type name of every section, keyed by its short vocabulary name. */
export const SECTION_TYPES = {
  hero: "SectionHero",
  featureGrid: "SectionFeatureGrid",
  cta: "SectionCta",
  logoWall: "SectionLogoWall",
  testimonial: "SectionTestimonial",
  faq: "SectionFaq",
  markup: "SectionMarkup",
  richText: "SectionRichText",
} as const;

/** The post-type of a page that composes sections. */
export const PAGE_TYPE = "Page";

/**
 * A curated menu, and the item it is made of (SH382).
 *
 * Why these are here rather than in the render model. A menu *looks* like renderer machinery,
 * and SH249's test for that is whether it needs a new mechanism: this one needs none. It is
 * ordinary content, so `post.upsert`, the file projection, `shio verify` and the section
 * overlay all work on it the day it exists — and the composition rule is the one `Page` already
 * uses, which is why `references: "post"` gives SH154's `dangling-reference` coverage of a menu
 * entry pointing at a deleted page **for free**, with no lint written for it.
 *
 * Why the folder tree could not be the source, even after SH381 gave `{{#navigation}}` a `url`.
 * A real site's navigation is **curated**: a label that differs from the page title, groups that
 * are not folders, an ordering that is editorial, entries that leave the site, a page
 * deliberately left out, and one menu per locale on a translated tree. A folder chain expresses
 * none of those, so every site was hand-writing its nav into every layout.
 */
export const MENU_TYPE = "Menu";
export const MENU_ITEM_TYPE = "MenuItem";

/**
 * A page: metadata plus an ordered list of section URLs.
 *
 * `sections` is a Multi Select scoped to no single post-type on purpose — the values are
 * addresses, and each address resolves to a post that carries its own type. The front end
 * dispatches on that type (see `SECTION_COMPONENTS`), so adding a section type to the
 * vocabulary never requires editing `Page`.
 */
export const page = (): ShPostTypeDef =>
  postType(PAGE_TYPE, {
    title: "Page",
    namePlural: "Pages",
    description:
      "A page composed of sections. `sections` lists section friendly URLs in render order.",
    fields: [
      text("title", { label: "Title", required: true, isTitle: true }),
      textArea("description", {
        label: "Description",
        description: "Used as the page summary and the meta description.",
        isSummary: true,
      }),
      multiSelect("sections", {
        label: "Sections",
        description:
          "Friendly URLs of the section posts, in render order — e.g. /sections/home/hero.",
        // SH154/SH169 — this is what tells the server the values are addresses and not tags.
        // Without it `shio verify` cannot see a page naming a section that does not exist, and
        // SH101's digest reads a section-composed page as the hash of nothing. `postType` stays
        // open on purpose: the values resolve to posts that carry their own type, and the front
        // end dispatches on it.
        references: "post",
      }),
      // SH607 — markup the page carries itself, rendered around its sections. Added because it
      // was already being written: `shio convert` emits `title`, `description`, `sections` AND
      // `content` on every page, and three `Page` definitions shipped without one that could hold
      // all four — this one had no `content` and the SH71 bootstrap starter's has no `sections`.
      // So the type every conversion has ever been written against matched no file in the repo,
      // and the chain worked only on instances carrying a hand-made hybrid.
      //
      // An Ace HTML editor rather than the WYSIWYG one: this holds a whole page body captured
      // from somebody else's site, so it is code a curator inspects, and SH191 then projects it
      // to an `.html` sidecar instead of inlining a megabyte into the content file.
      aceHtml("content", {
        label: "Content",
        description:
          "Markup this page carries itself, rendered around its sections. A conversion writes "
          + "the source's own body here for the blocks nobody made authorable.",
      }),
    ],
  });

/**
 * A menu: a title the template asks for, and an ordered list of addresses.
 *
 * The same composition rule as `Page.sections`, deliberately — one shape to learn. Each address
 * resolves to a post that carries its own type, and three are meaningful: a `MenuItem` (a label
 * and/or an external link), another `Menu` (a nested group), or **any page at all**, which is the
 * cheap case and takes its label from the page's own title. `postType` stays open for exactly that
 * reason.
 */
export const menu = (): ShPostTypeDef =>
  postType(MENU_TYPE, {
    title: "Menu",
    namePlural: "Menus",
    description:
      "A curated menu. `items` lists addresses in render order: a MenuItem, a nested Menu, or a page.",
    fields: [
      text("title", { label: "Title", required: true, isTitle: true }),
      multiSelect("items", {
        label: "Items",
        description:
          "Friendly URLs in render order — a MenuItem, a nested Menu, or a page (which uses its own title).",
        // SH154/SH169, and here it is what makes the whole post-type free: without it, a menu
        // entry naming a deleted page is invisible to `shio verify` and SH101's digest reads the
        // menu as the hash of nothing.
        references: "post",
      }),
    ],
  });

/**
 * One entry: the label override, the link override, and where it points.
 *
 * `target` is a Multi Select holding **one** address, which looks like the wrong field kind and is
 * the right one: `references: "post"` is declared on Multi Select alone, so it is the only shape the
 * lint and the digest already understand — and a `Content Select` stores a post **id**, which would
 * put a UUID in a content file and break SH79's rule that no address a human writes is a UUID.
 *
 * An item needs `target` or `href`, never both, and that is a `shio verify` rule rather than a
 * schema one: the model has no way to express "exactly one of these", and a required field would
 * make an external link impossible.
 */
export const menuItem = (): ShPostTypeDef =>
  postType(MENU_ITEM_TYPE, {
    title: "Menu Item",
    namePlural: "Menu Items",
    description:
      "One menu entry: a label, and either a target page or an external URL. Not both.",
    fields: [
      text("label", { label: "Label", required: true, isTitle: true }),
      multiSelect("target", {
        label: "Target page",
        description: "The friendly URL of the page this points at. Leave empty for an external link.",
        references: "post",
      }),
      text("href", {
        label: "External URL",
        description: "An absolute URL, for an entry that leaves the site. Ignored when a target is set.",
      }),
      comboBox("rel", {
        label: "Link relationship",
        description: "Emitted as rel= — what an external entry needs and an internal one does not.",
        choices: { "noopener noreferrer": "External (noopener noreferrer)", nofollow: "Nofollow" },
        allowNull: true,
      }),
    ],
  });

export const sectionHero = (): ShPostTypeDef =>
  postType(SECTION_TYPES.hero, {
    title: "Hero Section",
    namePlural: "Hero Sections",
    description: "The opening statement of a page: a headline, a line of support, one call to action.",
    fields: [
      text("heading", { label: "Heading", required: true, isTitle: true }),
      textArea("subheading", { label: "Subheading", isSummary: true }),
      htmlEditor("body", { label: "Body" }),
      text("ctaLabel", { label: "Call-to-action label" }),
      text("ctaHref", { label: "Call-to-action link" }),
      file("image", { label: "Image" }),
      comboBox("align", {
        label: "Alignment",
        choices: { left: "Left", center: "Centered" },
        allowNull: true,
      }),
    ],
  });

export const sectionFeatureGrid = (): ShPostTypeDef =>
  postType(SECTION_TYPES.featureGrid, {
    title: "Feature Grid Section",
    namePlural: "Feature Grid Sections",
    description: "A short list of capabilities, each with a title and a sentence.",
    fields: [
      text("heading", { label: "Heading", required: true, isTitle: true }),
      textArea("intro", { label: "Intro", isSummary: true }),
      comboBox("columns", {
        label: "Columns",
        choices: { "2": "Two", "3": "Three", "4": "Four" },
        allowNull: true,
      }),
      relator("features", {
        label: "Features",
        title: "Feature",
        fields: [
          text("title", { label: "Title", required: true }),
          textArea("body", { label: "Body" }),
          text("icon", { label: "Icon", description: "An icon name your front end knows." }),
        ],
      }),
    ],
  });

export const sectionCta = (): ShPostTypeDef =>
  postType(SECTION_TYPES.cta, {
    title: "Call-to-action Section",
    namePlural: "Call-to-action Sections",
    description: "One decision, stated once: a heading, a sentence and a button.",
    fields: [
      text("heading", { label: "Heading", required: true, isTitle: true }),
      textArea("body", { label: "Body", isSummary: true }),
      text("ctaLabel", { label: "Button label" }),
      text("ctaHref", { label: "Button link" }),
      comboBox("variant", {
        label: "Variant",
        choices: { primary: "Primary", subtle: "Subtle" },
        allowNull: true,
      }),
    ],
  });

export const sectionLogoWall = (): ShPostTypeDef =>
  postType(SECTION_TYPES.logoWall, {
    title: "Logo Wall Section",
    namePlural: "Logo Wall Sections",
    description: "Social proof as a row of logos.",
    fields: [
      text("heading", { label: "Heading", isTitle: true }),
      relator("logos", {
        label: "Logos",
        title: "Logo",
        fields: [
          text("name", { label: "Name", required: true }),
          file("image", { label: "Image" }),
          text("href", { label: "Link" }),
        ],
      }),
    ],
  });

export const sectionTestimonial = (): ShPostTypeDef =>
  postType(SECTION_TYPES.testimonial, {
    title: "Testimonial Section",
    namePlural: "Testimonial Sections",
    description: "What customers said, attributed.",
    fields: [
      text("heading", { label: "Heading", isTitle: true }),
      relator("quotes", {
        label: "Quotes",
        title: "Quote",
        fields: [
          textArea("quote", { label: "Quote", required: true }),
          text("author", { label: "Author", required: true }),
          text("role", { label: "Role" }),
          file("avatar", { label: "Avatar" }),
        ],
      }),
    ],
  });

export const sectionFaq = (): ShPostTypeDef =>
  postType(SECTION_TYPES.faq, {
    title: "FAQ Section",
    namePlural: "FAQ Sections",
    description: "Questions people actually ask, answered in place.",
    fields: [
      text("heading", { label: "Heading", isTitle: true }),
      textArea("intro", { label: "Intro", isSummary: true }),
      relator("questions", {
        label: "Questions",
        title: "Question",
        fields: [
          text("question", { label: "Question", required: true }),
          htmlEditor("answer", { label: "Answer" }),
        ],
      }),
    ],
  });

/**
 * Markup kept exactly as somebody else's page wrote it, as a section (SH763).
 *
 * Why a section type rather than a second field on `Page`. `shio convert` splits a mixed page's
 * kept markup at the blocks a reviewer accepted, and the previous shape carried the trailing half
 * in a `Page.contentAfter` field — which no `Page` definition declared, so every conversion against
 * a real instance was refused at the batch. Declaring it would have been the wrong repair three
 * times over: it encodes *position* rather than meaning, it admits exactly **one** split (markup
 * between two accepted blocks needs content, section, content, section, content), and it is
 * replication-only, so every hand-authored page would carry a field that is always empty.
 *
 * As a section the order comes from the list, N splits work, `Page.sections` already speaks it, and
 * a curator can move or replace a raw block — which is the mixed→authorable path mixed mode exists
 * to offer, and the one thing a positional field could never give them.
 *
 * **One field, and therefore no title field.** The value is a captured page's own bytes: making it
 * `isTitle` would put a `<footer>` in a `VARCHAR(255)` column, and a second `label` field would have
 * to be *drawn* — `ShConvertedLayoutRenderTest` requires every declared field to reach the page —
 * which means injecting a heading into markup this type exists to leave alone. The exemption is
 * signed in `post-types.test.mjs` rather than inferred, and what a curator reads in the console is
 * the friendly URL the conversion wrote.
 */
export const sectionMarkup = (): ShPostTypeDef =>
  postType(SECTION_TYPES.markup, {
    title: "Markup Section",
    namePlural: "Markup Sections",
    description:
      "Markup kept verbatim from a source page, rendered in place among the sections. A conversion "
      + "writes one per run of markup nobody made authorable.",
    fields: [
      // Ace rather than the WYSIWYG editor, for `Page.content`'s reason: this holds a whole block
      // captured from somebody else's site, so it is code a curator inspects — and the WYSIWYG
      // would rewrite it on the first save.
      aceHtml("html", {
        label: "Markup",
        required: true,
        description: "The source's own bytes for this run of the page, rendered as they are.",
      }),
    ],
  });

export const sectionRichText = (): ShPostTypeDef =>
  postType(SECTION_TYPES.richText, {
    title: "Rich Text Section",
    namePlural: "Rich Text Sections",
    description: "Prose. The escape hatch that keeps the other sections from growing fields they do not need.",
    fields: [
      text("heading", { label: "Heading", isTitle: true }),
      htmlEditor("body", { label: "Body", required: true }),
    ],
  });

/**
 * The whole vocabulary, in a fixed order.
 *
 * Order is alphabetical by post-type name and it is load-bearing: these definitions are
 * serialized into blueprint packages, and a set that iterated in a different order would
 * produce different bytes from the same source (P5).
 */
export const sectionPostTypes = (): ShPostTypeDef[] => [
  menu(),
  menuItem(),
  page(),
  sectionCta(),
  sectionFaq(),
  sectionFeatureGrid(),
  sectionHero(),
  sectionLogoWall(),
  sectionMarkup(),
  sectionRichText(),
  sectionTestimonial(),
];
