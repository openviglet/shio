import { Button } from "@/components/ui/button"
import { ChevronRight, FileText, Folder, Search, X } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import axios from "axios"
import type { WidgetProps } from "./index"
import { ContentBrowserDialog, type ContentBrowserSelection } from "./content-browser-dialog"

interface PathSegment {
  id: string
  name: string
}

interface ResolvedContent {
  name: string
  path: PathSegment[]
  parentFolderId?: string
  type: "POST" | "FOLDER"
}

function parseSettings(raw?: string): { folderId?: string } {
  if (!raw) return {}
  try {
    const p = JSON.parse(raw)
    return p?.contentSelect ?? {}
  } catch {
    return {}
  }
}

async function resolveFullPath(contentId: string): Promise<ResolvedContent | null> {
  if (!contentId) return null

  // Try as post
  const postRes = await axios.get(`/v2/post/xp/${contentId}`).catch(() => null)
  if (postRes?.data?.shPost) {
    const post = postRes.data.shPost
    const folderId = post.shFolder?.id
    const pathSegments: PathSegment[] = []

    if (folderId) {
      const pathRes = await axios.get(`/v2/object/${folderId}/path`).catch(() => null)
      if (pathRes?.data) {
        const data = pathRes.data
        if (data.site) {
          pathSegments.push({ id: data.site.id, name: data.site.name })
        }
        if (Array.isArray(data.breadcrumb)) {
          for (const c of data.breadcrumb) {
            pathSegments.push({ id: c.id, name: c.name })
          }
        }
      } else {
        // Fallback: use what we have from the post
        if (post.shFolder?.shSite?.name) pathSegments.push({ id: post.shFolder.shSite.id ?? "", name: post.shFolder.shSite.name })
        if (post.shFolder?.name) pathSegments.push({ id: post.shFolder.id, name: post.shFolder.name })
      }
    }

    return { name: post.title, path: pathSegments, parentFolderId: folderId, type: "POST" }
  }

  // Try as folder
  const folderRes = await axios.get(`/v2/folder/${contentId}`).catch(() => null)
  if (folderRes?.data) {
    const folder = folderRes.data
    const pathSegments: PathSegment[] = []
    const parentId = folder.parentFolder?.id ?? folder.shSite?.id

    if (parentId) {
      const pathRes = await axios.get(`/v2/object/${parentId}/path`).catch(() => null)
      if (pathRes?.data) {
        const data = pathRes.data
        if (data.site) {
          pathSegments.push({ id: data.site.id, name: data.site.name })
        }
        if (Array.isArray(data.breadcrumb)) {
          for (const c of data.breadcrumb) {
            pathSegments.push({ id: c.id, name: c.name })
          }
        }
      }
    }

    return { name: folder.name, path: pathSegments, parentFolderId: parentId, type: "FOLDER" }
  }

  return null
}

export function ContentSelectWidget({ attr, onChange }: WidgetProps) {
  const { t } = useTranslation()
  const [browserOpen, setBrowserOpen] = useState(false)
  const [browserStartId, setBrowserStartId] = useState<string | undefined>(undefined)
  const [resolved, setResolved] = useState<ResolvedContent | null>(null)
  const [resolving, setResolving] = useState(false)

  const settings = useMemo(() => parseSettings(attr.shPostTypeAttr?.widgetSettings), [attr.shPostTypeAttr?.widgetSettings])

  const openBrowserAt = (folderId?: string) => {
    setBrowserStartId(folderId ?? resolved?.parentFolderId ?? settings.folderId)
    setBrowserOpen(true)
  }

  const doResolve = useCallback(async (contentId: string) => {
    if (!contentId) {
      setResolved(null)
      return
    }
    setResolving(true)
    try {
      const result = await resolveFullPath(contentId)
      setResolved(result)
    } catch {
      setResolved(null)
    } finally {
      setResolving(false)
    }
  }, [])

  useEffect(() => {
    doResolve(attr.strValue ?? "")
  }, [attr.strValue, doResolve])

  const handleBrowserSelect = (selection: ContentBrowserSelection) => {
    onChange({ ...attr, strValue: selection.id })
    // The path from the browser is already complete
    setResolved({
      name: selection.name,
      path: selection.pathSegments ?? [],
      parentFolderId: selection.parentFolderId,
      type: selection.type,
    })
  }

  const handleClear = () => {
    onChange({ ...attr, strValue: "" })
    setResolved(null)
  }

  return (
    <div className="space-y-2">
      {attr.strValue ? (
        <div className="rounded-lg border bg-muted/30 overflow-hidden">
          {/* Clickable breadcrumb path — each segment opens browser at that folder */}
          {resolved && resolved.path.length > 0 && (
            <div className="flex items-center gap-1 px-3 py-1.5 text-xs bg-muted/50 border-b flex-wrap">
              {resolved.path.map((seg, i) => (
                <span key={seg.id} className="flex items-center gap-1">
                  {i > 0 && <ChevronRight className="h-3 w-3 text-muted-foreground/50 shrink-0" />}
                  <button
                    type="button"
                    className="text-primary hover:underline hover:text-primary/80 transition-colors"
                    onClick={() => openBrowserAt(seg.id)}
                  >
                    {seg.name}
                  </button>
                </span>
              ))}
            </div>
          )}

          {/* Selected item */}
          <div className="flex items-center gap-3 px-3 py-2.5">
            {resolved?.type === "FOLDER"
              ? <Folder className="h-4.5 w-4.5 text-orange-500 shrink-0" />
              : <FileText className="h-4.5 w-4.5 text-blue-500 shrink-0" />
            }
            <div className="flex-1 min-w-0">
              {resolving ? (
                <p className="text-sm text-muted-foreground">{t("common.loading")}</p>
              ) : resolved ? (
                <p className="text-sm font-medium truncate">{resolved.name}</p>
              ) : (
                <p className="text-sm font-mono text-muted-foreground truncate">{attr.strValue}</p>
              )}
            </div>
            <div className="flex items-center gap-1 shrink-0">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={() => openBrowserAt()}
              >
                <Search className="h-3.5 w-3.5" />
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="h-7 w-7 text-muted-foreground hover:text-destructive"
                onClick={handleClear}
              >
                <X className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <Button
          type="button"
          variant="outline"
          className="w-full justify-start gap-2 text-muted-foreground"
          onClick={() => openBrowserAt()}
        >
          <Search className="h-4 w-4" />
          {t("content.browseContent")}
        </Button>
      )}

      <ContentBrowserDialog
        open={browserOpen}
        onOpenChange={setBrowserOpen}
        onSelect={handleBrowserSelect}
        startFolderId={browserStartId}
        selectMode="content"
      />
    </div>
  )
}
