import { LoginForm } from "@/components/login/login-form"
import { ShLogo } from "@/components/logo/sh-logo"
import { ModeToggle } from "@/components/mode-toggle"
import { Login } from "@viglet/viglet-design-system"
import { FileText, Globe, Layers } from "lucide-react"
import { useTranslation } from "react-i18next"
import { LoginSettingsButton, useLoginSettings } from "./login-settings"

const LoginPage = () => {
  const { t } = useTranslation()
  const { settings, update } = useLoginSettings()

  return (
    <Login color="#ea580c" colorDark="#fb923c">
      <Login.Background
        withFormulas={settings.tokens}
        withBonds={settings.tokens}
        withLightning={settings.animations}
        withExplosion={settings.explosion}
        withOrbs={settings.animations}
        withGrid
      />

      <Login.Settings>
        <LoginSettingsButton settings={settings} onUpdate={update} />
        <ModeToggle />
      </Login.Settings>

      <Login.Content>
        <Login.Logo pulsePaused={!settings.animations}>
          <ShLogo size={52} />
        </Login.Logo>

        <Login.Title>Viglet Shio</Login.Title>
        <Login.Tagline>{t("login.heroTagline")}</Login.Tagline>

        <Login.Features>
          <Login.FeaturePill icon={<FileText className="h-3.5 w-3.5 text-orange-500" />}>
            {t("login.featureCms")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Globe className="h-3.5 w-3.5 text-orange-500" />}>
            {t("login.featureApi")}
          </Login.FeaturePill>
          <Login.FeaturePill icon={<Layers className="h-3.5 w-3.5 text-amber-500" />}>
            {t("login.featureTemplates")}
          </Login.FeaturePill>
        </Login.Features>

        <Login.Card>
          <LoginForm />
        </Login.Card>

        <Login.Footer>Viglet Shio · Headless CMS</Login.Footer>
      </Login.Content>
    </Login>
  )
}

export default LoginPage
