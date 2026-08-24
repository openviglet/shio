/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { UserProvider } from "@viglet/viglet-design-system"
import {
  BentoBackToTop,
  BentoCommandPalette,
  BentoNavRail,
  BentoShortcutsDialog,
  BentoUserMenu,
} from "@viglet/viglet-design-system/bento"
import { useCallback, useEffect, useState } from "react"
import { Outlet } from "react-router-dom"
import { ROUTES } from "@/app/routes.const"
import { ShUserService } from "@/services/auth/user.service"
import { useBentoNav, useBentoNavItems } from "./bento-nav"

const shUserService = new ShUserService()

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
 * The **package's**, not `@/contexts/user.context`. `BentoUserMenu` calls the design system's own
 * `useCurrentUser`, and SH894 established that Shio's provider is a deliberate local variant —
 * typed on `ShCurrentUser`, bound to `ShUserService`. A local variant of a context is a
 * *different context object*, so the shared menu found no provider and threw
 * `useCurrentUser must be used within UserProvider`, blanking the entire shell behind the error
 * boundary. Nothing in `tsc` says this; the first render did.
 *
 * The seam the package left is `fetchUser`, so Shio's service feeds the package's provider and
 * `ShCurrentUser` is structurally assignable to `VigUser`. Shio's own provider is deliberately
 * **not** also mounted here: nothing under this tree reads it yet, and a second provider means a
 * second fetch of the same endpoint. The first Block AR surface that needs Shio's user context
 * is where that decision gets made properly — SH920 carries it.
 *
 * `ReviewPendingProvider` is absent for the same reason. The rail renders **sections only**, so
 * SH207's badge has nowhere to appear until SH889 wires the command palette; mounting the
 * provider now would be a fetch nothing reads.
 */
export default function BentoRootPage() {
  const fetchUser = useCallback(() => shUserService.get(), [])
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
    <UserProvider fetchUser={fetchUser}>
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
            <BentoUserMenu
              accountRoute={ROUTES.ADMIN_USERS}
              logoutUrl={ROUTES.LOGOUT}
              onOpenShortcuts={() => setShortcutsOpen(true)}
            />
          </header>

          <BentoNavRail groups={groups} homeRoute={ROUTES.BENTO} />

          <div className="bento-rail-gutter">
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
    </UserProvider>
  )
}
