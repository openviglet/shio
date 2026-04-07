import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"
import { IconFileText } from "@tabler/icons-react"
import { Download, Eye, Globe, Pencil, Plus, Search, Trash2, Upload, Network } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { formatDistanceToNow } from "date-fns"
import { toast } from "@openviglet/viglet-design-system"

const siteService = new ShSiteService()

export default function SiteListPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [sites, setSites] = useState<ShSite[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState("")

  useEffect(() => {
    resetBreadcrumb([{ label: t("sidebar.dashboard") }])
    siteService.query().then(data => {
      setSites(data)
      setLoading(false)
    })
  }, [resetBreadcrumb, t])

  const filteredSites = sites.filter(s =>
    s.name.toLowerCase().includes(filter.toLowerCase())
  )

  const handleExport = async (e: React.MouseEvent, site: ShSite) => {
    e.stopPropagation()
    const blob = await siteService.export(site.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement("a")
    a.href = url
    a.download = `${site.name}.zip`
    a.click()
    URL.revokeObjectURL(url)
  }

  const handleDelete = async (e: React.MouseEvent, site: ShSite) => {
    e.stopPropagation()
    if (confirm(`${t("dialog.deleteMessage")} (${site.name})`)) {
      await siteService.delete(site.id)
      setSites(prev => prev.filter(s => s.id !== site.id))
      toast.success(`${site.name} deleted`)
    }
  }

  return (
    <>
      <PageHeader title={t("sidebar.dashboard")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <Tabs defaultValue="sites">
          <TabsList>
            <TabsTrigger value="sites">{t("content.sites")}</TabsTrigger>
            <TabsTrigger value="tasks">Tasks</TabsTrigger>
          </TabsList>

          <TabsContent value="sites">
            <div className="flex items-center justify-between mb-4 mt-2">
              <h2 className="text-xl font-semibold">Select your site</h2>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={() => navigate(ROUTES.SITE_IMPORT)}>
                  <Upload className="mr-2 h-4 w-4" />{t("content.import")}
                </Button>
                <Button size="sm" onClick={() => navigate(ROUTES.SITE_NEW)}>
                  <Plus className="mr-2 h-4 w-4" />New
                </Button>
              </div>
            </div>

            <div className="relative mb-4">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Find a site..."
                value={filter}
                onChange={e => setFilter(e.target.value)}
                className="pl-10"
              />
            </div>

            {loading ? (
              <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
            ) : filteredSites.length === 0 ? (
              <Card>
                <CardContent className="flex flex-col items-center justify-center py-16 text-muted-foreground">
                  <Globe className="h-12 w-12 mb-4 opacity-30" />
                  <p className="text-lg font-medium mb-1">{t("common.noResults")}</p>
                  <p className="text-sm">Create your first site to get started</p>
                </CardContent>
              </Card>
            ) : (
              <Card>
                <CardContent className="p-0">
                  <div className="divide-y">
                    {filteredSites.map(site => (
                      <div
                        key={site.id}
                        className="group flex items-start gap-4 px-4 py-4 hover:bg-muted/50 cursor-pointer transition-colors"
                        onClick={() => navigate(`${ROUTES.CONSOLE}/content/object/${site.id}`)}
                      >
                        <div className="shrink-0 mt-1">
                          <Network className="h-10 w-10 text-orange-500" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-blue-600 dark:text-blue-400 hover:underline">{site.name}</span>
                          </div>
                          <p className="text-sm text-muted-foreground mt-0.5">
                            {site.date && formatDistanceToNow(new Date(site.date), { addSuffix: true })}
                            {site.description && ` - ${site.description}`}
                          </p>
                          <Badge variant="secondary" className="mt-1.5 text-[10px] px-1.5 py-0 bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400">
                            Site
                          </Badge>
                        </div>
                        <div className="shrink-0 flex flex-wrap gap-x-4 gap-y-1 text-xs opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={e => handleExport(e, site)}
                            className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
                          >
                            <Download className="h-3.5 w-3.5" /> Download
                          </button>
                          <button
                            onClick={e => { e.stopPropagation(); navigate(`${ROUTES.CONSOLE}/content/site/${site.id}`) }}
                            className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
                          >
                            <Pencil className="h-3.5 w-3.5" /> Edit
                          </button>
                          <button
                            onClick={e => { e.stopPropagation(); navigate(`${ROUTES.PREVIEW}/${site.id}`) }}
                            className="flex items-center gap-1 text-muted-foreground hover:text-foreground transition-colors"
                          >
                            <Eye className="h-3.5 w-3.5" /> View Site
                          </button>
                          <button
                            onClick={e => handleDelete(e, site)}
                            className="flex items-center gap-1 text-destructive/70 hover:text-destructive transition-colors"
                          >
                            <Trash2 className="h-3.5 w-3.5" /> Delete
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            )}
          </TabsContent>

          <TabsContent value="tasks">
            <div className="text-center py-12 text-muted-foreground">
              <p>No pending tasks</p>
            </div>
          </TabsContent>
        </Tabs>
      </div>
    </>
  )
}
