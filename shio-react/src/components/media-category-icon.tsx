/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { createElement } from "react"
import { mediaCategory, mediaCategoryIcon } from "@/lib/media-type"

/**
 * A file's kind as an icon, for the thumbnail a non-image does not have.
 *
 * One component rather than `const Icon = mediaCategoryIcon(...)` in each caller: the React
 * compiler's lint reads a component chosen during render as a component created during render, and
 * three callers in the media library did exactly that (SH1226 surfaced it once the page became
 * analysable). The icons are module constants, so choosing one here is a lookup, not a new type.
 */
export function MediaCategoryIcon({ contentType, className }: Readonly<{
  contentType: string | null | undefined
  className?: string
}>) {
  return createElement(mediaCategoryIcon(mediaCategory(contentType)), { className, "aria-hidden": true })
}
