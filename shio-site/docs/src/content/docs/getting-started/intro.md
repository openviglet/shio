---
title: "What is Viglet Shio?"
description: Viglet Shio is an open-source, agent-ready CMS. A coding agent builds the site, a curator approves it, and Shio renders it or the Content Delivery API serves it.
sidebar:
  order: 1
---

**Viglet Shio** is an open-source, agent-ready Content Management System, with one
difference that shapes everything else: every capability is built for a **coding agent**
first, and a person can still do all of it from the console.

Most CMSs are designed for a person clicking through forms, with an API added afterwards
for developers. Shio is designed the other way round. An agent, Claude Code, Cursor,
anything that speaks MCP, builds and maintains the site. A curator **approves**: reviewing,
correcting, publishing. And the Content Delivery API serves the result to whatever front
end you like.

Shio hosts **no model and no prompts**. It is not an AI product; it is the substrate an
external agent drives, running on your own infrastructure with your content in your own
database.

---

## The problem it solves

Building a site with a conventional CMS through an API is a long conversation. What post
types exist? What fields does this one have? What is this page's id? Which endpoint
creates a folder? Every question is a round trip, and every answer is a UUID that has to
survive to the next call.

Shio's answer is to make the instance **describe itself in one call**, to address content
by **path instead of id**, and to make every write **idempotent**, so an agent can plan
work, do it, and check it without interrupting anyone for anything except the decisions
that are genuinely a person's.

---

## Who does what

| | Does | Through |
|---|---|---|
| **The agent** | Builds the model, writes the pages, wires the templates, checks its own work | MCP, the REST agent surface, content projected to files, the `shio` CLI |
| **The curator** | Reviews, corrects wording, decides what goes live | The console, the Universal Editor, preview links |
| **The delivery API** | Serves published content to readers | REST + GraphQL, a TypeScript client, React hooks, a Next.js starter |

The division is enforced, not suggested: **an agent's writes land as drafts**, and
publishing is a permission an agent may not even have. See
[Letting an agent in](/shio/docs/agent-safety/).

---

## What you can do with it

### Model your content
Define **post types** with the fields you need, text, rich text, files, relationships,
dates, code. A post type is a reusable shape that both a curator's form and an agent's
write are validated against.

### Build pages, or don't
Shio can render pages itself, from **Handlebars** templates over page layouts, regions and
themes, which is how a site an agent just built is visible with no front end at all. Or
serve nothing but JSON and let your own front end own the markup. Both are first-class;
see [what "headless" means here](#what-headless-means-here).

### Author content as files
Project a whole site to disk as Markdown, edit it with the tools you already use, and push
it back, with a three-way merge that never silently overwrites a curator's edit.

### Adopt a site that already exists
Point Shio at a live website and it will capture it, propose which blocks could become
editable content, and convert it into a site a curator can maintain.

### Query it from anywhere
REST and GraphQL, with per-site and per-environment API keys, ETags and rate limiting
included.

---

## The vocabulary, in the order you need it

### Sites, folders, posts

A **site** is one website's worth of content. Inside it, **folders** organise content into
a tree, sections, categories, whatever structure suits you. A **post** is a single piece
of content: a page, an article, an image, a template.

Two things about that tree are worth knowing early:

- A post's **friendly URL** is unique within its site, and it is *not* its folder path.
  Folders organise; URLs address. Moving a post between folders does not have to change
  its URL.
- **A file is a post too.** An uploaded image is a `File` post with fields of its own.

### Post types

A **post type** declares the fields a post may have. It is Shio's **one name-keyed
surface**: types are referenced by name (`Article`, `Page`, `PageLayout`) everywhere, in
the API, in the CLI, in the files on disk, and never by id. That is deliberate, and it is
what lets a content model live in git as `shio/post-types/Article.json`.

Some post types are **system types**, created by Shio itself: `PageLayout`, `Region`,
`Theme`, `Redirect`, `SiteScripts`. They are ordinary content, which is why your
templates can be exported, diffed and linted like anything else.

### Addresses

Everything is reached by an **address**:

| Form | Names |
|---|---|
| `post:mysite/blog/hello-world` | one post |
| `folder:mysite/blog` | one folder |
| `site:mysite` | a site |
| `id:9f8c…` | anything, by raw id |

Three rules that save an afternoon:

1. **Writes take only `post:` and `folder:`** (plus `site:` for the two site operations).
2. **A site's home page is `post:mysite/`**: the trailing slash with nothing after it.
3. An `id:` is a read convenience. Addressing writes by path is what removes the "which
   UUID was that?" problem entirely.

### Draft and published

A post can exist in two states at once: a **draft** and a **published** version. Readers
see the published one. The draft is where work happens.

- Every write from an agent, and every save in the Universal Editor, goes to the **draft**.
- **Publishing is an explicit act**, and it can be scheduled.
- A **preview** shows the draft exactly as it will look, before anyone else can see it.
- **Unpublishing** withdraws the live version and keeps the draft.
- A **delete** goes to the trash, not away.

### Page layouts, regions and themes

If Shio is rendering your pages, three system types do the work:

- A **Page Layout** is the page-level Handlebars template.
- A **Region** is a reusable fragment the layout slots by name, through a `sh-region`
  attribute.
- A **Theme** supplies the CSS, and design tokens, if you use them.

A page does not name its own layout: the **site** binds a post type to a layout, so every
`Article` renders through the same one. See
[Pages, Layouts & Regions](/shio/docs/website-development/).

### What "headless" means here

Headless normally means "no rendering". Shio renders, so the honest description is that
**presentation is never coupled to content**:

| Route | Serves | Guards |
|---|---|---|
| `/api/v2/cda/**` + `/graphql` | JSON, for your own front end | API key, per-site and per-environment scoping |
| `/sites/{site}/…` | Published pages, rendered by Shio | Anonymous, cacheable, indexable |
| `/preview/{site}/…` | **Drafts**, rendered by Shio | Authenticated, never cached, never indexed |

Nothing forces you to pick one. A site can be delivered by a Next.js app over the API
while a curator uses the preview route to check a draft, and a replicated site can be
served straight from Shio while you decide what to do with it.

---

## Where to go next

The documentation is organised the way the work is:

| Hub | Start here if you want to |
|---|---|
| **[The agent builds it](/shio/docs/agent-builds-it/)** | Connect an agent, drive the API, author content as files, use the CLI |
| **[The curator approves it](/shio/docs/curator-approves-it/)** | Understand the rails, curate content, edit a rendered page |
| **[The CDA delivers it](/shio/docs/cda-delivers-it/)** | Build a front end against the delivery API |
| **[Run it](/shio/docs/run-it/)** | Install, configure, secure and operate an instance |

Or read [Core Concepts](/shio/docs/getting-started/core-concepts/) next, which takes the vocabulary above one
level deeper.
