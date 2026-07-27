import { notFound } from "next/navigation";
import { draftMode } from "next/headers";
import type { Metadata } from "next";
import { Sections } from "@viglet/shio-sections";
import { getShioClient, shio, SITE_ID, slugToUrl } from "@/lib/shio";
import { isSectionPage, resolveSections } from "@/lib/sections";

// ISR: pages not pre-rendered at build are rendered on-demand and cached for
// 60s. For instant updates, wire publish webhooks to `/api/revalidate` (SH9) —
// then this fixed window is just a fallback. In Draft Mode the route renders
// dynamically (SSR) and this value is ignored.
export const revalidate = 60;

type Params = { slug?: string[] };

/**
 * Pre-render every published post's friendly URL at build time (SSG). Pages not
 * listed here are still rendered on-demand and cached (ISR via `revalidate`).
 */
export async function generateStaticParams(): Promise<Params[]> {
  if (!SITE_ID) return [];
  const listing = await shio.query({ siteId: SITE_ID, size: 500 });
  return listing.posts
    .filter((p) => p.furl)
    .map((p) => ({ slug: p.furl!.replace(/^\//, "").split("/") }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<Params>;
}): Promise<Metadata> {
  const { slug } = await params;
  if (!SITE_ID) return {};
  const client = await getShioClient();
  const post = await client.getPostByUrl(SITE_ID, slugToUrl(slug));
  if (!post) return {};
  return {
    title: post.title ?? undefined,
    description: post.summary ?? undefined,
  };
}

/**
 * The catch-all route, section-aware.
 *
 * Two kinds of page reach it and it tells them apart by what the content declares, not
 * by which blueprint built the site: a page whose `sections` field names other posts is
 * composed with `<Sections>`, and anything else falls back to the single HTML field the
 * plain starter renders. That fallback is why one route can serve both — a `blog`'s
 * `Article` and a `landing`'s `Page` are the same request with different content.
 */
export default async function CatchAllPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { slug } = await params;
  if (!SITE_ID) notFound();

  // PREVIEW client when Draft Mode is on (SSR draft preview), PROD otherwise.
  const { isEnabled: isDraft } = await draftMode();
  const client = await getShioClient();

  const post = await client.getPostByUrl(SITE_ID, slugToUrl(slug));
  if (!post) notFound();

  if (isSectionPage(post)) {
    const sections = await resolveSections(client, SITE_ID, post);
    return (
      <>
        {isDraft && <DraftBanner />}
        <Sections sections={sections} />
      </>
    );
  }

  // `attrs` is the post-type field map (e.g. content, body, image...). This
  // starter renders a `content`/`body`/`text` HTML field if present; adapt to
  // your own post types.
  const html = pickHtml(post.attrs);

  return (
    <article>
      {isDraft && <DraftBanner />}
      <h1>{post.title}</h1>
      {post.summary && <p style={{ color: "#666" }}>{post.summary}</p>}
      {html ? (
        <div dangerouslySetInnerHTML={{ __html: html }} />
      ) : (
        <pre
          style={{
            background: "#f6f6f6",
            padding: "1rem",
            borderRadius: 8,
            overflow: "auto",
          }}
        >
          {JSON.stringify(post.attrs, null, 2)}
        </pre>
      )}
    </article>
  );
}

function pickHtml(attrs: Record<string, unknown>): string | null {
  for (const key of ["content", "body", "text", "html"]) {
    const value = attrs[key];
    if (typeof value === "string" && value.trim()) return value;
  }
  return null;
}

/** Shown while Draft Mode is active; links to disable it. */
function DraftBanner() {
  return (
    <div
      style={{
        background: "#fff7ed",
        border: "1px solid #fdba74",
        borderRadius: 8,
        padding: "0.5rem 0.75rem",
        marginBottom: "1rem",
        fontSize: "0.85rem",
      }}
    >
      Draft preview is on.{" "}
      <a href="/api/preview?disable=1">Exit preview</a>
    </div>
  );
}
