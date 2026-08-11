import { useEffect, useState } from "react";
import { ShAuthorizationService } from "@/services/auth/authorization.service";
import type { ShDiscoveryAPI } from "@/models/auth/discovery";

/**
 * Module-level cache so multiple components (header, login, admin settings,
 * …) don't each issue their own request to /discovery.
 */
let cached: ShDiscoveryAPI | undefined;
let inflight: Promise<ShDiscoveryAPI> | undefined;

function fetchDiscovery(): Promise<ShDiscoveryAPI> {
  if (cached) return Promise.resolve(cached);
  inflight ??= new ShAuthorizationService()
    .discovery()
    .then((data) => {
      cached = data;
      inflight = undefined;
      return data;
    })
    .catch((err) => {
      inflight = undefined;
      throw err;
    });
  return inflight;
}

/**
 * Read the cached discovery response. Fetches once on first use.
 * Returns `undefined` while loading, so callers can render defaults.
 */
export function useDiscovery(): ShDiscoveryAPI | undefined {
  const [data, setData] = useState<ShDiscoveryAPI | undefined>(cached);

  useEffect(() => {
    if (cached) {
      if (data !== cached) setData(cached);
      return;
    }
    // SH665 — the flag needs the cleanup that clears it, and never had one. `prefer-const` was
    // the tell: a guard that is only ever `true` guards nothing, so a response arriving after the
    // component unmounted still called `setData`. The lint has reported this since the day it was
    // written and nothing ran the lint.
    let alive = true;
    fetchDiscovery()
      .then((d) => {
        if (alive) setData(d);
      })
      .catch(() => {
        // discovery failed — keep undefined, components fall back to defaults
      });
    return () => {
      alive = false;
    };
  }, [data]);

  return data;
}
