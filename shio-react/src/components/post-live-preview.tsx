import { useCallback, useEffect, useLayoutEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { Monitor, RefreshCw, Smartphone, Tablet } from "lucide-react"

import { Button } from "@/components/ui/button"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { ShProposedRenderService } from "@/services/insights/insights.service"
import { isCancelled } from "@/lib/read-options"

const service = new ShProposedRenderService()

/** Long enough that a pause in typing is a pause, not a gap between two words. */
const QUIET_MS = 1500

/** The three widths worth looking at, named rather than free-form: a slider invites fiddling. */
const WIDTHS = {
  phone: 390,
  tablet: 768,
  desktop: 0,
} as const

type Width = keyof typeof WIDTHS

interface Props {
  /** The page being edited, as an SH79 address. */
  address: string
  /** The form's current values — what the preview is a preview *of*. */
  data: Record<string, unknown>
  /** Bumped by the editor on a save, so the frame refreshes even with live rendering off. */
  revision: number
  /**
   * SH1167 — whether the site binds a layout to this post's type: `false` asks nothing and says so,
   * `undefined` waits for the answer, and `true` renders as before. Required and never defaulted:
   * a destructuring default turns the caller's `undefined` into `true`, which is how the first
   * version still rendered every menu once before its site had answered.
   */
  renderable: boolean | undefined
}

/**
 * SH1014 — the page as it would look, from values nobody has saved.
 *
 * <p>A curator could not see a change without saving it: the preview token addresses a post id and
 * the preview route renders persisted state, so "what will this look like" meant committing to it
 * first. This renders the values on screen, through the same page render service `/preview` uses,
 * and writes nothing.</p>
 *
 * <h2>Live is a switch, and it is off</h2>
 *
 * <p>The design called for a debounced re-render. It is here, behind a switch that starts off,
 * because each render composes a whole page on the server — layout, regions, theme — and a curator
 * who opened the editor to fix a typo has not asked for one of those per pause in their typing.
 * P3 is a budget on tokens and this is the same argument about a heavier unit. Off, the frame still
 * refreshes on demand and on every save, which is the question being asked most of the time.</p>
 *
 * <h2>srcdoc, not a URL</h2>
 *
 * <p>There is no address that serves this page — that is the whole point, the document does not
 * exist anywhere. The markup comes back from a POST and goes into the frame directly, sandboxed:
 * a preview renders content from this instance, but it is still a page being displayed inside the
 * console's own origin, and the console's session is exactly what it must not be able to reach.</p>
 */
export function PostLivePreview({ address, data, revision, renderable }: Readonly<Props>) {
  const { t } = useTranslation()
  const [html, setHtml] = useState<string>("")
  const [width, setWidth] = useState<Width>("desktop")
  const [live, setLive] = useState(false)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  // The latest values, so a render that fires after a keystroke sends what is on screen now rather
  // than what was there when the timer was armed. SH1136 — written after the commit, not during the
  // render: a ref assigned in render is a value React may throw away with the render it came from.
  const latest = useRef(data)
  useLayoutEffect(() => {
    latest.current = data
  })

  /*
   * SH1116 — a render composes a whole page on the server, so with live rendering on a slow one can
   * land after the render a later pause asked for, and the frame shows values the curator has since
   * typed over. Each render aborts the one before it, and unmounting aborts the last; only a render
   * nobody superseded may write the frame, the flag or the spinner.
   */
  const inflight = useRef<AbortController | null>(null)

  const render = useCallback(() => {
    inflight.current?.abort()
    const abort = new AbortController()
    inflight.current = abort
    setLoading(true)
    setFailed(false)
    return service.html(address, latest.current, { signal: abort.signal })
      .then(setHtml)
      // A page with no layout cannot render, which is a fact about the page rather than an error:
      // the editor still works, and a dialog about it would be in the way of the fix. A superseded
      // render is neither, and must not raise the flag over the render that replaced it.
      .catch((reason: unknown) => {
        if (!isCancelled(reason)) setFailed(true)
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false)
      })
  }, [address])

  const cancel = useCallback(() => inflight.current?.abort(), [])

  // On mount and on every save the curator asked for. SH1136 — scheduled, like the render a pause in
  // typing asks for below, so the spinner is raised from the timer's callback and not from the effect
  // body: the render is a request to an external system, and this is the effect subscribing to it.
  // SH1167 — only a type the site renders as a page is asked for, and only once that is known.
  const asks = renderable === true
  useEffect(() => {
    if (!asks) return undefined
    const timer = setTimeout(() => void render(), 0)
    return () => {
      clearTimeout(timer)
      cancel()
    }
  }, [asks, render, cancel, revision])

  // And on a pause in typing, only while the switch is on. By value: the caller rebuilds `data`
  // every render, so depending on the object would re-arm this timer forever and never fire it.
  const typed = JSON.stringify(data)
  useEffect(() => {
    if (!live || !asks) return undefined
    const timer = setTimeout(() => void render(), QUIET_MS)
    return () => clearTimeout(timer)
  }, [live, asks, render, typed])

  if (renderable === false) {
    return <p className="text-xs text-muted-foreground">{t("insights.previewNoLayout")}</p>
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => void render()} disabled={!asks}
          loading={loading}>
          {!loading && <RefreshCw className="h-3.5 w-3.5 mr-1" />}
          {t("insights.previewRefresh")}
        </Button>

        <div className="flex items-center gap-1">
          <WidthButton icon={<Smartphone className="h-3.5 w-3.5" />} value="phone"
            current={width} onPick={setWidth} label={t("insights.widthPhone")} />
          <WidthButton icon={<Tablet className="h-3.5 w-3.5" />} value="tablet"
            current={width} onPick={setWidth} label={t("insights.widthTablet")} />
          <WidthButton icon={<Monitor className="h-3.5 w-3.5" />} value="desktop"
            current={width} onPick={setWidth} label={t("insights.widthDesktop")} />
        </div>

        <div className="flex items-center gap-2 ml-auto">
          <Switch id="sh-live-preview" checked={live} onCheckedChange={setLive} />
          <Label htmlFor="sh-live-preview" className="text-xs">{t("insights.previewLive")}</Label>
        </div>
      </div>

      {failed && <p className="text-xs text-muted-foreground">{t("insights.previewUnavailable")}</p>}

      <div className="rounded-md border bg-muted/20 p-2 overflow-auto">
        <iframe
          title={t("insights.previewFrame")}
          srcDoc={html}
          // Scripts run because a page whose scripts do not run is not what a visitor gets, which
          // is the whole question. `allow-same-origin` is deliberately absent: the frame is then a
          // unique origin and cannot read the console's session, cookies or storage.
          sandbox="allow-scripts"
          className="w-full h-[480px] bg-background border-0 mx-auto block"
          style={WIDTHS[width] ? { width: `${WIDTHS[width]}px` } : undefined}
        />
      </div>

      <p className="text-[10px] text-muted-foreground">{t("insights.previewNotSaved")}</p>
    </div>
  )
}

function WidthButton({ icon, value, current, onPick, label }: Readonly<{
  icon: React.ReactNode
  value: Width
  current: Width
  onPick: (width: Width) => void
  label: string
}>) {
  return (
    <Button
      type="button"
      variant={current === value ? "secondary" : "ghost"}
      size="icon"
      className="h-7 w-7"
      aria-pressed={current === value ? "true" : "false"}
      aria-label={label}
      onClick={() => onPick(value)}
    >
      {icon}
    </Button>
  )
}
