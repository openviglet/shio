/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { QueryClientProvider } from "@tanstack/react-query"
import React from "react"

import i18n from "@/i18n"
import { createQueryClient } from "@/lib/query-client"
import { reportProblem } from "@/lib/problem"

/**
 * SH1046 — the provider that replaced `ShioServiceProvider`, in the same place in the tree.
 *
 * ## What was here before
 *
 * A service-injection context built for exactly this purpose: one `ShSiteService`,
 * `ShFolderService`, `ShPostTypeService`, `ShPostUnifiedService` and `ShWidgetService`, memoised
 * and handed down. `useShioService` had **no call sites** — every page constructed its own service
 * at module scope instead — and the provider was still mounted around the whole route tree,
 * building five objects nobody read. It was the right instinct one layer too low: the services are
 * stateless wrappers over axios, so sharing the *instance* saves nothing. What every page was
 * really re-doing was the **request**.
 *
 * ## What it holds now
 *
 * The one `QueryClient`. Its defaults are in `lib/query-client.ts` rather than here, so they can be
 * driven under plain Node — a behaviour reachable only through a rendered provider is one nothing
 * asserts.
 *
 * ## The failure handler is the cache's
 *
 * A failed read reaches the curator once, in the server's own words, through the same
 * `reportProblem` every save path uses (SH955). Per-component handlers would fire once per screen
 * sharing the read, which is the thing that made three toasts out of one outage.
 */
export function ShioQueryProvider({ children }: { children: React.ReactNode }) {
  // One client for the life of the app, built here rather than at module scope so a test, or a
  // second root, gets its own cache instead of whatever the last one left behind.
  //
  // The i18n instance directly rather than `useTranslation`: this handler fires long after the
  // render that built the client, so it has to read the language in force *then*. Closing over a
  // `t` would pin the language the app booted in; rebuilding the client on a language change would
  // empty the cache, which is a language switch that refetches every screen.
  const [client] = React.useState(() => createQueryClient((error) => {
    reportProblem(error, i18n.t("common.error"))
  }))

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}
