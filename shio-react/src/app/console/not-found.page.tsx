import { Button } from "@/components/ui/button"
import { ROUTES } from "@/app/routes.const"
import { FileQuestion } from "lucide-react"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"

/**
 * SH180 — the page an unmatched console path renders.
 *
 * Without it the SPA renders a normal-looking, error-free empty page for any path that
 * matches no route, which is what made SH179's three dead **View Site** buttons invisible in
 * the product: they navigated somewhere, the page had no error and no failed request, and
 * nothing said the path was wrong. Worse for the work around it — a blank page and a working
 * page are the same observation, so a green probe on any route proved less than it appeared to.
 *
 * It names the path it could not match, because "404" alone does not tell whoever hit it
 * whether the link was wrong or the route was never registered.
 */
export default function NotFoundPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <div className="rounded-full bg-muted p-4 mb-4">
        <FileQuestion className="h-8 w-8 text-muted-foreground" />
      </div>
      <h1 className="text-2xl font-semibold mb-1">404</h1>
      <h2 className="font-semibold text-lg mb-2">{t("common.notFoundTitle")}</h2>
      <p className="text-sm text-muted-foreground max-w-md mb-2">{t("common.notFoundDescription")}</p>
      <code className="text-xs bg-muted rounded px-2 py-1 mb-6 break-all">{location.pathname}</code>
      <Button onClick={() => navigate(ROUTES.HOME)}>{t("common.backToHome")}</Button>
    </div>
  )
}
