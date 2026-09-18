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
import { ShSearchService, type ShConsoleSearchRow } from "@/services/search/search.service"
import { ShSiteService } from "@/services/site/site.service"
import type { ShSite } from "@/models/site/site"

const searchService = new ShSearchService()
const siteService = new ShSiteService()

const ALL_SITES = "__all__"
const ANY_STATE = "__any__"

/**
 * SH1023/SH1027 — what this page's URL says, in the words the server says them in.
 *
 * `site` carries the site's **name**, not its id, and every other key here is `ShContentQuery`'s.
 * That is the whole value of putting the state in the URL: the link a curator pastes into a chat
 * is a request an agent can run — `/agent/find?site=Blog&state=draft` and
 * `shio find --site Blog --state draft` are the same question. A uuid in that position would make
 * them two.
 *
 * `state` defaults to empty, which the server reads as **both**. That default is SH1027 in one
 * word: this page called the delivery API before, which is published-only, so a curator could not
 * find the drafts an agent had just written.
 */
const SEARCH_LIST = { q: "", site: "", state: "", folder: "", subtree: "" }

export default function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [sites, setSites] = useState<ShSite[]>([])
  const [results, setResults] = useState<ShConsoleSearchRow[]>([])
  const [total, setTotal] = useState(0)
  /** SH1027 — how many matched and were removed because this reader may not open them. */
  const [withheld, setWithheld] = useState(0)
  const [loading, setLoading] = useState(false)
  const [searched, setSearched] = useState(false)
  const list = useListState(SEARCH_LIST, { text: ["q", "folder"] })
  const q = list.value.q
  const siteName = list.value.site
  const scope = list.value.folder
    ? (list.value.subtree === "true" ? "subtree" : "folder")
    : "site"

  useEffect(() => {
    siteService.query().then(setSites).catch(() => setSites([]))
  }, [t])

  /**
   * The search runs from the URL, not from the button.
   *
   * <p>That inversion is the whole feature (SH1023): arriving at
   * `/content/search?q=pricing&site=Blog&state=draft` runs the search and shows its rows, which is
   * what makes the address reproduce the view. The form's submit commits the draft — one
   * keystroke's worth early — and this effect does the rest.</p>
   *
   * <p>SH1027 — it sends the filters as the server's own words, so nothing is translated on the
   * way. There is no site-name-to-id step any more either: `site` goes as a name, which is what
   * `/agent/find` takes.</p>
   */
  useEffect(() => {
    if (!q.trim()) {
      setResults([])
      setTotal(0)
      setWithheld(0)
      setSearched(false)
      return
    }
    let current = true
    setLoading(true)
    searchService.search({
      q: q.trim(),
      site: siteName,
      state: list.value.state,
      folder: list.value.folder,
      subtree: list.value.subtree,
    })
      .then((found) => {
        if (!current) return
        setResults(found.results)
        setTotal(found.count)
        setWithheld(found.withheld)
      })
      .catch(() => {
        if (!current) return
        setResults([])
        setTotal(0)
        setWithheld(0)
      })
      .finally(() => {
        if (!current) return
        setLoading(false)
        setSearched(true)
      })
    return () => { current = false }
  }, [q, siteName, list.value.state, list.value.folder, list.value.subtree])

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
          {/*
            SH1027 — `state` empty means BOTH, and that default is the task in one word: this page
            called the delivery API before, which is published-only, so a curator could not find
            the drafts an agent had just written.
          */}
          <Select value={list.value.state || ANY_STATE}
            onValueChange={(value) => list.set("state", value === ANY_STATE ? "" : value)}>
            <SelectTrigger className="sm:w-40"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value={ANY_STATE}>{t("content.searchAnyState")}</SelectItem>
              <SelectItem value="draft">{t("content.filterDraft")}</SelectItem>
              <SelectItem value="published">{t("content.filterPublished")}</SelectItem>
            </SelectContent>
          </Select>
          {/*
            The scope, as the three questions a curator actually asks — spelled in the two words
            the server takes. "This folder and below" is the agent's own default for `folder`, and
            it is the one the CDA's filter could not answer at all: its scope was direct children.
          */}
          <Select value={scope} onValueChange={(value) => list.patch(
            value === "site"
              ? { folder: "", subtree: "" }
              : { subtree: value === "subtree" ? "true" : "" })}>
            <SelectTrigger className="sm:w-48"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="site">{t("content.scopeSite")}</SelectItem>
              <SelectItem value="folder" disabled={!list.value.folder}>
                {t("content.scopeFolder")}
              </SelectItem>
              <SelectItem value="subtree" disabled={!list.value.folder}>
                {t("content.scopeSubtree")}
              </SelectItem>
            </SelectContent>
          </Select>
          <Button type="submit" disabled={loading || !list.draft.q.trim()}>
            <SearchIcon className="mr-2 h-4 w-4" />
            {t("common.search")}
          </Button>
        </form>

        {/*
          SH1027 — the folder this search is scoped to, when it is. It arrives in the URL: the
          content browser links here with its own path already in it, which is what makes the two
          scoped options reachable at all on a page that has no folder of its own.
        */}
        {list.value.folder && (
          <p className="text-xs text-muted-foreground -mt-4 mb-6 font-mono">
            {list.value.folder}
          </p>
        )}

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
              {/*
                SH1027 — the number, not silence. A filtered listing that says nothing is
                indistinguishable from a search that found less, and the reader's next move is to
                conclude the content is not there — which sends them to write the page again
                rather than to ask somebody for access.
              */}
              {withheld > 0 && (
                <span className="ml-2">{t("content.searchWithheld", { count: withheld })}</span>
              )}
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
                {results.map((row) => (
                  <TableRow key={row.address} className="cursor-pointer"
                    onClick={() => row.id && navigate(`${ROUTES.CONSOLE}/content/post/${row.id}`)}>
                    <TableCell className="font-medium">
                      {row.title || "(untitled)"}
                      {/* The state, because a draft and what is live are now both in here. */}
                      {row.state === "DRAFT" && (
                        <Badge variant="secondary" className="ml-2 text-[10px]">
                          {t("content.filterDraft")}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline">{row.type}</Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground font-mono text-xs">
                      {row.address}
                    </TableCell>
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
