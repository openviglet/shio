import { ROUTES } from "@/app/routes.const"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { safeReturnUrl } from "@/lib/return-url"
import { cn } from "@/lib/utils"
import { ShAuthorizationService } from "@/services/auth/authorization.service"
import { ShSetupService } from "@/services/auth/setup.service"
import { AlertCircle, KeyRound, LogIn, User } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useSearchParams } from "react-router-dom"

interface LoginData { username: string; password: string }

export function LoginForm({ className, ...props }: React.ComponentProps<"form">) {
  const [searchParams] = useSearchParams()
  // After login the tenant isn't known yet in the URL; land on "/" so
  // TenantGateway can resolve the tenant and redirect to /{tenant}/console.
  const returnUrl = safeReturnUrl(searchParams.get("returnUrl"), ROUTES.ROOT)
  const { t } = useTranslation()
  const form = useForm<LoginData>()
  const [error, setError] = useState("")
  const [isLoading, setIsLoading] = useState(false)
  const authorization = new ShAuthorizationService()

  useEffect(() => {
    // SH1140 — both mount reads take one signal, aborted on unmount. Each answer redirects the page,
    // so one landing after the curator has already left the login screen would pull them back to
    // setup or to the identity provider from wherever they went. SH1047's guard missed this effect
    // twice over: one read is built in the call, the other is held under a name that is not *service.
    const abort = new AbortController()
    // Check if first-access setup is needed
    new ShSetupService().status({ signal: abort.signal }).then((status) => {
      if (status.required) {
        globalThis.location.href = ROUTES.SETUP;
        return;
      }
    }).catch(() => { /* ignore, a cancellation included */ });

    authorization.discovery({ signal: abort.signal }).then((discovery) => {
      if (discovery.keycloak) {
        globalThis.location.href = "/oauth2/authorization/keycloak";
      }
    }).catch(() => { /* ignore — login page is still usable, and a cancellation is not a failure */ });
    return () => abort.abort()
  }, []);

  function onSubmit(data: LoginData) {
    setError(""); setIsLoading(true)
    try {
      if (!data.username || !data.password) throw new Error(t("login.emptyFields"))
      authorization.login(data.username, data.password).then(() => {
        globalThis.location.href = returnUrl
      }).catch(() => { setError(t("login.invalidCredentials")); setIsLoading(false) })
    } catch (err) {
      setIsLoading(false)
      setError(err instanceof Error ? err.message || t("login.loginFailed") : t("login.loginFailed"))
    }
  }

  return (
    <Card className="border-0 shadow-none bg-transparent">
      <CardHeader className="text-center pb-2 px-0">
        <CardTitle className="text-xl font-bold tracking-tight">{t("login.welcomeBack")}</CardTitle>
        <CardDescription>{t("login.signInDescription")}</CardDescription>
      </CardHeader>
      <CardContent className="px-0">
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className={cn("flex flex-col gap-5", className)} {...props}>
            {error && (
              <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0" /><span>{error}</span>
              </div>
            )}
            <div className="grid gap-4">
              <FormField control={form.control} name="username" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("login.username")}</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input {...field} placeholder={t("login.enterUsername")} className="pl-9" disabled={isLoading} />
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )} />
              <FormField control={form.control} name="password" render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("login.password")}</FormLabel>
                  <FormControl>
                    <div className="relative">
                      <KeyRound className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input {...field} placeholder={t("login.enterPassword")} type="password" className="pl-9" disabled={isLoading} />
                    </div>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )} />
            </div>
            <Button type="submit" className="w-full bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 text-white font-semibold" loading={isLoading}>
              {!isLoading && <LogIn className="mr-2 h-4 w-4" />}
              {isLoading ? t("login.signingIn") : t("login.signIn")}
            </Button>
          </form>
        </Form>
      </CardContent>
    </Card>
  )
}
