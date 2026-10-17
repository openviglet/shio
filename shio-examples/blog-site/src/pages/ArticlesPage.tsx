import { Link } from "react-router-dom"
import {
  useShioPost,
  useShioQuery,
  useShioSites,
  type ShioPostSummary,
} from "@viglet/shio-react-sdk"

const SITE_NAME = "Tech Blog"

export function ArticlesPage() {
  // The CDA query filters by site id, not name — resolve it from the sites list.
  const { data: sites, loading: sitesLoading } = useShioSites()
  const siteId = sites?.find((s) => s.name === SITE_NAME)?.id

  // A listing returns lightweight summaries (title/summary/date) — no attributes.
  // Gate the query on the resolved site id so it never fires with siteId=undefined.
  const { data: listing, loading } = useShioQuery(
    { siteId, postType: "Article" },
    !!siteId
  )

  if (sitesLoading || loading) return <div className="loading">Loading articles...</div>

  const articles = listing?.posts ?? []

  return (
    <div className="articles-grid">
      {articles.map((article) => (
        <ArticleCard key={article.id} article={article} />
      ))}
      {articles.length === 0 && (
        <div className="loading" style={{ gridColumn: "1 / -1" }}>
          No articles found. Import content into Viglet Shio first.
        </div>
      )}
    </div>
  )
}

/**
 * A listing item carries metadata only, so the card hydrates the full post to
 * render the cover image, category and author from its attributes.
 */
function ArticleCard({ article }: Readonly<{ article: ShioPostSummary }>) {
  const { data: post } = useShioPost(article.id)
  const attrs = (post?.attrs ?? {}) as Record<string, string>

  return (
    <Link to={`/post/${article.id}`} className="article-card">
      {attrs.COVER_IMAGE && (
        <img src={attrs.COVER_IMAGE} alt={article.title ?? ""} />
      )}
      <div className="article-card-body">
        <div className="article-card-meta">
          {attrs.CATEGORY && <span className="tag">{attrs.CATEGORY}</span>}
          <span>{attrs.AUTHOR || article.title}</span>
        </div>
        <h2>{attrs.TITLE || article.title}</h2>
        <p>{attrs.EXCERPT || article.summary}</p>
      </div>
    </Link>
  )
}
