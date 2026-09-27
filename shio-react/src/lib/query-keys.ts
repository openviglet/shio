/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1046 — a cache key is the address every other surface already uses.
 *
 * <h2>P10 applied to the cache</h2>
 *
 * <p>SH79's grammar is what the MCP tools, the `shio` CLI, the agent surface and the console's own
 * routes all address content by: <code>post:&lt;site&gt;/&lt;url&gt;</code>,
 * <code>folder:&lt;site&gt;/&lt;chain&gt;</code>, <code>site:&lt;name&gt;</code>,
 * <code>postType:&lt;name&gt;</code>, <code>id:&lt;uuid&gt;</code>. A cache keyed on anything else
 * would be a fifth way to name one thing, and the first invalidation to spell it differently from
 * the fetch would be a screen that never refreshes — the failure that looks like a slow server.</p>
 *
 * <h2>Why the scheme is its own segment</h2>
 *
 * <p>A query key is an array and TanStack matches by prefix, so splitting
 * <code>"postType:Page"</code> into <code>["postType:", "Page"]</code> makes
 * <code>["postType:"]</code> match the catalogue <em>and</em> every single type under it. That is
 * the whole invalidation story for free: after a save, one call with the scheme drops everything
 * about that kind of thing. Keeping the address as one string would need a second list saying what
 * else to drop, which is the list that goes stale.</p>
 *
 * <h2>The trailing colon is not decoration</h2>
 *
 * <p><code>"postType:"</code> with nothing after it is the collection — the same spelling the
 * grammar uses for "everything of this kind". Writing the scheme without it (<code>"postType"</code>)
 * would make the key a word that happens to look like an address, and the next reader would have to
 * know which of the two was meant.</p>
 */

/**
 * The schemes a key may carry.
 *
 * <p>The first five are SH79's, spelled exactly as every other surface spells them. The rest are
 * console-local collections that have no address because nothing outside the console addresses
 * them — they take the same shape so that there is one rule rather than two.</p>
 */
export const SCHEMES = [
  "post:", "folder:", "site:", "postType:", "id:",
  "widget:", "group:", "role:", "user:", "token:", "webhook:", "staticFile:",
  "authProvider:", "exchangeProvider:", "tenant:", "summary:",
  // SH1116 — the one singleton among them: there is one email configuration per tenant, so the key
  // carries no id. It is a scheme rather than a bare string for the reason `addressKey` throws on an
  // unknown one: every invalidation names a scheme, so a key outside this list would cache and never
  // be invalidated.
  "emailConfig:",
  /*
   * SH1152 — four more console-local collections, each a scheme of its own rather than a suffix under
   * `summary:`. The reason is invalidation: it matches by the key's first segment, so folding these
   * together would mean adding a note dropped the locale catalogue with it. A scheme is what lets one
   * write invalidate exactly what it changed.
   */
  "note:", "translation:", "insights:", "locale:",
] as const;

export type ShScheme = (typeof SCHEMES)[number];

/** A query key: the scheme, then the rest of the address when there is any. */
export type ShQueryKey = readonly [ShScheme] | readonly [ShScheme, string];

/**
 * The key for an address, or a throw.
 *
 * <p>A throw and not a fallback: a key built from an unknown scheme would cache and never be
 * invalidated, because every invalidation names a scheme. That is a screen showing yesterday's
 * content with no error anywhere — so the typo has to fail where it is written.</p>
 *
 * @param address `"postType:"` for the catalogue, `"postType:Page"` for one, and so on
 */
export function addressKey(address: string): ShQueryKey {
  const scheme = SCHEMES.find((one) => address.startsWith(one));
  if (!scheme) {
    throw new Error(`SH1046: "${address}" carries no known address scheme. A cache key is an `
      + `address, so it is spelled the way every other surface spells it: ${SCHEMES.join(" ")}`);
  }
  const rest = address.slice(scheme.length);
  return rest ? [scheme, rest] : [scheme];
}

/** The address a key came from — the round trip, so a key can be read back in a report or a log. */
export function keyAddress(key: ShQueryKey): string {
  return key.length === 1 ? key[0] : `${key[0]}${key[1]}`;
}
