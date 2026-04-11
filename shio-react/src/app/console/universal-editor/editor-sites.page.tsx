import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { PageHeader } from "@/components/page-header"
import { ROUTES } from "@/app/routes.const"
import {
  ExternalLink,
  Globe,
  Link2,
  Pencil,
  Search,
} from "lucide-react"
import { IconLayersIntersect } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { useShioService } from "@/contexts/ShioServiceContext"
import type { ShSite } from "@/models/site/site"

export default function EditorSitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { siteService } = useShioService()
  const [sites, setSites] = useState<ShSite[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState("")
  const [customUrl, setCustomUrl] = useState("")

  useEffect(() => {
    siteService.query().then(data => {
      setSites(data)
      setLoading(false)
    }).catch(() => setLoading(false))
  }, [siteService])

  const filteredSites = sites.filter(s =>
    s.name.toLowerCase().includes(search.toLowerCase()) ||
    (s.description || "").toLowerCase().includes(search.toLowerCase())
  )

  const handleOpenEditor = (siteId: string) => {
    navigate(`${ROUTES.CONSOLE}/universal-editor/${siteId}`)
  }

  const handleOpenCustomUrl = () => {
    if (!customUrl.trim()) return
    navigate(`${ROUTES.CONSOLE}/universal-editor/external?url=${encodeURIComponent(customUrl)}`)
  }

  return (
    <div className="flex flex-col gap-6 p-6">
      <PageHeader
        turIcon={IconLayersIntersect}
        title={t("universalEditor.title")}
      />

      {/* Custom URL */}
      <Card className="p-4">
        <div className="flex items-center gap-2 mb-3">
          <Link2 className="h-4 w-4 text-muted-foreground" />
          <h3 className="text-sm font-semibold">{t("universalEditor.openUrl")}</h3>
        </div>
        <div className="flex gap-2">
          <Input
            placeholder={t("universalEditor.urlPlaceholder")}
            value={customUrl}
            onChange={(e) => setCustomUrl(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleOpenCustomUrl()}
            className="flex-1"
          />
          <Button onClick={handleOpenCustomUrl} disabled={!customUrl.trim()} className="gap-1.5">
            <ExternalLink className="h-4 w-4" />
            {t("universalEditor.open")}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground mt-2">
          {t("universalEditor.urlHint")}
        </p>
      </Card>

      {/* Sites list */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h3 className="text-sm font-semibold">{t("universalEditor.selectSite")}</h3>
          <div className="relative w-64">
            <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder={t("content.searchPlaceholder")}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="pl-9"
            />
          </div>
        </div>

        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-orange-500 border-t-transparent" />
          </div>
        ) : filteredSites.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <Globe className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("universalEditor.noSites")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm mb-6">
              {t("universalEditor.noSitesDescription")}
            </p>
          </div>
        ) : (
          <div className="grid gap-3">
            {filteredSites.map(site => (
              <Card
                key={site.id}
                className="flex items-center justify-between p-4 hover:shadow-sm transition-shadow cursor-pointer group"
                onClick={() => handleOpenEditor(site.id)}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="h-10 w-10 rounded-lg bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center shrink-0">
                    <Globe className="h-5 w-5 text-white" />
                  </div>
                  <div className="min-w-0">
                    <h4 className="text-sm font-semibold truncate">{site.name}</h4>
                    {site.description && (
                      <p className="text-xs text-muted-foreground truncate">{site.description}</p>
                    )}
                  </div>
                </div>
                <Button
                  variant="ghost"
                  size="sm"
                  className="gap-1.5 opacity-0 group-hover:opacity-100 transition-opacity"
                >
                  <Pencil className="h-3.5 w-3.5" />
                  {t("universalEditor.editSite")}
                </Button>
              </Card>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

