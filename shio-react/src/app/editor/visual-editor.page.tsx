import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { ROUTES } from "@/app/routes.const"
import {
  ArrowDown,
  ArrowLeft,
  ArrowUp,
  Copy,
  Loader2,
  Monitor,
  Plus,
  RefreshCw,
  Save,
  Smartphone,
  Trash2,
  Upload,
} from "lucide-react"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams, useSearchParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import { ShSiteService } from "@/services/site/site.service"
import { ShUniversalEditorService } from "@/services/universal-editor/universal-editor.service"
import {
  attrFromDeclared, labelSource, labelSourceKey, labelsFrom, type DeclaredWidgetField,
} from "./field-labels"
import { getWidget } from "@/components/widgets"
import type {
  ContentChange, FrameRect, InsertableContainer, InsertableType, SectionOp, SelectedElement,
  SelectedSection,
} from "@/models/universal-editor/universal-editor.model"
import { siteDestination } from "@/lib/site-front-end"

const siteService = new ShSiteService()
const editorService = new ShUniversalEditorService()

/**
 * The Page field that holds an ordered list of sections (SH1038).
 *
 * One name, and it is the one the generated layout renders — `{{#getRelation sections}}`, written by
 * the CLI's conversion and by every blueprint that composes a page. The helper takes the field's
 * *value* and not its name, so the name is gone by the time the markup exists and nothing in the
 * rendered DOM could carry it.
 *
 * Which is why the write is **checked rather than assumed**: the console reads the Page and offers
 * the controls only when this field holds an array as long as the list on screen. A page whose
 * sections live somewhere else gets the header and the position with no buttons — the same rule the
 * first half of this task followed, that a button which does nothing is worse than none.
 */
const SECTIONS_FIELD = "sections"

/** The array the Page holds today, or why this page's sections cannot be reordered. */
type SectionArray =
  | { state: "loading" }
  | { state: "ready"; entries: unknown[] }
  | { state: "unavailable" }

/**
 * The selected section's outline and its toolbar, drawn HERE and not by the page (SH1038).
 *
 * The page's own affordances are an injected stylesheet, which is right for a field: an outline and
 * a caret are things CSS can express. A section needs a **toolbar**, and a stylesheet cannot draw
 * one — nor could the page own the buttons if it did, because the writes are the console's and the
 * page has no session. So the page sends a box and this draws over it.
 *
 * `pointer-events-none` on the outline is what keeps the frame clickable through it: a transparent
 * div over the page would otherwise eat the next click, which is the click that selects the next
 * section. Only the toolbar takes the pointer back.
 */
/**
 * The catalogue, as something to place (SH1039).
 *
 * The rail is the whole of the answer's point: the section types used to reach the console as
 * post-type names in a picker — a list of names — and what a curator needs is a thing they can put
 * on the page. So each entry is the type's own title and description, and pressing it writes.
 *
 * `above` / `below` rather than a drag caret: the caret is what a drag needs, and the capability it
 * expresses is *where*, which two buttons say exactly. With nothing selected there is no `where`
 * to be relative to, and the section goes last — which on the empty page this exists for is also
 * first.
 */
function SectionInserter({ container, relative, busy, t, onInsert }: {
  container: InsertableContainer
  /** Whether a section is selected, and the placement is relative to it. */
  relative: boolean
  busy: boolean
  t: (key: string, options?: Record<string, unknown>) => string
  onInsert: (type: InsertableType, after: boolean) => void
}) {
  if (!container.accepts.length) {
    return (
      <p className="text-sm text-muted-foreground">
        {/*
          P6 inside an ordinary panel: an empty catalogue is the shape of both "nothing fits here"
          and "nobody said", and the server distinguishes them. `fix` is present only for the
          second, and it is the line that would give this page a catalogue.
        */}
        {container.fix ?? t("universalEditor.nothingFits")}
      </p>
    )
  }
  return (
    <div className="space-y-2">
      <div className="text-xs font-medium text-muted-foreground">
        {t("universalEditor.addSection")}
      </div>
      {container.accepts.map((type) => (
        <div key={type.type} className="rounded-md border p-2">
          <div className="text-sm font-medium">{type.title || type.type}</div>
          {type.description && (
            <p className="text-xs text-muted-foreground mt-0.5">{type.description}</p>
          )}
          {type.provisioned ? (
            <div className="flex gap-1 mt-2">
              {relative && (
                <Button variant="outline" size="sm" className="h-7 text-xs" disabled={busy}
                  onClick={() => onInsert(type, false)}>
                  <Plus className="h-3 w-3 mr-1" />{t("universalEditor.insertAbove")}
                </Button>
              )}
              <Button variant="outline" size="sm" className="h-7 text-xs" disabled={busy}
                onClick={() => onInsert(type, true)}>
                <Plus className="h-3 w-3 mr-1" />
                {relative ? t("universalEditor.insertBelow") : t("universalEditor.insertAtEnd")}
              </Button>
            </div>
          ) : (
            /*
              The template names it and this instance has not got it. Shown rather than hidden, for
              the reason the server reports it rather than dropping it: the page declares the type,
              so a shorter list would be a quieter lie than an honest gap.
            */
            <p className="text-xs text-amber-600 dark:text-amber-500 mt-1">
              {t("universalEditor.typeNotProvisioned", { type: type.type })}
            </p>
          )}
        </div>
      ))}
    </div>
  )
}

