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
import { JSDOM } from "jsdom"
import { vigletBootLoader } from "@viglet/viglet-design-system/vite"

/**
 * SH918 — the splash and the app agree about the theme, proved by running both.
 *
 * ## Why this one needs a DOM
 *
 * The defect was two pieces of code answering the same question at different times and getting
 * different answers. Nothing about that is visible in either file: the boot loader's script is a
 * string inside a design-system plugin, `ThemeProvider` is a React component, and the only place
 * they meet is a browser, seconds apart. Reading either one alone shows nothing wrong — which is
 * why this survived until SH887 mounted a page outside the console shell and the hero title came
 * up white on white.
 *
 * So this executes the **real** scripts — the seed from `src/theme.ts` and the boot loader's own,
 * pulled out of the plugin by calling its `transformIndexHtml` — in `jsdom`, in the order the
 * page runs them, and asserts what `<body>` ends up carrying. No build, no browser, no server:
 * `jsdom` is already a devDependency and the two scripts are a few lines each.
 *
 * ## The case that matters is a light-preference machine
 *
 * On a dark-preference machine the two agreed by accident and always had. The failing case is
 * `prefers-color-scheme: light` with nothing stored, which is every first visit from a laptop in
 * day mode — the boot script honoured the OS, `ThemeProvider` hard-coded dark, and `<body>` kept
 * a near-white ground under an `<html class="dark">` for the rest of the session. Both cases are
 * asserted, because a fix that made the light case pass by breaking the dark one would look
 * identical from here.
 */
const HERE = dirname(fileURLToPath(import.meta.url))
const CONSOLE_ROOT = join(HERE, "..")

/**
 * Imported, not scraped. `src/theme.ts` is TypeScript and this file is `.mjs`, which used to
 * mean reading the source and pulling the seed out with a regex — and that quietly produced the
 * template's *uninterpolated* text, `${JSON.stringify(...)}` and all, which `eval` then rejected.
 * Node strips types on import, so the exact function `vite.config.ts` calls is the exact function
 * that runs here. A test that reconstructs what ships is a test of the reconstruction.
 */
const {
  SHIO_THEME_STORAGE_KEY: STORAGE_KEY,
  SHIO_DEFAULT_THEME: DEFAULT_THEME,
  SHIO_LIGHT_BOOT_CLASS: LIGHT_BOOT_CLASS,
  shioThemeSeedScript: seedScript,
} = await import("../src/theme.ts")

/** The boot loader's own script, from the plugin, configured exactly as vite.config.ts does. */
async function bootScript() {
  const plugin = vigletBootLoader({
    title: "Viglet Shio",
    subtitle: "Headless CMS",
    color: "#7C3AED",
    colorDark: "#6D28D9",
    prefix: "shio",
    storageKey: STORAGE_KEY,
    testFlag: "__SHIO_LOADING_TEST__",
  })
  const result = await plugin.transformIndexHtml(
    '<!doctype html><html><head></head><body><div id="root"><!--viglet-boot-loader--></div></body></html>',
    { path: "/index.html", filename: "index.html" },
  )
  const script = (result.tags ?? []).find(
    (tag) => tag.tag === "script" && typeof tag.children === "string"
      && tag.children.includes(STORAGE_KEY),
  )
  assert.ok(script,
    "SH918: the boot loader no longer injects a script reading the theme from storage. If it"
      + " stopped deciding the splash's colour that way, the seed in src/theme.ts is teaching"
      + " nothing and should go — but check before deleting it.")
  return script.children
}

/**
 * A page load: a DOM whose OS preference is `prefers`, nothing stored unless `stored` says so,
 * running the two scripts in the order `<head>` runs them. Returns what the page ended up with.
 */
function load({ prefers, stored, seed = true, boot }) {
  const dom = new JSDOM("<!doctype html><html><head></head><body></body></html>", {
    url: "http://localhost/",
    runScripts: "outside-only",
  })
  const { window } = dom

  // jsdom implements no media queries at all — `window.matchMedia` is undefined — so the boot
  // script's own fallback is stubbed here rather than mocked away: this is the input the defect
  // turned on, and a stub that always answered "dark" would pass over the failing case.
  window.matchMedia = (query) => ({
    matches: query.includes("light") ? prefers === "light" : prefers === "dark",
    media: query,
    addEventListener() {},
    removeEventListener() {},
  })
  if (stored !== undefined) window.localStorage.setItem(STORAGE_KEY, stored)

  if (seed) window.eval(seedScript())
  window.eval(boot)
  // The boot loader defers the class to DOMContentLoaded, which jsdom will not fire for a
  // document it has already finished parsing — so it is dispatched explicitly.
  window.document.dispatchEvent(new window.Event("DOMContentLoaded"))

  return {
    stored: window.localStorage.getItem(STORAGE_KEY),
    lightBoot: window.document.body.classList.contains(LIGHT_BOOT_CLASS),
    htmlClass: window.document.documentElement.className,
  }
}

