import { PageHeader } from "@/components/page-header"
import { IconHome } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

export default function HomePage() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeader title={t("sidebar.dashboard")} turIcon={IconHome} />
      <div className="p-6">
        <h2 className="text-2xl font-bold">{t("sidebar.dashboard")}</h2>
        <p className="text-muted-foreground mt-2">Welcome to Viglet Shio CMS</p>
      </div>
    </>
  )
}
