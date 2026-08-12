/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio annotations <page>` (SH588) — the props a page emits, joined against the fields its
 * post-types declare.
 *
 * <p>SH524's question asked at the page rather than at the template: SH524 finds a field no
 * template draws, this finds a field a template draws and does not make editable. Every instrument
 * this project added — SH516, SH521, SH526, SH548, SH560 — turned a rule in a file into an
 * assertion, and this is the next on that list: a paragraph in `SKILL.md` that was hand-written
 * four times, differently each time, and once wrong.</p>
 *
 * <h2>No browser, unlike `audit`</h2>
 *
 * <p>The annotations come off the server's renderer, so they are in the bytes the preview already
 * returns. Adding Chromium to answer a question settled before any script runs would make the
 * cheapest check in the chain the most expensive one to run.</p>
 */
import { describeAnnotations, joinDeclared, scanAnnotations } from "../annotations.mjs";
import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { pageTarget, resolveUrl } from "../snapshot.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("annotations");

export async function run(flags, positionals, ctx) {
  // No `warn`, for the reason `audit` gives: this command never documented `--url` as the page, so
  // accepting it would create the hazard rather than carry it.
  const target = pageTarget(flags, positionals);
  if (!target) {
    throw new Error(
      "shio annotations: pass a page — post:<site>/<url>.\n"
        + "Example: shio annotations post:Acme/about",
    );
  }

  const admin = resolveAdmin(flags, ctx.env);
  const agent = new ShioAgentClient({ ...admin, fetch: ctx.fetch });
  const sites = /^https?:\/\//.test(String(target)) ? new Map() : await siteUrls(agent);
  const resolved = resolveUrl(target, sites, {
    previewBase: admin.baseUrl,
    // The editor annotations are a property of the instance's own render. A site's public front
    // end serves what the CDA gave it and has no reason to carry them, so this asks the preview
    // unless the caller pointed it at an absolute URL themselves.
    preview: flags.preview === true || !/^https?:\/\//.test(String(target)),
  });
  if (resolved.error) {
    throw new Error(`shio annotations: ${resolved.error}`);
  }

  const url = withEditor(resolved.url);
  const html = await fetchPage(url, resolved.preview ? admin : null, ctx);
  const scan = scanAnnotations(html);

  const [fields, types] = await Promise.all([
    declaredFields(admin, ctx),
    // Only the page's own owner carries `data-shio-post-type`; a section's post has to be
    // resolved. One batched read, because N sections asking one at a time is the cost `/agent/read`
    // exists to remove.
    resolveTypes(agent, scan),
  ]);
  const report = joinDeclared(scan, { fields, types });

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ target, url, ...report }, null, 2)}\n`);
  } else {
    ctx.stdout.write(describeAnnotations(report, { target }));
  }

  // A declared field that renders without an annotation is the defect this exists to find, and it
  // is one a machine observed rather than a judgement — so it gates.
  //
  // SH685 — and an unresolved type gates too, which is a reversal. The old rule was that "we could
  // not check this" is not the same claim as "this is wrong", and that is true; what it left out is
  // that it is not the same claim as "this is right" either, and only one of those two was being
  // acted on. The run exited 1 on the Page's `0/4` while eight of nine posts went unchecked, so the
  // red read as a finding about the page and hid the blind spot — worse than either answer alone.
  //
  // It is honest as a gate now in a way it would not have been before: with `ids: true` the read
  // resolves what it is asked, so an unresolved type means the page renders `sh-post` for a post
  // this instance cannot read — a dangling annotation, and a finding in its own right. Reported
  // separately from `missing` so the exit code never says which one it was; the report does.
  const unchecked = report.unresolved > 0;
  return { target, url, ...report, exitCode: report.missing > 0 || unchecked ? 1 : 0 };
}

/** `?editor=true`, added without disturbing a query the caller already wrote. */
function withEditor(url) {
  const parsed = new URL(url);
  parsed.searchParams.set("editor", "true");
  return parsed.toString();
}

