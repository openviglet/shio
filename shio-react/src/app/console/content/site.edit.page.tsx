import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { IconFileText } from "@tabler/icons-react"
import { Download, Loader2, Save, Trash2 } from "lucide-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { toast } from "@viglet/viglet-design-system"

const service = new ShSiteService()
const postTypeService = new ShPostTypeService()

/** Tolerantly parse the site's searchablePostTypes (JSON array, or comma/semicolon/newline list). */
function parseSearchableTypes(raw: string | null | undefined): string[] {
  if (!raw || !raw.trim()) return []
  const trimmed = raw.trim()
  if (trimmed.startsWith("[")) {
    try {
      const arr = JSON.parse(trimmed)
      if (Array.isArray(arr)) return arr.map((s) => String(s).trim()).filter(Boolean)
    } catch { /* fall through to delimiter split */ }
  }
  return trimmed.split(/[,;\n]/).map((s) => s.trim()).filter(Boolean)
}

export default function SiteEditPage() {
  const { siteId } = useParams() as { siteId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const form = useForm<ShSite>()
  const [isLoading, setIsLoading] = useState(false)
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])
  const [searchableTypes, setSearchableTypes] = useState<string[]>([])

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("content.editSite") },
    ])
    service.get(siteId).then((data) => {
      form.reset(data)
      setSearchableTypes(parseSearchableTypes(data.searchablePostTypes))
    })
  }, [siteId, resetBreadcrumb, t, form])

  useEffect(() => {
    postTypeService.query().then(setPostTypes).catch(() => setPostTypes([]))
  }, [])

  const toggleSearchableType = (name: string, checked: boolean) => {
    setSearchableTypes((prev) => checked ? [...prev, name] : prev.filter((n) => n !== name))
  }

  async function onSubmit(data: ShSite) {
    setIsLoading(true)
    try {
      // Empty selection = search all post-types (the backend treats null/empty as "all").
      await service.update({ ...data, searchablePostTypes: searchableTypes.length ? JSON.stringify(searchableTypes) : "" })
      toast.success(t("common.save"))
      navigate(ROUTES.CONTENT_ROOT)
    } finally {
      setIsLoading(false)
    }
  }

  const handleDelete = async () => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(siteId)
      toast.success(t("common.delete"))
      navigate(ROUTES.CONTENT_ROOT)
    }
  }

  const handleExport = async () => {
    const blob = await service.export(siteId)
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `site-${siteId}.zip`
    a.click()
    URL.revokeObjectURL(url)
  }

  return (
    <>
      <PageHeader title={t("content.editSite")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Card>
          <CardHeader>
            <CardTitle>{t("content.editSite")}</CardTitle>
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
                <FormField
                  control={form.control}
                  name="url"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("content.url")}</FormLabel>
                      <FormControl>
                        <Input {...field} value={field.value ?? ""} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <div className="space-y-3 pt-2">
                  <div>
                    <Label>{t("content.searchableTypes")}</Label>
                    <p className="text-xs text-muted-foreground mt-1">{t("content.searchableTypesHint")}</p>
                  </div>
                  <div className="rounded-md border p-3 space-y-2">
                    {postTypes.length === 0 ? (
                      <p className="text-xs text-muted-foreground">{t("content.searchableTypesNone")}</p>
                    ) : (
                      postTypes
                        .slice()
                        .sort((a, b) => a.title.localeCompare(b.title))
                        .map((pt) => (
                          <div key={pt.id} className="flex items-center gap-2">
                            <Checkbox id={`st-${pt.id}`}
                              checked={searchableTypes.includes(pt.name)}
                              onCheckedChange={(checked) => toggleSearchableType(pt.name, checked === true)} />
                            <Label htmlFor={`st-${pt.id}`} className="font-normal cursor-pointer">
                              {pt.title} <span className="text-muted-foreground font-mono text-xs">({pt.name})</span>
                            </Label>
                          </div>
                        ))
                    )}
                    {searchableTypes.length === 0 && postTypes.length > 0 && (
                      <p className="text-xs text-muted-foreground pt-1 border-t mt-2">{t("content.searchableTypesAll")}</p>
                    )}
                  </div>
                </div>

                <div className="flex gap-2 pt-4">
                  <Button type="submit" disabled={isLoading}>
                    {isLoading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
                    {t("common.save")}
                  </Button>
                  <Button type="button" variant="outline" onClick={handleExport}>
                    <Download className="mr-2 h-4 w-4" />
                    {t("content.export")}
                  </Button>
                  <Button type="button" variant="destructive" onClick={handleDelete}>
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t("common.delete")}
                  </Button>
                  <Button type="button" variant="outline" onClick={() => navigate(ROUTES.CONTENT_ROOT)}>
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
