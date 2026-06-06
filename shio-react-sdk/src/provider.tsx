import { createContext, useContext, useMemo } from "react";
import type { ReactNode } from "react";
import { ShioClient } from "@viglet/shio-client";
import type { ShioClientConfig } from "@viglet/shio-client";

const ShioContext = createContext<ShioClient | null>(null);

export type ShioProviderProps = { children: ReactNode } & (
  | ShioClientConfig
  | { client: ShioClient }
);

/**
 * Provides a {@link ShioClient} (from `@viglet/shio-client`) to all child
 * components. Either pass connection config (`baseUrl`, `apiToken`, ...) and the
 * provider builds the client, or pass a pre-built `client` (useful for sharing
 * one instance between SSR and the browser).
 */
export function ShioProvider(props: ShioProviderProps) {
  const { children } = props;
  const provided = "client" in props ? props.client : undefined;
  const config = provided ? undefined : (props as ShioClientConfig);

  const client = useMemo(
    () => provided ?? new ShioClient(config as ShioClientConfig),
    // Rebuild only when a meaningful connection field changes.
    [provided, config?.baseUrl, config?.apiToken, config?.fetch, config?.credentials]
  );

  return <ShioContext.Provider value={client}>{children}</ShioContext.Provider>;
}

/** Access the Shio client from context. */
export function useShioClient(): ShioClient {
  const client = useContext(ShioContext);
  if (!client) {
    throw new Error("useShioClient must be used within a <ShioProvider>");
  }
  return client;
}
