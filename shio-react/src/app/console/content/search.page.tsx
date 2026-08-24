import { IconSearch } from "@tabler/icons-react"
import { AdaptiveEmptyState } from "@/components/adaptive-empty-state"
import { AdaptivePageHeader } from "@/components/adaptive-page-header"
import { AdaptivePanel } from "@/components/adaptive-panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { Search as SearchIcon } from "lucide-react"
import { useEffect, useState, type FormEvent } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSearchService } from "@/services/search/search.service"
import { ShSiteService } from "@/services/site/site.service"
import type { ShCdaPostSummary } from "@/models/search/search.model"
import type { ShSite } from "@/models/site/site"

const searchService = new ShSearchService()
const siteService = new ShSiteService()

const ALL_SITES = "__all__"

export default function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { resetBreadcrumb } = useBreadcrumb()
  const [sites, setSites] = useState<ShSite[]>([])
  const [siteId, setSiteId] = useState<string>(ALL_SITES)
  const [q, setQ] = useState("")
  const [results, setResults] = useState<ShCdaPostSummary[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: `${ROUTES.CONSOLE}/content` },
      { label: t("content.searchTitle") },
    ])
    siteService.query().then(setSites).catch(() => setSites([]))
  }, [resetBreadcrumb, t])

  const runSearch = async (e?: FormEvent) => {
    e?.preventDefault()
    if (!q.trim()) return
    setLoading(true)
    try {
      const listing = await searchService.search(q.trim(), siteId === ALL_SITES ? undefined : siteId)
      setResults(listing.posts)
      setTotal(listing.totalPosts)
    } catch {
      setResults([])
      setTotal(0)
    } finally {
      setLoading(false)
      setSearched(true)
    }
  }

  return (
    <>
      <AdaptivePageHeader
        icon={SearchIcon}
        title={t("content.searchTitle")}
        subtitle={t("content.searchDescription")}
        urlBase={`${ROUTES.CONSOLE}/content`}
      />
      <div className="p-6">

        <form onSubmit={runSearch} className="flex flex-col gap-3 sm:flex-row sm:items-end mb-6">
          <div className="flex-1">
            <Input
              value={q}
              onChange={(e) => setQ(e.target.value)}
              placeholder={t("content.searchPlaceholder")}
              autoFocus
            />
          </div>
          <Select value={siteId} onValueChange={setSiteId}>
            <SelectTrigger className="sm:w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_SITES}>{t("content.searchAllSites")}</SelectItem>
              {sites.map((s) => (
                <SelectItem key={s.id} value={s.id}>{s.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="submit" disabled={loading || !q.trim()}>
            <SearchIcon className="mr-2 h-4 w-4" />
            {t("common.search")}
          </Button>
        </form>

        {/*
          SH903 — three states, not two, and the third is the one the page opens in.

          Before a query has been run this rendered **nothing at all** below the form: a heading,
          an input and blank space, which is the shape a broken page has. §LV.6 named search as a
          surface "where an empty result is normal" and it is more than that — an empty result is
          normal *and* so is not having asked yet, and the two want different words. "No results
          for that" on a page nobody has searched is worse than silence; it reports a failure that
          did not happen.

          The live check found this rather than a reader: `bento-search` opens with no query, so
          the probe landed on exactly the state that rendered no surface at all.
        */}
        {!searched ? (
          <AdaptiveEmptyState
            icon={IconSearch}
            tone="slate"
            title={t("content.searchPromptTitle")}
            description={t("content.searchPromptHint")}
          />
        ) : results.length === 0 ? (
          <AdaptiveEmptyState
            icon={IconSearch}
            tone="slate"
            title={t("content.searchNoResults")}
            description={t("content.searchNoResultsHint")}
          />
        ) : (
          <AdaptivePanel contentClassName="p-0">
            <div className="px-4 py-2 text-sm text-muted-foreground border-b">
              {t("content.searchResultCount", { count: total })}
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("common.title")}</TableHead>
                  <TableHead className="w-40">{t("content.postType")}</TableHead>
                  <TableHead>{t("content.url")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {results.map((post) => (
                  <TableRow key={post.id} className="cursor-pointer"
                    onClick={() => navigate(`${ROUTES.CONSOLE}/content/post/${post.id}`)}>
                    <TableCell className="font-medium">{post.title || "(untitled)"}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{post.postType}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground font-mono text-xs">{post.furl}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </AdaptivePanel>
        )}
      </div>
    </>
  )
}
