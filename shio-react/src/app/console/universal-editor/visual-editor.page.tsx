import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { ROUTES } from "@/app/routes.const"
import {
  ArrowLeft,
  Loader2,
  Monitor,
  RefreshCw,
  Save,
  Smartphone,
  Upload,
} from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams, useSearchParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSiteService } from "@/services/site/site.service"
import { ShUniversalEditorService } from "@/services/universal-editor/universal-editor.service"
import { UE_TOKEN_KEY } from "./editor-sites.page"
import { labelSource, labelSourceKey, labelsFrom } from "./field-labels"
import type { ContentChange, SelectedElement } from "@/models/universal-editor/universal-editor.model"
import { siteDestination } from "@/lib/site-front-end"

const siteService = new ShSiteService()
const editorService = new ShUniversalEditorService()

/**
 * Ask the page for the SH185 bridge.
 *
 * Shio's renderer injects `shio-editor.js` only when the request carries this, so an ordinary
 * preview — the one a curator opens from **View Site** — ships no editor code at all. A front
 * end that has never heard of the flag ignores it, which is the behaviour the protocol wants:
 * the editor degrades to a plain iframe rather than failing.
 */
function withEditorFlag(url: string): string {
  return url + (url.includes("?") ? "&" : "?") + "editor=true"
}

/**
 * How long to wait for `sh:ready` after the frame has loaded (SH333).
 *
 * Generous on purpose. Being wrong in this direction costs a curator a few seconds of a badge
 * that already says "connecting"; being wrong in the other tells somebody their working page is
 * broken, which is the failure this task exists to remove rather than to invert.
 */
const HANDSHAKE_TIMEOUT_MS = 8000

/** Where the SH185 handshake has got to (SH333). */
type BridgeState = "waiting" | "ready" | "absent"

/** The badge, per handshake state (SH333). Amber, not red: see `BRIDGE_LABEL`. */
const BRIDGE_BADGE: Record<BridgeState, string> = {
  waiting: "",
  ready: "border-green-400 text-green-600",
  absent: "border-amber-400 text-amber-600",
}

/**
 * What the badge says. `absent` is deliberately **not** an error.
 *
 * No bridge has two causes that look identical from here and want opposite words: the page is an
 * error page, or it is a front end that has never heard of `?editor=true` — which SH185 calls the
 * behaviour the protocol wants, degrading to a plain iframe rather than failing. Telling the
 * second curator their site is broken would be a worse defect than the one being fixed.
 */
const BRIDGE_LABEL: Record<BridgeState, string> = {
  waiting: "universalEditor.connecting",
  ready: "universalEditor.connected",
  absent: "universalEditor.notConnected",
}

