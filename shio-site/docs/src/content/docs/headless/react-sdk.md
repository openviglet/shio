---
title: React SDK
description: "@viglet/shio-react-sdk: React hooks and render components for the Shio Content Delivery API."
sidebar:
  order: 9
---

React hooks and render components for consuming Shio content, built on top of
[`@viglet/shio-client`](/docs/headless/javascript-client/). React is one consumer of the
core client, the data fetching, types, and auth all come from the core.

## Install

```bash
npm install @viglet/shio-react-sdk
# @viglet/shio-client is installed automatically as a dependency
```

## Provider

Wrap your app once with `ShioProvider`, passing connection config or a pre-built
client (useful for sharing one instance between SSR and the browser):

```tsx
import { ShioProvider } from "@viglet/shio-react-sdk";

export function App({ children }) {
  return (
    <ShioProvider baseUrl="https://cms.example.com" apiToken={token}>
      {children}
    </ShioProvider>
  );
}
```

```tsx
// or share a pre-built client
import { ShioProvider, ShioClient } from "@viglet/shio-react-sdk";
const client = new ShioClient({ baseUrl, apiToken });
<ShioProvider client={client}>{children}</ShioProvider>
```

## Hooks

Every hook returns `{ data, loading, error }` and cancels the in-flight request
on unmount or dependency change.

| Hook | Returns (`data`) |
|---|---|
| `useShioSites()` | `ShioSite[]` |
| `useShioSite(siteId)` | `ShioSite \| null` |
| `useShioChildren(folderId, { page, size })` | `ShioListing \| null` |
| `useShioBreadcrumb(objectId)` | `ShioPath \| null` |
| `useShioPost(postId, state?)` | `ShioPost \| null` |
| `useShioPostByUrl(siteId, url, { locale, state })` | `ShioPost \| null` |
| `useShioQuery({ siteId, folderId, postType, page, size })` | `ShioListing` |

```tsx
import { useShioPostByUrl } from "@viglet/shio-react-sdk";

function Page({ siteId, url }) {
  const { data: post, loading, error } = useShioPostByUrl(siteId, url);

  if (loading) return <p>Loading…</p>;
  if (error) return <p>Something went wrong.</p>;
  if (!post) return <p>Not found.</p>;

  return <h1>{post.title}</h1>;
}
```

`useShioQuery` and `useShioChildren` refetch when their `locale` or `state` changes, so a
language switcher or a preview toggle re-reads without remounting.

## Render components

| Component | Renders |
|---|---|
| `ShioRichText` | An HTML field value (`dangerouslySetInnerHTML`) |
| `ShioImage` | A File/image field, resolving relative paths against `baseUrl` |
| `ShioRelator` | A Relator field (list of nested items) via a render prop |
| `ShioField` | A single field, picking a renderer by value shape (or an explicit `as`) |

```tsx
import { ShioRichText, ShioField, ShioRelator } from "@viglet/shio-react-sdk";

<ShioRichText html={post.attrs.content as string} />

<ShioField value={post.attrs.summary} />
<ShioField value={post.attrs.cover} as="image" baseUrl="https://cms.example.com" />

<ShioRelator items={post.attrs.gallery}>
  {(item) => <figure>{String(item.caption)}</figure>}
</ShioRelator>
```

:::caution[Sanitize untrusted HTML]
`ShioRichText` injects HTML verbatim. Shio content is authored by trusted
editors, but if your content can contain untrusted input, sanitize it upstream
(e.g. with DOMPurify) before rendering.
:::

:::note[Field rendering is heuristic]
The CDA delivers `attrs` as a plain JSON map without per-field widget metadata,
so `ShioField` infers the renderer from the value's shape. Pass `as` explicitly
when you need a specific renderer.
:::

## Next steps

- [Next.js Starter](/docs/headless/nextjs-starter/): scaffold a full site in seconds.
