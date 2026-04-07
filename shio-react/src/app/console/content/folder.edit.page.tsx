import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShFolderService } from "@/services/folder/folder.service"
import type { ShFolder } from "@/models/folder/folder"
import { toast } from "@openviglet/viglet-design-system"

const service = new ShFolderService()

export default function FolderEditPage() {
  const { folderId } = useParams() as { folderId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShFolder>()
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.editFolder") },
    ])
    service.get(folderId).then((data) => {
      form.reset(data)
      const items: { label: string; href?: string }[] = [
        { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      ]
      if (data.breadcrumb) {
        for (const crumb of data.breadcrumb) {
          items.push({
            label: crumb.name,
            href: `${ROUTES.CONTENT_ROOT}/object/${crumb.id}`,
          })
        }
      }
      items.push({ label: t("content.editFolder") })
      resetBreadcrumb(items)
    })
  }, [folderId, resetBreadcrumb, t, form])

  async function onSubmit(data: ShFolder) {
    setIsLoading(true)
    try {
      await service.update(data)
      toast.success(t("common.save"))
      navigate(-1)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHeader title={t("content.editFolder")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("content.editFolder")}</CardTitle>
          </CardHeader>
          <CardContent>
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                  control={form.control}
                  name="name"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.name")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.description")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="flex gap-2 pt-4">
                  <Button type="submit" disabled={isLoading}>
                    {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                    {t("common.save")}
                  </Button>
                  <Button type="button" variant="outline" onClick={() => navigate(-1)}>
                    {t("common.cancel")}
                  </Button>
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
