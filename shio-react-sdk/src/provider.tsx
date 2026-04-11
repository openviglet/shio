import { createContext, useContext, useMemo } from "react";
import type { ReactNode } from "react";
import { ShioClient } from "./client";
import type { ShioConfig } from "./types";

const ShioContext = createContext<ShioClient | null>(null);

/** Provides the Shio CMS client to all child components */
export function ShioProvider({
  baseUrl,
  apiToken,
  children,
}: ShioConfig & { children: ReactNode }) {
  const client = useMemo(
    () => new ShioClient({ baseUrl, apiToken }),
    [baseUrl, apiToken]
  );

  return (
    <ShioContext.Provider value={client}>{children}</ShioContext.Provider>
  );
}

/** Access the Shio client from context */
export function useShioClient(): ShioClient {
  const client = useContext(ShioContext);
  if (!client) {
    throw new Error("useShioClient must be used within a <ShioProvider>");
  }
  return client;
}
