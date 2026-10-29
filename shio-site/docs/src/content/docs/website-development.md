---
title: Pages, Layouts & Regions
description: "How Viglet Shio composes a page: Handlebars templates over Page Layouts, Regions and Themes, the helper set, sections, preview and public delivery."
sidebar:
  label: Pages, Layouts & Regions
---

Shio composes a page out of content it already holds. A **Page** finds its **Page
Layout**, the layout slots one or more **Regions**, and a **Theme** supplies the CSS, 
all four are ordinary content, so everything you can do to a post (write it over the
API, project it to a file, export it in a package, lint it) works on your templates
too.

Templates are **Handlebars**. There is no server-side JavaScript engine and no
sandbox to configure: a template interpolates the content model and calls a small,
fixed set of helpers.

:::note[What changed since the JavaScript engine]
Older versions of Shio rendered pages by **executing server-side JavaScript**
(Nashorn or an external Node.js runtime) and exposed an `shObject` API to that code.
That engine was removed, and it is not coming back, running arbitrary JavaScript
inside the CMS was a sandboxing liability, and a template that can do anything is a
template nothing can verify.

If you are moving from those docs:

| You used to write | You write now |
|---|---|
| `shio.website.javascript.engine` / `shio.website.nashorn` | **nothing**: the properties no longer exist |
| JavaScript in a layout's `JAVASCRIPT` field that built an HTML string | Handlebars in the layout's `HTML` field |
| `shObject.post.title` | `{{TITLE}}`: a page's own fields are named at the **root** of the model |
| `shObject.generatePostLink(id)` / `generateFolderLink(id)` | `{{link}}` on a row inside `{{#query}}` / `{{#navigation}}` |
| A Navigation or Query **Component API** attached to a region | the `{{#navigation}}` and `{{#query}}` **helpers**, called from the region's own template |

The model (pages, layouts, regions, themes, and `sh-region`) is unchanged. A site
exported from a 2018-era Shio renders with **no migration**; only the template
language changed.
:::

---

![The render pipeline: page, layout, region, theme, helpers, HTML, digest](/img/diagrams/shio-render-pipeline.svg)

## The five content types

`PageLayout`, `Region`, `Theme`, `Redirect` and `SiteScripts` are **system post
types** created on demand:

```http
POST /api/v2/render/post-types
```

The call is idempotent and never touches a type that already exists, so it is safe to
run on every deploy. Two companions answer the state of an existing instance:

| Call | Answers |
|---|---|
| `GET /api/v2/render/post-types/drift` | which render types are missing a field the current release declares |
| `POST /api/v2/render/post-types/reconcile` | add those missing fields (a repair, not a create) |

| Type | Fields | Job |
|---|---|---|
| `PageLayout` | `TITLE`, `HTML`, `THEME`, `JAVASCRIPT` | The page-level template. `HTML` holds the Handlebars; `THEME` names the theme to inline. |
| `Region` | `TITLE`, `HTML`, `JAVASCRIPT` | A reusable fragment slotted into a layout. `JAVASCRIPT` here is **browser** JavaScript, emitted inline once per region name. |
| `Theme` | `TITLE`, `CSS`, `JAVASCRIPT`, `TOKENS` | Styling. `TOKENS` holds W3C Design Tokens (DTCG): see below. |
| `Redirect` | `TITLE`, `TO`, `PERMANENT` | Its own friendly URL **is** the path it answers; `TO` is where the reader goes. |
| `SiteScripts` | `TITLE`, `SCRIPTS` | One ordered list of third-party scripts per site, as JSON. |

Layouts, regions and themes are addressed **by title**, because that is what a
`sh-region` attribute and a layout binding both name.

## Binding a layout to a post type

A page does not name its layout. The **site** does, in its `postTypeLayout` map:

```json
{ "Article": "Article Layout", "Page": "Home Layout" }
```

Every page of a type resolves to the same layout, which is why a thousand articles
cost one resolution, and why a template lint reports at the layout, the artefact you
actually edit.

