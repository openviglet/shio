---
title: REST API Reference
description: "The console REST API: unified posts, folders, sites, post types, static files, the trash, review sessions and tokens, plus the endpoints that deliberately do not exist."
---

Shio has **four** HTTP surfaces, and picking the right one is most of the work:

| Surface | Path | Use it when |
|---|---|---|
| **Delivery (CDA)** | `/api/v2/cda/**`, `/graphql` | You are building a front end. Read-only, cacheable, [documented here](/docs/headless/content-delivery-api/) |
| **Agent** | `/api/v2/agent/**` | You are building an agent. Addressed, atomic, self-describing, [documented here](/docs/agent-surface/) |
| **MCP** | `POST /mcp` | Your client speaks MCP, [documented here](/docs/mcp/) |
| **Console** | `/api/v2/**` | **This page.** What the React console calls: id-keyed CRUD, administration |

If you are writing a script or an integration, the agent surface is almost always the
better target: it addresses content by path, it is transactional, and its errors tell you
how to fix them. The console API is here because the console uses it and because some
administrative capabilities live only here.

---

## Authentication

| Caller | How |
|---|---|
| The console | Session cookie + CSRF token (`POST /api/v2/login`) |
| A script | HTTP Basic against the same endpoints |
| A non-browser caller on the **agent** or **delivery** surfaces | A `Key` header, no cookie and no CSRF: see [Security](/docs/security/) |

The console API is session-shaped: it expects a cookie and a CSRF token on writes. That is
deliberate, and it is the reason the agent surface exists as a **stateless** alternative
rather than as a convenience wrapper.

---

## Posts

One controller owns posts, in both states, at `/api/v2/post-unified`:

| Method | Path | Does |
|---|---|---|
| `POST` | `/api/v2/post-unified` | Create |
| `GET` | `/{id}` | Read one |
| `PUT` | `/{id}` | Update |
| `DELETE` | `/{id}` | Move to the trash |
| `GET` | `/folder/{folderId}` | List a folder's posts (paged) |
| `POST` | `/{id}/publish` | Publish the draft |
| `POST` | `/{id}/unpublish` | Withdraw the published row |
| `POST` | `/{id}/schedule` | Publish (or unpublish) at a timestamp |
| `GET` | `/{id}/versions` | Version history |
| `POST` | `/{id}/restore` | Restore an earlier version |
| `GET` | `/trash` | What is in the trash |
| `POST` | `/{id}/untrash` | Take it back out |
| `DELETE` | `/{id}/purge` | Remove permanently |
| `GET` | `/{id}/translations` | Sibling pages in other locales |
| `POST` | `/{id}/translate` | Create a translation sibling |

## Objects: listings, copy, move, ACL

`/api/v2/object` handles the operations that apply to *anything* addressable: a post, a
folder, a site:

| Method | Path | Does |
|---|---|---|
| `GET` | `/{id}/list` | The console listing for a container (paged) |
| `GET` | `/{id}/path` | Breadcrumb |
| `PUT` | `/copyto/{destId}` | Copy the given objects into a destination |
| `PUT` | `/moveto/{destId}` | Move them |
| `GET` `PUT` | `/{id}/acl` | Read and set content permissions |

Copy and move **authorize both ends** (the source and the destination) and a copy takes
names that are free rather than overwriting.

## Folders

| Method | Path | Does |
|---|---|---|
| `GET` | `/api/v2/folder/{id}` | Read one |
| `POST` | `/{parentFolderId}` | Create beneath a parent |
| `PUT` | `/{id}` | Rename or update |
| `DELETE` | `/{id}` | Move the subtree to the trash |
| `GET` | `/{id}/path` | Breadcrumb |
| `GET` | `/trash` · `POST /{id}/untrash` · `DELETE /{id}/purge` | The trash, for folders |

## Sites

