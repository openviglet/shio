import { draftMode } from "next/headers";
import { NextResponse } from "next/server";
import { PREVIEW_SECRET } from "@/lib/shio";

/**
 * Draft-preview entry point (Shio roadmap SH9, riding on SH5 PREVIEW-env keys).
 *
 * Enable:  `/api/preview?secret=<SHIO_PREVIEW_SECRET>&url=/the/path`
 * Disable: `/api/preview?disable=1`
 *
 * Enabling turns on Next.js Draft Mode (a cookie); subsequent requests render
 * dynamically (SSR) through the PREVIEW CDA client (`getShioClient`), which
 * prefers draft rows over published ones. This is the "preview this draft" link
 * Shio's console hands the editor (SH13).
 */
export async function GET(request: Request): Promise<NextResponse> {
  const { searchParams } = new URL(request.url);
  const draft = await draftMode();

  if (searchParams.get("disable")) {
    draft.disable();
    return NextResponse.redirect(new URL(searchParams.get("url") ?? "/", request.url));
  }

  if (!PREVIEW_SECRET) {
    return NextResponse.json(
      { message: "SHIO_PREVIEW_SECRET is not configured" },
      { status: 500 }
    );
  }
  if (searchParams.get("secret") !== PREVIEW_SECRET) {
    return NextResponse.json({ message: "Invalid preview secret" }, { status: 401 });
  }

  draft.enable();

  // Redirect to the requested page (relative paths only, to avoid open redirects).
  const target = searchParams.get("url") ?? "/";
  const safeTarget = target.startsWith("/") ? target : "/";
  return NextResponse.redirect(new URL(safeTarget, request.url));
}
