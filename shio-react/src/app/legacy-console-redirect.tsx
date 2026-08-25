/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { Navigate, useLocation } from "react-router-dom"
import { bentoPathFor } from "@/app/routes.const"

/**
 * SH909 — every `/console/**` URL, sent to the same surface under `/bento`.
 *
 * §LV.12's instruction was to **redirect rather than drop**: the console has been addressable at
 * `/console` for the whole life of the React app, so its paths are in bookmarks, in e-mails, in
 * whatever an operator wrote down, and in redirects this repository does not control. A 404 for
 * each of them is not a migration, it is a removal.
 *
 * The mapping is a prefix swap — `/console/content/post/<id>` → `/bento/content/post/<id>` — with
 * two cases that are not: `/console` and `/console/home` are the same page under bento, and it is
 * the shell's *index* rather than a `/home` route (SH898). `bentoPathFor` holds both, next to the
 * constants it is derived from.
 *
 * `replace` on purpose: a redirect that pushes leaves the old URL in the history, so Back from the
 * bento page returns to the console path, which redirects forward again — a trap the reader cannot
 * escape with the button they reached for.
 *
 * The search string and the hash are carried across. The console has one surface where that is not
 * cosmetic — `?q=` on search — and dropping a query on a redirect is the sort of loss nobody
 * reports, because the page looks like it simply found nothing.
 */
export default function LegacyConsoleRedirect() {
  const location = useLocation()
  return <Navigate to={`${bentoPathFor(location.pathname)}${location.search}${location.hash}`} replace />
}
