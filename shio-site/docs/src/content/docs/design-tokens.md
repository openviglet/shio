---
title: Design Tokens (DTCG)
description: "Viglet Shio reads W3C Design Tokens (DTCG) on a Theme and compiles them to CSS custom properties, so a restyle is a reviewable field edit instead of a stylesheet rewrite."
sidebar:
  label: Design Tokens (DTCG)
---

A `Theme` in Shio carries a `TOKENS` field. It holds **design tokens in the W3C DTCG
format**, and Shio compiles them into CSS custom properties on every page the theme
renders.

The reason it exists is narrow and worth stating first, because it explains every
decision below.

---

## The problem tokens solve

Before this, a `Theme` held `CSS` and `JAVASCRIPT` as opaque blobs. So "make the accent
colour warmer", "tighten the headings" and "more density", which are the **commonest**
changes anybody asks for, were all stylesheet rewrites. That has three costs:

- **Nothing declares what may be changed.** A stylesheet does not say which values are
  design decisions and which are incidental.
- **Nothing verifies the result.** A diff shows a changed string.
- **An agent editing raw CSS by string replacement is the least reliable thing in the
  loop.** It is the operation most likely to produce a page that renders and looks wrong.

A declared token vocabulary turns a restyle into a **field edit**: reviewable in the
console, diffable in the file projection, and safe for an agent because the set of names
is closed.

Raw CSS stays available. It is the **escape hatch, not the interface**.

---

## What DTCG is

