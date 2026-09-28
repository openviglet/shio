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
import { createElement } from "react"
import { renderToStaticMarkup } from "react-dom/server"
import { BentoChangeMark } from "@viglet/viglet-design-system/bento"
import { changeStateOf } from "../src/lib/change-mark.ts"

// @scans code — the dialog is read for the calls it makes; comments are not what is asserted.
import { stripComments } from "./support/source-text.mjs"

/**
 * SH1178 — the bulk plan marks each change the way the diff does, not with the server's word.
 *
 * The dialog printed `change.action` raw in a grey badge, so a Portuguese curator read `delete` in
 * English and a deletion looked exactly like a no-op, on the one sheet where a batch is confirmed.
 * The dialog is TSX and cannot be imported here, so this holds the three things it is made of: the
 * mapping for every action the server sends, the mark itself telling the states apart, and the
 * dialog drawing that mark for a change and keeping its badge for a refusal.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const ACTIONS = ["create", "update", "move", "publish", "unpublish", "delete", "noop"]

test("SH1178: every action the change set sends maps to one of the mark's states", () => {
  assert.deepEqual(Object.fromEntries(ACTIONS.map((action) => [action, changeStateOf(action)])), {
    create: "added", update: "changed", move: "changed", publish: "changed", unpublish: "changed",
    delete: "removed", noop: "unchanged",
  }, "SH1178: a deletion must read as removed and a no-op as unchanged, never as the same thing")
})

test("SH1178: the mark draws a removal and a no-op differently, so the sheet can tell them apart", () => {
  const drawn = Object.fromEntries(["added", "removed", "changed", "unchanged"].map((state) =>
    [state, renderToStaticMarkup(createElement(BentoChangeMark, { state }))]))
  assert.equal(new Set(Object.values(drawn)).size, 4,
    "SH1178: two change states render identically, which is the defect this replaced")
  assert.notEqual(drawn.removed, drawn.unchanged)
})

test("SH1178: the plan draws the mark for a change, names what changed, and keeps a refusal's badge", () => {
  const dialog = stripComments(readFileSync(
    join(HERE, "..", "src", "app", "content", "content-browser", "bulk-plan.dialog.tsx"), "utf8"))
  assert.match(dialog, /<BentoChangeMark state=\{changeStateOf\(change\.action\)\}/,
    "SH1178: the plan does not mark its changes with the design system's mark")
  assert.match(dialog, /t\(`content\.changeAction\.\$\{change\.action\}`/,
    "SH1178: a changed row does not name its action in the curator's language")
  assert.match(dialog, /change\.problem \? \([\s\S]*?variant="destructive"[\s\S]*?bulkRefusedBadge/,
    "SH1178: a refusal lost its badge; it is not a change and must not be marked as one")
  assert.doesNotMatch(dialog, /\{change\.problem \? t\("content\.bulkRefusedBadge"\) : change\.action\}/,
    "SH1178: the raw action word is back in the badge")
  for (const lang of ["en", "pt"]) {
    const strings = JSON.parse(readFileSync(join(HERE, "..", "src", "i18n", "locales", lang, "content.json"), "utf8"))
    const missing = ACTIONS.filter((action) => changeStateOf(action) === "changed")
      .filter((action) => !strings[`content.changeAction.${action}`])
    assert.deepEqual(missing, [], `SH1178: the ${lang} console has no word for these changed actions`)
  }
})
