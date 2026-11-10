import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ShMarketplaceService } from "@/services/marketplace/marketplace.service"
import { addressKey } from "@/lib/query-keys"
import { useQueryClient } from "@tanstack/react-query"
import { MarketplaceNextStep } from "./marketplace.next-step"
import type {
  ShMarketplaceItem,
  ShMarketplaceSetupResult,
} from "@/models/marketplace/marketplace.model"
import { LayoutTemplate } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new ShMarketplaceService()

type Values = Record<string, string | boolean>

/**
 * SH1251 — turn a package into a site from the dialog, the way Turing's import ends on something
 * that works.
 *
 * <p>The order is P7's: the curator fills the package's parameters and names a site, the plan runs
 * first and shows both halves (the install, when the package is not here yet, and what the apply
 * would create), and only a plan for exactly these inputs can be confirmed. Changing any input
 * throws the plan away, so the confirm never writes something the curator did not see.</p>
 *
 * <p>Both calls go to `POST /api/v2/marketplace/{id}/setup`, which installs and applies as two
 * operations: an apply that refuses leaves the package installed, and the refusal says why.</p>
 */
export function MarketplaceSetup({
  item,
  onDone,
}: {
  item: ShMarketplaceItem
  onDone: () => void
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const properties = item.params?.properties ?? {}
  const required = new Set(item.params?.required ?? [])

  const [site, setSite] = useState("")
  const [createSite, setCreateSite] = useState(true)
  const [mergePostTypes, setMergePostTypes] = useState(item.requires.includes("mergePostTypes"))
  const [values, setValues] = useState<Values>(() => initialValues(item))
  const [plan, setPlan] = useState<ShMarketplaceSetupResult | null>(null)
  const [done, setDone] = useState<ShMarketplaceSetupResult | null>(null)
  const [busy, setBusy] = useState(false)
  // SH1385 — a refused plan is said here, in the dialog, and not only in a toast the modal can hide:
  // the Marketplace's first run failed with "Install and apply" quietly disabled and nothing else.
  const [failure, setFailure] = useState<string | null>(null)
  // SH1387 — off unless ticked: a package's forms ship closed, and opening them is the admin's act.
  const [openForms, setOpenForms] = useState(false)

  /** Any change invalidates the plan: a confirm must write what was planned, and nothing else. */
  const change = (next: () => void) => {
    next()
    setPlan(null)
    setDone(null)
    setFailure(null)
  }

  const request = (dryRun: boolean) => ({
    site: site.trim(),
    params: coerce(values, properties),
    createSite,
    mergePostTypes,
    dryRun,
    // Only a package whose plan proposed forms can be asked to open them.
    openForms: openForms && Boolean(plan?.forms),
  })

  const run = (dryRun: boolean) => {
    setBusy(true)
    setFailure(null)
    service
      .setup(item.id, request(dryRun))
      .then((result) => {
        if (dryRun) {
          setPlan(result)
          return
        }
        setPlan(null)
        setDone(result)
        toast.success(t("marketplace.setupDone", { site: result.apply.site }))
        // SH1422 — the next step finds the new site in the catalogue, which predates it.
        void queryClient.invalidateQueries({ queryKey: addressKey("site:") })
        onDone()
      })
      .catch((error) => {
        const reason = problemOf(error, t("marketplace.setupFailed"))
        setFailure(reason)
        toast.error(reason)
      })
      .finally(() => setBusy(false))
  }

  const names = Object.keys(properties)

  // SH1397 — a package this instance cannot read is refused before its download; say so here,
  // where the install would have been offered, rather than after a plan that cannot succeed.
  if (item.unreadable?.length) {
    return (
      <p role="status" className="rounded-md border p-3 text-sm">
        {t("marketplace.needsNewerShio", { keys: item.unreadable.join(", ") })}
      </p>
    )
  }

  return (
    <div className="flex flex-col gap-3 rounded-md border p-3">
      <p className="flex items-center gap-2 text-sm font-medium">
        <LayoutTemplate className="size-4" />
        {t("marketplace.setupTitle")}
      </p>

      <div className="grid gap-1.5">
        <Label htmlFor="marketplace-setup-site">{t("marketplace.setupSite")}</Label>
        <Input
          id="marketplace-setup-site"
          value={site}
          placeholder={t("marketplace.setupSitePlaceholder")}
          onChange={(event) => change(() => setSite(event.target.value))}
        />
      </div>

      {names.map((name) => {
        const param = properties[name]
        const id = `marketplace-setup-${name}`
        const label = `${name}${required.has(name) ? " *" : ""}`
        if (param?.type === "boolean") {
          return (
            <div key={name} className="flex items-center gap-2">
              <Checkbox
                id={id}
                checked={Boolean(values[name])}
                onCheckedChange={(checked) =>
                  change(() => setValues((all) => ({ ...all, [name]: checked === true })))
                }
              />
              <Label htmlFor={id}>{label}</Label>
            </div>
          )
        }
        return (
          <div key={name} className="grid gap-1.5">
            <Label htmlFor={id}>{label}</Label>
            <Input
              id={id}
              type={param?.type === "integer" || param?.type === "number" ? "number" : "text"}
              value={String(values[name] ?? "")}
              onChange={(event) =>
                change(() => setValues((all) => ({ ...all, [name]: event.target.value })))
              }
            />
            {param?.description && (
              <p className="text-xs text-muted-foreground">{param.description}</p>
            )}
          </div>
        )
      })}

      <div className="flex flex-wrap gap-4">
        <div className="flex items-center gap-2">
          <Checkbox
            id="marketplace-setup-create"
            checked={createSite}
            onCheckedChange={(checked) => change(() => setCreateSite(checked === true))}
          />
          <Label htmlFor="marketplace-setup-create">{t("marketplace.setupCreateSite")}</Label>
        </div>
        <div className="flex items-center gap-2">
          <Checkbox
            id="marketplace-setup-merge"
            checked={mergePostTypes}
            onCheckedChange={(checked) => change(() => setMergePostTypes(checked === true))}
          />
          <Label htmlFor="marketplace-setup-merge">{t("marketplace.setupMerge")}</Label>
        </div>
      </div>

      {failure && (
        <p role="alert" className="rounded-md border border-destructive/50 p-3 text-sm text-destructive">
          {failure}
        </p>
      )}

      {plan && <PlanSummary result={plan} />}

      {/* SH1387 — the package proposes where its forms send submissions; this is the admin's yes. */}
      {plan?.forms && (
        <div className="flex flex-col gap-1 rounded-md border p-3 text-sm">
          <div className="flex items-center gap-2">
            <Checkbox
              id="marketplace-setup-forms"
              checked={openForms}
              onCheckedChange={(checked) => setOpenForms(checked === true)}
            />
            <Label htmlFor="marketplace-setup-forms">{t("marketplace.formsOffer")}</Label>
          </div>
          <p className="text-muted-foreground">
            {t("marketplace.formsPlan", {
              folder: plan.forms.folder,
              types: plan.forms.types.join(", "),
              origins: plan.forms.origins.join(", "),
            })}{" "}
            {plan.forms.sections != null && t("marketplace.formsSections", { count: plan.forms.sections })}
          </p>
          {plan.forms.configuration === "kept" && (
            <p className="text-muted-foreground">{t("marketplace.formsKept")}</p>
          )}
        </div>
      )}

      {/* SH1422 — the site it made, and the three ways into it, rather than the list of every site. */}
      {done && <MarketplaceNextStep siteName={done.apply.site} />}
      {done?.forms?.opened && (
        <p className="text-sm">
          {t("marketplace.formsOpened", { count: done.forms.sections ?? 0, folder: done.forms.folder })}
          {!done.forms.enabled && <> {t("marketplace.formsOpenedDisabled")}</>}
        </p>
      )}

      <div className="flex flex-wrap justify-end gap-2">
        <Button variant="outline" disabled={busy || !site.trim()} onClick={() => run(true)}>
          {t("marketplace.setupPlan")}
        </Button>
        {/* Enabled by a plan for these exact inputs, and by nothing else (P7). */}
        <Button disabled={busy || !plan} onClick={() => run(false)}>
          {item.installed || item.builtIn
            ? t("marketplace.setupApply")
            : t("marketplace.setupInstallApply")}
        </Button>
      </div>
    </div>
  )
}

/** Both halves of the plan, in the order they would happen. */
function PlanSummary({ result }: { result: ShMarketplaceSetupResult }) {
  const { t } = useTranslation()
  const content = result.apply.content
  const rows = (content?.results ?? []).filter((row) => row.action !== "noop")
  return (
    <div className="rounded-md bg-muted p-3 text-sm">
      <p className="font-medium">{t("marketplace.setupPlanTitle")}</p>
      <ul className="mt-1 flex flex-col gap-0.5 text-muted-foreground">
        {result.install && (
          <li>
            {t("marketplace.wouldInstall", {
              version: result.install.version ?? "-",
              origin: result.install.origin,
            })}
          </li>
        )}
        {result.apply.createdSite && (
          <li>{t("marketplace.setupWouldCreateSite", { site: result.apply.createdSite })}</li>
        )}
        {(result.apply.postTypes ?? []).map((postType) => (
          <li key={postType.name}>
            {t("marketplace.setupPostType", { action: postType.action, name: postType.name })}
          </li>
        ))}
        <li>
          {t("marketplace.setupContent", { changed: content?.changed ?? 0, ops: content?.ops ?? 0 })}
        </li>
        {(result.apply.assets ?? [])
          .filter((asset) => asset.action !== "noop")
          .map((asset) => (
            <li key={asset.path}>
              {t("marketplace.setupAsset", { action: asset.action, path: asset.path })}
            </li>
          ))}
        {rows.slice(0, 12).map((row) => (
          <li key={`${row.action} ${row.address}`} className="pl-3 font-mono text-xs">
            {row.action} {row.address}
          </li>
        ))}
        {rows.length > 12 && (
          <li className="pl-3 text-xs">{t("marketplace.setupMore", { count: rows.length - 12 })}</li>
        )}
      </ul>
    </div>
  )
}

/** The package's declared defaults, so an untouched form sends what the package expects. */
function initialValues(item: ShMarketplaceItem): Values {
  const values: Values = {}
  for (const [name, param] of Object.entries(item.params?.properties ?? {})) {
    if (param.default === undefined) continue
    values[name] = typeof param.default === "boolean" ? param.default : String(param.default)
  }
  return values
}

/** A form holds strings; the package declared types. Empty fields are left out for the defaults. */
function coerce(
  values: Values,
  properties: NonNullable<ShMarketplaceItem["params"]>["properties"] & object,
): Record<string, string | number | boolean> {
  const out: Record<string, string | number | boolean> = {}
  for (const [name, value] of Object.entries(values)) {
    if (typeof value === "boolean") {
      out[name] = value
      continue
    }
    if (value.trim() === "") continue
    const type = properties[name]?.type
    out[name] = type === "integer" || type === "number" ? Number(value) : value
  }
  return out
}

/** The server's teaching error, or a fallback (P6). */
function problemOf(error: unknown, fallback: string): string {
  const problem = (error as { response?: { data?: { title?: string; fix?: string } } })?.response?.data
  if (!problem) return fallback
  return [problem.title, problem.fix].filter(Boolean).join(" — ") || fallback
}
