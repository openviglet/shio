import { IconSearch } from "@tabler/icons-react"
import { EmptyState } from "@/components/empty-state"
import { PageHero } from "@/components/page-hero"
import { Panel } from "@/components/panel"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { ROUTES } from "@/app/routes.const"
import { Search as SearchIcon } from "lucide-react"
import { BentoFilterBar, type BentoFilterValue } from "@viglet/viglet-design-system/bento"
import { useCallback, useEffect, useMemo, useState } from "react"
import { contentFacets, countLocally, fromBarValue, toBarValue, withCounts, withSiteFacet } from "@/lib/filter-bar"
// SH1023 — the search term and the site live in the URL, so a search can be sent to somebody.
import { useListState } from "@/hooks/use-list-state"
import { useTranslation } from "react-i18next"
import { useLocation, useNavigate } from "react-router-dom"
import { searchBackTo } from "./content-browser/browser-view"
import { ShSearchService, type ShConsoleSearchRow } from "@/services/search/search.service"
import { usePostTypes, useSites } from "@/hooks/use-catalogue"
import { isCancelled } from "@/lib/read-options"
import { SavedViews } from "@/components/saved-views"

const searchService = new ShSearchService()

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
const SEARCH_LIST = {
  q: "", site: "", type: "", state: "", locale: "", modifiedSince: "", modifiedBefore: "",
  folder: "", subtree: "",
}

/** The keys a search is asked with. `subtree` only qualifies `folder`, so it alone asks nothing. */
const ASKING = ["q", "site", "type", "state", "locale", "modifiedSince", "modifiedBefore", "folder"] as const

/** SH1024 — stable empties, so the facets' memo does not recompute on every render. */
const NO_SITES: { name: string }[] = []
const NO_TYPES: { name: string; title?: string }[] = []
const NO_RESULTS: ShConsoleSearchRow[] = []

/** One search's answer: the rows, how many matched, and how many were withheld (SH1027). */
interface SearchAnswer {
  results: ShConsoleSearchRow[]
  total: number
  withheld: number
}

