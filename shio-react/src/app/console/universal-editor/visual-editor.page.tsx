import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Separator } from "@/components/ui/separator"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import {
  ArrowLeft,
  Eye,
  Layers,
  Monitor,
  MousePointerClick,
  PanelRightClose,
  PanelRightOpen,
  Pencil,
  RefreshCw,
  Save,
  Smartphone,
  X,
} from "lucide-react"
import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams, useSearchParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShUniversalEditorService } from "@/services/universal-editor/universal-editor.service"
import type { ShEditorPostData } from "@/services/universal-editor/universal-editor.service"

interface ContentChange {
  postId: string
  prop: string
  type: string
  value: string
  label: string
}

interface DomField {
  prop: string
  type: string
  label: string
  value: string
  selector: string
}

interface SelectedElement {
  resource: string
  postId: string
  type: string
  prop: string
  label: string
  model: string
  selector: string
  fields?: DomField[]
}

const editorService = new ShUniversalEditorService()

export default function VisualEditorPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { objectId } = useParams() as { objectId: string }
  const [searchParams] = useSearchParams()

  const iframeRef = useRef<HTMLIFrameElement>(null)
  const [mode, setMode] = useState<"edit" | "preview">("preview")
  const [isMobile, setIsMobile] = useState(false)
  const [iframeKey, setIframeKey] = useState(0)
  const [iframeReady, setIframeReady] = useState(false)
  const [showPanel, setShowPanel] = useState(true)
  const [selectedElement, setSelectedElement] = useState<SelectedElement | null>(null)
  const [postData, setPostData] = useState<ShEditorPostData | null>(null)
  const [domFields, setDomFields] = useState<DomField[]>([])
  const [pageMeta, setPageMeta] = useState<{ siteName?: string; folderPath?: string }>({})
  const postMetaRef = useRef<Map<string, { model: string; fields: DomField[] }>>(new Map())
  const [pendingChanges, setPendingChanges] = useState<Map<string, ContentChange>>(new Map())
  const [saving, setSaving] = useState(false)
  const [pageTitle, setPageTitle] = useState("")

  // Determine the URL to load in the iframe
  const externalUrl = searchParams.get("url")
  const previewUrl = externalUrl || `/api/v2/object/${objectId}/preview`

  // Send message to iframe
  const sendToIframe = useCallback((type: string, data: unknown) => {
    if (iframeRef.current?.contentWindow) {
      iframeRef.current.contentWindow.postMessage({ type, data }, "*")
    }
  }, [])

  // Handle messages from iframe
  useEffect(() => {
    function onMessage(event: MessageEvent) {
      if (!event.data?.type) return

      const { type, data } = event.data

      switch (type) {
        case "sh:ready":
          setIframeReady(true)
          setPageTitle(data?.title || "")
          setPageMeta({ siteName: data?.siteName, folderPath: data?.folderPath })
          if (mode === "edit") {
            sendToIframe("sh:mode-change", { mode: "edit" })
          }
          break

        case "sh:mode-changed":
          break

        case "sh:element-selected":
          setSelectedElement(data)
          setDomFields(data?.fields || [])
          // Store model + fields per postId for later use in save
          if (data?.postId && data?.model && data?.fields?.length > 0) {
            postMetaRef.current.set(data.postId, { model: data.model, fields: data.fields })
          }
          if (data?.postId) {
            editorService.getPostData(data.postId)
              .then(setPostData)
              .catch(() => {
                // Post doesn't exist yet — auto-create from DOM data
                if (data.model && data.fields?.length > 0) {
                  editorService.ensurePost(data.postId, data.model, data.fields, data.siteName, data.folderPath)
                    .then(setPostData)
                    .catch(() => setPostData(null))
                } else {
                  setPostData(null)
                }
              })
          }
          break

        case "sh:content-changed":
          if (data) {
            setPendingChanges(prev => {
              const next = new Map(prev)
              next.set(`${data.postId}:${data.prop}`, data)
              return next
            })
          }
          break

        case "sh:media-edit-request":
          // Could open a media picker dialog
          break

        case "sh:content-map":
          break
      }
    }

    window.addEventListener("message", onMessage)
    return () => window.removeEventListener("message", onMessage)
  }, [mode, sendToIframe])

  // Toggle edit/preview mode
  const toggleMode = useCallback((newMode: "edit" | "preview") => {
    setMode(newMode)
    sendToIframe("sh:mode-change", { mode: newMode })
    if (newMode === "preview") {
      setSelectedElement(null)
      setPostData(null)
    }
  }, [sendToIframe])

  // Save all pending changes
  const handleSave = useCallback(async () => {
    if (pendingChanges.size === 0) return

    setSaving(true)
    try {
      // Group changes by postId
      const byPost = new Map<string, ContentChange[]>()
      for (const change of pendingChanges.values()) {
        const existing = byPost.get(change.postId) || []
        existing.push(change)
        byPost.set(change.postId, existing)
      }

      for (const [postId, changes] of byPost) {
        // Ensure post exists before updating (auto-creates site/folder/post type/post if needed)
        const meta = postMetaRef.current.get(postId)
        if (meta) {
          await editorService.ensurePost(
            postId,
            meta.model,
            meta.fields.map(f => ({ prop: f.prop, type: f.type, value: f.value, label: f.label })),
            pageMeta.siteName,
            pageMeta.folderPath,
          ).catch(() => { /* post may already exist */ })
        }

        const updates = changes.map(c => ({
          attrName: c.prop,
          value: c.value,
          type: c.type,
        }))
        await editorService.batchUpdate(postId, updates)
      }

      setPendingChanges(new Map())
      toast.success(t("common.saved"))
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }, [pendingChanges, t])

  // Refresh iframe
  const handleRefresh = () => {
    setIframeReady(false)
    setIframeKey(k => k + 1)
  }

  // Navigate back
  const handleBack = () => {
    if (objectId) {
      navigate(`${ROUTES.CONTENT_ROOT}/object/${objectId}`)
    } else {
      navigate(ROUTES.CONTENT_ROOT)
    }
  }

  // Update a field from the properties panel
  const handlePanelFieldChange = (attrName: string, value: string, fieldType: string, fieldSelector?: string) => {
    if (!selectedElement?.postId) return

    const change: ContentChange = {
      postId: selectedElement.postId,
      prop: attrName,
      type: fieldType,
      value,
      label: attrName,
    }

    setPendingChanges(prev => {
      const next = new Map(prev)
      next.set(`${change.postId}:${change.prop}`, change)
      return next
    })

    // Also update the DOM in the iframe using the field-specific selector
    sendToIframe("sh:content-patch", {
      selector: fieldSelector || selectedElement.selector,
      value,
      type: fieldType,
    })
  }

  return (
    <div className="flex flex-col h-screen bg-background">
      {/* Top toolbar */}
      <nav className="flex items-center justify-between px-4 py-2 border-b bg-background shrink-0 gap-4">
        {/* Left: back + title */}
        <div className="flex items-center gap-3 min-w-0">
          <Button variant="ghost" size="sm" className="gap-1.5 shrink-0" onClick={handleBack}>
            <ArrowLeft className="h-4 w-4" />
            <span className="hidden sm:inline">{t("common.back")}</span>
          </Button>
          <Separator orientation="vertical" className="h-5" />
          <div className="flex items-center gap-2 min-w-0">
            <Layers className="h-4 w-4 text-orange-500 shrink-0" />
            <span className="text-sm font-medium truncate">
              {t("universalEditor.title")}
            </span>
            {pageTitle && (
              <span className="text-sm text-muted-foreground truncate hidden md:inline">
                — {pageTitle}
              </span>
            )}
          </div>
        </div>

        {/* Center: mode toggle + device */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1 bg-muted rounded-lg p-0.5">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant={mode === "edit" ? "default" : "ghost"}
                  size="sm"
                  className="h-7 gap-1.5 px-2.5"
                  onClick={() => toggleMode("edit")}
                >
                  <Pencil className="h-3.5 w-3.5" />
                  <span className="hidden sm:inline text-xs">{t("universalEditor.edit")}</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("universalEditor.editMode")}</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant={mode === "preview" ? "default" : "ghost"}
                  size="sm"
                  className="h-7 gap-1.5 px-2.5"
                  onClick={() => toggleMode("preview")}
                >
                  <Eye className="h-3.5 w-3.5" />
                  <span className="hidden sm:inline text-xs">{t("universalEditor.preview")}</span>
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("universalEditor.previewMode")}</TooltipContent>
            </Tooltip>
          </div>

          <Separator orientation="vertical" className="h-5" />

          <div className="flex items-center gap-1 bg-muted rounded-lg p-0.5">
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant={!isMobile ? "default" : "ghost"}
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => setIsMobile(false)}
                >
                  <Monitor className="h-3.5 w-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("universalEditor.desktop")}</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant={isMobile ? "default" : "ghost"}
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => setIsMobile(true)}
                >
                  <Smartphone className="h-3.5 w-3.5" />
                </Button>
              </TooltipTrigger>
              <TooltipContent>{t("universalEditor.mobile")}</TooltipContent>
            </Tooltip>
          </div>
        </div>

        {/* Right: actions */}
        <div className="flex items-center gap-2 shrink-0">
          {pendingChanges.size > 0 && (
            <Badge variant="secondary" className="gap-1">
              {pendingChanges.size} {t("universalEditor.changes")}
            </Badge>
          )}

          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleRefresh}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t("universalEditor.refresh")}</TooltipContent>
          </Tooltip>

          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => setShowPanel(!showPanel)}>
                {showPanel ? <PanelRightClose className="h-4 w-4" /> : <PanelRightOpen className="h-4 w-4" />}
              </Button>
            </TooltipTrigger>
            <TooltipContent>
              {showPanel ? t("universalEditor.hidePanel") : t("universalEditor.showPanel")}
            </TooltipContent>
          </Tooltip>

          <Button
            size="sm"
            className="gap-1.5"
            disabled={pendingChanges.size === 0 || saving}
            onClick={handleSave}
          >
            <Save className="h-3.5 w-3.5" />
            {saving ? t("common.loading") : t("common.save")}
          </Button>
        </div>
      </nav>

      {/* Main content: iframe + properties panel */}
      <div className="flex-1 flex overflow-hidden">
        {/* Iframe area */}
        <div className="flex-1 bg-muted/30 flex items-start justify-center overflow-auto p-0 relative">
          {!iframeReady && (
            <div className="absolute inset-0 flex items-center justify-center bg-background/80 z-10">
              <div className="flex flex-col items-center gap-3">
                <div className="h-8 w-8 animate-spin rounded-full border-4 border-orange-500 border-t-transparent" />
                <span className="text-sm text-muted-foreground">{t("common.loading")}</span>
              </div>
            </div>
          )}
          <iframe
            ref={iframeRef}
            key={iframeKey}
            title={t("universalEditor.title")}
            src={previewUrl}
            className={`bg-white border-0 transition-all duration-300 h-full ${
              isMobile
                ? "w-[375px] rounded-lg shadow-2xl my-4 border"
                : "w-full"
            }`}
          />
        </div>

        {/* Properties panel */}
        {showPanel && mode === "edit" && (
          <div className="w-80 border-l bg-background overflow-y-auto shrink-0">
            <PropertiesPanel
              selectedElement={selectedElement}
              postData={postData}
              domFields={domFields}
              pendingChanges={pendingChanges}
              onFieldChange={handlePanelFieldChange}
              onDeselect={() => {
                setSelectedElement(null)
                setPostData(null)
                setDomFields([])
              }}
            />
          </div>
        )}
      </div>

      {/* Status bar */}
      <div className="flex items-center justify-between px-4 py-1 border-t bg-muted/30 text-xs text-muted-foreground shrink-0">
        <div className="flex items-center gap-2">
          <div className={`h-2 w-2 rounded-full ${iframeReady ? "bg-green-500" : "bg-yellow-500"}`} />
          <span>{iframeReady ? t("universalEditor.connected") : t("universalEditor.connecting")}</span>
        </div>
        <div className="flex items-center gap-4">
          <span className="flex items-center gap-1">
            <MousePointerClick className="h-3 w-3" />
            {mode === "edit" ? t("universalEditor.editMode") : t("universalEditor.previewMode")}
          </span>
          {isMobile && <span>{t("universalEditor.mobile")} (375px)</span>}
        </div>
      </div>
    </div>
  )
}

