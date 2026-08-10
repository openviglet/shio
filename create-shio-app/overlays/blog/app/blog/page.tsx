import Link from "next/link";
import type { Metadata } from "next";
import { getShioClient, SITE_ID } from "@/lib/shio";

/**
 * The post list the `blog` package was missing (SH216).
 *
 * The starter's catch-all renders every Article's own page perfectly well — an Article is an
 * ordinary post with an HTML body. What it cannot render is a page about the *model*: a reader
 * arriving at `/blog` wants the posts, newest first, and no per-post route can produce that.
 * That is why this package gets a page of its own rather than the section overlay, which would
 * ship a dependency it never imports.
 *
 * ISR, like the catch-all: a new post appears within the window, and a publish webhook to
 * `/api/revalidate` makes it immediate.
 */
export const revalidate = 60;

export const metadata: Metadata = {
  title: "Blog",
};

export default async function BlogIndex() {
  if (!SITE_ID) {
    return (
      <main>
        <p>Set SHIO_SITE_ID to list your posts.</p>
      </main>
    );
  }

  const client = await getShioClient();
  // By post type rather than by folder: an Article is an Article wherever a curator files it,
  // and a listing keyed on `/blog` would quietly drop the first one somebody moved.
  const listing = await client.query({ siteId: SITE_ID, postType: "Article", size: 50 });

  // `query` returns SUMMARIES — title, summary, furl, date — and no `attrs`. That is the SDK's
  // two-call design, not an omission: a listing that carried every body would spend the reader's
  // bandwidth on content this page does not show. So the sort key is the post's `date` rather
  // than the Article's own `publishedAt` field, and the author is on the post's page where the
  // body is. Fetching 50 posts to print 50 bylines is the N+1 that design exists to refuse.
  const posts = [...listing.posts].sort((a, b) => (b.date ?? "").localeCompare(a.date ?? ""));

  return (
    <main>
      <h1>Blog</h1>
      {posts.length === 0 && <p>No posts yet.</p>}
      <ul>
        {posts.map((post) => (
          <li key={post.id}>
            <h2>
              <Link href={post.furl ?? "#"}>{post.title}</Link>
            </h2>
            {post.summary && <p>{post.summary}</p>}
            {post.date && <time dateTime={post.date}>{post.date.slice(0, 10)}</time>}
          </li>
        ))}
      </ul>
    </main>
  );
}
