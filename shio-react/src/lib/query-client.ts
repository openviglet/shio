/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
import { QueryCache, QueryClient } from "@tanstack/react-query";

/**
 * SH1046 — the console's one query client, and the three decisions it owns.
 *
 * <h2>Why this is a `.ts` file beside the provider and not inside it</h2>
 *
 * <p>The defaults below are the whole behaviour — how long a catalogue stays fresh, what a failure
 * does, how often it is retried — and a behaviour that can only be reached through a rendered
 * provider is one nothing asserts. `test/query-cache.test.mjs` imports this and drives the real
 * client with the real defaults: two reads inside the stale window issue one request, the second
 * navigation issues none. That is the claim SH1046 exists to make, made in-process.</p>
 *
 * <h2>Thirty seconds, and where the number comes from</h2>
 *
 * <p>A catalogue read — post types, sites, widgets, groups, roles — is what seventeen call sites
 * were refetching on every mount, and the browser refetched the <em>entire</em> post-type catalogue
 * on every folder navigation to pick a default. None of those change while a curator walks a tree.
 * Thirty seconds is long enough that a folder-to-folder walk costs one request rather than one per
 * folder, and short enough that a curator who edits a post type in another tab sees it on the next
 * screen rather than after a reload. A write invalidates its own scheme, so the window is the
 * ceiling on staleness caused by <em>somebody else</em>, never by this session.</p>
 *
 * <h2>Retry once, and never on a refusal</h2>
 *
 * <p>Retrying a 4xx is asking a second time for something the server has explained it will not do:
 * it doubles the cost of every refusal and delays the message the curator needs by a round trip.
 * P6 says an error is an instruction — so a refusal is delivered, not repeated. A 5xx or a dropped
 * connection is retried once, which covers the restart a developer's instance does constantly and
 * stops well short of the retry storm that makes an outage worse.</p>
 *
 * <h2>The error handler is the cache's, not the component's</h2>
 *
 * <p>TanStack v5 removed `onError` from `useQuery` precisely because a per-component handler fires
 * once per observer — three screens sharing one failed read toasted three times. The cache-level
 * handler fires once per failed query, which is once per thing that actually went wrong, and it
 * reads the backend's RFC 9457 document (SH955) so a refusal reaches a person in the server's own
 * words instead of as "Error".</p>
 */

/** How long a catalogue read stays fresh. See the class comment — this is the number SH1046 turns on. */
export const STALE_MS = 30_000;

/** How long an unobserved answer is kept before it is collected, so a Back is free. */
export const GC_MS = 5 * 60_000;

/** What a failed query does, once per query rather than once per component that asked. */
export type ShQueryFailure = (error: unknown, address: string) => void;

/**
 * Whether a failure is worth asking about again.
 *
 * <p>Exported because it is the decision, not a detail: a status the server chose is an answer, and
 * a missing status is a request that never arrived.</p>
 */
export function shouldRetry(failures: number, error: unknown): boolean {
  if (failures >= 1) return false;
  const status = (error as { response?: { status?: number } } | undefined)?.response?.status;
  return status === undefined || status >= 500;
}

/**
 * The client the console mounts.
 *
 * @param onFailure what to do with a failed query — the console passes a toast; a test passes a
 *     recorder, which is why this is an argument rather than an import
 */
export function createQueryClient(onFailure?: ShQueryFailure): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: STALE_MS,
        gcTime: GC_MS,
        retry: (failures, error) => shouldRetry(failures, error),
        // A curator switching windows is not evidence that a catalogue changed, and the console
        // is a tool people leave open all day: this default would otherwise undo the whole task.
        refetchOnWindowFocus: false,
      },
    },
    queryCache: new QueryCache({
      onError: (error, query) => {
        onFailure?.(error, String(query.queryKey.join("")));
      },
    }),
  });
}
