import { Link, useParams } from "react-router-dom"
import { useShioPost } from "@viglet/shio-react-sdk"

export function PostPage() {
  const { postId } = useParams() as { postId: string }
  const { post, loading } = useShioPost(postId)

  if (loading) return <div className="loading">Loading article...</div>
  if (!post) return <div className="loading">Article not found.</div>

  return (
    <>
      <Link to="/" className="back-link">&larr; Back to articles</Link>

      {/* Hero */}
      {post.attrs.COVER_IMAGE && (
        <div className="post-hero">
          <img src={post.attrs.COVER_IMAGE as string} alt={post.title} />
          <div className="post-hero-overlay">
            {post.attrs.CATEGORY && (
              <span className="tag">{post.attrs.CATEGORY}</span>
            )}
            <h1>{post.attrs.TITLE || post.title}</h1>
            <div className="meta">{post.attrs.AUTHOR} &middot; {post.date}</div>
          </div>
        </div>
      )}

      {!post.attrs.COVER_IMAGE && (
        <div style={{ padding: "48px", maxWidth: 740, margin: "0 auto" }}>
          <h1 style={{ fontSize: 40, marginBottom: 8 }}>{post.attrs.TITLE || post.title}</h1>
          <div style={{ color: "#64748b", fontSize: 14, fontFamily: "-apple-system, sans-serif" }}>
            {post.attrs.AUTHOR} &middot; {post.attrs.CATEGORY} &middot; {post.date}
          </div>
        </div>
      )}

      {/* Content */}
      {post.attrs.CONTENT && (
        <div className="post-body">
          <div dangerouslySetInnerHTML={{ __html: post.attrs.CONTENT as string }} />
        </div>
      )}
    </>
  )
}
