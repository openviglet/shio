import { createHmac, timingSafeEqual } from "node:crypto";
import { revalidatePath, revalidateTag } from "next/cache";
import { NextResponse } from "next/server";
import { WEBHOOK_SECRET } from "@/lib/shio";

// Webhook receiver — runs on the Node runtime (needs `node:crypto`).
export const runtime = "nodejs";

/**
 * On-demand revalidation endpoint (Shio roadmap SH9).
 *
 * Shio's publish pipeline (SH12) POSTs here on publish / unpublish / delete. The
 * body is HMAC-SHA256-signed with `SHIO_WEBHOOK_SECRET`; we verify it, then ask
 * Next.js to drop the affected page(s) from the ISR cache so the next request
 * re-renders fresh content — turning minutes-of-staleness ISR into seconds.
 *
 * Expected payload (the contract SH12 emits — see docs/recipes/nextjs-rendering.md):
 *   {
 *     "event":   "publish" | "unpublish" | "delete",
 *     "siteId":  "<site id>",
 *     "postId":  "<post id>",
 *     "url":     "/the/friendly-url",   // optional but preferred
 *     "tags":    ["optional", "cache", "tags"]
 *   }
 *
 * Signature header: `x-shio-signature: sha256=<hex hmac of the raw body>`.
 */
export async function POST(request: Request): Promise<NextResponse> {
  if (!WEBHOOK_SECRET) {
    return NextResponse.json(
      { revalidated: false, message: "SHIO_WEBHOOK_SECRET is not configured" },
      { status: 500 }
    );
  }

  const raw = await request.text();
  const signature = request.headers.get("x-shio-signature");
  if (!verifySignature(raw, signature, WEBHOOK_SECRET)) {
    return NextResponse.json(
      { revalidated: false, message: "Invalid signature" },
      { status: 401 }
    );
  }

  let payload: WebhookPayload;
  try {
    payload = JSON.parse(raw) as WebhookPayload;
  } catch {
    return NextResponse.json(
      { revalidated: false, message: "Invalid JSON body" },
      { status: 400 }
    );
  }

  const revalidated: string[] = [];

  // Drop the affected page itself plus the home/listing page.
  if (payload.url) {
    revalidatePath(payload.url);
    revalidated.push(`path:${payload.url}`);
  }
  revalidatePath("/");
  revalidated.push("path:/");

  // Honour any explicit cache tags (pair with `fetch(..., { next: { tags } })`).
  for (const tag of payload.tags ?? []) {
    revalidateTag(tag);
    revalidated.push(`tag:${tag}`);
  }

  return NextResponse.json({ revalidated: true, event: payload.event, dropped: revalidated });
}

type WebhookPayload = {
  event?: string;
  siteId?: string;
  postId?: string;
  url?: string;
  tags?: string[];
};

/** Constant-time verification of `sha256=<hex>` against HMAC-SHA256(raw, secret). */
function verifySignature(
  raw: string,
  header: string | null,
  secret: string
): boolean {
  if (!header) return false;
  const provided = header.startsWith("sha256=") ? header.slice(7) : header;
  const expected = createHmac("sha256", secret).update(raw).digest("hex");
  // timingSafeEqual throws if lengths differ — guard first.
  const a = Buffer.from(provided, "hex");
  const b = Buffer.from(expected, "hex");
  if (a.length !== b.length || a.length === 0) return false;
  return timingSafeEqual(a, b);
}
