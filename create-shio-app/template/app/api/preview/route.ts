import { cookies, draftMode } from "next/headers";
import { NextResponse } from "next/server";
import { PREVIEW_SECRET, PREVIEW_TOKEN_COOKIE } from "@/lib/shio";

/**
 * Draft-preview entry point (Shio roadmap SH9 + SH13, riding on SH5 PREVIEW-env keys).
 *
 * Two ways in:
 *   - Console-issued, short-lived token (SH13):
 *       `/api/preview?token=<minted>&url=/the/path`
 *     Shio's console mints an expiring PREVIEW-env CDA token and hands the editor
 *     this link. The token is stashed in an httpOnly cookie and used per-request
 *     by `getShioClient()`; it stops working on its own when it expires server-side.
 *   - Static shared secret (SH9):
 *       `/api/preview?secret=<SHIO_PREVIEW_SECRET>&url=/the/path`
 *     Falls back to the long-lived `SHIO_PREVIEW_TOKEN` server client.
 *
 * Disable: `/api/preview?disable=1`
 *
 * Enabling turns on Next.js Draft Mode (a cookie); subsequent requests render
 * dynamically (SSR) through the PREVIEW CDA client, which prefers draft rows
 * over published ones.
 */
export async function GET(request: Request): Promise<NextResponse> {
  const { searchParams } = new URL(request.url);
  const draft = await draftMode();
  const cookieStore = await cookies();

  if (searchParams.get("disable")) {
    draft.disable();
    cookieStore.delete(PREVIEW_TOKEN_COOKIE);
    return NextResponse.redirect(new URL(searchParams.get("url") ?? "/", request.url));
  }

  // Redirect target — relative paths only, to avoid open redirects.
  const target = searchParams.get("url") ?? "/";
  const safeTarget = target.startsWith("/") ? target : "/";

  const token = searchParams.get("token");
  if (token) {
    // SH13: trust the console-minted token; the backend enforces its expiry and
    // single-site scope. Stash it httpOnly so getShioClient() reads draft-preferred.
    draft.enable();
    cookieStore.set(PREVIEW_TOKEN_COOKIE, token, {
      httpOnly: true,
      sameSite: "lax",
      path: "/",
      maxAge: 60 * 60, // 1h ceiling; the token itself may expire sooner server-side
    });
    return NextResponse.redirect(new URL(safeTarget, request.url));
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
  return NextResponse.redirect(new URL(safeTarget, request.url));
}
