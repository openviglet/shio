import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { ROUTES } from "@/app/routes.const"
import { useBreadcrumb } from "@/contexts/breadcrumb.context"
import { useUnifiedEditor } from "@/contexts/unified-editor.context"
import { IconFileText } from "@tabler/icons-react"
import { Search } from "lucide-react"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import { useNavigate, useSearchParams } from "react-router-dom"
import { ShSearchService } from "@/services/search/search.service"

const service = new ShSearchService()

interface SearchResult {
  id?: string
  title?: string
  type?: string
  breadcrumb?: { id: string; name: string; type: string }[]
  [key: string]: unknown
}

export default function SearchPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { enabled: unifiedEditor } = useUnifiedEditor()
  const [searchParams, setSearchParams] = useSearchParams()
  const { resetBreadcrumb } = useBreadcrumb()
  const [results, setResults] = useState<SearchResult[]>([])
  const [query, setQuery] = useState(searchParams.get("q") ?? "")
  const [isSearching, setIsSearching] = useState(false)

  useEffect(() => {
    resetBreadcrumb([
      { label: t("content.sites"), href: ROUTES.CONTENT_ROOT },
      { label: t("common.search") },
    ])
  }, [resetBreadcrumb, t])

  useEffect(() => {
    const q = searchParams.get("q")
    if (q) {
      setQuery(q)
      setIsSearching(true)
      service.search(q).then((data) => {
        setResults(data as SearchResult[])
        setIsSearching(false)
      }).catch(() => setIsSearching(false))
    }
  }, [searchParams])

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (query.trim()) {
      setSearchParams({ q: query.trim() })
    }
  }

  const handleResultClick = (result: SearchResult) => {
    if (result.type === "folder" && result.id) {
      navigate(`${ROUTES.CONTENT_ROOT}/object/${result.id}`)
    } else if (result.id) {
      navigate(`${ROUTES.CONTENT_ROOT}/${unifiedEditor ? "post-unified" : "post"}/${result.id}`)
    }
  }

  return (
    <>
      <PageHeader title={t("common.search")} turIcon={IconFileText} urlBase={ROUTES.CONTENT_ROOT} />
      <div className="p-6 space-y-6">
        <form onSubmit={handleSearch} className="flex gap-2">
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t("content.searchPlaceholder")}
            className="max-w-md"
          />
          <Button type="submit" disabled={isSearching}>
            <Search className="mr-2 h-4 w-4" />
            {t("common.search")}
          </Button>
        </form>

        {isSearching && (
          <p className="text-muted-foreground">{t("common.loading")}</p>
        )}

        {!isSearching && results.length === 0 && searchParams.get("q") && (
          <p className="text-muted-foreground">{t("common.noResults")}</p>
        )}

        <div className="space-y-3">
          {results.map((result, index) => (
            <Card
              key={result.id ?? index}
              className="cursor-pointer hover:shadow-md transition-shadow"
              onClick={() => handleResultClick(result)}
            >
              <CardHeader className="py-4">
                <CardTitle className="text-base">{result.title ?? `Result ${index + 1}`}</CardTitle>
                {result.breadcrumb && result.breadcrumb.length > 0 && (
                  <CardDescription>
                    {result.breadcrumb.map((b) => b.name).join(" / ")}
                  </CardDescription>
                )}
              </CardHeader>
            </Card>
          ))}
        </div>
      </div>
    </>
  )
}
