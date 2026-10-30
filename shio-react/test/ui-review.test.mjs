/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { strict as assert } from "node:assert"
import { existsSync, readFileSync } from "node:fs"
import { dirname, join } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"
import { pickNext, recordReview, reviewableFamilies } from "../scripts/ui-review.mjs"

/**
 * SH1321 — the console review's rotation, which decides what an agent looks at and so what it
 * never looks at. The review itself needs a browser and a model; the order is pure and lives here.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const REPO = join(ROOT, "..")

const report = {
  probes: [
    { name: "sites", path: "/console/content", ok: true, errors: [], screenshot: "a.png" },
    { name: "post-edit:Page", path: "/console/content/post/1", ok: true, errors: [], screenshot: "b.png" },
    { name: "post-edit:Article", path: "/console/content/post/2", ok: false, errors: ["boom"],
      screenshot: "c.png" },
    { name: "trash", path: "/console/content/trash", ok: true, errors: [], missing: [], screenshot: "d.png" },
    { name: "keyboard-landmarks", path: "/bento", ok: false, errors: ["no skip link"] },
  ],
}

test("SH1321: families are grouped as the cost budget groups them, and only screenshotted probes count", () => {
  const families = reviewableFamilies(report)
  assert.deepEqual(families.map((f) => f.family).sort(), ["post-edit", "sites", "trash"])
  const postEdit = families.find((f) => f.family === "post-edit")
  assert.equal(postEdit.failing, true, "one failing member makes the family failing")
  assert.deepEqual(postEdit.routes.map((r) => r.name), ["post-edit:Article", "post-edit:Page"])
})

test("SH1321: failing first, then never reviewed, then oldest, then by name", () => {
  const families = reviewableFamilies(report)
  const ledger = { families: { sites: { reviewed: "2026-10-01" }, trash: { reviewed: "2026-09-01" } } }
  const order = pickNext(families, ledger, 10).map((f) => `${f.family}:${f.reason}`)
  assert.deepEqual(order, [
    "post-edit:the console check reported it failing",
    "trash:last reviewed 2026-09-01",
    "sites:last reviewed 2026-10-01",
  ])
  const fresh = pickNext(reviewableFamilies({ probes: report.probes.filter((p) => p.ok) }), {}, 10)
  assert.deepEqual(fresh.map((f) => f.family), ["post-edit", "sites", "trash"], "a tie is broken by name")
  assert.equal(pickNext(families, ledger, 1).length, 1)
})

test("SH1321: recording moves a family to the back, and the ledger is written sorted", () => {
  const families = reviewableFamilies({ probes: report.probes.filter((p) => p.ok) })
  const ledger = recordReview({ why: "x", families: {} }, ["trash", "post-edit"],
    { reviewed: "2026-10-30", commit: "abc1234", findings: ["SH9902", "SH9901"] })
  assert.equal(ledger.why, "x", "the ledger's own fields survive a record")
  assert.deepEqual(Object.keys(ledger.families), ["post-edit", "trash"])
  assert.deepEqual(ledger.families.trash,
    { reviewed: "2026-10-30", commit: "abc1234", findings: ["SH9901", "SH9902"] })
  assert.equal(pickNext(families, ledger, 1)[0].family, "sites")
})

test("SH1321: a record refuses what would corrupt the rotation", () => {
  assert.throws(() => recordReview({}, ["sites"], { reviewed: "yesterday" }), /YYYY-MM-DD/)
  assert.throws(() => recordReview({}, ["sites"], { reviewed: "2026-10-30", findings: ["fix the header"] }),
    /task ids/)
})

test("SH1321: the ledger, the picker and the skill that drives them are all in the tree", () => {
  const ledger = JSON.parse(readFileSync(join(ROOT, "ui-review-ledger.json"), "utf8"))
  assert.equal(typeof ledger.families, "object")
  for (const [family, entry] of Object.entries(ledger.families)) {
    assert.match(entry.reviewed, /^\d{4}-\d{2}-\d{2}$/, `${family}: reviewed is a date`)
  }
  assert.ok(existsSync(join(ROOT, "e2e", "ui-review-pick.mjs")))
  const skill = readFileSync(join(REPO, ".claude", "skills", "shio-console-review", "SKILL.md"), "utf8")
  assert.match(skill, /ui-review-pick\.mjs/, "the skill must drive the picker, not choose routes itself")
  assert.ok(existsSync(join(REPO, ".claude", "skills", "shio-console-review", "checklist.md")))
})
