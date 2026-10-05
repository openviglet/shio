import { IconMail, IconTrash } from "@tabler/icons-react"
import { Save } from "lucide-react"
import {
  BentoDataTable, BentoIdentityCell, type BentoDataTableAction,
} from "@viglet/viglet-design-system/bento"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { ROUTES } from "@/app/routes.const"
import { useSites } from "@/hooks/use-catalogue"
import { useListState } from "@/hooks/use-list-state"
import { isCancelled } from "@/lib/read-options"
import { reportProblem } from "@/lib/problem"
import { ShSearchService, type ShConsoleSearchRow } from "@/services/search/search.service"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import { ShSiteFormService, type ShSiteFormInput } from "@/services/site-form/site-form.service"

const searchService = new ShSearchService()
const postService = new ShPostUnifiedService()
const formService = new ShSiteFormService()

/** SH1023 — the site this page is about lives in the URL, by name, like every other list's. */
const FORMS_LIST = { site: "" }

const EMPTY_SETTINGS: ShSiteFormInput = {
  siteId: "", folderUrl: "", allowedTypes: "", allowedOrigins: "", enabled: false,
}

/**
 * SH1066 — a site's form submissions, and where its form sends them.
 *
 * <p>A submission is a post (SH633), so storage, trash, ACL and the agent surface already applied to
 * it; attention did not. The only way to find one was the folder a developer configured, where it
 * looked like any other draft. This page is that folder's inbox: the rows are `submission=true` over
 * the shared query vocabulary (the same question `shio_find` asks), and the settings are the site's
 * row on `/api/v2/site-form`, which had no screen at all.</p>
 *
 * <p>Trash is the one row action. A post has no archive state, and SH970 said so rather than
 * inventing one: the trash is where a submission goes when it is dealt with, and where it can be
 * brought back from.</p>
 */
