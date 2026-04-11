import { useShioPost } from "@viglet/shio-react-sdk"

const ABOUT_ID = "d1e2f3a4-0001-4000-d000-000000000002"

export function AboutPage() {
  const { post: page, loading } = useShioPost(ABOUT_ID)

  if (loading) return <div className="loading">Loading...</div>

  return (
    <>
      {/* About Hero */}
      <section className="hero" style={{ textAlign: "center", padding: "80px 48px" }}>
        <h1>{page?.attrs.TITLE || "About Viglet"}</h1>
        <p className="subtitle" style={{ margin: "0 auto 32px" }}>
          {page?.attrs.SUBTITLE || "Our mission is to simplify content management"}
        </p>
        {page?.attrs.HERO_IMAGE && (
          <img
            src={page.attrs.HERO_IMAGE as string}
            alt="About"
            style={{
              width: "100%", maxWidth: 800, height: 360, objectFit: "cover",
              borderRadius: 16, boxShadow: "0 20px 60px rgba(0,0,0,0.1)",
            }}
          />
        )}
      </section>

      {/* About Body */}
      {page?.attrs.BODY && (
        <section className="body-section">
          <div dangerouslySetInnerHTML={{ __html: page.attrs.BODY as string }} />
        </section>
      )}

      {/* CTA */}
      {page?.attrs.CTA_TEXT && (
        <section style={{ textAlign: "center", padding: "60px 48px", background: "#fafafa" }}>
          <a className="hero-cta" href="/services">
            {page.attrs.CTA_TEXT}
          </a>
        </section>
      )}
    </>
  )
}
