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
 * A frosted panel with **no header**, which this console needed four times before the layer had it.
 *
 * <h2>What it cost before anybody noticed (SH902)</h2>
 *
 * `AdaptiveSectionCard` re-skins only when it finds a `Header`/`StaticHeader` child — its own line
 * is `chrome === "bento" && header` — and that fallback is deliberate: a section built some other
 * way keeps its content rather than losing it silently. It is wrong for a panel with nothing to
 * title. The content browser's stats strip, its toolbar and its listing, and the post-type list,
 * carry a count, a breadcrumb, a view toggle and rows; the hero above already names the page. All
 * four were written with `AdaptiveSectionCard` and no header, and all four rendered **console cards
 * under the bento shell**. Invisible in review and in `tsc`; what found it was `checkBentoSurface`
 * reading `backdrop-filter` off computed style — `frost: null` on a page that looked finished.
 *
 * <h2>Why this file still exists, and what it is not (SH932)</h2>
 *
 * It was `AdaptivePanel` and it had a console branch. **SH923** gave the frosted half to the
 * package as `BentoPanel`, and SH932 established that the console branch was **dead**: every one of
 * its call sites is mounted under `SectionCardChromeProvider chrome="bento"`, including the one
 * place that looked like an exception — `not-found.page.tsx` composes it inside its `if (bento)`
 * arm and uses `EmptyState` outside the shell. So the branch is gone and the name no longer
 * promises a choice that was deleted.
 *
 * What is left is a **seam, not an adapter**: one import to change if the package's props move,
 * across the surfaces that use it. It deliberately does not re-export `BentoPanel` under a second
 * name — the duplicate gate would be right to object — and it adds no styling, so the panel this
 * console renders is the package's and cannot drift from it.
 */
export function Panel({ children, className, contentClassName }: Readonly<PanelProps>) {
  return <BentoPanel className={className} contentClassName={contentClassName}>{children}</BentoPanel>
}
