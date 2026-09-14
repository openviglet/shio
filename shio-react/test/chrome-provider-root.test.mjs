/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { createElement as h } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { Outlet, Route, Routes, StaticRouter } from "react-router-dom"
import {
  AdaptiveSectionCard,
  SectionCardChromeProvider,
  useSectionChrome,
} from "@viglet/viglet-design-system/bento"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH958 — one chrome provider above the router, and no route carrying its own.
 *
 * <p>The design system's `SectionChrome` context defaults to `console`. App.tsx paid for that on
 * every route: twenty-one elements, and one more in `graphql.routes.tsx`, each wrapped in
 * `SectionCardChromeProvider chrome="bento"`. The wrappers were not decoration — a surface mounted
 * without one rendered `AdaptiveSectionCard` and `EmptyState` in the console look — and **nothing
 * failed when a route forgot it**, because the wrong chrome renders perfectly well.</p>
 *
 * <p>So the provider moved above `<Routes>`, and this holds both halves: the arrangement in the
 * source, and the one fact the arrangement depends on and a reading cannot prove — that a context
 * mounted above the router reaches an element React Router renders, through a nested route's
 * `Outlet`, with no wrapper of its own.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")
const APP = stripComments(readFileSync(join(SRC, "App.tsx"), "utf8"))
const OPEN = /<SectionCardChromeProvider\s+chrome="bento"\s*>/g

/** Reads the chrome where a route element would, and prints it. */
function ChromeProbe() {
  return h("output", null, useSectionChrome())
}

/** A tree shaped like App.tsx's: a shell route with an Outlet, and a leaf element under it. */
function routed(leaf, { provided }) {
  const routes = h(Routes, null,
    h(Route, { path: "/bento", element: h("main", null, h(Outlet)) },
      h(Route, { path: "content", element: leaf })))
  const tree = provided ? h(SectionCardChromeProvider, { chrome: "bento" }, routes) : routes
  return renderToStaticMarkup(h(StaticRouter, { location: "/bento/content" }, tree))
}

test("SH958: App.tsx mounts one chrome provider, and it encloses the router", () => {
  const opens = [...APP.matchAll(OPEN)]
  assert.equal(opens.length, 1,
    `SH958: App.tsx opens ${opens.length} bento chrome providers. One belongs above <Routes>; a`
      + " second is a route wrapping itself again, which is the per-route arrangement this replaced"
      + " — and the next route added without that wrapper would read the console default.")

  const provider = opens[0].index
  const routesOpen = APP.indexOf("<Routes>")
  const routesClose = APP.lastIndexOf("</Routes>")
  const providerClose = APP.lastIndexOf("</SectionCardChromeProvider>")
  assert.ok(provider < routesOpen && routesClose < providerClose,
    "SH958: the provider does not enclose <Routes>, so some route elements render outside it and"
      + " get the package's console default without anything saying so.")
})

test("SH958: no route element wraps itself in a chrome provider", () => {
  const wrapped = [...APP.matchAll(/element=\{[\s\S]*?\}\s*\/?>/g)]
    .map(([element]) => element)
    .filter((element) => /SectionCardChromeProvider/.test(element))
  assert.deepEqual(wrapped, [],
    "SH958: a route element carries its own chrome provider again. The root one already covers it;"
      + " a local one says a route has to remember, which is exactly what a new route forgets.")

  const routes = join(SRC, "app", "routes")
  for (const file of readdirSync(routes).filter((f) => f.endsWith(".tsx"))) {
    assert.doesNotMatch(stripComments(readFileSync(join(routes, file), "utf8")), /SectionCardChromeProvider/,
      `SH958: app/routes/${file} wraps a route in a chrome provider. Route modules are spread into`
        + " App.tsx's <Routes>, so they are under the root provider already.")
  }
})

test("SH958: a route element with no wrapper of its own reads bento through the router", () => {
  assert.equal(routed(h(ChromeProbe), { provided: true }), "<main><output>bento</output></main>",
    "SH958: a provider above <Routes> does not reach an element rendered through a nested route's"
      + " Outlet. Every surface would then take the console default, which is the look Block AW"
      + " retires, and no route wrapper is left to hide it.")

  // What the chrome actually changes, through the same arrangement: the frosted section.
  const card = h(AdaptiveSectionCard, null,
    h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "T" }),
    h(AdaptiveSectionCard.Content, null, h("span", null, "body")))
  assert.match(routed(card, { provided: true }), /bento-glass/,
    "SH958: an AdaptiveSectionCard mounted as a route element under the root provider renders"
      + " something other than the bento section.")
})

test("SH958: without the provider the package still answers console, which is why it is there", () => {
  // The control, and the note for SH963. The day this fails the design system's default is bento
  // (VDS145): the root provider has become a no-op, and SH963 is the line that deletes it.
  assert.equal(routed(h(ChromeProbe), { provided: false }), "<main><output>console</output></main>",
    "SH958: with no provider the chrome is no longer `console`. If the package's default is now"
      + " bento, the root provider in App.tsx does nothing — delete it under SH963 rather than"
      + " keeping a wrapper that decides nothing.")
})
