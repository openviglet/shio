# @viglet/shio-model

[![npm](https://img.shields.io/npm/v/@viglet/shio-model?logo=npm)](https://www.npmjs.com/package/@viglet/shio-model)
[![License](https://img.shields.io/npm/l/@viglet/shio-model)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/website-0ea5e9?logo=googlechrome&logoColor=white)](https://openviglet.github.io/shio/)

Typed **TypeScript DSL** for the content model of
**[Viglet Shio](https://openviglet.github.io/shio/)**, the open source, **agent-ready CMS
for coding agents** (SH62). Author your post-types **as code** — `postType()`,
`text()`, `comboBox()`, `relator()`, … — and compile to the canonical Shio
post-type JSON that the [`shio` CLI](https://www.npmjs.com/package/@viglet/shio)
(`shio push`) applies.

It is the authoring **inverse** of
[`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client)'s type
generation (SH11): SH11 turns the live model *into* types for **consuming**
content; this turns typed code *into* the model for **authoring** it. Content
model as code means the model is reviewable in a pull request and appliable from
CI — a **GitOps** workflow, and the shape an AI agent driving Shio from
[Claude Code](https://claude.com/claude-code) writes in.

Zero runtime dependencies. The `tsserver` already running in your editor gives
autocomplete, rename and compile-time validation — no VSCode extension needed.

## Install

```bash
npm install @viglet/shio-model
```

## Example

```ts
// shio/post-types/news.ts
import { postType, text, htmlEditor, comboBox, relator } from "@viglet/shio-model";

export default postType("News", {
  title: "News",
  namePlural: "News",
  fields: [
    text("title", { label: "Title", isTitle: true, required: true }),
    text("summary", { label: "Summary", isSummary: true }),
    htmlEditor("body", { label: "Body" }),
    comboBox("category", {
      label: "Category",
      choices: { tech: "Technology", biz: "Business" }, // not "id: label\n…"
      allowNull: true,
    }),
    relator("authors", {
      title: "Authors",
      fields: [text("name", { isTitle: true }), text("email")],
    }),
  ],
});
```

`shio push` compiles this to the canonical `ShPostTypeDef` JSON (via
`serializeDef`, shared byte-for-byte with the CLI) and applies it through the
lossless admin API. JSON stays the interchange format — `.ts` and `.json` files
coexist in `shio/post-types/`.

## The middle path

The DSL is an **optional authoring layer**, not the sole source of truth — JSON
stays canonical and the console GUI editor stays a legitimate writer. See
[docs/specs/SH62-post-types-dsl.md](https://github.com/openviglet/shio/blob/HEAD/docs/specs/SH62-post-types-dsl.md)
and [IMPROVEMENTS.md §II.7](https://github.com/openviglet/shio/blob/HEAD/docs/IMPROVEMENTS.md).

## Status

✅ **SH63 shipped** — DSL core, the `widgetSettings` codec (encode + symmetric
decode), and the canonical serialization shared with the CLI are complete (see
[CHANGELOG.md](https://github.com/openviglet/shio/blob/HEAD/docs/CHANGELOG.md)
Block B). The CLI compile step (SH64), CI drift guard (SH65) and console "managed
by code" lock (SH66) are tracked in the
[roadmap](https://github.com/openviglet/shio/blob/HEAD/docs/ROADMAP.md) Block B.

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI — compiles this DSL and pushes it (`shio push`, `shio build`) |
| [`@viglet/shio-sections`](https://www.npmjs.com/package/@viglet/shio-sections) | The page-section vocabulary, authored with this DSL |
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | Zero-dependency Content Delivery API client |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## License

Apache-2.0 — [Viglet Shio](https://openviglet.github.io/shio/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[docs.viglet.org/shio](https://docs.viglet.org/shio/) ·
part of the [Viglet](https://www.viglet.org) open source family.
