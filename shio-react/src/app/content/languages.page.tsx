import { IconLanguage } from "@tabler/icons-react"
import { AlertTriangle, Bot, Check, Plus } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { Link, useNavigate, useParams } from "react-router-dom"
import { GradientButton, toast } from "@viglet/viglet-design-system"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { isCancelled } from "@/lib/read-options"
import { reportProblem } from "@/lib/problem"
import { ShSiteService } from "@/services/site/site.service"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { ShCoverageCell, ShSiteCoverage } from "@/models/site/site"
import { sendRequest } from "@/app/system/dock-chat-send"
import { useAgentAvailable } from "@/app/system/use-agent-available"

const siteService = new ShSiteService()
const unifiedService = new ShPostUnifiedService()

const PAGE_SIZE = 50

/** SH1062 — one cell the agent is asked for: its source, the language, and the translation when it exists. */
type AskTarget = { sourceId: string; locale: string; existing?: string }

/**
 * SH1060 — the Languages board: a row per page, a column per language the site offers.
 *
 * What it is for is the decision a translation backlog forces — where to spend the next week —
 * and that decision needs the gaps in one place rather than a page at a time. Each cell is
 * current, stale (SH1058) or missing, and each is a way in: an existing translation opens, and a
 * missing one is created from this page rather than by walking to the source first.
 *
 * The counts come from the server's own pass over the site (SH1131), never from the rows on
 * screen: a board that counted what it is showing would say "one missing" while paging through
 * forty.
 */
