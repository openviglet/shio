import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconShieldCog } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShGroupService, type ShGroup } from "@/services/auth/group.service"
import { toast } from "@openviglet/viglet-design-system"

const service = new ShGroupService()

export default function AdminGroupPage() {
  const { groupId } = useParams()
  const isNew = groupId === "new"
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShGroup>()
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("admin.groups"), href: `${ROUTES.ADMIN_ROOT}/groups` },
      { label: isNew ? t("admin.newGroup") : t("admin.editGroup") },
    ])
    if (!isNew && groupId) {
      service.get(groupId).then((data) => form.reset(data))
    }
  }, [groupId, isNew, resetBreadcrumb, t, form])

  async function onSubmit(data: ShGroup) {
    setIsLoading(true)
    try {
      if (isNew) {
        await service.create(data)
      } else {
        await service.update(data)
      }
      toast.success(t("common.save"))
      navigate(`${ROUTES.ADMIN_ROOT}/groups`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHeader title={isNew ? t("admin.newGroup") : t("admin.editGroup")} turIcon={IconShieldCog} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{isNew ? t("admin.newGroup") : t("admin.editGroup")}</CardTitle>
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
                        <Input {...field} />
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
