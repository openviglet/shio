/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  IconCopy,
  IconPencil,
  IconSettings,
  IconStack2,
  IconTrash,
  IconWand,
} from "@tabler/icons-react"
import { useState } from "react"
import {
  AdaptiveSectionCard,
  BENTO_EMPHASIS_NEXT,
  BENTO_EMPHASIS_SPAN,
  BENTO_RECORD_STATES,
  BENTO_TONE_CLASS,
  BENTO_TONES,
  BentoActionsMenu,
  BentoBackLink,
  BentoCountTile,
  BentoEmptyState,
  BentoEntityTile,
  BentoFormSection,
  BentoHero,
  BentoHeroIconPicker,
  BentoInlineEdit,
  BentoPanel,
  BentoSection,
  BentoStatusMarker,
  BentoTile,
  BentoTileGrid,
  bentoChipClass,
  resolveBentoLayout,
  toBentoLayoutEntries,
} from "@viglet/viglet-design-system/bento"
import { ROUTES } from "@/app/routes.const"
import { COPY, SPECIMENS } from "./bento-vocabulary.fixture"

/**
 * SH887 — the first Shio page that imports `@viglet/viglet-design-system/bento`.
 *
 * ## Why a throwaway page is a task and not a step inside SH888
 *
 * The bento layer was extracted from **one** product and had been exercised by that product
 * alone. The first consumer of a newly extracted layer is what finds the assumptions the
 * extraction carried over: a React context that was always mounted around it, a CSS custom
 * property some global stylesheet always defined, a Tailwind class the other application's
 * preset happened to emit, a transitive dependency that resolved because the other repository
 * had it at the top level. None of those show up in `tsc`, none of them show up in a resolve
 * check like SH915's, and every one of them shows up as a page that renders and looks wrong.
 *
 * So this page exists to be looked at, once, before a route tree (SH888) or a real surface
 * (Block AR) is built on top of the layer. It has **no nav entry and no data**: it takes no
 * service, issues no request, and reads nothing from the backend, which is what lets it mount
 * outside the product's shell and what keeps it from failing for a reason that is not the layer's.
 * A top-level route gives the layer the empty page it expects.
 *
 * ## What is deliberately not here
 *
 * Twelve of the layer's thirty-six runtime exports are page-level singletons — the shell
 * (rail, user menu, back-to-top), the nav-driven surfaces (command palette, shortcuts dialog),
 * and the two whole-screen shapes that own a hero and the save-bar morph (`BentoListPage`,
 * `BentoEntityShell`, `BentoFormHero` and the two bars it composes). Rendering a second fixed
 * save bar or a second hero beside this page's own is exactly the drift the authoring contract
 * warns about, and each of them needs product data this harness refuses to fetch. Each is
 * assigned to the task that owns it, by name, in `test/bento-vocabulary.test.mjs` — which is
 * also what fails the day the package exports something this console has never rendered.
 */

/** Specimen ids that stand for a mosaic whose filter matched nothing. */
const NOTHING: typeof SPECIMENS = []

