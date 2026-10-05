/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { createElement as h } from "react"
import { renderToStaticMarkup } from "react-dom/server"
// react-router-dom 7 exports StaticRouter from the root; the `/server` subpath it had in v6 is
// not in its exports map any more and resolving it fails the whole file, not one test.
import { StaticRouter } from "react-router-dom"
import { UserProvider } from "@viglet/viglet-design-system"
import { BentoNavRail, BentoUserMenu } from "@viglet/viglet-design-system/bento"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH888 — the bento route tree exists, beside the console one, with no sidebar provider.
 *
 * ## The failure this is really about
 *
 * The shell came up **blank** on its first render, behind the error boundary, with
 * `useCurrentUser must be used within UserProvider` in the console. `BentoUserMenu` calls the
 * *design system's* `useCurrentUser`, and SH894 established that Shio's `UserProvider` is a
 * deliberate local variant — typed on `ShCurrentUser`, bound to `ShUserService`. A local variant
 * of a context is a **different context object**, so mounting Shio's around a shared component
 * that reads the package's satisfies nothing.
 *
 * `tsc` is silent about it: both are called `UserProvider`, both take children, and the mismatch
 * only exists at runtime in `useContext`. So is every import scan. The only thing that says so is
 * a render — which is why this file renders: these are wrappers, not pages, and
 * `react-dom/server` costs a millisecond.
 *
 * SH920 carries the consequence: the two trees now mount different user contexts, and every one
 * of Block AR's fourteen surfaces crosses that seam.
 *
 * ## What is asserted in source rather than rendered
 *
 * `bento.root.page.tsx` is TSX, and node's type stripping does not handle JSX, so the page cannot
 * be imported here. The composition is therefore checked two ways: the *behaviour* that broke is
 * rendered from the package's own components, and the *decision* that broke it — which provider
 * the shell imports — is read out of the file. Neither alone would have caught it.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

// Comments stripped: these assertions are largely "this file must NOT mention X", and the file
// explains at length why it does not. See test/support/source-text.mjs.
const shellSource = stripComments(readFileSync(join(SRC, "app", "system", "bento.root.page.tsx"), "utf8"))
const appSource = readFileSync(join(SRC, "App.tsx"), "utf8")
const navSource = stripComments(readFileSync(join(SRC, "app", "system", "bento-nav.ts"), "utf8"))

const render = (node) => renderToStaticMarkup(
  h(StaticRouter, { location: "/bento" }, node))

test("SH888: the shared user menu needs the package's provider, and nothing else satisfies it", () => {
  const menu = h(BentoUserMenu, { accountRoute: "/console/admin/users", logoutUrl: "/logout" })

  // The control: with no provider at all it throws, which is what a blank shell looked like.
  assert.throws(() => render(menu), /useCurrentUser/,
    "SH888: BentoUserMenu no longer requires a user context. If the package made it optional,"
      + " the shell's provider may be unnecessary — but check before removing it, because the"
      + " symptom of getting this wrong is an entirely blank page behind the error boundary.")

  // And with the package's own provider it renders.
  const withProvider = render(
    h(UserProvider, { fetchUser: () => Promise.resolve({ username: "u", firstName: "U", lastName: "U", email: "u@x", admin: true }) }, menu))
  assert.ok(withProvider.length > 0,
    "SH888: the user menu rendered nothing even with the package's provider mounted")
})