## Slotting a region

A layout marks a slot with `sh-region`, naming the Region post by title:

```handlebars
<!DOCTYPE html>
<html>
  <head>
    <title>{{TITLE}}</title>
    <style>{{theme.css}}</style>
  </head>
  <body>
    <header sh-region="Site Header"></header>
    <main>{{{HTML}}}</main>
    <footer sh-region="Site Footer"></footer>
  </body>
</html>
```

Three things in that snippet are worth naming:

- **`sh-region` survives the render**, joined by `data-shio-region` and
  `data-shio-region-post`. Do not strip it: the rendered DOM is the only place the
  [Universal Editor](#editing-the-rendered-page) can learn which node maps to which
  post. A slot whose Region post does not exist is **flagged**
  (`data-shio-region-missing`) rather than silently left empty: a missing region and
  an empty one are different mistakes.
- **`{{theme.css}}` is raw** and inlined by the layout, not linked. (It has to be raw:
  CSS decodes no HTML entities, so an escaped `url("/fonts/inter.woff2")` is an invalid
  `@font-face` rule.)
- **A region renders against the page's model.** `{{TEXT}}` inside a region is the
  *page's* `TEXT`; inside a `{{#query}}` block, `{{title}}` is the listed post's. The
  engine never infers ownership: the template decides it.

## The model a template sees

A page's own fields are at the **root**: `{{TITLE}}`, `{{HTML}}`, `{{ABSTRACT}}`, 
whatever the post type declares. Five named frames sit beside them:

| Frame | Holds |
|---|---|
| `post` | `id`, `title`, `summary`, `url`, `type`, `folder`, `locale`, and `canonical` (an absolute URL that is never blank) |
| `site` | `name`, `url` (your deployed front end, blank until you have one), `baseUrl` (always resolvable), `scripts` |
| `theme` | `css`, `javascript`, and `tokens` as a dotted map, `{{theme.tokens.color.accent}}` |
| `render` | the renderer's own facts, including `defaultLocale` |
| `param` | **query-string values only**, as strings, sorted, read-only |

`param` is deliberately narrow: no headers, no cookies, no request object: a layout
is content, and content must not have the servlet request in scope. `state` and
`editor` are excluded because they are the renderer's own, and `{{{param.q}}}` (the
unescaped form) is **refused when you save the template**, since that value comes
from whoever holds the URL.

## The helpers

Nine helpers, and the list is closed. A call to a helper that does not exist fails when
the template is compiled **on save**, not at render time, with one deliberate gap: a
parameterless `{{#thing}}` is read as an ordinary iteration rather than a helper call,
so only the three historically-missing names (`search`, `form`, `getRelation`) are
reported in that form.

| Helper | Shape |
|---|---|
| `query` | `{{#query folder= type= limit= page= sort= field= value=}}…{{/query}}` |
| `navigation` | `{{#navigation folder=}}…{{/navigation}}` |
| `search` | `{{#search q= type= limit=}}…{{/search}}` |
| `getRelation` | `{{#getRelation FIELD}}…{{/getRelation}}` |
| `menu` | `{{#menu title=}}…{{/menu}}` |
| `translations` | `{{#translations}}…{{/translations}}` |
| `image` | `{{#image FIELD sizes=}}…{{/image}}` |
| `form` | `{{#form type=}}…{{/form}}` |
| `file_url` | `{{file_url FIELD}}` |

A listing row carries `title`, `link`, `summary` and the row's own fields, so the
old `generatePostLink` is now just `{{link}}`:

```handlebars
<ul>
  {{#query type="Article" limit=10 sort="publishDate"}}
    <li><a href="{{link}}">{{title}}</a> <span>{{summary}}</span></li>
  {{/query}}
</ul>
```

### Paging a listing

`{{#query}}` takes `page=` alongside `limit=`, and publishes the pager's facts as
Handlebars data variables you read inside the block: `@page`, `@pages`, `@total`,
`@hasPrev`, `@hasNext`, `@prevPage`, `@nextPage`, plus `@index`, `@first` and `@last`
per row. The counts are real totals, not a "has more" flag.

