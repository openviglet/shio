import { useEffect, useSyncExternalStore } from "react";
import { ShAuthorizationService } from "@/services/auth/authorization.service";
import { createDiscoveryStore } from "@/lib/discovery-store";
import type { ShDiscoveryAPI } from "@/models/auth/discovery";

/**
 * The one this console uses. The store itself is in `@/lib/discovery-store` (SH704), which is
 * where it can be driven by a test — this file is the wiring and nothing else.
 */
export const discoveryStore = createDiscoveryStore(() => new ShAuthorizationService().discovery());

/**
 * Read the cached discovery response. Fetches once on first use.
 * Returns `undefined` while loading, so callers can render defaults.
 */
export function useDiscovery(): ShDiscoveryAPI | undefined {
  const value = useSyncExternalStore(
    discoveryStore.subscribe,
    discoveryStore.get,
    discoveryStore.get,
  );

  useEffect(() => {
    // Nothing is set here — `load` writes to the store and the subscription delivers it. That is
    // the whole of SH704: the effect starts work, it does not maintain a copy of the answer.
    void discoveryStore.load().catch(() => {
      // Discovery failed; components fall back to their defaults, which is the documented state.
    });
  }, []);

  return value;
}
