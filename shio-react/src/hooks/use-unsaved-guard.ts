import { useEffect } from "react"
import { useTranslation } from "react-i18next"
import { useBlocker } from "react-router-dom"

/**
 * SH1011/SH1090 — nothing throws away unsaved work without asking first.
 *
 * <p>Two exits, two mechanisms. `beforeunload` covers the ways a page leaves without the router:
 * the tab closes, the page reloads, the reader follows a link out of the console. `useBlocker`
 * covers every way it leaves *through* the router: the rail, the command palette, a link in the
 * page, Back. The second one needs a data router, which the console did not have until SH1090
 * (`createBrowserRouter` in `App.tsx`); before that it threw, and SH1011 shipped with a note saying
 * so and each page guarding its own Cancel by hand.</p>
 *
 * <p>Only a change of <em>page</em> is blocked. A list writing its filters to the URL changes the
 * search, not the path, and asking about that would put a dialog in front of every keystroke.</p>
 *
 * <p>Both are armed only while there is something to lose. A guard that asks on every clean page is
 * how a guard becomes the thing people click through without reading.</p>
 *
 * @param message what to ask on an in-app navigation; the browser writes its own words for a tab
 *                close and ignores any a page offers
 */
export function useUnsavedGuard(dirty: boolean, message?: string) {
  const { t } = useTranslation()

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

  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    dirty && currentLocation.pathname !== nextLocation.pathname)

  useEffect(() => {
    if (blocker.state !== "blocked") return
    if (globalThis.confirm(message ?? t("content.discardUnsaved"))) blocker.proceed()
    else blocker.reset()
  }, [blocker, message, t])
}
