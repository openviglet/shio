import type { ShioListing, ShioPageParams } from "@viglet/shio-client";
import { useShioClient } from "../provider";
import { useAsync } from "./use-async";

/** List a folder's children (subfolders + published posts). `null` when the folder is unknown. */
export function useShioChildren(
  folderId: string | undefined,
  params?: ShioPageParams
) {
  const client = useShioClient();
  return useAsync<ShioListing | null>(
    (signal) => client.listChildren(folderId!, params, { signal }),
    [client, folderId, params?.page, params?.size],
    !!folderId
  );
}
