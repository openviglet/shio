/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useSectionChrome } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { Card, CardContent } from "@/components/ui/card"
import { cn } from "@/lib/utils"

interface AdaptivePanelProps {
  children: React.ReactNode
  className?: string
  /** Applied to the inner content wrapper, where the console card put its padding. */
  contentClassName?: string
}

/**
 * SH902 — a frosted panel with **no header**, which the bento layer has no component for.
 *
 * ## The gap, and what it cost before it was noticed
 *
 * `AdaptiveSectionCard` re-skins only when it finds a `Header`/`StaticHeader` child: its own line
 * is `chrome === "bento" && header`, and the fallback is deliberate — a section built some other
 * way falls through to the console card rather than losing its content silently (SH892).
 *
 * That is correct for the package and wrong for four of Shio's panels. The content browser's
 * stats strip, its toolbar and its listing, and the post-type list, are **panels, not sections**:
 * they carry a count, a breadcrumb, a view toggle, a list of rows. A header on any of them would
 * be noise, and the hero directly above already names the page. So all four were written with
 * `AdaptiveSectionCard` + `Content` and no header — and all four silently rendered **console
 * cards under the bento shell**, which is exactly the no-op SH892 documented and SH900 then
 * walked into three times in one commit.
 *
 * It was invisible in review and in `tsc`. What found it was `checkBentoSurface` reading
 * `backdrop-filter` off computed style: `frost: null` on a page that looked finished.
 *
 * ## Why this is local, and what would replace it
 *
 * The bento vocabulary has `BentoTile` (a clickable tile), `BentoSection` (a heading plus a grid)
 * and `BentoFormSection` (a titled form group). None of them is "a frosted container with
 * arbitrary content", so every consumer that needs one will hand-roll
 * `bento-glass rounded-3xl border` — and the moment two of them differ, the product looks
 * inconsistent for a reason nobody can see in a diff.
 *
 * One implementation here rather than four at the call sites, and **SH923** asks the package for
 * the component so this can be deleted. The classes below are the ones `BentoSaveBar` uses for
 * the same job, copied deliberately so the two match today.
 */
export function AdaptivePanel({ children, className, contentClassName }: Readonly<AdaptivePanelProps>) {
  const chrome = useSectionChrome()

  if (chrome === "bento") {
    return (
      <div className={cn("bento-glass rounded-2xl border shadow-md", className)}>
        <div className={contentClassName}>{children}</div>
      </div>
    )
  }

  return (
    <Card className={className}>
      <CardContent className={contentClassName}>{children}</CardContent>
    </Card>
  )
}
