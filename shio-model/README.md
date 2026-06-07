# @viglet/shio-model

Typed TypeScript DSL for the **Shio CMS** content model (SH62). Author your
post-types as code and compile to the canonical Shio post-type JSON that the
[`shio` CLI](../cli) (`shio push`) applies. It is the authoring **inverse** of
[`@viglet/shio-client`](../shio-client)'s type generation (SH11): SH11 turns the
live model *into* types for **consuming** content; this turns typed code *into*
the model for **authoring** it.

Zero runtime dependencies. The `tsserver` already running in your editor gives
autocomplete, rename and compile-time validation — no VSCode extension needed.

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
[docs/specs/SH62-post-types-dsl.md](../docs/specs/SH62-post-types-dsl.md) and
[IMPROVEMENTS.md §II.7](../docs/IMPROVEMENTS.md).

## Status

🚧 Skeleton (SH63). The CLI compile step (SH64), CI drift guard (SH65) and the
console "managed by code" lock (SH66) are tracked in the
[roadmap](../docs/ROADMAP.md) Block B.
