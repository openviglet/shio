import type { ShioListing, ShioQueryParams } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/**
 * Query published posts by site / folder / post type (always returns a listing).
 *
 * Pass `enabled: false` to defer the request until its inputs are ready — e.g.
 * while a `siteId` is still being resolved from {@link useShioSites}. While
 * disabled the hook stays idle (`loading: false`, `data: undefined`) and never
 * fires a request with half-resolved params.
 */
export function useShioQuery(params?: ShioQueryParams, enabled = true) {
  const client = useShioClient();
  return useAsync<ShioListing>(
    (signal) => client.query(params, { signal }),
    [
      client,
      params?.siteId,
      params?.folderId,
      params?.postType,
      params?.page,
      params?.size,
    ],
    enabled
  );
}
