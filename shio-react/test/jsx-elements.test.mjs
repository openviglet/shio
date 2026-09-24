/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { test } from "node:test"

import { endOfOpeningTag, findElements } from "./support/jsx-elements.mjs"

/**
 * SH928 — six readers scan JSX through this helper and none of them scanned the helper.
 *
 * `findElements` is the shared answer to "which `<Tag>`s does this file render, and what is inside
 * them". `bento-a11y`, `bento-bespoke-slice`, `bento-browser`, `bento-site-slice` and
 * `spa-contexts` all ask it. It had no test of its own, and it was wrong: `bodyOf` counted
 * every `<Tag` before the next `</Tag>` as a nested open, self-closing children included, so a
 * container holding thirty of them was short thirty closers and was dropped.
 *
 * ## Why the direction of the error is the whole argument
 *
 * A scanner that drops an element **under-reports**. A lint asking "does any of these carry
 * `onClick`" answers no about a container it never saw, and a reviewer reads that as coverage.
 * That is the same failure SH893 filed this file against in the first place — a regex fooled by
 * the `>` in `=>` — one level up, which is why both cases are asserted here together.
 *
 * None of the six readers was giving a wrong answer when this was found. Their tags did not happen
 * to nest self-closed inside themselves, which is a property of a day's markup and not of the
 * tool, and it is exactly the kind of luck that expires without anybody noticing.
 *
 * ## Why the fixtures are written out rather than read off `App.tsx`
 *
 * The measurement that found this was `findElements(App.tsx, "Route")` returning 48 of 50, losing
 * the console tree and the bento tree. `App.tsx` is the right place to *find* the defect and the
 * wrong place to assert it: SH909 and SH910 are rewriting that file's route trees right now, so an
 * assertion pinned to it would go red on a change that has nothing to do with this scanner. The
 * shape is what matters, so the shape is what is written down.
 */

/** The smallest case that shows it: a container whose children self-close under its own tag. */
const SELF_CLOSING_CHILDREN = `<P attr><P /><P /></P>`

/** The same shape at the size it actually occurs in, with a prop holding an element and an arrow. */
const ROUTE_TREE = `
<Routes>
  <Route path="/" element={<Navigate to="/bento" replace />} />
  <Route path="/bento" element={<Shell onSelect={(id) => open(id)} />}>
    <Route index element={<Home />} />
    <Route path="content" element={<List />} />
    <Route path="*" element={<NotFound />} />
  </Route>
  <Route path="*" element={<NotFound />} />
</Routes>
`

test("SH928: a container is found even when every child self-closes under its own tag", () => {
  const found = findElements(SELF_CLOSING_CHILDREN, "P")

  assert.equal(found.length, 3,
    "SH928: findElements returned " + found.length + " of 3. Before the fix it returned the two"
      + " children and dropped the parent, because bodyOf counted a self-closing child as an open"
      + " that still needed a </P>.")

  const [parent, ...children] = found
  assert.equal(parent.selfClosing, false, "SH928: the parent came back marked self-closing")
  assert.equal(parent.attrs.trim(), "attr", "SH928: the parent's attributes were mis-sliced")
  assert.equal(parent.body, "<P /><P />",
    "SH928: the parent's body is both children verbatim. A body that stops at the first child is"
      + " the same defect with the depth off by one instead of the element missing.")
  assert.ok(children.every((child) => child.selfClosing && child.body === ""),
    "SH928: the children stopped being reported as self-closing with empty bodies")
})

test("SH928: every raw occurrence in a route tree comes back, containers included", () => {
  const raw = ROUTE_TREE.match(/<Route(?=[\s/>])/g) ?? []
  const found = findElements(ROUTE_TREE, "Route")

  assert.equal(found.length, raw.length,
    `SH928: ${found.length} of ${raw.length} <Route occurrences came back. This is the App.tsx`
      + " measurement in miniature — 48 of 50, and the two it lost were the only two containers.")

  const containers = found.filter((element) => !element.selfClosing)
  assert.equal(containers.length, 1,
    `SH928: ${containers.length} containers, expected 1. Zero is the pre-fix answer, and it is the`
      + " one that makes a route-tree lint pass by never seeing a tree.")
  assert.match(containers[0].attrs, /path="\/bento"/,
    "SH928: the container found was not the /bento tree")
  assert.equal((containers[0].body.match(/<Route(?=[\s/>])/g) ?? []).length, 3,
    "SH928: the container's body should hold its three children and stop at its own </Route>,"
      + " rather than running on to the sibling that follows it.")
})

test("SH928: a genuine nested container still balances", () => {
  const found = findElements(`<P a><P b><P /></P></P>`, "P")

  assert.equal(found.length, 3, "SH928: real nesting lost an element")
  assert.equal(found[0].body, "<P b><P /></P>",
    "SH928: skipping self-closing opens must not also skip the ones that do need a closer —"
      + " an outer container whose body stops at the inner </P> is over-reporting instead.")
  assert.equal(found[1].body, "<P />", "SH928: the inner container's body is wrong")
})

test("SH893/SH928: a `>` inside a prop still does not end the opening tag", () => {
  /*
   * The defect this file was written for, kept here because SH928 changed the function that
   * decides it. `bodyOf` now resolves every candidate open through `endOfOpeningTag`, so a
   * regression in the arrow-in-a-prop case would newly show up as *dropped containers* as well as
   * mis-sliced bodies — two symptoms, one cause, and this is the assertion that names the cause.
   */
  const [button] = findElements(`<Button onClick={() => open()}><Search /></Button>`, "Button")

  assert.ok(button, "SH893: the button was dropped entirely")
  assert.equal(button.body, "<Search />",
    "SH893: the body started mid-prop, so a check asking whether it holds readable text would"
      + " answer yes on the handler's own name.")
  assert.match(button.attrs, /onClick=\{\(\) => open\(\)\}/, "SH893: the attributes were truncated")
})

test("SH928: an opening tag that never ends yields nothing rather than a guess", () => {
  /*
   * `bodyOf` asks `endOfOpeningTag` about children now, so it inherits that scanner's one
   * unanswerable case: an unterminated quote runs to the end of the file. The contract the rest of
   * this helper keeps is that ambiguity returns nothing — a finding missed loudly, because the
   * count moves — rather than an element parsed on a guess.
   */
  assert.equal(endOfOpeningTag(`<P b="x`, 2), -1, "SH928: an unterminated opening tag reported an end")
  assert.deepEqual(findElements(`<P a><P b="x</P>`, "P"), [],
    "SH928: an unparseable child let its parent through with a body sliced at a `</P>` that is"
      + " inside a string.")
})
