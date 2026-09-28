/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoFormHero, BentoHero, type BentoTrailStep } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { useTranslation } from "react-i18next"

interface PageHeroProps {
  title: string
  /** The hero's eyebrow. */
  eyebrow?: string
  /** The hero's subtitle. */
  subtitle?: string
  /** The hero's back link. */
  urlBase?: string
  /** SH1054 — its words. Defaults to "Back": the package draws the link with this as its only text. */
  backLabel?: string
  /**
   * SH1192 — the entity's ancestors, root first and its parent last, for content that nests. Two or
   * more render as linked steps where the back link sits (VDS184); one renders the arrow back link.
   */
  trail?: readonly BentoTrailStep[]
  /** Actions, rendered as the hero's `trailing`. */
  actions?: React.ReactNode

  /*
   * SH901 — the save bar, when the page has one. `BentoFormHero` renders the controls in the hero
   * and fades a fixed bar in as the hero scrolls away, which the authoring contract asks for
   * because composing those two halves by hand is how they drift. Give it these and it renders
   * the morph; leave them out and it is just a hero.
   */
  /** Sticky/morph title. Defaults to `title`. */
  saveTitle?: string
  saveBadges?: React.ReactNode
  onCancel?: () => void
  loading?: boolean
  saveDisabled?: boolean
  dirty?: boolean
  titleMissing?: boolean
}

/**
 * SH900 — one page hero for every surface that has one, rather than a copy per page.
 *
 * It maps this product's header props onto the package's `BentoHero` or `BentoFormHero`, choosing
 * on whether the page gave it something only a save bar takes. That choice is worth one place
 * rather than twenty-four: the large working surfaces — the content browser, the post and
 * post-type editors, the infrastructure forms — each pass props here instead of composing the
 * morph themselves.
 */
export function PageHero({
  title,
  eyebrow,
  subtitle,
  urlBase,
  backLabel,
  trail,
  actions,
  saveTitle,
  saveBadges,
  onCancel,
  loading,
  saveDisabled,
  dirty,
  titleMissing,
}: Readonly<PageHeroProps>) {
  // A page has a save bar when it gave this adapter something only a save bar takes. `actions`
  // alone is not enough — the content browser passes two navigation buttons and wants no bar.
  const hasSaveBar = onCancel !== undefined || saveBadges !== undefined
  // SH1054 — the package draws the back link as an arrow plus `backLabel`, so with no label it was
  // an arrow and nothing: axe found that unnamed link on 28 routes, one per page with a way back.
  const { t } = useTranslation()
  const back = urlBase ? backLabel ?? t("common.back") : undefined

  if (hasSaveBar) {
    return (
      <BentoFormHero
        eyebrow={eyebrow}
        backTo={urlBase}
        backLabel={back}
        trail={trail}
        title={title}
        subtitle={subtitle}
        onCancel={onCancel ?? (() => undefined)}
        actions={actions}
        loading={loading}
        dirty={dirty}
        titleMissing={titleMissing}
        saveDisabled={saveDisabled}
        stickyTitle={saveTitle ?? title}
        stickyBadges={saveBadges}
      />
    )
  }

  /*
   * The two package heroes disagree when given both an eyebrow and a back link: `BentoFormHero`
   * draws the link, `BentoHero` draws the eyebrow and drops the link. So every page without a save
   * bar that passed both — the content browser, the admin lists — had no way back. Here the link
   * wins in both, and its words fall back to the eyebrow's, which on these pages names the parent.
   */
  return (
    <BentoHero
      eyebrow={urlBase ? undefined : eyebrow}
      backTo={urlBase}
      backLabel={urlBase ? backLabel ?? eyebrow ?? back : undefined}
      trail={trail}
      title={title}
      subtitle={subtitle}
      trailing={actions}
    />
  )
}
