---
title: Content in several languages
description: "The locale axis in Viglet Shio: every post carries a locale, a translation is a linked sibling post with its own URL, the CDA's ?locale, the locale segment in delivery URLs, and the template helpers that render a language switcher."
sidebar:
  label: Content i18n
---

Shio has one locale axis, and it is worth reading before you write the content rather
than after: the single decision that is expensive to change later is **the URL**, and it
is decided the moment the first translation is created.

---

## Every post has a locale

`locale` is a property of **every** post, not only of a translated one. It is a language
tag such as `en` or `pt-br`.

When nothing sets it, it is **`en`**. The column is nullable in the database, but no read
ever returns a blank one: an empty `lang` attribute does not mean *unspecified* to a
browser or a screen reader, it asserts that the language is unknown, which is worse than
a default that is merely not what you meant.

There is **no per-site default locale setting**. A site does not declare "this site is
Portuguese"; its posts each carry their own tag.

---

## A translation is a sibling post, not a field

This is the part that is not guessable, and the part that decides your URL structure.

Translating a post creates a **second post**. The two are linked by a shared
**translation group**, and each is otherwise an ordinary post: its own id, its own
fields, its own publish state, its own friendly URL.

What follows from that:

- **Each translation is published, previewed and deleted on its own.** Publishing the
  English page does not publish the Portuguese one.
- **Each translation has its own fields.** There is no partial-translation merge and no
  fallback at the field level; a translation whose body you left empty renders empty.
- **The translation group is internal.** It is a UUID, and no address a person writes
  ever contains one. You address a translation the way you address any post:
  `post:<site>/<its-own-url>`.

### Translations must have different URLs

**Two posts in one site cannot share a friendly URL, and being in different locales does
not exempt them.**

An address is `post:<site>/<friendly-url>` and it carries **no locale**, so two posts at
one URL are two posts the CMS cannot tell apart by name. `shio verify` reports them as a
`duplicate-url` finding, and says explicitly when the two claimants differ only by
locale, so you can see that the collision was a deliberate choice rather than an
accident. A move onto an occupied URL is refused for the same reason.

So give each translation its own path:

```
/about              (en)
/pt-br/sobre        (pt-br)
```

or

```
/about              (en)
/sobre              (pt-br)
```

Either works. What does not work is `/about` twice.

### Creating one

In the console, open the post and use **Translations → Add translation**, picking the
target locale. Through the API:

```http
POST /api/v2/post-unified/{id}/translate
{ "locale": "pt-br", "data": { … } }
```

`GET /api/v2/post-unified/{id}/translations` lists the siblings of a post's group.

---

## Declaring a site's languages

A site can say which languages it offers: a **default**, the **enabled** set, and optional
**fallbacks** (where a reader asking for one language should land when a page is missing in
it). Until a site declares this, Viglet Shio describes it by the languages its content is
already in, which is how every site behaved before the setting existed. Declaring is a
decision, and the difference matters: "offers Portuguese" can mean *we publish in Portuguese*
or *somebody translated two pages*.

- **In the console**, the site editor's **Languages** section edits the policy. It keeps a
  default inside the enabled set, and fallbacks whose both ends are offered. It only saves
  when you changed something.
- **Over REST**, `GET /api/v2/site/{id}/locales` reads the policy the site reports,
  `PUT` declares one, and `DELETE` stops declaring:

  ```json
  { "default": "pt-br", "enabled": ["pt-br", "en"], "fallback": { "pt-pt": "pt-br" } }
  ```

  Tags are lower-case (`pt-br`, not `pt-BR`); a mis-cased tag is refused with the spelling
  it expects. An optional `labels` map sets what the site's language switcher calls each
  enabled language, for example `{ "pt-br": "Português (Brasil)", "pt-pt": "Português (Portugal)" }`;
  a language without a label keeps its own name. The console edits it as **Switcher label**.
- **An agent** passes the same document as `data.locales` on `site.upsert`. It fills a site
  that has no policy and never replaces one that has, so an agent cannot quietly change a
  decision a curator made; changing it is done in the console.

Once a site declares its languages, a post's **Translations** menu offers only those, in the
site's order, with the default marked. An undeclared site still offers every language, since
narrowing it to what is already translated would stop it ever gaining a second.

---

## Working on a translation

- **Side by side.** From a translation's **Translations** menu, **Translate** opens the
  translation workspace: the source on the left, the translation on the right, field by field
  in the post type's own order. The source side is text, not a form, so you cannot edit the
  English believing it is the Portuguese. Each field can copy its value from the source, for
  the things a translation keeps verbatim (a product name, a URL, a number), and can be marked
  **reviewed**. Drag the divider to give either side more room; the console remembers the width
  in this browser. On a phone the two panes become one, with a switch between the source and
  the translation, and switching keeps what you have typed. Saving writes the
  translation's draft; publishing is the usual separate step.
- **When the source moved on.** If the page a translation was made from has changed since, the
  Translations menu marks that language, and marks the menu itself, so you see it before
  opening anything. Saving the translation is how it says it has caught up. The same fact is on
  the agent's read (`translationStale: true`) and in `shio verify`.
- **Which row is which.** In the content browser every post row shows its language beside its
  type, so a page and its translation (same title, same type) are never mistaken for a
  duplicate.
- **What is still to translate.** The site's **Languages** board, reached from its Languages
  section, has a row per page and a column per language the site offers. Each cell opens the
  translation, marks it as out of date, or, where it is missing, creates it from there. The
  totals count the whole site, not just the rows on screen. The same answer is
  `GET /api/v2/site/{id}/coverage` for one site, and the `missingLocale` filter on every content
  listing (`?missingLocale=pt-br` on `/agent/find`, `shio find`, and the console) for one
  language at a time.
