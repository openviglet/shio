import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"
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
 * ## One body, in the shell and outside it (SH959)
 *
 * It is mounted twice: as the bento tree's catch-all, for a stale `/bento/…` link, and at
 * App.tsx's bare `*`, for a path under no shell at all. It used to branch on the chrome it found —
 * a hero and a panel under the provider, a plain empty state under none — and SH958's provider
 * above the router means there is no "none" left, so it renders the hero everywhere and always
 * offers the bento home.
 */
export default function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <>
      <PageHero
        eyebrow="404"
        title={t("common.notFoundTitle")}
        subtitle={t("common.notFoundDescription")}
      />
      {/* viglet-ds-allow-page-column -- also the bare `*` route outside BentoShell (SH947), where nothing pads it */}
      <div className="p-6">
        <Panel contentClassName="flex flex-col items-center gap-4 p-8 text-center">
          <code className="text-xs bg-muted rounded-sm px-2 py-1 break-all">{location.pathname}</code>
          <Button onClick={() => navigate(ROUTES.BENTO)}>{t("common.backToHome")}</Button>
        </Panel>
      </div>
    </>
  )
}