They are data variables rather than row values because the pager is a property of the
*listing*, not of any one post, and `{{#if @last}}` is how you draw it **once**, since
the block body repeats per row:

```handlebars
{{#query type="Article" limit=10 page=param.page sort="newest"}}
  <li><a href="{{link}}">{{title}}</a></li>
  {{#if @last}}
    <nav>
      {{#if @hasPrev}}<a href="?page={{@prevPage}}">Previous</a>{{/if}}
      page {{@page}} of {{@pages}} ({{@total}} articles)
      {{#if @hasNext}}<a href="?page={{@nextPage}}">Next</a>{{/if}}
    </nav>
  {{/if}}
{{/query}}
```

`page=` reads whatever the template gives it: `param.page` for a request-driven
archive, a literal for a fixed second page, or a field on the page itself.

A listing may contain a listing: each block keeps its own `@index`, `@last` and pager
counts, so an archive whose cards list their own children still draws the outer pager
once. Outside any `{{#query}}` block these variables are simply absent, so an
`{{#if @last}}` written after `{{/query}}` renders nothing.

`{{#image}}` exists because a template cannot know two things: an image's intrinsic
width, and the HMAC a signed transform URL needs. It emits whole URLs, `{{url}}` plus
a `{{srcset}}` whose candidates are **filtered below the intrinsic width**, so nothing
ever upscales. An image nothing measured (an SVG, say) yields the plain URL and an
empty `srcset`, which in HTML means "use `src`".

```handlebars
{{#image HERO sizes="(max-width: 800px) 100vw, 800px"}}
  <img src="{{url}}" srcset="{{srcset}}" sizes="{{sizes}}" alt="{{../TITLE}}" width="{{width}}" height="{{height}}">
{{/image}}
```

## Themes and design tokens

A `Theme`'s `TOKENS` field takes **W3C Design Tokens (DTCG)**: the shape Style
Dictionary, Figma and Tokens Studio already export, so a designer's file pastes in
with no dialect to learn:

```json
{ "color": { "accent": { "$value": "#0b5d3b" } }, "font": { "body": { "$value": "Inter, sans-serif" } } }
```

Shio compiles it to custom properties on `:root` (`color.accent` →
`--color-accent`) and **prepends** the block to the theme's own CSS, so existing
layouts get tokens without being edited. The same values are readable as
`{{theme.tokens.color.accent}}` for the places `var()` cannot reach. A theme with no
tokens emits nothing at all, and a `TOKENS` field that is not valid JSON renders the
page on the CSS alone while recording the failure: the page must not break, and the
degradation must not be silent.

The format, every rule the compiler applies, and how a replicated site's stylesheet
becomes tokens are on their own page: [Design Tokens (DTCG)](/shio/docs/design-tokens/).

## Third-party scripts

Browser JavaScript used to live in a `Theme` or a `Region`, both of which are
per-layout, so a site with four layouts held four copies, each with its own load
order. Since consent has to load *before* what it gates, that was a compliance
problem, not just duplication.

A `SiteScripts` post holds **one ordered list per site** in its `SCRIPTS` field, as
a JSON array. Each entry carries `src` or `inline`, plus optional `position`
(`head` · `bodyStart` · `bodyEnd`), `defer`, `async` and a consent `category`:

```json
[
  { "src": "https://cdn.example.com/consent.js", "position": "head", "category": "necessary" },
  { "src": "https://cdn.example.com/analytics.js", "position": "bodyEnd", "defer": true, "category": "analytics" }
]
```

The renderer groups them into the three positions, each always present even when
empty, so a layout's loop is never an expression resolving to nothing:

```handlebars
{{#each site.scripts.head}}<script src="{{src}}"{{#if defer}} defer{{/if}}></script>{{/each}}
```

