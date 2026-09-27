/**
 * The site's redirect table, consulted before a page is looked up (Shio roadmap SH1133).
 *
 * A redirect written in Shio — a `Redirect` post, or the "keep the old address working" offer when
 * a page is renamed — is honoured by Shio's own `/sites/**` delivery. This front end serves the
 * site's URLs itself, so it has to do the redirecting: `middleware.ts` asks here first.
 *
 * Plain TypeScript with no `next` import, so it can be tested with `node --test` directly.
 */

/** One row of `GET /api/v2/cda/site/{siteId}/redirects`. */
export interface Redirect {
  from: string;
  to: string;
  permanent: boolean;
}

/** Where a moved path goes, and with which status. */
export interface Moved {
  destination: string;
  status: 301 | 302;
}

/**
 * The spelling Shio matches paths in: repeated slashes collapsed, one leading slash, no trailing
 * one (the root stays `/`). Case is kept, as it is on the server.
 */
export function canonicalPath(path: string): string {
  const collapsed = ("/" + (path ?? "")).replace(/\/{2,}/g, "/");
  // Repeated slashes are already collapsed, so there is at most one to take off the end.
  return collapsed.length > 1 ? collapsed.replace(/\/$/, "") : collapsed;
}

/** The row for this path, or `null` when it did not move. */
export function findRedirect(
  table: readonly Redirect[] | null | undefined,
  path: string
): Moved | null {
  const wanted = canonicalPath(path);
  for (const row of table ?? []) {
    if (row.to && canonicalPath(row.from) === wanted) {
      return { destination: row.to, status: row.permanent ? 301 : 302 };
    }
  }
  return null;
}

/**
 * A cached lookup over a table loaded by `load`.
 *
 * The table is small and changes rarely, so it is held for `ttlMs` (the same 60 seconds the pages'
 * ISR window uses) rather than fetched per request. A load that fails keeps the last table it had —
 * an unreachable CMS must never turn every request into an error — and is retried after the same
 * interval rather than on the next request.
 */
export function redirectLookup(
  load: () => Promise<readonly Redirect[] | null>,
  ttlMs = 60_000,
  now: () => number = Date.now
): (path: string) => Promise<Moved | null> {
  let table: readonly Redirect[] = [];
  let loadedAt = Number.NEGATIVE_INFINITY;
  // Requests arriving while a load is in flight wait for that load rather than starting their own.
  let pending: Promise<void> | null = null;
  return async (path: string) => {
    if (!pending && now() - loadedAt >= ttlMs) {
      loadedAt = now();
      pending = load()
        .then((loaded) => {
          table = loaded ?? [];
        })
        .catch(() => {
          // Keep the last table: see the note above.
        })
        .finally(() => {
          pending = null;
        });
    }
    if (pending) await pending;
    return findRedirect(table, path);
  };
}

/**
 * The loader `middleware.ts` uses: the CDA's redirect table for one site.
 *
 * It calls the endpoint with `fetch` rather than through `@viglet/shio-client`, because the SDK's
 * `getRedirects` is newer than the release this template installs. A Shio instance that predates the
 * endpoint answers 404, which is an empty table here rather than an error.
 */
export function cdaRedirects(
  baseUrl: string,
  siteId: string,
  token?: string
): () => Promise<Redirect[] | null> {
  return async () => {
    if (!siteId) return null;
    const url = `${baseUrl.replace(/\/+$/, "")}/api/v2/cda/site/${encodeURIComponent(siteId)}/redirects`;
    const response = await fetch(url, {
      headers: { Accept: "application/json", ...(token ? { Key: token } : {}) },
    });
    if (response.status === 404) return [];
    if (!response.ok) throw new Error(`redirect table: HTTP ${response.status}`);
    return (await response.json()) as Redirect[];
  };
}
