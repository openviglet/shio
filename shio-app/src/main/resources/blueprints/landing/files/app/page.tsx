import Link from "next/link";
import { getShioClient, SITE_ID } from "@/lib/shio";

// ISR: re-fetch from the CDA at most once per minute. Pair with publish
// webhooks + on-demand revalidation (`/api/revalidate`) for instant updates
// (see Shio roadmap SH9/SH12). In Draft Mode this renders dynamically (SSR).
export const revalidate = 60;

export default async function HomePage() {
  if (!SITE_ID) {
    return <SetupHint />;
  }

  const shio = await getShioClient();
  const site = await shio.getSite(SITE_ID);
  const listing = await shio.query({ siteId: SITE_ID, size: 50 });

  return (
    <>
      <header style={{ marginBottom: "2rem" }}>
        <h1 style={{ marginBottom: 4 }}>{site?.name ?? "Shio site"}</h1>
        {site?.description && (
          <p style={{ color: "#666", marginTop: 0 }}>{site.description}</p>
        )}
      </header>

      <h2 style={{ fontSize: "1rem", textTransform: "uppercase", color: "#888" }}>
        Published content
      </h2>

      {listing.posts.length === 0 ? (
        <p style={{ color: "#666" }}>
          No published posts yet. Publish content in Shio, then refresh.
        </p>
      ) : (
        <ul style={{ listStyle: "none", padding: 0 }}>
          {listing.posts.map((post) => (
            <li key={post.id} style={{ padding: "0.75rem 0", borderBottom: "1px solid #eee" }}>
              {post.furl ? (
                <Link href={post.furl}>{post.title ?? post.furl}</Link>
              ) : (
                <span>{post.title ?? post.id}</span>
              )}
              {post.summary && (
                <p style={{ margin: "4px 0 0", color: "#666", fontSize: "0.9rem" }}>
                  {post.summary}
                </p>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function SetupHint() {
  return (
    <>
      <h1>Welcome to your Shio site 👋</h1>
      <p>
        Set <code>SHIO_SITE_ID</code> (and <code>SHIO_URL</code> /{" "}
        <code>SHIO_CDA_TOKEN</code>) in <code>.env.local</code>, then restart{" "}
        <code>npm run dev</code>.
      </p>
      <p style={{ color: "#666" }}>
        This starter talks to the Shio Content Delivery API through{" "}
        <code>@viglet/shio-client</code> — no Java, just TypeScript.
      </p>
    </>
  );
}