**Shio never injects them.** A layout emits them where it wants; the delivery layer
reads the same post through the CDA. Nothing behaves one way in preview and another in
production, because there is no injection path to diverge. An entry with neither `src`
nor `inline` is skipped (and reported as `site-scripts-entry-empty`), and an
unrecognised position falls back to `bodyEnd`: never `head`, because a typo must not
move a script *earlier* than its author wrote it. `category` is carried for a layout
that gates its own emission; Shio itself never acts on it.

Because a layout has to emit them, `verify` reports a bound layout that never does:
that is `site-scripts-dropped`, and for a public site with a consent banner it is a
compliance defect that renders perfectly. Some layouts leave them out **on purpose**:
a transactional email template, a print stylesheet, an AMP variant, an embed a partner
puts in an iframe. Tick **Omits site scripts on purpose** on the `PageLayout` and the
check stops asking about that one, while every other layout in the site keeps being
checked.

## Sections

For pages assembled from blocks rather than written as one document, the
`@viglet/shio-sections` package ships a shared vocabulary: a `Page` type plus
`SectionHero`, `SectionFeatureGrid`, `SectionCta`, `SectionLogoWall`,
`SectionTestimonial`, `SectionFaq`, `SectionRichText` and `SectionMarkup`, with matching
unstyled, semantic React components behind stable `shio-*` class names.

`SectionMarkup` is the odd one and is worth knowing about even if you never write one by
hand: it declares a single `html` field and holds markup kept exactly as a captured page
wrote it. A replication puts one in the list for each run of markup nobody made
authorable, which is what lets a curator move or replace a raw block instead of meeting it
as a field they cannot reach.

A `Page` names its sections **by friendly URL, in order**, in a `Multi Select` whose
settings declare what it holds:

```json
{ "multiselect": { "references": "post" } }
```

That declaration is what makes the composition verifiable: `shio verify` resolves each
URL and reports the ones that are not there. A `Multi Select` without the key stays
tags; and if a type from this shipped vocabulary has lost the key on your instance,
`verify` reports that too (`vocabulary-drift`), because a page naming a section that does
not exist would otherwise report nothing at all. Sections are content a template reads: a region iterates them through
`{{#getRelation}}`, so a section-composed page renders through the ordinary path.

## Forms

`{{#form type="Contact"}}` renders the fields of a post type (each with its label,
input type, widget and whether it is required), so a contact form is the post type you
already modelled rather than a second form builder:

```handlebars
<form id="contact" action="{{site.formAction}}">
  {{#form type="Contact"}}
    <label for="{{name}}">{{label}}</label>
    <input type="{{input}}" name="{{name}}" {{#if required}}required{{/if}}>
  {{/form}}
  <input type="hidden" name="{{site.formHoneypot}}" value="" tabindex="-1"
         autocomplete="off" style="position:absolute;left:-9999px">
  <button type="submit">Send</button>
</form>
```

`site.formAction` is this site's submission endpoint and `site.formHoneypot` is the field name the
server checks, so neither the URL nor the honeypot's name is a literal you keep in step by hand.

The endpoint takes JSON and reads the token from a `Key` header, so submit with `fetch` rather than
letting the browser post the form natively:

```html
<script>
document.getElementById("contact").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.target;
  const data = Object.fromEntries(new FormData(form).entries());
  await fetch(form.action, {
    method: "POST",
    headers: { "Content-Type": "application/json", "Key": "YOUR_FORM_TOKEN" },
    body: JSON.stringify({ type: "Contact", data }),
  });
});
</script>
```

A submission becomes a **draft post**. It is visible to a curator in the console and
invisible to visitors until somebody publishes it, and it is an ordinary post for the
trash, export, permissions and the agent API. What arrived through a form is still
told apart from what a curator wrote: the console's **Forms** page lists one site's
submissions, with a **Move to trash** on each row for the ones dealt with, and the same
question is the `submission=true` filter on every content listing: `/agent/find`,
`shio_find`, `shio find` and the console search (`submission=false` leaves them out).

