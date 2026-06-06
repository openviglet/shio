import type { ShioPost } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/** Get a published post by id (attributes expanded). `null` when not found. */
export function useShioPost(postId: string | undefined) {
  const client = useShioClient();
  return useAsync<ShioPost | null>(
    (signal) => client.getPost(postId!, { signal }),
    [client, postId],
    !!postId
  );
}

/**
 * Resolve a published post by site + friendly URL — the client-side analog of
 * the catch-all route. `null` when unresolved.
 */
export function useShioPostByUrl(
  siteId: string | undefined,
  url: string | undefined
) {
  const client = useShioClient();
  return useAsync<ShioPost | null>(
    (signal) => client.getPostByUrl(siteId!, url!, { signal }),
    [client, siteId, url],
    !!siteId && !!url
  );
}