export default function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const location = useLocation()
  // SH1188 — read once: a filter change rewrites the URL and drops the router state with it.
  const [backTo] = useState(() => searchBackTo(location.state, ROUTES.HOME))
  // SH1046 — one cached read of the site list, shared with every other picker.
  const sites = useSites().data ?? NO_SITES
  const postTypes = usePostTypes().data ?? NO_TYPES
  /**
   * The last answer, as one value. SH1136 — what the page shows is derived from it and from whether
   * anything is being asked, so clearing the filters needs no effect that resets four pieces of state:
   * not asking is "nothing searched", whatever the last answer was.
   */
  const [answer, setAnswer] = useState<SearchAnswer | null>(null)
  // `q` is not a text key here: the bar debounces its own field, and a second debounce would
  // only delay the URL behind what the curator already stopped typing.
  const list = useListState(SEARCH_LIST, { text: ["folder"] })
  const { q, site, type, state, locale, modifiedSince, modifiedBefore, folder, subtree } = list.value
  const scope = folder ? (subtree === "true" ? "subtree" : "folder") : "site"
  /** Whether anything is being asked. A filter alone is a question: "every draft on Blog". */
  const asking = ASKING.some((key) => list.value[key] !== "")
  const shown = asking ? answer : null
  const results = shown?.results ?? NO_RESULTS
  const total = shown?.total ?? 0
  /** SH1027 — how many matched and were removed because this reader may not open them. */
  const withheld = shown?.withheld ?? 0
  const searched = shown !== null

  /*
   * SH1024 — the design system's filter bar over the same URL, the way the content browser draws
   * it. Both stable, for the reason the browser found in a browser: the bar debounces typing in an
   * effect keyed on `value` and `onChange`, and a fresh one per render restarts its timer.
   */
  const barValue = useMemo(
    () => toBarValue({ q, site, type, state, locale, modifiedSince, modifiedBefore }),
    [q, site, type, state, locale, modifiedSince, modifiedBefore])
  const patchList = list.patch
  const onBarChange = useCallback((next: BentoFilterValue) => patchList(fromBarValue(next)), [patchList])
  /** The locales offered are the ones in what was found, so a language chip can only narrow. */
  // SH1024 — the counts are over what was found, which is the question every facet narrows.
  const facets = useMemo(() => withCounts(withSiteFacet(
    contentFacets(postTypes, results.map((row) => row.locale ?? ""), {
      type: t("content.filterType"), state: t("content.filterState"), locale: t("content.filterLocale"),
      modified: t("content.filterModified"), draft: t("content.filterDraft"), published: t("content.filterPublished"),
    }), sites, t("content.filterSite")), searched ? {
      site: countLocally(results, (row) => [row.address.replace(/^post:/, "").split("/")[0]]),
      type: countLocally(results, (row) => [row.type]),
      state: countLocally(results, (row) => [row.state?.toLowerCase(), row.live ? "published" : null]),
      locale: countLocally(results, (row) => [row.locale]),
    } : undefined, barValue), [postTypes, results, sites, searched, barValue, t])

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
    if (!asking) return
    /*
     * SH1047 — the `current` flag this replaced ignored a superseded answer; the signal stops it
     * being sent for. Both keep the wrong rows off the screen, and only one stops the server doing
     * work for a search the curator has already typed past.
     */
    const abort = new AbortController()
    searchService.search({
      q: q.trim(), site, type, state, locale, modifiedSince, modifiedBefore, folder, subtree,
    }, 50, { signal: abort.signal })
      .then((found) => {
        setAnswer({ results: found.results, total: found.count, withheld: found.withheld })
      })
      .catch((reason: unknown) => {
        // A cancelled search leaves the previous rows alone: the next one is already running, and
        // blanking the list between keystrokes is the flicker the debounce exists to avoid.
        if (isCancelled(reason)) return
        setAnswer({ results: [], total: 0, withheld: 0 })
      })
    return () => abort.abort()
  }, [asking, q, site, type, state, locale, modifiedSince, modifiedBefore, folder, subtree])

  return (
    <>
      <PageHero
        icon={SearchIcon}
        title={t("content.searchTitle")}
        subtitle={t("content.searchDescription")}
        urlBase={backTo}
        backLabel={backTo === ROUTES.HOME ? t("home.title") : t("content.browsing")}
      />
      <div>

        {/*
          SH1024 — the bar, over the URL's keys in the server's words. `state` empty still means
          BOTH (SH1027): this page called the delivery API before, which is published-only, so a
          curator could not find the drafts an agent had just written.
        */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start mb-6">
          <div className="flex-1">
            <BentoFilterBar
              value={barValue}
              onChange={onBarChange}
              facets={facets}
              views={<SavedViews list="search" />}
              queryLabel={t("content.searchPlaceholder")}
            />
          </div>
          {/*
            The scope, as the three questions a curator actually asks — spelled in the two words
            the server takes. "This folder and below" is the agent's own default for `folder`, and
            it is the one the CDA's filter could not answer at all: its scope was direct children.
            It stays a select beside the bar, not a facet: its options depend on a folder the URL
            brings, which no chip can choose.
          */}
          {folder && (
            <Select value={scope} onValueChange={(value) => list.patch(
              value === "site"
                ? { folder: "", subtree: "" }
                : { subtree: value === "subtree" ? "true" : "" })}>
              <SelectTrigger className="sm:w-48" aria-label={t("content.searchScope")}><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="site">{t("content.scopeSite")}</SelectItem>
                <SelectItem value="folder">{t("content.scopeFolder")}</SelectItem>
                <SelectItem value="subtree">{t("content.scopeSubtree")}</SelectItem>
              </SelectContent>
            </Select>
          )}
        </div>

        {/*
          SH1027 — the folder this search is scoped to, when it is. It arrives in the URL: the
          content browser links here with its own path already in it, which is what makes the two
          scoped options reachable at all on a page that has no folder of its own.
        */}
        {folder && (
          <p className="text-xs text-muted-foreground -mt-4 mb-6 font-mono">
            {folder}
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
