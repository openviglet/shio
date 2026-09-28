import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { Building2, Save, ArrowLeft, Trash2, Info, Sparkles, Ban, CircleCheck } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShTenantService } from "@/services/tenant/tenant.service"
import type { ShTenant } from "@/models/tenant/tenant.model"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"

const service = new ShTenantService()

export default function TenantPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { tenantId } = useParams() as { tenantId: string }
  const isNew = tenantId === "new"

  const [item, setItem] = useState<ShTenant>({
    id: "", name: "", description: "", enabled: true, status: "ACTIVE", plan: "FREE", owner: "", createdDate: "",
  })
  const [saving, setSaving] = useState(false)
  const isDefault = item.name === "shio"
  const isSuspended = item.status === "SUSPENDED"

  /*
   * SH1116 — the server's copy, read once per tenant and cancelled when the curator leaves.
   *
   * The effect this replaced had `item.name` in its dependencies, so it re-read on **every
   * keystroke in the name field** and `setItem` put the saved row back: the curator's edits, to
   * every field, reverted a moment after they were typed. The key is the id and nothing else, which
   * is the whole of that fix — a query re-runs when its subject changes, not when the form does.
   */
  const loaded = useQuery<ShTenant>({
    queryKey: addressKey(`tenant:${tenantId}`),
    queryFn: ({ signal }) => service.get(tenantId, { signal }),
    enabled: !isNew,
  })

  // Set during render, not in an effect: React's own "adjust state when the input changes" pattern.
  const [seeded, setSeeded] = useState<string | null>(null)
  if (loaded.data && seeded !== loaded.data.id) {
    setSeeded(loaded.data.id)
    setItem(loaded.data)
  }

  useEffect(() => {
    // A cancelled read is this side hanging up and has no answer to report (SH1047).
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  const handleSave = async () => {
    if (!item.name.trim()) {
      toast.error(t("tenantAdmin.nameRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("tenantAdmin.created"))
        navigate(`${ROUTES.ADMIN_TENANTS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("tenantAdmin.updated"))
      }
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (isDefault) return
    if (confirm(t("dialog.deleteMessage"))) {
      // SH1139 — a delete that navigates away is not a delete that was reported. This awaited and
      // left, so a refusal escaped as an unhandled rejection and the dock, its live caption and the
      // curator all learned nothing: the row vanished from the list either way.
      try {
        await service.delete(item.id)
        toast.success(t("common.deleted"))
        navigate(ROUTES.ADMIN_TENANTS)
      } catch (error) {
        reportProblem(error, t("common.error"))
      }
    }
  }

  const handleProvision = async () => {
    try {
      const result = await service.provision(item.id)
      toast.success(t("tenantAdmin.provisioned", { count: result.postTypesCreated }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  const handleToggleSuspend = async () => {
    try {
      const updated = isSuspended ? await service.activate(item.id) : await service.suspend(item.id)
      setItem(updated)
      toast.success(isSuspended ? t("tenantAdmin.activated") : t("tenantAdmin.suspended"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  return (
    <>
      <PageHero
        icon={Building2}
        title={t("tenantAdmin.title")}
        eyebrow={t("admin.title")}
        subtitle={t("tenantAdmin.description")}
        urlBase={ROUTES.ADMIN_TENANTS}
        backLabel={t("tenantAdmin.title")}
      />
      <div>
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" aria-label={t("common.back")} onClick={() => navigate(ROUTES.ADMIN_TENANTS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("tenantAdmin.newInstance") : item.name}</span>
            {isDefault && (
              <Badge variant="secondary" className="text-xs">{t("tenantAdmin.defaultBadge")}</Badge>
            )}
            {!isNew && isSuspended && (
              <Badge variant="outline" className="text-xs border-amber-300 text-amber-600 dark:border-amber-700 dark:text-amber-400">
                {t("tenantAdmin.statusSuspended")}
              </Badge>
            )}
          </div>
          <div className="flex items-center gap-2">
            {!isNew && !isDefault && (
              <Button variant="ghost" size="sm" onClick={handleToggleSuspend}>
                {isSuspended
                  ? <><CircleCheck className="h-4 w-4 mr-1" />{t("tenantAdmin.activate")}</>
                  : <><Ban className="h-4 w-4 mr-1" />{t("tenantAdmin.suspend")}</>}
              </Button>
            )}
            {!isNew && !isDefault && (
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

        <AdaptiveSectionCard className="mb-6">
          <AdaptiveSectionCard.StaticHeader
            icon={Building2}
            title={t("tenantAdmin.general")}
            description={t("tenantAdmin.generalDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Label htmlFor="name">{t("tenantAdmin.name")}</Label>
                <Tooltip>
                  <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{isDefault ? t("tenantAdmin.nameImmutable") : t("tenantAdmin.nameHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Input id="name" value={item.name} className="font-mono"
                disabled={!isNew}
                onChange={(e) => setItem({ ...item, name: e.target.value })}
                placeholder={t("tenantAdmin.namePlaceholder")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t("common.description")}</Label>
              <Textarea id="description" value={item.description || ""}
                onChange={(e) => setItem({ ...item, description: e.target.value })} rows={3} />
            </div>
            {!isNew && !isDefault && (
              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="plan">{t("tenantAdmin.plan")}</Label>
                  <Tooltip>
                    <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("tenantAdmin.planHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Input id="plan" value={item.plan || ""} className="font-mono"
                  onChange={(e) => setItem({ ...item, plan: e.target.value })}
                  placeholder="FREE" />
              </div>
            )}
            {!isNew && (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="enabled">{t("common.enabled")}</Label>
                  {isDefault && (
                    <Tooltip>
                      <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                      <TooltipContent>{t("tenantAdmin.defaultHint")}</TooltipContent>
                    </Tooltip>
                  )}
                </div>
                <Switch id="enabled" checked={item.enabled} disabled={isDefault}
                  onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
              </div>
            )}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Provisioning (SH22 Part C) — copy the default content model into this tenant */}
        {!isNew && !isDefault && (
          <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader
            icon={Building2}
            title={t("tenantAdmin.provision")}
            description={t("tenantAdmin.provisionHint")}
          />
            <AdaptiveSectionCard.Content>
              <Button variant="outline" size="sm" onClick={handleProvision}>
                <Sparkles className="h-4 w-4 mr-1" />
                {t("tenantAdmin.provision")}
              </Button>
            </AdaptiveSectionCard.Content>
          </AdaptiveSectionCard>
        )}
      </div>
    </>
  )
}