async function fetchPage(url, credentials, ctx) {
  const headers = { Accept: "text/html" };
  if (credentials?.user) {
    const raw = `${credentials.user ?? ""}:${credentials.password ?? ""}`;
    headers.Authorization = `Basic ${Buffer.from(raw, "utf8").toString("base64")}`;
  }
  const res = await ctx.fetch(url, { method: "GET", headers });
  if (!res.ok) {
    throw new Error(
      `shio annotations: ${url} answered ${res.status}. A page that does not render has no `
        + "annotations to count, and this is not that finding — check the address and that the "
        + "post is published or that you are reading the preview.",
    );
  }
  return res.text();
}

/** Post-type name → the field names it declares, from the lossless console defs. */
async function declaredFields({ baseUrl, user, password }, ctx) {
  const client = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });
  const defs = (await client.getPostTypeDefs()) ?? [];
  const fields = new Map();
  for (const def of defs) {
    if (!def?.name) continue;
    fields.set(def.name, (def.fields ?? []).map((field) => field.name).filter(Boolean));
  }
  return fields;
}

/**
 * Post id → post-type name, for the owners the page did not name a type for.
 *
 * <h3>The instrument could not read the type it had just drawn (SH685)</h3>
 *
 * <p>On the home page of the `www.viglet.org` replica this resolved **one** post of nine and said
 * `type not resolved, NOT checked` about the other eight — while `GET /agent/read?address=id:<uuid>`
 * answered `SectionFaq` and `SectionFeatureGrid` for those very ids, one call away. So the
 * comparison the command exists to make was made for a ninth of the page, and its headline
 * `67 of 4 declared` counted annotations against the declared fields of the only type it looked up.
 *
 * <p>The read never failed and the `catch` below was never the path taken. `/agent/read` answers
 * with the **canonical** address — `post:VigletOrg/sections/bf666ccc4` — whatever address was asked
 * for, and carries no `id` unless `ids=true` is passed. So `doc.id ?? doc.address` fell through to a
 * `post:` address, `.replace(/^id:/, "")` left it alone, and the map was keyed by something the
 * lookup — a raw uuid off the rendered markup — could never match. Every entry was written and none
 * was ever found.
 *
 * <p>`ids: true` is the whole fix, and it is asked of the read rather than joined by position.
 * Position would work — an address that resolves to nothing comes back *in place*, echoing the
 * request and carrying an `error`, so the array is one entry per address in order — but that is a
 * property of the endpoint nothing here asserts, and this defect is what betting on an unasserted
 * property of that response looks like.
 */
async function resolveTypes(client, scan) {
  const unknown = scan.posts.filter((row) => !row.type).map((row) => `id:${row.post}`);
  const types = new Map();
  if (!unknown.length) return types;
  let docs = [];
  try {
    docs = await client.read(unknown, { ids: true });
  } catch {
    // Silent, and the report says `NOT checked` for every row it could not resolve. A read that
    // failed is not evidence that a page is annotated, and it is not evidence that it is not.
    return types;
  }
  for (const doc of Array.isArray(docs) ? docs : []) {
    // `doc.id` and nothing else. The address fallback is gone: it is the wrong key by construction
    // (canonical, not the one asked for) and having it there is what made a broken join look like a
    // working one — every miss read as "this post has no type" rather than as "I asked wrongly".
    if (doc?.id && doc?.type) types.set(String(doc.id), doc.type);
  }
  return types;
}

/** Site name → public base URL, from the context pack's `sites` section (as `shio audit`). */
async function siteUrls(client) {
  const sites = new Map();
  try {
    const { body } = await client.getContext({ include: "sites", format: "json" });
    for (const site of JSON.parse(body).sites ?? []) {
      if (site?.name) sites.set(site.name, site.url ?? "");
    }
  } catch {
    // Left empty deliberately: `resolveUrl` then says "not a site this instance reports", which is
    // a better answer than a fabricated origin.
  }
  return sites;
}
