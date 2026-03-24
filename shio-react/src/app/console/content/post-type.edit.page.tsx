import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Separator } from "@/components/ui/separator"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { toast } from "sonner"

const service = new ShPostTypeService()

export default function PostTypeEditPage() {
  const { postTypeId } = useParams() as { postTypeId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShPostType>()
  const [isLoading, setIsLoading] = useState(false)
  const [postType, setPostType] = useState<ShPostType | null>(null)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.postTypes"), href: ROUTES.POST_TYPE_ROOT },
      { label: t("content.editPostType") },
    ])
    service.get(postTypeId).then((data) => {
      setPostType(data)
      form.reset(data)
    })
  }, [postTypeId, resetBreadcrumb, t, form])

  async function onSubmit(data: ShPostType) {
    setIsLoading(true)
    try {
      await service.update(data)
      toast.success(t("common.save"))
      navigate(ROUTES.POST_TYPE_ROOT)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHeader title={t("content.editPostType")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("content.editPostType")}</CardTitle>
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
                  name="title"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("content.title")}</FormLabel>
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
                  <Button type="button" variant="outline" onClick={() => navigate(ROUTES.POST_TYPE_ROOT)}>
                    {t("common.cancel")}
                  </Button>
                </div>
              </form>
            </Form>
          </CardContent>
        </Card>

        {postType?.shPostTypeAttrs && postType.shPostTypeAttrs.length > 0 && (
          <Card>
            <CardHeader>
              <CardTitle>{t("content.attributes")}</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <Separator />
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("common.name")}</TableHead>
                    <TableHead>{t("content.label")}</TableHead>
                    <TableHead>{t("content.widget")}</TableHead>
                    <TableHead>{t("content.ordinal")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {postType.shPostTypeAttrs
                    .sort((a, b) => a.ordinal - b.ordinal)
                    .map((attr) => (
                      <TableRow key={attr.id}>
                        <TableCell className="font-medium">{attr.name}</TableCell>
                        <TableCell>{attr.label}</TableCell>
                        <TableCell>{attr.shWidget?.name ?? ""}</TableCell>
                        <TableCell>{attr.ordinal}</TableCell>
                      </TableRow>
                    ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
