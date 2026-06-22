import { Link, useParams } from "react-router-dom"
import { useShioPost } from "@viglet/shio-react-sdk"

export function PostPage() {
  const { postId } = useParams() as { postId: string }
  const { data: post, loading } = useShioPost(postId)

  if (loading) return <div className="loading">Loading article...</div>
  if (!post) return <div className="loading">Article not found.</div>

  const attrs = post.attrs as Record<string, string>

  return (
    <>
      <Link to="/" className="back-link">&larr; Back to articles</Link>

      {/* Hero */}
      {attrs.COVER_IMAGE && (
        <div className="post-hero">
          <img src={attrs.COVER_IMAGE} alt={post.title ?? ""} />
          <div className="post-hero-overlay">
            {attrs.CATEGORY && (
              <span className="tag">{attrs.CATEGORY}</span>
            )}
            <h1>{attrs.TITLE || post.title}</h1>
            <div className="meta">{attrs.AUTHOR} &middot; {post.date}</div>
          </div>
        </div>
      )}

      {!attrs.COVER_IMAGE && (
        <div style={{ padding: "48px", maxWidth: 740, margin: "0 auto" }}>
          <h1 style={{ fontSize: 40, marginBottom: 8 }}>{attrs.TITLE || post.title}</h1>
          <div style={{ color: "#64748b", fontSize: 14, fontFamily: "-apple-system, sans-serif" }}>
            {attrs.AUTHOR} &middot; {attrs.CATEGORY} &middot; {post.date}
          </div>
        </div>
      )}

      {/* Content */}
      {attrs.CONTENT && (
        <div className="post-body">
          <div dangerouslySetInnerHTML={{ __html: attrs.CONTENT }} />
        </div>
      )}
    </>
  )
}
