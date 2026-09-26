/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio share <address>` (SH1111) — a link a person can open, and the links that are live.
 *
 * The most useful thing an agent can hand a person after building something they have not seen is
 * a URL. This prints one: an expiring share link for the page, narrowed to that page (SH1110), the
 * same mint the console's Share button calls so there is one idea of what a share link is.
 *
 * A link that was sent is a link somebody may want to stop, and until SH1111 nothing listed them:
 * the token screen hides ephemeral rows by design. `--list` reads the live ones and `--revoke`
 * stops one, so reading and stopping sit beside making.
 */
import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("share");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  if (flags.list) {
    const links = await admin.shareLinks();
    if (!links.length) {
      ctx.stdout.write("No live share links. Make one: shio share post:<site>/<url>\n");
      return 0;
    }
    for (const link of links) {
      ctx.stdout.write(`${link.id}  ${link.site}${link.page ?? "  (whole site)"}  `
        + `expires ${link.expiresAt}  by ${link.owner}\n`);
    }
    return 0;
  }

  if (typeof flags.revoke === "string" && flags.revoke) {
    const stopped = await admin.revokeShare(flags.revoke);
    if (!stopped) {
      throw new Error(`shio share --revoke: no live link ${flags.revoke} you can see. `
        + "List them: shio share --list");
    }
    ctx.stdout.write(`Revoked ${stopped.id} (${stopped.site}${stopped.page ?? ""}). `
      + "Its next request is refused.\n");
    return 0;
  }

  const address = positionals[0];
  if (!address) {
    throw new Error("shio share: pass the page to share, or --list, or --revoke <id>. "
      + "Example: shio share post:Acme/pricing");
  }
  const agent = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const resolved = await agent.resolve(address);
  const id = resolved?.id;
  if (!id) throw new Error(`shio share: ${address} resolved to no post.`);
  const ttl = flags.ttl === undefined ? undefined : Number(flags.ttl);
  if (ttl !== undefined && (!Number.isInteger(ttl) || ttl <= 0)) {
    throw new Error(`shio share --ttl takes whole seconds, got ${JSON.stringify(flags.ttl)}.`);
  }
  const minted = await admin.mintShare(id, ttl);
  if (!minted?.previewUrl) {
    throw new Error("shio share: this instance answered no link — previews are turned off there.");
  }
  // The mint answers a relative link, because the instance does not reliably know its own public
  // origin; this caller does, having just reached it.
  const url = /^https?:\/\//.test(minted.previewUrl) ? minted.previewUrl : `${baseUrl.replace(/\/+$/, "")}${minted.previewUrl}`;
  ctx.stdout.write(`${url}\n`);
  ctx.stderr.write(`Opens ${address} only, until ${minted.expiresAt}. Stop it early: `
    + "shio share --list, then shio share --revoke <id>\n");
  return 0;
}
