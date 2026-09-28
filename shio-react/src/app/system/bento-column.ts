/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import type { BentoShellColumn } from "@viglet/viglet-design-system/bento"

/**
 * SH946 — which reading column a route sits in, decided once by the shell.
 *
 * <p>`BentoShell` owns the width, the gutters and the vertical padding, so a page sets none of
 * them. What a page may still need is a different column, and the shell takes that as a prop
 * above the router. So the routes that want one are named here, as patterns, rather than each
 * page carrying a width class. Everything else is `default` (80rem, the width home and the
 * post-type picker used to set by hand).</p>
 *
 * <p>`narrow` (56rem) is for a single-question form, where a long line is harder to read: the
 * three create and import forms, which set `max-w-4xl` themselves before this, and (SH947) the
 * six infrastructure editors that set `max-w-2xl`, which the design system's page lint now
 * refuses on a page.</p>
 */
const NARROW: readonly RegExp[] = [
  /^\/bento\/content\/site\/new\/?$/,
  /^\/bento\/content\/site\/import\/?$/,
  /^\/bento\/content\/folder\/[^/]+\/new-folder\/?$/,
  /^\/bento\/admin\/(?:tokens|auth-providers|exchange-providers|webhooks|tenants)\/[^/]+\/?$/,
  /^\/bento\/admin\/email-config\/?$/,
]

/**
 * SH1169 — `full` is a tool that owns the viewport: the shell's `main` fills what the header leaves,
 * with no bottom rhythm, and the tool scrolls inside itself. The explorer computed that height as
 * `100vh - 4rem` instead, and ran 21px past the window once the shell's chrome stopped being 4rem.
 */
const FULL: readonly RegExp[] = [
  /^\/bento\/graphql\/?$/,
]

/**
 * SH1094 — the two pages composed as `BentoSplitPage` (VDS173), which the package draws inside the
 * shell's wide column: a post beside its preview, and a page beside its translation.
 */
const WIDE: readonly RegExp[] = [
  /^\/bento\/content\/post\/[^/]+\/?$/,
  /^\/bento\/content\/post\/[^/]+\/translate\/?$/,
]

export function columnFor(pathname: string): BentoShellColumn {
  if (FULL.some((pattern) => pattern.test(pathname))) return "full"
  if (WIDE.some((pattern) => pattern.test(pathname))) return "wide"
  return NARROW.some((pattern) => pattern.test(pathname)) ? "narrow" : "default"
}