test("SH888: the shell mounts a provider that satisfies the package's own hook", () => {
  /*
   * This assertion used to demand the *package's* `UserProvider` by import path, because when
   * SH888 shipped that was the only thing which satisfied `BentoUserMenu`. SH920 then removed the
   * seam rather than living with it — `@/contexts/user.context` now wraps the package's provider
   * and republishes its value, so one mount serves both hooks from one fetch — and this test went
   * red on the commit that did it. Which is what it was for: it named the decision instead of
   * letting the shell quietly go back to a provider that satisfies nothing.
   *
   * What has to stay true is narrower than the import path, so that is what it says now.
   */
  assert.match(shellSource, /<UserProvider>/,
    "SH888: the bento shell mounts no user provider at all. BentoUserMenu reads the package's"
      + " context, and a shell without it renders entirely blank behind the error boundary — no"
      + " type error, no failed request, nothing in a diff.")
  assert.match(shellSource, /import \{ UserProvider \} from "@\/contexts\/user\.context"/,
    "SH888: the shell no longer mounts Shio's provider. Since SH920 that is the one that also"
      + " mounts the package's — going back to importing the package's here directly would put"
      + " the two trees on two contexts again, which is what SH920 removed.")
  assert.doesNotMatch(shellSource, /fetchUser/,
    "SH888: the shell configures the provider itself again. How a user is fetched is not a"
      + " shell's business, and a second shell would copy it.")
})

test("SH1213: the header's leading edge carries the mark, the wordmark and the palette trigger", () => {
  // The shell's contract (vds-shell) marks the mark-and-wordmark and the palette trigger required
  // in headerStart. Shio passed only headerEnd, and the header named no product at all.
  const start = /headerStart=\{([\s\S]*?)\n\s{8}\}\n\s{8}headerEnd=/.exec(shellSource)
  assert.ok(start, "SH1213: BentoShell is handed no headerStart, so the header names no product")
  assert.match(start[1], /<ShLogo\b/, "SH1213: the header's leading edge carries no Shio mark")
  assert.match(start[1], />Shio</, "SH1213: the header's leading edge carries no wordmark")
  assert.match(start[1], /<BentoPaletteTrigger\b/,
    "SH1213: the header has no palette trigger — below md, where the rail hides, it is the pointer's"
      + " only way to a deep area")
})

test("SH1216: the header's trailing edge carries the ground control, before the user menu", () => {
  // vds-shell marks the ground (light, dark, system) required on the trailing edge. The console
  // shell had none, so the theme could be chosen on the login page and nowhere after it.
  const end = /headerEnd=\{([\s\S]*?)\n\s{8}\}\n/.exec(shellSource)
  assert.ok(end, "SH1216: BentoShell is handed no headerEnd")
  assert.match(end[1], /<ModeToggle\s*\/>[\s\S]*<BentoUserMenu\b/,
    "SH1216: the header has no theme control, or it sits after the user menu the contract puts last")
})

