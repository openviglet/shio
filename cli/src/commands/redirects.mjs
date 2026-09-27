/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio redirects` (SH1064) — which old addresses still answer, and which ones should.
 *
 * Two questions that are only useful together. The redirect table on its own is a list of posts
 * of one type, which `shio find --type Redirect` already prints; what nobody could ask for is the
 * other half — the addresses this site used to serve, still linked to, answering nothing. So this
 * command reads the table and then asks `verify --checks routes` for `url-orphaned`, and the exit
 * code is about the second half: a listing that always exits 0 is one a gate cannot use.
 *
 * It composes the two reads rather than adding an endpoint (P10). The orphan half is the
 * expensive one, which is why `--no-check` exists — but it is on by default, because the reason
 * this command is worth its name is the row nobody knew to look for.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("redirects");

/**
 * One row per redirect: where it answers, where it sends, and which status a visitor gets.
 *
 * <p>SH1133 — and who serves it. `frontEnds` maps a site name to `{ id, url }` from the context
 * pack; a site with a front-end URL may be delivered by a front end of its own, which sees these
 * rows only if it reads the CDA's table. Absent, every row's `frontEnd` is null.</p>
 */
export function rows(found, read, frontEnds = new Map()) {
  const fields = new Map();
  // `read` answers with one doc per address, and `data` is the field values — the same shape
  // `shio annotations` joins on, keyed by the canonical address the read returns.
  for (const doc of Array.isArray(read) ? read : []) {
    if (doc?.address && doc.data && typeof doc.data === "object") {
      fields.set(doc.address, doc.data);
    }
  }
  return (found?.results ?? []).map((row) => {
    const values = fields.get(row.address) ?? {};
    const to = values.TO ?? values.to ?? null;
    return {
      address: row.address,
      from: pathOf(row.address),
      to: to === null || to === "" ? null : String(to),
      permanent: isTrue(values.PERMANENT ?? values.permanent),
      state: row.state ?? null,
      frontEnd: frontEnds.get(siteOf(row.address))?.url || null,
    };
  }).sort((left, right) => left.from.localeCompare(right.from));
}

/** The addresses that used to answer and do not — `verify`'s own finding, not a second rule. */
export function orphans(report) {
  return (report?.findings ?? []).filter((finding) => finding.rule === "url-orphaned");
}

export function render(redirects, orphaned, { checked = true, frontEnds = new Map() } = {}) {
  const lines = [];
  for (const row of redirects) {
    // A redirect with no target renders as a page rather than redirecting (SH385), so an empty
    // `to` is the row worth seeing, not a blank column.
    const target = row.to ?? "(no target — it renders as a page)";
    const status = row.to ? (row.permanent ? "301" : "302") : "";
    const draft = row.state && row.state !== "published" ? `  ${row.state.toUpperCase()}` : "";
    lines.push(`  ${row.from} -> ${target}  ${status}${draft}`.trimEnd());
  }
  lines.unshift(`${redirects.length} redirect(s)`);
  // SH1133 — this instance's /sites/ delivery honours every row above. A site with a front-end URL
  // may be served by a front end of its own instead, and that one honours them only if it reads the
  // table; the CMS cannot tell from the URL which of the two it is, so the note says both.
  const served = [...new Set(redirects.filter((row) => row.frontEnd).map((row) => siteOf(row.address)))];
  for (const name of served.sort()) {
    const { id, url } = frontEnds.get(name) ?? {};
    lines.push("", `${name} is served at ${url}. If that is a front end of its own rather than this `
      + "instance's /sites/ delivery, these redirects reach visitors only when it reads "
      + `GET /api/v2/cda/site/${id ?? "<siteId>"}/redirects (create-shio-app's middleware.ts does).`);
  }
  if (!checked) {
    lines.push("", "orphans not checked (--no-check)");
    return `${lines.join("\n")}\n`;
  }
  lines.push("", orphaned.length === 0
    ? "no orphaned addresses"
    : `${orphaned.length} address(es) this site used to serve and no longer does:`);
  for (const finding of orphaned) {
    lines.push(`  ${pathOf(finding.address)}  ${inbound(finding.message)}`);
    lines.push(`    shio apply - <<< '{"ops":[{"op":"post.redirect","address":`
      + `"${finding.address}","to":"post:<site>/<where it went>"}]}'`);
  }
  return `${lines.join("\n")}\n`;
}

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const site = flags.site ? String(flags.site) : positionals?.[0] ?? null;
  const checked = flags["no-check"] !== true;

  const found = await client.findAll(site ? { site, type: "Redirect" } : { type: "Redirect" });
  const addresses = (found.results ?? []).map((row) => row.address);
  // One read for every redirect rather than one each: the fields are the answer, and N round
  // trips for N rows is the shape this surface's multi-address read exists to avoid (SH171).
  const read = addresses.length > 0 ? await client.read(addresses) : null;
  // SH1133 — each site's front-end URL, read only when there is a redirect to say it about.
  const frontEnds = addresses.length > 0 ? await siteFrontEnds(client) : new Map();
  const table = rows(found, read, frontEnds);

  const report = checked
    ? await client.verify(site ? { site, checks: "routes" } : { checks: "routes" })
    : null;
  const orphaned = checked ? orphans(report) : [];

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ redirects: table, orphaned }, null, 2)}\n`);
    return orphaned.length > 0 ? 1 : 0;
  }
  ctx.stdout.write(render(table, orphaned, { checked, frontEnds }));
  return orphaned.length > 0 ? 1 : 0;
}

/** Site name -> `{ id, url }`, from the context pack's site rows (the read `shio site` makes). */
async function siteFrontEnds(client) {
  const { body } = await client.getContext({ include: "sites", format: "json" });
  const sites = JSON.parse(body).sites ?? [];
  return new Map(sites
    .filter((site) => site?.name)
    .map((site) => [site.name, { id: site.id ?? null, url: site.url ?? "" }]));
}

/** `post:Site/some/url` -> `Site`. */
function siteOf(address) {
  const text = String(address ?? "");
  const colon = text.indexOf(":");
  const slash = text.indexOf("/", colon + 1);
  return text.slice(colon + 1, slash < 0 ? undefined : slash);
}

/** `post:Site/some/url` -> `/some/url`; anything else is printed as it came. */
function pathOf(address) {
  const slash = String(address ?? "").indexOf("/");
  return slash < 0 ? String(address ?? "") : String(address).slice(slash);
}

/** The count verify already stated, rather than a second count of the same thing. */
function inbound(message) {
  const match = /(\d+) published page\(s\)/u.exec(String(message ?? ""));
  return match ? `${match[1]} inbound link(s)` : "linked";
}

/** The Check Box value, which is `"1"`/`"0"` from the console and a boolean from an op. */
function isTrue(value) {
  return value === true || value === "true" || value === "1" || value === 1;
}
