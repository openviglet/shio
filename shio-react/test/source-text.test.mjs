/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1118 — the reader that thirty-one suites see the source through, read for once itself.
 *
 * <p>Every other test in this directory asserts something *about the console* through
 * `stripComments`. None asserted anything about `stripComments`, which is why a body that deleted
 * code from a string literal survived three sightings of its own symptom: SH823, SH890 and SH888
 * each found a scan reading its own prose, fixed it by reaching for the strip, and never asked what
 * the strip removed. SH1049 measured one Playwright glob cutting `console-check.mjs`'s stripped
 * view almost in half and turning four assertions green over nothing, and the comment written to
 * explain the hazard caused it a second time an hour later.</p>
 *
 * <p>A silenced scan is invisible by construction — it passes. So these are the assertions that
 * would have caught it, at the boundary it crossed.</p>
 */
import assert from "node:assert/strict"
import { test } from "node:test"

import { stripComments } from "./support/source-text.mjs"

/** What a scan is looking for, planted after the thing that used to swallow it. */
const CODE = "const theThingAScanLooksFor = 1"

/**
 * A closer somewhere below, which is what turns a string into a deletion.
 *
 * <p>This is the half that makes the defect real and the half a planted fixture forgets: an opener
 * with nothing after it removes nothing, because the old body's regex was non-greedy and simply
 * found no match. It needs a `*&#47;` further down to close on — and every file in this repository
 * has one, because every file here carries doc comments. The first draft of this test left it out
 * and passed against the broken stripper.</p>
 */
const LATER_DOC = "/** an ordinary doc comment, further down the file */"

test("SH1118: a block-comment opener inside a string does not swallow the rest of the file", () => {
  // The exact shapes measured in this repository before the fix.
  const planted = [
    ["a route wildcard", `export const P = [\`\${c}/*\`, \`/:t\${c}/*\`]`],
    ["a Playwright glob", `await page.route("**/api/v2/**", handler)`],
    ["a robots.txt fixture", `const robots = "User-agent: *\\nDisallow: /*"`],
    ["a CSS selector", `assert.equal(declarationsOf("a{color:red}/* c"), x)`],
  ]

  for (const [what, line] of planted) {
    const source = `${line}\n${CODE}\n${LATER_DOC}\n`
    assert.match(stripComments(source), new RegExp(CODE),
      `SH1118: ${what} opened a block comment, and everything from it to the next closer was `
      + "deleted from the scanned text. A scan for an absence then matches an empty string and "
      + "passes — which is how this reader agreed with four assertions over nothing. Measured "
      + "before the fix: 19 such spans and 77,285 characters across this repository's JS sources, "
      + "the worst hiding 656 lines behind a robots.txt fixture.")
  }
})

test("SH1118: a comment that opens and never closes throws, naming the id", () => {
  const error = (() => {
    try {
      stripComments(`/* opened and never closed\n${CODE}\n`, "planted.ts")
      return null
    } catch (e) {
      return e
    }
  })()

  assert.ok(error,
    "SH1118: a file that ends inside a comment is the one case this cannot read, and returning "
    + "the tail as an empty string is agreeing with every assertion over it. A reader that cannot "
    + "see the file has to say so.")
  assert.match(error.message, /SH1118/,
    "SH1118: the failure carries the id, or whoever reads the red run has the nothing that the "
    + "three sightings before it had.")
  assert.match(error.message, /line 1 of planted\.ts/,
    "SH1118: and where, because the label is the only thing separating 'this file' from 'one of "
    + "the three hundred and sixty-one this walk reads'.")
})

test("SH1118: and it still removes every comment, which is what it is for", () => {
  const cases = [
    ["a one-line JSDoc", "/** doc */\nconst a = 1", "doc"],
    ["a multi-line JSDoc", "/**\n * doc\n */\nconst a = 1", "doc"],
    ["a line comment", "// doc\nconst a = 1", "doc"],
    ["a trailing block comment", "const a = 1 /* doc */", "doc"],
    ["a leading block comment with code after it", "/* doc */ const a = 1", "doc"],
    ["a JSX comment", "<div>{/* doc */}</div>", "doc"],
    ["an apostrophe in prose", "/* the curator's own, don't touch: doc */\nconst a = 1", "doc"],
  ]

  for (const [what, source, prose] of cases) {
    assert.doesNotMatch(stripComments(source), new RegExp(prose),
      `SH823: ${what} survived the strip, so a scan asserting this file does not say something `
      + "would fail against the paragraph explaining that it does not — the defect this whole "
      + "helper exists for.")
  }

  assert.match(stripComments("/* doc */ const a = 1"), /const a = 1/,
    "SH1118: and the code after a closer stays. Dropping the whole line instead is how the first "
    + "attempt at this fix failed `fetch-cancellation.test.mjs`, whose planted file is exactly "
    + "that shape.")
})
