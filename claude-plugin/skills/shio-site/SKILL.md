---
name: shio-site
description: Build or change pages in a Shio CMS site — create sections, write content, publish. Use when the user asks to add, edit, restructure or publish pages, or to build a site (blog, docs, marketing site, landing page, product catalogue) in Shio. Also use before writing content by hand, to check whether a blueprint already covers it.
---

# Building and changing a Shio site

Shio is a CMS whose primary operator is an agent. Everything below is one call over MCP;
none of it needs the console.

## Always start here

1. `shio_context` — the content model, the sitemap by path and the instance's conventions,
   in one call. Store the `digest` it returns. Next session, compare it before re-reading
   anything.
2. Read `shio://blueprints`. **Before modelling anything by hand**, check whether a package
   already covers it: `blog`, `docs`, `marketing-site`, `landing`, `product-catalogue`. A
   blueprint brings the post-types, the folder tree and sample pages in one call, and
   applying one twice changes nothing the second time.

## Addressing

Never carry a UUID between calls. Content is addressed by path:

- `post:<site>/<friendly-url>` — a page, by its URL
- `folder:<site>/<name-chain>` — a folder, by its chain of folder names
- `site:<name>` — the site itself

A folder path and a friendly URL are **separate namespaces**: a post at `/blog/hello` may
live in any folder. Writes accept only `post:` and `folder:` — never the lenient `site:`
form — because a write that guessed wrong would create content in the wrong place silently.

## Writing

Prefer `shio_apply` over `shio_write`. It takes a *statement* about what a section should
contain rather than a script of operations, so re-sending it after a context compaction is
safe and the second run reports `changed:0`.

```
shio_apply { site, folders: ["/blog"], posts: [{ url, type, folder, data }], dryRun: true }
```

Rules that matter:

- **Always `dryRun: true` first.** Read the plan. Every line should be a change you
  intended. If any line says `delete`, stop and ask the user.
- **Writes land in DRAFT.** Publishing is a separate, explicit step (`shio_publish`). Do
  not publish unless the user asked.
- **To rename or move a page, use `post.move`** (`shio_write`) — never upsert at the new
  address. An upsert at a new address creates a *second* post and orphans the first with
  its id, its history and its audit trail.
- **`prune` deletes.** It only ever removes undeclared content inside the folders your
  document declares, and every deletion appears in the plan first. Do not pass it casually.
- **Deleting takes two calls, by design.** A call whose plan contains a `delete` is refused
  unless it carries the `confirm` value that the *dry run of that same call* returned. So:
  dry-run it, show the user exactly what would go, then re-send with `confirm`. If the
  server answers "not for this plan", the content changed underneath you — plan again and
  show the user the new list rather than retrying with the old token.
- **You may not be allowed to publish.** Call `GET /agent/session` (or read the refusal) —
  an agent credential can be issued without publish permission, in which case everything you
  write stays in DRAFT and the right move is to tell the user what is ready, not to retry.

## Finishing

Run `shio_verify` before telling the user you are done, and `shio_digest` on any page whose
structure matters. `ok:false` means at least one error; every finding carries a `fix`.
Then say what you did **and what you did not check**.

## From the terminal

The same capabilities are in the `shio` CLI, which is often cheaper for bulk work:

```
shio blueprints                       # the catalogue
shio apply --blueprint blog --site Acme --param blogTitle="The Acme Blog" --dry-run
shio pull --content                   # project the content to shio/content/**.md
# ...edit with Edit/Write/Grep — zero API tokens...
shio push --content --dry-run         # plan, then apply
shio verify                           # exits non-zero on an error
```

Editing files under `shio/content/**` and pushing once is the cheapest way to author more
than two or three pages.
