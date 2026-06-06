import { ShioClient } from "@viglet/shio-client";

/**
 * Shared Shio Content Delivery API client.
 *
 * Reads connection details from the environment (see `.env.local`). Safe to
 * import from any Server Component, Route Handler, or `generateStaticParams`.
 */
export const shio = new ShioClient({
  baseUrl: process.env.SHIO_URL ?? "http://localhost:2710",
  apiToken: process.env.SHIO_CDA_TOKEN,
});

/** The site this front-end renders. */
export const SITE_ID = process.env.SHIO_SITE_ID ?? "";

/** Normalize Next's `slug[]` into a CDA friendly-URL path (always leading slash). */
export function slugToUrl(slug?: string[]): string {
  return "/" + (slug ?? []).join("/");
}
