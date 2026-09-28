import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { ROUTES } from "@/app/routes.const"
import { AlertTriangle, Check, Columns2, Languages, Plus } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import type { ShLocale } from "@/services/locale/locale.service"
import { useInvalidate, useLocales, useSiteLocalePolicy } from "@/hooks/use-catalogue"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { Button } from "@/components/ui/button"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShPostUnified } from "@/models/post-unified/post-unified"
import { reportProblem } from "@/lib/problem"
import { offeredLocales } from "@/lib/site-languages"

const unifiedService = new ShPostUnifiedService()

/**
 * SH53 — language switcher / translation creator for the post editor. Lists the
 * sibling translations of the current post (switch to one) and offers the
 * remaining locales to spin up a new linked translation.
 *
 * SH1056 — "the remaining locales" are the site's, once it has declared them: forty-five seeded
 * tags on every post is an offer to write pages the site will never link to. An undeclared site
 * still offers the whole catalogue, and says so, because narrowing to the languages its content is
 * already in would stop it ever gaining a second one.
 */
export function PostTranslationsMenu({ postId, siteId, currentLocale }: {
  postId: string
  siteId?: string
  currentLocale?: string
}) {
  const { t, i18n } = useTranslation()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)

  /*
   * SH1152 — four reads, three of them now shared or cancelled and none of them in an effect.
   *
   * The locale catalogue and the site's policy are the same two answers `site.edit.page.tsx` asks
   * for, and this menu is mounted from the post editor — so the instance's locale list was fetched
   * once per surface that wanted it. They are shared hooks now. The translations themselves are this
   * post's, keyed on its id, and the refresh after a write is an invalidation rather than a second
   * call site.
   */
  const read = useQuery<ShPostUnified[]>({
    queryKey: addressKey(`translation:${postId}`),
    queryFn: ({ signal }) => unifiedService.translations(postId, { signal }),
    enabled: Boolean(postId),
  })
  const translations = read.data ?? []
  const locales = useLocales().data ?? []
  const policy = useSiteLocalePolicy(siteId).data ?? null
  const invalidate = useInvalidate()

  const existing = new Set(translations.map((tr) => tr.locale).filter(Boolean) as string[])
  // SH1058 — the page being edited is a member of the group the menu already read, so knowing
  // whether it has fallen behind its source costs no second call.
  const staleHere = translations.some((tr) => tr.id === postId && tr.translationStale)
  const localeLabel = (l: ShLocale) => (i18n.language === "pt" ? l.pt : l.en) || l.initials

  const switchTo = (tr: ShPostUnified) => {
    if (tr.id !== postId) navigate(`${ROUTES.CONSOLE}/content/post/${tr.id}`)
  }

  const addTranslation = async (initials: string) => {
    setBusy(true)
    try {
      const created = await unifiedService.translate(postId, initials)
      /*
       * SH1152 — the whole scheme, not this post's key. Creating a translation changes the group as
       * seen from *every* member of it, and the curator navigates to the new page and may come back.
       * Without this, the source post's menu would still be showing the group as it was before the
       * translation it just created — a cache the reads did not have and so a defect the reads did
       * not have either.
       */
      await invalidate("translation:")
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
        <Button type="button" size="sm" variant="outline" disabled={busy}
          title={staleHere ? t("content.translationStaleHint") : undefined}>
          <Languages className="size-4" />
          {currentLocale ? currentLocale.toUpperCase() : t("content.translations")}
          {/* SH1058 — the trigger carries it because a curator who has not opened the menu is
              exactly the one who does not know the source moved under this page. */}
          {staleHere && (
            <span className="ml-1 inline-flex items-center gap-1 text-amber-600 dark:text-amber-400">
              <AlertTriangle className="size-3.5" />
              <span className="text-xs">{t("content.translationStale")}</span>
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>{t("content.translations")}</DropdownMenuLabel>
        {translations.map((tr) => (
          <DropdownMenuItem key={tr.id} onClick={() => switchTo(tr)}>
            {tr.id === postId ? <Check className="mr-2 h-4 w-4" /> : <span className="mr-2 w-4" />}
            <span className="font-mono text-xs uppercase">{tr.locale}</span>
            <span className="ml-2 truncate text-muted-foreground">{tr.title}</span>
            {/* SH1058 — which of the languages needs a pass, which is the question somebody opens
                this menu to answer once a page has more than two. */}
            {tr.translationStale && (
              <AlertTriangle className="ml-auto size-3.5 shrink-0 text-amber-600 dark:text-amber-400"
                aria-label={t("content.translationStale")} />
            )}
          </DropdownMenuItem>
        ))}
        {/* SH1061 — the way into the workspace, offered only on a page that HAS a source to sit
            beside: on the source itself there is nothing to put in the left pane. */}
        {translations.some((tr) => tr.id === postId && tr.translationGroupId !== postId) && (
          <>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate(`${ROUTES.CONSOLE}/content/post/${postId}/translate`)}>
              <Columns2 className="mr-2 h-4 w-4" />
              {t("content.translateOpen")}
            </DropdownMenuItem>
          </>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuLabel className="text-muted-foreground">{t("content.addTranslation")}</DropdownMenuLabel>
        {!policy?.declared && (
          <p className="px-2 pb-1 text-xs text-muted-foreground">{t("content.translationsUndeclared")}</p>
        )}
        {offeredLocales(locales, policy)
          .filter((l) => !existing.has(l.initials))
          .map((l) => (
            <DropdownMenuItem key={l.initials} disabled={busy} onClick={() => addTranslation(l.initials)}>
              <Plus className="mr-2 h-4 w-4" />
              <span className="font-mono text-xs uppercase">{l.initials}</span>
              <span className="ml-2 truncate text-muted-foreground">{localeLabel(l)}</span>
              {policy?.declared && policy.defaultLocale === l.initials && (
                <span className="ml-auto text-xs text-muted-foreground">{t("content.defaultLocale")}</span>
              )}
            </DropdownMenuItem>
          ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