### Letting a site accept submissions

Two things, and neither lives in the template: a folder chosen in a template is a
folder anyone who can edit a template can choose:

1. **Configure the site**, on the Forms page's settings panel or with
   `POST /api/v2/site-form`: the site, the folder submissions land in, the post types it
   invites (comma-separated), the browser origins it accepts, and `enabled`. Creating the
   configuration does **not** turn it on; set `enabled` when you are ready. The folder
   must already exist (a submission never creates one), and `shio verify` reports
   `form-destination-missing` for an enabled form whose folder is gone, which the
   endpoint otherwise answers with an error only the visitor sees.
2. **Issue a `FORM` API token** for that site and put it in the page. It is public by
   construction, and that is safe because of what the scope cannot do: it cannot read
   the delivery API, cannot publish, cannot touch another site, and cannot post
   anywhere but this endpoint.

`PageLayout`, `Region`, `Theme`, `SiteScripts`, `Redirect` and `File` can **never** be
submitted, whatever the allow-list says: the first three are templates the renderer
executes.

Send the token as the `Key` header:

```bash
curl -X POST https://cms.example.com/api/v2/cda/form/acme   -H "Key: $SHIO_FORM_TOKEN" -H "Content-Type: application/json"   -d '{"type":"Contact","data":{"TITLE":"Hello","MESSAGE":"Please call"}}'
```

A success answers `{"received": true, "id": "...", "state": "DRAFT"}`. A refusal
answers `received: false` with a `title` and a `fix`: the site is not accepting, the
type is not invited, the configured folder is missing. Submissions are rate-limited per
token by the same budget as the rest of the delivery API.

`shio_hp` is a honeypot: render it hidden, and a submission that fills it in is
discarded. It answers exactly as a success does, deliberately, so a bot cannot tell.

:::note
A form on your own domain posting to the CMS is a cross-origin request, which needs
CORS configured on the instance.
:::

## Menus and translations

A `Menu` and its `MenuItem` children are ordinary content, read by a template rather
than by the server:

```handlebars
{{#menu title="Main"}}<a href="{{href}}">{{label}}</a>{{/menu}}
{{#translations}}<a href="{{link}}" hreflang="{{locale}}">{{locale}}</a>{{/translations}}
```

## Redirects

A `Redirect` post's **friendly URL is the path it answers**; `TO` is a friendly URL in
the same site or an absolute URL, and `PERMANENT` opts into a 301. The default is
**302**, deliberately: a permanent redirect is cached by browsers effectively for
ever, and a wrong one is the hardest routing mistake to take back. It is not a rewrite
engine (one exact path in, one address out, no patterns) which is what makes a
site's URL space checkable without issuing requests.

Both spellings of a path answer: if `/docs/` is stored and a reader asks for `/docs`,
Shio redirects to the stored form (permanently: two spellings of one path will not
stop meaning the same page), keeping the reader's query string.

## Seeing the page

Two routes, one renderer, opposite guards.

### Preview

```http
GET /preview/{site}/{path}
```

Authenticated, `no-store`, `X-Robots-Tag: noindex`, and it shows **drafts**, plus a
banner the page's own stylesheet cannot hide. This is how a site an agent just built
is visible with no front end and no client setup. It is preview-grade on purpose: a
freshness lifetime is how a preview quietly becomes infrastructure.

### Public delivery

```http
GET /sites/{site}/{format}/{locale}/{path}
```

Anonymous, **`PUBLISHED` only**, cacheable (`public, max-age=300`), indexable, no
banner and no editor bridge. `/sites/mysite/` is the home page. Two variants: the
`x-sh-site` header names the site so the URL carries only the content path, and
`sh-format` replaces the format segment.

`{format}` is vestigial: it must be `default`, and anything else is a **404**. That
strictness is load-bearing: a page served from `/sites/mysite/` carrying
`<img src="/logo.png">` requests `/sites/mysite/logo.png`, which is exactly this
three-segment shape, and a lenient format slot answered those with the *home page* at
200. A browser expecting an image got HTML, and a missing asset stopped being
something an author could see. `{locale}` is checked the same way: accepted when the
site has published content in that locale, or when it is the default. See
[Content in several languages](/shio/docs/content-i18n/) for what fills that segment.

