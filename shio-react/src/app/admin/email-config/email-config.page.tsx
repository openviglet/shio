import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { Mail, Save, Info } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShEmailConfigService } from "@/services/config/email-config.service"
import type { ShEmailConfig } from "@/models/config/email-config.model"
import { reportProblem } from "@/lib/problem"
import { addressKey } from "@/lib/query-keys"
import { isCancelled } from "@/lib/read-options"

const service = new ShEmailConfigService()

const EMPTY: ShEmailConfig = {
  enabled: false, host: "", port: "", username: "", password: "",
  testConnection: false, smtpAuth: false, tlsEnabled: false, tlsRequired: false, sslEnabled: false,
}

export default function EmailConfigPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [config, setConfig] = useState<ShEmailConfig>(EMPTY)
  const [saving, setSaving] = useState(false)

  /*
   * SH1116 — one read, cancelled when the curator leaves, and a failure that is not an empty form.
   *
   * `catch(() => setConfig(EMPTY))` made "the server did not answer" and "nothing is configured" the
   * same screen — and the second one invites a save that overwrites the first one's real settings.
   * `useQuery` answers the three states apart, and `isCancelled` keeps a navigation out of the
   * failure path (SH1047).
   */
  const loaded = useQuery<ShEmailConfig>({
    queryKey: addressKey("emailConfig:"),
    queryFn: ({ signal }) => service.get({ signal }),
  })

  // Set during render rather than in an effect: `react-hooks/set-state-in-effect`, and React's own
  // "adjust state when the input changes" pattern. One answer, so one seeding.
  const [seeded, setSeeded] = useState(false)
  if (loaded.data && !seeded) {
    setSeeded(true)
    setConfig(loaded.data)
  }

  useEffect(() => {
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

  const set = <K extends keyof ShEmailConfig>(key: K, value: ShEmailConfig[K]) =>
    setConfig((prev) => ({ ...prev, [key]: value }))

  const handleSave = async () => {
    setSaving(true)
    try {
      const saved = await service.update(config)
      setConfig(saved)
      toast.success(t("emailConfig.saved"))
    } catch (error) {
      reportProblem(error, t("common.error"))
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
          <TooltipTrigger aria-label={t("common.moreInfo")}><Info aria-hidden="true" className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
          <TooltipContent>{hint}</TooltipContent>
        </Tooltip>
      </div>
      <Switch id={key} checked={config[key] as boolean}
        onCheckedChange={(checked) => set(key, checked as ShEmailConfig[typeof key])} />
    </div>
  )

  return (
    <>
      {/*
        SH905 — this is the page §LV.8 said to watch for: the one that "needs something the shell
        does not have". It turned out to need the opposite — it had **hand-rolled** what the
        adapter already gives.

        The bar below was a `sticky top-0` div repeating the page title with a Save beside it.
        Two problems, and neither is cosmetic. It duplicated the heading, so the page named itself
        twice with nothing between the two. And it was sticky *without* a morph: it sat over the
        content from the first pixel of scroll rather than fading in as the hero left, which is
        the one behaviour the whole save-bar design is about.

        Passing `onCancel` is what makes the adapter render the pair at all (`hasSaveBar`), and
        passing `actions` **replaces** the hero's own Save/Cancel — the package's line is
        `children: actions ?? <submit + cancel>`. That matters here: the hero's default Save is a
        bare `type="submit"` and this page has no form for it to submit, so taking the default
        would have been SH899's dead button one more time. This page's Save is an `onClick`, so it
        goes in `actions`, and there is exactly one of it.
      */}
      <PageHero
        title={t("emailConfig.title")}
        eyebrow={t("admin.title")}
        subtitle={t("emailConfig.description")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
        onCancel={() => navigate(ROUTES.HOME)}
        loading={saving}
        actions={
          <Button size="sm" onClick={handleSave} disabled={saving}>
            <Save className="h-4 w-4 mr-1" />
            {saving ? t("common.loading") : t("common.save")}
          </Button>
        }
      />
      <div>
        {/* Server section */}
        <AdaptiveSectionCard className="mb-6">
          <AdaptiveSectionCard.StaticHeader
            icon={Mail}
            title={t("emailConfig.server")}
            description={t("emailConfig.serverDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
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
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>

        {/* SMTP section */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader
            icon={Mail}
            title={t("emailConfig.smtp")}
            description={t("emailConfig.smtpDesc")}
          />
          <AdaptiveSectionCard.Content className="space-y-4">
            {toggleRow("smtpAuth", t("emailConfig.smtpAuth"), t("emailConfig.smtpAuthHint"))}
            {toggleRow("tlsEnabled", t("emailConfig.tlsEnabled"), t("emailConfig.tlsEnabledHint"))}
            {toggleRow("tlsRequired", t("emailConfig.tlsRequired"), t("emailConfig.tlsRequiredHint"))}
            {toggleRow("sslEnabled", t("emailConfig.sslEnabled"), t("emailConfig.sslEnabledHint"))}
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>
    </>
  )
}
