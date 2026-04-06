import { Button } from "@/components/ui/button"
import { Separator } from "@/components/ui/separator"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ROUTES } from "@/app/routes.const"
import {
  ArrowLeft,
  ChevronDown,
  ExternalLink,
  Monitor,
  Pencil,
  RefreshCw,
  Smartphone,
  Trash2,
} from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import axios from "axios"
import { toast } from "sonner"

interface ObjectInfo {
  shSite?: { id: string; name: string }
  shPost?: { id: string; title: string; shPostType: { id: string }; shFolder: { id: string } }
  currentFolder?: { id: string; name: string; parentFolder?: { id: string } }
  breadcrumb?: { id: string; name: string }[]
}

export default function PreviewPage() {
  const { objectId } = useParams() as { objectId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [isMobile, setIsMobile] = useState(false)
  const [objectInfo, setObjectInfo] = useState<ObjectInfo | null>(null)
  const [previewUrl, setPreviewUrl] = useState("")
  const [iframeKey, setIframeKey] = useState(0)

  useEffect(() => {
    setPreviewUrl(`/api/v2/object/${objectId}/preview`)

    axios.get(`/v2/object/${objectId}/path`).then((res) => {
      setObjectInfo(res.data)
    }).catch(() => {
      setObjectInfo(null)
    })
  }, [objectId])

  const objectType = objectInfo?.shPost
    ? "POST"
    : objectInfo?.currentFolder
      ? "FOLDER"
      : objectInfo?.shSite
        ? "SITE"
        : null

  const objectTitle = objectInfo?.shPost?.title
    ?? objectInfo?.currentFolder?.name
    ?? objectInfo?.shSite?.name
    ?? ""

  const handleEdit = () => {
    if (objectType === "POST" && objectInfo?.shPost) {
      navigate(`${ROUTES.CONTENT_ROOT}/post/${objectId}`)
    } else if (objectType === "FOLDER") {
      navigate(`${ROUTES.CONTENT_ROOT}/folder/${objectId}/edit`)
    } else if (objectType === "SITE" && objectInfo?.shSite) {
      navigate(`${ROUTES.CONTENT_ROOT}/site/${objectInfo.shSite.id}`)
    }
  }

  const handleReturn = () => {
    if (objectType === "POST" && objectInfo?.shPost?.shFolder) {
      navigate(`${ROUTES.CONTENT_ROOT}/object/${objectInfo.shPost.shFolder.id}`)
    } else if (objectType === "FOLDER" && objectInfo?.currentFolder) {
      const parentId = objectInfo.currentFolder.parentFolder?.id ?? objectInfo.shSite?.id
      if (parentId) navigate(`${ROUTES.CONTENT_ROOT}/object/${parentId}`)
      else navigate(ROUTES.CONTENT_ROOT)
    } else if (objectType === "SITE") {
      navigate(ROUTES.CONTENT_ROOT)
    } else {
      navigate(ROUTES.CONTENT_ROOT)
    }
  }

  const handleClearCache = async () => {
    try {
      await axios.get(`/v2/object/${objectId}/clear-cache`)
      toast.success(t("preview.cacheCleared"))
      setIframeKey((k) => k + 1)
    } catch {
      toast.error(t("common.error"))
    }
  }

  const handleOpenNewWindow = () => {
    window.open(previewUrl, "_blank")
  }

  const handleRefresh = () => {
    setIframeKey((k) => k + 1)
  }

  const editLabel = objectType === "POST"
    ? t("preview.editPost")
    : objectType === "FOLDER"
      ? t("preview.editFolder")
      : t("preview.editSite")

  return (
    <div className="flex flex-col h-screen bg-background">
      {/* Top navbar */}
      <nav className="flex items-center justify-between px-4 py-2 border-b bg-background shrink-0 gap-4">
        {/* Left: back + title */}
        <div className="flex items-center gap-3 min-w-0">
          <Button variant="ghost" size="sm" className="gap-1.5 shrink-0" onClick={handleReturn}>
            <ArrowLeft className="h-4 w-4" />
            <span className="hidden sm:inline">{t("preview.backToConsole")}</span>
          </Button>
          <Separator orientation="vertical" className="h-5" />
          <span className="text-sm font-medium truncate text-muted-foreground">
            {objectTitle}
          </span>
        </div>

        {/* Center: device toggle */}
        <div className="flex items-center gap-1 bg-muted rounded-lg p-0.5">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant={!isMobile ? "default" : "ghost"}
                size="icon"
                className="h-7 w-7"
                onClick={() => setIsMobile(false)}
              >
                <Monitor className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t("preview.desktop")}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button
                variant={isMobile ? "default" : "ghost"}
                size="icon"
                className="h-7 w-7"
                onClick={() => setIsMobile(true)}
              >
                <Smartphone className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t("preview.mobile")}</TooltipContent>
          </Tooltip>
        </div>

        {/* Right: actions */}
        <div className="flex items-center gap-2 shrink-0">
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-8 w-8" onClick={handleRefresh}>
                <RefreshCw className="h-4 w-4" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t("preview.refresh")}</TooltipContent>
          </Tooltip>

          <div className="flex items-center">
            <Button size="sm" className="rounded-r-none gap-1.5" onClick={handleEdit}>
              <Pencil className="h-3.5 w-3.5" />
              {editLabel}
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button size="sm" className="rounded-l-none border-l border-primary-foreground/20 px-2">
                  <ChevronDown className="h-3.5 w-3.5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={handleClearCache} className="gap-2">
                  <Trash2 className="h-4 w-4" />
                  {t("preview.clearCache")}
                </DropdownMenuItem>
                <DropdownMenuItem onClick={handleOpenNewWindow} className="gap-2">
                  <ExternalLink className="h-4 w-4" />
                  {t("preview.openNewWindow")}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      </nav>

      {/* Preview iframe */}
      <div className="flex-1 bg-muted/30 flex items-start justify-center overflow-auto p-0">
        <iframe
          key={iframeKey}
          title={t("preview.title")}
          src={previewUrl}
          className={`bg-white border-0 transition-all duration-300 h-full ${
            isMobile
              ? "w-[375px] rounded-lg shadow-2xl my-4 border"
              : "w-full"
          }`}
        />
      </div>
    </div>
  )
}
