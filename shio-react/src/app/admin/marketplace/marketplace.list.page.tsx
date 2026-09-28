import { IconPackage } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { ShMarketplaceService } from "@/services/marketplace/marketplace.service"
import {
  MARKETPLACE_BUILTIN_ONLY,
  MARKETPLACE_UNREACHABLE,
  type ShMarketplaceCatalogue,
  type ShMarketplaceItem,
} from "@/models/marketplace/marketplace.model"
import { AlertTriangle, Download, Package, RefreshCw, Search, Trash2 } from "lucide-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new ShMarketplaceService()

/**
 * The package marketplace (SH679) — the human half of blueprints.
 *
 * Before this, `grep -ril blueprint shio-react/src` returned nothing: a curator could not see what
 * the instance can build from, which is the human side of the product's headline capability.
 *
 * <h2>Why the install dialog is a dialog</h2>
 *
 * Installing puts a stranger's package where the registry will load it, and the package may declare
 * opt-ins (SH151/SH152) that let it create a site or alter a post-type the curator already has.
 * P7 says the human is never surprised, so those are a consent to read, not a footnote — and the
 * dry run runs first, so the dialog reports what *would* happen before anything is written.
 */
export default function MarketplaceListPage() {
  const { t } = useTranslation()

  const [catalogue, setCatalogue] = useState<ShMarketplaceCatalogue | null>(null)
  const [loading, setLoading] = useState(true)
  const [term, setTerm] = useState("")
  const [category, setCategory] = useState<string | null>(null)
  const [selected, setSelected] = useState<ShMarketplaceItem | null>(null)
  const [readme, setReadme] = useState<string | null>(null)
  const [plan, setPlan] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  /**
   * The read. It touches no state before the request starts, which is what lets the mount effect
   * call it (SH699): `loading` already begins `true`, so raising it there would be the cascading
   * render `react-hooks/set-state-in-effect` is about.
   */
  const fetchCatalogue = useCallback(
    () =>
      service
        .catalogue()
        .then(setCatalogue)
        .catch(() => setCatalogue(null))
        .finally(() => setLoading(false)),
    [],
  )

  /** The curator asked — refresh, install, uninstall — so the spinner is a response to them. */
  const load = useCallback(() => {
    setLoading(true)
    void fetchCatalogue()
  }, [fetchCatalogue])

  useEffect(() => {
    void fetchCatalogue()
  }, [fetchCatalogue])

  const categories = useMemo(() => {
    const found = new Set<string>()
    catalogue?.items.forEach((item) => {
      if (item.category) found.add(item.category)
    })
    return [...found].sort((a, b) => a.localeCompare(b))
  }, [catalogue])

  const items = useMemo(() => {
    const needle = term.trim().toLowerCase()
    return (catalogue?.items ?? []).filter((item) => {
      if (category && item.category !== category) return false
      if (!needle) return true
      return `${item.id} ${item.title ?? ""} ${item.description ?? ""} ${item.tags.join(" ")}`
        .toLowerCase()
        .includes(needle)
    })
  }, [catalogue, term, category])

  /** Open the detail, and fetch the README through the instance rather than from the browser. */
  const open = useCallback((item: ShMarketplaceItem) => {
    setSelected(item)
    setPlan(null)
    setReadme(null)
    if (!item.readmeUrl) return
    service
      .readme(item.readmeUrl)
      // A README that will not load is not a reason to withhold the entry, which is the answer
      // the curator asked for. Absent rather than an error.
      .then(setReadme)
      .catch(() => setReadme(null))
  }, [])

  /** The dry run, so the dialog can say what would happen before anything is written (P7). */
  const preview = useCallback((item: ShMarketplaceItem) => {
    setBusy(true)
    service
      .install(item.id, true)
      .then((result) =>
        setPlan(t("marketplace.wouldInstall", { version: result.version ?? "-", origin: result.origin })),
      )
      .catch((error) => {
        setPlan(null)
        toast.error(problemOf(error, t("marketplace.installFailed")))
      })
      .finally(() => setBusy(false))
  }, [t])

  const install = useCallback(
    (item: ShMarketplaceItem) => {
      setBusy(true)
      service
        .install(item.id, false)
        .then((result) => {
          toast.success(t("marketplace.installed", { id: item.id, count: result.loaded }))
          setSelected(null)
          load()
        })
        .catch((error) => toast.error(problemOf(error, t("marketplace.installFailed"))))
        .finally(() => setBusy(false))
    },
    [load, t],
  )

  const uninstall = useCallback(
    (item: ShMarketplaceItem) => {
      setBusy(true)
      service
        .uninstall(item.id)
        .then(() => {
          toast.success(t("marketplace.uninstalled", { id: item.id }))
          setSelected(null)
          load()
        })
        .catch((error) => toast.error(problemOf(error, t("marketplace.uninstallFailed"))))
        .finally(() => setBusy(false))
    },
    [load, t],
  )

  return (
    <div className="flex flex-col gap-4">
      {/*
        SH906 — the marketplace is the one of these three that genuinely is a mosaic, so its
        grid stays a grid. The refresh moves into the hero beside the filters it belongs with.
      */}
      <PageHero
        title={t("marketplace.title")}
        eyebrow={t("admin.title")}
        subtitle={t("marketplace.description")}
      />

      <div className="flex flex-wrap items-center gap-2">
        <div className="relative flex-1 min-w-[220px]">
          <Search className="absolute left-2 top-2.5 size-4 text-muted-foreground" />
          <Input
            className="pl-8"
            placeholder={t("marketplace.searchPlaceholder")}
            value={term}
            onChange={(event) => setTerm(event.target.value)}
          />
        </div>
        <Button
          variant={category === null ? "secondary" : "outline"}
          size="sm"
          onClick={() => setCategory(null)}
        >
          {t("marketplace.allCategories")}
        </Button>
        {categories.map((each) => (
          <Button
            key={each}
            variant={category === each ? "secondary" : "outline"}
            size="sm"
            onClick={() => setCategory(each)}
          >
            {each}
          </Button>
        ))}
        <Button variant="outline" size="sm" onClick={load} disabled={loading}>
          <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          {t("common.refresh")}
        </Button>
      </div>

      {/* Where the entries came from. Always drawn, because a listing that silently shrank is
          indistinguishable from a catalogue that shrank. */}
      {catalogue && <SourceNote source={catalogue.source} />}

      {items.length === 0 && !loading ? (
        <EmptyState
          icon={IconPackage}
          tone="blue"
          title={t("marketplace.empty")}
          description={t("marketplace.emptyHint")}
        />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
          {items.map((item) => (
            /*
              SH906 — a real `<button>`, not a `<div onClick>`.
              The console tile was a `Card` with an `onClick`: not focusable, not announced, and
              unreachable by keyboard — the whole catalogue was mouse-only. That is the same class
              of defect SH922 swept for, from the other side: SH922 found interactive things nested
              inside interactive things, and this is a thing that looks interactive and is not one.
              The panel carries the chrome; the button carries the behaviour.
            */
            <Panel
              key={item.id}
              className="cursor-pointer transition-colors hover:border-primary"
              contentClassName="p-0"
            >
              <button
                type="button"
                onClick={() => open(item)}
                className="flex w-full flex-col gap-2 p-4 text-left"
              >
                <div className="flex items-start justify-between gap-2">
                  <span className="font-medium break-all">{item.title ?? item.id}</span>
                  <Badge variant={item.installed ? "secondary" : "outline"}>
                    {item.installed ? t("marketplace.installedBadge") : t("marketplace.availableBadge")}
                  </Badge>
                </div>
                <span className="text-xs text-muted-foreground break-all">{item.id}</span>
                {item.description && (
                  <p className="line-clamp-3 text-sm text-muted-foreground">{item.description}</p>
                )}
                <div className="flex flex-wrap items-center gap-1 pt-1">
                  {item.builtIn && <Badge variant="outline">{t("marketplace.builtIn")}</Badge>}
                  {item.version && <Badge variant="outline">{item.version}</Badge>}
                  {item.requires.map((each) => (
                    <Badge key={each} variant="outline" className="border-amber-400 text-amber-600">
                      {each}
                    </Badge>
                  ))}
                </div>
              </button>
            </Panel>
          ))}
        </div>
      )}

      <Dialog open={Boolean(selected)} onOpenChange={(next) => !next && setSelected(null)}>
        <DialogContent className="sm:max-w-2xl max-h-[85vh] overflow-y-auto">
          {selected && (
            <>
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <Package className="size-4" />
                  {selected.title ?? selected.id}
                </DialogTitle>
                <DialogDescription className="break-all">
                  {selected.id} {selected.version ? `· ${selected.version}` : ""}
                  {selected.author ? ` · ${selected.author}` : ""}
                </DialogDescription>
              </DialogHeader>

              {selected.description && <p className="text-sm">{selected.description}</p>}

              {/* P7: the opt-ins are a consent, not a footnote. "This package will alter a
                  post-type you already have" is exactly the surprise worth a click. */}
              {selected.requires.length > 0 && (
                <div className="flex items-start gap-2 rounded-md border border-amber-400 p-3 text-sm">
                  <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
                  <div>
                    <p className="font-medium">{t("marketplace.requiresTitle")}</p>
                    <p className="text-muted-foreground">
                      {t("marketplace.requiresBody", { list: selected.requires.join(", ") })}
                    </p>
                  </div>
                </div>
              )}

              <ParamList item={selected} />

              {readme && (
                <pre className="max-h-64 overflow-auto rounded-md bg-muted p-3 text-xs whitespace-pre-wrap">
                  {readme}
                </pre>
              )}

              {plan && <p className="text-sm text-muted-foreground">{plan}</p>}

              <DialogFooter className="gap-2">
                {selected.installed ? (
                  selected.builtIn ? (
                    <span className="text-sm text-muted-foreground">
                      {t("marketplace.builtInNote")}
                    </span>
                  ) : (
                    <Button variant="destructive" disabled={busy} onClick={() => uninstall(selected)}>
                      <Trash2 className="size-4" />
                      {t("marketplace.uninstall")}
                    </Button>
                  )
                ) : (
                  <>
                    <Button variant="outline" disabled={busy} onClick={() => preview(selected)}>
                      {t("marketplace.dryRun")}
                    </Button>
                    <Button disabled={busy} onClick={() => install(selected)}>
                      <Download className="size-4" />
                      {t("marketplace.install")}
                    </Button>
                  </>
                )}
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}

/** The package's parameters, which a later apply has to satisfy. */
function ParamList({ item }: { item: ShMarketplaceItem }) {
  const { t } = useTranslation()
  const properties = item.params?.properties
  if (!properties) return null
  const required = new Set(item.params?.required ?? [])
  const names = Object.keys(properties)
  if (names.length === 0) return null

  return (
    <div className="rounded-md border p-3">
      <p className="mb-2 text-sm font-medium">{t("marketplace.params")}</p>
      <ul className="flex flex-col gap-1 text-sm">
        {names.map((name) => (
          <li key={name} className="text-muted-foreground">
            <code className="text-foreground">{name}</code>
            {required.has(name) && <span className="text-amber-600"> *</span>}
            {properties[name]?.description ? ` — ${properties[name].description}` : ""}
            {properties[name]?.default !== undefined && (
              <span> ({String(properties[name].default)})</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Where the list came from — and, when a catalogue could not be read, why. */
function SourceNote({ source }: { source: ShMarketplaceCatalogue["source"] }) {
  const { t } = useTranslation()

  if (source.state === MARKETPLACE_BUILTIN_ONLY) {
    return <p className="text-xs text-muted-foreground">{t("marketplace.sourceBuiltIn")}</p>
  }
  if (source.state === MARKETPLACE_UNREACHABLE) {
    return (
      <div className="flex items-start gap-2 rounded-md border border-amber-400 p-3 text-sm">
        <AlertTriangle className="mt-0.5 size-4 shrink-0 text-amber-600" />
        <div>
          <p className="font-medium">{t("marketplace.sourceUnreachable", { url: source.url })}</p>
          <p className="text-muted-foreground">{source.reason}</p>
          <p className="text-muted-foreground">{t("marketplace.sourceFallback")}</p>
        </div>
      </div>
    )
  }
  return (
    <p className="text-xs text-muted-foreground">
      {t("marketplace.sourceRemote", { url: source.url })}
      {source.reason ? ` — ${source.reason}` : ""}
    </p>
  )
}

/**
 * The server's teaching error, or a fallback.
 *
 * Every 4xx here carries `fix` (P6), and it is the most useful sentence in the response — the
 * marketplace's two commonest refusals are "the feature is not enabled" and "no entry has that
 * id", and both say what to do. Showing a bare status instead would throw the instruction away.
 */
function problemOf(error: unknown, fallback: string): string {
  const problem = (error as { response?: { data?: { title?: string; fix?: string } } })?.response?.data
  if (!problem) return fallback
  return [problem.title, problem.fix].filter(Boolean).join(" — ") || fallback
}
