---
title: Administration
description: "Run a Viglet Shio instance from the console: users and access, API and device tokens, members-only pages, sites, tenants and the agent review queue."
sidebar:
  order: 1
---

This page is for operators: the people who set up who can do what on a Viglet Shio instance.
Everything an operator configures lives in the console under **Administration**, at
`/bento/admin`. The page starts with the common tasks, then describes each screen. The curator's
half (the content browser, the post form, copy and move, the trash) is on
[The content console](/docs/content-console/).

---

## Change the default password

On first startup the instance creates an administrator account, user `admin` with password
`admin`.

1. Sign in to the console as `admin`.
2. Open **Administration**, then **Users**, and choose `admin`.
3. Set a new password and choose **Save**.

:::caution
Do this before the instance is reachable by anyone else.
:::

## Create an API token

1. Open **Administration**, then **API Tokens**.
2. Choose **New API Token**.
3. Pick the **scope** (see [API tokens](#api-tokens)) and the environment, **Production** or
   **Preview**.
4. To limit the token to some sites, turn **All sites** off and pick them.
5. For an agent token that may publish, turn on **May publish**.
6. Choose **Create token**, and copy the value: it is shown once.

Send the value in the `Key` header:

```bash
curl -H 'Key: YOUR_TOKEN' http://localhost:2710/api/v2/site
```

## Make a folder members-only

A folder is gated through the agent surface, with the `folder.visibility` batch op:

1. Set `data.visibility` to `members` on the folder's address, and optionally `data.groups` to
   the groups it is for. With no groups, any signed-in user may read it.
2. To send anonymous visitors to sign in instead of a 404, set `membersLogin` on the site with
   `site.upsert`.

Everything inside the folder follows. Gate a single post the same way with `post.visibility`,
and open a folder again with `public`. What a visitor then sees is in
[Members-only pages](#members-only-pages-who-may-read-the-published-site).

## Serve each site at its own hostname

1. Set `shio.sites.host-domain=sites.example.org`, or the `SHIO_SITES_HOST_DOMAIN` environment
   variable.
2. Point a wildcard DNS record and a certificate for `*.sites.example.org` at the instance.
3. Restart the instance.

Each site is then served at `<friendly-url>.sites.example.org`; see
[A site at its own address](#a-site-at-its-own-address) for the rules.

---

## Finding your way around

The **navigation rail** down the left carries **Home** and two areas, **Content** and
**Administration**. Each area opens its **hub**, which lists every screen of the area as a
tile in small groups; Administration's are *Access*, *Agent and audit*, *Integrations* and
*This instance*, and `/bento/admin` lands there. To reach a named screen directly, use the
**command palette**: <kbd>Ctrl</kbd>/<kbd>Cmd</kbd>+<kbd>K</kbd> from anywhere, or <kbd>/</kbd>
outside a field. It searches descriptions as well as titles, so *credentials* finds API Tokens.

| Area | Screens |
|---|---|
| **Content** | Sites · Search · Post Types · Media Library · Trash · Universal Editor |
| **Administration** | Users (users, groups, roles) · API Tokens · Tenants · Authentication Providers · Exchange Providers · Email · Webhooks · Activity · Agent Review · GraphQL |

| Pattern | Behaviour |
|---|---|
| Lists of records | A **table** you sort by its column headers, with the create action at the top right |
| Editors | **Save** in the header, and a bar with Save once you scroll the header away, which says when a field has an **unsaved change** |
| Delete, Suspend | In the header's **⋯** menu |
| **Tenants** | Shown only on a multi-tenant instance (`shio.multi-tenant`, off by default) |
| Addresses under `/console` | Redirect to `/bento`, keeping the query string and anchor. On a multi-tenant instance the tenant comes from your session, not from the URL |

## Home

The console's landing screen, at `/bento`: how many sites and post types the instance has,
recent content changes, and shortcuts to creating a site, browsing content, searching, modelling
a post type, webhooks and the activity log. Every number is a link into the screen it counts.

---

## Users

**Administration → Users** creates, changes and deletes users. A user can sign in to the
console, be the subject of a permission grant, and own content.

| Property | Holds |
|---|---|
| Username, password | The sign-in |
| Email address | |
| First and last name | |
| Groups | The groups the user belongs to |

---

## Groups and roles

**Groups** collect users and **roles** name a capability, so a permission grant can be written
once for many people. Both are managed on the Users screen. A grant accepts a **user**, a
**group** or a **role** as its subject.

A **group's page lists its members**: add one by choosing a user who is not yet a member, and
remove one from its row; each change is saved at once. Below the members, the page lists the
**roles** that name the group, each linking to the role, where that relation is changed.

---

## Permissions

Two independent layers, configured in different places.

| Layer | Decides | Set on |
|---|---|---|
| Console access | Whether somebody can sign in, and which screens they reach | Their user, groups and roles |
| Content permissions (the ACL) | Who may read or write a folder or post | The item's **Permissions** action in the content browser |

| ACL rule | Behaviour |
|---|---|
| A grant | A **subject** (user, group or role) plus a **permission**, **Read** or **Write** |
| Inheritance | A grant on a folder covers what is inside it |
| No grants | The item is open to anyone signed in, and the panel says so. The first grant starts restricting it |
| Surfaces | The same ACL holds on every surface: an agent's token reaches exactly what a curator's session reaches. A copy needs read on its sources, a move needs write on both ends. See [Letting an agent in](/docs/agent-safety/) |

### Members-only pages (who may read the published site)

The ACL decides who edits. A separate rule decides who may **read a published page**: a folder
or post can be **members-only**, for any signed-in user or for named groups (see
[Make a folder members-only](#make-a-folder-members-only)). A member is a Viglet Shio user, so a
members area needs no second account system.

| Where | A visitor outside the groups gets |
|---|---|
| A page under `/sites/...` | The same **404** a missing page gets. A browser receives the site's published `/404` page, in its language when translated, or a plain "Page not found" page with a link home; an API client receives the JSON problem. A member's response is never stored in a shared cache |
| Listings (menus, `{{#query}}`, related content, search) | The gated pages left out, with exact counts |
| The delivery API | The same rule, applied to the **owner of the token**, in single reads and in every listing |
| The preview | Gated pages shown to editors, since that is where they are edited. Administrators always read everything |

With `membersLogin` on the site, an anonymous visitor is sent to sign in instead of the 404,
and comes back to the page.

| Signing in on a site at its own hostname | Behaviour |
|---|---|
| Where | At the instance's address (`shio.url`), then back on the site's hostname, signed in there |
| How | The instance hands the site host a one-time code, valid for a minute and only in the browser that started the sign-in; the console session never leaves the instance |
| What it opens | That one site's members-only pages, for eight hours: not the console, the API or another site. It is never listed among your API tokens |
| How it ends | Signing out of Viglet Shio, the account being disabled, or a sign-out link on the site: `/api/v2/site-member/sign-out?to=/` on the site's hostname, where `to` is the page to land on |

---

## API tokens

**Administration → API Tokens** mints the keys an external front end or an agent uses. The
scope is the whole of what a token may do:

| Scope | What it may do |
|---|---|
| **Read** | Read published content only |
| **Preview** | Read published content *and* unpublished drafts |
| **Write** | Read, plus write content back through the Content Delivery API |
| **Agent** | Write through the agent surface (`/api/v2/agent`, `/mcp`) and read drafts. Not a CDA write key: it cannot patch published content directly |

| Setting | Effect |
|---|---|
| **May publish** | Agent tokens only, **off by default**. Without it, an agent writes drafts a curator approves in Agent Review |
| **Production** / **Preview** | Production reads published content; preview reads draft-preferred, so one front end switches by swapping only the token |
| **All sites** | On by default; off restricts the token to the sites you pick |
| Disabled | Revokes the token without deleting it |

The value is shown once, at creation. See
[Content Delivery API](/docs/headless/content-delivery-api/) for what a token reaches.

---

## Device tokens

A device token lets the Viglet Shio mobile app, or any native client, act as **you** without
storing your password. It reaches what your own sign-in reaches, and changes to your groups apply
to it at once.

**Administration → My devices** lists your devices with when each was last used, revokes them
(a revoked device is refused on its next call), and adds one with **Add device**: the token is
shown once, to paste into the app.

The app can also sign you in itself, through this instance's sign-in page, with your password or
your company's single sign-on, with nothing to paste. An operator turns that on by listing the
app's redirect address, which must match exactly:

```properties
shio.device-auth.redirect-uris=com.example.shio://signed-in
```

Empty, the default, leaves in-app sign-in off, and **My devices** is the only way to add a
device.

---

## Tenants

**Administration → Tenants** lists the isolated workspaces on this instance. Each tenant has its
own content.

| Rule | Behaviour |
|---|---|
| The `shio` tenant | The single-tenant default: it cannot be renamed, disabled or deleted |
| Names | Lowercase, no spaces, and **immutable once created**, since the name is carried on every row of the tenant's content |
| Provisioning | Copies the default tenant's post type model into a new tenant; running it again is safe |
| Suspension | A tenant can be suspended and later reactivated |

How a request is resolved to a tenant is on [Multi-tenancy](/docs/multi-tenancy/).

---

## Authentication providers

**Administration → Authentication Providers** configures where console users are authenticated.
The built-in provider is **Shio Native**, the username and password store above; an external
identity source is added as a second provider. For sessions, CSRF, CORS and token
authentication, see [Security](/docs/security/).

## Exchange providers

**Administration → Exchange Providers** configures where content is imported from and exported
to. The built-in provider is **Shio Package**, the local archive that site import and download
use. See [Import & Export](/docs/import-export/).

## Email

**Administration → Email** configures the outgoing SMTP server: host, port, credentials and
whether the connection is secured. The same settings can be given as properties at startup; see
the [Configuration Reference](/docs/configuration-reference/).

## Webhooks

**Administration → Webhooks** manages the outbound notifications sent when content is
**published**, **unpublished** or **deleted**. A subscription belongs to a site, so a static-site
rebuild is triggered by the one site that changed. The form, the payloads, signature
verification and delivery guarantees are on [Webhooks](/docs/webhooks/).

## Activity

**Administration → Activity** is the audit trail of content changes: who created, edited,
published, unpublished, deleted or restored what, and when. Every write through any surface
(console, CLI, Content Delivery API, agent) is recorded under the name of whoever made it.

---

## Agent review

**Administration → Agent Review** groups an agent's writes into **runs** and shows what each one
changed.

| Part | Behaviour |
|---|---|
| **Waiting** | The runs with changes still to decide; the queue opens here. When nothing is waiting the page says so and offers the full list |
| **All runs** | Every run the agent made |
| A run's name | The intent it declared, or its short id and *no intent declared* |
| A row | What the run created, updated, published or deleted, with **Approve**, which publishes its drafts, and **Revert**, which puts things back |
| Several rows | Tick them, and the bar that appears decides all of them with **Approve and publish** |

The assistant dock reports when an agent run finishes. Review is the moderation step for
anything you did not type yourself, visitor form submissions included. See
[Letting an agent in](/docs/agent-safety/).

---

## Sites

Sites are managed from **Content → Sites**.

| Task | How |
|---|---|
| Create | **New Site**, then a name, a description and the URL its front end is served from |
| Import | **Import Site**, then upload the package |
| Download | **Download** on the site's row: its content, folders and configuration, the package **Import Site** reads back |

| Property | Description |
|---|---|
| **Name** | The site's display name, and the name it is addressed by (`site:<name>`) |
| **Description** | Free text |
| **URL** | The base URL of the site's front end, which the Universal Editor frames and **Preview** and **View published site** open. Empty, **Preview** opens the draft preview at `/preview/<site>/` and **View published site** opens `/sites/<furl>/` |

### A site at its own address

With `shio.sites.host-domain` set (see
[Serve each site at its own hostname](#serve-each-site-at-its-own-hostname)):

| Rule | Behaviour |
|---|---|
| Routing | The host's first label is the site's friendly URL; the path is the page |
| Canonical URL | Names the host, unless the site has a **URL** of its own, which stays canonical |
| Friendly URL | Must be a valid hostname label (lowercase letters, digits, hyphens); otherwise the site keeps its `/sites/<furl>/` address |
| Unclaimed hostname | Answers 404, saying how to fix it |
| Console | **View published site** opens the site at its hostname, and the site's API read returns it as `publishedUrl` |
| Default | Empty: nothing changes until it is set |

---

## Search

Search is configured with properties, not on a console screen. Site search is served by
Viglet Shio itself; connecting an external index is an operator setting. See
[Search & Caching](/docs/search-caching/).

---

## Related pages

| Page | Description |
|---|---|
| [The content console](/docs/content-console/) | The browser, the post form, copy and move, the trash |
| [Letting an agent in](/docs/agent-safety/) | Drafts, publishing, the review queue, the trash |
| [Security](/docs/security/) | Authentication and authorization mechanics |
| [Multi-tenancy](/docs/multi-tenancy/) | How tenants isolate content |
| [Configuration Reference](/docs/configuration-reference/) | Every `application.properties` setting |
| [Import & Export](/docs/import-export/) | Packages and content migration |
| [Installation Guide](/docs/installation-guide/) | Setup and first-time configuration |
