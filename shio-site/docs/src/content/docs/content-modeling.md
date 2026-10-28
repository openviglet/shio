---
title: Content Modeling
description: "Post types in Viglet Shio: one name-keyed surface, the widget catalogue, relationships, the model as code in TypeScript or JSON, and renaming without losing content."
sidebar:
  order: 1
---

A **post type** declares the fields a post may have. It is what a curator's form is
generated from, what an agent's write is validated against, and what a generated TypeScript
type is derived from: one declaration, three consumers.

---

## One name-keyed surface

Post types are referenced **by name** (`Article`, `Page`, `PageLayout`) everywhere: the
REST API, MCP calls, the CLI, and the files on disk. There is no id-keyed twin; the one that
used to exist was retired.

```http
GET    /api/v2/post-type            # every type
GET    /api/v2/post-type/Article    # one type and its fields
POST   /api/v2/post-type            # create
PUT    /api/v2/post-type/Article    # replace the definition
POST   /api/v2/post-type/Article/rename
DELETE /api/v2/post-type/Article
```

That choice is what makes a content model versionable: a type is a file called
`Article.json`, and a diff of two revisions is readable.

**Renaming is a move, not a create-plus-delete.** `POST /{name}/rename` keeps the type's
identity and its content. Deleting a type that content still points at answers a teaching
`409` naming what is in the way, rather than orphaning posts.

---

## Fields and widgets

Every field has a **name**, a label, and a **widget**. The widget decides three things at
once: how a curator edits the value, how the value is stored, and how it projects to a file
on disk.

| Widget | For |
|---|---|
| `Text` | A single line |
| `Text Area` | Plain multi-line text |
| `HTML Editor` | Rich text. The **first** one on a type becomes the document body when the post projects to a Markdown file |
| `Ace Editor - HTML` · `- CSS` · `- Javascript` | Code. These project to **sidecar files** beside the Markdown, so a template is editable with real tooling |
| `Check Box` | A boolean |
| `Combo Box` | One of a declared list of choices |
| `Multi Select` | Several values, tags, or a list of references (see below) |
| `Date` | A date or timestamp |
| `File` | An uploaded file, referenced by the `File` post it creates |
| `Content Select` | A reference to another post |
| `Relator` | A repeating group of sub-fields |
| `Tab` | Layout only: groups fields in the console form |
| `Hidden` | Stored, not shown |

Two fields can be marked as the type's **title** and **summary**, which is what listings,
search results and the `{{#query}}` helper's rows read.

### Widget settings

Some widgets need configuration, and it travels as `widgetSettings` on the field: a JSON
string. A `Combo Box` needs its choices; a `Multi Select` needs to say whether it holds tags
or references:

```json
{ "multiselect": { "references": "post" } }
```

That declaration is not cosmetic. It is what makes a composition **verifiable**, with it,
`shio verify` resolves each value as an address and reports the ones that do not exist. A
`Multi Select` without it stays a tag list, because reading every tag as a URL would report
a defect for every tag on every post.

---

## Relationships

| You want | Use |
|---|---|
| One post to point at another | `Content Select` |
| An ordered list of other posts (a page composed of sections, say) | `Multi Select` with `references: "post"` |
| Repeating structured rows inside one post | `Relator`, with its own sub-fields |
| A file | `File`, which creates a `File` post you can address like anything else |

Sections are the ordered-list case: a `Page` names its sections **by friendly URL, in
order**, and a region's template walks them with `{{#getRelation}}`. See
[Blueprints](/docs/blueprints/) for the shipped section vocabulary.

---

## System post types

Some types are created by Shio itself and are read-only in the console:

| Type | For |
|---|---|
| `Text` | General-purpose content |
| `File` | An uploaded asset, with its own dimension and checksum fields |
| `PageLayout` · `Region` · `Theme` | The render model |
| `Redirect` | One old path in, one address out |
| `SiteScripts` | A site's third-party script list, in load order |

The render types are created **on demand**: `POST /api/v2/render/post-types`, or the
`render.provision` op, rather than at install time, so provision them once before writing
a layout. They are otherwise ordinary content: exportable, projectable, lintable.

---

## Asking what a model change would cost

A post type change is a decision with alternatives, not a yes/no: making a field required
is either a data cleanup or a mistake, and only the number of posts that would violate it
tells you which. Send the definition you are considering with `?dryRun=true` and get the
cost instead of the change:

```http
PUT /api/v2/post-type/Article?dryRun=true
```

| Finding | What it tells you |
|---|---|
| `required-field-violated` | how many published posts have no value for a field you are making required, with a sample |
| `removed-field-still-read` | a template interpolates a field this change removes, so those pages would render nothing there |
| `declared-field-unread` | fields no template reads. Not a defect: a field can be delivered over the CDA, but it is the list to check before adding another |
| `stored-key-undeclared` | keys the content stores that no field declares, so nothing validates them and the console cannot edit them |

The last two are true of the model as it stands, so sending a type back to itself is a
way to review one nobody is currently changing.

## Modelling from content you already have

