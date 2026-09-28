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
 * SH1003 — the console's half of the channel back.
 *
 * What can go wrong here is a rail that hides the page it is about (a dialog), one that shows every
 * note ever written until nobody reads it, and a field anchor guessed from focus rather than
 * carried — which would give a different answer depending on what the curator last clicked.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1003: the rail is under the fields, not in a dialog", () => {
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(page, /<NoteRail objectId=\{postId\} \/>/,
    "SH1003: a note is written WHILE reading the page it is about, and a dialog would hide the "
      + "thing being corrected behind the box for correcting it")
  assert.doesNotMatch(read("src", "components", "note-rail.tsx"), /Dialog/,
    "SH1003: the rail has no dialog of its own either")
})

test("SH1003: open notes lead, and resolved ones are behind a click", () => {
  const rail = read("src", "components", "note-rail.tsx")

  assert.match(rail, /const open = notes\.filter\(\(note\) => !note\.resolved\)/)
  assert.match(rail, /showResolved \? notes : open/,
    "SH1003: a rail that showed every resolved note would grow into a page nobody scrolls, which "
      + "is how a comment thread stops being read")
})

test("SH1003: the field anchor is carried, never inferred", () => {
  const rail = read("src", "components", "note-rail.tsx")

  assert.match(rail, /objectId: string; field\?: string/)
  assert.match(rail, /fieldPath: field/,
    "SH1003: inferring it from focus would give a different answer depending on what the curator "
      + "last clicked")
})

test("SH1186: the note kind is one choice of two, drawn as a radio group rather than two buttons", () => {
  const rail = read("src", "components", "note-rail.tsx")

  assert.match(rail, /role="radiogroup"/)
  assert.match(rail, /role="radio"/)
  assert.match(rail, /aria-checked=\{kind === value \? "true" : "false"\}/)
  assert.doesNotMatch(rail, /variant=\{kind === "(COMMENT|REQUEST)"/,
    "SH1186: two solid buttons beside the submit read as three actions, and the picked kind looked "
      + "like the one that sends the note")
})

test("SH1003: the service uses the shared axios and the same rows the ops write", () => {
  const service = read("src", "services", "note", "note.service.ts")

  assert.match(service, /import axios from "@\/lib\/axios"/,
    "SH1003: the singleton, or CSRF and the base URL arrive by import order (SH233)")
  assert.match(service, /"\/v2\/note"/)
  assert.match(service, /\/resolve`/)
})

test("SH1003: both locales name the rail's controls", () => {
  for (const locale of ["en", "pt"]) {
    const note = JSON.parse(read("src", "i18n", "locales", locale, "note.json"))
    for (const key of ["note.title", "note.add", "note.resolve", "note.reopen",
      "note.kindComment", "note.kindRequest", "note.placeholder", "note.placeholderField"]) {
      assert.ok(note[key], `SH1003: ${locale} is missing ${key}`)
    }
  }
})
