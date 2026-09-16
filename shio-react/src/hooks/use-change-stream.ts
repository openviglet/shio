import { useEffect, useRef } from "react"

/**
 * SH1005 — the console's half of SH967's stream: a screen that is open learns that something
 * changed.
 *
 * <p>It carries no payload to the caller on purpose. The stream's own page is read with the
 * rights of whoever opened it and in the feed's order; a screen that rendered those rows
 * directly would be a third reader of the audit trail, with its own idea of filters and
 * position. So this says only *that* the trail moved, and the screen re-asks the reader it
 * already has, with the filters the curator actually set.</p>
 *
 * <p>An `EventSource` reconnects on its own, so an error handler that tears the connection down
 * would turn a momentary blip into a page that never updates again. A failed stream degrades to
 * the refresh button, which is what the page had before.</p>
 */
export function useChangeStream(onChanged: () => void, site?: string) {
  const handler = useRef(onChanged)
  handler.current = onChanged

  useEffect(() => {
    if (typeof EventSource === "undefined") return
    const base = `${import.meta.env.VITE_API_URL}/api/v2/events`
    const source = new EventSource(site ? `${base}?site=${encodeURIComponent(site)}` : base,
      { withCredentials: true })
    const notify = () => handler.current()
    source.addEventListener("changes", notify)
    return () => {
      source.removeEventListener("changes", notify)
      source.close()
    }
  }, [site])
}
