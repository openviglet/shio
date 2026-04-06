import { LoginForm } from "@/components/login/login-form"
import { ModeToggle } from "@/components/mode-toggle"
import { ShLogo } from "@/components/logo/sh-logo"
import { FileText, Globe, Layers } from "lucide-react"
import { useTranslation } from "react-i18next"
import { LoginFloatingBg } from "./login-floating-bg"
import { LoginSettingsButton, useLoginSettings } from "./login-settings"
import "./login-animations.css"

const LoginPage = () => {
  const { t } = useTranslation()
  const { settings, update } = useLoginSettings()

  return (
    <div className="relative min-h-svh flex items-center justify-center overflow-hidden bg-gradient-to-br from-orange-50 via-amber-50 to-orange-100 dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 px-4 py-12">
      {/* Animated background */}
      <LoginFloatingBg settings={settings} />

      {/* Top-right controls */}
      <div className="absolute top-4 right-4 z-20 flex items-center gap-1">
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </div>

      {/* Centered glass card */}
      <div className="relative z-10 w-full max-w-md">
        <div className="login-glass-card rounded-2xl p-8 sm:p-10">
          {/* Logo + branding */}
          <div className="flex flex-col items-center gap-5 mb-8 login-fade-in">
            <div className="relative login-logo-ring rounded-xl">
              <ShLogo size={64} />
            </div>
            <div className="text-center">
              <h1 className="text-2xl font-bold tracking-tight bg-gradient-to-r from-orange-600 to-amber-600 dark:from-orange-400 dark:to-amber-400 bg-clip-text text-transparent">
                Viglet Shio CMS
              </h1>
              <p className="text-sm text-muted-foreground mt-1">{t("login.heroTagline")}</p>
            </div>
          </div>

          {/* Feature pills */}
          <div className="flex flex-wrap items-center justify-center gap-2 mb-8 login-fade-in-d1">
            <div className="login-feature-pill flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-foreground/70">
              <FileText className="h-3.5 w-3.5 text-orange-500" />
              {t("login.featureCms")}
            </div>
            <div className="login-feature-pill flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-foreground/70">
              <Globe className="h-3.5 w-3.5 text-orange-500" />
              {t("login.featureApi")}
            </div>
            <div className="login-feature-pill flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs text-foreground/70">
              <Layers className="h-3.5 w-3.5 text-amber-500" />
              {t("login.featureTemplates")}
            </div>
          </div>

          {/* Login form */}
          <div className="login-fade-in-d2">
            <LoginForm />
          </div>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