**DTCG** is the format published by the [W3C Design Tokens Community
Group](https://www.designtokens.org/). A token file is JSON: nested **groups**, and a
**token** is any node carrying a `$value`.

```json
{
  "color": {
    "accent":     { "$value": "#0b5d3b", "$type": "color" },
    "text":       { "$value": "#1a1a1a", "$type": "color" },
    "background": { "$value": "#ffffff", "$type": "color" }
  },
  "font": {
    "family": { "body": { "$value": "Inter, system-ui, sans-serif", "$type": "fontFamily" } },
    "size":   { "base": { "$value": "1rem", "$type": "dimension" } }
  },
  "radius": {
    "card": { "$value": "12px", "$type": "dimension", "$description": "Cards and panels" }
  }
}
```

Three parts of the spec matter here:

| Key | Means |
|---|---|
| `$value` | The token's value. A node that has one **is** a token, and the walk stops there |
| `$type` | What kind of value it is (`color`, `dimension`, `fontFamily`, `fontWeight`, `shadow`, …) |
| `$description` | Prose for a reader. Carried, never rendered |

The `$` prefix is the spec's, not a Shio convention. Any other `$`-prefixed key at group
level is spec metadata and is skipped.

**Shio adopted this format rather than inventing one.** That was the open question when
the feature was designed, and the answer is that a dialect nothing else reads is a design
project with no payoff: **Style Dictionary, Figma and Tokens Studio already export and
import DTCG**, so a designer's export pastes straight into the field.

---

## What Shio does with it

### Compiled to custom properties

Every token becomes a CSS custom property on `:root`, with the dotted path as the name:

| Token path | Custom property |
|---|---|
| `color.accent` | `--color-accent` |
| `font.family.body` | `--font-family-body` |
| `radius.card` | `--radius-card` |

The generated block is **prepended to the theme's own CSS**:

```css
:root{--color-accent:#0b5d3b;--color-text:#1a1a1a;--font-family-body:Inter, system-ui, sans-serif;--radius-card:12px;}

.card {
  background: var(--color-background);
  border-radius: var(--radius-card);
}
```

Prepending is deliberate. The alternative was a separate `{{theme.tokens.css}}` handle for
layouts to place, and that would ship the feature **switched off**: every existing layout
would render without tokens until somebody edited a template most authors do not know to
change. Prepending also puts the declarations before the rules that use them, which is the
order CSS needs anyway.

Nothing new has to learn anything: every stylesheet, every browser and every devtool
already reads custom properties.

### Readable from a template

The same values are exposed as a flattened, dotted map:

```handlebars
<svg viewBox="0 0 24 24"><path fill="{{theme.tokens.color.accent}}" d="…"/></svg>
<td style="background:{{theme.tokens.color.background}}">…</td>
```

This exists for the places `var()` cannot reach: an SVG `fill`, an email template, an
inline style consumed by something that does not resolve custom properties. **Same value,
two forms, one source.**

---

## Authoring them

`TOKENS` is a field on the `Theme` post, so it is edited like any other content:

| Path | How |
|---|---|
| Console | Open the Theme, edit the **Design tokens** field (a code editor, since the field is JSON) |
| Files | The theme projects to disk; `TOKENS` travels in the post's `data` |
| An agent | `post.upsert` on the Theme's address, with `data.TOKENS` |
| MCP | `shio_write`, same op |
| Replication | `shio propose` writes them from a captured stylesheet (below) |

An agent example:

```json
{
  "ops": [
    { "op": "post.upsert", "address": "post:mysite/themes/main", "type": "Theme",
      "data": { "TOKENS": "{\"color\":{\"accent\":{\"$value\":\"#0b5d3b\"}}}" } }
  ]
}
```

:::note[An older instance may not have the field]
`TOKENS` is the render model's one deliberate addition beyond the original Theme shape, so
an instance created before it exists will not have the field. `GET /api/v2/render/post-types/drift`
names what is missing and `POST /api/v2/render/post-types/reconcile` adds it. An imported
theme with no tokens keeps rendering exactly as it did.
:::

---

## The rules, and why each one is what it is

| Situation | What happens | Why |
|---|---|---|
| **No tokens at all** | Nothing is emitted, not even an empty `:root{}` | A theme with no tokens must render **byte-identically** to how it rendered before the feature existed, or every render digest in the instance moves for something nobody used |
| **A token whose `$value` is an object** (a DTCG composite type) | That token is **skipped**; its siblings still compile | A composite has no single CSS form. Guessing one is worse than omitting it |
| **A name that cannot be a custom property** (`font size`) | Rewritten to `--font-size` | A custom property name cannot be quoted, so the browser drops the whole declaration silently, and in some parsers takes the rest of the block with it |
| **A malformed value** | Skipped, never a refusal | A token file is content. Refusing to render a page over one typo would take the site down; `shio verify` is where a rule about it belongs |
| **`TOKENS` is not valid JSON** | The page renders **on the theme's CSS alone**, and a content incident is recorded | The page must not fail, and the degradation must not be silent. Read it at `GET /api/v2/agent/diagnostics` |
| **Nesting deeper than 8 levels** | Ignored below that depth | A guard against a hand-written file with a cycle-shaped mistake |

The shape of those decisions is consistent: **a broken token costs you the token, a broken
file costs you the tokens, and neither costs you the page.**

---

## Tokens from a site you are replicating

Design tokens are also how [replication](/shio/docs/replication/) carries a captured site's
styling. `shio propose` reads the captured stylesheet and offers a DTCG token set beside
the block candidates, and `shio convert` writes it into `Theme.TOKENS`.

It reads two kinds of token, and they are **not equally useful**:

- **Declared custom properties** are the strong case. If the source's own CSS already says
  `var(--brand-primary)`, lifting that into `TOKENS` makes it genuinely editable:
  changing the token changes the replica. A modern site usually hands its whole palette
  over this way.
- **Recurring literals** are the weaker case. A colour repeated eleven times is probably a
  design decision, but the source's CSS does not say so, so the token is a *name for a
  value* rather than a control.

The report distinguishes them, because a reader deciding whether the replica is editable
needs to know which kind they got.

What it groups, and the DTCG `$type` it claims:

| Group | `$type` | Read from |
|---|---|---|
| `color.text` · `color.background` · `color.border` | `color` | Colour properties, grouped by the role the property implies |
| `font.size` | `dimension` | `font-size` |
| `font.family` | `fontFamily` | `font-family` |
| `font.weight` | `fontWeight` | `font-weight` |
| `spacing` | `dimension` | margins, paddings, gaps |
| `radius` | `dimension` | the `border-radius` family |
| `border.width` | `dimension` | the `border-width` family |
| `shadow` | `shadow` | `box-shadow`, `text-shadow` |

A `$type` is claimed **only when the reader can prove it** from the value (a colour, a
length). Otherwise it is left off rather than guessed.

Two details that came out of running this against real sites:

- **A declaration lifted into `TOKENS` is removed from the kept CSS.** Otherwise the
  prepended block and the surviving rule would both set the value, and editing the token
  would change nothing.
- **A colliding name takes its whole name as one flat segment.** DTCG forbids a group and a
  token sharing a name, so `--bg` and `--bg-alt` cannot become `bg` and `bg.alt`. They
  become `bg` and `bg-alt`. Splitting on every hyphen silently dropped seven of sixteen
  tokens on one real palette before this rule existed.

---

## When not to use tokens

Tokens are for values a person would call a **design decision**. They are not a general
CSS abstraction:

- **Layout structure** (a grid definition, a media query) belongs in the theme's CSS.
- **A one-off value used once** gains nothing from a name.
- **Anything with no single CSS value** (a composite shadow across several properties, an
  animation) is skipped by design; write it in CSS.

The test is whether somebody would ask you to change it. If they would, it is a token.

---

## Related Pages

| Page | Description |
|---|---|
| [Pages, Layouts & Regions](/shio/docs/website-development/#themes-and-design-tokens) | The Theme post type and how its CSS reaches a page |
| [Replication](/shio/docs/replication/) | Where a captured site's tokens come from |
| [Content Modeling](/shio/docs/content-modeling/) | The `Theme` post type's fields |
| [The Agent Surface](/shio/docs/agent-surface/) | `post.upsert`, and the diagnostics a malformed file lands in |
