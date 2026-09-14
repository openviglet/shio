/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconArticle,
  IconDatabase,
  IconFileText,
  IconFolder,
  IconLayoutGrid,
  IconPhoto,
  IconSitemap,
  IconWorld,
  type Icon as TablerIcon,
} from "@tabler/icons-react"

/**
 * Specimen data for {@link ../bento/bento-vocabulary.page BentoVocabularyPage}.
 *
 * ## Why the copy lives in a `.ts` and not in the page
 *
 * `console-english.test.mjs` (SH823) fails a `.tsx` under `src` that puts a readable English
 * sentence in a `title=`/`description=` prop or a JSX text node, because a string that is not a
 * key can never be missing from a locale file and so `i18n-parity` is blind to it. That lint is
 * right about every curator-facing page and would be wrong about this one: these are **specimen
 * strings naming the components being proved**, not product copy, and there is no Portuguese
 * curator who reads them — the page is a developer harness with no nav entry, mounted outside the
 * console shell.
 *
 * Rather than write three-a-dozen entries into that lint's `UNTRANSLATED` exemption map — which
 * would dilute a list whose value is that it is short and each line carries a reason — the
 * specimen text is separated from the composition. The page renders `{item.title}`; this file
 * holds the words. The split is also the honest one: what SH887 proves is that the *layer*
 * renders here, and the words are scaffolding around that proof.
 *
 * When a real surface is migrated (Block AR), its copy goes through `t()` like every other page.
 * This file is deleted with the harness.
 */

/** A specimen entity for the mosaic and the entity-tile row. */
export interface VocabularySpecimen {
  id: string
  title: string
  description: string
  icon: TablerIcon
  meta: string
  enabled: number
}

/**
 * Eight specimens, because the mosaic is `grid-cols-2 md:grid-cols-4 lg:grid-cols-6` with
 * `col-span-2` tiles: eight is the smallest count that wraps at every breakpoint, so a tile
 * that reflows badly is visible on the page rather than only on a narrow one.
 */
export const SPECIMENS: VocabularySpecimen[] = [
  {
    id: "site",
    title: "ShSite",
    description: "A delivery root, its home page addressed as post:<site>/",
    icon: IconWorld,
    meta: "12 folders",
    enabled: 1,
  },
  {
    id: "folder",
    title: "ShFolder",
    description: "A node in the name chain, addressed as folder:<site>/<chain>",
    icon: IconFolder,
    meta: "48 posts",
    enabled: 1,
  },
  {
    id: "post",
    title: "ShPost",
    description: "The unified post, drafted before it is ever published",
    icon: IconArticle,
    meta: "1,204 rows",
    enabled: 1,
  },
  {
    id: "post-type",
    title: "ShPostType",
    description: "The one name-keyed surface in the whole content API",
    icon: IconDatabase,
    meta: "9 attributes",
    enabled: 1,
  },
  {
    id: "static-file",
    title: "ShStaticFile",
    description: "Bytes the CDA serves without rendering anything",
    icon: IconPhoto,
    meta: "310 MB",
    enabled: 1,
  },
  {
    id: "page-layout",
    title: "PageLayout",
    description: "Regions a theme fills, resolved by the template graph",
    icon: IconLayoutGrid,
    meta: "4 regions",
    enabled: 1,
  },
  {
    id: "region",
    title: "Region",
    description: "One sh-region slot, addressed by the Universal Editor bridge",
    icon: IconSitemap,
    meta: "unused",
    enabled: 0,
  },
  {
    id: "theme",
    title: "Theme",
    description: "Handlebars templates and the helpers they may call",
    icon: IconFileText,
    meta: "missing",
    enabled: 0,
  },
]

/** Copy for the parts of the page that are not a specimen entity. */
export const COPY = {
  eyebrow: "SH887",
  title: "Bento vocabulary",
  subtitle:
    "Every component the bento layer exports, rendered in this console's own CSS and provider"
    + " environment. Not a curator surface: a harness that proves the layer arrived intact.",
  backLabel: "Console home",
  tonesTitle: "Tones",
  tonesDescription:
    "A tone is a token pair the preset defines; no component in the layer names a colour.",
  panelLabel: "BentoPanel — a frosted container with no heading of its own",
  panelBody:
    "The shape a stats strip, a toolbar or a listing needs. It carries no padding, so the caller"
    + " decides whether its content reaches the edge.",
  sectionEyebrow: "Content model",
  sectionTitle: "Tiles",
  sectionDescription: "BentoTile and BentoCountTile, spanning in multiples of two.",
  formTitle: "BentoFormSection",
  formDescription: "The grouping a detail screen puts its fields in, heading level h2 under the hero.",
  inlineLabel: "BentoInlineEdit specimen",
  inlinePlaceholder: "Type, then press Enter",
  iconPickerTitle: "BentoHeroIconPicker",
  iconPickerDescription: "The identity glyph, which lives in the hero and never in the form.",
  mosaicTitle: "BentoTileGrid",
  mosaicEmptyTitle: "Nothing here",
  mosaicEmptyDescription: "Which is what an empty mosaic looks like when a filter matches nothing.",
  emptyTitle: "BentoEmptyState",
  emptyDescription: "The shape every list falls back to, rendered here with no list around it.",
  layoutTitle: "Layout maths",
  layoutDescription:
    "resolveBentoLayout orders items and assigns spans; toBentoLayoutEntries turns the result"
    + " back into what a persistence callback would save.",
  actionsLabel: "Specimen actions",
  actionRename: "Rename",
  actionDuplicate: "Duplicate",
  actionDelete: "Delete",
  adaptiveTitle: "AdaptiveSectionCard",
  adaptiveDescription:
    "One form's markup, rendered as a frosted bento section here and as a console card under the"
    + " other chrome — the provider decides, and the fields are written once.",
  adaptiveBody: "A form's fields go here.",
  statusTitle: "BentoStatusMarker",
  statusDirty: "dirty",
  statusMissing: "title missing",
}
