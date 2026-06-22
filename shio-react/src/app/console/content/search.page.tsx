import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
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
      <PageHeader title={t("content.searchTitle")} turIcon={SearchIcon} urlBase={`${ROUTES.CONSOLE}/content`} />
      <div className="p-6">
        <div className="mb-4">
          <h2 className="text-lg font-semibold">{t("content.searchTitle")}</h2>
          <p className="text-sm text-muted-foreground">{t("content.searchDescription")}</p>
        </div>

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

        {searched && results.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="rounded-full bg-muted p-4 mb-4">
              <SearchIcon className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="font-semibold text-lg mb-1">{t("content.searchNoResults")}</h3>
            <p className="text-sm text-muted-foreground max-w-sm">{t("content.searchNoResultsHint")}</p>
          </div>
        ) : results.length > 0 ? (
          <Card>
            <CardContent className="p-0">
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
            </CardContent>
          </Card>
        ) : null}
      </div>
    </>
  )
}