function SectionOverlay({ rect, label, position, index, of, writable, t, onOp }: {
  rect: FrameRect
  label: string
  position: string
  index: number
  of: number
  writable: boolean
  t: (key: string) => string
  onOp: (op: SectionOp, to?: number) => void
}) {
  // Above the section, or inside its top edge when it starts at the top of the viewport — a bar
  // drawn at a negative offset from the first section is a bar nobody can press.
  const barAbove = rect.top >= 30
  return (
    <>
      <div
        // The accent as the token and not as a hue (SH890): a re-key reaches
        // `--vg-accent-from` and reaches nothing spelled as a utility class.
        className="absolute pointer-events-none border-2 border-[var(--vg-accent-from)] rounded-sm"
        style={{ top: rect.top, left: rect.left, width: rect.width, height: rect.height }}
        data-testid="section-outline"
      />
      <div
        className="absolute z-10 flex items-center gap-1 rounded bg-gray-900 text-gray-50 px-1.5 py-1 text-xs shadow"
        style={{ top: barAbove ? rect.top - 30 : rect.top + 4, left: rect.left }}
      >
        <span className="px-1 font-medium">{label}</span>
        <span className="px-1 text-gray-400">{position}</span>
        <button type="button" className="rounded px-1.5 py-0.5 hover:bg-gray-700 disabled:opacity-40"
          title={t("universalEditor.moveUp")} disabled={!writable || index === 0}
          onClick={() => onOp("move", index - 1)}>
          <ArrowUp className="h-3.5 w-3.5" />
        </button>
        <button type="button" className="rounded px-1.5 py-0.5 hover:bg-gray-700 disabled:opacity-40"
          title={t("universalEditor.moveDown")} disabled={!writable || index >= of - 1}
          onClick={() => onOp("move", index + 1)}>
          <ArrowDown className="h-3.5 w-3.5" />
        </button>
        {/*
          The title says what it does rather than what it is called: the array holds references, so
          a duplicated entry places the SAME section twice and editing either heading edits both.
          A curator who finds that out by editing one of them has been surprised (P7).
        */}
        <button type="button" className="rounded px-1.5 py-0.5 hover:bg-gray-700 disabled:opacity-40"
          title={t("universalEditor.duplicateHint")} disabled={!writable}
          onClick={() => onOp("duplicate")}>
          <Copy className="h-3.5 w-3.5" />
        </button>
        <button type="button" className="rounded px-1.5 py-0.5 hover:bg-red-700 disabled:opacity-40"
          title={t("universalEditor.removeSection")} disabled={!writable}
          onClick={() => onOp("remove")}>
          <Trash2 className="h-3.5 w-3.5" />
        </button>
      </div>
    </>
  )
}

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

  /**
   * SH703 — the destination the site lookup produced. The external one is not state at all: it is
   * `?url=`, read straight from the query string, so the effect below no longer has a branch that
   * writes what it could have computed.
   */
  const [fetchedSrc, setFetchedSrc] = useState<string>("")
  // SH333 — the three states this used to collapse into two. The frame had no `onLoad` and no
  // `onError`, and the SH185 handshake had no deadline, so "still loading" and "this will never
  // load" were the same word: a curator opening the editor on a site with no page at `/` read
  // `Conectando…` forever, over a canvas showing the renderer's 404 JSON.
  //
  // One state rather than a `ready` boolean beside it: two fields that must agree about the same
  // handshake are two fields that can disagree about it.
  /**
   * SH703 — the handshake, tagged with the destination it belongs to.
   *
   * <p>`bridge` and `frameLoaded` were reset by an effect on `src`, which is the shape this task is
   * about: a value changing above them drove two flags maintained by hand, and the reset landed one
   * render after the paint that still said `Connected`. Tagging them means a handshake for another
   * destination simply is not this destination's — it reads as `waiting` with no frame loaded,
   * without anybody clearing it afterwards, and the stale `Connected` window closes entirely.</p>
   */
  const [handshake, setHandshake] = useState<{
    src: string; bridge: BridgeState; frameLoaded: boolean
    /**
     * SH1037 — what the bridge on the other side said it is and what it can do.
     *
     * `capabilities: null` is the answer from a v1 bridge and from `@viglet/shio-editor-cors`, and
     * it means **assume everything**: two producers implement this protocol and the published one
     * implements less, so a console that treated silence as "supports nothing" would take away
     * editing that works today. An explicit list is the only thing that narrows what is offered.
     */
    protocol: number; capabilities: string[] | null
    /**
     * SH1039 — the page the bridge says it is live on, as a post id.
     *
     * Kept because the catalogue is asked of the *page* and not of a selection: the case insertion
     * exists for is a page that composes no sections yet, where there is nothing to click and
     * therefore nothing that would otherwise name the page.
     */
    pagePostId: string | null
  }>(
    () => ({
      src: "", bridge: "waiting", frameLoaded: false, protocol: 1, capabilities: null,
      pagePostId: null,
    }),
  )
  const [editMode, setEditMode] = useState(true)
  const [mobile, setMobile] = useState(false)
  const [saving, setSaving] = useState(false)
  const [selected, setSelected] = useState<SelectedElement | null>(null)
  /**
   * SH1038 — the section selected, when it is a section rather than one of its fields.
   *
   * Its own state and not a variant of `selected`: the two carry different shapes and drive
   * different controls, and the union would have every read branching on a discriminator before it
   * could use either. They are kept mutually exclusive where they are set.
   */
  const [selectedSection, setSelectedSection] = useState<SelectedSection | null>(null)
  /**
   * SH1038 — where the selected section is in the frame, kept current by `sh:section-geometry`.
   *
   * Beside `selectedSection` and not inside it, because the two change at different rates: a
   * selection happens when a curator clicks and a box moves on every scroll of the framed page, and
   * folding the second into the first would re-run every effect keyed on the selection once a frame.
   */
  const [sectionRect, setSectionRect] = useState<FrameRect | null>(null)
  /**
   * SH1038 — the Page's own ordered array, which is what every one of the three verbs replaces.
   *
   * Read rather than derived: the page on screen says how many sections there are and in what
   * order, and it does not say what the *entries* are — they are references, which may be ids or
   * friendly URLs, and a reorder has to write back the same tokens it read. `unavailable` is a real
   * answer, not an error: a page whose sections live in a field other than {@link SECTIONS_FIELD}
   * is one this console cannot write, and saying so beats offering a button that would.
   */
  const [loadedSections, setLoadedSections] = useState<{
    /** Which selection this answer is about — `<page>|<length>`, empty before the first read. */
    key: string
    /** The array as read, or `null` for "this page does not carry one this panel could write". */
    entries: unknown[] | null
  }>({ key: "", entries: null })
  const [reordering, setReordering] = useState(false)
  /**
   * SH1039 — what may be added to this page, and where, as the server answers it.
   *
   * Keyed by the page the bridge reported rather than by the selection, because the case insertion
   * exists for is a page that composes no sections: there is nothing to click, so a catalogue that
   * arrived with a selection would never arrive at all on the emptiest page.
   */
  const [catalogue, setCatalogue] = useState<{ key: string; containers: InsertableContainer[] }>(
    { key: "", containers: [] },
  )
  const [inserting, setInserting] = useState(false)
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
  /**
   * SH1037 — the declared field per name, so the panel can draw the widget the model names.
   *
   * Beside `fieldLabels` rather than replacing it: a label is resolved for every field the panel
   * lists and is cheap to look up, while this is only read for the field being edited. Both come
   * from the same fetch, so they cannot describe different versions of the type.
   */
  const [declaredFields, setDeclaredFields] = useState<Record<string, DeclaredWidgetField>>({})

  const postTypeService = useMemo(() => new ShPostTypeService(), [])
  // Which label source is loaded (SH305 - a post id or a type name, so a row and the page
  // cannot collide), so selecting a second field of the same post re-fetches nothing.
  const loadedLabelsRef = useRef<string | null>(null)

  /**
   * SH1036 — what the bridge last said it was live on, and how many times it has said it.
   *
   * The frame can navigate on its own now (a curator clicking a link in the preview), and the
   * console cannot read the frame's URL — it is another origin. What it can see is two things: a
   * `sh:ready` per bridged load, carrying that page's url, and the iframe's own `load` event per
   * navigation of any kind.
   *
   * `reportedPage` is what re-keys the session: a different url means the selection, the pending
   * changes and the loaded labels all belong to the page that is no longer on screen. Before this
   * they survived the navigation and Save still offered to write them — onto the previous page's
   * posts, from a panel describing an element that is no longer in the document.
   *
   * `readyCount` against `readyAtLoad` is how a page with NO bridge is detected: the script sends
   * `sh:ready` at `DOMContentLoaded`, which precedes the frame's `load`, so a bridged navigation
   * has always incremented the count by the time the load fires and a bridge-less one has not.
   * Counting rather than flagging, because the two events interleave per navigation and a boolean
   * cannot say which navigation it was about.
   */
  const reportedPageRef = useRef<string>("")
  const readyCountRef = useRef(0)
  const readyAtLoadRef = useRef(0)
  const loadsRef = useRef(0)

  // Resolve the iframe URL: an explicit ?url= (external mode), the site's own front end,
  // or — since SH184 — Shio's own renderer.
  //
  // A wrong key is no longer a 404 (SH181: the endpoint answers an id, a furl or a name),
  // and a site with no front-end url is no longer a dead end either. An empty `url` is the
  // *normal* state of a site an agent just built, so refusing to open the editor at that
  // moment made it unavailable for exactly the sites that most needed looking at. SH183
  // renders the page here, and the fallback mirrors ShSiteFrontEnd on the server so the two
  // cannot come to disagree about where a site is visible.
  const externalSrc = siteId === "external" ? searchParams.get("url") : null
  const src = externalSrc ?? fetchedSrc
  const bridge: BridgeState = handshake.src === src ? handshake.bridge : "waiting"
  const frameLoaded = handshake.src === src && handshake.frameLoaded
  // Every write stamps the destination it is about, and carries the sibling flag over only when it
  // belonged to that same destination. Without the stamp a frame's `onLoad` would land under the
  // previous `src` and the derived value would never see it.
  const setBridge = useCallback((next: BridgeState) => setHandshake((prev) => ({
    ...prev, src, bridge: next, frameLoaded: prev.src === src && prev.frameLoaded,
  })), [src])
  const setFrameLoaded = useCallback((next: boolean) => setHandshake((prev) => ({
    ...prev, src, frameLoaded: next, bridge: prev.src === src ? prev.bridge : "waiting",
  })), [src])
  const ready = bridge === "ready"
  /**
   * SH1037 — whether the page claims a capability, with silence meaning yes.
   *
   * The negotiation is one-sided on purpose: the console asks nothing and the page volunteers. A
   * handshake with a round trip would have to survive the page navigating under it (SH1036), and
   * there is nothing the console could usefully refuse — an affordance it hides is the only action
   * available to it either way.
   */
  const claims = useCallback((capability: string) => {
    const declared = handshake.src === src ? handshake.capabilities : null
    return declared === null || declared.includes(capability)
  }, [handshake, src])

  useEffect(() => {
    if (externalSrc) return
    if (!siteId) return
    siteService
      .get(siteId)
      .then((site) => {
        // `?editor=true` asks Shio's renderer for the SH185 bridge. It is a query param
        // rather than something the parent turns on afterwards, so an ordinary preview never
        // ships editor code — and it is harmless on an external front end that ignores it.
        const destination = siteDestination(site)
        setFetchedSrc(withEditorFlag(destination.url))
        setSiteError(null)
      })
      .catch(() => setSiteError(t("universalEditor.siteNotFound", { key: siteId })))
  }, [siteId, externalSrc, t])

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
        case "sh:ready": {
          const announced = msg.data as
            { protocol?: number; capabilities?: string[]; postType?: string; postId?: string }
            | undefined
          setHandshake((prev) => ({
            ...prev,
            src,
            bridge: "ready",
            frameLoaded: prev.src === src && prev.frameLoaded,
            protocol: announced?.protocol ?? 1,
            // SH1039 — which page, so the catalogue can be asked without waiting for a click.
            pagePostId: announced?.postId ?? null,
            // Only an ARRAY narrows anything. A v1 bridge sends no `capabilities` key at all, and
            // `null` here is what makes `claims` answer yes to everything.
            capabilities: Array.isArray(announced?.capabilities) ? announced.capabilities : null,
          }))
          readyCountRef.current += 1
          // SH1036 — the bridge names the page it is live on, and on an in-frame navigation that is
          // a different page than the one this panel is describing. Re-key rather than reload: the
          // frame is already showing the new page, and the console's job is to stop talking about
          // the old one.
          const reported = (msg.data as { url?: string } | undefined)?.url
          if (reported && reported !== reportedPageRef.current) {
            reportedPageRef.current = reported
            setSelected(null)
            setSelectedSection(null)
            setSectionRect(null)
            setChanges(new Map())
            // The labels belong to the previous page's post-type. Cleared rather than re-fetched
            // here: the next selection is what says which type is wanted, and fetching now would
            // be a request for a page nobody has clicked in yet.
            loadedLabelsRef.current = null
            setFieldLabels({})
          }
          break
        }
        // SH1036 — the page is leaving. Stop describing it now rather than when the next one
        // announces itself: a panel showing a selection, and a Save offering to write pending
        // changes, both belong to a document that is unloading. `sh:ready` re-keys on arrival; this
        // closes the window in between, which on a slow page is where a curator would have pressed
        // Save and written the old page's fields.
        case "sh:navigating":
          setBridge("waiting")
          setSelected(null)
          setSelectedSection(null)
          setSectionRect(null)
          setChanges(new Map())
          break
        case "sh:element-selected":
          setSelected(msg.data as SelectedElement)
          // SH1038 — one selection at a time. A field and the section holding it are two different
          // objects, and a panel showing both would leave the curator guessing which one Save and
          // the move controls act on.
          setSelectedSection(null)
          setSectionRect(null)
          break
        case "sh:component-selected": {
          const section = msg.data as SelectedSection
          setSelectedSection(section)
          setSectionRect(section.rect ?? null)
          setSelected(null)
          break
        }
        // SH1038 — the box moved (a scroll, a resize, or the page re-laid out after an op), or it
        // is gone. `rect: null` is a removal: the overlay comes down rather than staying over
        // whatever moved into that space.
        case "sh:section-geometry": {
          const moved = (msg.data as { rect?: FrameRect | null } | undefined)?.rect ?? null
          setSectionRect(moved)
          if (!moved) setSelectedSection(null)
          break
        }
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
  }, [frameOrigin, setBridge])

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
  }, [bridge, frameLoaded, setBridge])

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
    const source = labelSource(selected)
    const key = labelSourceKey(source)
    if (source.kind === "none" || loadedLabelsRef.current === key) return
    loadedLabelsRef.current = key

    // SH335 — both branches end at the post-type API, because that is where a label is declared.
    // What differs is only *which* type: the selected post's (SH305) or the page's (SH303).
    const typeName = source.kind === "post"
      ? editorService.postTypeOf(source.postId)
      : Promise.resolve(source.postType)

    typeName
      .then((name) => (name ? postTypeService.get(name).then((def) => def.fields) : null))
      .then((declared) => {
        setFieldLabels(labelsFrom(declared))
        // SH1037 — the declared field itself, not only its label. The panel needs the widget and
        // its settings to draw the field the model describes; the fetch that resolves labels
        // already answers with both, and a second read for the same document would be a second
        // chance for the two to disagree.
        const byName: Record<string, DeclaredWidgetField> = {}
        for (const field of declared ?? []) {
          if (field?.name) byName[field.name] = field as DeclaredWidgetField
        }
        setDeclaredFields(byName)
      })
      .catch(() => {
        setFieldLabels({})
        setDeclaredFields({})
      })
  }, [selected, postTypeService])

  /**
   * SH1038 — the Page's ordered array, read when a section is selected, checked before it is used.
   *
   * The check is the whole mechanism and it is one comparison: the array has to be as long as the
   * list the page says it drew. `{{#getRelation sections}}` renders the references in the order the
   * field names them, so if the lengths agree then index *i* on screen is entry *i* here — and if
   * they do not, this is not the field that page is drawing and no write from this panel could be
   * right. Keyed on the length as well as on the page, so a reorder an agent made behind this
   * screen is re-read the next time a curator selects something.
   *
   * The read is **tagged with the selection it belongs to**, which is SH703's shape on this page
   * one more time: an answer for another section simply is not this one's, so it reads as `loading`
   * with nothing to clear afterwards. The effect that reset a flag before fetching is the version
   * this replaces — it wrote state the render could have derived, and left one paint in which a
   * previous page's array was offered as this one's.
   */
  /**
   * SH1039 — the catalogue, asked once per page the bridge reports.
   *
   * Tagged with the page it belongs to, like every other read on this screen, so a frame that
   * navigated is simply not describing this one. Fetched on the handshake rather than on a
   * selection: the page with nothing on it is the page most in need of an answer.
   */
  const pageOfFrame = handshake.src === src ? handshake.pagePostId : null
  useEffect(() => {
    if (!pageOfFrame || catalogue.key === pageOfFrame) return
    let current = true
    editorService.insertable(pageOfFrame).then((containers) => {
      if (current) setCatalogue({ key: pageOfFrame, containers })
    })
    return () => { current = false }
  }, [pageOfFrame, catalogue.key])

  /**
   * The one container this page composes, or `null` where it composes none — or more than one.
   *
   * **More than one is deliberately `null`.** `sh:component-selected` says a section's index and
   * how many its container holds; it does not say *which* container, because the handle both halves
   * can compute is document order and a selection carries only its own. So on a page with two
   * ordered lists the console cannot tell which one an insert belongs to, and a rail offered there
   * would write into whichever list it guessed. That is the rule the rest of this screen follows —
   * a control that would write the wrong thing is worse than none — and it costs nothing today: a
   * conversion and both blueprints compose exactly one.
   */
  const soleContainer: InsertableContainer | null =
    catalogue.key && catalogue.key === pageOfFrame && catalogue.containers.length === 1
      ? catalogue.containers[0]
      : null

  // SH1039 — the page the array belongs to is the selection's when there is one and the frame's
  // otherwise, because an empty page has nothing to select and is exactly where an insert is
  // wanted. `of` and the container's `holds` are the same number wherever both exist.
  const pageOfSection = selectedSection?.pagePostId ?? pageOfFrame
  const sectionCount = selectedSection?.of ?? soleContainer?.holds ?? null
  const sectionKey = pageOfSection && sectionCount !== null
    ? `${pageOfSection}|${sectionCount}` : ""
  const sectionArray: SectionArray = (() => {
    if (!sectionKey || loadedSections.key !== sectionKey) return { state: "loading" }
    return loadedSections.entries
      ? { state: "ready", entries: loadedSections.entries }
      : { state: "unavailable" }
  })()

  useEffect(() => {
    // Once per key, and never while a write of this page's array is in flight. Both matter: the
    // bridge echoes the new position *before* the patch resolves, so a read started on that echo
    // can land after the write and put the array back as it was a moment ago — which reads as "this
    // page cannot be reordered" and stays that way until the curator selects something else. What
    // makes a stale read safe to skip is that the write is conditional: `editable` stores the
    // version it saw and `patch` sends it, so a change behind this screen is refused rather than
    // overwritten (SH985).
    if (!sectionKey || !pageOfSection || reordering || loadedSections.key === sectionKey) return
    let current = true
    editorService.editable(pageOfSection)
      .then((post) => {
        if (!current) return
        const entries = (post.data ?? {})[SECTIONS_FIELD]
        setLoadedSections({
          key: sectionKey,
          entries: Array.isArray(entries) && entries.length === sectionCount ? entries : null,
        })
      })
      .catch(() => { if (current) setLoadedSections({ key: sectionKey, entries: null }) })
    return () => { current = false }
  }, [sectionKey, pageOfSection, sectionCount, reordering, loadedSections.key])

  /**
   * One of the three verbs, as one replacement of the Page's array (SH1038).
   *
   * The page is told **first** and the write follows, which is the optimistic half: the write is a
   * draft merge and the frame is showing a render of the published row, so nothing about the write
   * makes that document change — a curator would press Move and watch the page sit still. The two
   * can only disagree when the write is refused, and that case reloads the frame rather than
   * leaving a page that shows an order the content does not have.
   *
   * `to` is the destination index **after the section has been taken out**, which is the same
   * arithmetic the bridge does, so moving down by one means `index + 1` on both sides.
   */
  const runSectionOp = async (op: SectionOp, to?: number) => {
    if (!selectedSection?.pagePostId || sectionArray.state !== "ready" || reordering) return
    const index = selectedSection.index
    const next = [...sectionArray.entries]
    if (op === "remove") {
      next.splice(index, 1)
    } else if (op === "duplicate") {
      next.splice(index + 1, 0, next[index])
    } else {
      const target = Math.max(0, Math.min(to ?? index, next.length - 1))
      const [moved] = next.splice(index, 1)
      next.splice(target, 0, moved)
    }
    postToIframe("sh:section-op", { op, index, to })
    setReordering(true)
    try {
      await editorService.patch(selectedSection.pagePostId, { [SECTIONS_FIELD]: next })
      // Under the key the echoed selection will carry: a remove and a duplicate change the length,
      // and re-reading the page to learn what this call just wrote would be a round trip for an
      // answer already in hand.
      setLoadedSections({ key: `${selectedSection.pagePostId}|${next.length}`, entries: next })
      toast.success(t("universalEditor.sectionSaved"))
    } catch {
      toast.error(t("universalEditor.sectionSaveError"))
      // The page now shows an order the content does not have, and there is no way to put one
      // element back without knowing what else changed. A reload is the honest repair.
      refresh()
    } finally {
      setReordering(false)
    }
  }

  /**
   * SH1039 — place a new section of `type`, before or after the selected one.
   *
   * Two writes and not one, which is what an insert is: a section is a post, and the Page names it
   * by reference. They are ordered so a failure leaves the smaller mess — the new post exists and
   * the page does not name it, which a curator sees in the content browser, rather than the page
   * naming a post that was never created, which is a dangling reference on a live page.
   *
   * The reference is spelled the way the array already spells its entries: a friendly URL where the
   * neighbours are paths, the id where they are ids. Mixing the two resolves (SH181's order tries
   * the id first) and would leave the file projection carrying one line unlike its siblings.
   *
   * The frame reloads afterwards, and that is not laziness: a new section's markup comes from the
   * template, so there is nothing the bridge could have drawn optimistically the way it does for a
   * move. `/preview` is draft-preferred, so the reload shows what was just written.
   */
  const insertSection = async (type: InsertableType, after: boolean) => {
    const pageId = selectedSection?.pagePostId ?? pageOfFrame
    if (!pageId || sectionArray.state !== "ready" || inserting || !type.provisioned) return
    setInserting(true)
    try {
      // The type's own definition, for the field that carries a title. The catalogue does not say
      // which one it is — that flag decides nothing about placing a section and would ride on every
      // row of every catalogue (P3) — and this is one fetch on the one action that needs it.
      const declared = await postTypeService.get(type.type).then((def) => def.fields).catch(() => [])
      const titleField = declared?.find((field) => field.isTitle)?.name
      const created = await editorService.create({
        folderId: await folderForSection(pageId),
        postType: type.type,
        data: titleField
          ? { [titleField]: t("universalEditor.newSection", { type: type.title ?? type.type }) }
          : {},
      })
      const entries = [...sectionArray.entries]
      const spellsPaths = entries.some((entry) => typeof entry === "string" && entry.startsWith("/"))
      const reference = spellsPaths && created.furl ? created.furl : created.id
      const at = selectedSection ? selectedSection.index + (after ? 1 : 0) : entries.length
      entries.splice(at, 0, reference)
      await editorService.patch(pageId, { [SECTIONS_FIELD]: entries })
      toast.success(t("universalEditor.sectionInserted"))
      refresh()
    } catch {
      toast.error(t("universalEditor.sectionInsertError"))
    } finally {
      setInserting(false)
    }
  }

  /**
   * Where a new section post goes: beside the sections this page already has, else beside the page.
   *
   * A conversion writes every section into one folder and names it by address, so the neighbour is
   * the right answer whenever there is one. The fallback is the honest case of the first section on
   * a page nobody has composed yet — there is no neighbour to be beside, and the page's own folder
   * is the only place the curator is already looking.
   */
  const folderForSection = async (pageId: string): Promise<string> => {
    const neighbour = selectedSection?.postId
    const source = neighbour ?? pageId
    const post = await editorService.editable(source)
    return post.shFolder?.id ?? ""
  }

  // The declared label for a field name, or "" when the schema has not arrived (or has no
  // label for it) — the caller falls back to the name the bridge sent.
  const labelFor = (prop?: string | null) => (prop ? fieldLabels[prop] ?? "" : "")

  /**
   * The declared field for a prop, when the type has arrived and names a widget (SH1037).
   *
   * A `Text` widget is deliberately excluded: it renders as the same box the read-only line already
   * is, so drawing it through the map buys nothing and costs a component per field. The map's
   * fallback is `TextWidget`, so "no widget declared" and "declared Text" would be
   * indistinguishable here — which is exactly the case where the plain line is the honest render.
   */
  const declaredWidgetFor = (prop?: string | null): DeclaredWidgetField | null => {
    const declared = prop ? declaredFields[prop] : null
    return declared && declared.widget && declared.widget !== "Text" ? declared : null
  }

  /**
   * SH503 — an edit to a field the page draws into an attribute.
   *
   * Two things happen, and the second is the one that is easy to leave out: the change joins the
   * pending set exactly as an inline edit does — same key, same patch, same Save — and it is posted
   * back into the frame so the page shows the new destination. Without that second half a curator
   * repoints a button, sees the old link, and has no way to tell whether anything happened (P7).
   */
  const editAttrField = (
    field: { prop: string; label: string }, value: string,
  ) => {
    const postId = selected?.postId
    if (!postId) return
    const change: ContentChange = {
      postId, prop: field.prop, type: "attr", value, label: field.label,
    }
    setChanges((prev) => new Map(prev).set(`${postId}:${field.prop}`, change))
    postToIframe("sh:field-set", { postId, prop: field.prop, value })
  }

  const persist = async (publish: boolean) => {
    // SH335 — no token check. Saving is a console write on the curator's own session now; the
    // token that used to gate this authorized nothing the cookie did not already.
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
        await editorService.patch(postId, attrs, publish)
      }
      // Publish requested but nothing was edited in this session: publish the selected post.
      if (publish && byPost.size === 0 && selected?.postId) {
        await editorService.publish(selected.postId)
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

  // A new destination is a new handshake. SH703 — that is now a property of the state rather than
  // an effect that repairs it: the handshake carries the `src` it belongs to, so navigating from a
  // page that answered to one that does not reads as `waiting` on the first render instead of
  // reporting `Connected` against a frame that never spoke until the effect caught up.

  return (
    <div className="flex flex-col h-screen">
      {/* Sticky save bar */}
      <div className="sticky top-0 z-10 flex items-center justify-between bg-orange-50 dark:bg-orange-950/30 px-4 py-3 border-b">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.UNIVERSAL_EDITOR)}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <span className="font-semibold">{t("universalEditor.title")}</span>
          {/*
            SH1037 — the toggle is an affordance the PAGE has to honour: it turns into a
            `sh:mode-change`, and a bridge that does not implement inline editing answers it with
            nothing. Shown disabled rather than hidden, with the reason on hover: a control that
            vanishes reads as a console bug, and a curator looking for edit mode needs to be told
            the page is what cannot do it.
          */}
          <div className="flex items-center gap-2">
            <Switch id="edit-mode" checked={editMode && claims("edit-inline")}
              disabled={!claims("edit-inline")} onCheckedChange={setEditMode} />
            <Label htmlFor="edit-mode" className="text-sm"
              title={claims("edit-inline") ? undefined : t("universalEditor.noInlineEditing")}>
              {!claims("edit-inline")
                ? t("universalEditor.previewOnly")
                : editMode ? t("universalEditor.editMode") : t("universalEditor.previewMode")}
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
          {/*
            SH1038 — the frame gets a wrapper whose box IS the framed viewport, and the overlay is
            positioned inside it. That is the whole coordinate system: the page reports a box in its
            own viewport's coordinates, the iframe fills this wrapper exactly, so `rect.top` is a
            `top` here and nothing has to be measured on this side — which matters because the
            wrapper scrolls and the iframe is centred in mobile mode, so a measured offset would be
            two more things to keep current. The border moved off the iframe and onto the wrapper
            for the same reason: `absolute` is relative to the padding box, so a border on the
            iframe would put every overlay one pixel out.
          */}
          {!siteError && src && (
            <div className={`relative bg-white shadow-sm border h-full ${mobile ? "w-[390px]" : "w-full"}`}>
            <iframe
              ref={iframeRef}
              src={src}
              title={t("universalEditor.title")}
              onLoad={() => {
                setFrameLoaded(true)
                loadsRef.current += 1
                // SH1036 — a NAVIGATION that handed back no bridge. The script sends `sh:ready` at
                // DOMContentLoaded, before this fires, so a bridged page has already incremented the
                // count and one without a bridge has not. Only from the second load on: the first
                // handshake keeps the SH333 deadline below, which is what distinguishes "slow" from
                // "absent" on a page nobody has navigated yet. Before this the badge read
                // `Connected` over a page with no editor on it, because it keyed on an iframe `src`
                // that in-frame navigation cannot change.
                if (loadsRef.current > 1 && readyCountRef.current === readyAtLoadRef.current) {
                  setBridge("absent")
                }
                readyAtLoadRef.current = readyCountRef.current
              }}
              // A cross-origin error page still fires `load`, so this is the narrow case (the
              // frame could not be navigated at all) rather than the common one. The timeout
              // above is what catches a page that loaded and handed back no bridge.
              onError={() => { setFrameLoaded(true); setBridge("absent") }}
              className="w-full h-full"
            />
            {editMode && selectedSection && sectionRect && (
              <SectionOverlay
                rect={sectionRect}
                label={selectedSection.section || t("universalEditor.section")}
                position={t("universalEditor.sectionPosition", {
                  position: selectedSection.index + 1, of: selectedSection.of,
                })}
                index={selectedSection.index}
                of={selectedSection.of}
                writable={sectionArray.state === "ready" && !reordering}
                t={t}
                onOp={runSectionOp}
              />
            )}
            </div>
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
          {/*
            SH1038 — a section's own header. The controls are on the overlay, over the section they
            act on, which is where a curator is already looking; what stays here is the identity,
            the position, and the one thing a toolbar button cannot say — whether this page's
            sections can be written at all, and what Duplicate actually does.
          */}
          {selectedSection ? (
            <div className="space-y-3">
              <div>
                <div className="font-semibold">{selectedSection.section || t("universalEditor.section")}</div>
                <div className="text-xs text-muted-foreground">
                  {t("universalEditor.sectionPosition", {
                    position: selectedSection.index + 1, of: selectedSection.of,
                  })}
                </div>
              </div>
              <p className="text-sm text-muted-foreground">
                {t("universalEditor.sectionSelected")}
              </p>
              {sectionArray.state === "unavailable" && (
                <p className="text-sm text-amber-600 dark:text-amber-500">
                  {t("universalEditor.sectionsNotWritable")}
                </p>
              )}
              {sectionArray.state === "ready" && (
                <p className="text-xs text-muted-foreground">
                  {t("universalEditor.duplicateHint")}
                </p>
              )}
              {soleContainer && sectionArray.state === "ready" && (
                <SectionInserter container={soleContainer} relative busy={inserting}
                  t={t} onInsert={insertSection} />
              )}
            </div>
          ) : selected ? (
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
                    {f.type === "attr" && claims("field-set") ? (
                      <Input
                        // SH503 — the one field kind that has to be edited here. A text field is
                        // typed into on the page; a value drawn into an attribute has no text node
                        // to carry a caret, which is why a link's destination was readable in this
                        // panel and editable nowhere in the editor.
                        //
                        // Uncontrolled, keyed by prop: `selected` is a snapshot the bridge sent, and
                        // re-rendering the panel from it would put the curator's cursor back to the
                        // start of the box on every keystroke.
                        defaultValue={f.value}
                        className="mt-1 h-8 text-sm"
                        onChange={(event) => editAttrField(f, event.target.value)}
                      />
                    ) : declaredWidgetFor(f.prop) ? (
                      /*
                        SH1037 — the field the MODEL describes, through the console's own widget map.
                        The panel used to draw read-only text for everything that is not an `attr`,
                        so a Date was a string, a Check Box was "1" and a Content Select was a UUID
                        — and the field whose caret cannot express its shape (`affordance: "panel"`)
                        is precisely the one that ends up here. Read-only still, because writing back
                        from the panel for every kind is the remainder of this task: what changes is
                        that the curator sees a date as a date.
                      */
                      <div className="mt-1 pointer-events-none opacity-90">
                        {(() => {
                          const declared = declaredWidgetFor(f.prop)!
                          const Widget = getWidget(declared.widget ?? "Text")
                          return <Widget attr={attrFromDeclared(declared, f.value) as never}
                            onChange={() => undefined} postId={selected.postId ?? undefined} />
                        })()}
                      </div>
                    ) : (
                      <div className="text-sm break-words">{f.value || <span className="italic text-muted-foreground">—</span>}</div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="space-y-4">
              <div className="flex flex-col items-center justify-center py-10 text-center">
                <h3 className="font-semibold mb-1">{t("universalEditor.noSelection")}</h3>
                <p className="text-sm text-muted-foreground">{t("universalEditor.noSelectionDescription")}</p>
              </div>
              {/*
                SH1039 — and the rail is here too, with nothing selected. That is the case insertion
                exists for: a page composing no sections has nothing to click, so a rail that only
                appeared beside a selection would never appear on the emptiest page.
              */}
              {soleContainer && sectionArray.state === "ready" && (
                <SectionInserter container={soleContainer} relative={false} busy={inserting}
                  t={t} onInsert={insertSection} />
              )}
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
