import { IconSearch } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { Search as SearchIcon } from "lucide-react"
import { useEffect, useState, type FormEvent } from "react"
// SH1023 — the search term and the site live in the URL, so a search can be sent to somebody.
import { useListState } from "@/hooks/use-list-state"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { ShSearchService } from "@/services/search/search.service"
import { ShSiteService } from "@/services/site/site.service"
import type { ShCdaPostSummary } from "@/models/search/search.model"
import type { ShSite } from "@/models/site/site"

const searchService = new ShSearchService()
const siteService = new ShSiteService()

const ALL_SITES = "__all__"

/**
 * SH1023 — what this page's URL says, and the two words it says it in.
 *
 * `site` carries the site's **name**, not its id. The id is what the CDA search takes and the name
 * is what every other surface spells — `/agent/find?site=Blog`, `shio find --site Blog` — and the
 * whole value of putting the state in the URL is that the link a curator pastes into a chat is a
 * request an agent can run. A uuid in that position would make the two different questions.
 */
const SEARCH_LIST = { q: "", site: "" }

export default function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [sites, setSites] = useState<ShSite[]>([])
  const [results, setResults] = useState<ShCdaPostSummary[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const list = useListState(SEARCH_LIST, { text: ["q"] })
  const q = list.value.q
  const siteName = list.value.site
  const siteId = sites.find((s) => s.name === siteName)?.id ?? ""

  useEffect(() => {
    siteService.query().then(setSites).catch(() => setSites([]))
  }, [t])

  /**
   * The search runs from the URL, not from the button.
   *
   * <p>That inversion is the whole feature: arriving at `/content/search?q=pricing&site=Blog` runs
   * the search and shows its rows, which is what makes the address reproduce the view. The form's
   * submit commits the draft — one keystroke's worth early — and the effect below does the rest.
   * It waits for the sites to load when one is named, because the name has to become an id before
   * the CDA can be asked and an early run would search every site instead of the one asked for.</p>
   */
  useEffect(() => {
    if (!q.trim()) {
      setResults([])
      setTotal(0)
      setSearched(false)
      return
    }
    if (siteName && !sites.length) return
    let current = true
    setLoading(true)
    searchService.search(q.trim(), siteId || undefined)
      .then((listing) => {
        if (!current) return
        setResults(listing.posts)
        setTotal(listing.totalPosts)
      })
      .catch(() => {
        if (!current) return
        setResults([])
        setTotal(0)
      })
      .finally(() => {
        if (!current) return
        setLoading(false)
        setSearched(true)
      })
    return () => { current = false }
  }, [q, siteName, siteId, sites.length])

  const runSearch = (e?: FormEvent) => {
    e?.preventDefault()
    list.set("q", list.draft.q)
  }

  return (
    <>
      <PageHero
        icon={SearchIcon}
        title={t("content.searchTitle")}
        subtitle={t("content.searchDescription")}
        urlBase={`${ROUTES.CONSOLE}/content`}
      />
      <div className="p-6">

        <form onSubmit={runSearch} className="flex flex-col gap-3 sm:flex-row sm:items-end mb-6">
          <div className="flex-1">
            <Input
              value={list.draft.q}
              onChange={(e) => list.set("q", e.target.value)}
              placeholder={t("content.searchPlaceholder")}
              autoFocus
            />
          </div>
          <Select value={siteName || ALL_SITES}
            onValueChange={(value) => list.set("site", value === ALL_SITES ? "" : value)}>
            <SelectTrigger className="sm:w-56">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_SITES}>{t("content.searchAllSites")}</SelectItem>
              {sites.map((s) => (
                <SelectItem key={s.id} value={s.name}>{s.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button type="submit" disabled={loading || !list.draft.q.trim()}>
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
          <EmptyState
            icon={IconSearch}
            tone="slate"
            title={t("content.searchPromptTitle")}
            description={t("content.searchPromptHint")}
          />
        ) : results.length === 0 ? (
          <EmptyState
            icon={IconSearch}
            tone="slate"
            title={t("content.searchNoResults")}
            description={t("content.searchNoResultsHint")}
          />
        ) : (
          <Panel contentClassName="p-0">
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
          </Panel>
        )}
      </div>
    </>
  )
}
