import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { IconShieldCog } from "@tabler/icons-react"
import { Loader2, Save } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShUserService } from "@/services/auth/user.service"
import type { ShUser } from "@/models/auth/user"
import { toast } from "@viglet/viglet-design-system"

const service = new ShUserService()

export default function AdminUserPage() {
  const { userId } = useParams()
  const isNew = userId === "new"
  const { t } = useTranslation()
  const navigate = useNavigate()
  const form = useForm<ShUser>()
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    if (!isNew && userId) {
      service.getByUsername(userId).then((data) => form.reset(data))
    }
  }, [userId, isNew, t, form])

  async function onSubmit(data: ShUser) {
    setIsLoading(true)
    try {
      if (isNew) {
        await service.create(data)
      } else {
        await service.update(userId!, data)
      }
      toast.success(t("common.save"))
      navigate(`${ROUTES.ADMIN_ROOT}/users`)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHero
        icon={IconShieldCog}
        title={isNew ? t("admin.newUser") : t("admin.editUser")}
        eyebrow={t("admin.users")}
        subtitle={t("admin.usersSubtitle")}
        urlBase={ROUTES.ADMIN_ROOT}
      />
      <div>
        <Panel contentClassName="p-6">
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                <FormField
                  control={form.control}
                  name="username"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.username")}</FormLabel>
                      <FormControl>
                        <Input {...field} disabled={!isNew} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="firstName"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.firstName")}</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="lastName"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.lastName")}</FormLabel>
                      <FormControl>
                        <Input {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="email"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.email")}</FormLabel>
                      <FormControl>
                        <Input type="email" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="password"
                  rules={isNew ? { required: t("common.required") } : undefined}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.password")}</FormLabel>
                      <FormControl>
                        <Input type="password" {...field} value={field.value ?? ""} />
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
        </Panel>
      </div>
    </>
  )
}
