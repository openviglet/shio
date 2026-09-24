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
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { stripComments } from "./support/source-text.mjs"

/**
 * SH958, closed by SH963 — no chrome provider anywhere, and a routed card still renders bento.
 *
 * <p>SH958 moved the one `SectionCardChromeProvider chrome="bento"` above `<Routes>`, because the
 * package's chrome defaulted to `console` and a route that forgot its wrapper rendered the old look
 * with nothing failing. Its last test was the control: without the provider the package still
 * answered console, and "the day this fails ... SH963 is the line that deletes it".</p>
 *
 * <p>VDS146 removed the switch from the package (2026.3.11), and SH963 deleted the provider. What
 * this holds now: nothing in the console declares a chrome, and a card rendered as a route element,
 * through a nested route's `Outlet`, is the bento section with nothing above it.</p>
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const SRC = join(HERE, "..", "src")

/** A tree shaped like App.tsx's: a shell route with an Outlet, and a leaf element under it. */
function routed(leaf) {
  const routes = h(Routes, null,
    h(Route, { path: "/bento", element: h("main", null, h(Outlet)) },
      h(Route, { path: "content", element: leaf })))
  return renderToStaticMarkup(h(StaticRouter, { location: "/bento/content" }, routes))
}

/** Every `.tsx` under src. */
function sources(dir = SRC, found = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name)
    if (entry.isDirectory()) sources(path, found)
    else if (entry.name.endsWith(".tsx")) found.push(path)
  }
  return found
}

test("SH963: no console source declares a section chrome", () => {
  const declaring = sources()
    .filter((path) => /SectionCardChromeProvider|useSectionChrome/.test(
      stripComments(readFileSync(path, "utf8"))))
    .map((path) => path.slice(SRC.length + 1).replace(/\\/g, "/"))
  assert.deepEqual(declaring, [],
    "SH963: a console file names the chrome provider or its hook, which VDS146 removed from the "
      + "package. There is one chrome; nothing has to choose it.")
})

test("SH963: a card rendered as a route element is the bento section, with no provider above", () => {
  const card = h(AdaptiveSectionCard, null,
    h(AdaptiveSectionCard.StaticHeader, { icon: () => null, title: "T" }),
    h(AdaptiveSectionCard.Content, null, h("span", null, "body")))

  assert.match(routed(card), /bento-glass/,
    "SH963: an AdaptiveSectionCard mounted through the router renders something other than the "
      + "bento section. SH958's provider is gone, so the package alone has to answer bento.")
})
