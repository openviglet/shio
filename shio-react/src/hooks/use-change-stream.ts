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
  /** SH969 — what failed in the same range, such as a scheduled publish (`ShAgentChanges.Incident`). */
  incidents?: StreamIncident[]
}

export interface StreamIncident {
  kind: string
  /** `id:<uuid>` when the incident is about a post. */
  subject?: string
  message?: string
  fix?: string
  at?: string
  /** One batch op that attempts the failed transition now. */
  retry?: { op: string; address: string }
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
  /** SH970 — present only on the `CREATE` a site's form wrote. */
  submission?: { title?: string; summary?: string }
}

interface Shared {
  source: EventSource
  listeners: Set<ChangeListener>
}

/** A page, and the feed cursor after it (the event's id), to resume from later. */
type ChangeListener = (page?: ChangePage, cursor?: string) => void

const pool = new Map<string, Shared>()

/**
 * SH968 — listen to the stream for one scope, with each event's page, through the shared
 * connection. A page that does not parse still notifies, without a payload, because "something
 * changed" is true either way.
 */
export function subscribeChanges(site: string | undefined, listener: ChangeListener) {
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
      // A copy: a listener may leave while it is being told, and the set must not move under it.
      const cursor = (event as MessageEvent<string>).lastEventId || undefined
      for (const each of [...created.listeners]) each(page, cursor)
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

/**
 * SH969 — replay the feed from a cursor, through the stream, and stop once it has caught up.
 *
 * <p>For what happened while no screen was open. It is a connection of its own, never the shared
 * one, because a replayed page is old news: the shared listeners (the Activity line, the agent-run
 * tracker) would take a week of rows for things happening now. It goes through the stream rather
 * than `GET /agent/changes` because the hub applies the reader's content ACL to every row and
 * incident, and the agent feed does not. The stream has no "caught up" event, so it closes after
 * `quietMs` with nothing new, or after `maxMs` whatever happens.</p>
 */
export function replayChanges(since: string, listener: ChangeListener,
  { quietMs = 3000, maxMs = 30000 }: { quietMs?: number; maxMs?: number } = {}) {
  if (typeof EventSource === "undefined") return () => {}
  const source = new EventSource(
    `${import.meta.env.VITE_API_URL}/api/v2/events?since=${encodeURIComponent(since)}`,
    { withCredentials: true })
  let quiet = setTimeout(() => stop(), quietMs)
  const cap = setTimeout(() => stop(), maxMs)
  function stop() {
    clearTimeout(quiet)
    clearTimeout(cap)
    source.close()
  }
  source.addEventListener("changes", (event) => {
    clearTimeout(quiet)
    quiet = setTimeout(() => stop(), quietMs)
    let page: ChangePage | undefined
    try {
      page = JSON.parse((event as MessageEvent<string>).data) as ChangePage
    } catch {
      page = undefined
    }
    listener(page, (event as MessageEvent<string>).lastEventId || undefined)
  })
  return stop
}
