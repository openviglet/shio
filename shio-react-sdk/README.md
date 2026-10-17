# @viglet/shio-react-sdk

[![npm](https://img.shields.io/npm/v/@viglet/shio-react-sdk?logo=npm)](https://www.npmjs.com/package/@viglet/shio-react-sdk)
[![License](https://img.shields.io/npm/l/@viglet/shio-react-sdk)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/shio.viglet.org-0ea5e9?logo=googlechrome&logoColor=white)](https://shio.viglet.org/)

The **React SDK** for **[Viglet Shio](https://shio.viglet.org/)**, the open
source, **agent-ready CMS**. Hooks and render components for
consuming Shio content through the **Content Delivery API (CDA)**, built on
[`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client).

- **React 18 / 19** — `react` is a peer dependency.
- Targets the stable, read-only CDA contract (`/api/v2/cda/**`).
- Everything the core client exports is **re-exported** here, so this is the only
  package a React app needs.

## Install

```bash
npm install @viglet/shio-react-sdk react
```

## Quick start

Wrap your tree once:

```tsx
import { ShioProvider } from "@viglet/shio-react-sdk";

export function App({ children }: { children: React.ReactNode }) {
  return (
    <ShioProvider
      baseUrl="https://cms.example.com"
      apiToken={import.meta.env.VITE_SHIO_CDA_TOKEN}
    >
      {children}
    </ShioProvider>
  );
}
```

Then read content anywhere below it:

```tsx
import { useShioPostByUrl, ShioRichText } from "@viglet/shio-react-sdk";

function Article({ siteId, url }: { siteId: string; url: string }) {
  const { data: post, loading, error } = useShioPostByUrl(siteId, url);

  if (loading) return <p>Loading…</p>;
  if (error) return <p>{error.message}</p>;
  if (!post) return <p>Not found</p>;

  return (
    <article>
      <h1>{String(post.attrs.title ?? "")}</h1>
      <ShioRichText html={String(post.attrs.content ?? "")} />
    </article>
  );
}
```

`<ShioProvider>` also accepts an already-built client:
`<ShioProvider client={myClient}>`.

## Hooks

Every hook returns a `ShioAsyncState<T>` — `{ data, loading, error }`.

| Hook | Returns |
|---|---|
| `useShioSites()` | Every site the token can see |
| `useShioSite(siteId)` | One site |
| `useShioChildren(folderId, params?)` | A folder's children, paged |
| `useShioBreadcrumb(objectId)` | The path from the site root to an object |
| `useShioPost(postId)` | One post by id |
| `useShioPostByUrl(siteId, url)` | One post by its friendly URL — built for `[...slug]` routing |
| `useShioQuery(params?, enabled?)` | A query across a site / folder / post-type, paged |
| `useShioClient()` | The `ShioClient` from context, for anything the hooks don't cover |

## Components

| Component | For |
|---|---|
| `<ShioRichText html={…} />` | Renders an HTML-editor field safely into a `div` (accepts any `div` props) |
| `<ShioImage src={…} baseUrl={…} alt={…} />` | Resolves a stored file reference to a URL and renders an `img` |
| `<ShioRelator items={…}>` | Maps over a Relator (nested repeatable) field |
| `<ShioField value={…} as={…} />` | Renders one attribute by kind — `text`, `html`, `image`, `list`, `json` |

## Next.js

For **server components**, use
[`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client)
directly — it is zero-dependency and needs no provider. Reach for this package
in **client components**, where the hooks give you loading and error state.

The fastest way to a working project is the generator, which wires both up for
you:

```bash
npm create @viglet/shio-app@latest my-site
```

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | The framework-agnostic core this is built on |
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI — content as files, `shio types`, and the MCP bridge for [Claude Code](https://claude.com/claude-code) |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary and its React components |
| [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) | Author the content model as typed code |
| [`@viglet/shio-editor-cors`](https://www.npmjs.com/package/@viglet/shio-editor-cors) | Inline visual editing with the Universal Editor |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## Develop

```bash
npm run build      # vite lib build + .d.ts
npm run typecheck
```

## License

Apache-2.0 — [Viglet Shio](https://shio.viglet.org/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
