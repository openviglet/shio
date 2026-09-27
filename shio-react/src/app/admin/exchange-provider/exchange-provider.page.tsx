import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { ArrowLeftRight, Save, ArrowLeft, Trash2, Info, Plus, X } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShExchangeProviderService } from "@/services/provider/exchange-provider.service"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"
import type {
  ShExchangeProviderInstance,
  ShExchangeProviderVendor,
} from "@/models/provider/exchange-provider.model"

const service = new ShExchangeProviderService()

export default function ExchangeProviderPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { exchangeProviderId } = useParams() as { exchangeProviderId: string }
  const isNew = exchangeProviderId === "new"

  const [item, setItem] = useState<ShExchangeProviderInstance>({
    id: "", name: "", description: "", enabled: true, properties: {},
  })
  const [newKey, setNewKey] = useState("")
  const [saving, setSaving] = useState(false)

  /*
   * SH1116 — two reads, both cancelled, and neither keyed on the form.
   *
   * `item.name` was in the first effect's dependency list, so typing the provider's name re-read it
   * and `setItem` put the saved row back over every field being edited — including the properties
   * the vendor form had just been filled in with. A query's key is its subject, which is the fix.
   *
   * The blank model for a new provider is its own query rather than a branch inside one, because it
   * has a different subject: `exchangeProvider:model` is the vendor-shaped skeleton and is the same
   * answer for every new provider, so it is worth caching under its own key.
   */
  const loaded = useQuery<ShExchangeProviderInstance>({
    queryKey: addressKey(`exchangeProvider:${exchangeProviderId}`),
    queryFn: ({ signal }) => service.get(exchangeProviderId, { signal }),
    enabled: !isNew,
  })
  const model = useQuery<ShExchangeProviderInstance>({
    queryKey: addressKey("exchangeProvider:model"),
    queryFn: ({ signal }) => service.model({ signal }),
    enabled: isNew,
  })
  const vendorList = useQuery<ShExchangeProviderVendor[]>({
    queryKey: addressKey("exchangeProvider:vendor"),
    queryFn: ({ signal }) => service.vendors({ signal }),
  })
  const vendors = vendorList.data ?? []

  /*
   * Set during render, not in an effect: React's own "adjust state when the input changes" pattern,
   * and `react-hooks/set-state-in-effect` is why it is not the effect it reads more naturally as.
   * `seeded` is the subject already poured into the form — the provider's id, or `"model"` for the
   * blank — so each answer seeds once and a curator's edits are never overwritten.
   */
  const [seeded, setSeeded] = useState<string | null>(null)
  const provider = loaded.data
  const blank = model.data
  if (provider && seeded !== provider.id) {
    setSeeded(provider.id)
    setItem({ ...provider, properties: provider.properties ?? {} })
  } else if (blank && seeded !== "model") {
    setSeeded("model")
    setItem({ ...blank, name: t("exchangeProvider.untitled"), properties: blank.properties ?? {} })
  }

  useEffect(() => {
    // A cancelled read is this side hanging up and has nothing to report (SH1047). The `catch(() =>
    // undefined)` this replaced swallowed every other failure too, which is how a provider that
    // would not load left a blank form ready to save over it.
    const failure = loaded.error ?? model.error
    if (failure && !isCancelled(failure)) reportProblem(failure, t("common.error"))
  }, [loaded.error, model.error, t])

  const properties = item.properties ?? {}

  const setProperty = (key: string, value: string) => {
    setItem({ ...item, properties: { ...properties, [key]: value } })
  }

  const removeProperty = (key: string) => {
    const next = { ...properties }
    delete next[key]
    setItem({ ...item, properties: next })
  }

  const addProperty = () => {
    const key = newKey.trim()
    if (!key || key in properties) return
    setItem({ ...item, properties: { ...properties, [key]: "" } })
    setNewKey("")
  }

  const handleSave = async () => {
    if (!item.name.trim()) {
      toast.error(t("exchangeProvider.nameRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("exchangeProvider.created"))
        navigate(`${ROUTES.ADMIN_EXCHANGE_PROVIDERS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("exchangeProvider.updated"))
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
        navigate(ROUTES.ADMIN_EXCHANGE_PROVIDERS)
      } catch (error) {
        reportProblem(error, t("common.error"))
      }
    }
  }

  return (
    <>
      <PageHero
        icon={ArrowLeftRight}
        title={t("exchangeProvider.title")}
        eyebrow={t("admin.title")}
        subtitle={t("exchangeProvider.description")}
        urlBase={ROUTES.ADMIN_ROOT}
      />
      <div>
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" aria-label={t("common.back")} onClick={() => navigate(ROUTES.ADMIN_EXCHANGE_PROVIDERS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("exchangeProvider.newInstance") : item.name}</span>
            {item.vendor?.name && <Badge variant="outline" className="text-xs">{item.vendor.name}</Badge>}
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
            icon={ArrowLeftRight}
            title={t("exchangeProvider.general")}
            description={t("exchangeProvider.generalDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t("common.name")}</Label>
              <Input id="name" value={item.name}
                onChange={(e) => setItem({ ...item, name: e.target.value })}
                placeholder={t("exchangeProvider.namePlaceholder")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t("common.description")}</Label>
              <Textarea id="description" value={item.description || ""}
                onChange={(e) => setItem({ ...item, description: e.target.value })}
                placeholder={t("exchangeProvider.descriptionPlaceholder")} rows={3} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="vendor">{t("exchangeProvider.vendor")}</Label>
              <Select value={item.vendor?.id ?? ""}
                onValueChange={(value) =>
                  setItem({ ...item, vendor: vendors.find((v) => v.id === value) })}>
                <SelectTrigger id="vendor">
                  <SelectValue placeholder={t("exchangeProvider.vendorPlaceholder")} />
                </SelectTrigger>
                <SelectContent>
                  {vendors.map((vendor) => (
                    <SelectItem key={vendor.id} value={vendor.id}>
                      {vendor.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {item.vendor?.description && (
                <p className="text-xs text-muted-foreground">{item.vendor.description}</p>
              )}
            </div>
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <Label htmlFor="enabled">{t("common.enabled")}</Label>
                <Tooltip>
                  <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("exchangeProvider.enabledHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Switch id="enabled" checked={item.enabled}
                onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
            </div>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* Configuration properties */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader
            icon={ArrowLeftRight}
            title={t("exchangeProvider.properties")}
            description={t("exchangeProvider.propertiesDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            {Object.keys(properties).length === 0 ? (
              <p className="text-xs text-muted-foreground">{t("exchangeProvider.noProperties")}</p>
            ) : (
              <div className="space-y-3">
                {Object.entries(properties).map(([key, value]) => (
                  <div key={key} className="flex items-end gap-2">
                    <div className="w-1/3 space-y-1">
                      <Label className="text-xs font-mono">{key}</Label>
                    </div>
                    <Input value={value}
                      onChange={(e) => setProperty(key, e.target.value)}
                      placeholder={t("exchangeProvider.valuePlaceholder")} />
                    <Button variant="ghost" size="icon" className="h-9 w-9 text-destructive shrink-0"
                      onClick={() => removeProperty(key)}>
                      <X className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
            <div className="flex items-center gap-2 border-t pt-4">
              <Input value={newKey}
                onChange={(e) => setNewKey(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); addProperty() } }}
                placeholder={t("exchangeProvider.keyPlaceholder")} className="font-mono" />
              <Button variant="outline" size="sm" onClick={addProperty} disabled={!newKey.trim()}>
                <Plus className="h-4 w-4 mr-1" />
                {t("exchangeProvider.addProperty")}
              </Button>
            </div>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>
    </>
  )
}
