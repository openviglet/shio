# Next.js rendering modes against the Shio CDA (SH9)

> How to render a Shio-backed Next.js site in each mode (SSG, ISR, SSR draft
> preview) and how to wire publish events to **on-demand revalidation** so a
> statically-generated site reflects edits within seconds.
>
> Everything here is shipped in the [`create-shio-app`](../../create-shio-app)
> starter; this doc is the reference behind it. → ROADMAP **SH9**, IMPROVEMENTS §II.4.

The whole chain is JavaScript/TypeScript: the page code talks to the
[`@viglet/shio-client`](../../shio-client) core, which consumes the
[CDA](specs/SH1-cda-read-contract.md) (`/api/v2/cda/**`). No Java.

---

## 1. SSG: static at build time

Pre-render known friendly URLs at `next build` with `generateStaticParams`:

```ts
// app/[...slug]/page.tsx
export async function generateStaticParams() {
  const { posts } = await shio.query({ siteId: SITE_ID, size: 500 });
  return posts
    .filter((p) => p.furl)
    .map((p) => ({ slug: p.furl!.replace(/^\//, "").split("/") }));
}
```

Fastest response, fully cacheable on a CDN. Best for content that changes rarely
and whose URL set is known at build time.

## 2. ISR: incremental static regeneration

Add a revalidation window. Pages render on-demand on first hit, then serve from
cache and re-generate at most once per window:

```ts
export const revalidate = 60; // seconds
```

ISR is the sane default: static speed with a bounded staleness. The trade-off is
latency-to-freshness equal to the window. To remove that trade-off, add §3.

## 3. On-demand revalidation: instant updates

Drive freshness from Shio's publish pipeline instead of a timer. The Next.js app
exposes a signed webhook receiver; Shio calls it on publish/unpublish/delete; the
receiver drops exactly the affected page(s) from the ISR cache.

```ts
// app/api/revalidate/route.ts (Node runtime)
export async function POST(request: Request) {
  const raw = await request.text();
  if (!verifySignature(raw, request.headers.get("x-shio-signature"), SECRET)) {
    return NextResponse.json({ revalidated: false }, { status: 401 });
  }
  const { url, tags } = JSON.parse(raw);
  if (url) revalidatePath(url);
  revalidatePath("/");            // home / listing
  for (const t of tags ?? []) revalidateTag(t);
  return NextResponse.json({ revalidated: true });
}
```

`revalidatePath` works without any extra plumbing because the webhook carries the
post's friendly URL. `revalidateTag` is available too, but only affects fetches
you explicitly tagged with `fetch(url, { next: { tags: [...] } })`.

### Webhook contract (what SH12 emits)

The publish pipeline (**SH12**, outbound webhooks) MUST POST this shape so any
`create-shio-app` site revalidates without per-site glue:

| Field | Type | Notes |
|---|---|---|
| `event` | `"publish" \| "unpublish" \| "delete"` | the transition |
| `siteId` | string | the post's site |
| `postId` | string | the post's id |
| `url` | string | the post's friendly URL, leading slash (e.g. `/blog/hello`), preferred; drives `revalidatePath` |
| `tags` | string[] | optional cache tags to invalidate |
| `occurredAt` | string | optional ISO timestamp |

**Signing.** The raw request body is HMAC-SHA256'd with the shared secret
(`SHIO_WEBHOOK_SECRET` on the Next side) and sent as:

```
x-shio-signature: sha256=<hex digest>
```

The receiver recomputes the digest and compares in constant time
(`crypto.timingSafeEqual`), rejecting mismatches with `401`. Until SH12 ships the
emitter you can exercise the endpoint manually:

```bash
BODY='{"event":"publish","siteId":"<id>","url":"/blog/hello"}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SHIO_WEBHOOK_SECRET" -hex | awk '{print $2}')
curl -X POST http://localhost:3000/api/revalidate \
  -H "content-type: application/json" \
  -H "x-shio-signature: sha256=$SIG" \
  -d "$BODY"
```

## 4. SSR draft preview

Render unpublished content on demand, before it's live. This builds on **SH5**'s
per-environment keys: a `PREVIEW`-env CDA token reads draft-preferred content
(prefers the `DRAFT` row, falls back to `PUBLISHED`), while the normal `PROD`
token only ever sees `PUBLISHED`.

1. Configure `SHIO_PREVIEW_TOKEN` (a PREVIEW-env token) + `SHIO_PREVIEW_SECRET`.
2. `lib/shio.ts` exposes `getShioClient()`, which returns the preview client when
   Next.js **Draft Mode** is on and the PROD client otherwise.
3. `app/api/preview` enables Draft Mode and redirects to the page; with Draft
   Mode on, the route renders dynamically (SSR) through the preview client.

```
Enable (static secret):  /api/preview?secret=<SHIO_PREVIEW_SECRET>&url=/blog/hello
Enable (console token):  /api/preview?token=<minted-token>&url=/blog/hello
Disable:                 /api/preview?disable=1
```

**SH13** is the console-issued form: the editor clicks **Preview Draft** on a
post and Shio mints a short-lived, single-site `PREVIEW`-env token, opening
`/api/preview?token=…`. The route stashes that token in an httpOnly cookie and
`getShioClient()` reads draft-preferred through it for the session; the token
expires server-side on its own (no shared secret to leak, nothing to revoke).
The static-`secret` form (with the long-lived `SHIO_PREVIEW_TOKEN`) still works
for a permanent preview *deployment*.

---

## Choosing a mode

| Need | Mode |
|---|---|
| Marketing pages, rarely change | SSG |
| Mostly static, tolerate N-second staleness | ISR |
| Static speed **and** instant publish | ISR + on-demand revalidation (§3) |
| Show unpublished drafts to editors | SSR draft preview (§4) |

They compose: a site is typically SSG/ISR for visitors, revalidated on publish,
with Draft Mode flipping individual requests to SSR-preview for editors.
