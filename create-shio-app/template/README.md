# {{PROJECT_NAME}}

A [Next.js](https://nextjs.org) (App Router) site powered by **Shio CMS** via the
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

All content comes from the Shio CDA (`/api/v2/cda/**`). You never touch the Java backend — just edit React/TypeScript.

## Customizing

The post's fields arrive as `post.attrs` (a JSON object keyed by your post-type
attribute names). The catch-all page renders a `content`/`body`/`text`/`html`
field if present, and otherwise dumps `attrs` so you can see the shape. Adapt the
rendering to your own post types.

## Learn more

- [Shio CMS](https://github.com/openviglet/shio)
- [Next.js Docs](https://nextjs.org/docs)
