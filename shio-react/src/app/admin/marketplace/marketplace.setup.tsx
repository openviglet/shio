import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { ROUTES } from "@/app/routes.const"
import { ShMarketplaceService } from "@/services/marketplace/marketplace.service"
import type {
  ShMarketplaceItem,
  ShMarketplaceSetupResult,
} from "@/models/marketplace/marketplace.model"
import { LayoutTemplate } from "lucide-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
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
  const properties = item.params?.properties ?? {}
  const required = new Set(item.params?.required ?? [])

  const [site, setSite] = useState("")
  const [createSite, setCreateSite] = useState(true)
  const [mergePostTypes, setMergePostTypes] = useState(item.requires.includes("mergePostTypes"))
  const [values, setValues] = useState<Values>(() => initialValues(item))
  const [plan, setPlan] = useState<ShMarketplaceSetupResult | null>(null)
  const [done, setDone] = useState<ShMarketplaceSetupResult | null>(null)
  const [busy, setBusy] = useState(false)

  /** Any change invalidates the plan: a confirm must write what was planned, and nothing else. */
  const change = (next: () => void) => {
    next()
    setPlan(null)
    setDone(null)
  }

  const request = (dryRun: boolean) => ({
    site: site.trim(),
    params: coerce(values, properties),
    createSite,
    mergePostTypes,
    dryRun,
  })

  const run = (dryRun: boolean) => {
    setBusy(true)
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
        onDone()
      })
      .catch((error) => toast.error(problemOf(error, t("marketplace.setupFailed"))))
      .finally(() => setBusy(false))
  }

  const names = Object.keys(properties)

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

      {plan && <PlanSummary result={plan} />}

      {done && (
        <p className="text-sm">
          {t("marketplace.setupDone", { site: done.apply.site })}{" "}
          <Link className="underline" to={ROUTES.CONTENT_ROOT}>
            {t("marketplace.setupOpen")}
          </Link>
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
