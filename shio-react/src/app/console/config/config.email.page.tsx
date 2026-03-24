import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Separator } from "@/components/ui/separator"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconSettings } from "@tabler/icons-react"
import { Save } from "lucide-react"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"

type EmailConfig = {
  smtpHost: string
  port: string
  username: string
  password: string
  fromAddress: string
}

export default function ConfigEmailPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<EmailConfig>()

  useEffect(() => {
    resetBreadcrumb([
      { label: t("config.title"), href: ROUTES.CONFIG_ROOT },
      { label: t("config.email") },
    ])
  }, [resetBreadcrumb, t])

  return (
    <>
      <PageHeader title={t("config.email")} turIcon={IconSettings} urlBase={ROUTES.CONFIG_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("config.email")}</CardTitle>
            <CardDescription>
              {t("config.emailDescription")}
            </CardDescription>
          </CardHeader>
          <Separator />
          <CardContent className="pt-6">
            <div className="rounded-md border border-amber-200 bg-amber-50 p-4 mb-6 dark:border-amber-900 dark:bg-amber-950">
              <p className="text-sm text-amber-800 dark:text-amber-200">
                {t("config.emailNote")}
              </p>
            </div>
            <Form {...form}>
              <form className="space-y-4">
                <FormField
                  control={form.control}
                  name="smtpHost"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("config.smtpHost")}</FormLabel>
                      <FormControl>
                        <Input placeholder="smtp.example.com" {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="port"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("config.port")}</FormLabel>
                      <FormControl>
                        <Input placeholder="587" {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="username"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("common.username")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="password"
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
                <FormField
                  control={form.control}
                  name="fromAddress"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("config.fromAddress")}</FormLabel>
                      <FormControl>
                        <Input type="email" placeholder="noreply@example.com" {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="flex gap-2 pt-4">
                  <Button type="button" disabled>
                    <Save className="mr-2 h-4 w-4" />
                    {t("common.save")}
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
