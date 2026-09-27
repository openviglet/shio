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
 * SH1014 — the console renders what has not been saved, and the frame it renders into is not
 * trusted.
 *
 * The two claims worth guarding are both invisible on screen: a frame that could reach the
 * console's session, and a re-render that fires per keystroke. Neither shows up in a screenshot and
 * both are one attribute away.
 *
 * @scans code
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const read = (...path) => readFileSync(join(ROOT, ...path), "utf8")

test("SH1014: the preview frame cannot reach the console it is rendered inside", () => {
  const preview = read("src", "components", "post-live-preview.tsx")

  assert.match(preview, /sandbox="allow-scripts"/u,
    "SH1014: scripts run because a page whose scripts do not run is not what a visitor gets, "
      + "which is the question being asked")
  // In the attribute, not in the sentence explaining why it is absent — a scan that matched
  // prose would fail on the comment that documents the rule.
  assert.doesNotMatch(preview, /sandbox="[^"]*allow-same-origin/u,
    "SH1014: with it the frame shares the console's origin and can read the curator's session, "
      + "cookies and storage — the markup is composed from content a caller supplied, so the one "
      + "thing it must not reach is the console that is showing it")
  assert.match(preview, /srcDoc=\{html\}/u,
    "SH1014: there is no URL that serves this page — the document does not exist anywhere, which "
      + "is the whole point")
})

test("SH1014: re-rendering as you type is a switch, and it starts off", () => {
  const preview = read("src", "components", "post-live-preview.tsx")

  assert.match(preview, /const \[live, setLive\] = useState\(false\)/u,
    "SH1014: each render composes a whole page on the server — layout, regions, theme — and a "
      + "curator fixing a typo has not asked for one per pause in their typing")
  // SH1167 — and only for a type the site renders as a page.
  assert.match(preview, /if \(!live \|\| !asks\) return undefined/u)
  // SH1136 named the value, so the dependency is a variable the lint can check.
  assert.match(preview, /const typed = JSON\.stringify\(data\)/u)
  assert.match(preview, /\}, \[live, asks, render, typed\]\)/u,
    "SH1014: debounced by VALUE — depending on the object the caller rebuilds every render would "
      + "re-arm the timer forever and never fire it")
  // SH1116: the effect also aborts the render in flight, and so depends on `cancel`. SH1136: the
  // render is scheduled rather than called, so the effect body itself sets no state.
  assert.match(preview, /const timer = setTimeout\(\(\) => void render\(\), 0\)[\s\S]*?\}, \[asks, render, cancel, revision\]\)/u,
    "SH1014: and it still refreshes on every save, which is the question being asked most of the time")
})

test("SH1014: the console asks for the markup, and says the values are not saved", () => {
  const service = read("src", "services", "insights", "insights.service.ts")
  const preview = read("src", "components", "post-live-preview.tsx")
  const page = read("src", "app", "content", "post.edit.page.tsx")

  assert.match(service, /"\/v2\/agent\/render\?format=html"/u,
    "SH1014: format=html is what a frame needs; without it the same call answers the digest, "
      + "which an agent wants and this would otherwise pay for")
  assert.match(service, /\{ address, state: "draft", data \}/u)
  assert.match(preview, /t\("insights\.previewNotSaved"\)/u,
    "SH1014: a preview that looks like the page is exactly the thing a curator could mistake for "
      + "having saved it")
  assert.match(page, /<PostLivePreview address=\{`id:\$\{postId\}`\} data=\{data\} revision=\{insightsAt\}\s+renderable=\{renderable\} \/>/u)
})

test("SH1014: every width is reachable and announced", () => {
  const preview = read("src", "components", "post-live-preview.tsx")

  assert.match(preview, /aria-pressed=\{current === value \? "true" : "false"\}/u,
    "SH1014: a toggle that does not say it is pressed is a button a screen reader reads as plain")
  assert.match(preview, /aria-label=\{label\}/u,
    "SH1014: the buttons are icons, so without this they are announced as nothing")

  const keys = [...preview.matchAll(/t\("(insights\.[\w.]+)"/gu)].map(([, key]) => key)
  assert.ok(keys.length >= 8, `SH1014: only ${keys.length} key(s) found — the scan stopped matching`)
  for (const locale of ["en", "pt"]) {
    const strings = JSON.parse(read("src", "i18n", "locales", locale, "insights.json"))
    for (const key of keys) {
      assert.ok(strings[key], `SH1014: ${locale} is missing ${key}`)
    }
  }
})
