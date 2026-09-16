/**
 * SH1011 — the last thing typed, kept in this browser, offered and never imposed.
 *
 * ## What this is for, given autosave exists
 *
 * Autosave writes the draft after the typing stops, which leaves two windows it cannot cover: the
 * seconds between a keystroke and that write, and every case where the write cannot happen at all —
 * the instance is down, the laptop is offline, the session expired. In those the browser is the
 * only place the work exists.
 *
 * ## Offered, never restored silently
 *
 * A buffer that restored itself would be a second writer of the page with no name on it: a curator
 * who opens a post and finds text they do not remember typing has no way to tell whether it is
 * theirs, an agent's, or yesterday's. So the page loads what the server has, and *asks*.
 *
 * ## IndexedDB rather than localStorage
 *
 * A rich-text body is not small and `localStorage` is a few megabytes for the whole origin, shared
 * with everything else the console keeps. Filling it would break those too, and it throws rather
 * than degrading.
 */

const DB = "shio-drafts"
const STORE = "buffers"

export interface DraftBuffer {
  postId: string
  data: unknown
  /** When it was buffered, so the page can say how old the offer is. */
  at: number
}

/** Every access is wrapped: a private window, blocked site data or a quota refuses, and that is fine. */
function open(): Promise<IDBDatabase | null> {
  return new Promise((resolve) => {
    if (typeof indexedDB === "undefined") {
      resolve(null)
      return
    }
    const request = indexedDB.open(DB, 1)
    request.onupgradeneeded = () => {
      if (!request.result.objectStoreNames.contains(STORE)) {
        request.result.createObjectStore(STORE, { keyPath: "postId" })
      }
    }
    request.onsuccess = () => resolve(request.result)
    // No rejection: losing the buffer is not an error the page should report. The work is still on
    // screen, autosave is still running, and a dialog about browser storage helps nobody.
    request.onerror = () => resolve(null)
  })
}

function withStore<T>(mode: IDBTransactionMode,
                      run: (store: IDBObjectStore) => IDBRequest): Promise<T | null> {
  return open().then((db) => {
    if (!db) return null
    return new Promise<T | null>((resolve) => {
      try {
        const request = run(db.transaction(STORE, mode).objectStore(STORE))
        request.onsuccess = () => resolve(request.result as T)
        request.onerror = () => resolve(null)
      } catch {
        resolve(null)
      }
    })
  })
}

/** Keep what is on screen now. Called as the curator types; failure is silent by design. */
export function bufferDraft(postId: string, data: unknown): Promise<unknown> {
  return withStore("readwrite", (store) =>
    store.put({ postId, data, at: Date.now() } satisfies DraftBuffer))
}

/** What this browser kept for that post, or nothing. */
export function readBuffer(postId: string): Promise<DraftBuffer | null> {
  return withStore<DraftBuffer>("readonly", (store) => store.get(postId))
}

/** Forget it — after a save, or after the curator declines the offer. */
export function clearBuffer(postId: string): Promise<unknown> {
  return withStore("readwrite", (store) => store.delete(postId))
}