test("SH888: no sidebar provider anywhere under the bento tree", () => {
  assert.doesNotMatch(shellSource, /SidebarProvider|SidebarInset|SidebarTrigger/,
    "SH888: the bento shell reaches for sidebar chrome. The package ships no provider for it on"
      + " purpose — the rail is fixed, one width, and hidden below md, so a provider would be"
      + " state nothing reads — and this is the incompatibility that made a parallel tree"
      + " necessary rather than a migration in place.")

  // The rail is `position: fixed` and takes no space in the flow, so content that does not clear
  // it renders underneath. Since SH946 the package's BentoShell reserves the gutter itself when it
  // is handed the rail, so what must hold is that the rail goes to the shell, not beside it.
  assert.match(shellSource, /<BentoShell[\s\S]*?rail=\{<BentoNavRail\b/,
    "SH888/SH946: the rail is not handed to BentoShell, so nothing reserves its gutter and the "
      + "routed page renders beneath the fixed rail")
})

/**
 * SH909 — this check's subject changed, so the check did.
 *
 * It asked whether the bento route was nested inside the console one, because that would put the
 * whole bento shell inside a `SidebarProvider` — §LIV.3's arrangement that cannot work. **There is
 * no console route any more**: SH909 made bento the default and left the old prefix as a redirect,
 * so the original assertion failed on its own premise ("App.tsx no longer opens a route at
 * ROUTES.CONSOLE"), which is the honest way for a check to notice its world moved.
 *
 * The rule that survives is the one it was always about, and it is now stronger: **nothing in this
 * application mounts a sidebar around the shell's tree.** Nesting was one way to reach that state;
 * a wrapper somebody adds above `<Routes>` is another, and the original check could not see it.
 */
test("SH909: nothing in the app mounts sidebar chrome around the bento tree", () => {
  assert.match(appSource, /<Route path=\{ROUTES\.BENTO\}/,
    "SH888: App.tsx no longer mounts a route at ROUTES.BENTO")

  // Stripped, because this file's own comments explain at length what the console shell mounted
  // and why the two could not nest — the third time a "must not mention X" scan in this directory
  // has matched the paragraph explaining X (see `source-text.mjs`).
  assert.doesNotMatch(stripComments(appSource), /SidebarProvider|SidebarInset|SidebarTrigger|ConsoleRootPage/,
    "SH909: App.tsx mounts sidebar chrome again. The bento shell ships no sidebar provider on"
      + " purpose — the rail is fixed, one width, hidden below md — and a bento component that"
      + " reaches for that context inside a provider renders blank with no error. This was the"
      + " incompatibility that made a parallel tree necessary (§LIV.3); the tree is gone and the"
      + " incompatibility is not.")
})

test("SH888: the rail's segment is reserved, or /bento reads as a tenant", () => {
  const consts = readFileSync(join(SRC, "app", "routes.const.ts"), "utf8")
  const reserved = /RESERVED_TOP_SEGMENTS\s*=\s*new Set\(\[([\s\S]*?)\]\)/.exec(consts)

  assert.ok(reserved, "SH888: RESERVED_TOP_SEGMENTS is no longer a Set literal")
  assert.match(reserved[1], /"bento"/,
    "SH888: `bento` is not reserved, so readTenantSegment() reads /bento as a tenant named"
      + " \"bento\" and prefixes every ROUTES.* the shell renders with it — the rail would still"
      + " draw, pointing at routes that do not exist (SH887 hit this exact shape).")
})

test("SH888: the rail renders the sections Shio declares, and only those with a hub route", () => {
  // BentoNavRail filters `groups` down to sections carrying an `areaRoute` and draws one icon
  // each — items are the command palette's, which SH889 fills in. A section without areaRoute is
  // therefore invisible, silently, which is worth knowing before wondering where an icon went.
  const groups = [
    { section: { id: "shown", labelKey: "sidebar.content", icon: () => null, areaRoute: "/console/content" }, items: [] },
    { section: { id: "hidden", labelKey: "sidebar.administration", icon: () => null }, items: [] },
  ]
  const html = render(h(BentoNavRail, { groups, homeRoute: "/bento" }))

  assert.ok(html.includes('href="/console/content"'),
    "SH888: a section with an areaRoute did not reach the rail")
  assert.ok(html.includes('href="/bento"'),
    "SH888: the rail no longer draws its Home link, which it renders itself from homeRoute")
  assert.equal((html.match(/<a /g) ?? []).length, 2,
    "SH888: the rail drew a link for a section with no areaRoute. If the package started"
      + " rendering those, Shio's nav can stop carrying a route for every section — but until"
      + " then a section without one is invisible and this is where that is written down.")
})

test("SH888: Shio's nav declares sections both readers will share", () => {
  assert.match(navSource, /BENTO_NAV/,
    "SH888: the nav declaration is gone. The package holds no default — an entry appears when"
      + " its route does, and the package cannot read Shio's feature model.")

  for (const key of ["sidebar.content", "sidebar.administration"]) {
    assert.ok(navSource.includes(key),
      `SH888: the nav no longer declares ${key}. The rail calls t() on labelKey, so a key that`
        + " does not exist renders as the key itself — visible, but only to somebody looking.")
  }

  // SH889 landed and this assertion did what it said it would: it expected empty item lists and
  // failed the moment they were filled, naming its own replacement. The population is now
  // `bento-nav.test.mjs`'s subject; what stays here is the shell's own requirement, which is that
  // the rail can find sections at all.
  assert.match(navSource, /areaRoute/,
    "SH888: no section declares an areaRoute, so BentoNavRail filters every one of them out and"
      + " draws nothing but Home — silently, because a section without a hub route is not an"
      + " error to the rail, it is simply invisible.")
})
