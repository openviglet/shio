---
title: Administration
description: "The operator's half of the Viglet Shio console: home, users, groups, roles, permissions, API tokens, tenants, providers, email, webhooks, activity and the agent review queue."
sidebar:
  order: 1
---

Everything an operator configures lives in the Shio console under **Administration**,
at `/bento/admin` on your instance. This page walks those screens in order.

The curator's half (the content browser, the post form, copy and move, the trash) is on
[The content console](/shio/docs/content-console/).

---

## Finding your way around

The **navigation rail** down the left carries **Home** and two areas (**Content** and
**Administration**) rather than a list of every screen. Each area opens its **hub**, which
lists every screen of the area as a tile in small labelled groups; Administration's are
*Access*, *Agent and audit*, *Integrations* and *This instance*, and `/bento/admin` lands
there. To reach a named screen directly, use the **command palette**: <kbd>Ctrl</kbd>/<kbd>Cmd</kbd>+<kbd>K</kbd> from anywhere, or
<kbd>/</kbd> when you are not typing in a field. It searches descriptions as well as
titles, so *credentials* finds API Tokens.

| Area | Screens |
|---|---|
| **Content** | Sites · Search · Post Types · Media Library · Trash · Universal Editor |
| **Administration** | Users (users, groups, roles) · API Tokens · Tenants · Authentication Providers · Exchange Providers · Email · Webhooks · Activity · Agent Review · GraphQL |

