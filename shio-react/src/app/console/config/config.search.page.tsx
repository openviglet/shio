import { PageHeader } from "@/components/page-header"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconSettings } from "@tabler/icons-react"
import { useEffect } from "react"
import { useTranslation } from "react-i18next"

export default function ConfigSearchPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()

  useEffect(() => {
    resetBreadcrumb([
      { label: t("config.title"), href: ROUTES.CONFIG_ROOT },
      { label: t("config.search") },
    ])
  }, [resetBreadcrumb, t])

  return (
    <>
      <PageHeader title={t("config.search")} turIcon={IconSettings} urlBase={ROUTES.CONFIG_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("config.search")}</CardTitle>
            <CardDescription>
              {t("config.searchDescription")}
            </CardDescription>
          </CardHeader>
          <Separator />
          <CardContent className="pt-6">
            <p className="text-muted-foreground">
              {t("config.searchPlaceholder")}
            </p>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
