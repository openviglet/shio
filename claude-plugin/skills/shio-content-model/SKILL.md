---
name: shio-content-model
description: Author or change a Shio content model — post-types, fields, widgets — as code. Use when the user asks to add a post-type or a field, change a widget, model content for a new kind of page, or wants the model in version control. Also use when a write failed with "no post-type named X".
---

# The Shio content model, as code

A post-type is a content type; its fields are widgets. The model is **code**, not console
clicks: it lives in `shio/post-types/` and is pushed with the CLI.

## Read before you write

`shio_context` returns the whole model. For one type's fields, read
`shio://schema/<PostTypeName>`. Do not invent a post-type when one exists — an instance with
two types for one idea costs every later session an archaeology pass.

Check `shio://blueprints` too: `marketing-site` and `landing` ship a **section vocabulary**
(`Page`, `SectionHero`, `SectionFeatureGrid`, `SectionCta`, `SectionLogoWall`,
`SectionTestimonial`, `SectionFaq`, `SectionRichText`) with matching React components in
`@viglet/shio-sections`. If the user is building pages out of blocks, use that vocabulary
rather than a new one — it is what the console, the front end and the render digest all
already understand.

## The workflow

```
shio pull                 # writes shio/post-types/*.json — the lossless authoring form
# edit, or author in TypeScript with @viglet/shio-model
shio push --dry-run       # the plan
shio push                 # apply
shio push --check         # CI drift guard: non-zero when git and the server disagree
shio types --out lib/shio-types.d.ts   # TypeScript types from the live model
```

Authoring in TypeScript gives compile-time validation of widget settings:

```ts
import { postType, text, textArea, htmlEditor, comboBox } from "@viglet/shio-model";

export default postType("Article", {
  title: "Article",
  namePlural: "Articles",
  fields: [
    text("headline", { label: "Headline", required: true, isTitle: true }),
    textArea("summary", { label: "Summary", isSummary: true }),
    htmlEditor("body", { label: "Body", required: true }),
    comboBox("kind", { label: "Kind", choices: { guide: "Guide", news: "News" } }),
  ],
});
```

## The widgets

`Text` · `Text Area` · `HTML Editor` · `Check Box` · `Combo Box` · `Date` · `Hidden` ·
`Multi Select` · `Ace Editor - Javascript` · `Ace Editor - HTML` · `Content Select` ·
`File` · `Relator` (a repeatable group of nested fields) · `Tab` (a UI grouping, holds no
data). A widget name outside that list is a 400 that names the ones that exist.

## Things that will bite

- **`isTitle` and `isSummary` are per-type flags on a field**, not field names. A type with
  no title field shows blank rows in the console.
- **A post-type pushed by the CLI is marked "managed by code"** and becomes read-only in the
  console. That is the point — but tell the user before you push a type they edit by hand.
- **System post-types cannot be changed** (409). Neither can a type a blueprint provisioned
  *if* you try to replace it wholesale: a blueprint seeds a model and does not own it, so
  applying one again never reverts a field a curator added.
- **Deleting a field deletes its content.** Say so before doing it.
