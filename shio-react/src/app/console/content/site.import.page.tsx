import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Upload } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import { toast } from "@openviglet/viglet-design-system"

const service = new ShSiteService()

export default function SiteImportPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [isLoading, setIsLoading] = useState(false)
  const [file, setFile] = useState<File | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.importSite") },
    ])
  }, [resetBreadcrumb, t])

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!file) return
    setIsLoading(true)
    try {
      await service.import(file)
      toast.success(t("content.import"))
      navigate(ROUTES.CONTENT_ROOT)
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
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label>{t("content.selectFile")}</Label>
                <Input
                  ref={fileInputRef}
                  type="file"
                  accept=".zip"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
              </div>
              <div className="flex gap-2 pt-4">
                <Button type="submit" disabled={isLoading || !file}>
                  {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
                  {t("content.uploadFile")}
                </Button>
                <Button type="button" variant="outline" onClick={() => navigate(ROUTES.CONTENT_ROOT)}>
                  {t("common.cancel")}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
