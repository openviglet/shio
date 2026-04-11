import { Link } from "react-router-dom"
import { useShioQuery } from "@viglet/shio-react-sdk"

const SITE_NAME = "tech_blog"

export function ArticlesPage() {
  const { posts, loading } = useShioQuery("Article", { siteName: SITE_NAME, folderPath: "Home/Articles" })

  if (loading) return <div className="loading">Loading articles...</div>

  return (
    <div className="articles-grid">
      {posts.map((article) => (
        <Link key={article.id} to={`/post/${article.id}`} className="article-card">
          {article.attrs.COVER_IMAGE && (
            <img src={article.attrs.COVER_IMAGE as string} alt={article.title} />
          )}
          <div className="article-card-body">
            <div className="article-card-meta">
              {article.attrs.CATEGORY && (
                <span className="tag">{article.attrs.CATEGORY}</span>
              )}
              <span>{article.attrs.AUTHOR || article.title}</span>
            </div>
            <h2>{article.attrs.TITLE || article.title}</h2>
            <p>{article.attrs.EXCERPT || article.summary}</p>
          </div>
        </Link>
      ))}
      {posts.length === 0 && (
        <div className="loading" style={{ gridColumn: "1 / -1" }}>
          No articles found. Import content into Shio CMS first.
        </div>
      )}
    </div>
  )
}
