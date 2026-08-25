import { IconError404 } from "@tabler/icons-react"
import { useSectionChrome } from "@viglet/viglet-design-system/bento"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { ROUTES } from "@/app/routes.const"

/**
 * SH180 — the page an unmatched path renders.
 *
 * Without it the SPA renders a normal-looking, error-free empty page for any path that
 * matches no route, which is what made SH179's three dead **View Site** buttons invisible in
 * the product: they navigated somewhere, the page had no error and no failed request, and
 * nothing said the path was wrong. Worse for the work around it — a blank page and a working
 * page are the same observation, so a green probe on any route proved less than it appeared to.
 *
 * It names the path it could not match, because "404" alone does not tell whoever hit it
 * whether the link was wrong or the route was never registered.
 *
 * ## SH908 — the one page mounted in three places, and why its header is conditional
 *
 * §LV.11's argument for migrating it is that it is "the surface most likely to be seen in the
 * wrong chrome by somebody who did not choose to be there": during a cutover, a stale bookmark,
 * an old link in an email or a redirect that has not landed yet is what puts a user here. So the
 * bento tree gets its own catch-all, and this page renders in whatever chrome the path implies —
 * a stale `/bento/…` link lands in the bento shell and is offered the bento home, a `/console/…`
 * one keeps the sidebar it was already in.
 *
 * **The header is rendered only in bento, and that is not a style choice.** `PageHero`'s
 * console branch is `PageHeader`, which calls `SidebarTrigger` → `useSidebar()`, which *throws*
 * outside a `SidebarProvider`. This page is also mounted at App.tsx's bare `*` route — outside
 * every shell, for a path that is not under `/console` or `/bento` at all — so a console header
 * here does not look wrong, it blanks the application behind the error boundary at exactly the
 * moment somebody has already followed a broken link. That hazard is why this page rendered no
 * chrome at all before this task, which nothing recorded; `outside-shell-chrome.test.mjs` records
 * it now, for every component mounted outside the console shell rather than for this one.
 */
export default function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  const bento = useSectionChrome() === "bento"

  const home = bento ? ROUTES.BENTO : ROUTES.HOME
  const path = (
    <code className="text-xs bg-muted rounded px-2 py-1 break-all">{location.pathname}</code>
  )
  const back = <Button onClick={() => navigate(home)}>{t("common.backToHome")}</Button>

  /*
   * Two bodies rather than one, because the hero already says in bento what the empty state has
   * to say in console. Composing both would name the page twice forty pixels apart, which is the
   * duplication Block AR has now hit five times (SH898, SH899, SH906) — the console header has no
   * subtitle slot and the bento hero has both, so the same copy has nowhere else to go.
   */
  if (bento) {
    return (
      <>
        <PageHero
          icon={IconError404}
          eyebrow="404"
          title={t("common.notFoundTitle")}
          subtitle={t("common.notFoundDescription")}
        />
        <div className="p-6">
          <Panel contentClassName="flex flex-col items-center gap-4 p-8 text-center">
            {path}
            {back}
          </Panel>
        </div>
      </>
    )
  }

  return (
    <div className="py-8">
      <h1 className="text-center text-2xl font-semibold">404</h1>
      <EmptyState
        icon={IconError404}
        title={t("common.notFoundTitle")}
        description={
          <>
            {t("common.notFoundDescription")}
            <span className="mt-3 block">{path}</span>
          </>
        }
        action={back}
      />
    </div>
  )
}
