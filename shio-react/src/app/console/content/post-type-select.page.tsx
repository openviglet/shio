import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { ShPostTypeService } from "@/services/post-type/post-type.service"
import type { ShPostType } from "@/models/post-type/post-type"
import { IconFileText } from "@tabler/icons-react"
import { FolderPlus, Pencil, Star } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import axios from "axios"

const postTypeService = new ShPostTypeService()

export default function PostTypeSelectPage() {
  const { folderId } = useParams() as { folderId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [postTypes, setPostTypes] = useState<ShPostType[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Build breadcrumb from folder path
    axios.get(`/v2/object/${folderId}/path`).then((res) => {
      const items: { label: string; href?: string }[] = [
        { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
      ]
      if (Array.isArray(res.data)) {
        for (const crumb of res.data) {
          items.push({
            label: crumb.name,
            href: `${ROUTES.CONTENT_ROOT}/object/${crumb.id}`,
          })
        }
      }
      resetBreadcrumb(items)
    }).catch(() => {
      resetBreadcrumb([{ label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` }])
    })

    postTypeService.query().then((data) => {
      setPostTypes(data)
      setLoading(false)
    })
  }, [folderId, resetBreadcrumb, t])

  const systemTypes = postTypes.filter(pt => pt.system)
  const customTypes = postTypes.filter(pt => !pt.system)

  const handleSelect = (pt: ShPostType) => {
    localStorage.setItem("sh-last-post-type", pt.name)
    navigate(`${ROUTES.CONTENT_ROOT}/folder/${folderId}/post-type/${encodeURIComponent(pt.name)}/new`)
  }

  return (
    <>
      <PageHeader title="Select Post Type" turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-6 flex-wrap gap-3">
          <h2 className="text-xl font-semibold">What would you like to post?</h2>
          <div className="flex gap-2 items-center flex-wrap">
            <Button
              variant="outline"
              size="sm"
              className="text-orange-600 border-orange-300 hover:bg-orange-50 dark:text-orange-400 dark:border-orange-800 dark:hover:bg-orange-950"
              onClick={() => navigate(`${ROUTES.CONTENT_ROOT}/folder/${folderId}/new-folder`)}
            >
              <FolderPlus className="mr-2 h-4 w-4" /> Or create a new Folder
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => navigate(ROUTES.POST_TYPE_NEW)}
            >
              <Pencil className="mr-2 h-4 w-4" /> Or model a new Post Type
            </Button>
          </div>
        </div>

        {loading ? (
          <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
        ) : (
          <div className="space-y-6">
            {/* System post types */}
            {systemTypes.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6 gap-3">
                {systemTypes.map(pt => (
                  <Card
                    key={pt.name}
                    className="cursor-pointer hover:bg-muted/50 hover:shadow-md transition-all"
                    onClick={() => handleSelect(pt)}
                  >
                    <CardContent className="p-4">
                      <span className="text-sm font-medium">{pt.title || pt.name}</span>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}

            {/* Custom post types */}
            {customTypes.length > 0 && (
              <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
                {customTypes.map(pt => (
                  <Card
                    key={pt.name}
                    className="cursor-pointer hover:bg-muted/50 hover:shadow-md transition-all"
                    onClick={() => handleSelect(pt)}
                  >
                    <CardContent className="p-4">
                      <div className="flex items-start justify-between">
                        <div>
                          <span className="text-base font-medium">{pt.title || pt.name}</span>
                          {pt.description && (
                            <p className="text-xs text-muted-foreground mt-1">{pt.description}</p>
                          )}
                        </div>
                        <div className="flex items-center gap-1 text-muted-foreground/40">
                          <Pencil className="h-3.5 w-3.5" />
                          <Star className="h-3.5 w-3.5" />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </>
  )
}
