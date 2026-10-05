/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import assert from "node:assert/strict"
import { readFileSync, readdirSync, statSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

import { needsCheck, refreshThumbnails } from "../src/lib/site-thumbnail.ts"

/**
 * SH1218 — the console keeps each site's home picture current, and asks as little as it can.
 *
 * Every status read is a server render and every capture is a frame in the curator's browser, so
 * what is asserted here is the asking: which sites the list asks about, and that a second visit
 * with nothing republished sends nothing. The four effects are recorded fakes, which is what lets
 * this run with no browser and no instance.
 */
const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")

const EARLY = "2026-10-01T10:00:00Z"
const LATE = "2026-10-02T10:00:00Z"

function site(id, { published = 1, change = LATE, picture } = {}) {
  return {
    id, name: id, furl: id,
    pages: { published, draft: 0 },
    lastChange: change ? { at: change, actor: "admin", agent: false } : null,
    thumbnail: picture ? { url: `/api/v2/site/${id}/thumbnail`, digest: "a:old", at: picture } : null,
  }
}

function recorder({ fresh = false, home = "a:now", fail } = {}) {
  const memory = new Map()
  const calls = []
  return {
    calls,
    deps: {
      status: async (id) => { calls.push(`status ${id}`); return { home, stored: null, fresh } },
      capture: async (s) => {
        calls.push(`capture ${s.id}`)
        if (fail) throw new Error(fail)
        return new Blob(["x"], { type: "image/webp" })
      },
      put: async (id, digest) => { calls.push(`put ${id} ${digest}`); return { stored: true } },
      asked: (id) => memory.get(id),
      remember: (id, change) => memory.set(id, change),
    },
  }
}

test("SH1218: a stale home is captured and sent with the digest status gave", async () => {
  const { calls, deps } = recorder()
  const stored = await refreshThumbnails([site("cafe")], deps)
  assert.equal(stored, 1)
  assert.deepEqual(calls, ["status cafe", "capture cafe", "put cafe a:now"])
})

test("SH1218: a second visit with nothing republished sends nothing", async () => {
  const { calls, deps } = recorder()
  const sites = [site("cafe"), site("blog", { picture: EARLY })]
  await refreshThumbnails(sites, deps)
  calls.length = 0

  await refreshThumbnails(sites, deps)
  assert.deepEqual(calls, [],
    "SH1218: the list was opened again with no change, and the console asked or sent anyway")
})

test("SH1218: a picture newer than the last change is current without asking", () => {
  assert.equal(needsCheck(site("cafe", { change: EARLY, picture: LATE }), undefined), false)
  assert.equal(needsCheck(site("cafe", { change: LATE, picture: EARLY }), undefined), true)
})

test("SH1218: a site with no published page has no home to picture", () => {
  assert.equal(needsCheck(site("cafe", { published: 0 }), undefined), false)
})

test("SH1218: a fresh status captures nothing, and is not asked again for the same change", async () => {
  const { calls, deps } = recorder({ fresh: true })
  await refreshThumbnails([site("cafe")], deps)
  await refreshThumbnails([site("cafe")], deps)
  assert.deepEqual(calls, ["status cafe"])
})

test("SH1218: one visit takes at most three pictures, and a failed capture sends nothing", async () => {
  const { calls, deps } = recorder()
  await refreshThumbnails(["a", "b", "c", "d", "e"].map((id) => site(id)), deps)
  assert.equal(calls.filter((call) => call.startsWith("capture")).length, 3)

  const broken = recorder({ fail: "the home did not load" })
  assert.equal(await refreshThumbnails([site("cafe")], broken.deps), 0)
  assert.ok(!broken.calls.some((call) => call.startsWith("put")))
})

test("SH1218: the rasteriser is only ever loaded on demand, never by the eager bundle", () => {
  const sources = []
  const walk = (dir) => {
    for (const name of readdirSync(dir)) {
      const path = join(dir, name)
      if (statSync(path).isDirectory()) walk(path)
      else if (/\.(ts|tsx)$/.test(name)) sources.push(path)
    }
  }
  walk(join(ROOT, "src"))
  const statically = sources.filter((path) =>
    /from\s+["'](html-to-image|@\/lib\/site-thumbnail-capture)["']/.test(readFileSync(path, "utf8")))
  assert.deepEqual(statically.map((path) => relative(ROOT, path)), [],
    "SH1218: a static import puts the rasteriser in the eager load SH1043 measures")
})

test("SH1237: a Blob body keeps its own Content-Type through the console's axios interceptor", () => {
  // The run in Chromium found it: main.tsx stamped application/json on every non-FormData body, so
  // the captured picture went out as JSON and the server refused it with 415.
  const main = readFileSync(join(ROOT, "src", "main.tsx"), "utf8")
  assert.match(main, /!\(config\.data instanceof FormData\) && !\(config\.data instanceof Blob\)/,
    "SH1237: the interceptor stamps JSON on a Blob again, and the home picture is refused as 415")
  assert.match(readFileSync(join(ROOT, "e2e", "site-thumbnail.check.mjs"), "utf8"), /stored === true/,
    "SH1237: the browser check that drives the capture end to end is gone")
})
