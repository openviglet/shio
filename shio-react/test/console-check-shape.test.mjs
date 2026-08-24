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
 * SH897 — the browser check's own shape, guarded from the gate it does not run in.
 *
 * ## Why this file exists at all
 *
 * `e2e/console-check.mjs` needs a browser, a dev server and a live backend, so it is not part of
 * `pnpm test`. SH897's whole deliverable lived there, which meant it shipped with **no assertion
 * carrying its id** — and `cli/test/assertion-debt.test.mjs` said so, in the run right after.
 * That is SH527's rule doing its job, and this is the payment.
 *
 * ## What it guards, which is the mistake that was actually made
 *
 * SH897's first frost check accepted *either* a `backdrop-filter` **or** a non-zero
 * `border-radius`. The second clause made it vacuous: `rounded-3xl` is a Tailwind class on the
 * component itself, so the radius is 24px whether or not `bento.css` is loaded. Deleting the
 * stylesheet and re-running was the only thing that found it — the page rendered flat, borderless
 * and unstyled, `bento-root.png` showed it plainly, and the probe reported **ok**.
 *
 * A source scan cannot repeat that experiment. What it can do is hold the shape the experiment
 * settled on, so the fallback cannot come back on a tired afternoon as a "flaky in headless"
 * fix — which is exactly how a vacuous check gets written the second time.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CHECK = join(HERE, "..", "e2e", "console-check.mjs")

const source = stripComments(readFileSync(CHECK, "utf8"))

test("SH897: the browser check visits the bento route tree", () => {
  assert.match(source, /bento:\s*true/,
    "SH897: no route in console-check.mjs is marked `bento: true`, so the one script that opens a"
      + " real page never opens a bento one. It would run green through the whole of Block AR"
      + " while never visiting a migrated surface — worse than not running, because a green check"
      + " is counted as evidence.")
  assert.match(source, /path:\s*"\/bento"/,
    "SH897: the /bento route is gone from the probe list")
  assert.match(source, /checkBentoSurface/,
    "SH897: the bento probe no longer runs the bento assertions")
})

test("SH897: the frost check reads backdrop-filter, and accepts nothing in its place", () => {
  // From the element lookup to the close of the `else` branch that inspects it. Anchored on the
  // `const glass` line and bounded by the next line that is exactly four spaces and a brace,
  // which is where that block ends — a lazy `[\s\S]*?` to a bare `}` stops at the first nested
  // one and captured a region with no style read in it at all, which is how the first draft of
  // this test failed against source that was correct.
  const start = source.indexOf("const glass = document.querySelector")
  assert.notEqual(start, -1, "SH897: the frosted-surface check is no longer recognisable")
  const end = source.indexOf("\n    }\n", start)
  assert.notEqual(end, -1, "SH897: could not find the end of the frosted-surface block")
  const frost = [source.slice(start, end)]

  assert.match(frost[0], /backdropFilter/,
    "SH897: the frost check no longer reads backdrop-filter. That property is the only thing on"
      + " the page that distinguishes bento.css being loaded from it being absent — every"
      + " `bento-*` class name is in the markup either way.")

  // The regression, named. `border-radius` comes from Tailwind's own `rounded-3xl`, so accepting
  // it is accepting a value that is there when the stylesheet is not.
  assert.doesNotMatch(frost[0], /borderRadius/,
    "SH897: the frost check consults border-radius again. It was removed because it made the"
      + " check pass on a page with no bento.css at all: `rounded-3xl` is on the component, so"
      + " the radius is 24px regardless. Verified by deleting the import — the probe said ok"
      + " while the screenshot showed flat, unstyled divs.\n"
      + "  If backdrop-filter is genuinely unreliable somewhere, find a property the stylesheet"
      + " alone sets. Do not widen this back into an OR.")
})

test("SH897: a probe with nothing to check reports it rather than passing quietly", () => {
  assert.match(source, /checkSaveBarMorph/,
    "SH897: the save-bar morph check is gone. It is the third of §LIV.12's three facts and the"
      + " one that arrives with Block AR's first migrated editor.")
  assert.match(source, /ran:\s*false/,
    "SH897: checkSaveBarMorph no longer distinguishes 'did not run' from 'passed'. This file's"
      + " own history is why that matters: SH238/SH659 found that a probe which skips keeps"
      + " `ok: true`, counts toward 'N/N probes ok', and leaves the exit code at 0.")
  assert.match(source, /probe\.morph/,
    "SH897: the morph's result is no longer recorded on the probe, so it reaches neither the"
      + " report nor stdout and nobody learns it never ran.")
})

test("SH897: no post editor is opened for a static file", () => {
  assert.match(source, /if \(type === "File"\) continue/,
    "SH897: the probe list generates a post-edit route for post-type `File` again. A static file"
      + " is not a post-unified row — /agent/find returns it, /api/v2/staticfile/{id} serves it,"
      + " and /api/v2/post-unified/{id} answers 404 — so the check fails on a defect in itself."
      + " The bare 404 it drove into is SH921.")
})

test("SH897: the reader is not vacuous — it reads the real script", () => {
  assert.ok(source.length > 10_000,
    `SH897: console-check.mjs read as ${source.length} characters, which is not the script — every`
      + " assertion above would be reporting a missing feature as a missing file")
  assert.match(source, /chromium/,
    "SH897: the script no longer launches a browser, so whatever this is checking, it is not the"
      + " one thing in this repository that opens a real page")
})
