import { useShioPost, useShioQuery } from "@viglet/shio-react-sdk"

const SITE_NAME = "viglet_corporate"
const HOMEPAGE_ID = "d1e2f3a4-0001-4000-d000-000000000001"

export function HomePage() {
  const { post: page, loading: pageLoading } = useShioPost(HOMEPAGE_ID)
  const { posts: services, loading: servicesLoading } = useShioQuery("Service", { siteName: SITE_NAME })

  if (pageLoading) return <div className="loading">Loading...</div>

  return (
    <>
      {/* Hero Section */}
      <section className="hero" id="home">
        <div className="hero-content">
          <h1>{page?.attrs.TITLE || "Welcome to Viglet"}</h1>
          <p className="subtitle">{page?.attrs.SUBTITLE || "Building the future of content management"}</p>
          <a className="hero-cta" href={page?.attrs.CTA_URL as string || "/services"}>
            {page?.attrs.CTA_TEXT || "Get Started"}
          </a>
        </div>
        {page?.attrs.HERO_IMAGE && (
          <div className="hero-image">
            <img src={page.attrs.HERO_IMAGE as string} alt="Hero" />
          </div>
        )}
      </section>

      {/* Body Content */}
      {page?.attrs.BODY && (
        <section className="body-section">
          <div dangerouslySetInnerHTML={{ __html: page.attrs.BODY as string }} />
        </section>
      )}

      {/* Services Section */}
      <section className="services" id="services">
        <div className="services-header">
          <h2>Our Services</h2>
          <p>Everything you need to build and manage modern digital experiences.</p>
        </div>
        <div className="services-grid">
          {servicesLoading ? (
            <div className="loading">Loading services...</div>
          ) : (
            services.map((svc) => (
              <div key={svc.id} className="service-card">
                <div className="service-icon">{svc.attrs.ICON || "⚡"}</div>
                <h3>{svc.attrs.TITLE || svc.title}</h3>
                <p>{svc.attrs.DESCRIPTION || svc.summary}</p>
              </div>
            ))
          )}
        </div>
      </section>
    </>
  )
}
