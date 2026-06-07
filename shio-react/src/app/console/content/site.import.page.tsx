import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Upload } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import { toast } from "@viglet/viglet-design-system"

const service = new ShSiteService()

export default function SiteImportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [file, setFile] = useState<File | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.importSite") },
    ])
  }, [resetBreadcrumb, t])

  async function onImport() {
    if (!file) return
    setIsLoading(true)
    try {
      const site = await service.import(file)
      toast.success(t("content.importSite"))
      navigate(`${ROUTES.CONSOLE}/content/object/${site.id}`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHeader title={t("content.importSite")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("content.importSite")}</CardTitle>
            <CardDescription>{t("content.import")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="import-file">{t("content.selectFile")}</Label>
              <Input
                id="import-file"
                type="file"
                accept=".zip,application/zip"
                onChange={e => setFile(e.target.files?.[0] ?? null)}
              />
            </div>
            <div className="flex gap-2 pt-2">
              <Button onClick={onImport} disabled={!file || isLoading}>
                {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
                {t("content.uploadFile")}
              </Button>
              <Button type="button" variant="outline" onClick={() => navigate(ROUTES.CONTENT_ROOT)}>
                {t("common.cancel")}
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
