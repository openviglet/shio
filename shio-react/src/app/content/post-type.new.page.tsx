import { PageHero } from "@/components/page-hero"
import { AdaptiveSectionCard } from "@viglet/viglet-design-system/bento"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { IconFileText } from "@tabler/icons-react"
import { Save } from "lucide-react"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { toast } from "@viglet/viglet-design-system"
import { reportProblem } from "@/lib/problem"

const service = new ShPostTypeService()

export default function PostTypeNewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const form = useForm<ShPostType>()
  const [isLoading, setIsLoading] = useState(false)

  async function onSubmit(data: ShPostType) {
    setIsLoading(true)
    try {
      await service.create(data)
      toast.success(t("common.save"))
      navigate(ROUTES.POST_TYPE_ROOT)
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
        icon={IconFileText}
        eyebrow={t("content.postType")}
        title={t("content.newPostType")}
        urlBase={ROUTES.CONTENT_ROOT}
      />
      <div>
        {/* SH902 — the hero above says "New Post Type", so this section says what it holds. */}
        <AdaptiveSectionCard>
          <AdaptiveSectionCard.StaticHeader icon={IconFileText} title={t("content.postType")} />
          <AdaptiveSectionCard.Content>
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
                  <Button type="submit" loading={isLoading}>
                    {!isLoading && <Save className="mr-2 h-4 w-4" />}
                    {t("common.save")}
                  </Button>
                  <Button type="button" variant="outline" onClick={() => navigate(ROUTES.POST_TYPE_ROOT)}>
                    {t("common.cancel")}
                  </Button>
                </div>
              </form>
            </Form>
          </AdaptiveSectionCard.Content>
        </AdaptiveSectionCard>
      </div>
    </>
  )
}