export default function FormsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const sites = useSites().data ?? []
  const list = useListState(FORMS_LIST)
  const siteName = list.value.site || sites[0]?.name || ""
  const site = sites.find((each) => each.name === siteName)

  const [rows, setRows] = useState<ShConsoleSearchRow[]>([])
  const [loading, setLoading] = useState(true)
  const [formId, setFormId] = useState<string | null>(null)
  const [settings, setSettings] = useState<ShSiteFormInput>(EMPTY_SETTINGS)
  const [saving, setSaving] = useState(false)

  const load = useCallback((signal?: AbortSignal) => {
    if (!site) return Promise.resolve()
    return Promise.all([
      searchService.search({ site: site.name, submission: "true" }, 100, { signal }),
      formService.ofSite(site.id, { signal }),
    ])
      .then(([found, form]) => {
        setRows(found.results)
        setFormId(form?.id ?? null)
        setSettings(form
          ? { siteId: form.siteId, folderUrl: form.folderUrl ?? "", allowedTypes: form.allowedTypes ?? "",
            allowedOrigins: form.allowedOrigins ?? "", enabled: form.enabled }
          : { ...EMPTY_SETTINGS, siteId: site.id })
        setLoading(false)
      })
      .catch((reason: unknown) => {
        if (isCancelled(reason)) return
        setRows([])
        setLoading(false)
      })
  }, [site])

  useEffect(() => {
    const abort = new AbortController()
    void load(abort.signal)
    return () => abort.abort()
  }, [load])

  /** One configuration per site: created the first time, updated after (SH632 refuses a second). */
  const saveSettings = async () => {
    setSaving(true)
    try {
      const saved = formId
        ? await formService.update(formId, settings)
        : await formService.create(settings)
      setFormId(saved.id)
      toast.success(t("content.formsSaved"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setSaving(false)
    }
  }

  const trashRow = async (row: ShConsoleSearchRow) => {
    if (!row.id) return
    try {
      await postService.delete(row.id)
      setRows((current) => current.filter((each) => each.id !== row.id))
      toast.success(t("dock.trashed"))
    } catch (error) {
      reportProblem(error, t("common.error"))
    }
  }

  const trashAction: BentoDataTableAction<ShConsoleSearchRow> = {
    id: "trash", label: t("content.trash"), icon: IconTrash, tone: "destructive",
    onSelect: (chosen) => { for (const row of chosen) void trashRow(row) },
  }

  /** One setting, spelled as the server stores it: a path, or a comma-separated list. */
  const field = (key: "folderUrl" | "allowedTypes" | "allowedOrigins", label: string, hint: string) => (
    <div className="space-y-1">
      <Label htmlFor={`form-${key}`}>{label}</Label>
      <Input id={`form-${key}`} value={settings[key] ?? ""}
        onChange={(event) => setSettings((current) => ({ ...current, [key]: event.target.value }))} />
      <p className="text-xs text-muted-foreground">{hint}</p>
    </div>
  )

  return (
    <>
      <PageHero
        title={t("content.formsTitle")}
        subtitle={t("content.formsDescription")}
        urlBase={ROUTES.HOME}
        backLabel={t("home.title")}
      />
      <div className="space-y-4">
        <Select value={siteName} onValueChange={(value) => list.set("site", value)}>
          <SelectTrigger className="sm:w-64" aria-label={t("content.filterSite")}>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {sites.map((each) => <SelectItem key={each.id} value={each.name}>{each.name}</SelectItem>)}
          </SelectContent>
        </Select>

        {/* The destination, which had no screen: an operator configured it by hand or not at all. */}
        <Panel>
          <h2 className="mb-3 text-sm font-semibold">{t("content.formsSettings")}</h2>
          <div className="grid gap-4 md:grid-cols-3">
            {field("folderUrl", t("content.formsFolder"), t("content.formsFolderHint"))}
            {field("allowedTypes", t("content.formsTypes"), t("content.formsTypesHint"))}
            {field("allowedOrigins", t("content.formsOrigins"), t("content.formsOriginsHint"))}
          </div>
          <div className="mt-4 flex items-center justify-between gap-4">
            <label className="flex items-center gap-2 text-sm">
              <Switch checked={settings.enabled}
                onCheckedChange={(enabled) => setSettings((current) => ({ ...current, enabled }))} />
              {t("content.formsEnabled")}
            </label>
            <GradientButton type="button" size="sm" onClick={saveSettings} loading={saving}
              disabled={!site}>
              <Save className="size-4" />
              {t("common.save")}
            </GradientButton>
          </div>
        </Panel>

        {!loading && rows.length === 0 ? (
          <EmptyState
            icon={IconMail}
            title={t("content.formsEmptyTitle")}
            description={t("content.formsEmptyHint")}
          />
        ) : (
          /*
           * SH1230 — submissions on the design system's table: they grow without bound, and the
           * package's table virtualizes and selects where the hand-drawn one did neither. Trash is
           * still the one verb, on a row or on a selection. No arrival column: the rows are the
           * agent's own find rows (SH1027), which carry no date, for SH1228's token reason.
           */
          <BentoDataTable
            rows={rows}
            getRowId={(row) => row.address}
            getRowLabel={(row) => row.title || row.address}
            columns={[
              {
                id: "title", header: t("common.title"), width: "minmax(18rem, 3fr)", hideable: false,
                sortValue: (row) => (row.title || row.address).toLowerCase(),
                cell: (row) => <BentoIdentityCell name={row.title || t("content.formsUntitled")} detail={row.address} />,
              },
              {
                id: "type", header: t("content.postType"), width: "minmax(9rem, 1fr)",
                sortValue: (row) => (row.type ?? "").toLowerCase(),
                cell: (row) => <Badge variant="outline">{row.type}</Badge>,
              },
            ]}
            label={t("content.formsTitle")}
            rowActions={[trashAction]}
            selectionActions={[trashAction]}
            onRowOpen={(row) => { if (row.id) navigate(`${ROUTES.CONTENT_ROOT}/post/${row.id}`) }}
          />
        )}
      </div>
    </>
  )
}
