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
import { Fingerprint, Save, ArrowLeft, Trash2, Info, Plus, X } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShAuthProviderService } from "@/services/provider/auth-provider.service"
import type {
  ShAuthProviderInstance,
  ShAuthProviderVendor,
} from "@/models/provider/auth-provider.model"

const service = new ShAuthProviderService()

export default function AuthProviderPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { authProviderId } = useParams() as { authProviderId: string }
  const isNew = authProviderId === "new"

  const [item, setItem] = useState<ShAuthProviderInstance>({
    id: "", name: "", description: "", enabled: true, properties: {},
  })
  const [vendors, setVendors] = useState<ShAuthProviderVendor[]>([])
  const [newKey, setNewKey] = useState("")
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (isNew) {
      service.model()
        .then((model) => setItem({ ...model, name: t("authProvider.untitled"), properties: model.properties ?? {} }))
        .catch(() => undefined)
    } else {
      service.get(authProviderId)
        .then((provider) => setItem({ ...provider, properties: provider.properties ?? {} }))
    }
  }, [t, authProviderId, isNew, item.name])

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
      toast.error(t("authProvider.nameRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("authProvider.created"))
        navigate(`${ROUTES.ADMIN_AUTH_PROVIDERS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("authProvider.updated"))
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
      navigate(ROUTES.ADMIN_AUTH_PROVIDERS)
    }
  }

  return (
    <>
      <PageHero
        icon={Fingerprint}
        title={t("authProvider.title")}
        eyebrow={t("admin.title")}
        subtitle={t("authProvider.description")}
        urlBase={ROUTES.ADMIN_ROOT}
      />
      <div className="p-6 max-w-2xl">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.ADMIN_AUTH_PROVIDERS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("authProvider.newInstance") : item.name}</span>
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
            icon={Fingerprint}
            title={t("authProvider.general")}
            description={t("authProvider.generalDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">{t("common.name")}</Label>
              <Input id="name" value={item.name}
                onChange={(e) => setItem({ ...item, name: e.target.value })}
                placeholder={t("authProvider.namePlaceholder")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t("common.description")}</Label>
              <Textarea id="description" value={item.description || ""}
                onChange={(e) => setItem({ ...item, description: e.target.value })}
                placeholder={t("authProvider.descriptionPlaceholder")} rows={3} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="vendor">{t("authProvider.vendor")}</Label>
              <Select value={item.vendor?.id ?? ""}
                onValueChange={(value) =>
                  setItem({ ...item, vendor: vendors.find((v) => v.id === value) })}>
                <SelectTrigger id="vendor">
                  <SelectValue placeholder={t("authProvider.vendorPlaceholder")} />
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
                  <TooltipContent>{t("authProvider.enabledHint")}</TooltipContent>
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
            icon={Fingerprint}
            title={t("authProvider.properties")}
            description={t("authProvider.propertiesDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            {Object.keys(properties).length === 0 ? (
              <p className="text-xs text-muted-foreground">{t("authProvider.noProperties")}</p>
            ) : (
              <div className="space-y-3">
                {Object.entries(properties).map(([key, value]) => (
                  <div key={key} className="flex items-end gap-2">
                    <div className="w-1/3 space-y-1">
                      <Label className="text-xs font-mono">{key}</Label>
                    </div>
                    <Input value={value}
                      onChange={(e) => setProperty(key, e.target.value)}
                      placeholder={t("authProvider.valuePlaceholder")} />
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
                placeholder={t("authProvider.keyPlaceholder")} className="font-mono" />
              <Button variant="outline" size="sm" onClick={addProperty} disabled={!newKey.trim()}>
                <Plus className="h-4 w-4 mr-1" />
                {t("authProvider.addProperty")}
              </Button>
            </div>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>
    </>
  )
}
