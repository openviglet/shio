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

/**
 * SH1017 — the block editor, and the two things about it that are invisible on screen.
 *
 * The vocabulary rules run, because `lib/blocks.ts` is pure. What cannot be executed here — a
 * textarea's selection, a drag — is asserted against the source, and those assertions are about the
 * claims that a screenshot would not settle: that every mouse gesture has a keyboard path, that a
 * move is announced, and that a link is not a `prompt()`.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

const { BLOCK_TYPES, blankBlock, blocksFromPastedHtml, withMark, withoutMark } =
  await import("../src/lib/blocks.ts")

// The paste converter needs a DOM, because parsing HTML with anything else is the mistake it
// exists to avoid. jsdom is already a dev dependency here, so the real function runs rather than a
// reimplementation of it.
const { JSDOM } = await import("jsdom")
globalThis.DOMParser = new JSDOM().window.DOMParser

/**
 * Source with its comments removed.
 *
 * A scan for `prompt(` over raw source fails on the paragraph explaining why there is no
 * `prompt(` — which has now happened three times in this session's suites. The rule is about the
 * code, so the scan reads the code.
 */
const code = (source) => source
  .replace(/\/\*[\s\S]*?\*\//gu, "")
  .replace(/^\s*\/\/.*$/gmu, "")

test("SH1017: a new block carries the fields the server requires", () => {
  assert.equal(blankBlock("heading").level, 2,
    "SH1017: a heading with no level is refused by ShBlocks, so an editor that composes one "
      + "teaches its user a shape that does not exist — the insert would fail on save")
  assert.deepEqual(blankBlock("list").items, [""])
  assert.equal(blankBlock("list").ordered, false)
  assert.equal(typeof blankBlock("paragraph").key, "string")
  assert.notEqual(blankBlock("paragraph").key, blankBlock("paragraph").key,
    "SH1017: two blocks must never share a key — the server refuses it, and an editor would show "
      + "it as a paragraph that will not move")
})

test("SH1017: the editor offers exactly what the server accepts", () => {
  // The vocabulary is mirrored, not invented: an editor offering a ninth type would compose a
  // document every save refuses.
  assert.deepEqual([...BLOCK_TYPES].sort(),
    ["code", "embed", "heading", "image", "list", "paragraph", "quote", "reference"])
})

test("SH1017: a mark is the selection, sorted and never duplicated", () => {
  const block = { key: "b1", type: "paragraph", text: "one two three" }
  const once = withMark(block, { from: 4, to: 7, type: "em" })
  const twice = withMark(once, { from: 4, to: 7, type: "em" })

  assert.equal(twice.marks.length, 1,
    "SH1017: clicking Bold twice on one selection is one mark — the second would be a duplicate "
      + "the server stores and the renderer emits twice")

  const both = withMark(once, { from: 0, to: 3, type: "strong" })
  assert.deepEqual(both.marks.map((mark) => mark.from), [0, 4],
    "SH1017: sorted, so re-emitting the same marks does not make the form report itself dirty "
      + "(SH1011 compares a canonical serialization)")

  assert.equal(withoutMark(both, 0).marks.length, 1)
  assert.equal(withoutMark(withoutMark(both, 0), 0).marks, undefined,
    "SH1017: an empty marks list is absent rather than [], so an unmarked paragraph serializes "
      + "the way one written without marks does")
})

test("SH1017: pasted HTML becomes blocks, and carries no markup across", () => {
  const blocks = blocksFromPastedHtml(
    "<h2>Why</h2><p>Because.</p><ul><li>one</li><li>two</li></ul>"
      + "<p><span style=\"color:red\">styled</span></p>")

  assert.deepEqual(blocks.map((block) => block.type),
    ["heading", "paragraph", "list", "paragraph"],
    "SH1017: a paste from a word processor is a document. The widget this replaces had no paste "
      + "handling at all, so it arrived as the spans and inline styles that processor emits.")
  assert.equal(blocks[0].level, 2)
  assert.deepEqual(blocks[2].items, ["one", "two"])
  assert.equal(blocks[3].text, "styled",
    "SH1017: text is taken as text, so the span and its style are gone rather than stored")

  // A real script element is not a shape the model has a name for, so it produces no block at all
  // — the same rule the server's allow-list follows. (Text that *looks* like a tag is a different
  // question and a settled one: it is stored as the characters it is, and SH1016's renderer
  // escapes them. Asserting it never appears would be asserting that an author may not write
  // about HTML.)
  const injected = blocksFromPastedHtml("<p>a</p><script>alert(1)</script><p>b</p>")
  assert.deepEqual(injected.map((block) => block.type), ["paragraph", "paragraph"],
    "SH1017: a paste can never introduce a tag — a shape with no name in the model is not content "
      + "this instance can store, which is the property innerHTML could not have")
  assert.deepEqual(injected.map((block) => block.text), ["a", "b"])
})

test("SH1017: every mouse gesture has a keyboard path, and moves are announced", () => {
  const widget = read("src", "components", "widgets", "blocks.widget.tsx")

  assert.match(widget, /useSensor\(KeyboardSensor, \{ coordinateGetter: sortableKeyboardCoordinates \}\)/u)
  assert.match(widget, /aria-label=\{t\("blocks\.moveUp"\)\}/u,
    "SH1017: drag alone is a feature some curators do not have, so the two buttons are the path "
      + "and not a convenience")
  assert.match(widget, /aria-label=\{t\("blocks\.moveDown"\)\}/u)
  assert.match(widget, /aria-live="polite"/u,
    "SH1017: a move has no visual anchor for a screen reader, and the keyboard path is the one a "
      + "screen-reader user takes — so it is the path that must speak")
  assert.match(widget, /aria-label=\{t\("blocks\.regionLabel"/u,
    "SH1017: otherwise the form is a stack of unlabelled text boxes")
})

test("SH1017: a link is an inline form, and the old editor's calls are gone", () => {
  const widget = code(read("src", "components", "widgets", "blocks.widget.tsx"))

  assert.doesNotMatch(widget, /\bprompt\(/u,
    "SH1017: the widget this replaces asked for every URL with prompt() — unstyleable, "
      + "uncancellable without losing the selection, and blocked outright in some browsers")
  assert.doesNotMatch(widget, /execCommand/u,
    "SH1017: execCommand is what made the body markup a browser mutated rather than a document")
  assert.match(widget, /t\("blocks\.linkApply"\)/u)
})

test("SH1017: a Blocks field routes by its widget, not by the value's shape", () => {
  const form = read("src", "components", "widgets", "post-form-fields.tsx")

  assert.match(form, /if \(field\.widget === "Blocks"\) \{\s*attr\.blocksValue = Array\.isArray\(value\) \? value : \[\]/u,
    "SH1017: both a blocks document and a relator's rows are arrays of objects, so routing by "
      + "shape would hand a body to the relator editor")
  assert.match(form, /if \(field\.widget === "Blocks"\) \{\s*return attr\.blocksValue \?\? \[\]/u,
    "SH1017: and [] for an emptied document, so removing the last block survives the save")
})

test("SH1017: every string the editor asks for exists in both locales", () => {
  const widget = read("src", "components", "widgets", "blocks.widget.tsx")
  const keys = [...widget.matchAll(/t\("(blocks\.[\w.]+)"/gu)].map(([, key]) => key)
  // The per-type and per-mark keys are built from a template, so they are checked from the lists.
  const { BLOCK_TYPES: types, MARK_TYPES: marks } = { BLOCK_TYPES, MARK_TYPES: ["strong", "em", "code", "link", "strike"] }
  const all = [...keys, ...types.map((t) => `blocks.type.${t}`), ...marks.map((m) => `blocks.mark.${m}`)]

  assert.ok(all.length >= 20, `SH1017: only ${all.length} key(s) — the scan stopped matching`)
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "blocks.json"))
    for (const key of all) {
      assert.ok(strings[key], `SH1017: ${locale} is missing ${key}`)
    }
  }
})