test("SH918: a first visit from a light-preference machine no longer splits the theme", async () => {
  const boot = await bootScript()
  const result = load({ prefers: "light", stored: undefined, boot })

  assert.equal(result.stored, DEFAULT_THEME,
    `SH918: nothing was written to ${STORAGE_KEY} on a first visit, so the boot loader falls`
      + " back to prefers-color-scheme while ThemeProvider uses its default — which is the whole"
      + " defect. Check that vite.config.ts still injects the seed with injectTo head-prepend.")
  assert.equal(result.lightBoot, false,
    `SH918: <body> still carries ${LIGHT_BOOT_CLASS} on a light-preference machine. That class`
      + " paints a near-white ground and a salmon gradient, and ThemeProvider is about to stamp"
      + ` ${DEFAULT_THEME} on <html> — white text on a white body, for the whole session, on every`
      + " page that does not paint over the viewport.")
})

test("SH918: this is a real regression test — without the seed the split comes back", async () => {
  const boot = await bootScript()
  const unseeded = load({ prefers: "light", stored: undefined, seed: false, boot })

  assert.equal(unseeded.lightBoot, true,
    "SH918: with the seed removed the boot loader no longer chooses light on a light-preference"
      + " machine, so the test above is passing for some reason other than the fix. Either the"
      + " plugin changed its fallback or the matchMedia stub is not being read — find out which"
      + " before trusting the green run.")
})

test("SH918: a reader who chose light still gets a light splash", async () => {
  const boot = await bootScript()
  const chosen = load({ prefers: "dark", stored: "light", boot })

  assert.equal(chosen.stored, "light",
    "SH918: the seed overwrote a stored choice. It must only write when nothing is there — a"
      + " seed that clobbers is a console that forgets the theme you picked on every reload.")
  assert.equal(chosen.lightBoot, true,
    "SH918: a reader who chose light got a dark splash, so the seed is now deciding instead of"
      + " defaulting.")
})

test("SH918: a dark-preference machine is unchanged, which is the case that always worked", async () => {
  const boot = await bootScript()
  const result = load({ prefers: "dark", stored: undefined, boot })

  assert.equal(result.lightBoot, false, "SH918: a dark-preference first visit gained a light splash")
  assert.equal(result.stored, DEFAULT_THEME,
    "SH918: the default was not seeded on a dark-preference machine either — the seed should not"
      + " be conditional on the OS, or the two readers agree by luck again")
})

test("SH918: both readers take the constants from one declaration", () => {
  const app = readFileSync(join(CONSOLE_ROOT, "src", "App.tsx"), "utf8")
  const config = readFileSync(join(CONSOLE_ROOT, "vite.config.ts"), "utf8")

  assert.match(app, /SHIO_DEFAULT_THEME/,
    "SH918: App.tsx spells its own default again. Two literals that agree today are what this"
      + " task is about; they disagreed for as long as nobody rendered a page outside the shell.")
  assert.match(app, /SHIO_THEME_STORAGE_KEY/,
    "SH918: App.tsx spells the storage key again — and the key is what the two readers meet on")
  assert.match(config, /SHIO_THEME_STORAGE_KEY/,
    "SH918: vite.config.ts hardcodes the storage key it hands the boot loader, so the loader can"
      + " end up reading a different key than the seed writes and the console silently loses the"
      + " remembered theme")

  assert.doesNotMatch(app.replace(/SHIO_THEME_STORAGE_KEY/g, ""), /"shio-ui-theme"/,
    "SH918: a literal 'shio-ui-theme' is back in App.tsx")
})

test("SH918: the boot class is removed once React has replaced the splash", () => {
  const main = readFileSync(join(CONSOLE_ROOT, "src", "main.tsx"), "utf8")

  assert.match(main, /classList\.remove\(SHIO_LIGHT_BOOT_CLASS\)/,
    "SH918: main.tsx no longer clears the boot class. Seeding fixes first paint; it does not fix"
      + " a reader who then switches to dark from the toggle, which leaves <html class=\"dark\">"
      + " over a body still painting the light splash's ground for the rest of the session.")

  const guard = main.indexOf("__SHIO_LOADING_TEST__")
  const removal = main.indexOf("classList.remove(SHIO_LIGHT_BOOT_CLASS)")

  assert.notEqual(guard, -1,
    "SH918: main.tsx no longer guards on __SHIO_LOADING_TEST__, so there is nothing for the boot"
      + " class removal to sit inside and the check below would pass on position alone")
  assert.ok(removal > guard,
    "SH918: the boot class is cleared outside the `?loading=1` guard. That mode exists to hold"
      + " the loader visible for design work, and stripping the class it is styled with turns the"
      + " one thing it is meant to show into an unstyled page.")
})
