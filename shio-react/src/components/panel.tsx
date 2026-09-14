/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { BentoPanel } from "@viglet/viglet-design-system/bento"
import type React from "react"

interface PanelProps {
  children: React.ReactNode
  className?: string
  /** Applied to the inner content wrapper, which carries no padding of its own. */
  contentClassName?: string
}

/**
 * A frosted panel with **no header**: a count, a toolbar, a listing, where the hero above already
 * names the page.
 *
 * <h2>Why not a header-less section (SH902)</h2>
 *
 * `AdaptiveSectionCard` renders its frosted form only when it finds a `Header`/`StaticHeader` child,
 * and falls back to a plain card without one. The content browser's three panels and the post-type
 * list were written that way and rendered plain cards under the frosted shell — invisible in review
 * and in `tsc`; `checkBentoSurface` found it reading `backdrop-filter` off computed style.
 *
 * <h2>A seam, not an adapter</h2>
 *
 * One import to change if `BentoPanel`'s props move, across the surfaces that use it. It does not
 * re-export `BentoPanel` under a second name — the duplicate gate would object — and it adds no
 * styling, so the panel this console renders is the package's and cannot drift from it.
 */
export function Panel({ children, className, contentClassName }: Readonly<PanelProps>) {
  return <BentoPanel className={className} contentClassName={contentClassName}>{children}</BentoPanel>
}
