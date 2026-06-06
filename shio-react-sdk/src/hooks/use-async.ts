import { useEffect, useState } from "react";

/** Result shape shared by all data hooks. */
export interface ShioAsyncState<T> {
  data: T | undefined;
  loading: boolean;
  error: Error | null;
}

/**
 * Internal helper that runs an abortable async fetch and tracks
 * loading/data/error. Aborts the in-flight request on dependency change or
 * unmount, and never sets state after an abort (no React warnings).
 *
 * @param fn      receives an AbortSignal to forward to the client call
 * @param deps    re-run trigger (same contract as useEffect deps)
 * @param enabled when false, the fetch is skipped and state resets to idle
 */
export function useAsync<T>(
  fn: (signal: AbortSignal) => Promise<T>,
  deps: unknown[],
  enabled = true
): ShioAsyncState<T> {
  const [state, setState] = useState<ShioAsyncState<T>>({
    data: undefined,
    loading: enabled,
    error: null,
  });

  useEffect(() => {
    if (!enabled) {
      setState({ data: undefined, loading: false, error: null });
      return;
    }
    const controller = new AbortController();
    setState((prev) => ({ ...prev, loading: true, error: null }));
    fn(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setState({ data, loading: false, error: null });
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) {
          setState({
            data: undefined,
            loading: false,
            error: err instanceof Error ? err : new Error(String(err)),
          });
        }
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  return state;
}
