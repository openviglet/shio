import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { Save } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { isCancelled } from "@/lib/read-options"
import { addressKey } from "@/lib/query-keys"
import { useEffect, useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShUserService } from "@/services/auth/user.service"
import type { ShUser } from "@/models/auth/user"
import { toast } from "@viglet/viglet-design-system"
import { reportProblem } from "@/lib/problem"

const service = new ShUserService()

/** SH1168 — one empty string per rendered field, so no input is ever uncontrolled. */
const EMPTY_USER: ShUser = { username: "", firstName: "", lastName: "", email: "", password: "" }

export default function AdminUserPage() {
  const { userId } = useParams()
  const isNew = userId === "new"
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [isLoading, setIsLoading] = useState(false)

  /*
   * SH1116 — one read per user, cancelled on leaving, and the form reset moved out of the effect.
   *
   * `form.reset` was in the dependency list, which is a `useForm` identity rather than a subject: it
   * is stable in practice and says nothing about which user is wanted. The key is the username.
   */
  const loaded = useQuery<ShUser>({
    queryKey: addressKey(`user:${userId ?? ""}`),
    queryFn: ({ signal }) => service.getByUsername(userId ?? "", { signal }),
    enabled: !isNew && Boolean(userId),
  })

  /*
   * SH1166 — seeded through `values`, which react-hook-form resets from in its own effect and only
   * when the user differs. A `form.reset` during render updates every field's Controller mid-render.
   */
  // SH1168 — every input starts as "" and stays a string: without defaults each rendered `undefined`
  // until the user arrived, and the password the server never returns stayed `undefined` after it.
  const values = useMemo(() => loaded.data && { ...EMPTY_USER, ...loaded.data,
    password: loaded.data.password ?? "" }, [loaded.data])
  const form = useForm<ShUser>({ defaultValues: EMPTY_USER, values,
    resetOptions: { keepDirtyValues: true } })

  useEffect(() => {
    // The read had no failure path: a user that would not load left the blank create form on screen.
    if (loaded.error && !isCancelled(loaded.error)) reportProblem(loaded.error, t("common.error"))
  }, [loaded.error, t])

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
    } catch (error) {
      // SH1139 — a create that toasts success and lets a refusal escape as an unhandled rejection
      // looks exactly like a button that did nothing. The dock reads toasts, so the curator, the
      // mascot and a screen reader all missed it.
      reportProblem(error, t("common.error"))
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <>
      <PageHero
        title={isNew ? t("admin.newUser") : t("admin.editUser")}
        eyebrow={t("admin.users")}
        subtitle={t("admin.usersSubtitle")}
        urlBase={ROUTES.ADMIN_USERS}
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
                      {/* SH1335 — an edit sets a new password only when one is typed; blank keeps it. */}
                      {!isNew && <p className="text-muted-foreground text-xs">{t("admin.passwordKeepHint")}</p>}
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="flex gap-2 pt-4">
                  <Button type="submit" loading={isLoading}>
                    {!isLoading && <Save className="mr-2 h-4 w-4" />}
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
