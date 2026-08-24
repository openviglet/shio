import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Mail, Save, Info } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShEmailConfigService } from "@/services/config/email-config.service"
import type { ShEmailConfig } from "@/models/config/email-config.model"

const service = new ShEmailConfigService()

const EMPTY: ShEmailConfig = {
  enabled: false, host: "", port: "", username: "", password: "",
  testConnection: false, smtpAuth: false, tlsEnabled: false, tlsRequired: false, sslEnabled: false,
}

export default function EmailConfigPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [config, setConfig] = useState<ShEmailConfig>(EMPTY)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("emailConfig.title") },
    ])
    service.get().then(setConfig).catch(() => setConfig(EMPTY))
  }, [resetBreadcrumb, t])

  const set = <K extends keyof ShEmailConfig>(key: K, value: ShEmailConfig[K]) =>
    setConfig((prev) => ({ ...prev, [key]: value }))

  const handleSave = async () => {
    setSaving(true)
    try {
      const saved = await service.update(config)
      setConfig(saved)
      toast.success(t("emailConfig.saved"))
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const toggleRow = (
    key: keyof ShEmailConfig, label: string, hint: string,
  ) => (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2">
        <Label htmlFor={key}>{label}</Label>
        <Tooltip>
          <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
          <TooltipContent>{hint}</TooltipContent>
        </Tooltip>
      </div>
      <Switch id={key} checked={config[key] as boolean}
        onCheckedChange={(checked) => set(key, checked as ShEmailConfig[typeof key])} />
    </div>
  )

  return (
    <>
      <PageHeader title={t("emailConfig.title")} turIcon={Mail} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6 max-w-2xl">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-[var(--vg-accent-surface)] rounded-lg px-4 py-3 mb-6 border">
          <span className="font-semibold">{t("emailConfig.title")}</span>
          <Button size="sm" onClick={handleSave} disabled={saving}>
            <Save className="h-4 w-4 mr-1" />
            {saving ? t("common.loading") : t("common.save")}
          </Button>
        </div>

        {/* Server section */}
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">{t("emailConfig.server")}</CardTitle>
            <CardDescription>{t("emailConfig.serverDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {toggleRow("enabled", t("common.enabled"), t("emailConfig.enabledHint"))}
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="host">{t("emailConfig.host")}</Label>
                <Input id="host" value={config.host} autoComplete="off"
                  onChange={(e) => set("host", e.target.value)} placeholder="smtp.example.com" />
              </div>
              <div className="space-y-2">
                <Label htmlFor="port">{t("emailConfig.port")}</Label>
                <Input id="port" value={config.port} autoComplete="off"
                  onChange={(e) => set("port", e.target.value)} placeholder="587" />
              </div>
            </div>
            <div className="space-y-2">
              <Label htmlFor="username">{t("emailConfig.username")}</Label>
              <Input id="username" value={config.username} autoComplete="off"
                onChange={(e) => set("username", e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">{t("emailConfig.password")}</Label>
              <Input id="password" type="password" value={config.password} autoComplete="new-password"
                onChange={(e) => set("password", e.target.value)} />
            </div>
            {toggleRow("testConnection", t("emailConfig.testConnection"), t("emailConfig.testConnectionHint"))}
          </CardContent>
        </Card>

        {/* SMTP section */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t("emailConfig.smtp")}</CardTitle>
            <CardDescription>{t("emailConfig.smtpDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {toggleRow("smtpAuth", t("emailConfig.smtpAuth"), t("emailConfig.smtpAuthHint"))}
            {toggleRow("tlsEnabled", t("emailConfig.tlsEnabled"), t("emailConfig.tlsEnabledHint"))}
            {toggleRow("tlsRequired", t("emailConfig.tlsRequired"), t("emailConfig.tlsRequiredHint"))}
            {toggleRow("sslEnabled", t("emailConfig.sslEnabled"), t("emailConfig.sslEnabledHint"))}
          </CardContent>
        </Card>
      </div>
    </>
  )
}
