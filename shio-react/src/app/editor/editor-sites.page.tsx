import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ROUTES } from "@/app/routes.const"
import { IconLayersIntersect } from "@tabler/icons-react"
import { ExternalLink, Pencil } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { useSites } from "@/hooks/use-catalogue"


export default function EditorSitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  // SH1046 — one cached read of the site list, shared with every other picker.
  const sites = useSites().data ?? []
  const [externalUrl, setExternalUrl] = useState("")

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
      <PageHero
        title={t("universalEditor.title")}
        eyebrow={t("sidebar.content")}
        subtitle={t("universalEditor.description")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      <div className="space-y-6">
        {/* Sites */}
        <div>
          <h3 className="text-sm font-semibold mb-2">{t("universalEditor.selectSite")}</h3>
          {sites.length === 0 ? (
            <EmptyState
              icon={IconLayersIntersect}
              tone="blue"
              title={t("universalEditor.noSites")}
              description={t("universalEditor.noSitesDescription")}
            />
          ) : (
            <Panel contentClassName="p-0 divide-y">
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
            </Panel>
          )}
        </div>

        {/* External URL */}
        <Panel contentClassName="p-4 space-y-2">
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
        </Panel>
      </div>
    </>
  )
}
