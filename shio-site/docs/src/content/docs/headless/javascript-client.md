---
title: JavaScript Client
description: "@viglet/shio-client, the framework-agnostic TypeScript client for the Shio Content Delivery API."
sidebar:
  order: 8
---

A **framework-agnostic**, **zero-dependency** TypeScript client for the
[Content Delivery API](/docs/headless/content-delivery-api/). It runs anywhere `fetch` is
available: Node, the browser, edge runtimes, Next.js server components, Astro,
Vue, Svelte, or plain scripts. It is the **base** SDK; the
[React SDK](/docs/headless/react-sdk/) is one consumer of it.

## Install

```bash
npm install @viglet/shio-client
# or: pnpm add @viglet/shio-client
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
| `getRedirects(siteId)` | `GET /site/{id}/redirects` | `ShioRedirect[] \| null` |
| `listChildren(folderId, { page, size })` | `GET /object/{id}/list` | `ShioListing \| null` |
| `getPath(objectId)` | `GET /object/{id}/path` | `ShioPath \| null` |
| `getPost(postId)` | `GET /post/{id}` | `ShioPost \| null` |
| `getPostByUrl(siteId, url)` | `GET /post/by-url` | `ShioPost \| null` |
| `query({ siteId, folderId, postType, page, size })` | `GET /query` | `ShioListing` |

- **Language and version.** `listChildren`, `query` and `getPostByUrl` take `locale` (only
  posts in that language, paged by the server; see
  [Content in several languages](/docs/content-i18n/#reading-a-locale-through-the-cda)).
  `getPost`, `getPostByUrl`, `listChildren` and `query` take `state`: `published`, or `draft`
  and `rev:<n>` with a preview token, which is how a front end renders a draft preview. A
  refused state throws `ShioHttpError` with status `403`.
- Single-resource getters return `null` on `404`.
- Any other non-OK status throws `ShioHttpError` (`{ status, url, body }`).
- Every method accepts a final `{ signal }` option for cancellation (`AbortSignal`).

## Configuration

```ts
interface ShioClientConfig {
  baseUrl: string;                  // required
  apiToken?: string;                // sent as the `Key` header
  fetch?: typeof fetch;             // custom fetch (defaults to global fetch)
  headers?: Record<string, string>; // extra headers
  credentials?: RequestCredentials; // omitted by default (CDA uses token auth)
}
```

The `fetch` option makes the client easy to use in runtimes without a global
`fetch`, and trivial to stub in tests.

## Error handling

```ts
import { ShioClient, ShioHttpError } from "@viglet/shio-client";

try {
  const post = await shio.getPost(id);
  if (!post) {
    // 404 → not found / not published
  }
} catch (err) {
  if (err instanceof ShioHttpError) {
    console.error(err.status, err.url);
  }
}
```

## Use in Node / server-side

The client is safe to instantiate once and share (e.g. a module singleton):

```ts
// lib/shio.ts
import { ShioClient } from "@viglet/shio-client";

export const shio = new ShioClient({
  baseUrl: process.env.SHIO_URL!,
  apiToken: process.env.SHIO_CDA_TOKEN,
});
```

## Next steps

- [React SDK](/docs/headless/react-sdk/): hooks and render components built on this client.
- [Next.js Starter](/docs/headless/nextjs-starter/): a ready-made app using it.
