import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconLayersIntersect } from "@tabler/icons-react"
import { ExternalLink, KeyRound, Pencil } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import { ShApiTokenService } from "@/services/token/api-token.service"
import type { ShSite } from "@/models/site/site"
import type { ShApiToken } from "@/models/token/api-token.model"

const siteService = new ShSiteService()
const tokenService = new ShApiTokenService()

/** localStorage key for the WRITE token the editor uses to save back (SH10). */
export const UE_TOKEN_KEY = "sh-ue-token"

export default function EditorSitesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [sites, setSites] = useState<ShSite[]>([])
  const [writeTokens, setWriteTokens] = useState<ShApiToken[]>([])
  const [token, setToken] = useState<string>(() => localStorage.getItem(UE_TOKEN_KEY) ?? "")
  const [externalUrl, setExternalUrl] = useState("")

  useEffect(() => {
    resetBreadcrumb([{ label: t("universalEditor.title") }])
    siteService.query().then(setSites)
    tokenService.list().then((all) => setWriteTokens(all.filter((tok) => tok.scope === "WRITE")))
  }, [resetBreadcrumb, t])

  const selectToken = (value: string) => {
    setToken(value)
    localStorage.setItem(UE_TOKEN_KEY, value)
  }

  const openExternal = () => {
    if (!externalUrl.trim()) return
    navigate(`${ROUTES.UNIVERSAL_EDITOR}/external?url=${encodeURIComponent(externalUrl.trim())}`)
  }

  return (
    <>
      <PageHeader title={t("universalEditor.title")} turIcon={IconLayersIntersect} urlBase={ROUTES.UNIVERSAL_EDITOR} />
      <div className="p-6 space-y-6">
        <div>
          <h2 className="text-lg font-semibold">{t("universalEditor.title")}</h2>
          <p className="text-sm text-muted-foreground">{t("universalEditor.description")}</p>
        </div>

        {/* WRITE token selector — the editor saves back through the CDA write scope */}
        <Card>
          <CardContent className="p-4 space-y-2">
            <Label className="flex items-center gap-2">
              <KeyRound className="h-4 w-4" /> {t("universalEditor.selectToken")}
            </Label>
            {writeTokens.length === 0 ? (
              <p className="text-sm text-muted-foreground">{t("universalEditor.noWriteToken")}</p>
            ) : (
              <Select value={token} onValueChange={selectToken}>
                <SelectTrigger className="max-w-md">
                  <SelectValue placeholder={t("universalEditor.selectToken")} />
                </SelectTrigger>
                <SelectContent>
                  {writeTokens.map((tok) => (
                    <SelectItem key={tok.id} value={tok.token}>
                      {tok.title}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            )}
          </CardContent>
        </Card>

        {/* Sites */}
        <div>
          <h3 className="text-sm font-semibold mb-2">{t("universalEditor.selectSite")}</h3>
          {sites.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 text-center">
              <div className="rounded-full bg-muted p-4 mb-4">
                <IconLayersIntersect className="h-8 w-8 text-muted-foreground" />
              </div>
              <h3 className="font-semibold text-lg mb-1">{t("universalEditor.noSites")}</h3>
              <p className="text-sm text-muted-foreground max-w-sm mb-6">{t("universalEditor.noSitesDescription")}</p>
            </div>
          ) : (
            <Card>
              <CardContent className="p-0 divide-y">
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
              </CardContent>
            </Card>
          )}
        </div>

        {/* External URL */}
        <Card>
          <CardContent className="p-4 space-y-2">
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
          </CardContent>
        </Card>
      </div>
    </>
  )
}
