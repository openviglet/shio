---
title: Multi-Tenancy
description: "Run one Viglet Shio instance that serves many isolated tenants: discriminator-based data isolation, provisioning, membership, plan quotas and suspension."
sidebar:
  label: Multi-Tenancy
---

One Shio process can serve many fully isolated **tenants**: separate content, separate
users, separate files, separate API tokens, out of one schema and one JVM.

It is **off by default**. A single-instance installation behaves exactly as it always did,
and everything below is inert until you turn it on:

```properties
shio.multi-tenant=true
```

---

## How isolation works

Isolation is a **discriminator column**, not a schema or a database per tenant. Every row
of the active content tree carries a `tenant_id`, and Hibernate adds `tenant_id = ?` to
every query it issues, so a tenant's content is invisible to another tenant at the level
of the SQL, not at the level of a service remembering to filter.

Three consequences worth understanding before you deploy it:

- **You get one schema and one connection pool.** Adding a tenant is a row, not a
  migration, so provisioning is fast and cheap.
- **The filter is applied by the persistence layer.** A query written without thinking
  about tenancy is still tenant-scoped. This is why the isolation holds across surfaces, 
  console, delivery API, agent protocol and renderer all read through the same layer.
- **The tenant catalogue itself is global.** The table that lists tenants is deliberately
  *not* tenant-scoped, because something has to be able to see all of them.

:::caution[For contributors]
The discriminator column must be lowercase `tenant_id`, and a new column on a populated
table must be added nullable and backfilled. Both rules are load-bearing and both are
enforced by tests. If you are adding a table, read `docs/agents/database.md` in the
repository first.
:::

---

## Resolving the tenant

Which tenant a request belongs to is decided per request, from whichever of these applies:

| Caller | Resolved from |
|---|---|
| A logged-in console user | The identity provider's workspace claim → the user |
| An API key (delivery, agent, MCP) | **The token's own tenant**: looked up across tenants, then pinned for the request |
| Anything else | The default tenant, `shio` |

The token path matters more than it looks: it means **delivery is scoped to the tenant that
owns the content**, not to whoever is asking. A front end with a key for tenant B cannot
see tenant A's posts even if it guesses an id, and CDA responses vary on the key so a
shared cache cannot leak across tenants.

The reserved `shio` tenant is seeded at startup and cannot be deleted: it is the default,
and the source the provisioner copies from.

---

## Lifecycle

### Provisioning

Creating a tenant copies the **default content model** into it, so a new tenant starts with
the post types a working instance needs rather than an empty schema. The copy preserves
which types are system-owned, so a tenant cannot end up with an editable copy of something
that should be read-only.

Tenant administration is `ROLE_ADMIN`-gated, in the console's Tenants page or over the
tenant API. The console only shows that page when multi-tenancy is switched on: on a
single-tenant install there are no tenants to administer, so the navigation does not offer it.

### Membership

A user belongs to one or more tenants, and an identity provider's claim maps onto that
membership.

### Switching tenant

Which tenant you are working in is a property of your session, not of the URL. The control
is in the console header, next to your account menu: it lists the tenants you belong to,
marks the one you are in, and switching reloads the console so everything on screen is
re-read under the new one.

It appears only when there is somewhere to go: the instance runs multi-tenancy **and** you
belong to more than one tenant. On a single-tenant install, or if you are a member of one
tenant, there is no control and nothing to choose.

`POST /api/v2/tenant/{name}/switch` is the same operation for a script, and an identity
provider's `workspace_id` claim selects a tenant at sign-in.

### Plan quotas

A tenant carries a **plan**, and a plan carries limits. They are enforced at creation time
and answer **HTTP 402** when there is no room:

| Plan | Sites | Posts | Storage |
|---|---|---|---|
| `free` | 2 | 200 | 512 MB |
| unlimited | no limit | no limit | no limit |

The check runs before a site or a post is created, so a tenant at its ceiling gets a clear
refusal rather than a partially-created site.

**Storage is reported, not yet enforced.** A tenant's stored bytes are metered (the figure is
kept as files arrive and leave, so reading it costs nothing), and the plan's byte limit is
published alongside the other two. What does not happen yet is a refusal: the quota check compares
a *current total*, which is exact for sites and posts because each write adds one, and would let a
single large upload through under any limit. Rather than advertise a ceiling that a big enough file
walks past, Shio reports the number and leaves the refusal for when the check can weigh the
incoming size too.

### Suspension and teardown

A tenant can be **suspended** (kept intact but not served) and torn down, which removes
its content, its files and its tokens. Both are administrative operations, not something a
tenant's own credential can reach.

---

## Files

File bytes are stored per tenant: the tenant id prefixes the storage layout under
`shio.file-source.path`. Two tenants uploading `hero.png` never share a path, and a tenant
teardown removes its own bytes and nothing else.

---

## Background work runs per tenant

Anything the instance does **on a timer or off the request thread** has no tenant of its
own, and tenant isolation is resolved per thread, so background work has to be told which
tenant it is acting for, or it acts for the default one.

Two places this matters, and both behave the way you would want:

- **Scheduled publishes and unpublishes** sweep **once per tenant**. A suspended tenant is
  skipped: publishing on behalf of an account the platform has stopped is worse than
  publishing late, and the request path already refuses it. A tenant provisioned while a
  sweep is running is picked up on the next one.
- **Failures recorded asynchronously** (a webhook that gave up, a search index push that
  did not land) are filed under the tenant whose site they belong to, so they appear in
  that tenant's `/agent/diagnostics` and not in the platform's.

Nothing here needs configuring. It is listed because "the timer fired for everyone" is the
assumption an operator would otherwise carry, and on a single-tenant install it is true.

---

## What to check before enabling it

1. **Back up first, then start once.** Enabling tenancy backfills the discriminator on
   existing rows.
2. **Decide where the tenant claim comes from.** With an OIDC provider it is a claim on the
   token; without one, everything lands in the default tenant, which is the single-instance
   behaviour.
3. **Re-issue API tokens per tenant.** A token belongs to exactly one tenant, and that is
   what scopes delivery.
4. **Know the per-node limits.** The delivery rate limiter is per JVM, so a per-token budget
   multiplies by your node count.

---

## Related Pages

| Page | Description |
|---|---|
| [Installation Guide](/shio/docs/installation-guide/) | Getting an instance running first |
| [Configuration Reference](/shio/docs/configuration-reference/) | `shio.multi-tenant` and the file-source path |
| [Security](/shio/docs/security/) | Token scopes, authentication and CSRF |
| [Architecture Overview](/shio/docs/architecture-overview/) | Where tenant resolution sits |
