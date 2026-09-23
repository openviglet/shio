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

/** One row per redirect: where it answers, where it sends, and which status a visitor gets. */
export function rows(found, read) {
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
    };
  }).sort((left, right) => left.from.localeCompare(right.from));
}

/** The addresses that used to answer and do not — `verify`'s own finding, not a second rule. */
export function orphans(report) {
  return (report?.findings ?? []).filter((finding) => finding.rule === "url-orphaned");
}

export function render(redirects, orphaned, { checked = true } = {}) {
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
  const table = rows(found, read);

  const report = checked
    ? await client.verify(site ? { site, checks: "routes" } : { checks: "routes" })
    : null;
  const orphaned = checked ? orphans(report) : [];

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ redirects: table, orphaned }, null, 2)}\n`);
    return orphaned.length > 0 ? 1 : 0;
  }
  ctx.stdout.write(render(table, orphaned, { checked }));
  return orphaned.length > 0 ? 1 : 0;
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
