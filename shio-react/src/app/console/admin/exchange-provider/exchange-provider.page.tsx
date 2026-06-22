import { PageHeader } from "@/components/page-header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { ArrowLeftRight, Save, ArrowLeft, Trash2, Info, Plus, X } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShExchangeProviderService } from "@/services/provider/exchange-provider.service"
import type {
  ShExchangeProviderInstance,
  ShExchangeProviderVendor,
} from "@/models/provider/exchange-provider.model"

const service = new ShExchangeProviderService()

export default function ExchangeProviderPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { exchangeProviderId } = useParams() as { exchangeProviderId: string }
  const { resetBreadcrumb } = useBreadcrumb()
  const isNew = exchangeProviderId === "new"

  const [item, setItem] = useState<ShExchangeProviderInstance>({
    id: "", name: "", description: "", enabled: true, properties: {},
  })
  const [vendors, setVendors] = useState<ShExchangeProviderVendor[]>([])
  const [newKey, setNewKey] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("exchangeProvider.title"), href: ROUTES.ADMIN_EXCHANGE_PROVIDERS },
      { label: isNew ? t("common.create") : item.name || exchangeProviderId },
    ])
    if (isNew) {
      service.model()
        .then((model) => setItem({ ...model, name: t("exchangeProvider.untitled"), properties: model.properties ?? {} }))
        .catch(() => undefined)
    } else {
      service.get(exchangeProviderId)
        .then((provider) => setItem({ ...provider, properties: provider.properties ?? {} }))
    }
  }, [resetBreadcrumb, t, exchangeProviderId, isNew, item.name])

  useEffect(() => {
    service.vendors().then(setVendors).catch(() => setVendors([]))
  }, [])

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
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(item.id)
      navigate(ROUTES.ADMIN_EXCHANGE_PROVIDERS)
    }
  }

  return (
    <>
      <PageHeader title={t("exchangeProvider.title")} turIcon={ArrowLeftRight} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6 max-w-2xl">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-orange-50 dark:bg-orange-950/30 rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.ADMIN_EXCHANGE_PROVIDERS)}>
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
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">{t("exchangeProvider.general")}</CardTitle>
            <CardDescription>{t("exchangeProvider.generalDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
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
                  <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                  <TooltipContent>{t("exchangeProvider.enabledHint")}</TooltipContent>
                </Tooltip>
              </div>
              <Switch id="enabled" checked={item.enabled}
                onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
            </div>
          </CardContent>
        </Card>

        {/* Configuration properties */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("exchangeProvider.properties")}</CardTitle>
            <CardDescription>{t("exchangeProvider.propertiesDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
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
          </CardContent>
        </Card>
      </div>
    </>
  )
}
