import Link from "next/link";
import type { Metadata } from "next";
import type { ShioFolder, ShioPostSummary } from "@viglet/shio-client";
import { getShioClient, SITE_ID } from "@/lib/shio";

/**
 * The navigation tree the `docs` package was missing (SH216).
 *
 * A DocPage renders fine on its own route — it is an ordinary post with an HTML body. What no
 * per-page route can produce is the thing a documentation reader needs first: **where they are
 * in the set**. The tree is the model's shape, so it belongs to this package and not to the
 * starter's catch-all.
 */
export const revalidate = 60;

export const metadata: Metadata = {
  title: "Documentation",
};

/** One section of the tree: a folder and the pages directly inside it. */
type Section = { folder?: ShioFolder; pages: ShioPostSummary[] };

export default async function DocsIndex() {
  if (!SITE_ID) {
    return (
      <main>
        <p>Set SHIO_SITE_ID to build the documentation tree.</p>
      </main>
    );
  }

  const client = await getShioClient();
  const root = await client.query({ siteId: SITE_ID, postType: "DocPage", size: 200 });

  // One request per subfolder, in parallel. The tree is a handful of folders — the same trade
  // `resolveSections` makes for a page's sections — and doing it serially would make the page's
  // latency the sum of its branches rather than the slowest one.
  const branches = await Promise.all(
    (root.folders ?? []).map(async (folder): Promise<Section> => ({
      folder,
      pages: (await client.query({ siteId: SITE_ID, folderId: folder.id, postType: "DocPage", size: 200 })).posts,
    })),
  );

  // The DocPage model orders pages by a `weight` field, which lives in `attrs` and a listing does
  // not carry. Reading 200 posts to sort them would cost the whole tree's bodies to print its
  // titles, so this sorts on `title` and the weight orders the pages a reader actually opens.
  // Stated rather than silently different: a curator who sets `weight` should know where it acts.
  const byTitle = (a: ShioPostSummary, b: ShioPostSummary) =>
    (a.title ?? "").localeCompare(b.title ?? "");

  const sections: Section[] = [{ pages: root.posts }, ...branches];

  return (
    <main>
      <h1>Documentation</h1>
      {sections.every((section) => section.pages.length === 0) && <p>No pages yet.</p>}
      {sections.map((section) => (
        <section key={section.folder?.id ?? "root"}>
          {section.folder && <h2>{section.folder.name}</h2>}
          <ul>
            {[...section.pages].sort(byTitle).map((page) => (
              <li key={page.id}>
                <Link href={page.furl ?? "#"}>{page.title}</Link>
                {page.summary && <span> — {page.summary}</span>}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </main>
  );
}
