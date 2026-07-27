import type { ShioClient, ShioPost } from "@viglet/shio-client";
import type { SectionPost } from "@viglet/shio-sections";

/**
 * Turns a `Page`'s `sections` field into the resolved sections `<Sections>` renders.
 *
 * A page names its sections by **friendly URL, in order** (SH95/SH154) — not by id, so
 * nothing has to carry a UUID between calls, and not as a Relator, because sections are
 * deliberately heterogeneous. Resolving those URLs is the app's job rather than the
 * vocabulary's: a component that fetched would tie the vocabulary to one data-loading
 * strategy and be unusable from a server component, a loader or a static build.
 *
 * A URL that resolves to nothing is **dropped**, not rendered as a placeholder. It is a
 * real defect and it has an owner — `shio verify`'s `dangling-reference` rule reports it
 * by address — so putting the CMS's diagnostics on a visitor's page would be reporting it
 * twice, in the one place nobody can act on it.
 */
export async function resolveSections(
  client: ShioClient,
  siteId: string,
  page: ShioPost,
): Promise<SectionPost[]> {
  const urls = sectionUrls(page);
  if (urls.length === 0) return [];

  // In parallel: the list is short (a page is a handful of sections) and they are
  // independent, so N round trips should cost one round trip's latency.
  const resolved = await Promise.all(
    urls.map((url) => client.getPostByUrl(siteId, url).catch(() => null)),
  );

  const sections: SectionPost[] = [];
  for (const [index, post] of resolved.entries()) {
    if (!post?.postType) continue;
    sections.push({ type: post.postType, url: urls[index], data: post.attrs ?? {} });
  }
  return sections;
}

/**
 * The URLs a page declares, in order.
 *
 * Tolerant of both shapes a `Multi Select` arrives in — an array, or the single string a
 * one-value field can come back as — because a page with exactly one section is the most
 * common page there is and it must not render blank.
 */
export function sectionUrls(page: ShioPost): string[] {
  const value = (page.attrs ?? {})["sections"];
  const raw = Array.isArray(value) ? value : typeof value === "string" ? [value] : [];
  return raw
    .filter((entry): entry is string => typeof entry === "string" && entry.trim() !== "")
    .map((entry) => (entry.startsWith("/") ? entry : `/${entry}`));
}

/** True when this page is composed of sections rather than a single HTML field. */
export function isSectionPage(page: ShioPost): boolean {
  return sectionUrls(page).length > 0;
}