export default function LanguagesPage() {
  const { siteId } = useParams() as { siteId: string }
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [board, setBoard] = useState<ShSiteCoverage | null>(null)
  const [page, setPage] = useState(0)
  // SH1136 — derived, not raised by the read: the board is loading while the one on screen answers
  // another site or page than the one asked for. Raising a flag at the top of the read is what put a
  // synchronous setState in the mount effect. A refresh after an action keeps the board on screen.
  const [answered, setAnswered] = useState<string | null>(null)
  const loading = answered !== `${siteId}|${page}`
  const [busy, setBusy] = useState<string | null>(null)
  const agentAvailable = useAgentAvailable()

  const load = useCallback((signal?: AbortSignal) => {
    const asked = `${siteId}|${page}`
    return siteService.coverage(siteId, page, PAGE_SIZE, { signal })
      .then((answer) => {
        setBoard(answer)
        setAnswered(asked)
      })
      .catch((reason) => {
        // SH1047 — an abort is not an empty board: paging quickly would otherwise blank the table
        // the later answer is about to fill, and leaving the page would report a site with nothing
        // in it.
        if (isCancelled(reason)) return
        setBoard(null)
        setAnswered(asked)
      })
  }, [siteId, page])

  useEffect(() => {
    // SH1047 — the key moves when the reader pages, so a slow first page must not land over the
    // second: the earlier read is aborted rather than raced.
    const abort = new AbortController()
    void load(abort.signal)
    return () => abort.abort()
  }, [load])

  /**
   * SH1062 — ask the connected agent for a translation instead of writing it.
   *
   * <p>Shio hosts no model, so the board asks. It is the board's own two moves in sequence: a
   * missing cell's draft is created first (SH1060), then a `request` note on the draft asks for it
   * to be written or, for a stale one, brought up to date with its source. The draft carries the
   * target locale and its translation group, so the agent reading `shio://requests` gets the page
   * to write and where it comes from; its answer lands in the dock (SH1006) with the change attached
   * (SH1007). Nothing is published by asking.</p>
   */
  /**
   * One request per cell, one after another, then one reload. Each cell is the draft when it is
   * missing, then the note on it. A row asks for every language it is missing or behind in, a
   * column for every page on this screen in that language: each cell stays its own draft and its
   * own note, so each is answered, approved and marked on its own.
   */
  const ask = async (targets: AskTarget[], key: string) => {
    if (targets.length === 0) return
    setBusy(key)
    let sent = 0
    try {
      for (const { sourceId, locale, existing } of targets) {
        const target = existing ?? (await unifiedService.translate(sourceId, locale)).id
        const body = existing
          ? t("content.coverageAskRefresh", { locale })
          : t("content.coverageAskWrite", { locale })
        if (await sendRequest(target, body, t)) sent++
      }
      if (sent > 0) toast.success(t("content.coverageAskedMany", { count: sent }))
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      await load()
      setBusy(null)
    }
  }

  /** What a row or a column can still ask for: missing or stale, and not already asked. */
  const askable = (sourceId: string, locale: string, value: ShCoverageCell | undefined): AskTarget[] => {
    if (value?.requested) return []
    if (!value || value.state === "missing") return [{ sourceId, locale }]
    return value.state === "stale" && value.id ? [{ sourceId, locale, existing: value.id }] : []
  }
  const rowTargets = (row: ShSiteCoverage["rows"][number]) =>
    (board?.locales ?? []).flatMap((locale) => askable(row.id, locale, row.cells[locale]))
  const columnTargets = (locale: string) =>
    (board?.rows ?? []).flatMap((row) => askable(row.id, locale, row.cells[locale]))

  /** The ask control, shown only while an agent is around to answer (SH1019's rule). */
  const askButton = (rowId: string, locale: string, existing?: string) => agentAvailable && (
    <Button variant="ghost" size="icon-sm" className="text-muted-foreground"
      disabled={busy !== null}
      onClick={() => ask([{ sourceId: rowId, locale, existing }], `${rowId}|${locale}`)}
      aria-label={t("content.coverageAsk", { locale })}
      title={t("content.coverageAsk", { locale })}>
      <Bot className="size-3.5" />
    </Button>
  )

  /** The same ask over many cells, offered only when there is something left to ask for. */
  const askManyButton = (targets: AskTarget[], key: string, label: string) =>
    agentAvailable && targets.length > 0 && (
      <Button variant="ghost" size="icon-sm" className="text-muted-foreground"
        disabled={busy !== null}
        onClick={() => ask(targets, key)}
        aria-label={label} title={label}>
        <Bot className="size-3.5" />
      </Button>
    )

  /** The gesture the board exists for: start the missing translation from where it is missing. */
  const translate = async (sourceId: string, locale: string) => {
    setBusy(`${sourceId}|${locale}`)
    try {
      const created = await unifiedService.translate(sourceId, locale)
      toast.success(t("content.translationCreated"))
      navigate(`${ROUTES.CONSOLE}/content/post/${created.id}`)
    } catch (error) {
      reportProblem(error, t("common.error"))
    } finally {
      setBusy(null)
    }
  }

  const cell = (rowId: string, locale: string, value: ShCoverageCell | undefined) => {
    if (!value || value.state === "missing") {
      return (
        <span className="inline-flex items-center gap-0.5">
          <Button variant="ghost" size="sm" className="gap-1 px-2 text-xs text-muted-foreground"
            disabled={busy === `${rowId}|${locale}`}
            onClick={() => translate(rowId, locale)}
            title={t("content.coverageTranslate", { locale })}>
            <Plus className="size-3.5" />
            {t("content.coverageMissing")}
          </Button>
          {askButton(rowId, locale)}
        </span>
      )
    }
    // SH1062 — asked and not yet answered: the draft exists, but nobody has written it yet, and
    // "current" there would say the work is done.
    if (value.requested) {
      return (
        <Link className="inline-flex items-center gap-1 text-xs text-muted-foreground hover:underline"
          to={`${ROUTES.CONSOLE}/content/post/${value.id}`}
          title={t("content.coverageRequestedHint")}>
          <Bot className="size-3.5 text-sky-600 dark:text-sky-400" />
          {t("content.coverageRequested")}
        </Link>
      )
    }
    const stale = value.state === "stale"
    return (
      <span className="inline-flex items-center gap-0.5">
        <Link className="inline-flex items-center gap-1 text-xs hover:underline"
          to={`${ROUTES.CONSOLE}/content/post/${value.id}`}
          title={stale ? t("content.translationStaleHint") : undefined}>
          {stale
            ? <AlertTriangle className="size-3.5 text-amber-600 dark:text-amber-400" />
            : <Check className="size-3.5 text-green-600 dark:text-green-400" />}
          {stale ? t("content.translationStale") : t("content.coverageCurrent")}
        </Link>
        {stale && askButton(rowId, locale, value.id)}
      </span>
    )
  }

  const locales = board?.locales ?? []
  const pages = board ? Math.ceil(board.total / Math.max(board.size, 1)) : 0

  return (
    <>
      <PageHero
        title={t("content.coverageTitle")}
        subtitle={t("content.coverageDescription")}
        urlBase={`${ROUTES.CONTENT_ROOT}/site/${siteId}`}
        backLabel={t("content.siteLabel")}
      />
      <div className="space-y-3">
        {/* The totals are the site's, so they do not move as the reader pages. */}
        {board && (
          <div className="flex flex-wrap gap-2">
            {locales.map((locale) => (
              <Badge key={locale} variant="outline" className="gap-2 text-xs">
                <span className="font-mono uppercase">{locale}</span>
                <span className="text-muted-foreground">
                  {t("content.coverageTally", {
                    current: board.totals[locale]?.current ?? 0,
                    stale: board.totals[locale]?.stale ?? 0,
                    missing: board.totals[locale]?.missing ?? 0,
                  })}
                </span>
              </Badge>
            ))}
          </div>
        )}
        {!loading && (!board || board.rows.length === 0) ? (
          <EmptyState
            icon={IconLanguage}
            tone="blue"
            title={t("content.coverageEmptyTitle")}
            description={t("content.coverageEmptyDescription")}
          />
        ) : (
          <Panel contentClassName="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("content.coveragePage")}</TableHead>
                  {locales.map((locale) => (
                    <TableHead key={locale} className="w-32 font-mono text-xs uppercase">
                      <span className="inline-flex items-center gap-1">
                        {locale}
                        {/* SH1062 — this page's column: every page on screen missing or behind in it. */}
                        {askManyButton(columnTargets(locale), `column|${locale}`,
                          t("content.coverageAskColumn", { locale }))}
                      </span>
                    </TableHead>
                  ))}
                </TableRow>
              </TableHeader>
              <TableBody>
                {board?.rows.map((row) => (
                  <TableRow key={row.address}>
                    <TableCell>
                      <span className="inline-flex items-center gap-1">
                        <Link className="font-medium hover:underline"
                          to={`${ROUTES.CONSOLE}/content/post/${row.id}`}>
                          {row.title || row.address}
                        </Link>
                        {/* SH1062 — this page in every language it is missing or behind in. */}
                        {askManyButton(rowTargets(row), `row|${row.id}`,
                          t("content.coverageAskRow", { title: row.title || row.address }))}
                      </span>
                      <div className="text-xs text-muted-foreground">{row.address}</div>
                    </TableCell>
                    {locales.map((locale) => (
                      <TableCell key={locale}>{cell(row.id, locale, row.cells[locale])}</TableCell>
                    ))}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Panel>
        )}
        {pages > 1 && (
          <div className="flex items-center justify-end gap-2 text-xs">
            <GradientButton type="button" size="sm" variant="outline" disabled={page === 0}
              onClick={() => setPage((current) => Math.max(current - 1, 0))}>
              {t("common.previous")}
            </GradientButton>
            <span className="text-muted-foreground">
              {t("content.coveragePageOf", { page: page + 1, pages })}
            </span>
            <GradientButton type="button" size="sm" variant="outline" disabled={page + 1 >= pages}
              onClick={() => setPage((current) => current + 1)}>
              {t("common.next")}
            </GradientButton>
          </div>
        )}
      </div>
    </>
  )
}
