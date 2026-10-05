/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { ShSite } from "@/models/site/site"

/**
 * SH1218 — a picture of a site's published home, taken in this browser.
 *
 * The home is loaded from `/sites/<furl>/`, the published delivery route, in an offscreen
 * same-origin frame: that is the page a visitor sees, with no preview banner and no draft, which
 * is what the stored digest describes. `html-to-image` is imported here and only here, so it is a
 * lazy chunk rather than part of the console's eager load.
 */

/** The viewport the home is laid out at, a common laptop width. */
const VIEW = { width: 1280, height: 800 }

/** The picture's size: a card is a few hundred pixels wide, and this is twice that for sharpness. */
const PICTURE = { width: 640, height: 400 }

/** How long a home may take to load before the capture gives up on it. */
const LOAD_MS = 15_000

export function homeUrl(site: ShSite): string {
  return `/sites/${encodeURIComponent(site.furl || site.name)}/`
}

export async function captureHome(site: ShSite): Promise<Blob> {
  const frame = document.createElement("iframe")
  frame.setAttribute("aria-hidden", "true")
  frame.tabIndex = -1
  Object.assign(frame.style, {
    position: "fixed", left: "-10000px", top: "0", border: "0", pointerEvents: "none",
    width: `${VIEW.width}px`, height: `${VIEW.height}px`,
  })
  try {
    await new Promise<void>((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error("the home did not load")), LOAD_MS)
      frame.onload = () => { window.clearTimeout(timer); resolve() }
      frame.src = homeUrl(site)
      document.body.appendChild(frame)
    })
    const page = frame.contentDocument?.documentElement
    if (!page) throw new Error("the home is not readable from this origin")
    const { toCanvas } = await import("html-to-image")
    const canvas = await toCanvas(page, {
      width: VIEW.width, height: VIEW.height,
      canvasWidth: PICTURE.width, canvasHeight: PICTURE.height,
      pixelRatio: 1,
    })
    const picture = await new Promise<Blob | null>((resolve) =>
      canvas.toBlob(resolve, "image/webp", 0.8))
    if (!picture) throw new Error("the picture could not be encoded")
    return picture
  } finally {
    frame.remove()
  }
}
