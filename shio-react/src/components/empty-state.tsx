/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { Icon as TablerIcon } from "@tabler/icons-react"
import { BentoEmptyState, useSectionChrome, type BentoTone } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { cn } from "@/lib/utils"

interface EmptyStateProps {
  /** Tabler icon, as `BentoEmptyState` takes it. Rendered in both chromes. */
  icon: TablerIcon
  /** Bento only: the tone driving the icon chip's gradient. The console block is monochrome. */
  tone?: BentoTone
  /** What is missing, in plain language. */
  title: string
  /** One line on how to fill it. */
  description?: React.ReactNode
  /** Optional call to action, rendered under the hint in both chromes. */
  action?: React.ReactNode
  className?: string
}

/**
 * SH903 — the "nothing here" block, written once.
 *
 * ## The one of the three that really does still branch (SH932)
 *
 * SH932 renamed three `Adaptive*` components on the grounds that none of them adapted any more.
 * That was true of two. This one has a **live** second branch, and the reason is worth stating
 * because it is not the reason the name suggested.
 *
 * It is not choosing between two *eras*. The console era is gone. It is choosing between **inside
 * the shell and outside it**: `not-found.page.tsx` is mounted at App.tsx's bare `*`, for a path
 * under neither shell and therefore under no `SectionCardChromeProvider`, so `useSectionChrome()`
 * answers with its `console` default and this renders the plain block. That route is permanent —
 * a 404 for an unknown path cannot be inside a shell that only covers known ones — so the branch
 * is not migration debt waiting to be deleted.
 *
 * `EmptyState` rather than `AdaptiveEmptyState` all the same. *Adaptive* invited the next reader
 * to conclude there is a console chrome to render into, and the cheapest way to honour that
 * reading is to write one back.
 *
 * ## Three copies of the same twelve lines, and why that was the defect
 *
 * Search, the trash and the static-file list are the three surfaces where **rendering nothing is
 * the normal case**, not an error — an unmatched query, an empty bin, a site with no uploads. All
 * three said so, and all three said so in their own hand-rolled block: the same rounded icon chip,
 * the same `py-16 text-center`, the same `h-8 w-8 text-muted-foreground`, three times over. The
 * static-file one had grown a second axis (filtered-out vs genuinely blank) that the other two
 * never got, which is what copy-three-times always ends as — the copies stop being copies and
 * nobody can see which one is the reference.
 *
 * ## What is shared and what is deliberately not
 *
 * The **shape** is shared; the **message** is not. §LV.6 is explicit that "an empty search is not
 * the same message as an empty trash", so `title` and `description` are required per use rather
 * than defaulted to one generic line — an empty state that says "nothing here" tells a curator
 * only what they can already see. Search says the query matched nothing and suggests widening it;
 * the trash says it is empty and that deleting something puts it here; the file list distinguishes
 * a filter that excluded everything from a site with no files at all, and the two want different
 * next actions.
 *
 * ## One icon type, not two
 *
 * The package takes a Tabler icon and the console pages were written with lucide. Rather than
 * carry both props, this takes the Tabler one and renders it in console chrome too: it is an SVG
 * component that accepts `className` like any other, so the console branch is unchanged in
 * appearance and the call sites stop having to know which era they are in.
 */
export function EmptyState({
  icon: Icon,
  tone,
  title,
  description,
  action,
  className,
}: Readonly<EmptyStateProps>) {
  const chrome = useSectionChrome()

  if (chrome === "bento") {
    return (
      <BentoEmptyState
        icon={Icon}
        tone={tone}
        title={title}
        description={description}
        action={action}
        className={className}
      />
    )
  }

  return (
    <div className={cn("flex flex-col items-center justify-center py-16 text-center", className)}>
      <div className="rounded-full bg-muted p-4 mb-4">
        <Icon className="h-8 w-8 text-muted-foreground" />
      </div>
      <h3 className="font-semibold text-lg mb-1">{title}</h3>
      {description ? <p className="text-sm text-muted-foreground max-w-sm">{description}</p> : null}
      {action ? <div className="mt-4">{action}</div> : null}
    </div>
  )
}