export default function BentoVocabularyPage() {
  const [name, setName] = useState(SPECIMENS[0].title)
  const [icon, setIcon] = useState<string | null>(null)

  /*
   * The layout maths with no persistence behind it. `resolveBentoLayout` assigns each item an
   * emphasis and a span; `toBentoLayoutEntries` turns the result back into the rows a save
   * callback would write. Rendering both here means a change to either shows up as a mosaic
   * that reflows wrongly, rather than as a diff nobody ran.
   */
  const resolved = resolveBentoLayout(SPECIMENS, (item) => item.id, undefined)
  const entries = toBentoLayoutEntries(resolved)

  return (
    /*
     * `bg-background text-foreground min-h-screen` is not decoration: a page mounted outside a
     * shell paints its own ground, because nothing else will.
     *
     * Looking at this one is also what found SH918 — `<html>` carried `dark` while `<body>` kept
     * the boot loader's `shio-light-boot` class and its near-white ground for the whole session,
     * so the hero rendered white on white. That is fixed at the source now (the splash and the
     * app read one declaration, and the boot class is cleared once React replaces the splash),
     * and these classes stay anyway: they were never the fix, and a shell-less page that does
     * not paint its own ground is one theme change away from the same symptom.
     *
     * viglet-ds-allow-page-column -- routed outside BentoShell (SH947), so nothing else sets a column
     */
    <main className="bg-background text-foreground mx-auto min-h-screen max-w-6xl space-y-8 p-6">
      <BentoHero
        eyebrow={COPY.eyebrow}
        backTo={ROUTES.HOME}
        backLabel={COPY.backLabel}
        title={COPY.title}
        subtitle={COPY.subtitle}
        leading={<IconStack2 size={28} aria-hidden="true" />}
        trailing={
          <BentoActionsMenu
            triggerLabel={COPY.actionsLabel}
            actions={[
              { label: COPY.actionRename, icon: IconPencil, onSelect: () => undefined },
              { label: COPY.actionDuplicate, icon: IconCopy, onSelect: () => undefined },
              {
                label: COPY.actionDelete,
                icon: IconTrash,
                tone: "destructive",
                onSelect: () => undefined,
              },
            ]}
          />
        }
      />

      {/* Every tone the preset defines, so a re-keyed palette is one screenshot away from proof. */}
      <BentoFormSection icon={IconWand} tone="violet" title={COPY.tonesTitle} description={COPY.tonesDescription}>
        <ul className="flex flex-wrap gap-2">
          {BENTO_TONES.map((tone) => (
            <li key={tone} className={bentoChipClass(tone)}>
              <span className={BENTO_TONE_CLASS[tone]} aria-hidden="true" />
              {tone}
            </li>
          ))}
        </ul>
        {/* Since VDS182 a tone means a record's state, drawn as a dot and a word (SH1199). */}
        <ul className="flex flex-wrap gap-3">
          {BENTO_RECORD_STATES.map((state) => (
            <li key={state}><BentoStatusMarker state={state} /></li>
          ))}
        </ul>
      </BentoFormSection>

      {/*
        The one container in the layer with no heading, so it is proved by what it does NOT
        render as much as by what it does. Given padding here because an unpadded box on a
        proving page reads as a rendering fault rather than as the default it is.
      */}
      <BentoPanel contentClassName="flex flex-col gap-1 p-5">
        <p className="text-sm font-medium">{COPY.panelLabel}</p>
        <p className="text-sm text-muted-foreground">{COPY.panelBody}</p>
      </BentoPanel>

      <BentoSection
        eyebrow={COPY.sectionEyebrow}
        title={COPY.sectionTitle}
        description={COPY.sectionDescription}
      >
        <BentoTile
          icon={IconStack2}
          tone="indigo"
          eyebrow={COPY.eyebrow}
          title={COPY.sectionTitle}
          span="col-span-2"
          featured
        />
        {SPECIMENS.slice(0, 3).map((specimen) => (
          <BentoCountTile
            key={specimen.id}
            icon={specimen.icon}
            tone="blue"
            label={specimen.title}
            count={specimen.description.length}
            span="col-span-2"
          />
        ))}
      </BentoSection>

      {/* The form vocabulary: a section, an inline edit and the identity glyph that never becomes a field. */}
      <BentoFormSection
        icon={IconSettings}
        tone="emerald"
        title={COPY.formTitle}
        description={COPY.formDescription}
        trailing={<BentoStatusMarker dirty />}
      >
        <BentoInlineEdit
          value={name}
          onSave={setName}
          ariaLabel={COPY.inlineLabel}
          placeholder={COPY.inlinePlaceholder}
        />
        <BentoHeroIconPicker
          value={icon}
          onChange={setIcon}
          onClear={() => setIcon(null)}
          defaultIcon={IconStack2}
          tone="emerald"
          title={COPY.iconPickerTitle}
          description={COPY.iconPickerDescription}
        />
        <p className="text-muted-foreground text-xs">
          <BentoStatusMarker titleMissing />
          <span>{COPY.statusMissing}</span>
        </p>
      </BentoFormSection>

      {/* The adapter VDS22 shipped: a section composed from a header and a body. */}
      <AdaptiveSectionCard>
        <AdaptiveSectionCard.StaticHeader
          icon={IconWand}
          title={COPY.adaptiveTitle}
          description={COPY.adaptiveDescription}
        />
        <AdaptiveSectionCard.Content>
          <p className="text-sm text-muted-foreground">{COPY.adaptiveBody}</p>
        </AdaptiveSectionCard.Content>
      </AdaptiveSectionCard>

      <BentoSection title={COPY.mosaicTitle} description={COPY.layoutDescription}>
        <div className="col-span-2 md:col-span-4 lg:col-span-6">
          <BentoTileGrid
            items={SPECIMENS}
            tryAgainUrl={ROUTES.HOME}
            tone="indigo"
            hideNew
            itemKey={(item) => item.id}
            renderTile={(item) => (
              <BentoEntityTile
                to={ROUTES.HOME}
                defaultIcon={item.icon}
                icon={item.id === "site" ? icon : null}
                tone="blue"
                title={item.title}
                description={item.description}
                meta={item.meta}
                enabled={item.enabled}
                hasStatus
              />
            )}
            emptyTitle={COPY.mosaicEmptyTitle}
            emptyDescription={COPY.mosaicEmptyDescription}
          />
        </div>
      </BentoSection>

      {/* The same mosaic with nothing in it, which is the state a curator sees most often. */}
      <BentoTileGrid
        items={NOTHING}
        tryAgainUrl={ROUTES.HOME}
        hideNew
        itemKey={(item) => item.id}
        renderTile={() => null}
        emptyTitle={COPY.mosaicEmptyTitle}
        emptyDescription={COPY.mosaicEmptyDescription}
      />

      <BentoEmptyState
        icon={IconStack2}
        tone="slate"
        title={COPY.emptyTitle}
        description={COPY.emptyDescription}
        align="center"
      />

      {/*
       * The resolved layout, printed. A span that stops being a multiple of two, or an emphasis
       * cycle that stops cycling, is visible here as text before it is visible as a broken grid.
       */}
      <BentoFormSection icon={IconStack2} tone="slate" title={COPY.layoutTitle} description={COPY.layoutDescription}>
        <ul className="text-muted-foreground space-y-1 font-mono text-xs">
          {resolved.map((row) => (
            <li key={row.key}>
              {row.key} · {row.emphasis} · {BENTO_EMPHASIS_SPAN[row.emphasis]} ·{" "}
              {BENTO_EMPHASIS_NEXT[row.emphasis]}
            </li>
          ))}
        </ul>
        <p className="text-muted-foreground font-mono text-xs">{entries.length}</p>
      </BentoFormSection>

      <BentoBackLink to={ROUTES.HOME}>{COPY.backLabel}</BentoBackLink>
    </main>
  )
}
