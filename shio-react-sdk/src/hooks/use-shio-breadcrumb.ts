import type { ShioPath } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/**
 * Get the breadcrumb path (site + root→current folder chain) for a folder or
 * post. `null` when the object is unknown.
 */
export function useShioBreadcrumb(objectId: string | undefined) {
  const client = useShioClient();
  return useAsync<ShioPath | null>(
    (signal) => client.getPath(objectId!, { signal }),
    [client, objectId],
    !!objectId
  );
}
