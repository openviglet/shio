import { ShLogo } from "@/components/logo/sh-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ShSetupService } from "@/services/auth/setup.service"
import { StartupFirst } from "@viglet/viglet-design-system"
import { AlertCircle, Check, KeyRound, Loader2, Shield, ShieldCheck } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { LoginSettingsButton, useLoginSettings } from "../login/login-settings"

const setupService = new ShSetupService()

export default function SetupPage() {
  const { t } = useTranslation()
  const { settings, update } = useLoginSettings()
  const [password, setPassword] = useState("")
  const [confirmPassword, setConfirmPassword] = useState("")
  const [error, setError] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    setupService.status().then((status) => {
      if (!status.required) {
        globalThis.location.href = "/login"
      }
      setChecking(false)
    }).catch(() => setChecking(false))
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError("")

    if (password.length < 6) {
      setError(t("setup.minLength"))
      return
    }
    if (password !== confirmPassword) {
      setError(t("setup.passwordMismatch"))
      return
    }

    setIsLoading(true)
    try {
      await setupService.setAdminPassword(password)
      globalThis.location.href = "/login"
    } catch {
      setError(t("setup.setupFailed"))
      setIsLoading(false)
    }
  }

  if (checking) {
    return (
      <div className="min-h-svh flex items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-orange-500" />
      </div>
    )
  }

  return (
    <StartupFirst color="#ea580c" colorDark="#fb923c">
      <StartupFirst.Background
        withFormulas={settings.tokens}
        withBonds={settings.tokens}
        withLightning={settings.animations}
        withExplosion={settings.explosion}
        withOrbs={settings.animations}
        withGrid
      />

      <div className="absolute top-4 right-4 z-30 flex items-center gap-2">
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </div>

      <StartupFirst.Content>
        <StartupFirst.Logo size={64} pulsePaused={!settings.animations}>
          <ShLogo size={52} />
        </StartupFirst.Logo>

        <StartupFirst.Title>Viglet Shio CMS</StartupFirst.Title>
        <StartupFirst.Description>{t("setup.description")}</StartupFirst.Description>

        <div className="flex flex-wrap justify-center gap-2 mb-5">
          <div className="vig-login__feature-pill flex items-center gap-1.5 rounded-full px-3 py-1">
            <Shield className="h-3.5 w-3.5 text-orange-500" />
            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
              {t("setup.title")}
            </span>
          </div>
        </div>

        <StartupFirst.Card>
          <form onSubmit={handleSubmit} className="flex flex-col gap-5 p-6">
            {error && (
              <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{error}</span>
              </div>
            )}

            <div className="rounded-lg border bg-muted/30 p-4 space-y-1">
              <div className="flex items-center gap-2 text-sm font-medium">
                <ShieldCheck className="h-4 w-4 text-orange-500" />
                {t("setup.hint")}
              </div>
              <p className="text-xs text-muted-foreground">{t("setup.hintDescription")}</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="password">{t("setup.newPassword")}</Label>
              <div className="relative">
                <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={t("setup.enterPassword")}
                  className="pl-9 h-11"
                  autoFocus
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">{t("setup.confirmPassword")}</Label>
              <div className="relative">
                <Check className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder={t("setup.confirmPasswordPlaceholder")}
                  className="pl-9 h-11"
                />
              </div>
            </div>

            <Button
              type="submit"
              className="w-full h-11 bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 text-white font-semibold"
              disabled={isLoading}
            >
              {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <ShieldCheck className="mr-2 h-4 w-4" />}
              {isLoading ? t("common.loading") : t("setup.setPassword")}
            </Button>
          </form>
        </StartupFirst.Card>

        <StartupFirst.Footer>Viglet Shio CMS &mdash; Content Management System</StartupFirst.Footer>
      </StartupFirst.Content>
    </StartupFirst>
  )
}
