/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH931 — the one place that knows how a bento URL names its tenant.
 *
 * <h2>What was lost, and what was not</h2>
 *
 * The console was addressable as `/{tenant}/console/...`; bento is `/bento/...` with no prefix.
 * SH909 dropped the prefix and the reasoning holds: `ShVigletTenantResolver` reads the tenant from
 * the session, a CDA token's own tenant, or an OIDC `workspace_id` claim, and **never** from the
 * URL. The prefix never selected anything — it only kept generated links inside whatever prefix
 * the reader arrived with, which is a property of the link builder rather than of the request.
 *
 * What is actually lost is smaller and real. A curator with two memberships pastes a console URL
 * to a colleague. Under the old scheme the string said which tenant it meant, even though nothing
 * acted on it. Under bento the same paste is ambiguous: whoever opens it sees the post with that
 * id **in their own current tenant**, or a 404, and nothing anywhere says which was meant.
 *
 * <h2>The decision: a URL states its tenant and never changes it</h2>
 *
 * `?tenant=<name>` is a **claim about intent, not an instruction**. Opening such a link performs
 * no switch — `BentoTenantLinkNotice` reads the claim, compares it with the session's tenant, and
 * when they differ says so and offers a switch the reader has to press.
 *
 * The alternative shape — a parameter that switches the session on open — is the obvious one and
 * the dangerous one, so it is rejected here rather than left to whoever writes the next caller.
 * It makes a link a **state change**: a curator following a colleague's paste would be moved out
 * of the tenant they were working in, silently, with a half-written draft behind them in the one
 * they left. That is P7 exactly — never surprise the curator — and the cost of getting it wrong
 * is not a wrong page but a lost edit.
 *
 * <h2>Where it is inert</h2>
 *
 * A single-tenant instance can never carry an ambiguous link, so nothing here renders under one:
 * the notice gates on `useMultiTenant()` and the copy action lives inside `BentoTenantSwitcher`,
 * which already appears only where the reader has somewhere to go. The population that pays for
 * this feature is exactly the population that has the problem.
 */
export const TENANT_PARAM = "tenant"

/**
 * The tenant a URL claims to be about, or `null` when it makes no claim.
 *
 * Accepts a `location.search`, a full URL, or anything `URLSearchParams` can read. A blank value
 * (`?tenant=`) is no claim rather than a claim about a tenant with no name — a link builder that
 * stamps an empty string should not put a reader in front of a banner about `""`.
 */
export function tenantFromSearch(search: string | URLSearchParams | undefined | null): string | null {
  if (!search) return null
  const params = search instanceof URLSearchParams
    ? search
    : new URLSearchParams(search.includes("?") ? search.slice(search.indexOf("?")) : search)
  const claimed = params.get(TENANT_PARAM)?.trim()
  return claimed ? claimed : null
}

/**
 * Stamp `tenant` onto `href`, returning an absolute URL fit to paste.
 *
 * Absolute because the product of this function is something a curator sends to a colleague, and
 * a colleague cannot open `/bento/content?tenant=acme`. Existing parameters are preserved and an
 * existing `tenant` is replaced rather than appended, so copying twice yields the same string —
 * P5, and the reason this is a function rather than a template literal at each call site.
 */
export function linkWithTenant(href: string, tenant: string, base?: string): string {
  const origin = base ?? (typeof globalThis.location === "undefined"
    ? "http://localhost"
    : globalThis.location.origin)
  const url = new URL(href, origin)
  url.searchParams.set(TENANT_PARAM, tenant)
  return url.toString()
}
