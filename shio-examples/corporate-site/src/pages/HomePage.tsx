import {
  useShioPost,
  useShioQuery,
  useShioSites,
  type ShioPostSummary,
} from "@viglet/shio-react-sdk"

const SITE_NAME = "Viglet Corporate"
const HOMEPAGE_ID = "d1e2f3a4-0001-4000-d000-000000000001"

export function HomePage() {
  const { data: page, loading: pageLoading } = useShioPost(HOMEPAGE_ID)

  // The CDA query filters by site id, not name — resolve it from the sites list.
  // Gate the query on the resolved site id so it never fires with siteId=undefined.
  const { data: sites, loading: sitesLoading } = useShioSites()
  const siteId = sites?.find((s) => s.name === SITE_NAME)?.id
  const { data: listing, loading: servicesLoading } = useShioQuery(
    { siteId, postType: "Service" },
    !!siteId
  )

  if (pageLoading) return <div className="loading">Loading...</div>

  const attrs = (page?.attrs ?? {}) as Record<string, string>
  const services = listing?.posts ?? []

  return (
    <>
      {/* Hero Section */}
      <section className="hero" id="home">
        <div className="hero-content">
          <h1>{attrs.TITLE || "Welcome to Viglet"}</h1>
          <p className="subtitle">{attrs.SUBTITLE || "Building the future of content management"}</p>
          <a className="hero-cta" href={attrs.CTA_URL || "/services"}>
            {attrs.CTA_TEXT || "Get Started"}
          </a>
        </div>
        {attrs.HERO_IMAGE && (
          <div className="hero-image">
            <img src={attrs.HERO_IMAGE} alt="Hero" />
          </div>
        )}
      </section>

      {/* Body Content */}
      {attrs.BODY && (
        <section className="body-section">
          <div dangerouslySetInnerHTML={{ __html: attrs.BODY }} />
        </section>
      )}

      {/* Services Section */}
      <section className="services" id="services">
        <div className="services-header">
          <h2>Our Services</h2>
          <p>Everything you need to build and manage modern digital experiences.</p>
        </div>
        <div className="services-grid">
          {sitesLoading || servicesLoading ? (
            <div className="loading">Loading services...</div>
          ) : (
            services.map((svc) => <ServiceCard key={svc.id} service={svc} />)
          )}
        </div>
      </section>
    </>
  )
}

/**
 * A listing item carries metadata only, so the card hydrates the full post to
 * render the icon and description from its attributes.
 */
function ServiceCard({ service }: Readonly<{ service: ShioPostSummary }>) {
  const { data: post } = useShioPost(service.id)
  const attrs = (post?.attrs ?? {}) as Record<string, string>

  return (
    <div className="service-card">
      <div className="service-icon">{attrs.ICON || "⚡"}</div>
      <h3>{attrs.TITLE || service.title}</h3>
      <p>{attrs.DESCRIPTION || service.summary}</p>
    </div>
  )
}