// ── Properties Panel ──────────────────────────────────────────────────────

interface PropertiesPanelProps {
  selectedElement: SelectedElement | null
  postData: ShEditorPostData | null
  domFields: DomField[]
  pendingChanges: Map<string, ContentChange>
  onFieldChange: (attrName: string, value: string, fieldType: string, fieldSelector?: string) => void
  onDeselect: () => void
}

function PropertiesPanel({ selectedElement, postData, domFields, pendingChanges, onFieldChange, onDeselect }: PropertiesPanelProps) {
  const { t } = useTranslation()

  if (!selectedElement) {
    return (
      <div className="flex flex-col items-center justify-center h-full p-6 text-center">
        <div className="rounded-full bg-muted p-4 mb-4">
          <MousePointerClick className="h-8 w-8 text-muted-foreground" />
        </div>
        <h3 className="font-semibold text-sm mb-1">{t("universalEditor.noSelection")}</h3>
        <p className="text-xs text-muted-foreground max-w-[200px]">
          {t("universalEditor.noSelectionDescription")}
        </p>
      </div>
    )
  }

  return (
    <div className="flex flex-col">
      {/* Panel header */}
      <div className="flex items-center justify-between p-3 border-b">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold truncate">{selectedElement.label || selectedElement.prop}</h3>
          <div className="flex items-center gap-1.5 mt-0.5">
            {postData ? (
              <Badge variant="outline" className="text-[10px] h-4 px-1">
                {postData.postType.name}
              </Badge>
            ) : selectedElement.model && (
              <Badge variant="outline" className="text-[10px] h-4 px-1">
                {selectedElement.model}
              </Badge>
            )}
            {domFields.length > 0 && (
              <Badge variant="secondary" className="text-[10px] h-4 px-1">
                {domFields.length} {t("universalEditor.fields", { defaultValue: "fields" })}
              </Badge>
            )}
          </div>
        </div>
        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={onDeselect}>
          <X className="h-3 w-3" />
        </Button>
      </div>

      {/* Fields */}
      <div className="p-3 space-y-4">
        {/* Backend data available: show from postData */}
        {postData ? (
          <>
            {selectedElement.prop && postData.attrs[selectedElement.prop] && (
              <FieldEditor
                attrName={selectedElement.prop}
                attrData={postData.attrs[selectedElement.prop]}
                fieldType={selectedElement.type}
                pendingValue={pendingChanges.get(`${selectedElement.postId}:${selectedElement.prop}`)?.value}
                onChange={(value) => onFieldChange(selectedElement.prop, value, selectedElement.type)}
              />
            )}
            <Separator />
            <div className="space-y-1">
              <h4 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                {t("universalEditor.allFields")}
              </h4>
            </div>
            {Object.entries(postData.attrs).map(([name, data]) => (
              <FieldEditor
                key={name}
                attrName={name}
                attrData={data}
                fieldType={mapWidgetToType(data.widget)}
                pendingValue={pendingChanges.get(`${selectedElement.postId}:${name}`)?.value}
                onChange={(value) => onFieldChange(name, value, mapWidgetToType(data.widget))}
              />
            ))}
          </>
        ) : domFields.length > 0 ? (
          /* Fallback: render from DOM fields */
          domFields.map((field) => (
            <FieldEditor
              key={field.prop}
              attrName={field.prop}
              attrData={{
                strValue: field.value,
                widget: mapTypeToWidget(field.type),
                label: field.label,
              }}
              fieldType={field.type}
              pendingValue={pendingChanges.get(`${selectedElement.postId}:${field.prop}`)?.value}
              onChange={(value) => onFieldChange(field.prop, value, field.type, field.selector)}
            />
          ))
        ) : (
          <div className="text-xs text-muted-foreground text-center py-4">
            {t("common.loading")}
          </div>
        )}
      </div>
    </div>
  )
}

