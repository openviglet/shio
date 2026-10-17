# {{projectName}}

A [Next.js](https://nextjs.org) (App Router) site powered by **Viglet Shio** via the
Content Delivery API — headless, TypeScript-first, **zero Java**.

## Getting started

1. Set your connection details in `.env.local`:

   ```bash
   SHIO_URL=https://your-shio-instance
   SHIO_SITE_ID=<your site id>
   SHIO_CDA_TOKEN=<your CDA token>
   ```

2. Run the dev server:

   ```bash
   npm run dev
   ```

3. Open [http://localhost:3000](http://localhost:3000).

## How it works

- [`lib/shio.ts`](lib/shio.ts) creates a shared [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) instance from the environment.
- [`app/page.tsx`](app/page.tsx) lists the site's published posts (`shio.query`).
- [`app/[...slug]/page.tsx`](app/[...slug]/page.tsx) resolves any path to a post via `shio.getPostByUrl(siteId, "/the/path")` — friendly-URL routing straight from the CDA, with `generateStaticParams` (SSG) + `revalidate` (ISR).
- [`middleware.ts`](middleware.ts) answers a path that moved. Redirects written in Shio (a `Redirect` post, or keeping an old address working when a page is renamed) come from the CDA's `/site/{siteId}/redirects` table, held for 60 seconds, and a moved path gets its `301` or `302` before any page is looked up. The logic is in [`lib/redirects.ts`](lib/redirects.ts).

All content comes from the Shio CDA (`/api/v2/cda/**`). You never touch the Java backend — just edit React/TypeScript.

## Rendering modes

This starter ships all three Next.js rendering modes against the CDA. Pick per
route — they compose freely.

### SSG — static at build time

`generateStaticParams` in [`app/[...slug]/page.tsx`](app/[...slug]/page.tsx)
pre-renders every published post's friendly URL at `next build`. Fastest
possible response; ideal for content that changes rarely.

### ISR — incremental static regeneration

`export const revalidate = 60` caches a rendered page and re-generates it at most
once per minute on the next request. Pages not covered by `generateStaticParams`
are rendered on-demand and then cached the same way. Tune the window per route.

### On-demand revalidation — instant updates

Instead of waiting out the ISR window, Shio can push the moment content is
published. [`app/api/revalidate/route.ts`](app/api/revalidate/route.ts) receives
a signed webhook and calls `revalidatePath` so the affected page re-renders on
its next hit — staleness drops from minutes to seconds.

1. Set `SHIO_WEBHOOK_SECRET` in `.env.local`.
2. In Shio, add a publish webhook for this site pointing at
   `https://your-site/api/revalidate` with the same secret (HMAC-SHA256).

The webhook body is `{ event, siteId, postId, url, tags? }`, signed as
`x-shio-signature: sha256=<hex>`. See the full contract in
[docs/recipes/nextjs-rendering.md](https://github.com/openviglet/shio/blob/main/docs/recipes/nextjs-rendering.md).

### SSR draft preview

Editors can preview unpublished content before it goes live, using a
PREVIEW-env CDA token (which serves draft-preferred content):

1. Set `SHIO_PREVIEW_TOKEN` (a PREVIEW-env token) and `SHIO_PREVIEW_SECRET`.
2. Open `/api/preview?secret=<SHIO_PREVIEW_SECRET>&url=/the/path` — this turns on
   Next.js Draft Mode and the page renders dynamically (SSR) through the preview
   client. A banner with an "Exit preview" link appears.

When Draft Mode is off (the default for visitors), everything is plain SSG/ISR.

## Customizing

The post's fields arrive as `post.attrs` (a JSON object keyed by your post-type
attribute names). The catch-all page renders a `content`/`body`/`text`/`html`
field if present, and otherwise dumps `attrs` so you can see the shape. Adapt the
rendering to your own post types.

## Typed content (`post.attrs.*` autocomplete)

`post.attrs` is `Record<string, unknown>` by default. Generate TypeScript
declarations from your live post-types so you get autocomplete and type-checking:

```bash
npm run shio:types   # reads SHIO_URL / SHIO_CDA_TOKEN, writes lib/shio-types.d.ts
```

This emits one `Post` interface per post-type plus a `ShioPostTypes` name→type
map. Cast a fetched post to its generated type to unlock typed `attrs`:

```ts
import type { ArticlePost } from "@/lib/shio-types";

const post = (await client.getPostByUrl(SITE_ID, url)) as ArticlePost | null;
post?.attrs.title; // string — autocompleted
```

Re-run after changing a post-type. The output is auto-generated — don't edit it
by hand.

## Learn more

- [Viglet Shio](https://github.com/openviglet/shio)
- [Next.js Docs](https://nextjs.org/docs)
