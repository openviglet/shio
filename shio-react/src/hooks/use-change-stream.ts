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
 *
 * <p>SH968 — one connection per scope, shared. The shell now listens for agent runs on every
 * screen, and each open stream is a subscription the server polls every second, so the Activity
 * page and the working line join the shell's connection rather than opening their own. The last
 * listener to leave closes it.</p>
 */
export function useChangeStream(onChanged: () => void, site?: string) {
  const handler = useRef(onChanged)
  handler.current = onChanged

  useEffect(() => subscribeChanges(site, () => handler.current()), [site])
}

/** What one `changes` event carries: the rows this reader may see, as the feed shapes them. */
export interface ChangePage {
  changes?: StreamChange[]
}

/** The fields of a feed row the dock reads (`ShAgentChanges.Change`). */
export interface StreamChange {
  action: string
  address?: string
  at?: string
  by?: string
  /** The agent run that wrote it; absent on a person's own write (SH999). */
  agent?: string
  agentName?: string
  what?: string
  site?: string
  review?: { decision: string }
}

interface Shared {
  source: EventSource
  listeners: Set<(page?: ChangePage) => void>
}

const pool = new Map<string, Shared>()

/**
 * SH968 — listen to the stream for one scope, with each event's page, through the shared
 * connection. A page that does not parse still notifies, without a payload, because "something
 * changed" is true either way.
 */
export function subscribeChanges(site: string | undefined, listener: (page?: ChangePage) => void) {
  if (typeof EventSource === "undefined") return () => {}
  const key = site ?? ""
  let shared = pool.get(key)
  if (!shared) {
    const base = `${import.meta.env.VITE_API_URL}/api/v2/events`
    const source = new EventSource(site ? `${base}?site=${encodeURIComponent(site)}` : base,
      { withCredentials: true })
    const created: Shared = { source, listeners: new Set() }
    source.addEventListener("changes", (event) => {
      let page: ChangePage | undefined
      try {
        page = JSON.parse((event as MessageEvent<string>).data) as ChangePage
      } catch {
        page = undefined
      }
      for (const each of [...created.listeners]) each(page)
    })
    pool.set(key, created)
    shared = created
  }
  shared.listeners.add(listener)
  const held = shared
  return () => {
    held.listeners.delete(listener)
    if (held.listeners.size === 0) {
      held.source.close()
      pool.delete(key)
    }
  }
}