- **Asking the agent for it.** Shio runs no model, but while an agent is connected the board
  can hand it the work. The robot button on a missing cell creates the translation's draft and
  asks the agent to write it; on a stale one it asks for the translation to be brought up to
  date. A row's button asks for that page in every language it is missing or behind in, and a
  column's for every page on screen in that language. The translation workspace has the same
  **Ask the agent**. Each request is a note on the draft, which already says which language to
  write and which page it comes from; the cell reads **Requested** until the agent answers in
  the dock, and nothing is published by asking.

---

## Reading a locale through the CDA

```http
GET /api/v2/cda/post/by-url?siteId=…&url=/about&locale=pt-br
```

- **With `locale`**, only that translation matches.
- **Without `locale`**, whatever is published at that URL resolves, whichever locale it
  is in.

**There is no fallback.** Asking for a locale that has no translation at that URL returns
nothing: a `404`, not the English page. That is deliberate: a silent fallback would make
a half-translated site look finished, and a front end that wants a fallback can implement
the one it actually wants.

Every post the CDA returns carries **`availableLocales`**: the locales its translation
group is published in, sorted. That is what a front end builds a language switcher from,
and it is how a link that points at a page translated in one locale and not another can
be detected before it is rendered.

**Listings take `locale` too.** A folder listing, `/query` and `/search` all accept
`?locale=pt-br` and return only posts in that language, paged by the server, so a page of 50
holds 50 Portuguese posts and `totalPosts` counts only them. Folders are not filtered, since a
folder has no language and hiding them would empty the navigation. On GraphQL, posts carry
`locale` and `availableLocales`, and `postByUrl` and `posts` take `locale`.
`@viglet/shio-client` passes it on `listChildren`, `query` and `getPostByUrl`.

**The site says which languages it offers.** `GET /api/v2/cda/site` and `/site/{id}` carry
`locales`: `defaultLocale`, `enabled`, `fallback`, and `declared` (whether the site declared
them or they are derived from its content). With a post's `availableLocales`, that is a whole
language switcher from the delivery API: the site says which languages exist, the post says
which of them this page is in, and `fallback` says where a missing one should go. For an
undeclared site the list is what your token can actually read, so a production token never
hears about a language that only exists in drafts.

---

## The locale segment in delivery URLs

Pages Shio renders itself are served at:

```http
GET /sites/{site}/{format}/{locale}/{path}
```

So the locale is in every published URL, which is usually the first place a reader meets
it, before deciding whether they want translations at all.

- `{locale}` is **checked against the site**: it is accepted when the site has published
  content in that locale, declares it, or it is the default. Anything else is a **404**, not
  a silent fall-through to the home page.
- **The segment picks the translation.** `/sites/mysite/default/pt-br/about` serves the
  Portuguese member of `/about`'s translation group. A translation has its own URL, so when it
  lives elsewhere the answer is a `302` to that URL, in the reader's language. When the page
  has no translation in that language, the site's declared **fallbacks** are tried in order, and
  if none answers, the page at the path is served as it always was.
- **A declared site is addressed in its own language.** Once a site declares a default, a
  request with no language, or with the legacy default label, is redirected (`302`) to the
  same path in the site's default language, and the links a reader follows stay there. A site
  that has not declared anything keeps the addresses it always had.
- `/sites/mysite/` is the home page; the segments are filled in for you by the site's own
  navigation.

The check is not cosmetic. Without it, `/sites/mysite/default/xx-yy/` answered the home
page with a `200`, so a mistyped locale looked like a working page.

**What the fallback hides, `shio verify` reports.** A site that declares a language and has
published nothing in it answers every URL in that language with `200` and another language's
page, because that is what the fallback is for. So `shio verify` warns about it:

- `locale-declared-unwritten`: the site declares a language no published page is written in.
  The message names the language a reader is actually served. Publish the translations, or
  take the language out of the site's `locales.enabled` until they exist.
- `locale-undeclared-content`: published content in a language the site does not declare. No
  switcher links to it and no locale segment reaches it. Declare the language, or move the
  content.

Only published pages count, since those are what a reader is served. Layouts, regions, themes
and files are not counted, because they are not written in a language. A site that has not
declared a policy never gets either warning, because its languages are the ones its content is in.

See [Pages, Layouts & Regions § Public delivery](/shio/docs/website-development/#public-delivery).

---

## Rendering a language switcher

In a Handlebars template:

```handlebars
<html lang="{{post.locale}}">
…
{{#translations}}
  <a href="{{link}}" hreflang="{{locale}}">{{title}}</a>
{{/translations}}
```

- **`post.locale`** is on every page and is never blank: safe to put straight into
  `<html lang>`.
- **`{{#translations}}`** takes no arguments: a translation set is a property of *this*
  page, not something the template selects.
- It **excludes the current page**, so a switcher does not need an `{{#unless}}` around
  every item. To mark the current language, compare against `post.locale`.
- Items are ordered **by locale tag**, not newest-first, so a picker's items do not move
  when somebody edits one translation.
- Each item's label is the **sibling's own title**, which is already in the target
  language by construction.

The same pair renders `hreflang` links for search engines, which is the other reason to
have the sibling set on the page rather than fetched.

---

## Related

- [Content Modeling](/shio/docs/content-modeling/): post types, fields and the publish states
- [Pages, Layouts & Regions](/shio/docs/website-development/): templates, helpers and the delivery grammar
- [Content Delivery API](/shio/docs/headless/content-delivery-api/): `?locale` and `availableLocales`
- [The content console](/shio/docs/content-console/): where the Translations menu lives
