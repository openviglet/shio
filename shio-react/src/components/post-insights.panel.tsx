import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { AlertTriangle, ImageOff, Lightbulb, Loader2, RefreshCw } from "lucide-react"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { imagesWithoutAlt, readingMinutes } from "@/lib/insights-rules"
import {
  ShInsightsService,
  type ShInsightFinding,
  type ShPageDigest,
} from "@/services/insights/insights.service"

const service = new ShInsightsService()

interface Props {
  /** The SH79 address of the page being edited. Absent while the post is still loading. */
  address?: string
  /**
   * Bumped by the editor after a save. The panel re-reads on a change, which is what "on save and
   * on demand" means in practice — the editor owns when a save happened, not this component.
   */
  revision: number
  /** Scrolls to a field and focuses it, so a finding is a route to the input it is about. */
  onGoToField: (name: string) => void
}

/**
 * SH1013 — what the backend already knows about this page, beside the form for it.
 *
 * <p>The instance computes a lint with a fix per finding and a structural digest of any page, and
 * the console asked for neither: a curator could not see that the page they were editing had an
 * image with no alt text or a heading outline that starts at h3, while `shio verify` printed both
 * to a terminal nobody had open.</p>
 *
 * <h2>Three groups, because a finding and a fact are different things</h2>
 *
 * <p><b>Blockers</b> are the linter's errors, <b>Suggestions</b> its warnings, and <b>Page
 * shape</b> is the digest — not a judgement at all, just what the page is. Folding the third into
 * the other two would make "this page is 340 words" look like something to fix.</p>
 *
 * <h2>On save and on demand, never per keystroke</h2>
 *
 * <p>Both reads render the page server-side. P3 is a budget on tokens and this is the same argument
 * one layer out: a panel that re-linted on every character would cost a render per keystroke to
 * tell the curator something that cannot be true yet, since the draft it lints is the saved one.</p>
 */
