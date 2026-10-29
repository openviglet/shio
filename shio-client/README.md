# @viglet/shio-client

[![npm](https://img.shields.io/npm/v/@viglet/shio-client?logo=npm)](https://www.npmjs.com/package/@viglet/shio-client)
[![License](https://img.shields.io/npm/l/@viglet/shio-client)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/website-0ea5e9?logo=googlechrome&logoColor=white)](https://openviglet.github.io/shio/)

Framework-agnostic **TypeScript client** for the Content Delivery API (CDA) of
**[Viglet Shio](https://openviglet.github.io/shio/)**, the open source, **agent-ready CMS
for coding agents**.

- **Zero runtime dependencies** — just `fetch`.
- Runs in **Node, the browser, edge runtimes, Next.js server components, Astro, Vue, Svelte** and plain scripts.
- Targets the stable, read-only CDA contract (`/api/v2/cda/**`) — no Java, no legacy console API.
- This is the **base** SDK; [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) is one consumer of it.
- Pair it with [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) — `shio types` generates a `.d.ts` from your live content model, so `post.attrs.*` autocompletes.

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
| `listChildren(folderId, {page,size,locale})` | `GET /object/{id}/list` | `ShioListing \| null` |
| `getPath(objectId)` | `GET /object/{id}/path` | `ShioPath \| null` |
| `getPost(postId)` | `GET /post/{id}` | `ShioPost \| null` |
| `getPostByUrl(siteId, url, locale?)` | `GET /post/by-url` | `ShioPost \| null` |
| `query({siteId,folderId,postType,page,size,locale})` | `GET /query` | `ShioListing` |

- Single-resource getters return `null` on `404`.
- Any other non-OK status throws `ShioHttpError` (`{ status, url, body }`).
- Every method accepts a final `{ signal }` option for cancellation.

## A language switcher

A site's `locales` says which languages it offers; a post's `availableLocales` says which of
them this page exists in. Together they are a switcher, with no call beyond the two you
already make:

```ts
const site = await shio.getSite(siteId);
// null when the post at `url` is in another language; its availableLocales say which exist
const post = await shio.getPostByUrl(siteId, url, locale) ?? await shio.getPostByUrl(siteId, url);

const offered = site?.locales?.enabled ?? [];
const links = offered.map((tag) => ({
  tag,
  // a language this page is missing in shows the site's fallback for it, else its default
  target: post?.availableLocales.includes(tag)
    ? tag
    : site?.locales?.fallback[tag] ?? site?.locales?.defaultLocale,
}));
```

`locales.declared` is `false` when nobody has chosen the site's languages yet. The list is
then the languages its published pages are already written in, which is a description of the
content so far rather than a promise about it.

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

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI — content as files, type generation, and the MCP bridge for [Claude Code](https://claude.com/claude-code) |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components built on this client |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary |
| [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) | Author the content model as typed code |
| [`@viglet/shio-editor-cors`](https://www.npmjs.com/package/@viglet/shio-editor-cors) | Inline visual editing with the Universal Editor |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## License

Apache-2.0 — [Viglet Shio](https://openviglet.github.io/shio/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
