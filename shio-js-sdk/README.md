# @viglet/shio-editor-cors

[![npm](https://img.shields.io/npm/v/@viglet/shio-editor-cors?logo=npm)](https://www.npmjs.com/package/@viglet/shio-editor-cors)
[![License](https://img.shields.io/npm/l/@viglet/shio-editor-cors)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/website-0ea5e9?logo=googlechrome&logoColor=white)](https://openviglet.github.io/shio/)

The **Universal Editor bridge** for **[Viglet Shio](https://openviglet.github.io/shio/)**,
the open source, **agent-ready CMS**. Drop it into *any*
front end — Next.js, Astro, Nuxt, Remix, plain HTML — and the page becomes
**inline-editable** inside the Shio console, across origins.

Zero dependencies, ~one file, no framework assumptions. It annotates nothing on
its own: **you** mark what is editable with `data-sh-*` attributes, and this
bridge turns those marks into click-to-edit regions, talks to the console shell
over `postMessage`, and reports the changes back.

> Shio's own renderer injects an equivalent bridge automatically. **This package
> is for front ends that render themselves** and emit their own `data-sh-*`
> attributes — one bridge per producer.

## Install

```bash
npm install @viglet/shio-editor-cors
```

```ts
// Import once, as high in the app as you can. It self-initializes on DOM ready.
import "@viglet/shio-editor-cors";
```

Or as a plain script tag (IIFE build):

```html
<script src="https://unpkg.com/@viglet/shio-editor-cors/dist/shio-editor-cors.iife.js"></script>
```

## Configure with meta tags

The bridge reads its configuration from the document head, so it works the same
whether it was bundled or dropped in as a tag:

```html
<!-- Which console origin is allowed to edit this page. REQUIRED —
     without it the bridge stays inert and logs why. -->
<meta name="urn:shio:system:editor-origin" content="https://cms.example.com">

<!-- Which Shio instance the content lives in. -->
<meta name="urn:shio:system:connection" content="shio:https://cms.example.com">

<!-- Optional context, sent to the console on `sh:ready`. -->
<meta name="urn:shio:system:site" content="acme">
<meta name="urn:shio:system:path" content="/blog">
```

The editor origin has to be *told*: a framed page cannot work out who framed it —
`document.referrer` is empty on some navigations and `window.parent.location`
throws cross-origin — so a missing tag is a loud `console.warn`, never a silent
no-op.

## Mark what is editable

| Attribute | Meaning |
|---|---|
| `data-sh-resource` | The Shio address this subtree maps to (e.g. `post:acme/blog/hello`) — the editable root |
| `data-sh-prop` | Which attribute of that post this element renders |
| `data-sh-type` | `text` (default), `richtext`, `container`, `component` |
| `data-sh-label` | The label shown on hover in edit mode |
| `data-sh-model` | The post-type name, when it is not inherited from the resource root |

```html
<article data-sh-resource="post:acme/blog/hello" data-sh-model="Article">
  <h1 data-sh-prop="title" data-sh-type="text" data-sh-label="Title">
    Hello world
  </h1>
  <div data-sh-prop="content" data-sh-type="richtext" data-sh-label="Body">
    <p>…</p>
  </div>
</article>
```

`data-sh-prop` elements nested under a `data-sh-resource` inherit the resource
and the model, so you only annotate the root once.

## How it works

1. On DOM ready the bridge reads the meta tags. No editor origin → inert.
2. It injects the edit-mode styles and posts `sh:ready` to the parent frame with
   the page URL, title, editable count and connection details.
3. The Shio console shell toggles `preview` / `edit` mode over `postMessage`.
4. In edit mode a click on an annotated element opens an inline text or rich-text
   editor; changes are collected and sent back to the console to be saved.

Every message is origin-checked against the configured editor origin.

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | Zero-dependency Content Delivery API client |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary |
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI and the MCP bridge for [Claude Code](https://claude.com/claude-code) |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## Develop

```bash
npm run build   # vite lib build (es + umd + iife) and .d.ts
```

## License

Apache-2.0 — [Viglet Shio](https://openviglet.github.io/shio/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
