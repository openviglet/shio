# @viglet/shio-client

Framework-agnostic TypeScript client for the **Shio CMS Content Delivery API (CDA)**.

- **Zero runtime dependencies** — just `fetch`.
- Runs in **Node, the browser, edge runtimes, Next.js server components, Astro, Vue, Svelte** and plain scripts.
- Targets the stable, read-only CDA contract (`/api/v2/cda/**`) — no Java, no legacy console API.
- This is the **base** SDK; `@viglet/shio-react-sdk` is one consumer of it.

## Install

```bash
npm install @viglet/shio-client
```

## Quick start

```ts
import { ShioClient } from "@viglet/shio-client";

const shio = new ShioClient({
  baseUrl: "https://cms.example.com",
  apiToken: process.env.SHIO_CDA_TOKEN, // sent as the `Key` header
});

const sites = await shio.getSites();
const post = await shio.getPostByUrl(sites[0].id, "/blog/hello-world");
console.log(post?.attrs.title);
```

## API

| Method | CDA endpoint | Returns |
|---|---|---|
| `getSites()` | `GET /site` | `ShioSite[]` |
| `getSite(siteId)` | `GET /site/{id}` | `ShioSite \| null` |
| `listChildren(folderId, {page,size})` | `GET /object/{id}/list` | `ShioListing \| null` |
| `getPath(objectId)` | `GET /object/{id}/path` | `ShioPath \| null` |
| `getPost(postId)` | `GET /post/{id}` | `ShioPost \| null` |
| `getPostByUrl(siteId, url)` | `GET /post/by-url` | `ShioPost \| null` |
| `query({siteId,folderId,postType,page,size})` | `GET /query` | `ShioListing` |

- Single-resource getters return `null` on `404`.
- Any other non-OK status throws `ShioHttpError` (`{ status, url, body }`).
- Every method accepts a final `{ signal }` option for cancellation.

## Next.js App Router — catch-all page

The `getPostByUrl` call is built for `[...slug]` routing:

```ts
// app/[...slug]/page.tsx
import { ShioClient } from "@viglet/shio-client";

const shio = new ShioClient({
  baseUrl: process.env.SHIO_URL!,
  apiToken: process.env.SHIO_CDA_TOKEN,
});

export default async function Page({ params }: { params: { slug?: string[] } }) {
  const url = "/" + (params.slug?.join("/") ?? "");
  const post = await shio.getPostByUrl(process.env.SHIO_SITE_ID!, url);
  if (!post) return <div>Not found</div>;
  return <article dangerouslySetInnerHTML={{ __html: String(post.attrs.content ?? "") }} />;
}
```

## Custom runtimes / testing

Inject a `fetch` implementation for environments without a global `fetch`, or to
stub it in tests:

```ts
const shio = new ShioClient({ baseUrl, fetch: myFetch });
```

## Configuration

```ts
interface ShioClientConfig {
  baseUrl: string;              // required
  apiToken?: string;            // sent as `Key` header
  fetch?: typeof fetch;         // defaults to global fetch
  headers?: Record<string, string>;
  credentials?: RequestCredentials; // omitted by default (CDA uses token auth)
}
```

## Develop

```bash
npm install
npm run build      # vite lib build + .d.ts
npm run typecheck  # tsc --noEmit
npm test           # node:test, no network (injected fetch)
```

## License

GPL-3.0
