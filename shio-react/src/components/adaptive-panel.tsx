/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoPanel, useSectionChrome } from "@viglet/viglet-design-system/bento"
import type React from "react"
import { Card, CardContent } from "@/components/ui/card"

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
 * ## The frosted half is the package's now (SH923)
 *
 * The bento vocabulary had `BentoTile` (a clickable tile), `BentoSection` (a heading plus a grid)
 * and `BentoFormSection` (a titled form group), and nothing for "a frosted container with
 * arbitrary content" — so this file carried `bento-glass rounded-2xl border shadow-md` copied out
 * of `BentoSaveBar`, with a comment promising the two would be kept in step by hand.
 *
 * **VDS79 exports `BentoPanel`** and 2026.3.5 is the first release carrying it, so the copy is
 * gone and the promise with it. Nothing here decides what a frosted panel looks like any more,
 * which is the whole point: the next consumer to need one asks the package instead of picking its
 * own radius.
 *
 * What is left is the *adapter*, and only the console branch justifies it. **SH932** is the task
 * that removes both — the branch and this file's name, which promises a choice the era no longer
 * offers.
 */
export function AdaptivePanel({ children, className, contentClassName }: Readonly<AdaptivePanelProps>) {
  const chrome = useSectionChrome()

  if (chrome === "bento") {
    return <BentoPanel className={className} contentClassName={contentClassName}>{children}</BentoPanel>
  }

  return (
    <Card className={className}>
      <CardContent className={contentClassName}>{children}</CardContent>
    </Card>
  )
}
