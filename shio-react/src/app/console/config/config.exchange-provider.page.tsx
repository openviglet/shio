import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Switch } from "@/components/ui/switch"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconSettings } from "@tabler/icons-react"
import { Loader2, Save, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@openviglet/viglet-design-system"
import axios from "axios"

type ShExchangeProvider = {
  id: string
  name: string
  description: string
  vendor: string
  enabled: boolean
}

export default function ConfigExchangeProviderPage() {
  const { providerId } = useParams()
  const isNew = providerId === "new" || !providerId
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShExchangeProvider>({ defaultValues: { enabled: true } })
  const [isLoading, setIsLoading] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("config.title"), href: ROUTES.CONFIG_ROOT },
      { label: t("config.exchangeProviders"), href: ROUTES.CONFIG_EXCHANGE_PROVIDERS },
      { label: isNew ? t("config.newExchangeProvider") : t("config.editExchangeProvider") },
    ])
    if (!isNew && providerId) {
      axios.get<ShExchangeProvider>(`/api/v2/config/provider/exchange/${providerId}`).then((res) => form.reset(res.data))
    }
  }, [providerId, isNew, resetBreadcrumb, t, form])

  async function onSubmit(data: ShExchangeProvider) {
    setIsLoading(true)
    try {
      if (isNew) {
        await axios.post("/api/v2/config/provider/exchange", data)
      } else {
        await axios.put(`/api/v2/config/provider/exchange/${providerId}`, data)
      }
      toast.success(t("common.save"))
      navigate(ROUTES.CONFIG_EXCHANGE_PROVIDERS)
    } finally {
      setIsLoading(false)
    }
  }

  async function onDelete() {
    if (confirm(t("dialog.deleteMessage"))) {
      await axios.delete(`/api/v2/config/provider/exchange/${providerId}`)
      toast.success(t("common.deleted"))
      navigate(ROUTES.CONFIG_EXCHANGE_PROVIDERS)
    }
  }

  return (
    <>
      <PageHeader title={isNew ? t("config.newExchangeProvider") : t("config.editExchangeProvider")} turIcon={IconSettings} urlBase={ROUTES.CONFIG_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{isNew ? t("config.newExchangeProvider") : t("config.editExchangeProvider")}</CardTitle>
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
                        <Textarea {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="vendor"
                  rules={{ required: t("common.required") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("config.vendor")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="enabled"
                  render={({ field }) => (
                    <FormItem className="flex items-center gap-3">
                      <FormLabel>{t("config.enabled")}</FormLabel>
                      <FormControl>
                        <Switch checked={field.value} onCheckedChange={field.onChange} />
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
                  {!isNew && (
                    <Button type="button" variant="destructive" onClick={onDelete}>
                      <Trash2 className="mr-2 h-4 w-4" />
                      {t("common.delete")}
                    </Button>
                  )}
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
