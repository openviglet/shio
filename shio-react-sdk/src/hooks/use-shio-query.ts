import type { ShioListing, ShioQueryParams } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/** Query published posts by site / folder / post type (always returns a listing). */
export function useShioQuery(params?: ShioQueryParams) {
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
    ]
  );
}
