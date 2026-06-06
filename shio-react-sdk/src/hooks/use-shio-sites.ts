import type { ShioSite } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/** List all published sites. */
export function useShioSites() {
  const client = useShioClient();
  return useAsync<ShioSite[]>((signal) => client.getSites({ signal }), [client]);
}

/** Get one site (with its root folder id) by id. */
export function useShioSite(siteId: string | undefined) {
  const client = useShioClient();
  return useAsync<ShioSite | null>(
    (signal) => client.getSite(siteId!, { signal }),
    [client, siteId],
    !!siteId
  );
}
