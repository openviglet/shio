import { useEffect } from "react"

/**
 * SH1011 — the browser asks before unsaved work is thrown away.
 *
 * <p>Closing the tab mid-edit lost everything silently. `beforeunload` is what the browser offers
 * for that, and it covers the three ways a page leaves without the router being involved at all:
 * the tab closes, the page reloads, the reader follows a link out of the console.</p>
 *
 * <h2>What this deliberately does not do</h2>
 *
 * <p>It does not block an in-app route change. React Router 7's `useBlocker` — and
 * `unstable_usePrompt`, which wraps it — require a data router, and this console mounts
 * `<BrowserRouter>`; calling either here throws rather than degrading. SH1090 is the change that
 * makes it possible, and until it lands a page guards its own exits (a Cancel that asks) and the
 * browser guards the rest.</p>
 *
 * <p>The listener is registered only while there is something to lose. A permanently-registered
 * handler makes every reload of every clean page show the browser's dialog, which is how a guard
 * becomes the thing people click through without reading.</p>
 */
export function useUnsavedGuard(dirty: boolean) {
  useEffect(() => {
    if (!dirty) return undefined
    const ask = (event: BeforeUnloadEvent) => {
      // Both, because browsers disagree about which one arms the dialog and neither shows our
      // words: the sentence is the browser's, and the only decision here is whether to ask.
      event.preventDefault()
      event.returnValue = ""
    }
    globalThis.addEventListener("beforeunload", ask)
    return () => globalThis.removeEventListener("beforeunload", ask)
  }, [dirty])
}
