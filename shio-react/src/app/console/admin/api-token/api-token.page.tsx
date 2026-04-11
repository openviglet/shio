import { PageHeader } from "@/components/page-header"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { KeyRound, Save, ArrowLeft, Copy, Check, Trash2, Info } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useParams } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { ShApiTokenService } from "@/services/token/api-token.service"
import type { ShApiToken } from "@/models/token/api-token.model"

const service = new ShApiTokenService()

export default function ApiTokenPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { tokenId } = useParams() as { tokenId: string }
  const { resetBreadcrumb } = useBreadcrumb()
  const isNew = tokenId === "new"

  const [item, setItem] = useState<ShApiToken>({
    id: "", title: "", description: "", token: "", owner: "", createdDate: "", enabled: true,
  })
  const [copied, setCopied] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("admin.title"), href: ROUTES.ADMIN_ROOT },
      { label: t("apiToken.title"), href: ROUTES.ADMIN_TOKENS },
      { label: isNew ? t("common.create") : item.title || tokenId },
    ])
    if (!isNew) {
      service.get(tokenId).then(setItem)
    }
  }, [resetBreadcrumb, t, tokenId, isNew, item.title])

  const handleSave = async () => {
    if (!item.title.trim()) {
      toast.error(t("apiToken.titleRequired"))
      return
    }
    setSaving(true)
    try {
      if (isNew) {
        const created = await service.create(item)
        toast.success(t("apiToken.created"))
        navigate(`${ROUTES.ADMIN_TOKENS}/${created.id}`, { replace: true })
      } else {
        await service.update(item)
        toast.success(t("apiToken.updated"))
      }
    } catch {
      toast.error(t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const handleDelete = async () => {
    if (confirm(t("dialog.deleteMessage"))) {
      await service.delete(item.id)
      navigate(ROUTES.ADMIN_TOKENS)
    }
  }

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(item.token)
      setCopied(true)
      toast.success(t("apiToken.copied"))
      setTimeout(() => setCopied(false), 2000)
    } catch {
      toast.error(t("apiToken.copyFailed"))
    }
  }

  return (
    <>
      <PageHeader title={t("apiToken.title")} turIcon={KeyRound} urlBase={ROUTES.ADMIN_ROOT} />
      <div className="p-6 max-w-2xl">
        {/* Sticky save bar */}
        <div className="sticky top-0 z-10 flex items-center justify-between bg-orange-50 dark:bg-orange-950/30 rounded-lg px-4 py-3 mb-6 border">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="sm" onClick={() => navigate(ROUTES.ADMIN_TOKENS)}>
              <ArrowLeft className="h-4 w-4" />
            </Button>
            <span className="font-semibold">{isNew ? t("apiToken.newInstance") : item.title}</span>
            <Badge variant="outline" className="text-xs">CDA</Badge>
          </div>
          <div className="flex items-center gap-2">
            {!isNew && (
              <Button variant="ghost" size="sm" className="text-destructive" onClick={handleDelete}>
                <Trash2 className="h-4 w-4 mr-1" />
                {t("common.delete")}
              </Button>
            )}
            <Button size="sm" onClick={handleSave} disabled={saving}>
              <Save className="h-4 w-4 mr-1" />
              {saving ? t("common.loading") : t("common.save")}
            </Button>
          </div>
        </div>

        {/* General section */}
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">{t("apiToken.general")}</CardTitle>
            <CardDescription>{t("apiToken.generalDesc")}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="title">{t("common.title")}</Label>
              <Input id="title" value={item.title}
                onChange={(e) => setItem({ ...item, title: e.target.value })}
                placeholder={t("apiToken.titlePlaceholder")} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">{t("common.description")}</Label>
              <Textarea id="description" value={item.description || ""}
                onChange={(e) => setItem({ ...item, description: e.target.value })}
                placeholder={t("apiToken.descriptionPlaceholder")} rows={3} />
            </div>
            {!isNew && (
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Label htmlFor="enabled">{t("common.enabled")}</Label>
                  <Tooltip>
                    <TooltipTrigger><Info className="h-3.5 w-3.5 text-muted-foreground" /></TooltipTrigger>
                    <TooltipContent>{t("apiToken.enabledHint")}</TooltipContent>
                  </Tooltip>
                </div>
                <Switch id="enabled" checked={item.enabled}
                  onCheckedChange={(checked) => setItem({ ...item, enabled: checked })} />
              </div>
            )}
          </CardContent>
        </Card>

        {/* Token section (only on edit) */}
        {!isNew && item.token && (
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t("apiToken.tokenLabel")}</CardTitle>
              <CardDescription>{t("apiToken.tokenDesc")}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex items-center gap-2">
                <Input value={item.token} readOnly className="font-mono text-sm bg-muted" />
                <Button variant="outline" size="icon" onClick={handleCopy}>
                  {copied ? <Check className="h-4 w-4 text-green-500" /> : <Copy className="h-4 w-4" />}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">{t("apiToken.tokenHint")}</p>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  )
}
