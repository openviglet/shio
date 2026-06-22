import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { FileText, Image as ImageIcon, Download, Trash2, Upload, Loader2 } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShStaticFileService } from "@/services/static-file/static-file.service"
import type { ShStaticFileItem } from "@/models/static-file/static-file.model"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"

const service = new ShStaticFileService()
const siteService = new ShSiteService()
const postService = new ShPostUnifiedService()

function formatBytes(bytes: number): string {
  if (bytes < 0) return "—"
  if (bytes < 1024) return `${bytes} B`
  const units = ["KB", "MB", "GB"]
  let value = bytes / 1024
  let i = 0
  while (value >= 1024 && i < units.length - 1) {
    value /= 1024
    i++
  }
  return `${value.toFixed(1)} ${units[i]}`
}

const isImage = (ct: string | null) => !!ct && ct.startsWith("image/")

export default function StaticFileListPage() {
  const { t } = useTranslation()
  const { resetBreadcrumb } = useBreadcrumb()
  const [items, setItems] = useState<ShStaticFileItem[]>([])
  const [sites, setSites] = useState<ShSite[]>([])
  const [targetSite, setTargetSite] = useState<string>("")
  const [loading, setLoading] = useState(true)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const load = () => {
    setLoading(true)
    service.list().then(setItems).catch(() => setItems([])).finally(() => setLoading(false))
  }

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("staticFile.title") },
    ])
    load()
    siteService.query().then((s) => {
      setSites(s)
      if (s.length > 0) setTargetSite((prev) => prev || s[0].id)
    }).catch(() => setSites([]))
  }, [resetBreadcrumb, t])

  const handleUpload = async (file: File) => {
    if (!targetSite) {
      toast.error(t("staticFile.pickSiteFirst"))
      return
    }
    setUploading(true)
    try {
      await service.upload(file, targetSite)
      toast.success(t("content.fileUploaded", { name: file.name }))
      load()
    } catch {
      toast.error(t("common.error"))
    } finally {
      setUploading(false)
    }
  }

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) handleUpload(file)
    e.target.value = ""
  }

  const handleDelete = async (item: ShStaticFileItem) => {
    if (confirm(t("dialog.deleteMessage"))) {
      await postService.delete(item.id).catch(() => {})
      setItems((prev) => prev.filter((i) => i.id !== item.id))
      toast.success(t("content.deletedShort"))
    }
  }

  return (
    <>
      <PageHeader title={t("staticFile.title")} turIcon={FileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6">
        <div className="flex items-center justify-between mb-4 gap-3 flex-wrap">
          <div>
            <h2 className="text-lg font-semibold">{t("staticFile.title")}</h2>
            <p className="text-sm text-muted-foreground">{t("staticFile.description")}</p>
          </div>
          <div className="flex items-center gap-2">
            <Select value={targetSite} onValueChange={setTargetSite}>
              <SelectTrigger className="w-44">
                <SelectValue placeholder={t("staticFile.uploadTarget")} />
              </SelectTrigger>
              <SelectContent>
                {sites.map((site) => (
                  <SelectItem key={site.id} value={site.id}>{site.name}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button size="sm" disabled={uploading || !targetSite} onClick={() => fileInputRef.current?.click()}>
              {uploading ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Upload className="mr-2 h-4 w-4" />}
              {t("staticFile.upload")}
            </Button>
            <input ref={fileInputRef} type="file" className="hidden"
              aria-label={t("staticFile.upload")} onChange={handleFileInput} />
          </div>
        </div>

        {loading ? (
          <div className="text-center py-12 text-muted-foreground">{t("common.loading")}</div>
        ) : items.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <FileText className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("staticFile.blankTitle")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("staticFile.blankDescription")}</p>
          </div>
        ) : (
          <Card>
            <CardContent className="p-0">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-14"></TableHead>
                    <TableHead>{t("staticFile.fileName")}</TableHead>
                    <TableHead>{t("staticFile.type")}</TableHead>
                    <TableHead>{t("staticFile.size")}</TableHead>
                    <TableHead>{t("content.siteLabel")}</TableHead>
                    <TableHead>{t("common.owner")}</TableHead>
                    <TableHead className="w-24">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        {isImage(item.contentType) ? (
                          <img src={service.fileUrl(item)} alt={item.fileName ?? ""}
                            className="h-9 w-9 rounded object-cover border" loading="lazy" />
                        ) : (
                          <div className="h-9 w-9 rounded border flex items-center justify-center bg-muted">
                            <FileText className="h-4 w-4 text-muted-foreground" />
                          </div>
                        )}
                      </TableCell>
                      <TableCell className="font-medium">
                        <span className="flex items-center gap-1.5">
                          {isImage(item.contentType) && <ImageIcon className="h-3.5 w-3.5 text-muted-foreground shrink-0" />}
                          <span className="truncate max-w-xs">{item.fileName ?? item.id}</span>
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-mono text-xs">{item.contentType ?? "—"}</Badge>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{formatBytes(item.size)}</TableCell>
                      <TableCell className="text-muted-foreground">{item.siteName ?? "—"}</TableCell>
                      <TableCell className="text-muted-foreground">{item.owner ?? "—"}</TableCell>
                      <TableCell>
                        <div className="flex gap-1">
                          <a href={service.fileUrl(item)} download={item.fileName ?? undefined}
                            target="_blank" rel="noopener noreferrer">
                            <Button variant="ghost" size="icon" className="h-8 w-8">
                              <Download className="h-4 w-4" />
                            </Button>
                          </a>
                          <Button variant="ghost" size="icon" className="h-8 w-8 text-destructive"
                            onClick={() => handleDelete(item)}>
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
