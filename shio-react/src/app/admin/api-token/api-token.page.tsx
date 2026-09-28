import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { KeyRound, Save, ArrowLeft, Copy, Check, Trash2, Info } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShApiTokenService } from "@/services/token/api-token.service"
import type { ShApiToken, ShApiTokenScope, ShApiTokenEnv } from "@/models/token/api-token.model"
import { useSites } from "@/hooks/use-catalogue"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"

const service = new ShApiTokenService()

const SCOPES: ShApiTokenScope[] = ["READ", "PREVIEW", "WRITE", "AGENT"]
const ENVS: ShApiTokenEnv[] = ["PROD", "PREVIEW"]

export default function ApiTokenPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { tokenId } = useParams() as { tokenId: string }
  const isNew = tokenId === "new"

  const [item, setItem] = useState<ShApiToken>({
    id: "", title: "", description: "", token: "", owner: "", createdDate: "", enabled: true,
    scope: "READ", siteIds: [], env: "PROD", mayPublish: false,
  })
  // SH1046 — the site list is one cached read, shared with every other screen that fills a picker
  // from it. An empty array while it loads is what this form drew before and still draws.
  const sites = useSites().data ?? []
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)

  /*
   * SH1116 — one read per token, cancelled on leaving, keyed on the id and not on the form.
   *
   * `item.title` was in the dependency list, so typing the token's title re-read the row and put
   * the saved copy back over every field being edited.
   */
  const loaded = useQuery<ShApiToken>({
    queryKey: addressKey(`token:${tokenId}`),
    queryFn: ({ signal }) => service.get(tokenId, { signal }),
    enabled: !isNew,
  })

  // Set during render, not in an effect: React's own "adjust state when the input changes" pattern.
  const [seeded, setSeeded] = useState<string | null>(null)
  const token = loaded.data
  if (token && seeded !== token.id) {
    setSeeded(token.id)
    setItem({ ...token, scope: token.scope ?? "READ", siteIds: token.siteIds ?? [], env: token.env ?? "PROD", mayPublish: token.mayPublish ?? false })
  }

  useEffect(() => {
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  const allSites = (item.siteIds?.length ?? 0) === 0
  const toggleSite = (siteId: string, checked: boolean) => {
    const current = item.siteIds ?? []
    const next = checked ? [...current, siteId] : current.filter((id) => id !== siteId)
    setItem({ ...item, siteIds: next })
  }

  const handleSave = async () => {
    if (!item.title.trim()) {
      toast.error(t("apiToken.titleRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("apiToken.created"))
        navigate(`${ROUTES.ADMIN_TOKENS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("apiToken.updated"))
      }
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirm(t("dialog.deleteMessage"))) {
      // SH1139 — a delete that navigates away is not a delete that was reported. This awaited and
      // left, so a refusal escaped as an unhandled rejection and the dock, its live caption and the
      // curator all learned nothing: the row vanished from the list either way.
      try {
        await service.delete(item.id)
        toast.success(t("common.deleted"))
        navigate(ROUTES.ADMIN_TOKENS)
      } catch (error) {
        reportProblem(error, t("common.error"))
      }
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(item.token)
      setCopied(true)
      toast.success(t("apiToken.copied"))
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t("apiToken.copyFailed"))
    }
  }

  return (
    <>
      <PageHero
        title={t("apiToken.title")}
        eyebrow={t("admin.title")}
        subtitle={t("apiToken.description")}
        urlBase={ROUTES.ADMIN_TOKENS}
        backLabel={t("apiToken.title")}
      />
      <div>
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" aria-label={t("common.back")} onClick={() => navigate(ROUTES.ADMIN_TOKENS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("apiToken.newInstance") : item.title}</span>
            <Badge variant="outline" className="text-xs">CDA</Badge>
          </div>
          <div className="flex items-center gap-2">
            {!isNew && (
              <Button variant="ghost" size="sm" className="text-destructive" onClick={handleDelete}>
                <Trash2 className="h-4 w-4 mr-1" />
                {t("common.delete")}
              </Button>
            )}
            <Button size="sm" onClick={handleSave} disabled={saving}>
              <Save className="h-4 w-4 mr-1" />
              {saving ? t("common.loading") : t("common.save")}
            </Button>
          </div>
        </div>

        {/* General section */}
        <AdaptiveSectionCard className="mb-6">
          <AdaptiveSectionCard.StaticHeader
            icon={KeyRound}
            title={t("apiToken.general")}
            description={t("apiToken.generalDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="title">{t("common.title")}</Label>
              <Input id="title" value={item.title}
                onChange={(e) => setItem({ ...item, title: e.target.value })}
                placeholder={t("apiToken.titlePlaceholder")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t("common.description")}</Label>
              <Textarea id="description" value={item.description || ""}
                onChange={(e) => setItem({ ...item, description: e.target.value })}
                placeholder={t("apiToken.descriptionPlaceholder")} rows={3} />
            </div>
            {!isNew && (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="enabled">{t("common.enabled")}</Label>
                  <Tooltip>
                    <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("apiToken.enabledHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Switch id="enabled" checked={item.enabled}
                  onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
              </div>
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Access section (SH2 — scope + per-site allow-list) */}
        <AdaptiveSectionCard className="mb-6">
          <AdaptiveSectionCard.StaticHeader
            icon={KeyRound}
            title={t("apiToken.access")}
            description={t("apiToken.accessDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-5">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="scope">{t("apiToken.scope")}</Label>
                <Tooltip>
                  <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("apiToken.scopeHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Select value={item.scope}
                onValueChange={(value) => setItem({ ...item, scope: value as ShApiTokenScope })}>
                <SelectTrigger id="scope">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCOPES.map((scope) => (
                    <SelectItem key={scope} value={scope}>
                      {t(`apiToken.scope${scope.charAt(0)}${scope.slice(1).toLowerCase()}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                {t(`apiToken.scope${item.scope.charAt(0)}${item.scope.slice(1).toLowerCase()}Desc`)}
              </p>
            </div>

            {/*
              SH107 — publishing is a capability, not a default. It gates the agent surface:
              an agent's writes land in DRAFT and a human publishes them, unless this says
              otherwise. A console user has no token and is unaffected.
            */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Label htmlFor="mayPublish">{t("apiToken.mayPublish")}</Label>
                <Tooltip>
                  <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("apiToken.mayPublishHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Switch id="mayPublish" checked={Boolean(item.mayPublish)}
                onCheckedChange={(checked) => setItem({ ...item, mayPublish: checked })} />
            </div>

            {/* Environment (SH5 — prod / preview) */}
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="env">{t("apiToken.env")}</Label>
                <Tooltip>
                  <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("apiToken.envHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Select value={item.env}
                onValueChange={(value) => setItem({ ...item, env: value as ShApiTokenEnv })}>
                <SelectTrigger id="env">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {ENVS.map((env) => (
                    <SelectItem key={env} value={env}>
                      {t(`apiToken.env${env.charAt(0)}${env.slice(1).toLowerCase()}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                {t(`apiToken.env${item.env.charAt(0)}${item.env.slice(1).toLowerCase()}Desc`)}
              </p>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="allSites">{t("apiToken.allSites")}</Label>
                  <Tooltip>
                    <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("apiToken.siteAccessHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Switch id="allSites" checked={allSites}
                  onCheckedChange={(checked) =>
                    setItem({ ...item, siteIds: checked ? [] : sites.map((s) => s.id) })} />
              </div>
              {!allSites && (
                <div className="space-y-2 rounded-md border p-3">
                  <p className="text-xs text-muted-foreground">{t("apiToken.siteAccessDesc")}</p>
                  {sites.map((site) => (
                    <div key={site.id} className="flex items-center gap-2">
                      <Checkbox id={`site-${site.id}`}
                        checked={item.siteIds?.includes(site.id) ?? false}
                        onCheckedChange={(checked) => toggleSite(site.id, checked === true)} />
                      <Label htmlFor={`site-${site.id}`} className="font-normal cursor-pointer">
                        {site.name}
                      </Label>
                    </div>
                  ))}
                  {sites.length === 0 && (
                    <p className="text-xs text-muted-foreground">{t("apiToken.noSites")}</p>
                  )}
                </div>
              )}
            </div>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Token section (only on edit) */}
        {!isNew && item.token && (
          <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader
            icon={KeyRound}
            title={t("apiToken.tokenLabel")}
            description={t("apiToken.tokenDesc")}
          />
            <AdaptiveSectionCard.Content className="space-y-3">
              <div className="flex items-center gap-2">
                <Input value={item.token} readOnly className="font-mono text-sm bg-muted" />
                <Button variant="outline" size="icon" onClick={handleCopy}>
                  {copied ? <Check className="h-4 w-4 text-green-500" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">{t("apiToken.tokenHint")}</p>
            </AdaptiveSectionCard.Content>
          </AdaptiveSectionCard>
        )}
      </div>
    </>
  )
}
