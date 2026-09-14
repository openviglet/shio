import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ROUTES } from "@/app/routes.const"
import { Check, Languages, Plus } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShLocaleService, type ShLocale } from "@/services/locale/locale.service"
import type { ShPostUnified } from "@/models/post-unified/post-unified"
import { reportProblem } from "@/lib/problem"

const unifiedService = new ShPostUnifiedService()
const localeService = new ShLocaleService()

/**
 * SH53 — language switcher / translation creator for the post editor. Lists the
 * sibling translations of the current post (switch to one) and offers the
 * remaining locales to spin up a new linked translation.
 */
export function PostTranslationsMenu({ postId, currentLocale }: { postId: string; currentLocale?: string }) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [translations, setTranslations] = useState<ShPostUnified[]>([])
  const [locales, setLocales] = useState<ShLocale[]>([])
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    unifiedService.translations(postId).then(setTranslations).catch(() => setTranslations([]))
  }, [postId])

  useEffect(() => {
    load()
    localeService.list().then(setLocales).catch(() => setLocales([]))
  }, [load])

  const existing = new Set(translations.map((tr) => tr.locale).filter(Boolean) as string[])
  const localeLabel = (l: ShLocale) => (i18n.language === "pt" ? l.pt : l.en) || l.initials

  const switchTo = (tr: ShPostUnified) => {
    if (tr.id !== postId) navigate(`${ROUTES.CONSOLE}/content/post/${tr.id}`)
  }

  const addTranslation = async (initials: string) => {
    setBusy(true)
    try {
      const created = await unifiedService.translate(postId, initials)
      toast.success(t("content.translationCreated"))
      navigate(`${ROUTES.CONSOLE}/content/post/${created.id}`)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(false)
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <GradientButton type="button" size="sm" variant="outline" loading={busy}>
          <Languages className="size-4" />
          {currentLocale ? currentLocale.toUpperCase() : t("content.translations")}
        </GradientButton>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>{t("content.translations")}</DropdownMenuLabel>
        {translations.map((tr) => (
          <DropdownMenuItem key={tr.id} onClick={() => switchTo(tr)}>
            {tr.id === postId ? <Check className="mr-2 h-4 w-4" /> : <span className="mr-2 w-4" />}
            <span className="font-mono text-xs uppercase">{tr.locale}</span>
            <span className="ml-2 truncate text-muted-foreground">{tr.title}</span>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-muted-foreground">{t("content.addTranslation")}</DropdownMenuLabel>
        {locales
          .filter((l) => !existing.has(l.initials))
          .map((l) => (
            <DropdownMenuItem key={l.initials} disabled={busy} onClick={() => addTranslation(l.initials)}>
              <Plus className="mr-2 h-4 w-4" />
              <span className="font-mono text-xs uppercase">{l.initials}</span>
              <span className="ml-2 truncate text-muted-foreground">{localeLabel(l)}</span>
            </DropdownMenuItem>
          ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
