/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH918 — the console's theme, declared once because two readers decide it at different times.
 *
 * Two pieces of code answer "what theme is this" and they run seconds apart, in different
 * languages, on different objects:
 *
 *  1. The design system's boot-loader script, injected into `<head>` by its Vite plugin, runs
 *     **before any module**. It reads `localStorage[STORAGE_KEY]` and — finding nothing — falls
 *     back to `prefers-color-scheme`, then stamps `shio-light-boot` on `<body>` if that says
 *     light. That class paints a near-white ground and a salmon radial gradient.
 *  2. `ThemeProvider` in `App.tsx` runs when React mounts and stamps `dark` or `light` on
 *     `<html>`, from which every `--background` / `--foreground` token resolves.
 *
 * They disagreed on **every first visit from a light-preference machine**: the boot script
 * honoured the OS, the provider hard-coded `dark`. Nothing removed the boot class afterwards,
 * so `<html>` said dark and `<body>` stayed near-white for the whole session. No curator ever
 * saw it, because `console.root.page.tsx` paints `bg-background` across the viewport — and that
 * is exactly why it had to be fixed before the bento shell, which is a second parallel root that
 * would show the boot ground through anything it forgot to paint. SH887's proving page, mounted
 * outside every shell, is where it finally became visible: white hero text on white.
 *
 * Both readers now import from here. `vite.config.ts` is TypeScript and imports it directly to
 * seed storage ahead of the boot script; `App.tsx` imports it for the provider. One declaration
 * rather than two literals that happen to agree today.
 */

/** Where the chosen theme is remembered. Must match what `vigletBootLoader` is told to read. */
export const SHIO_THEME_STORAGE_KEY = "shio-ui-theme"

/**
 * What the console is before anybody chooses. Deliberately `dark` and deliberately not
 * `system`: this is Shio's own look, and a console that changes colour because a laptop
 * switched to night mode is a console two people describe differently.
 */
export const SHIO_DEFAULT_THEME = "dark"

/** The `<body>` class the boot loader adds for a light splash, and which outlives the splash. */
export const SHIO_LIGHT_BOOT_CLASS = "shio-light-boot"

/**
 * The script that runs **before** the boot loader and writes the default into storage when the
 * reader has never chosen. Emitted verbatim into `<head>`, so it is plain ES5 in a string: it
 * executes before any bundle and cannot import anything.
 *
 * Seeding rather than patching the boot loader, because the plugin exposes `storageKey` and no
 * `defaultTheme` — and because storage is the one input **both** readers already consult, which
 * makes agreement structural instead of a coincidence between two constants.
 *
 * `try`/`catch` around every access: a private window, a browser configured to block site data,
 * or an embedded webview can make `localStorage` *throw* on read, not merely return null. A
 * console that fails to start because it could not remember a colour would be a worse defect
 * than the one this fixes.
 */
export function shioThemeSeedScript(): string {
  return `(function () {
  try {
    if (!localStorage.getItem(${JSON.stringify(SHIO_THEME_STORAGE_KEY)})) {
      localStorage.setItem(${JSON.stringify(SHIO_THEME_STORAGE_KEY)}, ${JSON.stringify(SHIO_DEFAULT_THEME)});
    }
  } catch (e) { /* storage unavailable — the boot loader falls back on its own */ }
})();`
}
