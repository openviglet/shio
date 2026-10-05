import { useCallback, useState } from "react"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"
import { Separator } from "@/components/ui/separator"
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover"
import { IconRun } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

const LANGUAGES = [
  { code: "en", label: "English" },
  { code: "pt", label: "Português" },
] as const

const STORAGE_KEY = "shio-login-settings"

export interface LoginAnimationSettings {
  animations: boolean
  explosion: boolean
  tokens: boolean
}

const DEFAULTS: LoginAnimationSettings = {
  animations: true,
  explosion: false,
  tokens: true,
}

function loadSettings(): LoginAnimationSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) return { ...DEFAULTS, ...JSON.parse(raw) }
  } catch { /* ignore */ }
  return { ...DEFAULTS }
}

function saveSettings(settings: LoginAnimationSettings) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
}

export function useLoginSettings() {
  const [settings, setSettings] = useState<LoginAnimationSettings>(loadSettings)

  const update = useCallback((patch: Partial<LoginAnimationSettings>) => {
    setSettings((prev) => {
      const next = { ...prev, ...patch }
      saveSettings(next)
      return next
    })
  }, [])

  return { settings, update }
}

interface LoginSettingsButtonProps {
  readonly settings: LoginAnimationSettings
  readonly onUpdate: (patch: Partial<LoginAnimationSettings>) => void
}

export function LoginSettingsButton({ settings, onUpdate }: LoginSettingsButtonProps) {
  const { t, i18n } = useTranslation()

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          size="sm"
        >
          <IconRun className="h-[1.2rem] w-[1.2rem]" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" side="bottom" className="w-56 p-3 space-y-3">
        <div className="flex items-center justify-between">
          <label htmlFor="login-anim" className="text-sm cursor-pointer">
            {t("login.settingsAnimations")}
          </label>
          <Switch
            id="login-anim"
            checked={settings.animations}
            onCheckedChange={(checked) => onUpdate({ animations: checked })}
          />
        </div>
        <div className="flex items-center justify-between">
          <label htmlFor="login-explosion" className="text-sm cursor-pointer">
            {t("login.settingsExplosion")}
          </label>
          <Switch
            id="login-explosion"
            checked={settings.explosion}
            disabled={!settings.animations}
            onCheckedChange={(checked) => onUpdate({ explosion: checked })}
          />
        </div>
        <div className="flex items-center justify-between">
          <label htmlFor="login-tokens" className="text-sm cursor-pointer">
            {t("login.settingsTokens")}
          </label>
          <Switch
            id="login-tokens"
            checked={settings.tokens}
            onCheckedChange={(checked) => onUpdate({ tokens: checked })}
          />
        </div>
        <Separator />
        <div className="flex items-center justify-between">
          <span className="text-sm">{t("login.settingsLanguage")}</span>
          <div className="flex gap-1">
            {LANGUAGES.map((lang) => (
              <button
                key={lang.code}
                type="button"
                onClick={() => i18n.changeLanguage(lang.code)}
                className={`px-2 py-0.5 rounded-sm text-xs font-medium transition-colors ${
                  i18n.language?.startsWith(lang.code)
                    ? "bg-orange-600 text-white"
                    : "bg-muted text-muted-foreground hover:bg-muted/80"
                }`}
              >
                {lang.label}
              </button>
            ))}
          </div>
        </div>
      </PopoverContent>
    </Popover>
  )
}
