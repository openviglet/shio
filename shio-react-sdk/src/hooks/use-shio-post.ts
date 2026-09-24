import type { ShioContentState, ShioPost } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/**
 * Get a published post by id (attributes expanded). `null` when not found.
 *
 * SH1081 — `state` asks for another version (`"draft"`, `"rev:<n>"`), which needs a preview token.
 */
export function useShioPost(postId: string | undefined, state?: ShioContentState) {
  const client = useShioClient();
  return useAsync<ShioPost | null>(
    (signal) => client.getPost(postId!, { signal, state }),
    [client, postId, state],
    !!postId
  );
}

/**
 * Resolve a published post by site + friendly URL — the client-side analog of
 * the catch-all route. `null` when unresolved.
 *
 * SH1081 — `options.locale` pins a translation and `options.state` asks for another version, as
 * the client's `getPostByUrl` takes them.
 */
export function useShioPostByUrl(
  siteId: string | undefined,
  url: string | undefined,
  options?: { locale?: string; state?: ShioContentState }
) {
  const client = useShioClient();
  const locale = options?.locale;
  const state = options?.state;
  return useAsync<ShioPost | null>(
    (signal) => locale
      ? client.getPostByUrl(siteId!, url!, locale, { signal, state })
      : client.getPostByUrl(siteId!, url!, { signal, state }),
    [client, siteId, url, locale, state],
    !!siteId && !!url
  );
}
