/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio preview <file>` (SH1095) — see a content file's page before pushing it.
 *
 * Block I's claim is that `shio/content/**` describes a site completely and that editing files is
 * the agent's cheapest way to author (P4). What it could not do was look at the page **first**:
 * rendering from proposed values reached MCP, REST and the console (SH1014) and the files tier had
 * no verb for it. This is that verb, composed rather than invented (P10): the file is read the way
 * `push` reads it, its fields become the `data` map, and `POST /api/v2/agent/render` renders them
 * through the real page render service without saving anything.
 *
 * Three decisions, made here rather than inherited:
 *
 * - **Which address.** The file names the post it projects (its path, or `site:`/`url:` in the
 *   frontmatter), so the common case needs no flag. `--address` is for a file whose page lives at
 *   another address.
 * - **What it prints.** The page's outline and structural hash on stdout, and the rendered markup
 *   in a file whose path it prints, because a terminal is not a browser.
 * - **A page the instance does not have.** Refused with the reason: a render needs a page to render
 *   *at* (a friendly URL, a layout and a site), which is a fact about the instance, not the file.
 */
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import { tmpdir } from "node:os";

import { ShioHttpError } from "@viglet/shio-client";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { parsePost } from "../content-files.mjs";
import { CONTENT_DIR } from "../content-store.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("preview");

export async function run(flags, positionals, ctx) {
  const file = positionals[0];
  if (!file) {
    throw new Error("shio preview: pass a content file. Example: shio preview shio/content/Acme/about.md");
  }
  const absolute = isAbsolute(file) ? file : resolve(ctx.cwd, file);
  const text = await ctx.fs.readFile(absolute, "utf8");
  const post = parsePost(contentRelative(ctx.cwd, absolute), text);

  // The sidecars the frontmatter names sit beside the file (SH191), and a render without them is a
  // page missing its code fields: read exactly as `push` reads them.
  const data = { ...post.data };
  for (const [field, name] of Object.entries(post.sidecarFiles ?? {})) {
    data[field] = await ctx.fs.readFile(join(dirname(absolute), name), "utf8");
  }

  const address = flags.address ?? `post:${post.site}${post.url}`;
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  let rendered;
  try {
    rendered = await client.renderProposed({ address, state: flags.state, data }, { html: true });
  } catch (error) {
    if (error instanceof ShioHttpError && error.status === 404) {
      throw new Error(
        `shio preview: ${address} is not on this instance yet. A render needs a page to render at `
          + "(a friendly URL, a layout and a site), so push the file once, or pass --address with "
          + "the page it will replace.",
      );
    }
    throw error;
  }

  const out = flags.out ?? join(tmpdir(), "shio-preview", post.site, `${post.url.replaceAll(/^\/+/g, "") || "index"}.html`);
  await ctx.fs.mkdir(dirname(out), { recursive: true });
  await ctx.fs.writeFile(out, rendered.html ?? "", "utf8");

  const digest = rendered.digest ?? rendered;
  ctx.stdout.write(summary(address, digest, out));
  return { address, out, digest };
}

/** The path `parsePost` expects: relative to the content root when the file is under it. */
function contentRelative(cwd, absolute) {
  const root = resolve(cwd, CONTENT_DIR);
  const inside = relative(root, absolute);
  return inside.startsWith("..") || isAbsolute(inside) ? null : inside.replace(/\\/g, "/");
}

function summary(address, digest, out) {
  const lines = [`shio preview: ${address} (unsaved) digest ${digest.digest ?? "?"}, ${digest.words ?? 0} words`];
  for (const heading of digest.headings ?? []) {
    lines.push(`${"  ".repeat(Math.max(heading.level - 1, 0))}h${heading.level} ${heading.text}`);
  }
  const noAlt = (digest.images ?? []).filter((image) => image.alt === undefined || image.alt === null);
  if (noAlt.length) lines.push(`${noAlt.length} image(s) with no alt`);
  lines.push(`markup: ${out}`);
  return `${lines.join("\n")}\n`;
}
