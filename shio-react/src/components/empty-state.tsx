/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { Icon as TablerIcon } from "@tabler/icons-react"
import { BentoEmptyState } from "@viglet/viglet-design-system/bento"
import type React from "react"

interface EmptyStateProps {
  /** Tabler icon, as `BentoEmptyState` takes it. */
  icon: TablerIcon
  /** What is missing, in plain language. */
  title: string
  /** One line on how to fill it. */
  description?: React.ReactNode
  /** Optional call to action, rendered under the hint. */
  action?: React.ReactNode
  className?: string
}

/**
 * SH903 — the "nothing here" block, written once.
 *
 * ## One rendering (SH959)
 *
 * This had a second branch after the others lost theirs: a page mounted under no provider —
 * the catch-all 404, outside every shell — read the package's default and drew a monochrome block
 * instead of `BentoEmptyState`. SH958 put one provider above the router, so nothing is mounted
 * under none, and the branch could not be reached; it is deleted rather than kept as a fallback
 * nobody exercises.
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
 * ## Why it is still a component of this console's
 *
 * A seam, the way `panel.tsx` is: the call sites take one import whose props are the product's
 * rather than the package's, and the day `BentoEmptyState`'s props move, one file changes.
 */
export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
}: Readonly<EmptyStateProps>) {
  return (
    <BentoEmptyState
      icon={icon}
      title={title}
      description={description}
      action={action}
      className={className}
    />
  )
}
