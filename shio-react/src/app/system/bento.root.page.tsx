/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import {
  BentoBackToTop,
  BentoCommandPalette,
  BentoNavRail,
  BentoShortcutsDialog,
  BentoUserMenu,
} from "@viglet/viglet-design-system/bento"
import { useEffect, useState } from "react"
import { Outlet } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { ReviewPendingProvider } from "@/contexts/review-pending.context"
import { UserProvider } from "@/contexts/user.context"
import { useBentoNav, useBentoNavItems } from "./bento-nav"
import { BentoReviewPending } from "./bento-review-pending"
import { BentoTenantLinkNotice } from "./bento-tenant-link-notice"
import { BentoTenantSwitcher } from "./bento-tenant-switcher"

/**
 * SH888 — the bento shell, mounted beside the console one rather than replacing it.
 *
 * ## Why a second route tree and not a migration in place
 *
 * `console.root.page.tsx` wraps everything in a `SidebarProvider`, and its `PageHeader` calls
 * `SidebarTrigger`. The bento shell ships **no provider at all** — the rail is fixed, one width,
 * and hidden below `md`, so a provider would be state nothing reads — and a bento component
 * reaching for the sidebar context inside the console renders blank. The two are not compatible,
 * which means a page-at-a-time migration in place leaves the console broken in the middle of
 * every one of 43 slices.
 *
 * So both trees are mounted and each surface moves when its own task ships. The cost is two
 * shells for the length of Block AR; what it buys is that **every slice is independently
 * reversible**, which across 43 pages is worth more. SH909 is the task that removes whichever
 * shell loses, and it is on the roadmap precisely so the temporary state cannot become the
 * permanent one by nobody deciding.
 *
 * ## The shell is three pieces and no context between them
 *
 * `BentoNavRail` down the left, `BentoUserMenu` in the header, `BentoBackToTop` at the corner —
 * that is the whole contract. The routed page is wrapped in `bento-rail-gutter` so its content
 * clears the fixed rail; the rail itself is `position: fixed` and takes no space in the flow, so
 * a page that forgets that gutter renders underneath it.
 *
 * ## Whose UserProvider, and the thing that made it visible
 *
 * Shio's — and since SH920 that is the same thing as the package's, which is the point.
 *
 * `BentoUserMenu` calls the design system's own `useCurrentUser`, and a local variant of a
 * context is a *different context object*: the shell's first render came up entirely blank
 * behind the error boundary with `useCurrentUser must be used within UserProvider`. Nothing in
 * `tsc` said so — both are named `UserProvider` and both take children.
 *
 * SH888 worked around it by mounting the package's provider here directly, which left the two
 * trees on two contexts and put the same trap in front of all fourteen Block AR surfaces.
 * SH920 removed the seam instead: `@/contexts/user.context` now *wraps* the package's provider
 * and republishes its value as `ShCurrentUser`, so one mount satisfies both hooks from one
 * fetch. This file imports Shio's again and needs to know nothing about it.
 *
 * `ReviewPendingProvider` is mounted since **SH936**, and the sentence that used to stand here is
 * worth keeping because it was right about the rail and wrong about the conclusion: "the rail
 * renders sections only, so SH207's badge has nowhere to appear until a hub does; mounting it now
 * would be a fetch nothing reads." The rail still renders sections only and the hub still does not
 * exist. What that reasoning missed is that the *rail* was never the only place an ambient signal
 * can live — the header is, it is visible from every surface, and `BentoReviewPending` reads the
 * count there. Waiting for a hub meant waiting for a page nobody had scheduled, while an agent
 * wrote drafts nothing told a curator about.
 */
export default function BentoRootPage() {
  const groups = useBentoNav()
  const items = useBentoNavItems()
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)

  /*
   * SH889 — the palette's keybinding is the product's, because the package takes `open` and
   * `onOpenChange` and nothing else. Ctrl/Cmd+K, and `/` when the reader is not already typing:
   * a bare `/` inside an input would swallow the character, which is the one way a search
   * shortcut makes the product worse than not having it.
   */
  const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const typing = event.target instanceof HTMLElement
        && (event.target.isContentEditable
          || ["INPUT", "TEXTAREA", "SELECT"].includes(event.target.tagName))

      if (event.key === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault()
        setPaletteOpen((open) => !open)
      } else if (event.key === "/" && !typing && !event.metaKey && !event.ctrlKey) {
        event.preventDefault()
        setPaletteOpen(true)
      }
    }
    globalThis.addEventListener("keydown", onKey)
    return () => globalThis.removeEventListener("keydown", onKey)
  }, [])

  return (
    <UserProvider>
      {/*
        SH936 — mounted here, above both the header and the Outlet, and that placement is the
        whole reason this is a context rather than a fetch inside the indicator. The count is
        READ by the header badge and INVALIDATED by the review queue when a run is approved or
        reverted; two subtrees can only reach each other from a common ancestor. A badge saying
        two over a page showing nothing pending is the one number a curator learns to distrust.
      */}
      <ReviewPendingProvider>
        <div className="bg-background text-foreground min-h-svh">
          {/*
            `sticky`, not `fixed`, and `z-40` under the rail's own 40 rather than over everything.
            `BackendStatusProvider` renders "server unavailable, retrying" as a `sticky top-0
            z-50` bar from `main.tsx`, above this tree in the flow. A fixed header sat on top of
            it and covered its retry button with the avatar — visible only while the backend is
            down, which is precisely when that button is the one thing on screen worth reaching.
            Sticky keeps both: the banner takes its 53px and the header follows underneath.

            The rail reserves its own top ~80px (`pt-20`) against the viewport, so the two do not
            need to agree on a height. Nothing else lives in this header: the breadcrumb bar is
            console chrome, and the bento era puts a page's identity in its own hero.
          */}
          <header className="sticky top-0 z-40 flex h-16 items-center justify-end gap-2 px-4">
            {/*
              SH934 — the tenant switch is an identity action, so it sits beside the identity menu.
              It renders nothing at all unless the instance is multi-tenant AND this reader belongs
              to more than one, which is why the header does not gate it here: a control that knows
              when it has nothing to offer is one the shell does not have to reason about.
            */}
            <BentoReviewPending />
            <BentoTenantSwitcher />
            <BentoUserMenu
              accountRoute={ROUTES.ADMIN_USERS}
              logoutUrl={ROUTES.LOGOUT}
              onOpenShortcuts={() => setShortcutsOpen(true)}
            />
          </header>

          <BentoNavRail groups={groups} homeRoute={ROUTES.BENTO} />

          <div className="bento-rail-gutter">
            {/*
              SH931 — inside the gutter and above the Outlet, so it clears the fixed rail and sits
              over whatever page the link led to rather than inside it. It renders nothing unless
              the URL names a tenant that is not the session's, which makes this the ordinary
              silent case for everyone except the reader who needs telling.
            */}
            <BentoTenantLinkNotice />
            <Outlet />
          </div>

          {/*
            Both read the *same* declaration the rail does — that is SH889's whole point. A
            palette built from its own list is a second place a surface has to be added, and the
            two disagree within a release rather than at some later reckoning.
          */}
          <BentoCommandPalette open={paletteOpen} onOpenChange={setPaletteOpen} items={items} />
          <BentoShortcutsDialog open={shortcutsOpen} onOpenChange={setShortcutsOpen} isMac={isMac} />

          <BentoBackToTop />
        </div>
      </ReviewPendingProvider>
    </UserProvider>
  )
}
