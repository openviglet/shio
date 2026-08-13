import type { ShDiscoveryAPI } from "@/models/auth/discovery";

/**
 * SH704 — a module-scope value with subscribers, which is what `useSyncExternalStore` is for.
 *
 * <h2>What this replaces</h2>
 *
 * <p>`useDiscovery` used to read the cache into component state and keep the two in step with an
 * effect:</p>
 *
 * <pre>
 *   useEffect(() => {
 *     if (cached) { if (data !== cached) setData(cached); return; }
 *     let alive = true;
 *     fetchDiscovery().then((d) => { if (alive) setData(d); });
 *     return () => { alive = false; };
 *   }, [data]);
 * </pre>
 *
 * <p>Three things wrong with it, and they are one thing. The synchronous branch republished a value
 * the component <em>already had</em> — `useState(cached)` reads it at mount — one render late, for
 * every mount after the first, which is every mount. The `alive` flag existed because the second
 * copy could outlive the component holding it. And the effect depended on `data`, so it re-ran on
 * the state it had just set.</p>
 *
 * <p>None of that is a bug in the effect. It is what keeping a second copy costs. A subscription
 * has no second copy: React reads the value at render, re-reads when told, and drops the listener
 * on unmount — which is the `alive` flag as a property rather than as a variable somebody
 * remembered to write.</p>
 *
 * <h2>Why it lives here, and takes its fetcher</h2>
 *
 * <p>So it can be driven. The old shape was reachable only through a rendered component and a real
 * request, which is why its defects sat behind an eslint suppression instead of behind a test. In
 * `src/lib` with a type-only import, `test/use-discovery.test.mjs` can load it under plain Node and
 * assert what this module actually promises: one request for many callers, a stable reference, a
 * listener dropped on unsubscribe, and a failure that retries rather than wedging.</p>
 */
export interface DiscoveryStore {
  /** Register a listener; the returned function removes it. The `useSyncExternalStore` contract. */
  subscribe(listener: () => void): () => void;
  /**
   * The value, or `undefined` while it has never landed.
   *
   * <p>Returns the <b>same reference</b> until it changes, which is not a nicety:
   * `useSyncExternalStore` re-renders whenever the snapshot is not `Object.is`-equal to the
   * previous one, so a getter that built a fresh object would render forever.</p>
   */
  get(): ShDiscoveryAPI | undefined;
  /** Fetch once, however many callers ask. Resolves to the cached value on every later call. */
  load(): Promise<ShDiscoveryAPI>;
  /** Forget everything — for a test, and for a sign-out that changes what the instance supports. */
  reset(): void;
}

export function createDiscoveryStore(fetcher: () => Promise<ShDiscoveryAPI>): DiscoveryStore {
  let cached: ShDiscoveryAPI | undefined;
  let inflight: Promise<ShDiscoveryAPI> | undefined;
  const listeners = new Set<() => void>();

  return {
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    get() {
      return cached;
    },
    load() {
      if (cached) return Promise.resolve(cached);
      // `??=` so N components mounting in one pass share one request — the reason this cache
      // exists: the header, the login form and admin settings each ask on first paint.
      inflight ??= fetcher()
        .then((data) => {
          cached = data;
          inflight = undefined;
          // A copy, because a listener may unsubscribe while being notified — which is exactly
          // what an unmount during this pass does.
          for (const listener of [...listeners]) listener();
          return data;
        })
        .catch((err) => {
          // Cleared on the way out, so a failed discovery is retried by the next mount rather
          // than remembered as a promise that will never resolve again.
          inflight = undefined;
          throw err;
        });
      return inflight;
    },
    reset() {
      cached = undefined;
      inflight = undefined;
      listeners.clear();
    },
  };
}
