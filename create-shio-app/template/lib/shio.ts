import { cookies, draftMode } from "next/headers";
import { ShioClient } from "@viglet/shio-client";

const SHIO_BASE_URL = process.env.SHIO_URL ?? "http://localhost:2710";

/** httpOnly cookie holding a console-minted, short-lived preview token (SH13). */
export const PREVIEW_TOKEN_COOKIE = "shio-preview-token";

/** A draft-preferred client bound to a specific (short-lived) preview token. */
function previewClientFor(token: string): ShioClient {
  return new ShioClient({ baseUrl: SHIO_BASE_URL, apiToken: token });
}

/**
 * Shared Shio Content Delivery API clients.
 *
 * Reads connection details from the environment (see `.env.local`). Safe to
 * import from any Server Component, Route Handler, or `generateStaticParams`.
 *
 * Two clients are exposed:
 *   - `shio`        — a PROD-env CDA key; serves PUBLISHED content (SSG/ISR).
 *   - `shioPreview` — a PREVIEW-env CDA key; serves draft-preferred content (SSR
 *                     draft preview, see `app/api/preview`). `null` unless
 *                     `SHIO_PREVIEW_TOKEN` is set.
 *
 * Use {@link getShioClient} to pick the right one for the current request — it
 * honours Next.js Draft Mode automatically.
 */
export const shio = new ShioClient({
  baseUrl: process.env.SHIO_URL ?? "http://localhost:2710",
  apiToken: process.env.SHIO_CDA_TOKEN,
});

/** The PREVIEW-env client (draft-preferred reads), or `null` if not configured. */
export const shioPreview: ShioClient | null = process.env.SHIO_PREVIEW_TOKEN
  ? new ShioClient({
      baseUrl: process.env.SHIO_URL ?? "http://localhost:2710",
      apiToken: process.env.SHIO_PREVIEW_TOKEN,
    })
  : null;

/**
 * Pick the CDA client for the current request. Under Next.js Draft Mode, prefer
 * a console-minted short-lived preview token (SH13) from the request cookie;
 * otherwise fall back to the long-lived PREVIEW server client (SH9). With Draft
 * Mode off, always the PROD client. Call from Server Components / Route Handlers.
 */
export async function getShioClient(): Promise<ShioClient> {
  const { isEnabled } = await draftMode();
  if (isEnabled) {
    const token = (await cookies()).get(PREVIEW_TOKEN_COOKIE)?.value;
    if (token) return previewClientFor(token);
    if (shioPreview) return shioPreview;
  }
  return shio;
}

/** The site this front-end renders. */
export const SITE_ID = process.env.SHIO_SITE_ID ?? "";

/** Shared secret the publish webhook signs its body with (HMAC-SHA256, SH12). */
export const WEBHOOK_SECRET = process.env.SHIO_WEBHOOK_SECRET ?? "";

/** Secret that gates the draft-preview entry point (`app/api/preview`). */
export const PREVIEW_SECRET = process.env.SHIO_PREVIEW_SECRET ?? "";

/** Normalize Next's `slug[]` into a CDA friendly-URL path (always leading slash). */
export function slugToUrl(slug?: string[]): string {
  return "/" + (slug ?? []).join("/");
}
