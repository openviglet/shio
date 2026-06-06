import { notFound } from "next/navigation";
import { draftMode } from "next/headers";
import type { Metadata } from "next";
import { getShioClient, shio, SITE_ID, slugToUrl } from "@/lib/shio";

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
