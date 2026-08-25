import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconLayersIntersect } from "@tabler/icons-react"
import { ExternalLink, Pencil } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"

const siteService = new ShSiteService()

export default function EditorSitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [sites, setSites] = useState<ShSite[]>([])
  const [externalUrl, setExternalUrl] = useState("")

  useEffect(() => {
    resetBreadcrumb([{ label: t("universalEditor.title") }])
    siteService.query().then(setSites)
  }, [resetBreadcrumb, t])

  const openExternal = () => {
    if (!externalUrl.trim()) return
    navigate(`${ROUTES.UNIVERSAL_EDITOR}/external?url=${encodeURIComponent(externalUrl.trim())}`)
  }

  return (
    <>
      {/*
        SH907 — the site picker is an ordinary surface and takes the ordinary treatment. The
        *editor* it navigates to is the one this task is really about, and it is not here.
      */}
      <AdaptivePageHeader
        icon={IconLayersIntersect}
        title={t("universalEditor.title")}
        eyebrow={t("sidebar.content")}
        subtitle={t("universalEditor.description")}
        urlBase={ROUTES.CONTENT_ROOT}
      />
      <div className="p-6 space-y-6">
        {/* Sites */}
        <div>
          <h3 className="text-sm font-semibold mb-2">{t("universalEditor.selectSite")}</h3>
          {sites.length === 0 ? (
            <AdaptiveEmptyState
              icon={IconLayersIntersect}
              tone="blue"
              title={t("universalEditor.noSites")}
              description={t("universalEditor.noSitesDescription")}
            />
          ) : (
            <AdaptivePanel contentClassName="p-0 divide-y">
                {sites.map((site) => (
                  <button
                    type="button"
                    key={site.id}
                    onClick={() => navigate(`${ROUTES.UNIVERSAL_EDITOR}/${site.id}`)}
                    className="flex w-full items-center justify-between px-4 py-3 hover:bg-muted/50 transition-colors text-left"
                  >
                    <div className="min-w-0">
                      <div className="font-medium flex items-center gap-2">
                        {site.name}
                        {site.url && <Badge variant="secondary" className="text-xs">{site.url}</Badge>}
                      </div>
                      {site.description && (
                        <div className="text-sm text-muted-foreground truncate">{site.description}</div>
                      )}
                    </div>
                    <Pencil className="h-4 w-4 text-muted-foreground shrink-0" />
                  </button>
                ))}
            </AdaptivePanel>
          )}
        </div>

        {/* External URL */}
        <AdaptivePanel contentClassName="p-4 space-y-2">
            <Label htmlFor="external-url" className="flex items-center gap-2">
              <ExternalLink className="h-4 w-4" /> {t("universalEditor.openUrl")}
            </Label>
            <div className="flex gap-2 max-w-2xl">
              <Input
                id="external-url"
                value={externalUrl}
                placeholder={t("universalEditor.urlPlaceholder")}
                onChange={(e) => setExternalUrl(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && openExternal()}
              />
              <Button onClick={openExternal} disabled={!externalUrl.trim()}>
                {t("universalEditor.open")}
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">{t("universalEditor.urlHint")}</p>
        </AdaptivePanel>
      </div>
    </>
  )
}