function mapTypeToWidget(type: string): string {
  switch (type) {
    case "text": return "Text"
    case "richtext": return "HTML Editor"
    case "media": return "File"
    case "textarea": return "Text Area"
    default: return "Text"
  }
}

function mapWidgetToType(widget: string): string {
  switch (widget) {
    case "Text": return "text"
    case "Text Area": return "text"
    case "HTML Editor": return "richtext"
    case "Date": return "date"
    default: return "text"
  }
}

// ── Field Editor ──────────────────────────────────────────────────────────

interface FieldEditorProps {
  attrName: string
  attrData: { strValue: string; widget: string; label: string } | undefined
  fieldType: string
  pendingValue: string | undefined
  onChange: (value: string) => void
}

function FieldEditor({ attrName, attrData, fieldType, pendingValue, onChange }: FieldEditorProps) {
  if (!attrData) return null

  const currentValue = pendingValue ?? attrData.strValue ?? ""
  const isModified = pendingValue !== undefined

  return (
    <Card className={`p-3 space-y-2 ${isModified ? "border-orange-300 dark:border-orange-700" : ""}`}>
      <div className="flex items-center justify-between">
        <Label className="text-xs font-medium">{attrData.label || attrName}</Label>
        <div className="flex items-center gap-1">
          {isModified && (
            <Badge variant="outline" className="text-[10px] h-4 px-1 border-orange-300 text-orange-600">
              {/* modified indicator */}
              *
            </Badge>
          )}
          <Badge variant="secondary" className="text-[10px] h-4 px-1">
            {attrData.widget}
          </Badge>
        </div>
      </div>

      {fieldType === "richtext" || attrData.widget === "Text Area" || attrData.widget === "HTML Editor" ? (
        <Textarea
          value={currentValue}
          onChange={(e) => onChange(e.target.value)}
          rows={4}
          className="text-xs"
        />
      ) : fieldType === "date" ? (
        <Input
          type="date"
          value={currentValue}
          onChange={(e) => onChange(e.target.value)}
          className="text-xs"
        />
      ) : (
        <Input
          value={currentValue}
          onChange={(e) => onChange(e.target.value)}
          className="text-xs"
        />
      )}
    </Card>
  )
}