Every list of records here (users, groups, roles, tokens, providers, webhooks, tenants,
and Content's sites) is a **table** you can sort by its column headers, with the create
action at the top right. Every editor keeps **Save** in its header, and a bar with Save
fades in once you scroll the header away; it tells you when a field has an **unsaved
change**. Delete, and a tenant's Suspend, are in the header's **⋯** menu.

**Tenants appears only on a multi-tenant instance.** `shio.multi-tenant` is off by
default, and a single-tenant install is not offered a screen for managing tenants it
does not have.

:::note[Addresses moved from `/console` to `/bento`]
`/console/admin/tokens` is now `/bento/admin/tokens`, and so on. Old addresses redirect,
carrying any query string and anchor. If your instance is multi-tenant, note that the
`/{tenant}/console/…` prefix is gone: which tenant you are in comes from your session,
not from the URL.
:::

## Home

The console's landing screen, at `/bento`. It shows how many sites and post types this
instance has, a list of recent content changes, and shortcuts to the things you do most:
create a site, browse content, search, model a post type, manage webhooks, open the
activity log.

Every number on it is a link into the screen it counts.

---

## Users

Create, modify and delete users under **Administration → Administration**. A user can
sign in to the console, be the subject of a permission grant, and own content.

### Default user

On first startup Shio creates a default administrator account:

| Field | Value |
|---|---|
| **Username** | `admin` |
| **Password** | `admin` |

:::caution
Change the default password immediately after first login.
:::

### User properties

- Username and password
- Email address
- First and last name
- Group memberships

---

## Groups and roles

**Groups** collect users. **Roles** name a capability. Both exist so a permission grant
can be written once against many people rather than once per person, and both are
managed on the same Administration screen.

A permission grant accepts a **user**, a **group** or a **role** as its subject, so which
of the three you use is a question of how you want to maintain it, not of what it can
express.

A **group's page lists its members**. Add one by choosing a user who is not yet a member,
and remove one from its row; each change is saved at once. Each member's name links to
their user page. Below the members, the page lists the **roles** that name the group, which
is what its members may do; each links to the role, where that relation is changed.

---

## Permissions

Shio has two independent permission layers. They answer different questions and are
configured in different places.

### Console access

Whether somebody can sign in to the console at all, and what they may do once inside, is
decided by their user, groups and roles. This is the layer that keeps the Administration
screens away from a content editor.

### Content permissions (the ACL)

Any **folder** or **post** can carry its own grants. Open the item's **Permissions**
action in the content browser.

- A grant is a **subject** (user, group or role) plus a **permission**: **Read** or
  **Write**.
- Grants are **inherited** down the tree: a grant on a folder covers what is inside it.
- An item with **no grants is open**: anyone signed in can reach it. The console says
  so on the panel: *"No restrictions: anyone signed in can access this. Add a grant to
  restrict it."* Adding the first grant is what starts restricting it.

The same ACL is enforced on every surface. An agent's token reaches exactly what a
curator's session reaches, and no more: a copy needs read on its sources, a move needs
write on both ends. See [Letting an agent in](/shio/docs/agent-safety/).

### Members-only pages (who may read the published site)

The ACL decides who edits. A separate rule decides who may **read a published page**: a
folder can be made **members-only**, for any signed-in user or for named groups, and
everything inside it follows. A member is a Shio user, so a members area needs no
second account system.

- On the published site (`/sites/...`) a visitor outside the groups gets the same
  **404** a missing page gets, so the page is not confirmed to exist. A member reads it,
  and the response is never stored in a shared cache.
- Listings on public pages (menus, `{{#query}}`, related content, search) leave the
  gated pages out for that visitor, and the page counts stay exact.
- The delivery API applies the same rule to the **owner of the token** making the
  request, in single reads and in every listing.
- A site can send an anonymous visitor to **sign in** instead of the 404: with
  `membersLogin` on, the visitor goes to the console login and comes back to the page.
- Administrators always read everything, and the preview still shows gated pages to
  editors, because that is where they are edited.

Today a folder is gated through the agent surface: the `folder.visibility` operation,
with `visibility` set to `members` (and optionally `groups`), or `public` to open it
again. A single post is gated the same way with `post.visibility`, without moving it
into a gated folder. The site switch is `membersLogin` on `site.upsert`.

---

## API tokens

**Administration → API Tokens** mints the keys an external front end or an agent uses.
A token carries a **scope**, and the scope is the whole of what it may do:

| Scope | What it may do |
|---|---|
| **Read** | Read published content only. |
| **Preview** | Read published content *and* unpublished drafts. |
| **Write** | Read, plus write content back through the Content Delivery API. |
| **Agent** | Write through the agent surface (`/api/v2/agent`, `/mcp`) and read drafts. It is **not** a CDA write key: it cannot patch published content directly. |

An Agent token also has a **May publish** switch, **off by default**. An agent token
without it writes drafts that a curator then approves in Agent Review.

A token is additionally tagged as a **Production** or **Preview** environment key:
production reads published content, preview reads draft-preferred, so the same front end
can be pointed at either by swapping only the token.

By default a token reaches every site. Turn **All sites** off to restrict it to the sites
you pick. A token can also be **disabled**, which revokes it without deleting it.

The token value is shown once, when it is created. Send it in the `Key` header:

```bash
curl -H 'Key: YOUR_TOKEN' http://localhost:2710/api/v2/site
```

See [Content Delivery API](/shio/docs/headless/content-delivery-api/) for what the token then
reaches.

---

## Tenants

**Administration → Tenants** lists the isolated workspaces on this instance. Each tenant
has its own content, and the reserved `shio` tenant is the single-tenant default: it
cannot be renamed, disabled or deleted.

A tenant name is lowercase, has no spaces, and is **immutable once created** because it is
carried on every row of the tenant's content. A new tenant can be **provisioned** with a
copy of the default tenant's post-type model; provisioning is idempotent, so running it
again is safe. A tenant can be **suspended** and later reactivated.

Full detail, including how a request is resolved to a tenant, is on
[Multi-tenancy](/shio/docs/multi-tenancy/).

---

## Authentication providers

**Administration → Authentication Providers** configures where console users are
authenticated. Shio ships one provider: **Shio Native**, the built-in username and
password store described above. The screen exists so an external identity source can be
added as a second provider instance.

For the authentication and authorization mechanics (sessions, CSRF, CORS, token auth),
see [Security](/shio/docs/security/).

---

## Exchange providers

**Administration → Exchange Providers** configures the sources content can be imported
from and exported to. Shio ships one: **Shio Package**, the local exchange archive that
site import and download use.

See [Import & Export](/shio/docs/import-export/) for the package format and the import flow.

---

## Email

**Administration → Email** configures the outgoing SMTP server Shio sends mail through:
host, port, credentials and whether the connection is secured. The same settings can be
supplied as properties at startup. See the
[Configuration Reference](/shio/docs/configuration-reference/).

---

## Webhooks

**Administration → Webhooks** manages the outbound notifications Shio fires when content
is **published**, **unpublished** or **deleted**. A subscription belongs to a site, so a
static-site rebuild can be triggered by the one site that changed rather than by all of
them.

The subscription form, the payload each event carries, how to verify its signature and
what delivery does and does not guarantee are on [Webhooks](/shio/docs/webhooks/).

---

## Activity

**Administration → Activity** is the audit trail of content changes: who created, edited,
published, unpublished, deleted or restored what, and when. Every write through any
surface (console, CLI, CDA, agent) is recorded here under the name of whoever made it.

---

## Agent review

**Administration → Agent Review** groups an agent's writes into **runs** and shows what
each one changed. **Approve** publishes the drafts that run wrote; **revert** puts things
back.

**The queue opens on what is waiting.** *Waiting* lists the runs with changes still to
decide, and *All runs* lists every run the agent made. When nothing is waiting the page
says so and offers the full list, which is a different fact from a filter that matched
nothing. The assistant dock at the foot of the rail tells you when an agent run finishes.

A run is named by the intent it declared; one that declared none shows its short id and
*no intent declared*. Each row shows what the run created, updated, published or
deleted. **Approve** and **Revert** on a row decide that run; tick several rows and the
bar that appears decides all of them at once with **Approve and publish**.

This is the moderation step for content that arrives from anywhere you did not type it
yourself, including visitor form submissions, which land as drafts of a post type you
already modelled. See [Letting an agent in](/shio/docs/agent-safety/).

---

## Sites

Sites are managed from **Content → Sites**.

### Creating a site

1. Open **Content → Sites**.
2. Click **New Site**.
3. Give it a name, a description and the URL its front end is served from.

### Importing a site

1. Open **Content → Sites**.
2. Click **Import Site**.
3. Upload the site package.

### Downloading a site

Use the **Download** action on a site row. The package carries the site's content,
folders and configuration, and is what **Import Site** reads back.

### Site properties

| Property | Description |
|---|---|
| **Name** | The site's display name, and the name it is addressed by (`site:<name>`). |
| **Description** | Free text. |
| **URL** | The base URL of the site's front end. The Universal Editor frames this, and "View Site" opens it. |

---

## Search

Search is configured with properties rather than on a console screen. Site search is
served by Shio itself; connecting an external index is an operator setting. See
[Search & Caching](/shio/docs/search-caching/).

---

## Related pages

| Page | Description |
|---|---|
| [The content console](/shio/docs/content-console/) | The browser, the post form, copy and move, the trash |
| [Letting an agent in](/shio/docs/agent-safety/) | Drafts, publishing, the review queue, the trash |
| [Security](/shio/docs/security/) | Authentication and authorization mechanics |
| [Multi-tenancy](/shio/docs/multi-tenancy/) | How tenants isolate content |
| [Configuration Reference](/shio/docs/configuration-reference/) | Every `application.properties` setting |
| [Import & Export](/shio/docs/import-export/) | Packages and content migration |
| [Installation Guide](/shio/docs/installation-guide/) | Setup and first-time configuration |

---
