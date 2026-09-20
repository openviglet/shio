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
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1039 — the curator's half of *what may be inserted into a page, and where*.
 *
 * SH1038 made a section selectable, movable and removable. Adding one needed an answer the console
 * did not have: the shared section types reached it as post-type names in a picker, never as
 * something to place. The server now states the catalogue per container; this asserts the four
 * properties of the screen that consumes it, each of which a refactor breaks silently.
 *
 * ## Why source text and not a rendered component
 *
 * This workspace's convention, and the reason is the same one `bento-universal-editor.test.mjs`
 * gives: there is no component harness here, and the things worth holding are *structural* — which
 * call happens before which, and which branch a control is rendered in. A jsdom render would prove
 * the markup and not the ordering, which is the half that corrupts content when it is wrong.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const PAGE = stripComments(
  readFileSync(join(HERE, "..", "src", "app", "editor", "visual-editor.page.tsx"), "utf8"))
const SERVICE = stripComments(readFileSync(
  join(HERE, "..", "src", "services", "universal-editor", "universal-editor.service.ts"), "utf8"))

test("SH1039: the catalogue is read from the agent surface, as the opt-in it is", () => {
  assert.match(SERVICE, /\/v2\/agent\/editable/,
    "the catalogue is read off the RENDERED page — which container composes sections and what its "
    + "template declares it accepts — and the console cannot read the framed document, because it "
    + "is another origin. A second implementation here would answer what the model permits, which "
    + "is a different question that agrees only by accident (SH583)")
  assert.match(SERVICE, /include:\s*"insertable"/,
    "an opt-in, because the fields of every accepted type are what a properties panel needs and "
    + "what a caller asking which fields can I edit did not ask for (P3)")
  assert.match(SERVICE, /address:\s*`id:\$\{pageId\}`/,
    "SH172's escape hatch: the console holds an id and the agent surface is addressed")
})

test("SH1039: an insert creates the section before the page names it", () => {
  const create = PAGE.indexOf("editorService.create(")
  const patch = PAGE.indexOf("editorService.patch(pageId")
  assert.ok(create > 0 && patch > 0, "both writes have to be there for the order to mean anything")
  assert.ok(create < patch,
    "an insert is two writes, and the order decides what a failure leaves behind: a new post the "
    + "page does not name is something a curator finds in the content browser, while a page naming "
    + "a post that was never created is a dangling reference on a live page")
})

test("SH1039: the reference is spelled the way the array already spells its entries", () => {
  assert.match(PAGE, /startsWith\("\/"\)/,
    "a friendly URL where the neighbours are paths, the id where they are ids. Both resolve "
    + "(SH181 tries the id first), so the cost of mixing them is not a broken page - it is one "
    + "line of the file projection unlike its siblings, which is a P5 answer and not a P6 one")
})

test("SH1039: the rail is offered only where exactly one container could receive it", () => {
  assert.match(PAGE, /catalogue\.containers\.length === 1/,
    "`sh:component-selected` says a section's index and how many its container holds, and NOT "
    + "which container. So on a page composing two ordered lists the console cannot tell which one "
    + "an insert belongs to, and a rail offered there would write into whichever it guessed - the "
    + "rule the rest of this screen follows, that a control which would write the wrong thing is "
    + "worse than none")
})

test("SH1039: the rail is reachable with nothing selected, which is the case it exists for", () => {
  // A page that composes no sections has nothing to click. A rail that only appeared beside a
  // selection would therefore never appear on the emptiest page, which is precisely the one where
  // a curator needs to be told what fits.
  const uses = [...PAGE.matchAll(/<SectionInserter\b/g)]
  assert.equal(uses.length, 2,
    "one beside a selected section and one in the no-selection branch; if this is 1, check which "
    + "branch lost it - the empty page is the one that matters")
  assert.match(PAGE, /relative=\{false\}/,
    "and with nothing to be relative to, the section goes last - which on an empty page is first")
})

test("SH1039: a type the instance has not got is shown and not offered", () => {
  assert.match(PAGE, /type\.provisioned \? \(/,
    "the server reports it rather than dropping it, because the page declares the type and a "
    + "shorter catalogue would be a quieter lie than an honest gap (SH169). The console honours "
    + "that: it says so, and does not offer a button that would create a post of a type that does "
    + "not exist")
})
