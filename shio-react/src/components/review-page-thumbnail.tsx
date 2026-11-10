/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { useTranslation } from "react-i18next"

/** The width the page is laid out at before it is scaled down, a common laptop viewport. */
const VIEWPORT_WIDTH = 1280
const VIEWPORT_HEIGHT = 800
/** How small the thumbnail is drawn. 0.15 of 1280×800 is 192×120. */
const SCALE = 0.15

/**
 * SH1429 — the page itself, small, beside its row in the review queue.
 *
 * <p>The draft is drawn by the curator's own browser, in an iframe laid out at a laptop width and
 * scaled down; the instance runs no browser and stores no screenshot. It is lazy, so a run of forty
 * pages renders the rows on screen, and sandboxed with no scripts, so a thumbnail cannot run a page's
 * code or take focus. Clicking it opens the full preview, which is what a curator who wants to read
 * the page does next.</p>
 */
export function ReviewPageThumbnail({ url, title }: Readonly<{ url: string; title: string }>) {
  const { t } = useTranslation()
  return (
    <a
      href={url}
      target="_blank"
      rel="noopener noreferrer"
      className="relative block shrink-0 overflow-hidden rounded-md border bg-background"
      style={{ width: VIEWPORT_WIDTH * SCALE, height: VIEWPORT_HEIGHT * SCALE }}
      aria-label={t("review.openPreview", { title })}
      title={t("review.openPreview", { title })}
    >
      <iframe
        src={url}
        title={t("review.thumbnail", { title })}
        loading="lazy"
        sandbox="allow-same-origin"
        tabIndex={-1}
        aria-hidden="true"
        className="pointer-events-none origin-top-left border-0"
        style={{ width: VIEWPORT_WIDTH, height: VIEWPORT_HEIGHT, transform: `scale(${SCALE})` }}
      />
    </a>
  )
}
