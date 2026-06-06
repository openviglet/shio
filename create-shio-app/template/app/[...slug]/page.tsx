import { notFound } from "next/navigation";
import type { Metadata } from "next";
import { shio, SITE_ID, slugToUrl } from "@/lib/shio";

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
  const post = SITE_ID ? await shio.getPostByUrl(SITE_ID, slugToUrl(slug)) : null;
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

  const post = await shio.getPostByUrl(SITE_ID, slugToUrl(slug));
  if (!post) notFound();

  // `attrs` is the post-type field map (e.g. content, body, image...). This
  // starter renders a `content`/`body`/`text` HTML field if present; adapt to
  // your own post types.
  const html = pickHtml(post.attrs);

  return (
    <article>
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