Root-relative `href`s are rebased into whichever space is serving the page, so a link
that is wrong 404s **inside** the site , where the route lint already reports it ,
instead of silently walking the reader out of it.

## Static files and images

A file uploaded to Shio is a `File` post, and its bytes are reachable two ways:

| URL | Use |
|---|---|
| `/file_source/{postId}/{fileName}` | The canonical, id-keyed path. `{{file_url FIELD}}` points here. |
| `/sites/{site}/{folder-chain}/{fileName}` | The same bytes at the path a site's own structure implies: what a replicated site's verbatim `<img src="/img/hero.png">` needs. |

Either can be transformed on the way out with query parameters, `?w=`, `?h=`,
`?format=`, `?crop=`, which is the pipeline `{{#image}}` writes URLs for.

Running the instance rather than writing the template? The result cache, the dimension
and format ceilings, and how to require signed transform URLs are in
[Search & Caching § Image transforms](/shio/docs/search-caching/#image-transforms-the-operators-half).

## Editing the rendered page

Add `?editor=true` to a preview URL and Shio injects `/preview/_shio/editor.js`; an
ordinary preview ships no editor code at all. Annotate a template and the fields
become directly editable in the **Universal Editor**:

| You write | The browser gets |
|---|---|
| `sh-region="Header"` | `data-shio-region`, `data-shio-region-post` |
| `sh-field="TEXT"` | `data-shio-prop` |
| `sh-post="{{id}}"` | `data-shio-post` |

The page's own post is stamped on `<body>`, so the common case needs no `sh-post`, and
resolution is nearest-ancestor.

## Proving it renders

A template is checked **when you save it**: Shio compiles a `PageLayout`'s or
`Region`'s `HTML` on write and refuses a broken one with the line, the column and a
caret. A dry run reports the same thing as a plan note instead of failing.

What cannot be decided at write time is checked by `shio verify`, because a region's
template interpolates fields belonging to whatever page is bound to the layout that
slots it:

| Finding | Means |
|---|---|
| `template-unknown-field` | a template names a field no post type declares (an error at the top level, a warning inside `{{#query}}`) |
| `unused-region` / `unused-layout` | content nothing renders through |
| `missing-theme` | a layout names a `THEME` that is not there |
| `duplicate-template-title` | two layouts or regions share a title, so a binding is ambiguous |
| `dangling-reference` | a section or file reference that resolves to nothing |
| `section-not-accepted` | a warning: a page composes a section of a type its layout's `sh-accepts` does not name. The finding lists the types the layout does accept; replace the section, or add its type to `sh-accepts`. A layout that declares nothing reports nothing |
| `site-scripts-dropped` | a bound layout whose HTML never emits `site.scripts`; tick **Omits site scripts on purpose** on the layout if that is deliberate |
| `dangling-relation` | a `{{#getRelation "/some/url"}}` naming an address the site has no post at, so the block silently renders its `{{else}}` on every page |
| `redirect-target-missing` | a `Redirect` pointing at a page that is not there |

And a page's structure can be compared without a screenshot:

```http
GET /api/v2/agent/render
```

returns two digests , `d:` over the page's structure and `a:` over its appearance ,
kept separate so a restyle and a structural break are never confused for each other.

---

## Related Pages

| Page | Description |
|---|---|
| [Core Concepts](/shio/docs/getting-started/core-concepts/) | Sites, Folders, Posts and Post Types |
| [Content Modeling](/shio/docs/content-modeling/) | Post Types, fields and the publishing workflow |
| [Content Delivery API](/shio/docs/headless/content-delivery-api/) | Serving this content to your own front end |
| [Configuration Reference](/shio/docs/configuration-reference/) | Application properties |

---
