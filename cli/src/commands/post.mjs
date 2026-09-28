/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio post set <address> <field>=<value>...` (SH1091) — change some fields of one post's draft,
 * refused if the post moved under you.
 *
 * `post.upsert` declares a post's whole state (SH168), which is the right shape for an agent writing
 * a site and the wrong one for a person changing a title: every field not named is a field the
 * command has to fetch, carry and send back unchanged. This merges instead, through the CDA's
 * `PATCH /api/v2/cda/post/{id}`. The CDA's merge was chosen over the console's for security: the
 * console merge keeps its CSRF boundary, and the capability here is a `WRITE` token, which an AGENT
 * token deliberately is not (it carries no ROLE_CDA_WRITE). So this takes a WRITE token as well as
 * the console credentials that resolve the address.
 *
 * The merge is conditional. The draft's tag is read from `/editable` and sent as `If-Match`, so a
 * write that raced another is refused (409, `stale-expectation`), not a lost update. `--if-match`
 * pins the check to a tag you read earlier, which is the version that protects a read-think-write
 * loop; the tag every write answers is printed, so the next change can be pinned to it.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("post");

export async function run(flags, positionals, ctx) {
  const verb = String(positionals[0] ?? "").trim().toLowerCase();
  if (verb !== "set") {
    throw new Error(
      `shio post: "${positionals[0] ?? ""}" is not a verb this takes.\n`
        + "  allowed: set\n"
        + "  example: shio post set post:Blog/hello title=\"Hello again\"",
    );
  }
  const address = positionals[1];
  const assignments = positionals.slice(2);
  if (!address || assignments.length === 0) {
    throw new Error(
      "shio post set: an address and at least one field=value are required.\n"
        + "  example: shio post set post:Blog/hello title=\"Hello again\" summary=\"Shorter\"",
    );
  }
  const attrs = parseAssignments(assignments);

  const admin = resolveAdmin(flags, ctx.env);
  const token = flags.token ?? ctx.env.SHIO_WRITE_TOKEN;
  if (!token) {
    throw new Error(
      "shio post set: a WRITE-scope API token is required (--token, or SHIO_WRITE_TOKEN).\n"
        + "  The merge is the CDA's, which an AGENT token cannot write to by design; create a WRITE\n"
        + "  token under Admin > API tokens, scoped to the sites it may change.",
    );
  }
  const agent = new ShioAgentClient({ ...admin, fetch: ctx.fetch });
  const resolved = await agent.resolve(address);
  if (resolved.kind !== "post") {
    throw new Error(`shio post set: ${address} is a ${resolved.kind}, not a post`);
  }

  const cda = cdaFetch(admin.baseUrl, token, ctx.fetch);
  const editable = await cda("GET", `/post/${resolved.id}/editable`);
  const known = new Set((editable.body.fields ?? []).map((field) => field.name));
  const unknown = Object.keys(attrs).filter((name) => known.size > 0 && !known.has(name));
  if (unknown.length > 0) {
    throw new Error(
      `shio post set: ${unknown.join(", ")} is not a field of this post's type.\n`
        + `  allowed: ${[...known].sort().join(", ")}`,
    );
  }

  const ifMatch = flags.ifMatch ?? editable.etag;
  const written = await cda("PATCH", `/post/${resolved.id}`, {
    body: { attrs, publish: Boolean(flags.publish) },
    ifMatch,
  });
  if (written.stale) {
    throw new Error(
      `shio post set: ${address} changed since ${flags.ifMatch ? "the tag you passed" : "it was read"}, `
        + "so nothing was written.\n"
        + `  ${written.problem}\n`
        + "  Read it again (shio read), decide, and rerun; pass --if-match with the new tag to pin the check.",
    );
  }

  const names = Object.keys(attrs).join(", ");
  const state = flags.publish ? "published" : "draft";
  ctx.stdout.write(`${address} set ${names} (${state}) etag=${written.etag ?? "-"}\n`);
  return { address, id: resolved.id, attrs, etag: written.etag, state };
}

/**
 * `name=value` as a string, `name:=json` as a JSON value (httpie's spelling), so a number, a boolean
 * or a list is not sent as the text of one.
 */
export function parseAssignments(assignments) {
  const attrs = {};
  for (const assignment of assignments) {
    const json = /^([^=:]+):=(.*)$/su.exec(assignment);
    const text = /^([^=:]+)=(.*)$/su.exec(assignment);
    if (json) {
      try {
        attrs[json[1]] = JSON.parse(json[2]);
      } catch {
        throw new Error(`shio post set: ${json[1]}:= is not JSON: ${json[2]}`);
      }
    } else if (text) {
      attrs[text[1]] = text[2];
    } else {
      throw new Error(
        `shio post set: "${assignment}" is not field=value (or field:=json for a number, a list or an object)`,
      );
    }
  }
  return attrs;
}

/** The two CDA calls this makes, with the `Key` header a WRITE token travels in. */
function cdaFetch(baseUrl, token, fetchImpl) {
  const root = `${String(baseUrl).replace(/\/+$/, "")}/api/v2/cda`;
  return async (method, path, { body, ifMatch } = {}) => {
    const headers = { Key: token, Accept: "application/json" };
    if (body) headers["Content-Type"] = "application/json";
    if (ifMatch) headers["If-Match"] = ifMatch;
    const res = await (fetchImpl ?? globalThis.fetch)(`${root}${path}`, {
      method, headers, body: body ? JSON.stringify(body) : undefined,
    });
    const etag = res.headers.get("etag");
    if (res.status === 409) {
      // The instance answers a stale If-Match with 409 and a `stale-expectation` problem, the status
      // it gives every refusal about state; any other 409 is reported as what it is.
      const problem = await res.json().catch(() => ({}));
      if (String(problem.type ?? "").endsWith("stale-expectation")) {
        return { status: 409, stale: true, etag, problem: problem.detail ?? problem.title ?? "the post changed" };
      }
      throw new Error(`shio post set: ${method} ${path} answered 409: ${problem.detail ?? problem.title ?? ""}`);
    }
    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`shio post set: ${method} ${path} answered ${res.status}${text ? `: ${text.slice(0, 300)}` : ""}`);
    }
    return { status: res.status, etag, body: await res.json() };
  };
}
