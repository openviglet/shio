# @viglet/shio-sections

[![npm](https://img.shields.io/npm/v/@viglet/shio-sections?logo=npm)](https://www.npmjs.com/package/@viglet/shio-sections)
[![License](https://img.shields.io/npm/l/@viglet/shio-sections)](https://github.com/openviglet/shio/blob/HEAD/LICENSE)
[![Website](https://img.shields.io/badge/website-0ea5e9?logo=googlechrome&logoColor=white)](https://openviglet.github.io/shio/)

The **page-section vocabulary** for **[Viglet Shio](https://openviglet.github.io/shio/)**,
the open source, **agent-ready CMS**: composable section
post-types authored with [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model),
plus the matching **React** components.

One shared language for the agent, the console and the front end — **a page is an
ordered list of known sections** instead of an ad-hoc content model per site. An
AI agent driving Shio through [Claude Code](https://claude.com/claude-code) and
MCP writes `Hero`, `FeatureGrid`, `Cta`; the console shows the same names; the
front end renders them with the components below.

## Install

```bash
npm install @viglet/shio-sections
# React is an optional peer — only needed for the render half
npm install react
```

## The vocabulary

Eight sections, plus the page and menu types that hold them:

| Post-type | Factory | React component | For |
|---|---|---|---|
| `SectionHero` | `sectionHero()` | `<Hero>` | Headline, subheadline, image, call to action |
| `SectionFeatureGrid` | `sectionFeatureGrid()` | `<FeatureGrid>` | A grid of features or services |
| `SectionCta` | `sectionCta()` | `<Cta>` | A single call-to-action band |
| `SectionLogoWall` | `sectionLogoWall()` | `<LogoWall>` | Customer / partner logos |
| `SectionTestimonial` | `sectionTestimonial()` | `<Testimonial>` | Quote, author, portrait |
| `SectionFaq` | `sectionFaq()` | `<Faq>` | Question / answer rows |
| `SectionRichText` | `sectionRichText()` | `<RichText>` | Free-form HTML body |
| `SectionMarkup` | `sectionMarkup()` | `<Markup>` | Markup kept verbatim from a captured page (SH763) |
| `Page` | `page()` | `<Sections>` | The ordered list of sections |
| `Menu` / `MenuItem` | `menu()` / `menuItem()` | — | Navigation |

`SECTION_TYPES`, `PAGE_TYPE`, `MENU_TYPE` and `MENU_ITEM_TYPE` export the names;
`sectionPostTypes()` returns every definition at once.

## Author the content model

```ts
// shio/post-types/sections.ts
import { sectionPostTypes } from "@viglet/shio-sections";

export default sectionPostTypes();   // Page, Menu, MenuItem and all seven sections
```

Then push it to your Shio instance with the
[CLI](https://www.npmjs.com/package/@viglet/shio):

```bash
npx @viglet/shio push --dry-run   # preview
npx @viglet/shio push             # apply
```

## Render the front end

```tsx
import { Sections } from "@viglet/shio-sections";
import { ShioClient } from "@viglet/shio-client";

const shio = new ShioClient({ baseUrl: process.env.SHIO_URL! });

export default async function Page({ params }: { params: { slug?: string[] } }) {
  const post = await shio.getPostByUrl(
    process.env.SHIO_SITE_ID!,
    "/" + (params.slug?.join("/") ?? ""),
  );
  if (!post) return null;
  return <Sections post={post} />;
}
```

`<Sections>` dispatches each row of the page to the right component through
`SECTION_COMPONENTS`. Use a single component directly when you want to:

```tsx
import { Hero, FeatureGrid } from "@viglet/shio-sections";

<Hero data={heroData} className="my-hero" />
<FeatureGrid data={featureData} />
```

### Assets

Every component that renders an image takes a `resolveAsset` prop so you decide
how a stored file reference becomes a URL (CDN, image proxy, Next.js loader).
`defaultResolveAsset` is the identity-ish default.

## API

```ts
// Content model (post-type definitions)
import {
  SECTION_TYPES, PAGE_TYPE, MENU_TYPE, MENU_ITEM_TYPE,
  page, menu, menuItem, sectionPostTypes,
  sectionHero, sectionFeatureGrid, sectionCta, sectionLogoWall,
  sectionTestimonial, sectionFaq, sectionRichText,
} from "@viglet/shio-sections";

// Render
import {
  Sections, SECTION_COMPONENTS,
  Hero, FeatureGrid, Cta, LogoWall, Testimonial, Faq, RichText,
} from "@viglet/shio-sections";

// Helpers and types
import { defaultResolveAsset, rows, str } from "@viglet/shio-sections";
import type {
  SectionsProps, SectionProps, SectionPost,
  HeroData, FeatureGridData, CtaData, LogoWallData,
  TestimonialData, FaqData, RichTextData,
} from "@viglet/shio-sections";
```

## Related packages

| Package | For |
|---|---|
| [`@viglet/shio`](https://www.npmjs.com/package/@viglet/shio) | The Shio CLI and MCP bridge |
| [`@viglet/shio-model`](https://www.npmjs.com/package/@viglet/shio-model) | The DSL these post-types are authored with |
| [`@viglet/shio-client`](https://www.npmjs.com/package/@viglet/shio-client) | Zero-dependency Content Delivery API client |
| [`@viglet/shio-react-sdk`](https://www.npmjs.com/package/@viglet/shio-react-sdk) | React hooks and render components |
| [`@viglet/create-shio-app`](https://www.npmjs.com/package/@viglet/create-shio-app) | Next.js starter generator |

## Develop

```bash
npm run build      # vite lib build + .d.ts
npm run typecheck
npm test           # node:test
```

## License

Apache-2.0 — [Viglet Shio](https://openviglet.github.io/shio/) ·
[github.com/openviglet/shio](https://github.com/openviglet/shio) ·
[openviglet.github.io/shio/docs](https://openviglet.github.io/shio/docs/) ·
part of the [Viglet](https://www.viglet.org) open source family.