export function PostInsightsPanel({ address, revision, onGoToField }: Readonly<Props>) {
  const { t } = useTranslation()
  const [findings, setFindings] = useState<ShInsightFinding[]>([])
  const [digest, setDigest] = useState<ShPageDigest | null>(null)
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    if (!address) return undefined
    setLoading(true)
    setFailed(false)
    return Promise.all([service.verify(address), service.digest(address)])
      .then(([report, shape]) => {
        setFindings(report.findings ?? [])
        setDigest(shape)
      })
      // One flag, not a message: what the curator can do about a failed lint is ask again, and the
      // button that does it is already on screen.
      .catch(() => setFailed(true))
      .finally(() => setLoading(false))
  }, [address])

  useEffect(() => {
    void load()
  }, [load, revision])

  const blockers = findings.filter((finding) => finding.severity === "error")
  const suggestions = findings.filter((finding) => finding.severity !== "error")
  const noAlt = imagesWithoutAlt(digest)

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => void load()} disabled={loading}>
          {loading
            ? <Loader2 className="h-3.5 w-3.5 mr-1 animate-spin" />
            : <RefreshCw className="h-3.5 w-3.5 mr-1" />}
          {t("insights.refresh")}
        </Button>
        {failed && <span className="text-xs text-muted-foreground">{t("insights.unavailable")}</span>}
      </div>

      <Group
        icon={<AlertTriangle className="h-3.5 w-3.5 text-destructive" />}
        title={t("insights.blockers")}
        count={blockers.length}
        empty={t("insights.noBlockers")}
      >
        {blockers.map((finding) => (
          <FindingRow key={`${finding.rule}-${finding.field ?? ""}-${finding.message}`}
            finding={finding} onGoToField={onGoToField} t={t} />
        ))}
      </Group>

      <Group
        icon={<Lightbulb className="h-3.5 w-3.5 text-amber-500" />}
        title={t("insights.suggestions")}
        count={suggestions.length + noAlt.length}
        empty={t("insights.noSuggestions")}
      >
        {suggestions.map((finding) => (
          <FindingRow key={`${finding.rule}-${finding.field ?? ""}-${finding.message}`}
            finding={finding} onGoToField={onGoToField} t={t} />
        ))}
        {/*
          SH1013 — from the digest rather than from the linter: the markup rules scan published rows
          only, deliberately, and this is the one page where a missing alt is worth saying while it
          is still a draft.
        */}
        {noAlt.map((image) => (
          <div key={image.src} className="rounded-md border px-3 py-2 text-sm flex items-start gap-2">
            <ImageOff className="h-3.5 w-3.5 mt-0.5 shrink-0 text-muted-foreground" />
            <div className="min-w-0">
              <div className="truncate">{t("insights.imageNoAlt", { src: image.src })}</div>
              <div className="text-xs text-muted-foreground">{t("insights.imageNoAltFix")}</div>
            </div>
          </div>
        ))}
      </Group>

      <Group
        icon={<span className="text-xs">▤</span>}
        title={t("insights.pageShape")}
        count={digest ? undefined : 0}
        empty={t("insights.noDigest")}
      >
        {digest && (
          <div className="space-y-2">
            <div className="flex flex-wrap gap-1.5">
              <Badge variant="secondary" className="text-[10px]">
                {t("insights.words", { count: digest.words })}
              </Badge>
              <Badge variant="secondary" className="text-[10px]">
                {t("insights.readingTime", { minutes: readingMinutes(digest.words) })}
              </Badge>
              <Badge variant="secondary" className="text-[10px]">
                {t("insights.links", { count: digest.links?.length ?? 0 })}
              </Badge>
              <Badge variant="secondary" className="text-[10px]">
                {t("insights.images", { count: digest.images?.length ?? 0 })}
              </Badge>
            </div>
            {digest.headings?.length > 0 && (
              <ul className="text-xs text-muted-foreground space-y-0.5">
                {digest.headings.map((heading, index) => (
                  <li key={`${heading.level}-${heading.text}-${index}`}
                    style={{ paddingLeft: `${(heading.level - 1) * 12}px` }}>
                    <span className="opacity-60">h{heading.level}</span> {heading.text}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Group>
    </div>
  )
}

function Group({ icon, title, count, empty, children }: Readonly<{
  icon: React.ReactNode
  title: string
  count?: number
  empty: string
  children?: React.ReactNode
}>) {
  const isEmpty = count === 0 || (Array.isArray(children) && children.flat().length === 0)
  return (
    <div className="space-y-1.5">
      <div className="flex items-center gap-1.5 text-xs font-semibold">
        {icon}
        {title}
        {count !== undefined && count > 0 && (
          <Badge variant="secondary" className="h-4 px-1.5 text-[10px]">{count}</Badge>
        )}
      </div>
      {isEmpty ? <p className="text-xs text-muted-foreground">{empty}</p> : children}
    </div>
  )
}

function FindingRow({ finding, onGoToField, t }: Readonly<{
  finding: ShInsightFinding
  onGoToField: (name: string) => void
  t: (key: string, options?: Record<string, unknown>) => string
}>) {
  return (
    <div className="rounded-md border px-3 py-2 text-sm space-y-1">
      <div className="flex items-start gap-2">
        <Badge variant="outline" className="h-4 px-1.5 text-[10px] shrink-0 mt-0.5">
          {finding.rule}
        </Badge>
        <span className="min-w-0">{finding.message}</span>
      </div>
      {finding.fix && <p className="text-xs text-muted-foreground">{finding.fix}</p>}
      {/*
        The finding becomes a route to the input. A rule that knows no field — a broken link on the
        rendered page belongs to the page, not to one box — simply does not offer the button, rather
        than offering one that lands somewhere arbitrary.
      */}
      {finding.field && (
        <Button type="button" variant="ghost" size="sm" className="h-6 px-2 text-xs"
          onClick={() => onGoToField(finding.field as string)}>
          {t("insights.goToField", { field: finding.field })}
        </Button>
      )}
    </div>
  )
}