Writing a post type from nothing means naming fields before you know what they hold. If
three pages of the shape already exist, ask what they have in common instead:

```bash
shio propose --from post:mysite/products/one --from post:mysite/products/two              --from post:mysite/products/three --name Product
```

Every proposed field says how many of the instances carry it and names the ones that do
not: a field two of three share is a different proposal from one all three do. A field
only a minority carries is reported separately rather than folded in, because it is more
likely another type's field than an optional one. The report ends by saying it is
evidence about the instances it read, which is the honest limit of the method.

## The model as code

A content model is a deployable artefact, not console state.

```bash
shio pull                 # the live model → shio/post-types/*.json
shio push --dry-run       # what would change
shio push                 # apply
shio push --check         # exit non-zero if git and the server disagree (CI)
```

Serialization is **canonical and byte-identical**: pull twice and nothing changes, so a
diff means a real difference.

### Authoring in TypeScript

`shio/post-types/` also accepts `.ts` and `.mjs` modules written with
`@viglet/shio-model`, and the CLI compiles them to exactly the same canonical JSON:

```ts
import { postType, text, htmlEditor, comboBox, multiSelect } from "@viglet/shio-model";

export default postType("Article", {
  fields: [
    text("TITLE", { title: true, required: true }),
    htmlEditor("TEXT"),
    comboBox("CATEGORY", { choices: { tech: "Technology", biz: "Business" } }),
    multiSelect("SECTIONS", { references: "post" }),
  ],
});
```

The point is compile-time validation: `comboBox` *requires* its choices, so a missing one is
a TypeScript error in your editor rather than a `400` from a push. `shio build` compiles
without touching the server, if you want to inspect the output.

### Managed by code

Any push marks the types it wrote as **managed by code**. The console then shows them
read-only, badge, disabled form, and a `409` on a console `PUT` as the server-side
backstop. That is deliberate: it makes the boundary between "owned by git" and "edited by
hand" explicit instead of a last-writer-wins race.

### Generated TypeScript for a front end

```bash
shio types --out types/shio.d.ts
```

Produces one interface per post type from the live model, so `post.attrs.TITLE` has
autocomplete in your front end. See [JavaScript Client](/docs/headless/javascript-client/).

---

## Addressing content

A model is only useful if you can name what it produced:

| Form | Names |
|---|---|
| `post:mysite/blog/hello-world` | one post |
| `folder:mysite/blog` | one folder |
| `site:mysite` | a site |
| `id:9f8c…` | anything, by raw id |

Writes take only `post:` and `folder:` (plus `site:`), and a site's home page is
`post:mysite/`. A post's friendly URL is its own field (**not** its folder path) so
reorganising folders does not rewrite URLs.

---

## Publishing

A post has two states, `DRAFT` and `PUBLISHED`, and can be in both at once. Editing a
published post updates its draft and leaves the live version alone until something publishes
it. Publishing can be scheduled. Deleting moves the post to the trash. See
[Core Concepts](/docs/getting-started/core-concepts/#draft-and-published), and
[Preview, history and scheduling](/docs/content-lifecycle/) for the curator's tools that
act on those states.

A post also carries a **locale**, and a translation is a linked sibling post rather than a
field on this one, which decides your URL structure, so read
[Content in several languages](/docs/content-i18n/) before writing the second language.

### Required fields are checked when you publish, not when you save

A draft may be incomplete: that is what a draft is for, and a page you have half written
saves like any other. **Publishing refuses** while a field the post type declares required
is empty, from every writer: the console's Publish, the REST call, the agent's
`post.publish`, a scheduled transition, approving a run in the review queue, a `PATCH` that
publishes, and the delivery API's publish alike. Every one of those paths runs the same
checks, so there is no door around them. The refusal names the fields, and in the
console it marks the input you have to fill.

You find out before you press it. The publish preview and the agent's dry run both say
which fields would stop the publish, beside anything a publish gate would stop it for.

One way past it, and it is the same door a gate has: a curator can **waive** the check with
a reason, which is recorded on the page's history and on the version that goes live. An
agent cannot: waiving is a person's decision, and this is one of the things that stays
that way.

:::note[A container field is never "empty"]
A Tab holds no value, and a Relator's rows live in the rows rather than on the post, so
neither refuses a publish even when the type marks it required.
:::

---

## Search fields

A field can be mapped to a Turing Semantic Navigation field, title, description, text,
date, URL, image, or to a custom field for facets. That mapping only matters if you have
enabled Turing indexing, which is **off by default**; see
[Search & Caching](/docs/search-caching/).

---

## Related Pages

| Page | Description |
|---|---|
| [Core Concepts](/docs/getting-started/core-concepts/) | Sites, folders, posts, states |
| [The `shio` CLI](/docs/cli/) | `pull`, `push`, `build`, `types` |
| [Content as Files](/docs/content-as-files/) | How posts and code fields project to disk |
| [The Agent Surface](/docs/agent-surface/) | `post.upsert` and the field validation it applies |
| [Pages, Layouts & Regions](/docs/website-development/) | The render types in use |