export default function VisualEditorPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { siteId } = useParams()
  const [searchParams] = useSearchParams()
  const iframeRef = useRef<HTMLIFrameElement>(null)

  const [src, setSrc] = useState<string>("")
  // SH333 — the three states this used to collapse into two. The frame had no `onLoad` and no
  // `onError`, and the SH185 handshake had no deadline, so "still loading" and "this will never
  // load" were the same word: a curator opening the editor on a site with no page at `/` read
  // `Conectando…` forever, over a canvas showing the renderer's 404 JSON.
  //
  // One state rather than a `ready` boolean beside it: two fields that must agree about the same
  // handshake are two fields that can disagree about it.
  const [bridge, setBridge] = useState<BridgeState>("waiting")
  const [frameLoaded, setFrameLoaded] = useState(false)
  const ready = bridge === "ready"
  const [editMode, setEditMode] = useState(true)
  const [mobile, setMobile] = useState(false)
  const [saving, setSaving] = useState(false)
  const [selected, setSelected] = useState<SelectedElement | null>(null)
  // Why the site failed to resolve, if it did (SH181): a page that spins forever instead of
  // saying so is what let `GET /api/v2/site/{furl}` 404 twice and look like slow loading.
  const [siteError, setSiteError] = useState<string | null>(null)
  // Pending edits keyed by `${postId}:${prop}` so re-edits of the same field collapse.
  const [changes, setChanges] = useState<Map<string, ContentChange>>(new Map())
  // SH303 — field name -> declared label, from the page's post-type. A field's label belongs to
  // the model: the console shows it, `shio pull` round-trips it, and the page carrying a copy
  // would be a second place for it to be wrong. Until SH303 the bridge read a `data-shio-label`
  // nothing emitted, so every field in this panel was labelled `TEXT` or `HTML`.
  const [fieldLabels, setFieldLabels] = useState<Record<string, string>>({})

  const token = useMemo(() => localStorage.getItem(UE_TOKEN_KEY) ?? "", [])
  const postTypeService = useMemo(() => new ShPostTypeService(), [])
  // Which label source is loaded (SH305 - a post id or a type name, so a row and the page
  // cannot collide), so selecting a second field of the same post re-fetches nothing.
  const loadedLabelsRef = useRef<string | null>(null)

  // Resolve the iframe URL: an explicit ?url= (external mode), the site's own front end,
  // or — since SH184 — Shio's own renderer.
  //
  // A wrong key is no longer a 404 (SH181: the endpoint answers an id, a furl or a name),
  // and a site with no front-end url is no longer a dead end either. An empty `url` is the
  // *normal* state of a site an agent just built, so refusing to open the editor at that
  // moment made it unavailable for exactly the sites that most needed looking at. SH183
  // renders the page here, and the fallback mirrors ShSiteFrontEnd on the server so the two
  // cannot come to disagree about where a site is visible.
  useEffect(() => {
    const external = searchParams.get("url")
    if (siteId === "external" && external) {
      setSrc(external)
      setSiteError(null)
      return
    }
    if (!siteId) return
    siteService
      .get(siteId)
      .then((site) => {
        // `?editor=true` asks Shio's renderer for the SH185 bridge. It is a query param
        // rather than something the parent turns on afterwards, so an ordinary preview never
        // ships editor code — and it is harmless on an external front end that ignores it.
        const destination = siteDestination(site)
        setSrc(withEditorFlag(destination.url))
        setSiteError(null)
      })
      .catch(() => setSiteError(t("universalEditor.siteNotFound", { key: siteId })))
  }, [siteId, searchParams, t])

  /**
   * The origin the framed page lives at (SH185).
   *
   * A relative `src` — which is what Shio's own renderer gives us — is same-origin, so it is
   * this window's. An absolute one carries its own. Deriving it here means both directions of
   * the bridge can be pinned to a single known origin instead of `"*"`.
   */
  const frameOrigin = useMemo(() => {
    if (!src) return ""
    try {
      return new URL(src, window.location.origin).origin
    } catch {
      return ""
    }
  }, [src])

  const postToIframe = useCallback((type: string, data: Record<string, unknown>) => {
    // SH185 — never `"*"`. Posting into an arbitrary iframe origin hands the message to
    // whatever happens to be in the frame, which is a data-leak shape, and the site's own
    // origin was knowable the whole time.
    if (!frameOrigin) return
    iframeRef.current?.contentWindow?.postMessage({ type, data }, frameOrigin)
  }, [frameOrigin])

  // Bridge messages from the rendered page (shio-editor.js — SH185).
  useEffect(() => {
    function onMessage(event: MessageEvent) {
      // The other half of the same rule: a message is only trusted from the origin we
      // framed. Without this, any page in any tab can post `sh:content-changed` and put a
      // value into the pending-save set.
      if (!frameOrigin || event.origin !== frameOrigin) return
      const msg = event.data
      if (!msg || typeof msg.type !== "string" || !msg.type.startsWith("sh:")) return
      switch (msg.type) {
        case "sh:ready":
          setBridge("ready")
          break
        case "sh:element-selected":
          setSelected(msg.data as SelectedElement)
          break
        case "sh:content-changed": {
          const c = msg.data as ContentChange
          if (!c.postId || !c.prop) break
          setChanges((prev) => {
            const next = new Map(prev)
            next.set(`${c.postId}:${c.prop}`, c)
            return next
          })
          break
        }
      }
    }
    window.addEventListener("message", onMessage)
    return () => window.removeEventListener("message", onMessage)
  }, [frameOrigin])

  /**
   * SH333 — the handshake gets a deadline.
   *
   * Deliberately a timeout rather than reading the framed document, for the same reason SH185
   * pins the origin instead of posting to `"*"`: the editor has to work against an external
   * front end whose DOM this window cannot touch, so anything learned by inspection would work
   * for Shio's own renderer and silently not for the case the protocol exists to serve.
   *
   * The deadline starts when the frame reports it loaded, not when `src` is set — the two differ
   * by however long the page takes, and starting early is how a slow site gets told it is broken.
   */
  useEffect(() => {
    if (bridge !== "waiting" || !frameLoaded) return
    const timer = window.setTimeout(() => setBridge("absent"), HANDSHAKE_TIMEOUT_MS)
    return () => window.clearTimeout(timer)
  }, [bridge, frameLoaded])

  // Push the current mode to the iframe whenever it changes (once ready).
  useEffect(() => {
    if (ready) postToIframe("sh:mode-change", { mode: editMode ? "edit" : "preview" })
  }, [ready, editMode, postToIframe])

  // SH303 — resolve the declared field labels rather than reading them off the page: one place
  // declares a label, and it is the post-type. SH305 — and it is the *selected post's* type, not
  // the page's, or every row of a listing is labelled from the wrong schema (silently, since a
  // name the schema lacks falls back to the field name). `labelSource` picks: the post when a
  // WRITE token can read it — `GET /api/v2/cda/post/{id}` answers with that post's own declared
  // fields — and the page's type otherwise, which is SH303's behaviour and the honest fallback for
  // an editor that cannot save yet. A failure is silent on purpose: the panel falls back to the
  // field name, so a labelling nicety can never break the editor.
  useEffect(() => {
    const source = labelSource(selected, !!token)
    const key = labelSourceKey(source)
    if (source.kind === "none" || loadedLabelsRef.current === key) return
    loadedLabelsRef.current = key

    const fields = source.kind === "post"
      ? editorService.getEditable(source.postId, token).then((editable) => editable.fields)
      : postTypeService.get(source.postType).then((def) => def.fields)

    fields
      .then((declared) => setFieldLabels(labelsFrom(declared)))
      .catch(() => setFieldLabels({}))
  }, [selected, token, postTypeService])

  // The declared label for a field name, or "" when the schema has not arrived (or has no
  // label for it) — the caller falls back to the name the bridge sent.
  const labelFor = (prop?: string | null) => (prop ? fieldLabels[prop] ?? "" : "")

  const persist = async (publish: boolean) => {
    if (!token) {
      toast.error(t("universalEditor.noWriteToken"))
      return
    }
    if (changes.size === 0 && !publish) return

    // Group pending changes into one attr patch per post.
    const byPost = new Map<string, Record<string, unknown>>()
    for (const change of changes.values()) {
      const attrs = byPost.get(change.postId) ?? {}
      attrs[change.prop] = change.value
      byPost.set(change.postId, attrs)
    }

    setSaving(true)
    try {
      for (const [postId, attrs] of byPost) {
        await editorService.patch(postId, attrs, token, publish)
      }
      // Publish requested but nothing was edited in this session: publish the selected post.
      if (publish && byPost.size === 0 && selected?.postId) {
        await editorService.publish(selected.postId, token)
      }
      setChanges(new Map())
      toast.success(publish ? t("universalEditor.published") : t("universalEditor.draftSaved"))
    } catch {
      toast.error(t("universalEditor.saveError"))
    } finally {
      setSaving(false)
    }
  }

  const refresh = () => {
    setBridge("waiting")
    setFrameLoaded(false)
    if (iframeRef.current) iframeRef.current.src = src
  }

  // A new destination is a new handshake: without this, navigating from a page that answered to
  // one that does not would keep reporting `Connected` against a frame that never spoke.
  useEffect(() => {
    setBridge("waiting")
    setFrameLoaded(false)
  }, [src])

  return (
    <div className="flex flex-col h-screen">
      {/* Sticky save bar */}
      <div className="sticky top-0 z-10 flex items-center justify-between bg-orange-50 dark:bg-orange-950/30 px-4 py-3 border-b">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.UNIVERSAL_EDITOR)}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <span className="font-semibold">{t("universalEditor.title")}</span>
          <div className="flex items-center gap-2">
            <Switch id="edit-mode" checked={editMode} onCheckedChange={setEditMode} />
            <Label htmlFor="edit-mode" className="text-sm">
              {editMode ? t("universalEditor.editMode") : t("universalEditor.previewMode")}
            </Label>
          </div>
          {changes.size > 0 && (
            <Badge variant="secondary">{changes.size} {t("universalEditor.changes")}</Badge>
          )}
          <Badge variant="outline" className={BRIDGE_BADGE[bridge]}>
            {t(BRIDGE_LABEL[bridge])}
          </Badge>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="ghost" size="icon" className="h-8 w-8" title={t("universalEditor.desktop")}
            onClick={() => setMobile(false)}>
            <Monitor className={`h-4 w-4 ${!mobile ? "text-orange-600" : ""}`} />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" title={t("universalEditor.mobile")}
            onClick={() => setMobile(true)}>
            <Smartphone className={`h-4 w-4 ${mobile ? "text-orange-600" : ""}`} />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" title={t("universalEditor.refresh")} onClick={refresh}>
            <RefreshCw className="h-4 w-4" />
          </Button>
          <Button variant="outline" size="sm" onClick={() => persist(true)} disabled={saving}>
            <Upload className="h-4 w-4 mr-1" />
            {t("universalEditor.publish")}
          </Button>
          <Button size="sm" onClick={() => persist(false)} disabled={saving || changes.size === 0}>
            {saving ? <Loader2 className="h-4 w-4 mr-1 animate-spin" /> : <Save className="h-4 w-4 mr-1" />}
            {saving ? t("universalEditor.saving") : t("universalEditor.save")}
          </Button>
        </div>
      </div>

      {/* Editor body: iframe + properties panel */}
      <div className="flex flex-1 min-h-0">
        <div className="relative flex-1 flex items-stretch justify-center bg-muted/30 overflow-auto p-4">
          {siteError && (
            <div className="flex flex-col items-center justify-center gap-3 text-center">
              <p className="max-w-md text-sm text-muted-foreground">{siteError}</p>
              <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.UNIVERSAL_EDITOR)}>
                {t("universalEditor.selectSite")}
              </Button>
            </div>
          )}
          {!siteError && src && (
            <iframe
              ref={iframeRef}
              src={src}
              title={t("universalEditor.title")}
              onLoad={() => setFrameLoaded(true)}
              // A cross-origin error page still fires `load`, so this is the narrow case (the
              // frame could not be navigated at all) rather than the common one. The timeout
              // above is what catches a page that loaded and handed back no bridge.
              onError={() => { setFrameLoaded(true); setBridge("absent") }}
              className={`bg-white shadow-sm border ${mobile ? "w-[390px]" : "w-full"} h-full`}
            />
          )}
          {!siteError && !src && (
            <div className="flex items-center justify-center text-muted-foreground">
              <Loader2 className="h-5 w-5 animate-spin mr-2" /> {t("universalEditor.connecting")}
            </div>
          )}
          {/*
            SH333 — said in the curator's terms, over the page rather than instead of it. The frame
            keeps whatever it is showing: if that is the renderer's teaching 404, it is at least no
            longer the only thing on screen, and if it is a working page served by a front end with
            no bridge, nothing has been hidden from somebody who came here to look at it.
          */}
          {!siteError && bridge === "absent" && (
            <div className="absolute bottom-6 left-1/2 -translate-x-1/2 max-w-xl rounded-md border border-amber-300 bg-amber-50 dark:bg-amber-950/40 px-4 py-3 shadow-sm">
              <p className="text-sm font-medium">{t("universalEditor.bridgeAbsent")}</p>
              <p className="text-sm text-muted-foreground mt-1">
                {t("universalEditor.bridgeAbsentHint")}
              </p>
              <div className="flex gap-2 mt-2">
                <Button variant="outline" size="sm" onClick={refresh}>
                  <RefreshCw className="h-4 w-4 mr-1" /> {t("universalEditor.refresh")}
                </Button>
                <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.UNIVERSAL_EDITOR)}>
                  {t("universalEditor.selectSite")}
                </Button>
              </div>
            </div>
          )}
        </div>

        <aside className="w-80 border-l bg-background overflow-auto p-4">
          {selected ? (
            <div className="space-y-3">
              <div>
                <div className="font-semibold">
                  {labelFor(selected.prop) || selected.label || selected.model}
                </div>
                {selected.model && <div className="text-xs text-muted-foreground">{selected.model}</div>}
              </div>
              <div className="space-y-2">
                {(selected.fields ?? []).map((f) => (
                  <div key={f.prop} className="rounded-md border p-2">
                    <div className="text-xs font-medium text-muted-foreground">
                      {labelFor(f.prop) || f.label || f.prop}
                    </div>
                    <div className="text-sm break-words">{f.value || <span className="italic text-muted-foreground">—</span>}</div>
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <h3 className="font-semibold mb-1">{t("universalEditor.noSelection")}</h3>
              <p className="text-sm text-muted-foreground">{t("universalEditor.noSelectionDescription")}</p>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