| Method | Path | Does |
|---|---|---|
| `GET` | `/api/v2/site` | List |
| `GET` | `/{id}` | Read one, accepts an id, a friendly URL or a name |
| `POST` | `/api/v2/site` | Create (seeded from the bootstrap template) |
| `PUT` | `/{id}` | Update, including the post-type → layout bindings |
| `DELETE` | `/{id}` | Delete the site and everything in it |
| `GET` | `/{id}/export` | Download the site as an exchange package (`application/zip`) |

`POST /api/v2/import` accepts one of those packages back.

## Post types

**One name-keyed surface.** There is no id-keyed twin:

| Method | Path | Does |
|---|---|---|
| `GET` | `/api/v2/post-type` | List every type |
| `GET` | `/{name}` | One type and its fields |
| `POST` | `/api/v2/post-type` | Create |
| `PUT` | `/{name}` | Replace the definition |
| `POST` | `/{name}/rename` | **Rename**: a move, keeping the type's content |
| `DELETE` | `/{name}` | Delete; a type content points at answers a teaching `409` |

A type marked *managed by code* refuses a console `PUT` with a `409`: it is owned by
`shio push`, and the console shows it read-only rather than letting two writers race.

## Static files

`/api/v2/staticfile/**` uploads and manages file bytes. An upload creates a `File` post,
and the bytes are then reachable two ways:

| URL | For |
|---|---|
| `/file_source/{postId}/{fileName}` | The canonical, id-keyed path |
| `/sites/{site}/{folder-chain}/{fileName}` | The same bytes at the path the site's own structure implies |

Both accept transform parameters, `?w=`, `?h=`, `?format=`, `?crop=`.

## The rest

| Root | Owns |
|---|---|
| `/api/v2/render/post-types` | Provision the render model; `/drift` and `/reconcile` |
| `/api/v2/review` | Review sessions: list, read, `approve`, `revert` |
| `/api/v2/history` | The audit trail |
| `/api/v2/api-token` · `/preview-token` | API keys and short-lived preview tokens |
| `/api/v2/site-webhook` | Per-site webhook subscriptions |
| `/api/v2/widget` | The widget catalogue a post type's fields reference |
| `/api/v2/group` · `/role` | Users, groups and roles |
| `/api/v2/locale` | The locale axis |
| `/api/v2/tenant` | Tenants (admin): see [Multi-Tenancy](/docs/multi-tenancy/) |
| `/api/v2/provider/auth` · `/provider/exchange` · `/config/email` | Configuration providers |
| `/api/v2/discovery` · `/system/info` · `/ping` | Instance metadata and health |
| `/api/v2/setup` | First-run setup |

Interactive documentation for every endpoint above is served by the instance itself at
`/swagger-ui.html`, generated from the controllers, which makes it the authority when this
page and the tree disagree.

### Ids are the server's to assign

On a `POST`, leave the id out. The instance mints one and returns it, and a blank string is treated
as "not set" rather than stored: a row keyed on an empty string is listed by the collection
endpoint and reachable by nothing, since `/api/v2/thing/` does not match `/api/v2/thing/{id}`. If
you deliberately assign your own key, send a non-empty one and it is kept.

On a `PUT`, the id in the **path** is the row you are updating. A different id in the body does not
redirect the write.

---

## Three endpoints that do not exist

An absence cannot be discovered by reading, so it is worth stating. Inventing one of these
is the most common wrong turn:

| Does **not** exist | Use instead |
|---|---|
| `POST /api/v2/post` | `POST /api/v2/post-unified`, or `post.upsert` on the agent surface |
| `DELETE /api/v2/object/{id}` | Delete by type: `DELETE /api/v2/post-unified/{id}` or `DELETE /api/v2/folder/{id}` |
| A console post-type controller at `/api/v2/post/type` | `/api/v2/post-type/**`: the id-keyed twin was retired |

---

## Related Pages

| Page | Description |
|---|---|
| [The Agent Surface](/docs/agent-surface/) | The addressed, transactional alternative |
| [Content Delivery API](/docs/headless/content-delivery-api/) | The read-only delivery contract |
| [Security](/docs/security/) | Sessions, tokens, scopes, CSRF and CORS |
| [Administration Guide](/docs/administration-guide/) | What these endpoints look like in the console |
