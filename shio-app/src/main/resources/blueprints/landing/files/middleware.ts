import { NextResponse, type NextRequest } from "next/server";
import { cdaRedirects, redirectLookup } from "./lib/redirects";

/**
 * Answers a path that moved with the redirect written for it in Shio (roadmap SH1133).
 *
 * Shio's own delivery honours its redirects; this site does not go through that delivery, so the
 * table is consulted here, before any page is looked up. A redirect's source is its own address in
 * Shio, so it never shadows a page: the CMS refuses a redirect over a page that exists.
 */
const moved = redirectLookup(
  cdaRedirects(
    process.env.SHIO_URL ?? "http://localhost:2710",
    process.env.SHIO_SITE_ID ?? "",
    process.env.SHIO_CDA_TOKEN
  )
);

export async function middleware(request: NextRequest) {
  const found = await moved(request.nextUrl.pathname);
  if (!found) return NextResponse.next();
  // A relative destination is on this site; an absolute one is passed through as written.
  const destination = new URL(found.destination, request.url);
  if (!destination.search) destination.search = request.nextUrl.search;
  return NextResponse.redirect(destination, found.status);
}

export const config = {
  // Everything but Next's own assets and this app's route handlers; a moved path may end in `.html`.
  matcher: ["/((?!_next/|api/).*)"],
};
