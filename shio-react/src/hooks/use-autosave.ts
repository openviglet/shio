import { useEffect, useLayoutEffect, useRef, useState } from "react"

/** How long the typing has to stop before the draft is written. */
const QUIET_MS = 3000

/** What the save bar is saying about the draft on the server. */
export type AutosaveState =
  | { status: "idle" }
  | { status: "saving" }
  | { status: "saved"; at: number }
  | { status: "stopped"; reason: string }

/**
 * SH1011 — the draft is written while the curator works, and stops the moment it cannot be.
 *
 * <h2>Why a draft, and only a draft</h2>
 *
 * <p>Autosaving is a write nobody asked for, which is exactly the shape P7 refuses — so it is
 * confined to the one row where a write is invisible to a visitor. The published page is untouched;
 * the explicit Publish is still the only thing that changes what anybody sees.</p>
 *
 * <h2>Why it stops rather than retries</h2>
 *
 * <p>A conditional write refused with a 409 means somebody else wrote to this post. Retrying is the
 * overwrite the guard exists to prevent, and retrying <em>silently, on a timer</em> is that
 * overwrite with nobody watching. So the first refusal ends autosave for this page and hands the
 * reason back; the curator's own Save still works, and meets the conflict view as it always
 * did.</p>
 *
 * <p>It saves after the typing stops rather than on an interval, so a curator mid-sentence is never
 * racing their own editor, and it never fires while a save the curator asked for is in flight.</p>
 */
export function useAutosave(options: {
  dirty: boolean
  /** Paused while the curator's own save, publish or dialog is running. */
  busy: boolean
  /** Something to send, recomputed by the caller — `{}` means nothing changed. */
  attrs: Record<string, unknown>
  save: (attrs: Record<string, unknown>) => Promise<void>
}): AutosaveState {
  const { dirty, busy, attrs, save } = options
  const [state, setState] = useState<AutosaveState>({ status: "idle" })
  const stopped = useRef(false)
  // The latest of each, so the timer closes over a ref rather than over the values it was armed
  // with — an autosave that sent the document from three seconds ago would undo what was typed
  // since, which is the failure that makes people turn autosave off. SH1136 — written after the
  // commit rather than during the render, which is a render React may discard; the timer below fires
  // well after any commit, so it always reads the values on screen.
  const latest = useRef({ attrs, save })
  useLayoutEffect(() => {
    latest.current = { attrs, save }
  })

  const pending = Object.keys(attrs).length > 0
  const signature = JSON.stringify(attrs)

  useEffect(() => {
    if (!dirty || busy || !pending || stopped.current) return undefined
    const timer = setTimeout(() => {
      setState({ status: "saving" })
      latest.current.save(latest.current.attrs)
        .then(() => setState({ status: "saved", at: Date.now() }))
        .catch((error: unknown) => {
          stopped.current = true
          setState({
            status: "stopped",
            reason: (error as { message?: string })?.message ?? "",
          })
        })
    }, QUIET_MS)
    return () => clearTimeout(timer)
    // By VALUE rather than by identity: the caller rebuilds `attrs` on every render, so depending
    // on the object would re-arm the timer forever and never fire — and depending only on its key
    // list would fire three seconds after the FIRST keystroke rather than after the typing stops,
    // which is a different feature wearing this one's name.
  }, [dirty, busy, pending, signature])

  return state
}
