import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard, BentoActionsMenu } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ROUTES } from "@/app/routes.const"
import { Webhook, Save, Copy, Check, Trash2 } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShSiteWebhookService } from "@/services/webhook/site-webhook.service"
import type { ShSiteWebhook, ShWebhookEventType } from "@/models/webhook/site-webhook.model"
import { SH_WEBHOOK_EVENT_TYPES } from "@/models/webhook/site-webhook.model"
import { useSites } from "@/hooks/use-catalogue"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"
import { isDirty } from "@/lib/form-dirty"

const service = new ShSiteWebhookService()

const ALL_SITES = "__all__"

export default function SiteWebhookPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { webhookId } = useParams() as { webhookId: string }
  const isNew = webhookId === "new"

  const [item, setItem] = useState<ShSiteWebhook>({
    id: "", name: "", siteId: null, url: "", secret: "", events: [], enabled: true,
    owner: "", createdDate: "",
  })
  // SH1046 — one cached read of the site list, shared with every other picker in the console.
  const sites = useSites().data ?? []
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)

  /*
   * SH1116 — read once per webhook, cancelled on leaving, and keyed on the id alone.
   *
   * `item.name` was in this effect's dependencies, so every keystroke in the name field re-read the
   * row and `setItem` put the saved copy back over what was being typed — in every field, not just
   * the one. A query's key is its subject, so that cannot happen here.
   */
  const loaded = useQuery<ShSiteWebhook>({
    queryKey: addressKey(`webhook:${webhookId}`),
    queryFn: ({ signal }) => service.get(webhookId, { signal }),
    enabled: !isNew,
  })

  // Set during render, not in an effect: React's own "adjust state when the input changes" pattern.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.data && seeded !== loaded.data.id) {
    setSeeded(loaded.data.id)
    setItem({ ...loaded.data, events: loaded.data.events ?? [] })
  }

  useEffect(() => {
    // The read had no failure path at all before: a 404 left the blank form on screen, saving over
    // nothing. A cancelled read is not a failure (SH1047) and is the one case with nothing to say.
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

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
        // SH1196 — the saved row is the new baseline, so the save bar stops saying "unsaved".
        await loaded.refetch()
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
        navigate(ROUTES.ADMIN_WEBHOOKS)
      } catch (error) {
        reportProblem(error, t("common.error"))
      }
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
      {/*
        SH1196 — one hero and the save-bar morph, which the contract asks for. A second bar under the
        hero repeated the record's name, stayed on screen for good and held Delete beside Save.
      */}
      <PageHero
        title={isNew ? t("siteWebhook.newInstance") : item.name || t("siteWebhook.title")}
        subtitle={t("siteWebhook.description")}
        urlBase={ROUTES.ADMIN_WEBHOOKS}
        backLabel={t("siteWebhook.title")}
        saveBadges={!isNew ? <Badge variant={item.enabled ? "default" : "secondary"}>{item.enabled ? t("common.enabled") : t("common.disabled")}</Badge> : undefined}
        onCancel={() => navigate(ROUTES.ADMIN_WEBHOOKS)}
        loading={saving}
        dirty={isDirty(item, isNew ? undefined : loaded.data && { ...loaded.data, events: loaded.data.events ?? [] })}
        titleMissing={!item.name.trim()}
        actions={
          <Button size="sm" onClick={handleSave} disabled={saving}>
            <Save className="h-4 w-4 mr-1" />
            {saving ? t("common.loading") : t("common.save")}
          </Button>
        }
        trailing={isNew ? undefined : (
          <BentoActionsMenu actions={[{ id: "webhook.delete", label: t("common.delete"), icon: Trash2,
            tone: "destructive", onSelect: () => void handleDelete() }]} />
        )}
      />
      <div>
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
              <div className="space-y-0.5">
                <Label htmlFor="url">{t("siteWebhook.url")}</Label>
                <p className="text-xs text-muted-foreground">{t("siteWebhook.urlHint")}</p>
              </div>
              <Input id="url" value={item.url} className="font-mono text-sm"
                onChange={(e) => setItem({ ...item, url: e.target.value })}
                placeholder={t("siteWebhook.urlPlaceholder")} />
            </div>
            {!isNew && (
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label htmlFor="enabled">{t("common.enabled")}</Label>
                  <p className="text-xs text-muted-foreground">{t("siteWebhook.enabledHint")}</p>
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
              <div className="space-y-0.5">
                <Label htmlFor="site">{t("siteWebhook.site")}</Label>
                <p className="text-xs text-muted-foreground">{t("siteWebhook.siteHint")}</p>
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
                <div className="space-y-0.5">
                  <Label htmlFor="allEvents">{t("siteWebhook.allEvents")}</Label>
                  <p className="text-xs text-muted-foreground">{t("siteWebhook.eventsHint")}</p>
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
