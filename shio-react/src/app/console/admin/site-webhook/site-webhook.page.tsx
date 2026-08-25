import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Webhook, Save, ArrowLeft, Copy, Check, Trash2, Info } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShSiteWebhookService } from "@/services/webhook/site-webhook.service"
import type { ShSiteWebhook, ShWebhookEventType } from "@/models/webhook/site-webhook.model"
import { SH_WEBHOOK_EVENT_TYPES } from "@/models/webhook/site-webhook.model"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"

const service = new ShSiteWebhookService()
const siteService = new ShSiteService()

const ALL_SITES = "__all__"

export default function SiteWebhookPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { webhookId } = useParams() as { webhookId: string }
  const { resetBreadcrumb } = useBreadcrumb()
  const isNew = webhookId === "new"

  const [item, setItem] = useState<ShSiteWebhook>({
    id: "", name: "", siteId: null, url: "", secret: "", events: [], enabled: true,
    owner: "", createdDate: "",
  })
  const [sites, setSites] = useState<ShSite[]>([])
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("siteWebhook.title"), href: ROUTES.ADMIN_WEBHOOKS },
      { label: isNew ? t("common.create") : item.name || webhookId },
    ])
    if (!isNew) {
      service.get(webhookId).then((wh) => setItem({ ...wh, events: wh.events ?? [] }))
    }
  }, [resetBreadcrumb, t, webhookId, isNew, item.name])

  useEffect(() => {
    siteService.query().then(setSites).catch(() => setSites([]))
  }, [])

  const allEvents = (item.events?.length ?? 0) === 0
  const toggleEvent = (event: ShWebhookEventType, checked: boolean) => {
    const current = item.events ?? []
    const next = checked ? [...current, event] : current.filter((e) => e !== event)
    setItem({ ...item, events: next })
  }

  const handleSave = async () => {
    if (!item.name.trim()) {
      toast.error(t("siteWebhook.nameRequired"))
      return
    }
    if (!item.url.trim()) {
      toast.error(t("siteWebhook.urlRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("siteWebhook.created"))
        navigate(`${ROUTES.ADMIN_WEBHOOKS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("siteWebhook.updated"))
      }
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(item.id)
      navigate(ROUTES.ADMIN_WEBHOOKS)
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(item.secret)
      setCopied(true)
      toast.success(t("siteWebhook.copied"))
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t("siteWebhook.copyFailed"))
    }
  }

  return (
    <>
      <AdaptivePageHeader
        icon={Webhook}
        title={t("siteWebhook.title")}
        eyebrow={t("admin.title")}
        subtitle={t("siteWebhook.description")}
        urlBase={ROUTES.ADMIN_ROOT}
      />
      <div className="p-6 max-w-2xl">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.ADMIN_WEBHOOKS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("siteWebhook.newInstance") : item.name}</span>
            {!isNew && (
              <Badge variant={item.enabled ? "default" : "secondary"}>
                {item.enabled ? t("common.enabled") : t("common.disabled")}
              </Badge>
            )}
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
            icon={Webhook}
            title={t("siteWebhook.general")}
            description={t("siteWebhook.generalDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t("common.title")}</Label>
              <Input id="name" value={item.name}
                onChange={(e) => setItem({ ...item, name: e.target.value })}
                placeholder={t("siteWebhook.namePlaceholder")} />
            </div>
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="url">{t("siteWebhook.url")}</Label>
                <Tooltip>
                  <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("siteWebhook.urlHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Input id="url" value={item.url} className="font-mono text-sm"
                onChange={(e) => setItem({ ...item, url: e.target.value })}
                placeholder={t("siteWebhook.urlPlaceholder")} />
            </div>
            {!isNew && (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="enabled">{t("common.enabled")}</Label>
                  <Tooltip>
                    <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("siteWebhook.enabledHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Switch id="enabled" checked={item.enabled}
                  onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
              </div>
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Scope section — site + events */}
        <AdaptiveSectionCard className="mb-6">
          <AdaptiveSectionCard.StaticHeader
            icon={Webhook}
            title={t("siteWebhook.scope")}
            description={t("siteWebhook.scopeDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-5">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="site">{t("siteWebhook.site")}</Label>
                <Tooltip>
                  <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("siteWebhook.siteHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Select value={item.siteId && item.siteId.length > 0 ? item.siteId : ALL_SITES}
                onValueChange={(value) => setItem({ ...item, siteId: value === ALL_SITES ? null : value })}>
                <SelectTrigger id="site">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL_SITES}>{t("siteWebhook.allSites")}</SelectItem>
                  {sites.map((site) => (
                    <SelectItem key={site.id} value={site.id}>{site.name}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="allEvents">{t("siteWebhook.allEvents")}</Label>
                  <Tooltip>
                    <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("siteWebhook.eventsHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Switch id="allEvents" checked={allEvents}
                  onCheckedChange={(checked) =>
                    setItem({ ...item, events: checked ? [] : [...SH_WEBHOOK_EVENT_TYPES] })} />
              </div>
              {!allEvents && (
                <div className="space-y-2 rounded-md border p-3">
                  <p className="text-xs text-muted-foreground">{t("siteWebhook.eventsDesc")}</p>
                  {SH_WEBHOOK_EVENT_TYPES.map((event) => (
                    <div key={event} className="flex items-center gap-2">
                      <Checkbox id={`event-${event}`}
                        checked={item.events?.includes(event) ?? false}
                        onCheckedChange={(checked) => toggleEvent(event, checked === true)} />
                      <Label htmlFor={`event-${event}`} className="font-normal cursor-pointer">
                        {t(`siteWebhook.event${event.charAt(0)}${event.slice(1).toLowerCase()}`)}
                      </Label>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Secret section (only on edit) */}
        {!isNew && item.secret && (
          <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader
            icon={Webhook}
            title={t("siteWebhook.secret")}
            description={t("siteWebhook.secretDesc")}
          />
            <AdaptiveSectionCard.Content className="space-y-3">
              <div className="flex items-center gap-2">
                <Input value={item.secret} readOnly className="font-mono text-sm bg-muted" />
                <Button variant="outline" size="icon" onClick={handleCopy}>
                  {copied ? <Check className="h-4 w-4 text-green-500" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">{t("siteWebhook.secretHint")}</p>
            </AdaptiveSectionCard.Content>
          </AdaptiveSectionCard>
        )}
      </div>
    </>
  )
}
