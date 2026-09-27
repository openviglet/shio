import { Button } from "@/components/ui/button"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Plus, Trash2, ShieldCheck } from "lucide-react"
import { useQuery } from "@tanstack/react-query"
import { addressKey } from "@/lib/query-keys"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { ShAclService } from "@/services/acl/acl.service"
import type { ShAclPermission, ShAclPrincipalType, ShContentAcl } from "@/models/acl/acl.model"
import { reportProblem } from "@/lib/problem"

const service = new ShAclService()

const PRINCIPAL_TYPES: ShAclPrincipalType[] = ["USER", "GROUP", "ROLE"]
// SH995 — three levels, in the order they widen: a grant of PUBLISH carries WRITE with it.
const PERMISSIONS: ShAclPermission[] = ["READ", "WRITE", "PUBLISH"]

/**
 * SH54 — edit the content ACL of a folder/post. An empty list means "open"
 * (default-allow); adding any grant restricts access to the listed principals.
 */
export function PermissionsDialog({
  objectId,
  objectName,
  open,
  setOpen,
}: {
  objectId: string | null
  objectName?: string
  open: boolean
  setOpen: (v: boolean) => void
}) {
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="max-w-2xl">
        {objectId && (
          <PermissionsBody key={objectId} objectId={objectId} objectName={objectName} setOpen={setOpen} />
        )}
      </DialogContent>
    </Dialog>
  )
}

/**
 * SH702 — mounted inside `DialogContent`, so closing unmounts it and there is no grant list left
 * over to render against the next object.
 */
function PermissionsBody({
  objectId,
  objectName,
  setOpen,
}: {
  objectId: string
  objectName?: string
  setOpen: (v: boolean) => void
}) {
  const { t } = useTranslation()
  const [saving, setSaving] = useState(false)

  /*
   * SH1153 — one read per object, cancelled when it changes or the dialog unmounts.
   *
   * `catch(() => setEntries([]))` made "the server did not answer" and "this object has no rules of
   * its own" the same screen, and on a permissions dialog those are opposite facts: the second says
   * the object inherits, which is what a curator would act on.
   */
  const read = useQuery<ShContentAcl[]>({
    queryKey: addressKey(`summary:acl|${objectId}`),
    queryFn: ({ signal }) => service.get(objectId, { signal }),
    enabled: Boolean(objectId),
  })
  const loading = read.isPending

  /*
   * The rows are edited before they are saved, so they are local state seeded from the server rather
   * than a derived value — and seeded during render, once per object (`react-hooks/set-state-in-effect`).
   */
  const [entries, setEntries] = useState<ShContentAcl[]>([])
  const [seeded, setSeeded] = useState<string | null>(null)
  if (read.isSuccess && seeded !== objectId) {
    setSeeded(objectId)
    setEntries(read.data)
  }

  const addRow = () =>
    setEntries((prev) => [...prev, { principalType: "USER", principalName: "", permission: "READ" }])

  const updateRow = (i: number, patch: Partial<ShContentAcl>) =>
    setEntries((prev) => prev.map((e, idx) => (idx === i ? { ...e, ...patch } : e)))

  const removeRow = (i: number) => setEntries((prev) => prev.filter((_, idx) => idx !== i))

  const save = async () => {
    setSaving(true)
    try {
      const clean = entries.filter((e) => e.principalName.trim())
      await service.replace(objectId, clean)
      toast.success(t("acl.saved"))
      setOpen(false)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-5 w-5" /> {t("acl.title")}
          </DialogTitle>
          <DialogDescription>
            {objectName ? t("acl.descriptionFor", { name: objectName }) : t("acl.description")}
          </DialogDescription>
        </DialogHeader>

        {loading ? (
          <p className="text-sm text-muted-foreground py-6">{t("common.loading")}</p>
        ) : (
          <div className="space-y-3">
            {entries.length === 0 && (
              <p className="text-sm text-muted-foreground rounded-md border border-dashed p-4 text-center">
                {t("acl.openHint")}
              </p>
            )}
            {entries.map((e, i) => (
              <div key={i} className="flex items-center gap-2">
                <Select value={e.principalType} onValueChange={(v) => updateRow(i, { principalType: v as ShAclPrincipalType })}>
                  <SelectTrigger className="w-32" aria-label={t("acl.principalType")}><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {PRINCIPAL_TYPES.map((p) => (
                      <SelectItem key={p} value={p}>{t(`acl.principal.${p}`)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Input
                  className="flex-1"
                  value={e.principalName}
                  placeholder={t("acl.principalNamePlaceholder")}
                  onChange={(ev) => updateRow(i, { principalName: ev.target.value })}
                />
                <Select value={e.permission} onValueChange={(v) => updateRow(i, { permission: v as ShAclPermission })}>
                  <SelectTrigger className="w-28" aria-label={t("acl.permission")}><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {PERMISSIONS.map((p) => (
                      <SelectItem key={p} value={p}>{t(`acl.permission.${p}`)}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button variant="ghost" size="icon" aria-label={t("common.delete")} className="h-9 w-9 text-destructive" onClick={() => removeRow(i)}>
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            ))}
            <Button variant="outline" size="sm" onClick={addRow}>
              <Plus className="mr-2 h-4 w-4" /> {t("acl.addGrant")}
            </Button>
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="ghost" onClick={() => setOpen(false)}>{t("common.cancel")}</Button>
          <Button onClick={save} disabled={saving}>{t("common.save")}</Button>
        </div>
    </>
  )
}
